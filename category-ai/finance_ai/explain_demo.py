"""Trace the real LangChain pipeline with a fixed fictitious corpus, without a database."""
import argparse
import html
import json
import pathlib
import time
from contextlib import nullcontext
from unittest.mock import patch

import httpx
from langchain_core.callbacks import BaseCallbackHandler
from .pipeline import Request, build_pipeline, retrieve

CATEGORIES = [{"id": key, "name": name} for key, name in (
    ("food", "Alimentação"), ("transport", "Transporte"), ("health", "Saúde"))]
NAMES = {c["id"]: c["name"] for c in CATEGORIES}
CANTINA_HISTORY = [
    {"description": "Cantina Bela jantar", "categoryId": "food"},
    {"description": "Farmácia Horizonte", "categoryId": "health"},
    {"description": "Taxi Lago", "categoryId": "transport"},
]
CASES = [
    ("Regra sem LLM", "Mercado Alfa", CANTINA_HISTORY, "RULE", "food"),
    ("Histórico suficiente", "Loja Exemplo", [{"description": "Loja Exemplo", "categoryId": "food"}] * 3, "HISTORY", "food"),
    ("RAG com LLM", "Cantina Bela almoço", CANTINA_HISTORY, "LLM", "food"),
    ("Repetição com cache", "Cantina Bela almoço", CANTINA_HISTORY, "LLM", "food"),
    ("LLM sem exemplo relevante", "Restaurante Sabor da Vila", CANTINA_HISTORY, "LLM", "food"),
    ("Histórico conflitante", "Loja Exemplo", [{"description": "Loja Exemplo", "categoryId": c} for c in ("food", "transport")], "NONE", None),
    ("Descrição insuficiente", "PIX desconhecido", [], "NONE", None),
    ("Natureza financeira ambígua", "Transferência entre minhas contas", [], "NONE", None),
]


class Stages(BaseCallbackHandler):
    def __init__(self): self.names = []
    def on_chain_start(self, serialized, inputs, **kwargs):
        name = kwargs.get("name", "")
        if name in {"recuperar_exemplos", "sugerir_categoria"}: self.names.append(name)


