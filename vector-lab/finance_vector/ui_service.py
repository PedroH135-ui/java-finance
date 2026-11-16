"""Opt-in loopback API for the verified local fictitious Java Finance profile only."""
import logging
import os
import secrets
import threading
import time
from contextlib import asynccontextmanager
from dataclasses import dataclass
from pathlib import Path
from uuid import UUID, uuid4
from fastapi import Depends, FastAPI, Header, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from fastapi.security import HTTPBearer, HTTPAuthorizationCredentials
from langchain_core.runnables import RunnableLambda
from finance_ai.pipeline import Request as CategoryRequest, build_pipeline
from .model import LocalEmbeddings, MODEL, DIMENSIONS
from .ui_store import open_ui_store, UIHistoryRetriever


@dataclass(frozen=True)
class Settings:
    owner: str
    token: str
    provider: str

    @classmethod
    def load(cls):
        if os.environ.get('VECTOR_UI_SYNTHETIC_ONLY') != 'true' or os.environ.get('VERCEL_ENV'):
            raise RuntimeError('This service is only for the explicitly enabled local synthetic demo.')
        if any(value for key,value in os.environ.items() if key.startswith(('DB_','DATABASE_','POSTGRES_','PLUGGY_','NEON_'))):
            raise RuntimeError('Do not provide financial database or bank credentials to this service.')
        owner = str(UUID(os.environ.get('VECTOR_UI_OWNER','')))
        token = os.environ.get('CATEGORY_AI_TOKEN','')
        provider = os.environ.get('CATEGORY_AI_PROVIDER','none')
        if len(token)<32 or not token.isascii() or provider not in {'groq','none'}:
            raise RuntimeError('Invalid synthetic UI configuration.')
        return cls(owner,token,provider)


class BodyLimit:
    def __init__(self,app): self.app=app
    async def __call__(self,scope,receive,send):
        count=0
        async def bounded():
            nonlocal count
            message=await receive();count+=len(message.get('body',b''))
            if count>65536: raise HTTPException(413,'Requisição muito grande.')
            return message
        await self.app(scope,bounded,send)


def create_app(settings=None,embeddings=None,resources=None,model=None):
    settings = settings or Settings.load()
    embeddings = embeddings or LocalEmbeddings(Path(os.environ['VECTOR_UI_ROOT'])/'.tools/vector-rag/model')
    engine,store = resources or open_ui_store(embeddings)
    if model is None and settings.provider == 'groq':
        from finance_ai.cloud import groq_model
        model=groq_model('candidate')
    decision=build_pipeline(model,provider=settings.provider).last
    slot=threading.BoundedSemaphore(1)
    logger=logging.getLogger('finance_vector.ui')

    @asynccontextmanager
    async def lifespan(app):
        yield
        if resources is None: await engine.close()
    app=FastAPI(docs_url=None,redoc_url=None,openapi_url=None,lifespan=lifespan)
    app.add_middleware(BodyLimit)

    @app.middleware('http')
    async def timing(request:Request,call_next):
        try: correlation=str(UUID(request.headers.get('X-Correlation-ID','')))
        except ValueError: correlation=str(uuid4())
        start=time.perf_counter()
        response=await call_next(request)
        response.headers['X-Correlation-ID']=correlation
        logger.info('local_vector_request correlation_id=%s status=%d duration_ms=%d',correlation,response.status_code,round((time.perf_counter()-start)*1000))
        return response

    @app.exception_handler(RequestValidationError)
    async def invalid(request,exc): return JSONResponse(status_code=422,content={'detail':'Entrada inválida.'})

    def auth(credentials:HTTPAuthorizationCredentials|None=Depends(HTTPBearer(auto_error=False))):
        if credentials is None or not credentials.credentials.isascii() or len(credentials.credentials)>256 or not secrets.compare_digest(credentials.credentials,settings.token):
            raise HTTPException(401,'Acesso não autorizado.')

    from finance_ai.planning import install_planning
    install_planning(app,auth)

    @app.get('/health')
    def health(): return {'status':'UP','mode':'local-vector-demo','retrieval':'PGVECTOR','dimensions':DIMENSIONS,'provider':settings.provider,'modelEnabled':model is not None}

    @app.post('/suggest',dependencies=[Depends(auth)])
    def suggest(payload:CategoryRequest,owner:str=Header(alias='X-Local-Demo-Owner'),direction:str=Header(alias='X-Local-Demo-Direction')):
        if owner != settings.owner or direction not in {'EXPENSE','INCOME'}:
            raise HTTPException(403,'Perfil de demonstração inválido.')
        try:
            for c in payload.categories: UUID(c.id)
            for h in payload.history: UUID(h.category_id)
        except ValueError: raise HTTPException(422,'Categorias inválidas.') from None
        if not slot.acquire(blocking=False): raise HTTPException(503,'Serviço local ocupado.')
        try:
            retriever=UIHistoryRetriever(store,owner,direction)
            chain=RunnableLambda(retriever,name='recuperar_pgvector_local')|decision
            result=chain.invoke(payload).model_dump(by_alias=True)
            result['retrieval']={'method':'PGVECTOR','model':MODEL,'dimensions':DIMENSIONS,
                'examples':[{k:e[k] for k in ('description','categoryId','similarity')} for e in retriever.examples]}
            return result
        except Exception:
            logger.warning('local_vector_unavailable')
            raise HTTPException(503,'Sugestão local indisponível; escolha a categoria manualmente.') from None
        finally: slot.release()
    return app
