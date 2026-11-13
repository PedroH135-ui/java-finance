"""Groq adapter with explicit lab/release policy, bounded cache and provider backoff."""
import os
import threading
import logging
import time
import json
import hashlib
from collections import OrderedDict, deque
from .pipeline import ModelChoice, structured_classifier, normalize

MODEL = "openai/gpt-oss-20b"
DESCRIPTIONS = frozenset({"restaurante sabor da vila", "clinica odontologica sorriso", "posto de gasolina avenida", "cantina bela almoco"})
EXAMPLES = frozenset({"cantina bela jantar"})
CATEGORIES = frozenset({"Alimentação", "Saúde", "Transporte", "Assinaturas", "Moradia", "Outros gastos"})


def groq_model(mode="lab", *, clock=time.monotonic):
    if mode not in {"lab", "candidate", "production"}:
        raise ValueError("Modo de IA inválido.")
    if not os.environ.get("GROQ_API_KEY", "").strip():
        raise ValueError("Configure a chave Groq no processo Python antes de habilitar a nuvem.")
    import httpx
    from langchain_groq import ChatGroq
    # Fixed destination, no redirects/proxies or automatic retries. No tools or remote tracing.
    llm = ChatGroq(model=MODEL, api_key=os.environ["GROQ_API_KEY"], base_url="https://api.groq.com",
                   temperature=0, max_tokens=256, reasoning_effort="low", timeout=6.0, max_retries=0,
                   http_client=httpx.Client(timeout=6.0, trust_env=False, follow_redirects=False))
    classify = structured_classifier(llm, strict=True)
    lock = threading.Lock()
    remaining = 24  # Lab budget per process, including failed requests; no unlimited retries.
    cache = OrderedDict()
    attempts = deque()
    backoff_until = 0.0

    def choose(payload: dict) -> ModelChoice:
        nonlocal remaining, backoff_until
        payload = {"description": normalize(payload["description"]),
                   "categories": [{"id": c["id"], "name": normalize(c["name"])} for c in payload["categories"]],
                   "examples": [{"description": normalize(e["description"]), "categoryId": e["categoryId"]} for e in payload["examples"]]}
        # Exact corpus checks prevent real names, custom categories or history being sent accidentally.
        if mode == "lab":
            if payload["description"] not in DESCRIPTIONS:
                return ModelChoice()
            if any(c["name"] not in {normalize(name) for name in CATEGORIES} for c in payload["categories"]):
                return ModelChoice()
            if any(e["description"] not in EXAMPLES for e in payload["examples"]):
                return ModelChoice()
        key = hashlib.sha256(json.dumps(payload, sort_keys=True, ensure_ascii=False).encode()).digest()
        with lock:
            if mode == "lab":
                if remaining <= 0:
                    return ModelChoice()
                remaining -= 1
            else:
                now = clock()
                if key in cache:
                    expires, result = cache.pop(key)
                    if expires > now:
                        cache[key] = (expires, result)
                        return result.model_copy()
                while attempts and attempts[0] <= now - 60:
                    attempts.popleft()
                if now < backoff_until or len(attempts) >= 12:
                    raise RuntimeError("Sugestão temporariamente indisponível.")
                attempts.append(now)
        try:
            result = classify(payload)
            if mode != "lab":
                with lock:
                    cache[key] = (clock() + 300, result.model_copy())
                    while len(cache) > 128:
                        cache.popitem(last=False)
            return result
        except Exception as exc:
            status = getattr(exc, "status_code", None)
            safe_status = status if status in {400, 401, 403, 404, 429, 500, 502, 503} else "unavailable"
            logging.getLogger(__name__).warning("cloud_category_request_failed status=%s", safe_status)
            if mode != "lab":
                with lock:
                    backoff_until = clock() + 30
            raise RuntimeError("Serviço de sugestão indisponível.") from None

    return choose
