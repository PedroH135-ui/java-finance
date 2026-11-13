import asyncio
import os
import psycopg
from sqlalchemy import URL
from langchain_postgres import Column, PGEngine, PGVectorStore
from finance_ai.pipeline import STOP, normalize
from .model import DIMENSIONS, MODEL_KEY
from .corpus import FIXTURE, OWNER, documents

SCHEMA = 'vector_lab'
TABLE = 'classified_examples'
THRESHOLD = 0.60  # Experimental, fixed before evaluation; not a calibrated confidence.


def connect():
    password = os.environ.get('VECTOR_LAB_PASSWORD', '')
    if not password:
        raise RuntimeError('Set the dedicated local VECTOR_LAB_PASSWORD in memory.')
    # No connection-string input, DATABASE_URL, DB_URL, Neon or application configuration is read.
    return psycopg.connect(host='127.0.0.1', port=55435, dbname='finance_vector_lab',
        user='vector_lab', password=password, connect_timeout=5)


def prepare(embeddings):
    with connect() as conn:
        if conn.execute('SELECT current_database(),current_user').fetchone() != ('finance_vector_lab', 'vector_lab'):
            raise RuntimeError('Not the dedicated local lab database.')
        conn.execute('CREATE EXTENSION IF NOT EXISTS vector')
        conn.execute('CREATE SCHEMA IF NOT EXISTS vector_lab')
        conn.execute('CREATE TABLE IF NOT EXISTS vector_lab.lab_identity (marker text PRIMARY KEY)')
        markers = conn.execute('SELECT marker FROM vector_lab.lab_identity').fetchall()
        if markers and markers != [('java-finance/synthetic-vector-lab',)]:
            raise RuntimeError('Unexpected lab identity.')
        conn.execute("INSERT INTO vector_lab.lab_identity VALUES ('java-finance/synthetic-vector-lab') ON CONFLICT DO NOTHING")
        exists = conn.execute("SELECT to_regclass('vector_lab.classified_examples')").fetchone()[0] is not None
    url = URL.create('postgresql+asyncpg', username='vector_lab', password=os.environ['VECTOR_LAB_PASSWORD'],
        host='127.0.0.1', port=55435, database='finance_vector_lab')
    engine = PGEngine.from_connection_string(url=url, pool_size=2, max_overflow=0, echo=False)
    if not exists:
        engine.init_vectorstore_table(table_name=TABLE, schema_name=SCHEMA, vector_size=DIMENSIONS,
            metadata_columns=[Column('owner_id','TEXT'), Column('category_id','TEXT'), Column('direction','TEXT'),
                Column('active','BOOLEAN'), Column('model_key','TEXT'), Column('fixture','TEXT')])
    store = PGVectorStore.create_sync(engine=engine, embedding_service=embeddings, table_name=TABLE,
        schema_name=SCHEMA, metadata_columns=['owner_id','category_id','direction','active','model_key','fixture'])
    store.add_documents(documents())  # Stable UUIDs: repeated initialization upserts the same fixtures.
    return engine, store


def lexical_score(left, right):
    a = set(normalize(left).split()) - STOP
    b = set(normalize(right).split()) - STOP
    return len(a & b) / len(a | b) if a and b else 0.0


class VectorRetriever:
    def __init__(self, store, owner=OWNER, model_key=MODEL_KEY):
        if owner not in {OWNER, 'perfil_ficticio_b'}:
            raise ValueError('Only synthetic owners are allowed in this lab.')
        self.store, self.owner, self.model_key = store, owner, model_key
        self.last_candidates = []

    def __call__(self, request):
        filters = {'$and': [
            {'owner_id': {'$eq': self.owner}}, {'direction': {'$eq': 'EXPENSE'}},
            {'active': {'$eq': True}}, {'fixture': {'$eq': FIXTURE}},
            {'model_key': {'$eq': self.model_key}},
            {'category_id': {'$in': [c.id for c in request.categories]}},
        ]}
        found = self.store.similarity_search_with_score(request.description, k=5, filter=filters)
        self.last_candidates = [{'description': normalize(doc.page_content), 'categoryId': doc.metadata['category_id'],
            'score': lexical_score(request.description, doc.page_content), 'cosineSimilarity': 1.0 - distance,
            'accepted': 1.0 - distance >= THRESHOLD} for doc, distance in found]
        # Keep the existing lexical score for HISTORY decisions. Cosine is NOT interchangeable with it.
        return {'request': request, 'examples': [dict(e) for e in self.last_candidates if e['accepted']]}


def close(engine):
    asyncio.run(engine.close())
