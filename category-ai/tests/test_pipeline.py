import pytest
from finance_ai.pipeline import Request, ModelChoice, build_pipeline, normalize


def request(description="Mercado Alfa", history=None, categories=None):
    return Request.model_validate({"description": description, "categories": categories or [{"id": "food", "name": "Alimentação"}, {"id": "transport", "name": "Transporte"}], "history": history or []})


def test_rule_avoids_model_and_history_is_bounded():
    calls = []
    result = build_pipeline(lambda payload: calls.append(payload)).invoke(request())
    assert result.category_id == "food" and result.method == "RULE" and not calls


def test_history_is_used_before_rules_and_model():
    history = [{"description": "Loja Alfa", "categoryId": "transport"}] * 3
    result = build_pipeline(lambda _: pytest.fail("LLM should not be called")).invoke(request("Loja Alfa", history))
    assert result.category_id == "transport" and result.confidence == "HIGH" and result.evidence_count == 3


def test_conflicting_history_and_ambiguous_categories_abstain():
    history = [{"description": "Loja Alfa", "categoryId": c} for c in ["food", "transport"]]
    assert build_pipeline().invoke(request("Loja Alfa", history)).category_id is None
    cats = [{"id": "a", "name": "Mercado"}, {"id": "b", "name": "Alimentação"}]
    assert build_pipeline().invoke(request(categories=cats)).category_id is None


@pytest.mark.parametrize("text", ["Transferência mercado", "Empréstimo farmacia", "Mercado Pago", "Resgate", "Pix desconhecido"])
def test_financial_ambiguity_and_unknown_descriptions_do_not_invent_categories(text):
    assert build_pipeline().invoke(request(text)).category_id is None


def test_llm_output_is_validated_against_allowlist_and_context_is_minimized():
    seen = []
    def model(payload):
        seen.append(payload)
        return ModelChoice(category_id="transport")
    result = build_pipeline(model).invoke(request("Loja xyz 999 cpf 123 email@example.test"))
    assert result.category_id == "transport" and result.model_used and result.confidence == "LOW"
    assert "999" not in str(seen) and "@" not in str(seen)
    assert set(seen[0]) == {"description", "categories", "examples"}
    assert build_pipeline(lambda _: ModelChoice(category_id="invented")).invoke(request("Loja xyz")).category_id is None


def test_model_failure_abstains_and_no_memory_leaks_to_next_request():
    def broken(_): raise RuntimeError("private prompt must not escape")
    result = build_pipeline(broken).invoke(request("Loja xyz"))
    assert result.category_id is None and "private" not in result.reason and result.model_used
    chain = build_pipeline()
    chain.invoke(request("Loja xyz", [{"description": "Loja xyz", "categoryId": "food"}]))
    assert chain.invoke(request("Loja xyz")).category_id is None


def test_normalize_redacts_numeric_identifiers_and_emails():
    assert normalize("MERCÁDO 123 CPF 999.888 email@example.test") == "mercado cpf"


def test_cloud_model_names_are_rejected_before_constructing_model():
    from finance_ai.pipeline import local_model
    with pytest.raises(ValueError):
        local_model("model-cloud")
    with pytest.raises(ValueError):
        local_model("https://external.example/model")


def test_model_context_limits_categories_and_retrieved_examples():
    seen = []
    cats = [{"id": f"c{i:02}", "name": f"Tipo {i}"} for i in range(100)]
    history = [{"description": "Loja Alfa Outro", "categoryId": "c99"}] * 100
    def model(payload):
        seen.append(payload)
        return ModelChoice(category_id="not-in-context")
    result = build_pipeline(model).invoke(request("Loja Alfa Nova", history, cats))
    assert result.category_id is None
    assert len(seen[0]["categories"]) == 20 and len(seen[0]["examples"]) == 5
    assert seen[0]["categories"][0]["id"] == "c99"
