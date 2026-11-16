# Categorias automáticas: implementação e teste local

Em 16/11/2026, o usuário autorizou categorizar automaticamente **no ambiente local fictício**, com liberdade para corrigir e sem alterar saldos, antes de publicar. Esse pedido substitui a exigência anterior de confirmação de cada categoria somente neste experimento. Transferências próprias continuam exigindo confirmação. Branch `codex/categorias-auto-local`, derivada de `87c3468`; nenhum deploy, push ou merge desta etapa.

## Como funcionava antes, com um exemplo real da demonstração

1. Abrir os detalhes de `Pizza em domicílio` fazia o React chamar `GET /api/banking/entries/{id}/assistance`.
2. `BankAssistanceService` verificava o proprietário, elegibilidade e categorias permitidas. Consultava até 100 exemplos categorizados daquele perfil/natureza em `financial_record`. Histórico exato consistente podia resolver no próprio Java.
3. No modo vetorial local, `CategoryAdvisor` enviava descrição normalizada, categorias e exemplos ao Python 8126. Não enviava valor, saldo ou conexão bancária. O Python autenticado aceitava apenas o perfil fictício configurado.
4. `LocalEmbeddings` transformava descrições em vetores MiniLM de 384 dimensões. Medição real nesta sessão para `pizza em domicilio`: primeiros seis números `[-0.162946, -0.083075, -0.059448, -0.019150, -0.274341, -0.344702]`. São parte do vetor, não probabilidades ou tokens cobrados pela Groq. O encoder é local.
5. `UIHistoryRetriever` usa `PGVectorStore` para recuperar até cinco exemplos com corte cosseno experimental 0,60, filtrados por perfil, natureza, atividade, modelo, categorias e snapshot. O exemplo observado foi `pedido de pizza entregue em casa → Delivery`, cosseno **0,923**. Similaridade não mede probabilidade de categoria correta.
6. O código LangChain em `vector-lab/finance_vector/ui_service.py` encadeia recuperação e decisão:

   ```python
   retriever = UIHistoryRetriever(store, owner, direction)
   chain = RunnableLambda(retriever, name='recuperar_pgvector_local') | decision
   result = chain.invoke(payload).model_dump(by_alias=True)
   ```

7. `decision` é o Runnable final de `build_pipeline` em `category-ai/finance_ai/pipeline.py`. Tenta histórico forte e regras antes da geração. `drogaria`, havendo Saúde, pode ser resolvida pela regra; o caso da pizza passou pelo adaptador Groq. Esse adaptador pode reutilizar cache e não implica uma nova chamada HTTP em toda consulta.
8. `structured_classifier` usa chaves como `delivery` no prompt e converte a chave retornada para o UUID da categoria. O contexto é equivalente ao exemplo reduzido abaixo (o contrato real usa objetos e chaves, não apenas nomes):

   ```json
   {
     "description": "pizza em domicilio",
     "categories": [{"key": "delivery", "name": "Delivery"}],
     "examples": [{"description": "pedido de pizza entregue em casa", "key": "delivery"}]
   }
   ```

9. O modelo devolve uma chave permitida ou `null`. O Java valida novamente os IDs. Antes desta mudança, só o POST de um clique em Aplicar categoria gravava `category_id` e suas referências. O modelo não escreve SQL, não recebe acesso ao banco e não é treinado novamente: o RAG fornece contexto recuperado.

O Python remoto já publicado continua com recuperação lexical. Esse caminho com embeddings está na aplicação local, não na produção.

## O que foi implementado agora

`AutomaticCategoryService.runPending` é um worker Java com `@Scheduled`: até **três registros por lote**, com dez segundos de intervalo depois do término do lote. Não depende de abrir os detalhes ou de deixar o navegador aberto; depende do processo Java local estar em execução. Só consulta registros POSTED, visíveis, sem lançamento associado, sem transferência e sem decisão manual. Possíveis pares de transferência não são categorizados pelo worker. Não existe importação Pluggy neste fluxo.

O worker reutiliza a mesma recuperação/decisão existente. A consulta ao modelo ocorre fora da transação de escrita. Depois, `applyAutomatic` bloqueia as linhas na mesma ordem da edição manual, confere a versão e a categoria permitida e salva apenas metadados em `bank_entry`, além do evento de auditoria. Uma correção feita enquanto o modelo responde vence pelo controle de versão; a resposta antiga é descartada.

Resultado válido de HISTORY/RULE/LLM pode ser aplicado automaticamente, inclusive LLM com consistência LOW. A interface avisa que pode errar; não tratamos essa consistência como probabilidade calibrada. Resultado sem categoria, indisponibilidade ou ambiguidade não força uma escolha. Uma tentativa sem resultado fica em espera por 15 minutos para não consumir a cota a cada poll. A escolha manual permanece disponível.

### Dados e correções

A **V10** é uma migração aditiva de metadados: origem MANUAL/AUTO, bloqueio de automação, última tentativa, método e consistência. Preserva categorias existentes e remoções registradas antes da automação como decisões manuais. Não modifica valores, saldos, movimentos, postings ou pares de transferência.

