# RAG com embeddings e pgvector: laboratório local

Implementado e executado em 13/11/2026, na branch `codex/rag-vetorial-local`. Todo o corpus é fictício. Nenhum serviço publicado, dado real, saldo, migração Flyway ou banco Neon foi alterado.

## Estado e separação da aplicação

A integração principal continua com a recuperação lexical anterior. O novo experimento está em `vector-lab`, fora do pacote e das dependências do Python publicado e excluído dos contextos de deploy Vercel/Docker. Ele consulta exclusivamente seu próprio banco local. O Python Production continua sem credenciais de banco/Pluggy.

| Recurso do laboratório | Valor |
|---|---|
| Container | `java-finance-vector-lab` |
| Conexão | `127.0.0.1:55435`, banco `finance_vector_lab` |
| Volume | `java_finance_vector_lab_pg` |
| Schema/tabela | `vector_lab.classified_examples` |
| PostgreSQL/extensão | PostgreSQL 16 com pgvector 0.8.2, imagem fixada por digest |
| Modelo local | `sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2`, ONNX quantizado |
| Vetor | 384 componentes por descrição |
| Histórico indexado | 18 documentos: 15 do perfil A elegíveis e três armadilhas de isolamento |

Os bancos locais anteriores não foram reutilizados. Docker limita este container a 512 MiB e duas CPUs; o encoder Python usa duas threads de CPU. Não há API de embeddings ou GPU necessária. O download inicial obtém apenas os arquivos públicos do modelo; a inferência utiliza os pesos locais, sem enviar as descrições a um provedor de embeddings.

## 1. Transformar texto em números

`vector-lab/finance_vector/model.py` implementa a interface `Embeddings` do LangChain. `embed_documents()` codifica o histórico; `embed_query()` codifica a consulta com o mesmo modelo e normalização. FastEmbed/ONNX Runtime executa o modelo na CPU.

```python
class LocalEmbeddings(Embeddings):
    def embed_documents(self, texts):
        # Normaliza descrições e executa o modelo local.
        ...

    def embed_query(self, text):
        return self.embed_documents([text])[0]
```

O arquivo contém a implementação completa. Os números não foram inventados ou gerados por uma função aleatória. O relatório exibe dimensão e os primeiros oito componentes de cada vetor de consulta.

Escolhemos um modelo multilíngue de dimensão relativamente pequena para começar com português em CPU. Categoria, proprietário e outros metadados não são anexados ao texto para influenciar artificialmente a semelhança. O modelo aproxima significados, mas não conhece suas escolhas financeiras como verdade universal.

A revisão ONNX está fixa em `faf4aa4225822f3bc6376869cb1164e8e3feedd0`. O SHA-256 do arquivo de pesos é verificado antes da carga. Modelo, revisão e versão da normalização compõem `model_key`; vetores de outra versão não entram na consulta. Modelo, cache, senha e dados locais ficam em `.tools`, fora do Git.

## 2. Guardar vetores reais no PostgreSQL

`finance_vector/store.py` usa `PGEngine` e `PGVectorStore` de `langchain-postgres`, criando uma coluna `embedding vector(384)`. O adaptador gera os vetores ao inserir os `Document` do LangChain.

```python
store = PGVectorStore.create_sync(
    engine=engine,
    embedding_service=embeddings,
    table_name="classified_examples",
    schema_name="vector_lab",
    metadata_columns=[...],
)
store.add_documents(documents())
```

Há IDs estáveis para que repetir a carga faça upsert em vez de duplicar documentos. O banco também guarda descrição, categoria, perfil fictício, natureza receita/despesa, atividade, versão do modelo e versão do corpus. Não foram vetorizadas tabelas financeiras existentes.

O script nunca lê `DATABASE_URL`, `DB_URL`, `.env`, configurações Vercel ou Neon. Host, porta, banco e usuário de laboratório estão fixados no código; a senha aleatória é obtida por DPAPI e passa ao processo somente em memória. O banco confirma sua identidade antes da preparação.

## 3. Recuperar por significado e filtrar antes de usar

A consulta utiliza a distância por cosseno do pgvector, pelo operador `<=>`. A similaridade exibida é `1 - distância`. Em termos simplificados, a busca faz:

```sql
SELECT ...
FROM vector_lab.classified_examples
WHERE owner_id = :perfil
  AND direction = 'EXPENSE'
  AND active = true
  -- Também restringe categoria permitida, modelo/revisão e corpus.
ORDER BY embedding <=> :vetor_da_consulta
LIMIT 5;
```

