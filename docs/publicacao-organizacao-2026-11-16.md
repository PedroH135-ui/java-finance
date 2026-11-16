# Organização financeira publicada — 16/11/2026

O usuário autorizou publicar Python/Java, confirmar backup e testar migrações em branch isolada antes. Depois esclareceu que **a IA deve vir ativada por padrão**, com opção de desativar por usuário. Escolhas anteriores de desativação e correções humanas são preservadas; nenhum usuário existente foi ativado em massa. Commit funcional `c4135e9`, na branch local `codex/organizacao-financeira-local`. Não há autorização nova de push, PR ou merge nesta etapa.

## Recuperação e migrações

- Neon `java-finance`, projeto `twilight-field-61680463`, main `br-red-resonance-b6w00wvg`, schema inicial V13. A identidade coincide com o projeto Vercel previamente confirmado; o segredo sensível DB_URL não foi extraído/impresso na revalidação atual.
- A tentativa de criar nova recuperação por branch foi recusada pelo limite Free. Nenhuma branch anterior foi excluída e nenhum plano foi alterado.
- Backup lógico completo V13 criptografado com DPAPI, apenas em `.tools/organization-production`, sem dump em texto ou segredo versionado. Decriptação e SHA conferidos. DPAPI depende do usuário Windows desta máquina; não copiar como se fosse um backup portátil.
- Restauração real do mesmo arquivo em dois bancos novos, `organization_recovery_20261116` e `organization_verify_20261116`, na branch isolada existente `br-round-hall-b668ttqy` (`verify-cloud-v13-20261116`). Nenhum banco antigo foi sobrescrito. Hashes financeiros, categorias, preferências, correções, pares e usuários conferidos com a origem. Recuperação V13 configurada como read-only por padrão; banco de verificação separado.
- Expiração removida somente dessa branch para conservar a recuperação. Outras recuperações e a branch vetorial definitiva foram preservadas. Não excluir essa branch como “teste antigo”: agora contém a recuperação desta publicação.
- Flyway11.20.3: V13 → V15, duas migrações, ~4,1s, validação aprovada e repetição com zero migrações. PostgreSQL18.7 sem aviso de incompatibilidade. Todas as constraints válidas, zero naturezas/metas criadas pela migração e todos os fingerprints financeiros/preferências preservados.
- O Git exporta LF e arquivos locais tinham CRLF. Confirmada igualdade de conteúdo de todas as 15 migrações e executada validação/repetição adicional com os **bytes exatos exportados**; sem `repair` ou edição de checksum. Manifesto SHA256 privado guarda o pacote efetivamente publicado.

## Código, testes e configuração

- IA ativa no cadastro e primeiro vínculo sem preferência anterior. Opt-out salvo não é sobrescrito. Aviso no cadastro informa processamento de descrições na nuvem pela Groq. Cadastro em produção continua com a configuração anterior; esta entrega não autoriza liberá-lo.
- Funcionalidades e limites: `organizacao-financeira-local.md`. Categorias automáticas corrigíveis; natureza financeira manual; investimentos informativos; planejador guiado sem LLM e sem movimentação de dinheiro.
- Repetidos após o novo padrão: Java41unit+83IT aprovados, frontend lint/três testes/build aprovados. Validações anteriores: Python46, laboratório vetorial real10 e E2E local completo/repetido aprovados. Aviso de bundle grande permanece.
- Varredura final: Gitleaks nas fontes/JAR/pacotes sem achados; 12 valores protegidos comparados em memória com 468 arquivos/recursos do release, zero correspondências. Backup, segredos, sessões e logs permanecem ignorados.
- O comando oficial `vercel curl` criou automaticamente um bypass de automação persistente ao tentar testar o candidato protegido e falhou ao encaminhar um argumento de diretório. Essa chave recém-criada foi identificada por timestamp e revogada imediatamente; a proteção SSO foi conferida e preservada. Não repetir `vercel curl` sem considerar esse efeito da versão65.15.0. Nenhuma regra Trusted Sources foi ampliada. Verificações autenticadas usam os acessos existentes.

## Python — publicado e testado

