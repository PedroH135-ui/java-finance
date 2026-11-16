"""Explicit opt-in against only the empty, dedicated cloud test database."""
import os
from concurrent.futures import ThreadPoolExecutor
from uuid import uuid4
import pytest
from finance_ai.pipeline import Request
from finance_ai.vector_store import CloudRetriever,connect,reserve_budget

pytestmark=pytest.mark.skipif(os.environ.get('RUN_SYNTHETIC_CLOUD_VECTOR_TESTS')!='true',reason='Isolated cloud database opt-in required.')


class Embeddings:
    def embed_query(self,text):return [1.0]+[0.0]*383
    def embed_documents(self,texts):return [self.embed_query(t) for t in texts]


def request(category,description='pizza delivery ficticia',history=None):
    return Request(description=description,categories=[{'id':category,'name':'Delivery'}],history=history or [])


def test_tenants_directions_corrections_and_empty_snapshot():
    a,b,cat=str(uuid4()),str(uuid4()),str(uuid4())
    retriever=CloudRetriever(Embeddings(),a,'EXPENSE')
    retriever(request(cat,history=[{'description':'pizza teste','categoryId':cat}]))
    assert len(retriever.examples)==1
    other=CloudRetriever(Embeddings(),b,'EXPENSE');other(request(cat));assert other.examples==[]
    income=CloudRetriever(Embeddings(),a,'INCOME');income(request(cat));assert income.examples==[]
    with connect() as c:
        c.execute("SELECT set_config('category_rag.owner',%s,true)",(b,))
        assert c.execute('SELECT count(*) FROM category_rag.example WHERE owner_id=%s',(a,)).fetchone()[0]==0
    retriever(request(cat));assert retriever.examples==[]
    with connect() as c:
        c.execute("SELECT set_config('category_rag.owner',%s,true)",(a,))
        assert c.execute('SELECT count(*) FROM category_rag.example WHERE owner_id=%s',(a,)).fetchone()[0]==0


def test_concurrent_snapshots_read_their_own_human_history():
    owner,cat=str(uuid4()),str(uuid4())
    def run(description):
        retriever=CloudRetriever(Embeddings(),owner,'EXPENSE')
        retriever(request(cat,history=[{'description':description,'categoryId':cat}]))
        return [e['description'] for e in retriever.examples]
    with ThreadPoolExecutor(max_workers=2) as pool:
        results=list(pool.map(run,['pizza humana antiga','pizza humana corrigida']))
    assert results==[['pizza humana antiga'],['pizza humana corrigida']]


def test_global_budget_is_atomic_across_replicas():
    # Test owns this new synthetic database; never run against a production vector database.
    assert os.environ.get('CATEGORY_VECTOR_ALLOWED_HOST','').startswith('ep-purple-shadow-b6xibcx2')
    with connect() as c:c.execute('DELETE FROM category_rag.model_budget')
    def reserve(_):
        try:reserve_budget();return True
        except RuntimeError:return False
    with ThreadPoolExecutor(max_workers=8) as pool:results=list(pool.map(reserve,range(10)))
    assert sum(results)==6
    with connect() as c:
        assert c.execute("SELECT used FROM category_rag.model_budget WHERE bucket='minute'").fetchone()[0]==6
        assert c.execute("SELECT used FROM category_rag.model_budget WHERE bucket='day'").fetchone()[0]==6
        c.execute('DELETE FROM category_rag.model_budget')