def render(report):
    cards = []
    for case in report["cases"]:
        details = {key: case[key] for key in ("history", "retrieved", "discarded", "modelContext", "providerRequests", "stages")}
        label = "Nenhuma sugestão" if not case["category"] else case["category"]
        cards.append(f'''<article><div class="top"><span class="badge">{html.escape(case['method'])}</span>
<span>{case['elapsedMs']} ms · {len(case['providerRequests'])} chamada(s) HTTP à Groq</span></div>
<h2>{html.escape(case['description'])}</h2><p>{html.escape(case['case'])} → <strong>{html.escape(label)}</strong></p>
<p>{len(case['retrieved'])} exemplo(s) recuperado(s) · etapa LLM acionada: {'sim' if case['adapterCalls'] else 'não'}
{' · resposta reaproveitada do cache' if case['cacheReused'] else ''}</p>
<details><summary>Ver exemplos, pontuação e contexto enviado ao modelo</summary><pre>{html.escape(json.dumps(details, ensure_ascii=False, indent=2))}</pre></details></article>''')
    return '''<!doctype html><html lang="pt-BR"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>LangChain e RAG — demonstração fictícia</title><style>
body{font:16px/1.6 system-ui,sans-serif;background:#f3f6fa;color:#17273b;margin:0}main{max-width:1000px;margin:36px auto;padding:0 22px}
h1{line-height:1.2;font-size:34px}h2{font-size:22px;margin-bottom:8px}.intro,article{background:white;border:1px solid #dce4ed;border-radius:14px;padding:22px;margin:18px 0}
.top{display:flex;justify-content:space-between;gap:12px;flex-wrap:wrap;font-size:14px;color:#4b6176}.badge{background:#d9f4eb;color:#145d48;padding:2px 10px;border-radius:16px;font-weight:700}
summary{cursor:pointer;color:#185d91;font-weight:600}pre{background:#102238;color:#e0ecfa;border-radius:9px;padding:18px;overflow:auto;font-size:13px}a{color:#185d91}
</style><main><p>JAVA FINANCE · LABORATÓRIO LOCAL</p><h1>Veja a recuperação antes da geração</h1>
<div class="intro"><strong>Dados inteiramente fictícios. Sem banco, sem Java e sem chamadas à Vercel.</strong>
<p>Esta página é um relatório estático de uma execução da cadeia Python real. Abrir ou expandir um caso não chama o modelo.
Somente a execução com Groq faz chamadas externas, com o corpus fictício fixado no script.</p>
<p>LangChain: recuperar_exemplos → sugerir_categoria. Recuperação lexical: palavras em comum / união das palavras.
Exemplos entram a partir de 0,45; a partir de 0,80 podem resolver por histórico. A saída continua sendo somente uma sugestão.</p>
<p><strong>PROVIDER_SUMMARY</strong></p><p>“Etapa LLM acionada” e modelUsed não comprovam uma nova chamada de rede:
o cache pode responder. Os contadores HTTP abaixo medem requisições reais ao endpoint Groq.</p></div>'''.replace(
        "PROVIDER_SUMMARY", html.escape(f"Provedor: {report['provider']} · Casos: {len(report['cases'])} · Chamadas HTTP Groq: {report['httpRequests']} · Verificações: {'aprovadas' if report['passed'] else 'há divergências'}")
    ) + "".join(cards) + "</main></html>"


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--provider", choices=("none", "groq"), default="none")
    parser.add_argument("--output", type=pathlib.Path, required=True)
    args = parser.parse_args()
    calls, contexts = [], []
    classifier = None
    if args.provider == "groq":
        from .cloud import groq_model
        # Same cache/rate-limit policy as production, locally, with this fixed synthetic corpus only.
        classifier = groq_model(mode="candidate")

    def observed_model(payload):
        contexts.append(json.loads(json.dumps(payload)))
        return classifier(payload)

    original_send = httpx.Client.send
    def observed_send(client, request, *a, **kw):
        if request.url.host != "api.groq.com" or request.url.path != "/openai/v1/chat/completions":
            raise RuntimeError("This demo only permits Groq classification requests.")
        # This process constructs inputs exclusively from CASES. Never record auth headers.
        body = json.loads(request.content)
        context = json.loads(body["messages"][-1]["content"])
        event = {"modelContext": context}
        calls.append(event)
        response = original_send(client, request, *a, **kw)
        event["status"] = response.status_code
        return response

    chain = build_pipeline(observed_model if classifier else None, provider=args.provider)
    rows = []
    with patch.object(httpx.Client, "send", observed_send) if classifier else nullcontext():
        for label, description, history, expected_method, expected_id in CASES:
            request = Request.model_validate({"description": description, "history": history, "categories": CATEGORIES})
            examples = retrieve(request)["examples"]
            before_http, before_adapter = len(calls), len(contexts)
            stages = Stages()
            started = time.perf_counter()
            result = chain.invoke(request, config={"callbacks": [stages]})
            actual_calls = calls[before_http:]
            adapter_calls = len(contexts) - before_adapter
            if args.provider == "none" and expected_method == "LLM": expected_method, expected_id = "NONE", None
            checks = result.method == expected_method and result.category_id == expected_id
            checks = checks and stages.names == ["recuperar_exemplos", "sugerir_categoria"]
            if expected_method != "LLM": checks = checks and not actual_calls and not adapter_calls
            if classifier and label == "Repetição com cache": checks = checks and not actual_calls and adapter_calls == 1
            if classifier and label in {"RAG com LLM", "LLM sem exemplo relevante"}: checks = checks and len(actual_calls) == 1
            retrieved_descriptions = {e["description"] for e in examples}
            from .pipeline import normalize
            rows.append({"case": label, "description": description, "history": history,
                "retrieved": [{**e, "category": NAMES[e["categoryId"]]} for e in examples],
                "discarded": [e for e in history if normalize(e["description"]) not in retrieved_descriptions],
                "method": result.method, "category": NAMES.get(result.category_id), "confidence": result.confidence,
                "modelUsed": result.model_used, "adapterCalls": adapter_calls, "providerRequests": actual_calls,
                "modelContext": contexts[before_adapter:] if adapter_calls else [],
                "cacheReused": bool(adapter_calls and not actual_calls and result.category_id),
                "stages": stages.names, "passed": bool(checks),
                "elapsedMs": round((time.perf_counter() - started) * 1000)})
    report = {"provider": args.provider, "syntheticOnly": True, "databaseAccess": False,
              "productionAccess": False, "financialWrites": False, "httpRequests": len(calls),
              "passed": all(r["passed"] for r in rows), "cases": rows}
    args.output.mkdir(parents=True, exist_ok=True)
    (args.output / "result.json").write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding="utf-8")
    (args.output / "index.html").write_text(render(report), encoding="utf-8")
    print(json.dumps({**{k: v for k, v in report.items() if k != "cases"},
        "cases": [{k: row[k] for k in ("case", "description", "retrieved", "method", "category", "adapterCalls", "cacheReused", "passed", "elapsedMs")} | {"httpRequests": len(row["providerRequests"])} for row in rows]}, ensure_ascii=False, indent=2))
    return 0 if report["passed"] else 1


if __name__ == "__main__":
    raise SystemExit(main())
