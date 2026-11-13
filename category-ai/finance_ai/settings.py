"""Fail-closed release configuration; candidate runs the same classification policy."""
import os


def run_mode():
    mode = os.environ.get("CATEGORY_AI_MODE", "lab")
    if mode not in {"lab", "candidate", "production"}:
        raise ValueError("Modo de IA inválido.")
    if os.environ.get("VERCEL_ENV") == "production" and mode != "production":
        raise ValueError("O deploy de produção exige o modo de produção explícito.")
    if mode != "lab":
        token = os.environ.get("CATEGORY_AI_TOKEN", "")
        if os.environ.get("CATEGORY_AI_PROVIDER") != "groq" or len(token) < 32 or not token.isascii():
            raise ValueError("Configure o provedor e o token de serviço antes de iniciar a candidata/produção.")
        if mode == "production" and os.environ.get("CATEGORY_AI_LAB_EXPIRES_AT"):
            raise ValueError("Produção não pode usar a expiração do laboratório.")
        if mode == "candidate" and not os.environ.get("CATEGORY_AI_LAB_EXPIRES_AT", "").isdecimal():
            raise ValueError("A candidata de teste exige uma janela de expiração.")
        if any(value for key, value in os.environ.items() if key.startswith(("DB_", "PLUGGY_", "POSTGRES_"))):
            raise ValueError("O serviço Python não deve receber credenciais bancárias ou de banco de dados.")
    return mode