O código executável usa filtros parametrizados do `PGVectorStore`, não concatena valores em SQL. Recebe até cinco vizinhos e aceita os de similaridade pelo menos `0,60`. O relatório também mostra os vizinhos rejeitados: uma busca vetorial encontra o mais próximo mesmo quando não há nada realmente adequado.

O limite `0,60` foi definido antes da avaliação e continua experimental. Não é uma probabilidade de acerto. Não foi criado índice HNSW/IVFFlat: com 18 documentos, usamos busca vetorial exata para observar qualidade sem introduzir aproximação. Coluna vetorial e busca real no banco já existem; um índice aproximado é uma decisão de escala posterior.

Três exemplos têm texto idêntico a uma consulta, mas pertencem a outro perfil, são receita ou estão inativos. Os testes confirmam que não entram no contexto do perfil A. Estes filtros não equivalem a uma implementação de autorização de produção: o laboratório não oferece API autenticada e não configurou RLS. Uma integração futura deve receber proprietário confiável do Java, usar permissões mínimas e revisar isolamento antes de processar dados reais.

## 4. Encadear recuperação e decisão no LangChain

```python
lexical_chain = build_pipeline(model, provider=provider)
semantic_chain = (
    RunnableLambda(retriever, name="recuperar_pgvector")
    | lexical_chain.last
)
```

Reutilizamos a etapa de decisão atual, com histórico consistente, regras, abstenção e geração restrita. Alteramos somente de onde vêm os exemplos. A função principal da aplicação não foi modificada.

**Cosseno não é Jaccard.** Cada exemplo vetorial leva `cosineSimilarity` para ordenar/filtrar contexto, mas conserva em `score` a comparação lexical real. Assim, cosseno alto não é confundido com histórico exato suficiente para classificar sem revisão. Por exemplo, pizza em domicílio pode ter similaridade semântica acima de 0,90 e ainda não ser uma descrição exata; segue como contexto para uma sugestão do LLM.

## 5. Comparação observada

As mesmas categorias e 15 descrições elegíveis foram usadas nos dois lados. Foram escritas oito consultas com categoria esperada e quatro cenários ambíguos/conflitantes antes de executar o modelo.

| Consulta fictícia | Busca por palavras: exemplo aceito | Melhor candidato vetorial | Cosseno | Aceito em 0,60? |
|---|---|---|---:|---|
| supermercado compra de alimentos | Mesmo exemplo | Mesmo exemplo | 1,000 | Sim |
| hortifruti legumes frescos | Nenhum | feira de frutas e verduras | 0,479 | Não |
| pizza em domicilio | Nenhum | pedido de pizza entregue em casa | 0,923 | Sim |
| mensalidade plataforma streaming de video | Nenhum | assinatura mensal de filmes e series | 0,404 | Não |
| pagamento streaming musical | Nenhum | plano mensal de musica online | 0,654 | Sim |
| medicamentos na drogaria | Nenhum | remedios comprados na farmacia | 0,792 | Sim |
| viagem de uber | Nenhum | passagem de onibus urbano | 0,580 | Não |
| conta de luz residencial | Nenhum | conta de energia eletrica | 0,440 | Não |

Após os cortes, o primeiro exemplo aceito tinha a categoria esperada em **1/8** consultas na busca lexical e **4/8** na vetorial. Isso mede recuperação de contexto neste corpus, não acurácia de classificação final, e não demonstra melhora estatística em extratos reais. O vizinho “ônibus” está na categoria Transporte, mas não representa o mesmo estabelecimento de Uber: categoria igual não torna todo exemplo igualmente útil.

Uma exploração posterior dos mesmos resultados, sem alterar o corte usado na execução, mostrou:

| Corte cosseno explorado | Consultas positivas com primeiro exemplo da categoria esperada | Consultas ambíguas que recebem algum contexto |
|---|---:|---:|
| 0,40 | 8/8 | 1/3 |
| 0,45 | 6/8 | 0/3 |
| 0,50 | 5/8 | 0/3 |
| 0,60 | 4/8 | 0/3 |
| 0,70 | 3/8 | 0/3 |

As três consultas ambíguas dessa tabela são PIX desconhecido, nome fictício sem finalidade e transferência entre contas; o conflito de categorias é tratado separadamente. Em 0,40, uma transferência passa a recuperar um exemplo de conta de água. Isso não significa que o sistema a classificaria: a decisão financeira ainda pode se abster, mas o contexto já seria pouco confiável. Não escolher automaticamente um corte pelos melhores números deste conjunto pequeno; calibrar com outro conjunto autorizado e medir falsos contextos.

## 6. Gerar com os exemplos recuperados

