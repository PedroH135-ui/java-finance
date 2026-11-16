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
            return {"category_id": seen.get("category", "alimentacao")}
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


def test_release_accepts_custom_categories_and_only_sends_normalized_context(fake_cloud):
    fake_cloud["category"] = "moradia_aluguel"
    choose = groq_model("candidate")
    payload = {"description": "IMOBILIARIA FICTICIA 123 email@example.test", "categories": [{"id": "private-home-id", "name": "Moradia Aluguel"}], "examples": []}
    assert choose(payload).category_id == "private-home-id"
    messages = str(fake_cloud["messages"])
    assert "private-home-id" not in messages and "@" not in messages and "123" not in messages
    assert "imobiliaria ficticia" in messages


def test_release_cache_expires_and_category_ids_partition_profiles(fake_cloud):
    clock = [0.0]
    choose = groq_model("production", clock=lambda: clock[0])
    assert choose(PAYLOAD).category_id == "private-uuid"
    assert choose(PAYLOAD).category_id == "private-uuid"
    assert fake_cloud["calls"] == 1
    other = copy.deepcopy(PAYLOAD)
    other["categories"][0]["id"] = "another-profile-category"
    assert choose(other).category_id == "another-profile-category"
    assert fake_cloud["calls"] == 2
    clock[0] = 301
    choose(PAYLOAD)
    assert fake_cloud["calls"] == 3


def test_release_window_recovers_without_restarting_worker(fake_cloud):
    clock = [0.0]
    choose = groq_model("production", clock=lambda: clock[0])
    for index in range(12):
        choose({**PAYLOAD, "description": "restaurante ficticio " + chr(97 + index)})
    with pytest.raises(RuntimeError):
        choose({**PAYLOAD, "description": "restaurante ficticio novo"})
    assert fake_cloud["calls"] == 12
    clock[0] = 61
    choose({**PAYLOAD, "description": "restaurante ficticio novo"})
    assert fake_cloud["calls"] == 13


def test_provider_error_cooldown_avoids_repeated_requests(fake_cloud):
    class Unauthorized(Exception):
        status_code = 401
    fake_cloud["fail"] = Unauthorized("sensitive response")
    clock = [0.0]
    choose = groq_model("production", clock=lambda: clock[0])
    for _ in range(2):
        with pytest.raises(RuntimeError):
            choose(PAYLOAD)
    assert fake_cloud["calls"] == 1
    clock[0] = 31
    fake_cloud.pop("fail")
    assert choose(PAYLOAD).category_id == "private-uuid"
    assert fake_cloud["calls"] == 2
