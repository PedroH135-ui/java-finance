# Retomada da publicação da IA

Atualizado em 16/11/2026.

## Estado atual — prevalece sobre o histórico de 13/11 abaixo

### Categorias automáticas locais — etapa posterior de 16/11

- Usuário substituiu a confirmação de cada categoria por classificação automática revisável **somente no teste local fictício**. Transferências continuam manuais. Não publicar nem atualizar a PR anterior com este novo escopo sem instrução pertinente.
- Branch `codex/categorias-auto-local`, baseada em `87c3468`. Sem push/deploy/merge desta etapa. A PR #14 mantém o RAG local e Flyway anteriormente revisados.
- Worker Java local classifica lotes de três, dez segundos após o lote anterior. Usa o mesmo RAG/LLM; não depende da abertura dos detalhes. Scope exige banco loopback 55434, modo vetorial, hospedagem local, UUID/e-mail fictício e ausência de Vercel.
- V10 aditiva aplicada somente no banco fictício nesta etapa. Marca origem AUTO/MANUAL, bloqueio, tentativa, método e consistência. Decisões manuais e remoções anteriores protegidas. Correção manual vence corrida por versão; remover bloqueia nova aplicação até reativação explícita. AUTO não confirmado não entra no histórico do RAG. Nenhum movimento/posting/saldo/transferência criado pela automação.
- Java 100 testes (36+64) passaram, inclusive upgrade V9→V10 com dados existentes e mudança de descrição sem perder prioridade manual; frontend lint/3 testes/build e E2E automático real passaram. E2E comprovou aplicação em segundo plano, correção preservada, remoção preservada e reativação explícita, mantendo saldos/totais/contagens.
- Ambiente ativo na 8082 com `scripts/start-review-vector-ai.ps1 -Build -UseGroq -AutomaticCategories`. Aba do usuário conectada e filtrada pelo exemplo `Pizza delivery automática fictícia`, com Delivery · Automática.
- Ler `docs/categorias-automaticas-local.md` para a explicação prática, fontes, contratos, evidências, limites, rollback e o que falta antes de uma proposta de produção. O estado do Neon descrito abaixo é da etapa anterior; nenhum Neon/Vercel foi acessado nesta implementação local.

### Recuperação e V9 — resultado mais recente de 16/11

- **Atualização posterior autorizada:** `backend/pom.xml` fixa Flyway **11.20.3** (core e PostgreSQL alinhados), preservando Spring Boot/Java/SQL. Build e 88 testes Java passaram. A nova branch `verify-v9-flyway11203-20261116` / `br-hidden-shape-b6545xdr`, filha do backup V8 original, migrou V8 → V9, validou nove migrações e repetiu com zero migrações, **sem o aviso PostgreSQL 18.7**. Invariantes financeiros e campos vazios preservados; produção continua V8, sem V9 e sem mudanças financeiras. Compute `ep-flat-fire-b6jgjozs` suspenso; expiração `2026-11-18T15:00:00Z`. O aviso citado nas entradas anteriores abaixo é histórico e foi corrigido nesta versão local, ainda não publicada.
- Acesso local verificado na aba do usuário: a tela mostrava credenciais inválidas; login com `demo@javafinance.local` / `DemoLocal123!` abriu Movimentações com os dados fictícios. Nenhuma senha/autenticação foi alterada. É conta de demonstração, separada dos usuários de produção.
- Ambiente local recompilado/reiniciado com os módulos 11.20.3 e sessão novamente confirmada na aba. E2E vetorial pós-atualização passou, preservando saldos e exigindo cliques para aplicar/remover categoria. Evidência privada em `.tools/vector-ui-flyway18-e2e`; nenhuma mudança de regra de categorização.

