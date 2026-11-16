# Publicação do RAG em produção — 16/11/2026

Publicação autorizada pelo usuário após os testes em Preview. Java e Python estão READY nos domínios estáveis. Código funcional base `37334ad`; correção do pool e helper de rotação `ce2401f`, na branch local `codex/publica-rag-cloud`. Nenhum push, PR novo ou merge foi feito nesta etapa.

| Serviço | Deployment ativo | Endereço |
|---|---|---|
| Java 21 / Spring Boot / React, container | `dpl_FpjV78wBJF4PWoUWWDL4N1nir23e` | [Java Finance](https://java-finance.vercel.app) |
| Python / FastAPI / LangChain / Groq | `dpl_5mmPrfrZkJmuKDXTnUbC3nebHGUV` | [Saúde da IA](https://java-finance-category-ai-lab.vercel.app/health) |

## Estado funcional e limites

- V9–V13 aplicadas; treze migrações bem-sucedidas. Java/JPA/JDBC/Flyway/Pluggy v2 preservados, sem Prisma.
- Capacidade cloud ligada; **ativação individual desligada por padrão**. Ao terminar, zero usuários ativados e zero registros financeiros reais categorizados automaticamente.
- O usuário pode ativar em Movimentações e corrigir/remover depois. Correções duráveis vencem memória/modelo, inclusive se feitas em outro registro da mesma descrição enquanto a IA respondia.
- IA não importa lançamentos, cria movement/posting, identifica transferências automaticamente ou altera saldos. Transferências próprias continuam explícitas. Contas vinculadas continuam usando saldo bancário; manuais, saldo calculado.
- Prioridade: correção explícita → histórico exato → memória válida da IA → RAG/regras → LLM quando necessário. Previsão AUTO não se transforma em histórico humano confirmado. PIX sem finalidade abstém sem LLM.
- Embeddings MiniLM multilíngue, 384 dimensões, pesos/revisão/SHA fixos baixados no build. Cada descrição confirmada é um documento curto; não vetorizamos saldos nem transações financeiras completas.
- Vercel Hobby, Neon Free e Groq continuam sem contratação de plano pago. Orçamento compartilhado por banco vetorial: seis chamadas/minuto e trinta/dia; sujeito também às cotas dos provedores. Java reutiliza decisões exatas sem HTTP/LLM. Fila durável funciona sem computador ou navegador ligados.

## Banco vetorial definitivo

Projeto Neon `java-finance`, branch dedicada **`category-rag-production`**, sem expiração, criada somente com schema; banco `category_vectors`. Nenhuma linha financeira real ou fixture de Preview foi copiada.

- Papel de runtime exclusivo `category_rag_runtime`, sem superuser, createdb, createrole ou bypass RLS.
- Python recebeu somente a conexão desse banco, nunca a conexão financeira ou credenciais Pluggy.
- TLS verifica certificado e hostname; endpoint pooled. RLS obrigatório separa usuário/direção; snapshots atualizados não misturam correções concorrentes.
- Runtime pode ler/inserir/remover exemplos e atualizar orçamento, sem DDL. Tentativas de conectar ao banco financeiro e criar tabela foram rejeitadas.
- Schema vetorial 1 com checksum registrado; pgvector **0.8.7**. Fixtures da validação direta foram revertidas na transação; vetores sintéticos do smoke de produção foram removidos por UUID próprio do teste.

## Recuperação e preservação financeira

Ponto recente: **`recovery-category-release-20261116` / `br-plain-forest-b6qjh804`**, parent main, criado `2026-11-16T17:53:38Z`, LSN `0/24389A8`, READY e sem expiração. Foi lido e comparado por hashes com a produção. Compute de verificação suspenso depois. É uma recuperação dentro do mesmo projeto Neon, não um dump independente.

A cópia isolada V8→V13 anterior foi revalidada: mesmos hashes financeiros dos dados atuais e mesmos hashes de migração do JAR. O plano Free impediu criar/restaurar outra branch adicional; essas tentativas não mudaram a branch de teste nem a produção. Não houve upgrade de plano nem exclusão de recuperações para contornar o limite.

Após inicializar o deployment preparado e depois de promover:

- schema 13 e treze migrações bem-sucedidas;
- hashes de account, movement, posting, bank_entry, bank_link, category, financial_record e saldos calculados preservados;
- zero usuários habilitados, zero categorias AUTO em registros reais e zero pares de transferência criados;
- IDs/timestamps dos segredos existentes DB, Pluggy, cron e cadastro preservados.

A comparação recebeu somente hashes calculados no PostgreSQL. Não foram exportados valores, descrições, hashes de senha, sessões ou linhas financeiras para um dump.

## Rotação Groq

O usuário gerou a nova chave no painel oficial e digitou em entrada oculta no helper `scripts/configure-production-groq.ps1`. O valor foi salvo com DPAPI fora do Git, configurado apenas no servidor Python em Production/Preview e no armazenamento protegido local para próximos reinícios.

Nova chave validada no serviço e na API oficial. O usuário revogou a antiga **Finanças**; a API confirmou antiga 401 e nova 200. A chave **Koinonia** não foi alterada. A rotação não cria outro LLM nem nova contratação. Não registrar valores de chave em código, logs, PR ou chat.

Serviços locais que estavam abertos antes da rotação podem precisar reiniciar para carregar a chave protegida nova. Isso não afeta a operação cloud.

## Validações e correção encontrada no rollout

- Base: 113 testes Java, 45 Python, lint/três testes frontend, build e dois E2E cloud; histórico, memória, correções, isolamento e invariantes no [relatório de Preview](categorias-cloud-preview.md).
- O primeiro Python com endpoint pooled retornou 503. A reprodução exata mostrou que PgBouncer rejeitava `options` no startup. Corrigido para `SET LOCAL statement_timeout='15s'` dentro da transação, mantendo TLS e isolamento. Dez testes API/fila/RLS/orçamento passaram pelo endpoint pooled fictício real; conexão definitiva e timeout também verificados.
- Durante o rebuild, o Python anterior foi restaurado temporariamente. Depois, a versão corrigida foi promovida e passou em Production: assinatura RULE sem LLM; delivery com exemplo do RAG e uma chamada LLM; PIX NONE sem LLM. Somente textos fictícios; sem dados reais do banco.
- Preview também passou depois da revogação da chave antiga, inclusive uma chamada real Groq. O banco fictício e seus tokens continuaram separados.
- Java: health 200/UP, página principal 200, cadastro continua desligado, API privada sem sessão retorna 401.
- Fila Production: mensagem com UUID fictício inexistente foi aceita (202); callback para `/api/banking/jobs/categories` respondeu 200 no deployment Java novo. Nenhum usuário real foi habilitado; o callback não criou registros financeiros.
- `-XX:TieredStopAtLevel=1` aplicado a Production após teste em Preview, preservando os flags de memória anteriores, para reduzir cold start. Pode reduzir otimização sustentada; medir antes de alterar novamente.
- Conector Vercel continua retornando 404 para esses projetos; publicação e observação usaram a CLI autenticada. Nenhum bypass permanente ou regra adicional de acesso Production foi criado.

## Rotina futura, segurança e rollback

Varredura final: Gitleaks não encontrou segredos nas fontes públicas. Doze valores secretos protegidos foram comparados apenas em memória com 409 arquivos/recursos públicos e do JAR, sem correspondências. A checagem de arquivos versionados não encontrou `.env`, dumps, DPAPI ou diretórios privados. Esses testes reduzem o risco de exposição; não substituem a revisão de futuros commits.

Em outro computador, autentique as CLIs oficiais. Secrets já existentes na Vercel são preservados por um deploy de fontes; não é necessário extraí-los, copiar arquivos DPAPI ou executar provisionamento para atualizar código. O armazenamento DPAPI deste computador não funciona como backup portável.

Não executar `scripts/cloud-provision.mjs`, não recriar banco/roles em uma atualização e não apontar Preview para o banco real. A branch fictícia de Preview expira em 23/11/2026 às 18:00 UTC; a vetorial definitiva não tem expiração. Nenhum dado fictício foi migrado para a base financeira principal.

Kill switch: desligar `CATEGORY_AI_AUTOMATIC_CLOUD_ENABLED` e `CATEGORY_AI_ENABLED` em Production e redeployar uma versão compatível com V13. Desligar a preferência individual também impede respostas em andamento de aplicar categoria. Preservar migrações e escolhas manuais; não restaurar banco sobre operações posteriores sem análise.

**Não promover simplesmente um deployment Python antigo após a rotação:** ele pode conter a chave revogada no snapshot de ambiente. Se precisar voltar ao serviço lexical, redeployar suas fontes com a chave nova e desligar a automação Java enquanto a fila estiver indisponível.

Os hashes e detalhes operacionais ficam somente em `.tools/cloud-category-production`. Código e documentação públicos não contêm credenciais. Revalidar recuperação, testes e estado real antes de uma publicação futura; este relatório não autoriza novos deploys ou merges.
