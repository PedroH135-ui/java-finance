"""Compare actual lexical and PGVectorStore retrieval over the same synthetic history."""
import argparse
import hashlib
import html
import json
import time
from contextlib import nullcontext
from pathlib import Path
from unittest.mock import patch

import httpx
from langchain_core.runnables import RunnableLambda
from finance_ai.pipeline import Request, build_pipeline, retrieve
from .corpus import CATEGORIES, EXAMPLES, QUERIES
from .model import LocalEmbeddings, MODEL, REVISION, DIMENSIONS
from .store import VectorRetriever, prepare, connect, close, THRESHOLD

GENERATION_CASES = {'pizza em domicilio', 'pagamento streaming musical', 'conta de luz residencial'}


def retrieval_metrics(rows, http_calls):
    positives = [row for row in rows if row['expectedCategory']]
    metrics = {'positiveQueries': len(positives), 'groqHttpRequests': http_calls}
    for method in ('lexical', 'semantic'):
        metrics[method] = {
            'top1CategoryHits': sum(bool(r[method]['retrieved']) and r[method]['retrieved'][0]['categoryId'] == r['expectedCategory'] for r in positives),
            'recallAt3CategoryHits': sum(any(e['categoryId'] == r['expectedCategory'] for e in r[method]['retrieved'][:3]) for r in positives),
            'emptyRetrievals': sum(not r[method]['retrieved'] for r in positives)}
    ambiguous = [r for r in rows if r['expectedCategory'] is None and r['description'] != 'loja exemplo']
    metrics['cutoffExplorationOnly'] = [{
        'cutoff': cut,
        'positiveTop1CategoryHits': sum(bool(r['semantic']['candidates']) and r['semantic']['candidates'][0]['cosineSimilarity'] >= cut and r['semantic']['candidates'][0]['categoryId'] == r['expectedCategory'] for r in positives),
        'ambiguousQueriesWithContext': sum(any(e['cosineSimilarity'] >= cut for e in r['semantic']['candidates']) for r in ambiguous),
    } for cut in (0.40, 0.45, 0.50, 0.60, 0.70)]
    return metrics


def render(report):
    rows = []
    names = {c['id']: c['name'] for c in CATEGORIES}
    for r in report['cases']:
        def hits(items, semantic=False):
            return '<br>'.join(html.escape(f"{e['description']} → {names[e['categoryId']]} ({e['cosineSimilarity'] if semantic else e['score']:.3f})") for e in items) or 'Nenhum exemplo aceito'
        details = html.escape(json.dumps(r, ensure_ascii=False, indent=2))
        rows.append(f'''<article><h2>{html.escape(r['description'])}</h2><p>{html.escape(r['scenario'])} · categoria esperada: {html.escape(names.get(r['expectedCategory'], 'informação insuficiente/conflito'))}</p>
<div class="columns"><section><h3>Busca atual · palavras</h3><p>{hits(r['lexical']['retrieved'])}</p>
<p>Sugestão: {html.escape(names.get(r['lexical']['suggestion']['categoryId'], 'nenhuma'))} · {r['lexical']['suggestion']['method']} · HTTP Groq: {r['lexical']['httpRequests']}</p></section>
<section><h3>Busca pgvector · significado</h3><p>{hits(r['semantic']['retrieved'], True)}</p>
<p>Sugestão: {html.escape(names.get(r['semantic']['suggestion']['categoryId'], 'nenhuma'))} · {r['semantic']['suggestion']['method']} · HTTP Groq: {r['semantic']['httpRequests']}</p></section></div>
<p>Geração com LLM neste caso: {'testada' if r['generationTested'] else 'desativada; apenas recuperação/regras/histórico'}</p>
<details><summary>Ver candidatos, vetores de consulta, contexto e chamadas reais</summary><pre>{details}</pre></details></article>''')
    metrics = html.escape(json.dumps(report['metrics'], ensure_ascii=False, indent=2))
    return '''<!doctype html><html lang="pt-BR"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>RAG vetorial local — Java Finance</title>
<style>body{font:16px/1.6 system-ui;background:#f2f6fa;color:#163047;margin:0}main{max-width:1180px;margin:30px auto;padding:0 20px}article,.intro{background:white;border:1px solid #dce5ee;border-radius:14px;padding:24px;margin:20px 0}h1{line-height:1.2}h2{font-size:23px}.columns{display:grid;grid-template-columns:1fr 1fr;gap:20px}section{background:#eff7f5;border-radius:10px;padding:16px}pre{overflow:auto;background:#102738;color:#e5f5fa;padding:18px;border-radius:9px;font-size:13px}summary{cursor:pointer;font-weight:650;color:#12668e}@media(max-width:750px){.columns{grid-template-columns:1fr}}</style>
<main><p>JAVA FINANCE · EXPERIMENTO LOCAL</p><h1>Palavras versus embeddings + pgvector</h1>
<div class="intro"><strong>Corpus inteiramente fictício. Nenhuma conexão à produção, Neon, Java ou Nubank.</strong>
<p>Descrições → modelo multilíngue local → 384 números → coluna vector(384) no PostgreSQL isolado → busca por cosseno com filtros → exemplos para LangChain.</p>
<p>Jaccard e cosseno têm escalas diferentes; nenhum é probabilidade de acerto. O corte de cosseno 0,60 é experimental. A busca exata foi usada neste corpus pequeno, sem índice aproximado HNSW.</p>
<p>As mesmas categorias e o mesmo histórico foram usados nos dois lados. A etapa de decisão original foi reutilizada; apenas a recuperação mudou. A pontuação lexical continua sendo usada para reconhecer histórico forte, evitando confirmar categorias por cosseno.</p>
<p>Os detalhes mostram os cinco vizinhos mais próximos, inclusive os rejeitados. Embeddings são calculados nesta máquina, sem API de embeddings. Abrir este relatório estático não executa IA.</p><pre>METRICS</pre>
<p>Métricas calculadas em oito consultas positivas escritas antes da avaliação. É um corpus pequeno, sem validação estatística; melhorias aqui não garantem ganho em extratos reais. Ausência de geração não significa erro do classificador: vários casos foram executados apenas para comparar recuperação.</p></div>'''.replace('METRICS', metrics) + ''.join(rows) + '</main></html>'


