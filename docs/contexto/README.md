# Contexto do Java Finance

**Estado atual — 17/11, Production/V16:** salário, gastos e capacidade por meta publicados com autorização, backup V15 decriptado/restaurado e V16 aprovada na branch isolada. Java/Python UP; smoke autenticado sem salvar previsões ou meta fictícia; saldos, correções e preferências preservados. Ler [relatório e rollback](../publicacao-metas-renda-2026-11-17.md) e [implementação/testes](../metas-renda-mensal-local.md). Código e contexto enviados em `codex/metas-renda-mensal` e incorporados à [PR17](https://github.com/PedroH135-ui/java-finance/pull/17), sem merge; conferir os checks no HEAD da PR. As entradas abaixo são históricas, inclusive as referências à V16 somente local.

**17/11 — GitHub e evolução local:** [PR17](https://github.com/PedroH135-ui/java-finance/pull/17) aberta com a versão Production/V15, testes e rollback; checks aprovados, sem merge/deploy novo e segredos novamente verificados. Branch atual `codex/metas-renda-mensal` prepara **somente localmente** salário, histórico de gastos e capacidade por meta, V16 aditiva. Ler [implementação e limites](../metas-renda-mensal-local.md) e o topo do handoff. Não presumir que essa evolução local está na PR ou em produção.

**Publicação mais recente concluída:** Java/Python e V14/V15 em Production, commit funcional `c4135e9`, recuperação restaurada e teste isolado aprovados, hashes financeiros/preferências preservados. IA **ativada por padrão nos novos cadastros/primeiro vínculo**, opt-out anterior e correções preservados; cadastro real continua desabilitado. [Relatório e rollback](../publicacao-organizacao-2026-11-16.md). Sem push/PR/merge nesta etapa. Entradas abaixo são históricas.

**Etapa local mais recente (16/11):** branch `codex/organizacao-financeira-local`: natureza financeira confirmada separa consumo, PIX por tipo de contraparte, até três etapas/uma chamada LLM, padrões+organização ao vincular com opt-out respeitado, controle em Configurações, Investimentos e planejador de metas. Apenas dados fictícios e bancos Docker locais; sem push, PR, merge ou deploy. Ler [implementação e limites](../organizacao-financeira-local.md) e o topo do handoff. Produção continua V13 conforme última publicação registrada; esta etapa não a revalidou. As entradas abaixo são históricas.

Atualizado em 16/11/2026. Java 21 e Spring Boot 3.5.16, React/Vite e PostgreSQL no Neon. Java/React usam container na Vercel; o serviço Python FastAPI/LangChain é separado e usa Groq. Preserve essa arquitetura.

**Última validação em produção:** usuário autorizou teste da IA com dados reais; preferência já estava ligada ao abrir a sessão e foi mantida. Retomada controlada da fila concluída e módulos conferidos, saldos preservados. O recurso segue disponível para todos, cada usuário ativa (preferência explicitamente confirmada pelo usuário). Ler [resultado e melhorias pendentes](../validacao-ia-real-e-modulos.md). Dados financeiros reais ficam somente nas evidências privadas, não no Git. Não houve deploy nesta etapa; mensagem de sessão expirada continua somente local.

**Diagnóstico posterior:** PR #16 integrada (`34c2098`), sem novo deploy. Falha ao adicionar foi POST 401/sessão expirada; após renovação, uma tentativa adicionou 14 categorias ao perfil real, sem alteração financeira ou de opt-in. Melhoria de mensagem 401/403 em trabalho local na branch `codex/corrige-categorias-padrao`, ainda não publicada/enviada. Ler [diagnóstico e resolução](../categorias-padrao-sessao-expirada.md). As afirmações anteriores de PR aberta/nenhuma categoria real adicionada abaixo são históricas.

**Última publicação autorizada:** categorias padrão do commit `e9137ce` em Production, Java `dpl_8PAoHDgMV7uwXmwCFHQMj5kWSHZV`. Python e V13 preservados, nenhum usuário ativado ou categoria real adicionada por este rollout. Ler [publicação e rollback das categorias padrão](../publicacao-categorias-padrao-2026-11-16.md). As referências abaixo à etapa local registram o momento anterior.

Código enviado em `codex/categorias-padrao`, [PR #16](https://github.com/PedroH135-ui/java-finance/pull/16) aberta contra `feat/fundacao-financeira`, sem merge. Esta é a branch mais recente para retomar em outro computador. Conferir os checks remotos na PR.

**Trabalho local posterior:** categorias padrão no cadastro e botão idempotente para perfis existentes, branch `codex/categorias-padrao`, baseada na PR #15 já integrada (`647dbc4`). PR #14 fechada como substituída. Esta alteração ainda não foi publicada nem enviada ao GitHub. Ler [implementação, testes e rollback](../categorias-padrao.md). As referências abaixo à PR aberta são o registro da etapa anterior.

**Produção publicada em 16/11/2026**, com autorização explícita: Java/React e Python RAG/pgvector/fila na nuvem, schema **V13**, ativação individual desligada por padrão e correções humanas prioritárias. Recuperação recente confirmada e hashes financeiros preservados. Chave Groq rotacionada; antiga revogada. Ler [publicação, verificações e rollback](../publicacao-rag-producao-2026-11-16.md).

Branch atual `codex/publica-rag-cloud`, base funcional `37334ad`, correção pooled `ce2401f`. Enviada ao GitHub com autorização em 16/11/2026: [PR #15](https://github.com/PedroH135-ui/java-finance/pull/15), destino `feat/fundacao-financeira`, sem merge. A PR #14 anterior não representa a versão cloud publicada. Documentos de Preview/local e validações anteriores são históricos; o handoff mais recente prevalece.

- [Publicação Production, banco definitivo e rotação](../publicacao-rag-producao-2026-11-16.md)
- [Retomada e pendências](handoff.md)
- [Recuperação confirmada e resultado da V9 isolada](../validacao-v9-neon.md)
- [Preparar outro computador e concluir a V9](retomada-outro-computador.md)
- [LangChain e RAG, explicados pelo código atual](../langchain-rag-atual.md)
- [Executar e inspecionar exemplos fictícios de RAG](../demo-langchain-rag.md)
- [Laboratório de embeddings e pgvector, com comparação real](../rag-vetorial-local.md)
- [Revisão funcional, privacidade e rollback](../revisao-ia-producao.md)
- [Ferramentas Neon instaladas](../neon-agent-setup.md)
- [Validação local](../validacao-local.md)
- [Verificação de segredos](../verificacao-seguranca-publicacao.md)

Use `scripts/enter-dev.ps1` para carregar o Java/Maven locais antes dos testes. A demonstração local usa dados fictícios; não substitua sua conexão por dados de produção.
