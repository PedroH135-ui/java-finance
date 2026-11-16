# Categorias padrão publicadas — 16/11/2026

O usuário autorizou publicar esta alteração e depois enviar a branch/abrir PR, mantendo opt-in individual, prioridade humana e saldos. Código funcional **`e9137ce`**, branch `codex/categorias-padrao`, baseada no merge `647dbc4` da PR #15. Documentação posterior não muda o pacote funcional publicado. Não houve novo merge nesta etapa.

## Publicação e escopo

| Item | Resultado |
|---|---|
| Projeto Vercel | `java-finance`, equipe `pedro135he-7663s-projects` |
| Deployment Production | `dpl_8PAoHDgMV7uwXmwCFHQMj5kWSHZV` |
| URL imutável | `https://java-finance-ayrw10aby-pedro135he-7663s-projects.vercel.app` |
| Domínio principal | [Java Finance — Categorias](https://java-finance.vercel.app/#/categories) |
| Framework | Container Java 21/Spring Boot/React, READY e saúde UP |
| Banco | Schema V13 preservado, nenhuma migração nova |
| Python | Deployment `dpl_5mmPrfrZkJmuKDXTnUbC3nebHGUV` preservado |

Somente Java/React publicados. Fontes exportadas do commit exato por `git archive`, sem arquivos privados, dependências locais ou build de demonstração. Deployment Production preparado com `--skip-domain`, saúde verificada antes da promoção e comparação de hashes aprovada. Domínio promovido depois. O conector Vercel segue retornando 404 para este projeto; foi usada a CLI autenticada, com projeto/equipe conferidos. Nenhum vínculo Git, segredo, preferência, proteção ou configuração de deploy foi alterado.

O botão **Adicionar categorias padrão** apareceu na sessão real do usuário. Não foi clicado durante a validação de produção: nenhuma categoria real foi adicionada e nenhuma preferência foi ativada. Usuários existentes continuam precisando clicar; não existe preenchimento em massa ou ao fazer login. Novos cadastros recebem quatorze categorias na mesma transação do cadastro, quando este estiver habilitado. **O cadastro de produção continua desabilitado**, como antes.

Detalhes funcionais, lista de categorias e limites de normalização em [categorias padrão](categorias-padrao.md). A IA só usa as categorias após ativação por usuário, preservando correções. Adicionar padrões não chama LLM, cria jobs, aplica categorias a registros, importa movimentos ou altera saldos.

## Evidências e testes

- 117 testes Java aprovados: 40 unitários + 77 integrações, com PostgreSQL descartável local. Os quatro novos testes verificam cadastro, natureza/nome normalizado, arquivadas, concorrência, sessão/CSRF, isolamento e preservação financeira.
- Frontend: lint, três testes unitários e build aprovados. Aviso já existente de bundle maior que 500 kB permanece.
- Três cenários de navegador com API fictícia local e um E2E com Java/PostgreSQL locais aprovados; botão repetido, categorias anteriores intactas, contas/relatório/opt-in iguais antes/depois. Não foram usados dados reais nesses testes.
- Segredos: Gitleaks nas fontes sem achados; doze valores protegidos comparados só em memória com 432 arquivos/recursos e JAR local atual, zero correspondências. Pacote exato de fontes novamente aprovado no Gitleaks antes do deploy. `.tools`, `.env`, dumps, sessões e DPAPI ficam fora do Git.
- Produção: saúde UP, página 200, botão presente no bundle e na tela autenticada, categorias sem sessão retornam 401 e POST de padrões sem sessão/CSRF retorna 403. Nenhuma escrita autenticada foi usada como smoke.
- Comparação por hashes gerados dentro do PostgreSQL confirmou dados financeiros, saldos, categorias, preferências da IA, correções e pares de transferência preservados após preparação e promoção. Só hashes e metadados foram guardados, sem exportar linhas ou valores financeiros.
- IDs/timestamps/tipos/targets das variáveis existentes de Java e Python preservados; framework e vínculo Git preservados. Python não foi republicado.
- Consulta de runtime do deployment novo, janela de dez minutos, filtro error/fatal: zero registros retornados. Apenas contagens sanitizadas persistidas; sem logs brutos. Isso cobre o intervalo observado, não uma garantia contínua de ausência de falhas.
- Consulta do conector encontrou zero drains de logs para este projeto. A verificação desta etapa foi pontual; não foi configurado monitoramento externo novo.

## Rollback e continuidade

Rollback somente do Java para **`dpl_FpjV78wBJF4PWoUWWDL4N1nir23e`**, a versão anterior com IA cloud/V13 e os mesmos segredos, se necessário e autorizado. O Python atual deve permanecer publicado; não promover seus deployments antigos que usam chave Groq revogada.

Não há migração para desfazer. Categorias eventualmente criadas por usuários permanecem válidas e não devem ser apagadas em massa. Voltar o código remove a criação padrão e o botão; arquivar pela interface permite desativar uma categoria de forma reversível. Preservar correções, opt-in individual, movimentos, postings e saldos. Não usar `scripts/cloud-provision.mjs` para rollback ou atualização.

Próxima IA/computador: começar por `AGENTS.md`, `docs/contexto/README.md`, `docs/contexto/handoff.md` e este relatório. Credenciais já estão configuradas no servidor e não acompanham o clone. Consultar o CI da nova PR para resultado remoto, sem confundir os testes locais acima com Actions. Merge, novas publicações e ativação de usuários dependem da autorização na conversa atual.
