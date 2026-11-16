import os
import secrets
import threading
import time
import uuid
import logging
from fastapi import Depends, FastAPI, HTTPException, Request
from fastapi.exceptions import RequestValidationError
from fastapi.responses import JSONResponse
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from .pipeline import Request as CategoryRequest, Suggestion, build_pipeline, local_model
from .settings import run_mode

app = FastAPI(docs_url=None, redoc_url=None, openapi_url=None)
slots = threading.BoundedSemaphore(2)
mode = run_mode()
model_name = os.environ.get("CATEGORY_AI_MODEL", "").strip()
provider = os.environ.get("CATEGORY_AI_PROVIDER", "ollama" if model_name else "none")
if provider == "groq":
    from .cloud import groq_model, MODEL
    if model_name != MODEL:
        raise ValueError("Modelo Groq diferente do laboratório configurado.")
    model = groq_model(mode)
elif provider == "ollama" and model_name:
    model = local_model(model_name)
elif provider == "none":
    model = None
else:
    raise ValueError("Provedor de IA inválido ou modelo não configurado.")
pipeline = build_pipeline(model, provider=provider)


class BodyLimit:
    def __init__(self, app): self.app = app
    async def __call__(self, scope, receive, send):
        count = 0
        async def bounded_receive():
            nonlocal count
            message = await receive()
            count += len(message.get("body", b""))
            if count > 65536:
                raise HTTPException(413, "Requisição muito grande.")
            return message
        await self.app(scope, bounded_receive, send)


app.add_middleware(BodyLimit)


request_logger = logging.getLogger("finance_ai.requests")
if not request_logger.handlers:
    request_logger.addHandler(logging.StreamHandler())
request_logger.setLevel(logging.INFO)
request_logger.propagate = False


@app.middleware("http")
async def request_timing(request: Request, call_next):
    try:
        correlation = str(uuid.UUID(request.headers.get("X-Correlation-ID", "")))
    except (ValueError, AttributeError):
        correlation = str(uuid.uuid4())
    start = time.perf_counter()
    response = await call_next(request)
    response.headers["X-Correlation-ID"] = correlation
    request_logger.info("category_ai_request correlation_id=%s status=%d duration_ms=%d", correlation, response.status_code, round((time.perf_counter()-start)*1000))
    return response


def authorize(credentials: HTTPAuthorizationCredentials | None = Depends(HTTPBearer(auto_error=False))):
    expires_at = os.environ.get("CATEGORY_AI_LAB_EXPIRES_AT", "")
    if expires_at and (not expires_at.isdecimal() or time.time() >= int(expires_at)):
        raise HTTPException(503, "Laboratório expirado; renove a configuração para continuar os testes.")
    expected = os.environ.get("CATEGORY_AI_TOKEN", "")
    if len(expected) < 32 or not expected.isascii():
        raise HTTPException(503, "Serviço local não configurado.")
    if credentials is None or not credentials.credentials.isascii() or len(credentials.credentials)>256 or not secrets.compare_digest(credentials.credentials, expected):
        raise HTTPException(401, "Acesso não autorizado.")


@app.exception_handler(RequestValidationError)
async def invalid_request(request: Request, exc: RequestValidationError):
    return JSONResponse(status_code=422, content={"detail": "Entrada inválida."})


@app.get("/health")
def health():
    return {"status": "UP", "modelEnabled": model is not None, "provider": provider, "mode": mode}


@app.post("/suggest", response_model=Suggestion, dependencies=[Depends(authorize)])
def suggest(payload: CategoryRequest):
    if not slots.acquire(blocking=False):
        raise HTTPException(503, "Serviço ocupado; tente novamente.")
    try:
        return pipeline.invoke(payload)
    finally:
        slots.release()
