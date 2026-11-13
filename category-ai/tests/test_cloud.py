import copy
import pytest
from finance_ai.cloud import groq_model
from finance_ai.pipeline import Request, build_pipeline

PAYLOAD = {"description": "restaurante sabor da vila", "categories": [{"id": "private-uuid", "name": "Alimentação"}], "examples": []}


@pytest.fixture
def fake_cloud(monkeypatch):
    import langchain_groq
    seen = {"calls": 0}
    class FakeGroq:
        def __init__(self, **kwargs): seen["config"] = kwargs
        def with_structured_output(self, schema, **kwargs):
            seen["schema"], seen["options"] = schema, kwargs
            return self
        def invoke(self, messages):
            seen["calls"] += 1
            seen["messages"] = messages
            if seen.get("fail"):
                raise seen["fail"]
            return {"category_id": "alimentacao"}
    monkeypatch.setenv("GROQ_API_KEY", "synthetic-not-a-real-key")
    monkeypatch.setattr(langchain_groq, "ChatGroq", FakeGroq)
    return seen


def test_missing_credentials_fail_without_request(monkeypatch):
    monkeypatch.delenv("GROQ_API_KEY", raising=False)
    with pytest.raises(ValueError, match="Configure a chave"):
        groq_model()


def test_real_text_custom_categories_and_history_never_reach_provider(fake_cloud):
    choose = groq_model()
    for field, value in [("description", "nome real de uma pessoa"), ("categories", [{"id": "id", "name": "Categoria particular"}]), ("examples", [{"description": "histórico particular", "categoryId": "private-uuid"}])]:
        payload = copy.deepcopy(PAYLOAD)
        payload[field] = value
        assert choose(payload).category_id is None
    assert fake_cloud["calls"] == 0


def test_strict_schema_fixed_endpoint_and_bounded_calls(fake_cloud):
    choose = groq_model()
    for _ in range(24):
        assert choose(PAYLOAD).category_id == "private-uuid"
    assert choose(PAYLOAD).category_id is None
    assert fake_cloud["calls"] == 24
    assert fake_cloud["options"] == {"method": "json_schema", "strict": True}
    assert fake_cloud["config"]["base_url"] == "https://api.groq.com"
    assert fake_cloud["config"]["max_retries"] == 0
    assert fake_cloud["config"]["max_tokens"] == 256
    assert "private-uuid" not in str(fake_cloud["messages"])
    assert "synthetic-not-a-real-key" not in str(fake_cloud["messages"])


def test_cloud_failure_abstains_without_logging_payload_or_credentials(fake_cloud, caplog):
    class RateLimit(Exception):
        status_code = 429
    fake_cloud["fail"] = RateLimit("private-prompt synthetic-not-a-real-key")
    result = build_pipeline(groq_model(), provider="groq").invoke(Request(description=PAYLOAD["description"], categories=PAYLOAD["categories"], history=[]))
    assert result.category_id is None and result.provider == "groq"
    assert "status=429" in caplog.text
    assert "private-prompt" not in caplog.text and "synthetic-not-a-real-key" not in caplog.text
