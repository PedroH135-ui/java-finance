"""Small real-model evaluation with fictitious data; never connects to the database."""
import argparse
import json
import time
from .pipeline import Request, build_pipeline, local_model

CATEGORIES = [{"id": key, "name": name} for key, name in [
    ("food", "Alimentação"), ("health", "Saúde"), ("transport", "Transporte"),
    ("subscriptions", "Assinaturas"), ("housing", "Moradia"), ("other", "Outros gastos")]]
CASES = [
    ("Mercado Alfa", [], "food", "RULE"),
    ("Loja Exemplo", [{"description": "Loja Exemplo", "categoryId": "food"}], "food", "HISTORY"),
    ("Restaurante Sabor da Vila", [], "food", "LLM"),
    ("Clínica odontológica Sorriso", [], "health", "LLM"),
    ("Posto de gasolina Avenida", [], "transport", "LLM"),
    ("Cantina Bela almoço", [{"description": "Cantina Bela jantar", "categoryId": "food"}], "food", "LLM"),
    ("PIX desconhecido", [], None, "NONE"),
    ("Transferência entre minhas contas", [], None, "NONE"),
]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--model", default="qwen2.5:1.5b", choices=["qwen2.5:1.5b"])
    args = parser.parse_args()
    chain = build_pipeline(local_model(args.model))
    rows = []
    for description, history, category, method in CASES:
        start = time.perf_counter()
        result = chain.invoke(Request(description=description, categories=CATEGORIES, history=history))
        rows.append({"description": description, "expectedCategory": category, "category": result.category_id,
                     "expectedMethod": method, "method": result.method, "modelUsed": result.model_used,
                     "examples": result.evidence_count, "elapsedMs": round((time.perf_counter()-start)*1000),
                     "passed": result.category_id == category and result.method == method})
    print(json.dumps({"model": args.model, "passed": sum(r["passed"] for r in rows), "total": len(rows), "cases": rows}, ensure_ascii=False, indent=2))
    return 0 if all(r["passed"] for r in rows) else 1


if __name__ == "__main__":
    raise SystemExit(main())
