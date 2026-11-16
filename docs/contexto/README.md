# Contexto do Java Finance

Atualizado em 16/11/2026. Java 21 e Spring Boot 3.5.16, React/Vite e PostgreSQL no Neon. Java/React usam container na Vercel; o serviço Python FastAPI/LangChain é separado e usa Groq. Preserve essa arquitetura.

Estado: Python da IA publicado e testado em produção; Java/V9 aguardam confirmação do backup do banco correto. A aplicação Java atual e os saldos foram preservados.

O projeto Neon correto agora está acessível e foi identificado em 16/11; recuperação ainda não confirmada. Na branch `codex/rag-vetorial-local`, o laboratório pgvector foi integrado à tela local com dados fictícios. Ler o handoff antes de publicar: esta integração não está em produção.

- [Retomada e pendências](handoff.md)
- [Preparar outro computador e concluir a V9](retomada-outro-computador.md)
- [LangChain e RAG, explicados pelo código atual](../langchain-rag-atual.md)
- [Executar e inspecionar exemplos fictícios de RAG](../demo-langchain-rag.md)
- [Laboratório de embeddings e pgvector, com comparação real](../rag-vetorial-local.md)
- [Revisão funcional, privacidade e rollback](../revisao-ia-producao.md)
- [Ferramentas Neon instaladas](../neon-agent-setup.md)
- [Validação local](../validacao-local.md)
- [Verificação de segredos](../verificacao-seguranca-publicacao.md)

Use `scripts/enter-dev.ps1` para carregar o Java/Maven locais antes dos testes. A demonstração local usa dados fictícios; não substitua sua conexão por dados de produção.