def run(root: Path, use_groq=False):
    embeddings = LocalEmbeddings(root / '.tools/vector-rag/model')
    start = time.perf_counter()
    engine, store = prepare(embeddings)
    indexed_ms = round((time.perf_counter() - start) * 1000)
    retriever = VectorRetriever(store)
    calls = []
    classifier = None
    if use_groq:
        from finance_ai.cloud import groq_model
        classifier = groq_model('candidate')
    original_send = httpx.Client.send
    def observed_send(client, request, *a, **kw):
        if request.url.host != 'api.groq.com' or request.url.path != '/openai/v1/chat/completions':
            raise RuntimeError('Only the fixed Groq endpoint is allowed during generation.')
        body = json.loads(request.content)
        item = {'context': json.loads(body['messages'][-1]['content'])}
        calls.append(item)
        response = original_send(client, request, *a, **kw)
        item['status'] = response.status_code
        return response
    result_rows = []
    try:
        with patch.object(httpx.Client, 'send', observed_send) if use_groq else nullcontext():
            for description, expected, scenario in QUERIES:
                request = Request.model_validate({'description': description, 'categories': CATEGORIES,
                    'history': [{'description': text, 'categoryId': category} for text, category in EXAMPLES]})
                generate = bool(classifier and description in GENERATION_CASES)
                model, provider = (classifier, 'groq') if generate else (None, 'none')
                lexical_chain = build_pipeline(model, provider=provider)
                # Reuse the existing decision Runnable; do not rewrite its safety/history/rule policy.
                semantic_chain = RunnableLambda(retriever, name='recuperar_pgvector') | lexical_chain.last
                before = len(calls)
                started = time.perf_counter()
                lexical_examples = retrieve(request)['examples']
                lexical = lexical_chain.invoke(request)
                lexical_ms = round((time.perf_counter() - started) * 1000)
                lexical_calls = calls[before:]
                before = len(calls)
                started = time.perf_counter()
                semantic = semantic_chain.invoke(request)
                semantic_ms = round((time.perf_counter() - started) * 1000)
                semantic_calls = calls[before:]
                examples = [dict(e) for e in retriever.last_candidates if e['accepted']]
                query_vector = embeddings.embed_query(description)
                result_rows.append({'description': description, 'scenario': scenario, 'expectedCategory': expected,
                    'generationTested': generate, 'queryEmbedding': {'dimensions': len(query_vector), 'firstEight': query_vector[:8]},
                    'lexical': {'retrieved': lexical_examples, 'suggestion': lexical.model_dump(by_alias=True),
                        'elapsedMs': lexical_ms, 'httpRequests': len(lexical_calls), 'requests': lexical_calls},
                    'semantic': {'retrieved': examples, 'candidates': list(retriever.last_candidates),
                        'suggestion': semantic.model_dump(by_alias=True), 'elapsedMs': semantic_ms,
                        'httpRequests': len(semantic_calls), 'requests': semantic_calls}})
        metrics = retrieval_metrics(result_rows, len(calls))
        with connect() as conn:
            pg_version = conn.execute("SELECT extversion FROM pg_extension WHERE extname='vector'").fetchone()[0]
            rows, dimensions = conn.execute('SELECT count(*),array_agg(DISTINCT vector_dims(embedding)) FROM vector_lab.classified_examples').fetchone()
        return {'syntheticOnly': True, 'productionAccess': False, 'financialWrites': False,
            'embeddingModel': MODEL, 'modelRevision': REVISION, 'dimensions': DIMENSIONS,
            'weightsSha256': hashlib.sha256((root / '.tools/vector-rag/model/model_optimized.onnx').read_bytes()).hexdigest(),
            'pgvectorVersion': pg_version, 'storedRows': rows, 'storedDimensions': dimensions,
            'indexingMs': indexed_ms, 'cosineCutoff': THRESHOLD, 'metrics': metrics, 'cases': result_rows}
    finally:
        close(engine)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--root', type=Path, required=True)
    parser.add_argument('--groq', action='store_true')
    args = parser.parse_args()
    report = run(args.root.resolve(), args.groq)
    out = args.root / '.tools/local-review/rag-demo-groq/vector'
    out.mkdir(parents=True, exist_ok=True)
    (out / 'result.json').write_text(json.dumps(report, ensure_ascii=False, indent=2), encoding='utf-8')
    (out / 'index.html').write_text(render(report), encoding='utf-8')
    print(json.dumps({k:v for k,v in report.items() if k != 'cases'}, ensure_ascii=False, indent=2))


if __name__ == '__main__': main()
