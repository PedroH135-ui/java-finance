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
