# Contexto do Java Finance

Atualizado em 16/11/2026. Java 21 e Spring Boot 3.5.16, React/Vite e PostgreSQL no Neon. Java/React usam container na Vercel; o serviço Python FastAPI/LangChain é separado e usa Groq. Preserve essa arquitetura.

Estado: Python da IA publicado; recuperação Neon confirmada e V9 validada em branch isolada em 16/11. Produção Java continua na V8, sem alterações de saldos. O usuário pediu ver o resultado antes de publicar.

Flyway local atualizado para **11.20.3**; novo teste V8 → V9 no PostgreSQL 18.7 passou sem aviso de compatibilidade, com invariantes preservados. Essa atualização ainda não foi publicada.

O projeto Neon correto está acessível. A branch de recuperação foi preservada sem compute; a cópia de teste migrou com invariantes preservados. Na branch `codex/rag-vetorial-local`, o laboratório pgvector foi integrado à tela local com dados fictícios. Ler o handoff antes de publicar: esta integração não está em produção.

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
