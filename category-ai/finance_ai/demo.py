"""Offline, synthetic examples: python -m finance_ai.demo (from category-ai)."""
import json
from .pipeline import Request, build_pipeline

if __name__ == "__main__":
    chain = build_pipeline()
    categories = [{"id": "food", "name": "Alimentação"}, {"id": "transport", "name": "Transporte"}]
    cases = [
        {"description": "Mercado Alfa", "categories": categories, "history": []},
        {"description": "Loja Exemplo", "categories": categories, "history": [{"description": "Loja Exemplo", "categoryId": "food"}]},
        {"description": "Pix desconhecido", "categories": categories, "history": []},
    ]
    for case in cases:
        print(json.dumps({"example": case["description"], "suggestion": chain.invoke(Request.model_validate(case)).model_dump(by_alias=True)}, ensure_ascii=False))