Com `-UseGroq`, somente pizza em domicílio, streaming musical e conta de luz executam geração real, dos dois lados. As demais consultas comparam recuperação, regras e histórico com LLM desativado; o relatório explicita essa distinção.

Nos três casos, os dois lados sugeriram respectivamente Delivery, Assinaturas e Moradia. A busca vetorial acrescentou contexto nos dois primeiros; no terceiro, ambos ficaram sem exemplo aceito. Foram cinco requisições reais: o contexto idêntico do terceiro caso permitiu reaproveitar o cache na segunda avaliação.

Logo, **não foi demonstrado ganho de acurácia final nem economia de chamadas causada pela vetorização**. O ganho observado foi recuperar contexto sem palavras idênticas. Recuperar por vetores não substitui o LLM em todos os casos; histórico, regras, cache e tamanho do prompt continuam determinando consumo. Os embeddings custam CPU e armazenamento local; não consomem tokens da Groq.

## Executar em outro computador

Pré-requisitos: Windows/PowerShell 7, Python 3.13 no PATH e Docker Desktop Linux ativo. Não precisa de acesso ao banco real.

```powershell
pwsh -NoProfile -File scripts/setup-vector-rag-local.ps1
pwsh -NoProfile -File scripts/demo-vector-rag-local.ps1 -Tests
pwsh -NoProfile -File scripts/demo-vector-rag-local.ps1
```

O setup usa `vector-lab/requirements.lock` e cria `.tools/vector-rag-venv`, preservando `.tools/category-ai-venv` e as dependências publicadas. O cliente Python `pgvector==0.3.6` é uma biblioteca diferente da extensão servidor 0.8.2; sua versão atende ao intervalo exigido pelo `langchain-postgres` instalado.

Opcionalmente, para os três testes de geração fictícia:

```powershell
# Se a chave local protegida não existir:
pwsh -NoProfile -File scripts/configure-review-groq.ps1
pwsh -NoProfile -File scripts/demo-vector-rag-local.ps1 -UseGroq
```

A chave Groq passa somente em memória. Não é necessário token do serviço publicado. Sem `-UseGroq`, nenhuma consulta ao LLM é feita.

Relatório estático: `.tools/local-review/rag-demo-groq/vector/index.html` e `result.json`. O nome do diretório reaproveita o visualizador anterior; a presença de `groq` no caminho não indica que houve geração. Confira `generationTested` e o contador de HTTP. Abrir o relatório não faz consultas ao banco ou ao modelo.

## Testes e retomada

Sete testes reais de integração passaram: dimensão/extension persistida, carga idempotente, separação de perfil/natureza/atividade, filtro de versão/categorias, cosseno SQL comparado ao cálculo numérico dos vetores, preservação das diferentes escalas e abstenção em conflito/ambiguidade.

Os 35 testes Python existentes também passaram no ambiente original. As dependências do laboratório passaram em `pip check`; os arquivos de requisitos e a cadeia principal publicados não foram modificados. Código novo e relatório fictício passaram na varredura de segredos, e os links de documentação foram conferidos.

Os pesos, volume, banco e senha são locais e não acompanham o Git. Recriar o laboratório pelo setup no outro computador; não copiar conexões reais nem arquivos DPAPI como forma de migração. Para encerrar o banco sem apagar o volume fictício:

```powershell
docker stop java-finance-vector-lab
```

Nada disso aplica V9 ou publica o Java/Python. O rollback do experimento é parar esse laboratório e continuar usando a recuperação lexical da aplicação. A futura integração com dados reais exige autorização, backup, revisão de permissões, atualização/remoção dos vetores quando categorias mudarem, calibração independente, medição de latência e decisão de hospedagem do encoder. Não copiar as credenciais Neon para o Python de produção por rotina.

## Integração pela tela local — 16/11/2026

O laboratório agora pode atender o Java Finance de demonstração. O caminho é navegador → Java em `127.0.0.1:8082` → Python em `127.0.0.1:8126` → pgvector em `127.0.0.1:55435`. A aplicação usa seu PostgreSQL fictício separado, em 55434. Nada se conecta ao Neon ou Pluggy.

Depois de preparar a aplicação fictícia conforme `docs/validacao-local.md`, instalar o laboratório com `scripts/setup-vector-rag-local.ps1` e configurar opcionalmente a chave Groq protegida:

```powershell
pwsh -NoProfile -File scripts/start-review-vector-ai.ps1 -Build -UseGroq
```

