"""Cloud-only category advisor; application data and credentials stay in Java."""
import logging
import os
from pathlib import Path
import secrets
import threading
import time
from uuid import UUID, uuid4
from fastapi import Depends, FastAPI, Header, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from langchain_core.runnables import RunnableLambda
from pydantic import BaseModel, ConfigDict
from .embeddings import LocalEmbeddings, MODEL, DIMENSIONS
from .pipeline import Request as CategoryRequest, build_pipeline
from .settings import run_mode
from .vector_store import CloudRetriever, reserve_budget


class BodyLimit:
    def __init__(self,app): self.app=app
    async def __call__(self,scope,receive,send):
        size=0
        async def bounded():
            nonlocal size
            message=await receive();size+=len(message.get('body',b''))
            if size>65536: raise HTTPException(413,'Requisição muito grande.')
            return message
        await self.app(scope,bounded,send)


def authorize(expected,credentials):
    expiry=os.environ.get('CATEGORY_AI_LAB_EXPIRES_AT','')
    if expiry and (not expiry.isdecimal() or time.time()>=int(expiry)):
        raise HTTPException(503,'Teste expirado.')
    if len(expected)<32 or not expected.isascii(): raise HTTPException(503,'Serviço não configurado.')
    if (credentials is None or len(credentials.credentials)>256 or not credentials.credentials.isascii()
        or not secrets.compare_digest(expected,credentials.credentials)):
        raise HTTPException(401,'Acesso não autorizado.')


class Job(BaseModel):
    model_config=ConfigDict(extra='forbid')
    owner: UUID


def create_app(embeddings=None,model=None,retriever_factory=CloudRetriever):
    mode=run_mode()
    if mode not in {'candidate','production'}: raise RuntimeError('Cloud RAG requires an explicit release mode.')
    encoder=embeddings
    encoder_lock=threading.Lock()
    def get_embeddings():
        nonlocal encoder
        # /jobs and its queue producer do not load the 235 MB encoder on a cold start.
        with encoder_lock:
            if encoder is None:encoder=LocalEmbeddings(Path(__file__).resolve().parents[1]/'models')
            return encoder
    if model is None:
        from .cloud import groq_model
        model=groq_model(mode,before_request=reserve_budget)
    decision=build_pipeline(model,provider='groq',strict_errors=True).last
    app=FastAPI(docs_url=None,redoc_url=None,openapi_url=None)
    app.add_middleware(BodyLimit)
    slot=threading.BoundedSemaphore(2)
    bearer=HTTPBearer(auto_error=False)

    def auth(credentials:HTTPAuthorizationCredentials|None=Depends(bearer)):
        authorize(os.environ.get('CATEGORY_AI_TOKEN',''),credentials)
    def job_auth(credentials:HTTPAuthorizationCredentials|None=Depends(bearer)):
        authorize(os.environ.get('CATEGORY_AI_JOB_TOKEN',''),credentials)

    from .planning import install_planning
    install_planning(app,auth)

    @app.exception_handler(RequestValidationError)
    async def invalid(request,exc):return JSONResponse(status_code=422,content={'detail':'Entrada inválida.'})

    @app.middleware('http')
    async def timing(request:Request,call_next):
        try:correlation=str(UUID(request.headers.get('X-Correlation-ID','')))
        except ValueError:correlation=str(uuid4())
        start=time.perf_counter();response=await call_next(request)
        response.headers['X-Correlation-ID']=correlation
        logging.getLogger('finance_ai.requests').info('category_cloud correlation_id=%s status=%d duration_ms=%d',correlation,response.status_code,round((time.perf_counter()-start)*1000))
        return response

    @app.get('/health')
    def health():return {'status':'UP','mode':mode,'retrieval':'PGVECTOR','dimensions':DIMENSIONS,'provider':'groq','modelEnabled':True}

    @app.post('/suggest',dependencies=[Depends(auth)])
    def suggest(payload:CategoryRequest,owner:str=Header(alias='X-Category-Owner'),direction:str=Header(alias='X-Category-Direction')):
        try:
            owner=str(UUID(owner))
            for c in payload.categories:UUID(c.id)
            for h in payload.history:UUID(h.category_id)
            if direction not in {'EXPENSE','INCOME'}:raise ValueError()
        except ValueError:raise HTTPException(422,'Contexto inválido.') from None
        if not slot.acquire(blocking=False):raise HTTPException(503,'Serviço ocupado.')
        try:
            retriever=retriever_factory(get_embeddings(),owner,direction)
            result=(RunnableLambda(retriever,name='recuperar_pgvector_cloud')|decision).invoke(payload).model_dump(by_alias=True)
            result['retrieval']={'method':'PGVECTOR','model':MODEL,'dimensions':DIMENSIONS,
                'examples':[{k:e[k] for k in ('description','categoryId','similarity')} for e in retriever.examples]}
            return result
        except Exception:
            logging.getLogger('finance_ai.requests').warning('category_cloud_unavailable code=RETRYABLE')
            raise HTTPException(503,'Sugestão temporariamente indisponível.') from None
        finally:slot.release()

    @app.post('/jobs',status_code=202,dependencies=[Depends(job_auth)])
    async def enqueue(job:Job):
        from vercel.queue import send
        try:await send('category-automation',{'owner':str(job.owner)})
        except Exception:raise HTTPException(503,'Fila temporariamente indisponível.') from None
        return {'accepted':True}
    return app
