# Retomada da publicação da IA

Atualizado em 13/11/2026.

## Estado confirmado

- Branch `codex/ia-pronta-producao`; fonte revisada no commit `9ffc356f054fe2ec6b817ed30ead9c35b4f1b304`.
- Usuário autorizou Python e Java em produção, incluindo V9, com backup confirmado antes da migração. Depois autorizou envio ao GitHub após a publicação e a revisão de segredos. Não presumir autorização para merge na branch principal.
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
