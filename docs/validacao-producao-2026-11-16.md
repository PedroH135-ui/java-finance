# Validação antes de produção — 16/11/2026

**Resultado: a base técnica passou nas verificações abaixo; a categorização automática com RAG vetorial ainda não está pronta para ativação na nuvem.** Não publicar apenas mudando flags ou removendo os guards do laboratório. Não houve deploy, promoção, merge ou mudança de configuração da Vercel nesta etapa.

Escopo: revisar a versão local, testar o pacote real e as migrações, conferir segredos e incorporar o pedido do usuário para evitar LLM em descrições repetidas. Branch local `codex/categorias-auto-local`, baseada em `d148098`; o trabalho posterior é registrado no commit de validação. A PR #14 permanece com a entrega anterior; esta branch ainda não foi enviada.

## Economia de LLM implementada e testada

O Java agora tenta, nesta ordem:

1. Classificação humana exata e consistente do perfil; conflito humano leva à abstenção.
2. **MEMORY**: decisão original do LLM para a mesma descrição, proprietário, natureza e categoria ativa. A chave preserva números, acentos e pontuação; só uniformiza caixa e espaços. Nomes/identificadores diferentes não são agrupados silenciosamente.
3. Recuperação RAG, histórico forte e regras já existentes no Python.
4. LLM somente quando esses passos não resolvem; ausência de evidência ou falha mantém a categoria vazia.

A memória usa registros persistidos em `bank_entry`, não o cache de cinco minutos do Python. Só uma decisão original ainda válida com método LLM pode ser reutilizada: registros ocultos, pendentes, associados, transferências, categorias arquivadas e cópias MEMORY não viram origem. Correções humanas exatas têm prioridade. Conflitos não geram uma categoria forçada. A saída reutilizada permanece AUTO/LOW, sem ser promovida a exemplo humano confirmado do RAG.

Isso reduz chamadas para repetições exatas. Descrições novas/semelhantes ainda podem precisar de LLM quando o RAG não tiver evidência suficiente. Não há promessa de nunca mais consultar o modelo nem de economia percentual geral. Um erro original também pode se repetir até ser corrigido; a correção manual permanece disponível e tem prioridade nas próximas classificações equivalentes.

V11 apenas amplia o domínio de `category_auto_method` para incluir MEMORY; V9 e V10 anteriores não foram editadas. Não altera valores, saldos, postings ou escolhas de categoria.

## Evidências executadas

| Verificação | Resultado |
|---|---|
| Java completo | **104 testes**: 39 unitários + 65 integração; zero falhas/erros/ignorados |
| Revisão final dos testes específicos | 18 unitários + 9 integração, sem falhas; são subconjunto da suíte, não 27 casos adicionais |
| Python principal | **35 passaram** |
| Python embeddings/pgvector | **10 passaram** no banco vetorial fictício |
| Frontend | Lint, três testes e build passaram |
| E2E automático local | **Dois passaram**: correção/remoção/reativação e primeira decisão LLM → repetição MEMORY |
| Reutilização sem rede | Teste com duas instâncias novas de `CategoryAdvisor` registrou **zero chamadas HTTP**; não dependeu do cache do provedor |
| Imagem de produção | Dockerfile real compilou sem flags fictícias de frontend |
| Neon isolado | V8 → V11 aplicou exatamente V9/V10/V11; onze migrações válidas; repetição sem SQL novo |
| Runtime da imagem | Saúde 200 e endpoint protegido 401, na porta exclusivamente local 8086, contra a cópia Neon |
| Python já publicado | Saúde UP/production/Groq, sem token 401, regra fictícia, geração fictícia e corpo excessivo 413 passaram |
| npm audit | Zero vulnerabilidades reportadas |
| Segredos conhecidos | Cinco valores protegidos comparados em memória contra 379 arquivos/recursos públicos e do JAR: zero correspondências |

Os E2E preservaram saldo, fluxo, totais e quantidade de movimentos durante as classificações e correções. O teste de repetição confirmou método MEMORY e origem AUTO/LOW. O teste de correção confirmou que abrir/consultar não envia POST de categoria e que a escolha manual sobrevive aos ciclos do worker. Nenhum dado real foi enviado ao LLM; o smoke remoto usou somente descrições fictícias.

### Pacote exato verificado

- Imagem local: `java-finance:readiness-local`.
- Digest: `sha256:3348f88a463909049d98165d59175c44f1370f6d3bb86df569ef80c75ffbe954`.
- SHA-256 do JAR extraído dessa imagem: `f7bdf523844ba31e66932d539ff2a1c27c07bf6d022aca90609161af0170dc6d`.
- Flyway core e PostgreSQL: **11.20.3**, alinhados; sem aviso para PostgreSQL 18.7.
- Migrações usadas no teste Neon foram extraídas do próprio JAR da imagem, não de um pacote antigo ou de credenciais de produção.

O smoke do container usou perfil de produção **com IA, automação, Pluggy e cadastros desligados**, conexão TLS verificada da branch de teste e nenhum token Groq/Pluggy. Iniciou, validou schema/JPA, respondeu saúde e preservou o snapshot financeiro. O container temporário foi removido. Esse resultado não comprova automação cloud ativa; justamente esse comportamento permanece bloqueado.

Uma primeira tentativa do runner usou o digest de configuração em vez do digest de imagem do Docker; o runner foi corrigido e o smoke posterior passou. Não houve correção de código da aplicação nem mudança de produção para resolver isso.

## Neon e produção revalidados

