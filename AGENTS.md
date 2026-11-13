# Contexto obrigatório do Java Finance

Antes de trabalhar, leia `docs/contexto/README.md` e `docs/contexto/handoff.md`.
Para retomar em outro computador, use `docs/contexto/retomada-outro-computador.md`.
O fluxo real da IA está explicado em `docs/langchain-rag-atual.md`.

## Pendência de produção

Em 13/11/2026, somente o Python da IA foi publicado. Java/V9 aguardam confirmação
do backup do banco correto. Não considerar a integração completa só porque o
Python responde ou porque o código está no GitHub. Revalidar esse estado antes
de um futuro deploy.

- Preservar Java, JPA/JDBC, Flyway e Pluggy v2. Prisma não faz parte da aplicação.
- Antes da V9: identificar o banco correto, confirmar recuperação e testar em
  branch isolada. Iniciar Java com a conexão real pode aplicar Flyway mesmo em
  deployment sem promover domínio.
- Categorias são sugestões; salvar exige ação explícita do usuário.
- Identificação de transferências entre contas próprias também exige confirmação.
- Não importar lançamentos, criar postings ou alterar saldos como efeito da IA.
- Contas vinculadas usam saldo bancário; contas manuais usam saldo calculado.
- Não executar `scripts/cloud-provision.mjs` como rotina de atualização: o script
  antigo pode copiar dados e reconfigurar a conexão.
- Não imprimir nem versionar segredos, `.tools`, `.env`, dumps, sessões ou DPAPI.
- Não fazer merge na branch padrão ou novos deploys apenas por estes documentos;
  seguir o escopo autorizado na conversa atual.
