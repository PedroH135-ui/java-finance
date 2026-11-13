"""Groq laboratory adapter: only the explicit fictitious evaluation corpus can leave the PC."""
import os
import threading
import logging
from .pipeline import ModelChoice, structured_classifier

MODEL = "openai/gpt-oss-20b"
DESCRIPTIONS = frozenset({"restaurante sabor da vila", "clinica odontologica sorriso", "posto de gasolina avenida", "cantina bela almoco"})
EXAMPLES = frozenset({"cantina bela jantar"})
CATEGORIES = frozenset({"Alimentação", "Saúde", "Transporte", "Assinaturas", "Moradia", "Outros gastos"})


def groq_model():
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

    def choose(payload: dict) -> ModelChoice:
        nonlocal remaining
        # Exact corpus checks prevent real names, custom categories or history being sent accidentally.
        if payload["description"] not in DESCRIPTIONS:
            return ModelChoice()
        if any(c["name"] not in CATEGORIES for c in payload["categories"]):
            return ModelChoice()
        if any(e["description"] not in EXAMPLES for e in payload["examples"]):
            return ModelChoice()
        with lock:
            if remaining <= 0:
                return ModelChoice()
            remaining -= 1
        try:
            return classify(payload)
        except Exception as exc:
            status = getattr(exc, "status_code", None)
            safe_status = status if status in {400, 401, 403, 404, 429, 500, 502, 503} else "unavailable"
            logging.getLogger(__name__).warning("cloud_category_request_failed status=%s", safe_status)
            raise RuntimeError("Serviço de sugestão indisponível.") from None

    return choose