| Item | Estado observado |
|---|---|
| Projeto | `java-finance` / `twilight-field-61680463` |
| Produção | `main` / `br-red-resonance-b6w00wvg`; continua schema **8** |
| Recuperação recente | `recovery-readiness-20261116` / `br-aged-shape-b6n0u7m0`, pronta, sem compute/expiração configurada |
| Origem da recuperação | main, LSN `0/23AA720`, criada `2026-11-16T15:20:51Z` |
| Cópia de teste | `verify-readiness-v11-20261116` / `br-lively-sky-b6nysvb7`, filha da recuperação |
| Migração medida | Três migrações, **3.504 ms** no `migrate`, onze validadas, repetição zero |
| Encerramento | Compute `ep-jolly-cloud-b6x1zg43` suspenso (idle); cópia expira `2026-11-18T18:00:00Z` |
| Java na Vercel | Deployment `dpl_5pMaPchaokz49sJSTQSrCDbmzxb7` preservado; saúde 200; sem vínculo Git |

Contas, movimentos, postings, registros bancários, vínculos, categorias, saldos e view financeira foram comparados por contagens e fingerprints privados antes/depois na cópia. Tudo preservado; novos campos de categoria/transferência vazios, pares vazios e constraints válidas. As leituras na produção usaram transações explicitamente read-only e comprovaram V8 sem estrutura V9 no intervalo observado. Os metadados das variáveis DB da Vercel não mudaram; seus valores não foram exibidos.

É uma recuperação **dentro do mesmo projeto Neon**, não dump externo. Os pontos anteriores também foram preservados. Antes de um futuro deploy, revalidar e criar recuperação recente se houver operações novas. Não restaurar sobre a produção ativa por rotina.

## Bloqueios para a funcionalidade solicitada em PROD

| Bloqueio e evidência | O que precisa mudar | Teste de aceite antes de publicar |
|---|---|---|
| `AutomaticCategoryScope.java` exige banco 55434, perfil `demo@javafinance.local`, modo local e ausência de `VERCEL_ENV`; ativar esse flag em produção falha na inicialização | Criar modo de produção com ativação por usuário autorizado, isolamento e preferência persistida; preservar o guard do laboratório | Dois perfis: apenas o habilitado processa registros; sessão/CSRF/proprietário não podem ser contornados; desligar impede novas classificações |
| `ui_service.py` recusa Vercel; encoder usa `.tools/vector-rag/model`; `store.py` conecta em 127.0.0.1:55435; `vector-lab` fica fora do deploy | Hospedar encoder/recuperação em nuvem e definir acesso ao índice com escopo por perfil. Não copiar credenciais financeiras ao Python nem usar um servidor local como produção | Testar sem nenhum serviço local ligado, com autenticação de serviço, isolamento, mudança/remoção de categorias e latência/cold start medidos |
| `AutomaticCategoryService` usa `@Scheduled` e `AtomicBoolean` em memória; o cron versionado é de sincronização bancária diária, não uma fila de categorias | Implementar acionamento cloud e retomada persistente, com leases/idempotência e lotes limitados. Não presumir uma JVM continuamente ativa | Reiniciar no meio de um lote e executar duas instâncias: retomada correta, sem sobrescrever edição humana e sem multiplicar chamadas ao LLM |
| Python possui cache/cotas por instância; memória exata reduz chamadas, mas não é limite global | Definir orçamento/backoff global e política de invalidação/revisão das decisões reutilizadas | Concorrência e 429/timeout: respeitar limite, manter escolha manual e não forçar categoria |
| Java→Python não foi ativado na Vercel nesta etapa; metadados consultados não mostraram variáveis `CATEGORY_AI_*` no Java | Configurar URL/token de serviço após implementação/revisão do modo de produção e autorização do release | Smoke Java→Python no deployment isolado com contexto fictício, depois conferência controlada sem aplicar categoria financeira real |
| A chave Groq foi compartilhada no chat anteriormente; rotação não foi comprovada nesta auditoria | Confirmar substituição por chave nova antes da ativação final, sem valores no chat/Git | Chave anterior revogada e nova configurada apenas nos destinatários autorizados; teste fictício passa |

Esses são bloqueios de conclusão/implantação, não motivo para remover controles de segurança. Nenhum desses serviços/segredos/configurações foi provisionado ou alterado nesta revisão. O funcionamento do Python remoto sozinho não demonstra a integração Java/RAG/automação completa.

## Contexto, rollback e evidências

Evidências privadas: `.tools/production-readiness`, `.tools/local-review/readiness-*.log`, `.tools/readiness-automatic-e2e` e `.tools/security-audit`. Não incluir esses diretórios, dumps, sessões, DPAPI, pesos ou senhas no Git. Runners privados usam credenciais em memória e não acompanham um clone novo.

Uma varredura sem achados não garante ausência absoluta de segredos. O Gitleaks do diff/histórico e a lista de arquivos públicos devem passar antes do commit/push. Esta auditoria não equivale a auditoria independente de todos os binários/base images ou a aprovação de deploy.

Rollback local: reiniciar o launcher sem `-AutomaticCategories`; para desligar o experimento vetorial, usar o launcher lexical. Não apagar migrações V10/V11 já aplicadas nem restaurar backup sobre operações novas. A mudança de memória pode ser desativada por código em revisão separada, preservando os metadados existentes.

Próxima etapa para liberar a automação completa: implementar os itens cloud acima em uma versão isolada, repetir testes funcionais/segurança/financeiros e só então apresentar um release concreto para autorização. Não publicar esta versão como se a automação já estivesse disponível na nuvem.