- Pedido atual autorizou backup e teste em branch isolada, **com apresentação do resultado antes de publicar**. Nenhum deploy/merge autorizado nesta etapa. PR de contexto existente: https://github.com/PedroH135-ui/java-finance/pull/14.
- Recuperação confirmada: `recovery-before-v9-20261116` / `br-shy-scene-b63u96kc`, filha da produção `br-red-resonance-b6w00wvg`, criada às `2026-11-16T13:58:29Z`, LSN `0/235E9D0`, estado ready, sem compute e sem expiração configurada. É ponto dentro do mesmo projeto, não dump independente.
- Teste derivado da recuperação: `verify-v9-20261116` / `br-cold-leaf-b6xfl0i8`. Cópia V8 legível com dados bancários, coincidente com a produção no intervalo comparado. Flyway executou **apenas V9**, validou nove migrações e repetiu com zero migrações. Contagens/fingerprints financeiros, saldos e view de relatórios preservados; novos campos vazios, sem categoria ou transferência aplicada. Produção conferida novamente: V8, sem V9 e sem mudança dos fingerprints.
- Compute de teste `ep-quiet-brook-b6yrld61` suspenso (idle), expiração da branch `2026-11-18T14:00:00Z`. Produção e recuperação preservadas.
- Atenção: banco PostgreSQL 18.7; Flyway 11.7.2 atual emitiu aviso de versão ainda não testada por ele. Teste passou, mas revisar compatibilidade antes de publicar. Não houve alteração de dependências, repair, baseline, conexão Vercel, importação ou LLM com dados reais.
- Relatório completo: `docs/validacao-v9-neon.md`. Runners e fingerprints privados em `.tools/neon-v9` não acompanham o Git. Antes de futuro deploy, revalidar e preparar recuperação recente se houver novos dados. As afirmações de backup não confirmado nos registros anteriores abaixo são históricas e estão superadas por este teste.

### Integração local e investigação anterior

- Escopo atual: integração vetorial **local**, dados fictícios e PR com contexto. Não publicar nem fazer merge. Branch `codex/rag-vetorial-local`, base de PR `codex/ia-pronta-producao` (essa base também contém Java/V9 ainda não publicados).
- Neon resolvido em 16/11 por consultas somente de leitura: organização `org-still-river-65847486`, projeto `java-finance` / `twilight-field-61680463`, branch principal `br-red-resonance-b6w00wvg`, endpoint `ep-bold-math-b6bdxylj`, região São Paulo, banco `java_finance`. O endpoint do projeto coincide com o hostname nos logs atuais da Vercel. Não usar o projeto `comunia-db` nem continuar procurando o endpoint antigo.
- Java de produção atual observado: `dpl_5pMaPchaokz49sJSTQSrCDbmzxb7`, saúde 200/UP e logs de inicialização com schema **8**. A mudança do deployment/conexão foi externa a esta etapa; não atribuir ao código local. **Backup não criado/confirmado, V9 não aplicada por nós.**
- Projeto Java Vercel continua sem vínculo Git, verificado antes de preparar o push. O envio desta branch não publica a aplicação.
- Integração local: Java/React 8082 → FastAPI 8126 → embeddings MiniLM 384 + pgvector 55435. Banco fictício da aplicação 55434 separado. Python não recebe a conexão da aplicação nem credenciais Neon/Pluggy. Somente o perfil fictício explicitamente configurado é aceito.
- A tela mostra exemplos e similaridade; categorias continuam sugestões. Abertura não salva; aplicar/remover exige clique. O cosseno recupera contexto, não substitui o score lexical nem mede certeza de classificação. Quando histórico/regras não resolvem, o adaptador Groq pode gerar ou reutilizar cache.
- Snapshots versionados invalidam vetores de histórico removido/reclassificado. Histórico é limitado a 100 exemplos e categorias; não é indexação de todo o banco. Modo vetorial desativado por padrão e impedido em hospedagem não local.
- Validação em 16/11: Java `verify` 88 testes (33 unitários + 55 integração), Python vetorial 10 testes, frontend lint + 3 testes, build Java/React e E2E real pela tela passaram. E2E verificou contexto pizza → Delivery, zero gravações ao abrir, dois cliques de aplicar/remover, categoria restaurada, saldos/fluxo/totais e quantidade de movimentos preservados, sem chamadas externas do navegador. Chamadas Groq ocorrem pelo Python e receberam apenas exemplos fictícios.
- Executar `pwsh -NoProfile -File scripts/start-review-vector-ai.ps1 -Build -UseGroq` após preparar o ambiente fictício e o laboratório. Sem `-UseGroq`, não chama LLM. Ver `docs/rag-vetorial-local.md` para setup, limites, testes e rollback local.
- Próxima etapa de produção continua separada: confirmar recuperação desse projeto correto, testar V9 em branch isolada, verificar invariantes e só então retomar publicação autorizada pertinente. A integração vetorial requer ainda avaliação de hospedagem do encoder, permissões multiusuário, atualização de índice e calibração antes de qualquer proposta de produção.

## Histórico de 13/11 — identificação antiga do Neon superada

## Estado confirmado

