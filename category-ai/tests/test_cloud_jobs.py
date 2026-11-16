import asyncio
from uuid import uuid4
import pytest
import tasks


@pytest.fixture
def configured(monkeypatch):
    monkeypatch.setenv('CATEGORY_JAVA_URL','https://example.vercel.app')
    monkeypatch.setenv('CATEGORY_JAVA_ALLOWED_HOST','example.vercel.app')
    monkeypatch.setenv('CATEGORY_AI_JOB_TOKEN','synthetic-job-token-for-unit-test-only')
    return str(uuid4())


def test_subscriber_uses_fresh_identity_and_requeues_only_owner(configured,monkeypatch):
    identities=iter(['synthetic-first','synthetic-refreshed']);requests=[];messages=[]
    async def identity():return next(identities)
    async def send(topic,message):messages.append((topic,message))
    class Response:
        status_code=200
        def json(self):return {'more':len(requests)==1}
    class Client:
        def __init__(self,**kwargs):assert kwargs['trust_env'] is False and kwargs['follow_redirects'] is False
        async def __aenter__(self):return self
        async def __aexit__(self,*args):pass
        async def post(self,url,**kwargs):requests.append((url,kwargs));return Response()
    monkeypatch.setattr(tasks,'get_vercel_oidc_token',identity);monkeypatch.setattr(tasks,'send',send)
    monkeypatch.setattr(tasks.httpx,'AsyncClient',Client)
    asyncio.run(tasks.categories({'owner':configured}));asyncio.run(tasks.categories({'owner':configured}))
    assert [r[1]['headers']['x-vercel-trusted-oidc-idp-token'] for r in requests]==['synthetic-first','synthetic-refreshed']
    assert messages==[('category-automation',{'owner':configured})]
    assert all(r[0]=='https://example.vercel.app/api/banking/jobs/categories' for r in requests)


def test_subscriber_rejects_arbitrary_destinations_and_extra_payload(configured,monkeypatch):
    with pytest.raises(ValueError):asyncio.run(tasks.categories({'owner':configured,'url':'https://untrusted.example'}))
    monkeypatch.setenv('CATEGORY_JAVA_URL','https://untrusted.example')
    with pytest.raises(RuntimeError):asyncio.run(tasks.categories({'owner':configured}))
