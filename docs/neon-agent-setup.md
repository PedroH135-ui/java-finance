# Ferramentas Neon para agentes

Verificação realizada em 13/11/2026, na branch `codex/ia-pronta-producao`.

## Instalação

- Instruções oficiais consultadas: https://neon.com/.well-known/agent-skills/neon/SKILL.md.
- A CLI Neon 9.4.0 já estava disponível no cache npm e autenticada; foi reutilizada, sem instalação global ou mudança no PATH.
- Não havia skills Neon nem servidor MCP Neon configurados para este projeto.
- Instaladas `neon` e `neon-postgres` com `neon skills -s neon -s neon-postgres --agent codex -y`. São as skills pertinentes ao banco existente; não foi adicionado outro provedor de autenticação, IA ou hospedagem.
- Criados `.agents/skills/neon/` e `.agents/skills/neon-postgres/`, com sete arquivos cada, e `skills-lock.json` com origem e hashes.
- Criado `.codex/config.toml` pelo comando `neon mcp --oauth --project --read-only --agent codex -y`. Contém somente o servidor `Neon` e a URL `https://mcp.neon.tech/mcp?readonly=true`, sem chave ou token.

## Verificação e preservação

- `codex mcp list --json` reconhece o novo servidor habilitado. O estado de autenticação é `unknown`; a autorização OAuth do MCP ainda deve ocorrer no primeiro uso. A autenticação existente da CLI não comprova a autenticação do MCP.
- Os servidores anteriores `codex_app`, `cua_repl` e `node_repl` conservaram seus estados.
- Os hashes do `.env` existente e da configuração global `~/.codex/config.toml` permaneceram iguais.
- `neon skills status` informou `outdated` imediatamente após a instalação. Foi tentado `neon skills update -y`; o aviso persistiu. Uma comparação independente com o arquivo ZIP oficial de `neondatabase/agent-skills`, branch `main`, confirmou conteúdo idêntico nos 14 arquivos, normalizando somente CRLF/LF. A causa do aviso da CLI não foi confirmada.
- Downloads, inventário de hashes e evidências de comparação ficaram em `.tools/security-audit/`, já ignorado pelo Git. Nenhum segredo foi incluído nesses novos arquivos de configuração.

## Limites e pendências

Não foram executados `neon init`, `link`, `checkout`, `env pull` ou comandos de alteração de banco. Nenhum projeto, branch, compute ou serviço foi criado. Não houve publicação nem aplicação da V9 nesta etapa.

A listagem da organização autenticada retornou somente `comunia-db`. Ainda é necessário identificar com segurança o projeto do Java Finance antes de vincular o MCP a um ID específico ou confirmar o backup da produção. Por isso, o servidor foi instalado sem vínculo presumido e limitado a leitura; o acesso efetivo depende da conta autorizada no OAuth.

As skills ficam disponíveis no próximo turno. Se as ferramentas MCP não aparecerem na sessão atual, reabra a sessão do projeto para recarregar sua configuração e conclua o consentimento OAuth no fluxo oficial. Não envie credenciais no chat.

Nenhum commit ou push foi realizado nesta etapa. A publicação autorizada anteriormente continua condicionada à confirmação do backup correto antes da V9.
