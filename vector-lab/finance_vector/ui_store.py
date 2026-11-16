"""Versioned snapshots of the authenticated fictitious profile's supplied history."""
import hashlib
import json
from uuid import NAMESPACE_URL, uuid5
from langchain_core.documents import Document
from langchain_postgres import Column, PGVectorStore
from finance_ai.pipeline import normalize
from .model import MODEL_KEY, DIMENSIONS
from .store import connect, prepare, lexical_score, THRESHOLD

UI_TABLE = 'ui_examples'
UI_FIXTURE = 'local-ui-synthetic-v1'


def open_ui_store(embeddings):
    engine, _ = prepare(embeddings)
    with connect() as conn:
        exists = conn.execute("SELECT to_regclass('vector_lab.ui_examples')").fetchone()[0] is not None
    columns = ['owner_id','category_id','direction','active','model_key','fixture','snapshot_key']
    if not exists:
        engine.init_vectorstore_table(table_name=UI_TABLE, schema_name='vector_lab', vector_size=DIMENSIONS,
            metadata_columns=[Column(c, 'BOOLEAN' if c == 'active' else 'TEXT') for c in columns])
    store = PGVectorStore.create_sync(engine=engine, embedding_service=embeddings, table_name=UI_TABLE,
        schema_name='vector_lab', metadata_columns=columns)
    return engine, store


class UIHistoryRetriever:
    def __init__(self, store, owner, direction):
        self.store, self.owner, self.direction = store, owner, direction
        self.examples = []

    def __call__(self, request):
        allowed = {c.id for c in request.categories}
        history = sorted({(normalize(e.description),e.category_id) for e in request.history if e.category_id in allowed})
        context = {'owner':self.owner,'direction':self.direction,'model':MODEL_KEY,
            'categories': sorted((c.id,normalize(c.name)) for c in request.categories),'history':history}
        snapshot = hashlib.sha256(json.dumps(context,sort_keys=True).encode()).hexdigest()
        docs = [Document(id=str(uuid5(NAMESPACE_URL,f'{snapshot}|{description}|{category}')),
            page_content=description, metadata={'owner_id':self.owner,'category_id':category,'direction':self.direction,
                'active':True,'model_key':MODEL_KEY,'fixture':UI_FIXTURE,'snapshot_key':snapshot})
            for description,category in history if description]
        if docs: self.store.add_documents(docs)
        # A changed classification/archival gets a new snapshot; old vectors cannot influence this query.
        with connect() as conn:
            conn.execute('DELETE FROM vector_lab.ui_examples WHERE owner_id=%s AND direction=%s AND fixture=%s AND snapshot_key<>%s',
                (self.owner,self.direction,UI_FIXTURE,snapshot))
        filters = {'$and': [
            {'owner_id':{'$eq':self.owner}}, {'direction':{'$eq':self.direction}}, {'active':{'$eq':True}},
            {'model_key':{'$eq':MODEL_KEY}}, {'fixture':{'$eq':UI_FIXTURE}}, {'snapshot_key':{'$eq':snapshot}},
            {'category_id':{'$in':list(allowed)}},
        ]}
        found = self.store.similarity_search_with_score(request.description,k=5,filter=filters) if allowed else []
        self.examples = [{'description':normalize(doc.page_content),'categoryId':doc.metadata['category_id'],
            'score':lexical_score(request.description,doc.page_content),'similarity':1-distance}
            for doc,distance in found if 1-distance >= THRESHOLD]
        return {'request':request,'examples':list(self.examples)}