- A lista mostra `Delivery · Automática` para uma classificação automática.
- Nos detalhes, **Salvar minha categoria** aplica a escolha e a marca MANUAL. Até salvar a mesma categoria automática conta como confirmação explícita.
- **Remover categoria** deixa o registro vazio e bloqueia novo preenchimento automático.
- Só **Permitir classificação automática novamente** retira esse bloqueio.
- Rótulos AUTO ainda não confirmados são excluídos da consulta de histórico do RAG. Uma correção/confirmação humana passa a ser elegível. Isso evita o sistema usar suas próprias previsões como evidência de que estava certo.
- Categorizar muda a distribuição nos relatórios/orçamentos. Não muda os totais de renda/gastos, o saldo bancário ou o saldo calculado das contas manuais.

### Limite de segurança do protótipo

O modo é falso por padrão. `AutomaticCategoryScope` rejeita ativação fora de hospedagem local, com `VERCEL_ENV`, sem o modo vetorial ou com URL diferente de `jdbc:postgresql://127.0.0.1:55434/java_finance_review`. Exige o UUID configurado e o e-mail fictício `demo@javafinance.local`. Nenhum usuário de outro perfil é processado. O endpoint de estado exige sessão; reativar uma categoria exige POST, CSRF e versão.

Os launchers mantêm segredos fora do Git, não leem `.env` real e passam ao Java somente dados do banco fictício. A chave Groq fica no Python local. Nenhuma configuração Neon/Vercel foi alterada nesta etapa.

## Executar e conferir

Com o ambiente fictício e o laboratório já preparados:

```powershell
pwsh -NoProfile -File scripts/start-review-vector-ai.ps1 -Build -UseGroq -AutomaticCategories
```

Abra `http://127.0.0.1:8082/#/movements?month=2026-11`, conta fictícia `demo@javafinance.local` / `DemoLocal123!`. A faixa indica a automação local e a lista identifica categorias automáticas. Exemplos anteriores com correção/remoção manual continuam protegidos; não devem ser sobrescritos para demonstrar o recurso.

O E2E cria um registro novo pelo seed protegido, com identificador fictício exclusivo e contrapartida fictícia. Não simula uma chamada Nubank e não altera snapshots de saldo ou ledger. Executar:

```powershell
Set-Location frontend
$env:E2E_URL='http://127.0.0.1:8082'
$env:AUTOMATIC_REVIEW_E2E='1'
npx playwright test e2e/automatic-category-local.spec.ts --output ../.tools/automatic-category-e2e
```

## Evidências de 16/11

- Java: **100 testes passaram** (36 unitários + 64 integração), zero falhas/erros/ignorados. Cobertura nova: idempotência, disputa com edição manual, remoção/reativação, isolamento, categorias inválidas/arquivadas, abstenção, exclusão de AUTO do histórico e preservação da decisão manual quando o banco muda a descrição.
- Um teste de upgrade V9 → V10 com dados existentes preservou a view financeira, saldos bancários, escolhas e remoções anteriores, sem criar movimentos/postings/transferências.
- Frontend: lint, três testes e build passaram. Revisão React manteve os hooks no topo, dados derivados na renderização, queries independentes e controles acessíveis. Não houve troca de framework/componentes.
- E2E real (primeira execução: **45,3 s**): criou uma movimentação fictícia nova enquanto o servidor rodava e aguardou AUTO/Delivery **sem abrir assistência nem enviar POST de categoria**. Corrigiu para Alimentação, aguardou outro ciclo e confirmou a prioridade manual. Removeu e aguardou outro ciclo sem reaplicação. Reativou explicitamente e obteve AUTO/Delivery novamente.
- Repetição na versão final passou em **50,0 s**, com as mesmas invariantes. Consulta somente leitura dos exemplos fictícios confirmou: conta de luz → Moradia/LLM; medicamentos na drogaria → Saúde/RULE; streaming musical → Assinaturas/LLM; pizza delivery fictícia → Delivery/LLM. LLM indica uso do adaptador e pode incluir cache.
- Saldo, fluxo, totais e quantidade de movimentos preservados durante a categorização/correção/remoção. Zero requisições externas do navegador e zero erros de página. A geração Groq usou apenas textos fictícios pelo Python.
- Conferência na aba do usuário mostrou o registro novo com `Delivery · Automática`. Evidências privadas em `.tools/automatic-category-e2e` e `.tools/automatic-category-e2e-final`; não são publicadas.

Os testes Python anteriores do recuperador/decisão continuam como evidência anterior; não foram repetidos porque esses componentes não mudaram. O seed foi exercitado pelo E2E. Não confundir estes resultados locais com teste da V10 no Neon.

## Rollback e pendências antes de produção

Para desligar apenas a automação, execute o launcher vetorial **sem** `-AutomaticCategories`. Classificações existentes ficam disponíveis para correção manual; escolhas não são apagadas. Para voltar ao modo lexical local, use `scripts/start-review-local.ps1 -Restart`. Não apagar V10 nem seu histórico Flyway em um banco já migrado.

A produção não recebeu V10 ou automação. Antes de propor publicação, testar V9+V10 em cópia Neon com recuperação recente, rever as permissões multiusuário e preferência por usuário, fila durável/leases, limites globais do provedor, monitoramento sem prompts e política de revisão/retreinamento do contexto. O guard atual é propositalmente restrito ao perfil fictício; não retirar essa restrição como forma de publicar. A hospedagem do encoder vetorial continua pendente. Não publicar esta branch nem alterar o escopo da PR anterior apenas por este documento.