Abra `http://127.0.0.1:8082/#/movements?month=2026-11`, entre com o perfil público fictício `demo@javafinance.local` / `DemoLocal123!` e abra os detalhes de **Pizza em domicílio**. Outros alvos: **Pagamento streaming musical**, **Medicamentos na drogaria** e **Conta de luz residencial**. Sem `-UseGroq`, a recuperação vetorial e as regras funcionam, mas casos que dependem de geração podem ficar sem sugestão.

### O que o código faz e por quê

1. `seed_ui.py` confirma container, porta, banco e perfil fictício antes de adicionar histórico categorizado e quatro alvos. Reexecução preserva escolhas; não recria movimentos, postings ou saldos bancários. Contrapartidas fictícias equilibram o cenário. Não é um importador bancário.
2. O modo Java `CATEGORY_AI_LOCAL_VECTOR_DEMO=true` é opt-in, só aceita loopback autenticado e hospedagem local. Somente nesse modo envia até 100 exemplos/categorias e dois headers com proprietário/natureza. O modo normal mantém a minimização anterior e não envia esses headers.
3. `ui_service.py` aceita um único proprietário fictício, exige Bearer token, limita corpo a 64 KiB e uma sugestão concorrente. Recusa ambiente Vercel e credenciais financeiras. Não recebe conexão do banco da aplicação.
4. `ui_store.py` usa LangChain `PGVectorStore` e a tabela separada `vector_lab.ui_examples`. Normaliza descrições, cria embeddings e filtra proprietário, natureza, atividade, versão do modelo, categorias e snapshot do contexto atual. Trocar/remover classificações ou categorias invalida o snapshot anterior; vetores antigos deixam de participar e são removidos no escopo correspondente.
5. O corte cosseno experimental 0,60 seleciona até cinco exemplos. O score lexical original continua sendo usado pela decisão de histórico, evitando transformar semelhança semântica em certeza. A decisão existente tenta histórico/regras antes do LLM e pode abster-se.
6. O painel local mostra descrições recuperadas, categoria e similaridade. Informa se o adaptador LLM foi usado; `modelUsed` pode envolver cache e não prova uma nova chamada HTTP. O Groq vê somente o contexto fictício reduzido quando necessário. Abrir o painel nunca aplica categoria.

O encoder fica neste computador durante o experimento; a geração opcional ocorre na Groq. O serviço Python de produção continua separado e não recebeu pgvector, pesos ou dependências deste laboratório. O processo local não torna este fluxo disponível com o computador desligado.

### Evidência e rollback

Validação em 16/11: dez testes Python vetoriais (sete anteriores + autenticação/contrato, invalidação de snapshot e barreiras de ambiente), 88 testes Java, lint e três testes frontend, build completo e um E2E de navegador passaram. O E2E recuperou `pedido de pizza entregue em casa → Delivery`, confirmou zero escritas ao abrir, aplicou e removeu por clique, preservou saldos/fluxo/totais e quantidade de movimentos. A categoria do alvo terminou vazia para o teste manual do usuário. Similaridade não é probabilidade de acerto; esses casos não demonstram melhoria geral de acurácia.

Para repetir o E2E, com a aplicação já ativa:

```powershell
Set-Location frontend
$env:E2E_URL='http://127.0.0.1:8082'
$env:VECTOR_REVIEW_E2E='1'
npx playwright test e2e/vector-local.spec.ts --output ../.tools/vector-ui-e2e
```

O teste é opt-in e restrito à URL local; fora disso fica ignorado. Usa apenas a fixture privada fictícia e restaura sua própria aplicação de categoria. Evidências e imagens ficam ignoradas em `.tools`.

Rollback local: `pwsh -NoProfile -File scripts/start-review-local.ps1 -Restart` volta ao serviço lexical 8123 e desliga a opção vetorial no Java. Não apagar volumes ou aplicar rollback de Flyway. O serviço 8126 pode ser parado após conferir PID/comando em `.tools/vector-rag/ui-state.json`; não encerrar processos por nome genérico. Antes de migrar este experimento para dados reais: backup, branch de teste, autenticação multiusuário, política de retenção/vetores, calibração e avaliação da hospedagem do encoder continuam pendentes.

## Referências oficiais consultadas

- [PGVectorStore no LangChain](https://docs.langchain.com/oss/python/integrations/vectorstores/pgvectorstore).
- [Modelo multilíngue MiniLM](https://huggingface.co/sentence-transformers/paraphrase-multilingual-MiniLM-L12-v2).
- [Modelos suportados pelo FastEmbed](https://qdrant.github.io/fastembed/examples/Supported_Models/).
- [pgvector: distância, busca exata e índices](https://github.com/pgvector/pgvector).
