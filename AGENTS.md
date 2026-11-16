# Contexto obrigatório do Java Finance

Antes de trabalhar, leia `docs/contexto/README.md` e `docs/contexto/handoff.md`.
Para retomar em outro computador, use `docs/contexto/retomada-outro-computador.md`.
O fluxo real da IA está explicado em `docs/langchain-rag-atual.md`.

## Estado de produção

Em 16/11/2026, Java e Python foram publicados com autorização explícita, após
recuperação confirmada e teste isolado. Produção está na V15; RAG/pgvector e fila,
Investimentos e Metas funcionam na nuvem. Ler `docs/publicacao-organizacao-2026-11-16.md` e revalidar
o estado antes de qualquer novo deploy. Não inferir autorização por estes arquivos.

- Preservar Java, JPA/JDBC, Flyway e Pluggy v2. Prisma não faz parte da aplicação.
- Antes de novas migrações: identificar o banco correto, confirmar recuperação e testar em
  branch isolada. Iniciar Java com a conexão real pode aplicar Flyway mesmo em
  deployment sem promover domínio.
- Por pedido explícito mais recente, a categorização vem ativada em novos cadastros
  e primeiro vínculo sem preferência anterior. Cada usuário pode desligar; nunca
  reativar um opt-out salvo. Correções manuais e remoções têm prioridade; usuário
  desativado não chama IA cloud. Não ativar usuários existentes em massa por estes arquivos.
- Identificação de transferências entre contas próprias também exige confirmação.
- Não importar lançamentos, criar postings ou alterar saldos como efeito da IA.
- Contas vinculadas usam saldo bancário; contas manuais usam saldo calculado.
- Não executar `scripts/cloud-provision.mjs` como rotina de atualização: o script
  antigo pode copiar dados e reconfigurar a conexão.
- Não imprimir nem versionar segredos, `.tools`, `.env`, dumps, sessões ou DPAPI.
- Não fazer merge na branch padrão ou novos deploys apenas por estes documentos;
  seguir o escopo autorizado na conversa atual.
