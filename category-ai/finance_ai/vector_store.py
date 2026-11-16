"""Tenant-filtered short-text RAG. Only human-confirmed history arrives from Java."""
import hashlib
import json
import os
from contextlib import contextmanager, ExitStack
from pathlib import Path
import tempfile
from urllib.parse import urlsplit
from uuid import UUID
import psycopg
from .pipeline import normalize, STOP
from .embeddings import MODEL_KEY


@contextmanager
def connect():
    dsn=os.environ.get('CATEGORY_VECTOR_URL','')
    target=urlsplit(dsn)
    expected=os.environ.get('CATEGORY_VECTOR_ALLOWED_HOST','')
    if (target.scheme not in {'postgresql','postgres'} or not expected.endswith('.neon.tech')
        or target.hostname!=expected or target.path!='/category_vectors'
        or not target.password or target.username!='category_rag_runtime'):
        raise RuntimeError('A dedicated vector database and restricted runtime role are required.')
    # Explicit TLS certificate/hostname verification, including pooled Neon endpoints.
    import certifi
    with ExitStack() as stack:
        ca=Path(certifi.where())
        if os.name=='nt' and not str(ca).isascii():
            # libpq on Windows cannot resolve some Unicode paths; retain full TLS verification.
            directory=stack.enter_context(tempfile.TemporaryDirectory(prefix='finance-ca-'))
            temporary=Path(directory)/'roots.pem';temporary.write_bytes(ca.read_bytes());ca=temporary
        conn=stack.enter_context(psycopg.connect(dsn,sslmode='verify-full',sslrootcert=str(ca),connect_timeout=10,
                                               options='-c statement_timeout=15000'))
        yield conn


def reserve_budget():
    """Global per-project budget, shared across replicas. Rollback both counters on rejection."""
    with connect() as conn:
        for bucket,unit,limit in [('day','day',30),('minute','minute',6)]:
            used=conn.execute("""INSERT INTO category_rag.model_budget(bucket,period,used)
                VALUES (%s,date_trunc(%s,now()),1) ON CONFLICT(bucket,period)
                DO UPDATE SET used=category_rag.model_budget.used+1
                WHERE category_rag.model_budget.used<%s RETURNING used""",(bucket,unit,limit)).fetchone()
            if used is None: raise RuntimeError('Shared model budget reached.')
        conn.execute("DELETE FROM category_rag.model_budget WHERE period<now()-interval '2 days'")


def lexical_score(a,b):
    a,b=set(normalize(a).split())-STOP,set(normalize(b).split())-STOP
    return len(a&b)/len(a|b) if a and b else 0.0


class CloudRetriever:
    def __init__(self,embeddings,owner,direction,connector=connect):
        self.embeddings,self.owner,self.direction=embeddings,str(UUID(owner)),direction
        if direction not in {'EXPENSE','INCOME'}: raise ValueError('Invalid direction.')
        self.connector=connector
        self.examples=[]

    def __call__(self,request):
        allowed={str(UUID(c.id)) for c in request.categories}
        history=sorted({(normalize(e.description),str(UUID(e.category_id))) for e in request.history
                        if e.category_id in allowed and normalize(e.description)})
        snapshot=hashlib.sha256(json.dumps([MODEL_KEY,sorted(allowed),history]).encode()).hexdigest()
        # Compute outside the transaction: no database lock is held during ONNX inference.
        vectors=self.embeddings.embed_documents([d for d,c in history]) if history else []
        query=self.embeddings.embed_query(request.description)
        vector=lambda v:'['+','.join(str(float(x)) for x in v)+']'
        with self.connector() as conn:
            conn.execute("SELECT set_config('category_rag.owner',%s,true)",(self.owner,))
            # Serialize snapshots of this owner/direction across instances; the read shares the lock.
            conn.execute('SELECT pg_advisory_xact_lock(hashtextextended(%s,0))',(self.owner+':'+self.direction,))
            conn.execute('DELETE FROM category_rag.example WHERE owner_id=%s AND direction=%s AND snapshot<>%s',
                         (self.owner,self.direction,snapshot))
            for (description,category),embedding in zip(history,vectors):
                conn.execute('''INSERT INTO category_rag.example(owner_id,direction,snapshot,category_id,description,model_key,embedding)
                    VALUES (%s,%s,%s,%s,%s,%s,%s::vector) ON CONFLICT DO NOTHING''',
                    (self.owner,self.direction,snapshot,category,description,MODEL_KEY,vector(embedding)))
            rows=conn.execute('''SELECT description,category_id,1-(embedding<=>%s::vector) similarity
                FROM category_rag.example WHERE owner_id=%s AND direction=%s AND snapshot=%s AND model_key=%s
                AND category_id=ANY(%s::uuid[]) ORDER BY embedding<=>%s::vector,category_id,description LIMIT 5''',
                (vector(query),self.owner,self.direction,snapshot,MODEL_KEY,list(allowed),vector(query))).fetchall() if allowed else []
        self.examples=[dict(description=d,categoryId=str(c),similarity=float(s),score=lexical_score(request.description,d))
                       for d,c,s in rows if s>=0.60]
        return {'request':request,'examples':self.examples}