- Projeto `java-finance-category-ai-lab`, equipe `pedro135he-7663s-projects`.
- Deployment `dpl_CQ2R7PSbVpFA2219vPnntCRZi6JW`, URL imutável `https://java-finance-category-ai-nhhsbreh9-pedro135he-7663s-projects.vercel.app`. Domínio estável promovido: `https://java-finance-category-ai-lab.vercel.app`.
- Saúde UP, modo production, PGVECTOR384/Groq. Planejamento autenticado aprovado e anônimo401. Dados fictícios: PIX PERSON→RULE, histórico→HISTORY, dois exemplos→RAG (todos sem LLM); UNKNOWN→NONE; empresa sem contexto→LLM com uma tentativa real. Vetores fictícios removidos, sem acesso ao banco financeiro. Planejador3000/reserva600/6meses interpretado sem LLM.
- Rollback Python anterior: `dpl_5mmPrfrZkJmuKDXTnUbC3nebHGUV`, que já usava a chave atual. Não promover deployments antigos que carregavam a chave revogada.

## Java — publicado e verificado

- Projeto `java-finance`, pacote do commit exato por `git archive`, sem arquivos privados/configuração local.
- Deployment Production `dpl_HzvgiTtJqCrbY2RRmZg2iNa9ys58`, URL imutável `https://java-finance-enlk6axmd-pedro135he-7663s-projects.vercel.app`. READY, saúde UP observada no candidato pelo navegador autenticado, sem novo bypass. V15 e hashes conferidos enquanto o domínio ainda atendia a versão anterior. Depois foi promovido para [Java Finance](https://java-finance.vercel.app).
- Smoke público aprovado: saúde UP, cadastro continua desabilitado como antes, APIs privadas novas401 e POST sem sessão/CSRF403. Bundle contém módulos e aviso de IA ativada no cadastro.
- Navegador autenticado: a preferência já ativa continuou ligada em Configurações; o controle saiu de Movimentações, o aviso inferior foi preservado e o filtro Sem categoria está disponível. Aplicação RDB mostrou contexto público e botão bloqueado até confirmação. Nenhuma confirmação foi feita. Investimentos mostrou identificações pendentes e posições informativas.
- Planejador real Java→Python: teste explicitamente fictício3000, depois6meses e reserva600, gerou400/mês. Botão de confirmação permaneceu disponível, não foi clicado; nenhuma meta foi salva. Conversa temporária limpa por reload. Tela Metas deixada aberta para o usuário. Captura só em `.tools`, não no Git.
- Gate final após promoção e navegação: V15, constraints válidas, hashes financeiros, saldos calculados/bancários, categorias, preferências, correções, pares e usuários iguais ao backup. Zero naturezas/metas criadas; nenhum movement/posting, importação ou atualização bancária foi executado pelos testes.
- Variáveis/segredos (IDs, tipos, targets, updatedAt), framework e vínculo Git dos dois projetos conferidos e preservados. Não houve push, PR, merge ou upgrade de plano.
- Conector de logs retornou403; fallback CLI autenticada consultou error/fatal, ambos deployments, janela15min encerrada por volta de19h16 (São Paulo): zero registros retornados, sem paginação ou intervalos excluídos. É uma observação pontual de registros da CLI, não garantia contínua nem equivalência a todos os eventos de observabilidade. Mensagens brutas não foram persistidas.
- Java anterior/rollback: `dpl_8PAoHDgMV7uwXmwCFHQMj5kWSHZV`. V14/V15 são aditivas e podem permanecer se o código voltar. Relatórios do código anterior voltam à regra antiga de consumo; isso não é alteração de saldo. Não fazer downgrade destrutivo ou apagar posições, metas, categorias e confirmações. Recuperação integral depende de avaliar alterações posteriores e autorização, não restaurar banco em massa automaticamente.

## Limites que permanecem

- Registro antigo sem tipo de contraparte continua UNKNOWN até uma leitura bancária normal preencher o metadado; o deploy não adivinha pessoa/empresa pelo nome nem força sincronização. Categorias padrão para contas antigas entram na próxima leitura bem-sucedida. Não acionamos leitura real neste smoke.
- Alteração de natureza depende de confirmação manual; investimentos ainda são posições informadas, sem valoração bancária ou cálculo de rentabilidade. Planejador é guiado, não um chatbot geral nem compromisso de investimento.
- Cadastro de produção está desabilitado. O novo padrão no cadastro foi testado localmente; não criamos usuário real nem mudamos a autenticação para provar isso. Não reativar quem desligou e não inserir preferências em massa para usuários antigos sem uma decisão separada.
- Backup criptografado não acompanha o clone; recuperação restaurada permanece no banco isolado read-only e sem expiração. Não remover essa branch, reconfigurar a produção para ela ou chamar `cloud-provision.mjs` como atualização.
