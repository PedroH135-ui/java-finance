from pathlib import Path
from uuid import UUID
import pytest
from fastapi.testclient import TestClient
from finance_ai.pipeline import ModelChoice
from finance_vector.model import LocalEmbeddings
from finance_vector.store import close
from finance_vector.ui_store import open_ui_store
from finance_vector.ui_service import Settings,create_app

OWNER='00000000-0000-4000-8000-000000000001'
DELIVERY='00000000-0000-4000-8000-000000000002'
FOOD='00000000-0000-4000-8000-000000000003'
TOKEN='synthetic-ui-test-token-at-least-32-characters'
HEADERS={'Authorization':'Bearer '+TOKEN,'X-Local-Demo-Owner':OWNER,'X-Local-Demo-Direction':'EXPENSE'}


def payload(category=DELIVERY):
    return {'description':'pizza em domicilio','categories':[{'id':DELIVERY,'name':'Delivery'},{'id':FOOD,'name':'Alimentação'}],
        'history':[{'description':'pedido de pizza entregue em casa','categoryId':category}]}


@pytest.fixture(scope='module')
def client():
    root=Path(__file__).resolve().parents[2]
    embeddings=LocalEmbeddings(root/'.tools/vector-rag/model')
    resources=open_ui_store(embeddings)
    model=lambda context:ModelChoice(category_id=context['examples'][0]['categoryId'] if context['examples'] else None)
    app=create_app(Settings(OWNER,TOKEN,'none'),embeddings,resources,model)
    with TestClient(app) as client: yield client
    close(resources[0])


def test_auth_scope_and_payload_contract(client):
    assert client.post('/suggest',json=payload(),headers={}).status_code==401
    assert client.post('/suggest',json=payload(),headers={**HEADERS,'X-Local-Demo-Owner':str(UUID(int=5))}).status_code==403
    assert client.post('/suggest',json={**payload(),'amount':'100'},headers=HEADERS).status_code==422
    assert client.post('/suggest',content='x'*70000,headers={**HEADERS,'Content-Type':'application/json'}).status_code==413


def test_live_pgvector_trace_and_snapshot_updates(client):
    first=client.post('/suggest',json=payload(),headers=HEADERS)
    assert first.status_code==200
    result=first.json()
    assert result['categoryId']==DELIVERY and result['retrieval']['dimensions']==384
    assert result['retrieval']['examples'][0]['similarity']>0.9
    corrected=client.post('/suggest',json=payload(FOOD),headers=HEADERS).json()
    assert corrected['retrieval']['examples'][0]['categoryId']==FOOD
    assert all(e['categoryId']!=DELIVERY for e in corrected['retrieval']['examples'])
    empty=client.post('/suggest',json={**payload(),'history':[]},headers=HEADERS).json()
    assert not empty['retrieval']['examples'] and empty['categoryId'] is None


def test_local_config_refuses_production_and_financial_credentials(monkeypatch):
    monkeypatch.setenv('VECTOR_UI_SYNTHETIC_ONLY','true')
    monkeypatch.setenv('VECTOR_UI_OWNER',OWNER)
    monkeypatch.setenv('CATEGORY_AI_TOKEN',TOKEN)
    monkeypatch.setenv('VERCEL_ENV','production')
    with pytest.raises(RuntimeError):Settings.load()
    monkeypatch.delenv('VERCEL_ENV')
    monkeypatch.setenv('DB_URL','unexpected-configuration')
    with pytest.raises(RuntimeError):Settings.load()
