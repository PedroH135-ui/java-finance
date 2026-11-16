from fastapi.testclient import TestClient
from finance_ai.app import app

TOKEN = "synthetic-local-category-token-32-characters"
PAYLOAD = {"description": "Mercado Alfa", "categories": [{"id": "food", "name": "Alimentação"}], "history": []}


def test_auth_required_fail_closed_and_valid_request(monkeypatch):
    client = TestClient(app)
    monkeypatch.delenv("CATEGORY_AI_TOKEN", raising=False)
    assert client.post("/suggest", json=PAYLOAD).status_code == 503
    monkeypatch.setenv("CATEGORY_AI_TOKEN", TOKEN)
    assert client.post("/suggest", json=PAYLOAD).status_code == 401
    assert client.post("/suggest", json=PAYLOAD, headers={"Authorization": "Bearer wrong"}).status_code == 401
    result = client.post("/suggest", json=PAYLOAD, headers={"Authorization": "Bearer " + TOKEN})
    assert result.status_code == 200 and result.json()["categoryId"] == "food"
    assert result.json()["modelUsed"] is False


def test_validation_does_not_echo_payload_or_allow_financial_fields(monkeypatch):
    monkeypatch.setenv("CATEGORY_AI_TOKEN", TOKEN)
    result = TestClient(app).post("/suggest", json={**PAYLOAD, "amount": "private-value"}, headers={"Authorization": "Bearer " + TOKEN})
    assert result.status_code == 422 and "private-value" not in result.text


def test_oversized_payload_is_rejected(monkeypatch):
    monkeypatch.setenv("CATEGORY_AI_TOKEN", TOKEN)
    result = TestClient(app).post("/suggest", content='{"description":"' + "x" * 70000 + '"}', headers={"Content-Type": "application/json", "Authorization": "Bearer " + TOKEN})
    assert result.status_code == 413


def test_expired_remote_laboratory_denies_classification(monkeypatch):
    monkeypatch.setenv("CATEGORY_AI_TOKEN", TOKEN)
    monkeypatch.setenv("CATEGORY_AI_LAB_EXPIRES_AT", "1")
    result = TestClient(app).post("/suggest", json=PAYLOAD, headers={"Authorization": "Bearer " + TOKEN})
    assert result.status_code == 503
    assert "expirado" in result.text


def test_correlation_id_is_validated_and_does_not_echo_arbitrary_text():
    import uuid
    client = TestClient(app)
    correlation = str(uuid.uuid4())
    assert client.get("/health", headers={"X-Correlation-ID": correlation}).headers["X-Correlation-ID"] == correlation
    response = client.get("/health", headers={"X-Correlation-ID": "private-value-not-an-id"})
    assert str(uuid.UUID(response.headers["X-Correlation-ID"])) == response.headers["X-Correlation-ID"]


def test_non_ascii_or_oversized_credentials_fail_as_unauthorized(monkeypatch):
    import pytest
    from fastapi import HTTPException
    from fastapi.security import HTTPAuthorizationCredentials
    from finance_ai.app import authorize
    monkeypatch.setenv("CATEGORY_AI_TOKEN", TOKEN)
    for value in ["é" * 32, "x" * 1000]:
        with pytest.raises(HTTPException) as result:
            authorize(HTTPAuthorizationCredentials(scheme="Bearer", credentials=value))
        assert result.value.status_code == 401
