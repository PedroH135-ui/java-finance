import math
from pathlib import Path
import pytest
from finance_ai.pipeline import Request, build_pipeline
from langchain_core.runnables import RunnableLambda
from finance_vector.corpus import CATEGORIES, OWNER, documents
from finance_vector.model import DIMENSIONS, LocalEmbeddings
from finance_vector.store import prepare, connect, close, VectorRetriever


def request(description='pizza em domicilio', categories=CATEGORIES):
    return Request.model_validate({'description': description, 'categories': categories, 'history': []})


@pytest.fixture(scope='module')
def lab():
    root = Path(__file__).resolve().parents[2]
    embeddings = LocalEmbeddings(root / '.tools/vector-rag/model')
    engine, store = prepare(embeddings)
    yield embeddings, store
    close(engine)


def test_real_vectors_are_persisted_with_expected_dimensions(lab):
    with connect() as conn:
        rows, smallest, largest = conn.execute('SELECT count(*),min(vector_dims(embedding)),max(vector_dims(embedding)) FROM vector_lab.classified_examples').fetchone()
        assert rows == len(documents()) and smallest == largest == DIMENSIONS
        assert conn.execute("SELECT extversion FROM pg_extension WHERE extname='vector'").fetchone()[0] == '0.8.2'


def test_reindex_is_idempotent(lab):
    _, store = lab
    with connect() as conn:
        before = conn.execute('SELECT count(*) FROM vector_lab.classified_examples').fetchone()[0]
    store.add_documents(documents())
    with connect() as conn:
        assert conn.execute('SELECT count(*) FROM vector_lab.classified_examples').fetchone()[0] == before


def test_owner_direction_and_archived_filters_reject_identical_wrong_examples(lab):
    _, store = lab
    retriever = VectorRetriever(store)
    context = retriever(request())
    assert context['examples'][0]['categoryId'] == 'delivery'
    # The exact query exists for another owner, for income and for an inactive example.
    assert all(e['description'] != 'pizza em domicilio' for e in retriever.last_candidates)
    other = VectorRetriever(store, owner='perfil_ficticio_b')
    assert other(request())['examples'][0]['categoryId'] == 'health'


def test_model_revision_and_allowed_categories_are_query_filters(lab):
    _, store = lab
    assert not VectorRetriever(store, model_key='incompatible-model')(request())['examples']
    only_food = VectorRetriever(store)
    only_food(request(categories=[{'id':'food','name':'Alimentação'}]))
    assert all(e['categoryId'] == 'food' for e in only_food.last_candidates)
    with pytest.raises(ValueError): VectorRetriever(store, owner='not-a-synthetic-profile')


def test_pgvector_cosine_distance_matches_actual_vectors(lab):
    embeddings, _ = lab
    query = embeddings.embed_query('pizza em domicilio')
    example = embeddings.embed_query('pedido de pizza entregue em casa')
    expected = sum(a*b for a,b in zip(query,example)) / math.sqrt(sum(x*x for x in query)*sum(x*x for x in example))
    with connect() as conn:
        actual = conn.execute('SELECT 1-(embedding <=> %s::vector) FROM vector_lab.classified_examples WHERE owner_id=%s AND content=%s',
            (str(query), OWNER, 'pedido de pizza entregue em casa')).fetchone()[0]
    assert abs(actual - expected) < 0.00001


def test_semantic_context_does_not_become_lexical_confidence(lab):
    _, store = lab
    context = VectorRetriever(store)(request())
    assert context['examples'][0]['cosineSimilarity'] > 0.8
    assert context['examples'][0]['score'] < 0.45
    # No model: a high cosine score alone must not silently apply HISTORY classification.
    decision = build_pipeline().last
    assert decision.invoke(context).category_id is None


def test_conflicting_history_and_financial_ambiguity_abstain(lab):
    _, store = lab
    chain = RunnableLambda(VectorRetriever(store)) | build_pipeline().last
    assert chain.invoke(request('loja exemplo')).category_id is None
    assert chain.invoke(request('transferencia entre minhas contas')).category_id is None
    assert chain.invoke(request('pix desconhecido')).category_id is None