- Branch `codex/ia-pronta-producao`; fonte revisada no commit `9ffc356f054fe2ec6b817ed30ead9c35b4f1b304`.
- Usuário autorizou Python e Java em produção, incluindo V9, com backup confirmado antes da migração. Depois autorizou envio ao GitHub após a publicação e a revisão de segredos. Não presumir autorização para merge na branch principal.
- Código revisado enviado ao GitHub na branch `codex/ia-pronta-producao`, commit de segurança `79f1a69`. Nenhum merge ou PR foi realizado. A branch padrão remota foi preservada. A Vercel Java não tem vínculo Git, portanto esse push não dispara migração/deploy. Validação automática desse commit passou em Segredos e privacidade e Interface e dependências: https://github.com/PedroH135-ui/java-finance/actions/runs/43498485674.
- Usuário esclareceu que deseja aplicar a estrutura necessária sem acesso manual ao banco, e não pediu uma migração de hospedagem. O código atual não contém Prisma; usa Java/JPA/Flyway. Flyway já executa V9 no início do Java usando a conexão Secret da Vercel. Prisma também precisaria de acesso ao banco e não resolve a confirmação do backup; não foi instalado ou usado como substituto.
- Java em produção permanece no deployment `dpl_7hwmwEg2mXsnbn2PEeD3uhdD99Mz`, base `a462d694cc824e8f7a30ac18b4e1b0d0876a6644`.
- Consulta ao health check real retornou 200/UP. O host Neon foi identificado nos logs de inicialização, sem exibir a conexão completa ou credenciais.
- As variáveis `DB_URL`, `DB_USER` e `DB_PASSWORD` da Vercel são Secret e não retornam valores pela API. Não devem ser substituídas nem copiadas para outro serviço para contornar isso.
- Investigação dos metadados Vercel: as três variáveis foram cadastradas em 07/10/2026, por `pedro135he-7663`, e não possuem `configurationId`, comentário ou indicação de projeto Neon. Não existe `NEON_PROJECT_ID` nas variáveis do aplicativo. A autoria Vercel não identifica a conta proprietária no Neon.
- A instalação Neon Marketplace existente não tem vínculo administrativo com Java Finance. O armazenamento listado nos escopos consultados é somente `comunia-db`. A documentação original (`docs/publicacao.md`, inclusive no commit inicial da publicação) descreve um projeto Neon dedicado chamado `java-finance` e o banco `java_finance`; essa é uma pista histórica, não confirmação do nome atual ou da conta proprietária. O script original já recebia o ID por variável privada; o histórico desses arquivos não forneceu o ID.
- A autenticação atual da CLI Neon só lista `comunia-db`, projeto `crimson-scene-43157198`, na organização Vercel. Os metadados de armazenamento mostram vínculo com `comunia`, outro projeto Vercel; não é evidência válida de backup do Java Finance.
- Nenhum backup foi criado ou confirmado. V9 não foi aplicada e o Java não foi republicado.
- Python publicado em Production: deployment `dpl_3Xon8ddwTmKGGGjsusofnRFVo1Li`, domínio `https://java-finance-category-ai-lab.vercel.app`. Usa a fonte Python revisada do commit `9ffc356`, modo explícito `production`, Groq e token de serviço novo exclusivo de Production. Não recebeu credenciais Neon/Pluggy. Preview preservado.
- Smoke no domínio estável passou: saúde UP, modo production, provedor Groq; sem token retornou 401; regra classificou o exemplo fictício; LLM classificou restaurante fictício como Alimentação; corpo excessivo retornou 413. Nenhum dado real foi enviado. Java continua sem habilitação dessa IA; não foi aplicada categoria real.
- A URL temporária do deployment Production negou o OIDC de desenvolvimento por `TRUSTED_SOURCES_ENVIRONMENT_MISMATCH`. Não foi alterada a proteção ou criada regra de Trusted Sources: o smoke foi realizado após promoção do domínio estável autorizado.
- Ferramentas de navegador e Computer Use falharam ao inicializar com `failed to write kernel assets`, inclusive após reset. O painel oficial foi aberto via navegador padrão; sua conta/projetos não puderam ser inspecionados pela automação.
- Teste Java completo repetido: 85 testes, zero falhas, erros ou ignorados. Demais verificações anteriores estão na revisão da publicação.
- Skills e MCP Neon instalados conforme documento próprio; OAuth do MCP ainda não validado.

## Próximos passos

