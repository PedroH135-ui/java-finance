import os
import pytest
from finance_ai.settings import run_mode


@pytest.fixture(autouse=True)
def isolate(monkeypatch):
    for key in list(os.environ):
        if key.startswith(("DB_", "PLUGGY_", "POSTGRES_", "CATEGORY_AI_")) or key == "VERCEL_ENV":
            monkeypatch.delenv(key)


def production(monkeypatch):
    monkeypatch.setenv("CATEGORY_AI_MODE", "production")
    monkeypatch.setenv("CATEGORY_AI_PROVIDER", "groq")
    monkeypatch.setenv("CATEGORY_AI_TOKEN", "synthetic-service-token-at-least-32-characters")


def test_lab_default_and_valid_production(monkeypatch):
    assert run_mode() == "lab"
    production(monkeypatch)
    assert run_mode() == "production"


def test_production_refuses_lab_defaults_expiry_and_missing_auth(monkeypatch):
    monkeypatch.setenv("VERCEL_ENV", "production")
    with pytest.raises(ValueError):
        run_mode()
    production(monkeypatch)
    monkeypatch.setenv("CATEGORY_AI_LAB_EXPIRES_AT", "9999999999")
    with pytest.raises(ValueError):
        run_mode()
    monkeypatch.delenv("CATEGORY_AI_LAB_EXPIRES_AT")
    monkeypatch.setenv("CATEGORY_AI_TOKEN", "short")
    with pytest.raises(ValueError):
        run_mode()


@pytest.mark.parametrize("key", ["DB_URL", "PLUGGY_CLIENT_SECRET", "POSTGRES_PASSWORD"])
def test_release_refuses_database_or_banking_credentials(monkeypatch, key):
    production(monkeypatch)
    monkeypatch.setenv(key, "synthetic-never-exported")
    with pytest.raises(ValueError):
        run_mode()


def test_candidate_runs_release_policy_with_expiration(monkeypatch):
    production(monkeypatch)
    monkeypatch.setenv("CATEGORY_AI_MODE", "candidate")
    with pytest.raises(ValueError):
        run_mode()
    monkeypatch.setenv("CATEGORY_AI_LAB_EXPIRES_AT", "9999999999")
    assert run_mode() == "candidate"
