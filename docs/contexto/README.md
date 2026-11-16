# Contexto do Java Finance

Atualizado em 16/11/2026. Java 21 e Spring Boot 3.5.16, React/Vite e PostgreSQL no Neon. Java/React usam container na Vercel; o serviço Python FastAPI/LangChain é separado e usa Groq. Preserve essa arquitetura.

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