Laboratório vetorial local posterior, branch `codex/rag-vetorial-local`: implementado em `vector-lab`, separado da aplicação publicada. MiniLM multilíngue ONNX local, 384 dimensões, PostgreSQL/pgvector exclusivo em 127.0.0.1:55435, 18 documentos fictícios. Sete testes de integração passaram. Em oito consultas positivas, contexto aceito da categoria esperada em 1/8 lexical e 4/8 vetorial com corte experimental 0,60. Geração comparada em três casos, cinco chamadas Groq, mesmas categorias nos dois lados; não demonstrou ganho de acurácia final. Ler `docs/rag-vetorial-local.md` antes de retomar. Nenhum deploy, banco real ou dependência do serviço publicado foi alterado; Java/V9 continuam pendentes.

Teste local posterior: `scripts/demo-rag-local.ps1 -UseGroq` executou oito cenários fictícios da cadeia LangChain, com duas chamadas reais à Groq e comprovação de cache na repetição. Nenhum banco ou serviço Vercel foi acessado. Relatórios ficam em `.tools/local-review/rag-demo-groq`; código de demonstração e detalhes estão em `docs/demo-langchain-rag.md`. A demonstração é isolada e não foi publicada. Mantêm-se as pendências de Java/V9 descritas abaixo.

Suíte local ampliada: `scripts/demo-rag-local.ps1 -Suite nubank -UseGroq` passou em 14 casos fictícios de mercado, delivery, assinaturas e PIX, com três chamadas Groq e cache comprovado. PIX só com nome de pessoa acionou o LLM e retornou sem sugestão; falta avaliar filtro prévio para economizar essa chamada. Nenhuma regra de classificação foi alterada. Relatórios em `.tools/local-review/rag-demo-groq/nubank`; nada publicado ou gravado em banco.

O usuário informou que está sem acesso ao banco e pediu para deixar a conclusão documentada para outro computador. A V9 e o deploy Java devem continuar adiados. Começar por `docs/contexto/retomada-outro-computador.md`; não é necessário implementar a V9 novamente. `docs/langchain-rag-atual.md` explica o fluxo existente e diferencia o que foi desenvolvido do que já está em produção.

1. Identificar a conta/projeto Neon que possui o endpoint confirmado nos logs, sem publicar segredos. O host ficou apenas na evidência operacional local, fora deste contexto público.
2. Confirmar que o endpoint pertence ao projeto/branch selecionado. Criar um ponto de recuperação da branch correta, com metadados verificáveis; não fazer backup de `comunia-db` por engano.
3. Preferir cópia de recuperação sem compute, usando `neon branches create --no-compute --no-secrets` com projeto e parent explícitos. Ler a ajuda atual antes de executar; não presumir plano/cota ou prontidão.
4. Validar V9 em branch isolada antes da produção e preservar o ponto de recuperação. Comparar registros financeiros por contagens e fingerprints privados, sem registrar valores pessoais no Git.
5. Python Production concluído e testado. Preservar o token próprio de Production e o domínio estável; não publicar laboratório como Production. Só Python recebe chave Groq; não recebe Neon/Pluggy.
6. Configurar apenas as quatro variáveis IA do Java descritas na revisão; publicar após backup confirmado. Inicialização executa V9 inclusive em deployment sem domínio promovido.
7. Verificar versão da migração, saúde, sessão, listagem e sugestão sem aplicar categoria real, sincronizar banco ou importar lançamentos. Comparar invariantes financeiros. Documentar os resultados reais.

## Cuidados obrigatórios

Categorias continuam sugestões; não confirmar automaticamente. Não gerar postings, importar transações ou alterar saldos. Manter Pluggy v2 e correção de saldo bancário principal. Rollback da IA é desligar `CATEGORY_AI_ENABLED` e redeployar a aplicação revisada, preservando V9 e escolhas manuais. Não remover a migração nem restaurar backup sobre novas operações financeiras sem análise.

Não executar `scripts/cloud-provision.mjs`: ele é um provisionamento antigo que pode copiar dados e reconfigurar conexão. Não fazer `env pull`, `neon init` ou `checkout` que sobrescreva o `.env`. Não imprimir CLI auth, secrets, dumps ou dados financeiros. Credenciais protegidas e logs operacionais ficam em `.tools`, ignorado pelo Git.

Pacotes limpos preparados em `.tools/ai-production/java-source` e `python-source`. Somente Python foi publicado. O Java foi exportado por `git archive HEAD`, sem arquivos locais privados, e precisa ser regenerado após o commit de segurança antes da futura publicação. O Python usa somente a lista de fontes e dependências fixadas já revisada.
