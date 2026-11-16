import time
from uuid import uuid4
import pytest
from fastapi.testclient import TestClient
from finance_ai.pipeline import ModelChoice, build_pipeline, Request
from finance_ai.vector_app import create_app


@pytest.fixture
def configured(monkeypatch):
    for key in list(__import__('os').environ):
        if key.startswith(('DB_','PLUGGY_','POSTGRES_')):monkeypatch.delenv(key)
    monkeypatch.setenv('CATEGORY_AI_MODE','candidate')
    monkeypatch.setenv('CATEGORY_AI_PROVIDER','groq')
    monkeypatch.setenv('CATEGORY_AI_TOKEN','synthetic-advisor-token-for-tests-00000000')
    monkeypatch.setenv('CATEGORY_AI_JOB_TOKEN','synthetic-queue-token-for-tests-000000000')
    monkeypatch.setenv('CATEGORY_AI_LAB_EXPIRES_AT',str(int(time.time())+3600))
    return {'Authorization':'Bearer synthetic-advisor-token-for-tests-00000000',
            'X-Category-Owner':str(uuid4()),'X-Category-Direction':'EXPENSE'}


def test_cloud_auth_minimization_and_no_raw_validation_input(configured):
    class Retriever:
        def __init__(self,embeddings,owner,direction):self.examples=[]
        def __call__(self,request):return {'request':request,'examples':[]}
    calls=[]
    client=TestClient(create_app(embeddings=object(),model=lambda p:calls.append(p) or ModelChoice(),retriever_factory=Retriever))
    payload={'description':'pizza delivery ficticia','categories':[{'id':str(uuid4()),'name':'Delivery'}],'history':[]}
    assert client.post('/suggest',json=payload).status_code==401
    assert client.post('/suggest',json=payload,headers=configured).status_code==200
    assert len(calls)==1
    assert set(calls[0])=={'description','categories','examples'}
    assert configured['X-Category-Owner'] not in str(calls)
    bad=client.post('/suggest',json={**payload,'password':'do-not-echo'},headers=configured)
    assert bad.status_code==422 and 'do-not-echo' not in bad.text
    assert client.post('/jobs',json={'owner':configured['X-Category-Owner']},headers=configured).status_code==401


def test_cloud_infrastructure_failure_returns_retryable_503(configured):
    class Retriever:
        def __init__(self,*args):self.examples=[]
        def __call__(self,request):return {'request':request,'examples':[]}
    def unavailable(payload):raise RuntimeError('secret provider response')
    client=TestClient(create_app(embeddings=object(),model=unavailable,retriever_factory=Retriever))
    result=client.post('/suggest',json={'description':'pizza delivery ficticia','categories':[{'id':str(uuid4()),'name':'Delivery'}],'history':[]},headers=configured)
    assert result.status_code==503 and 'secret' not in result.text


def test_abstention_is_success_and_strict_error_mode_is_opt_in():
    request=Request(description='pizza delivery ficticia',categories=[{'id':'delivery','name':'Delivery'}],history=[])
    def unavailable(payload):raise RuntimeError('test')
    assert build_pipeline(unavailable).invoke(request).category_id is None
    with pytest.raises(RuntimeError):build_pipeline(unavailable,strict_errors=True).invoke(request)
    assert build_pipeline(lambda p:ModelChoice(),strict_errors=True).invoke(request).category_id is None


def test_person_pix_without_purpose_never_calls_model():
    calls=[]
    request=Request(description='PIX pessoa ficticia',categories=[{'id':'other','name':'Outros gastos'}],history=[])
    result=build_pipeline(lambda p:calls.append(p) or ModelChoice(),strict_errors=True).invoke(request)
    assert result.category_id is None and not result.model_used and calls==[]


def test_expired_preview_does_not_classify(configured,monkeypatch):
    monkeypatch.setenv('CATEGORY_AI_LAB_EXPIRES_AT','1')
    client=TestClient(create_app(embeddings=object(),model=lambda p:ModelChoice()))
    response=client.post('/suggest',json={'description':'mercado','categories':[],'history':[]},headers=configured)
    assert response.status_code==503
