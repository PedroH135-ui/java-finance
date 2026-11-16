# Categorização na nuvem — candidata testada

Atualização: 16/11/2026. Branch `codex/categorias-cloud-preview`.
Esta etapa não autoriza publicação em Production, merge ou alteração de saldos.
O pedido atual substitui a revisão obrigatória de categorias por automação **opt-in por usuário**, corrigível.
Transferências próprias continuam exigindo confirmação.

## Fluxo implementado

1. Em Movimentações, o usuário ativa a classificação. A preferência começa desligada; sessão, CSRF e versão protegem a mudança.
2. O Java grava um trabalho pendente. Uma sincronização bancária concluída também solicita processamento, apenas para quem ativou.
3. O Python publica somente o UUID do perfil na Vercel Queue. O consumidor privado chama um endpoint autenticado do Java; nenhuma descrição ou valor financeiro vai na mensagem da fila.
4. O Java obtém uma concessão temporária no PostgreSQL e lê até três registros elegíveis. Não segura os locks financeiros durante a chamada de IA.
5. Sua correção explícita persistida para a mesma descrição e direção vence. Não depende dos últimos 100 exemplos: a V13 mantém essa preferência separadamente.
6. Histórico humano exato e decisões originais LLM reutilizáveis evitam HTTP e novas chamadas ao modelo. Decisões MEMORY não viram novas fontes de aprendizado.
7. Quando necessário, o Java envia ao Python somente descrição sanitizada, até 100 categorias e até 100 exemplos humanos do próprio perfil. Sem valores, contas, tokens bancários ou dados de conexão financeira.
8. O encoder multilíngue MiniLM gera 384 dimensões no servidor Python, sem depender do computador pessoal. Os pesos têm revisão e SHA-256 fixados e são baixados somente no build.
9. O retriever LangChain procura até cinco exemplos no pgvector. Usuário, direção, categorias ativas, modelo e snapshot atual limitam a busca. RLS também isola usuários; uma trava por perfil/direção evita misturar snapshots concorrentes.
10. O pipeline LangChain usa histórico suficientemente semelhante ou regras simples antes do Groq. Similaridade vetorial não é convertida em confiança: quando o contexto ainda exige interpretação, o LLM decide entre categorias permitidas ou abstém.
11. Antes de salvar, o Java confere novamente consentimento, versão do registro, categoria ativa, bloqueio manual, elegibilidade e a última correção para a descrição. Uma correção ou desativação durante a chamada vence a resposta atrasada, inclusive uma correção feita em outro registro da mesma descrição.
12. Salva somente metadados de categoria e auditoria. Não cria movement/posting, não importa registros bancários, não confirma transferências e não desconta saldos.

## Recuperação e limites

- V12: preferências e trabalhos persistidos com geração e lease. Entrega duplicada é segura; falha temporária libera lease sem fingir que houve abstinência.
- V13: correções humanas duráveis; migra classificações MANUAL anteriores, priorizando a correção mais recente auditada. Remover uma categoria pausa aquele registro; permitir novamente reutiliza a correção anterior, quando válida.
- A fila retenta falhas. Pendências permanecem no PostgreSQL; nova sincronização, botão de tentar pendentes e passagem diária existente podem reenviar.
- O callback processa lotes curtos. A automação cloud não depende do scheduler local, da aba aberta ou do computador pessoal.
- Orçamento global desta candidata: seis requisições por minuto e trinta por dia ao LLM, contabilizadas no banco vetorial antes de uma chamada real. Cache local de respostas e reutilização Java reduzem consumo; falhas também consomem orçamento reservado.
- Cache de embeddings limitado a 512 textos. Sem LangSmith/tracing remoto. Logs registram correlação, status e duração, sem payload ou exceção bruta.
- Hobby/Free têm cotas, suspensão e cold starts. Não foi contratado plano pago. Esta configuração de baixo volume não promete disponibilidade contínua.

## Ambientes e isolamento

- Banco de teste: branch Neon somente schema, com expiração; nenhum dado ou usuário financeiro real copiado. Dois bancos vazios novos: aplicação fictícia e vetores.
- Python tem papel próprio `category_rag_runtime`, sem acesso ao banco financeiro e sem acesso ao banco fictício do Java. TLS verifica certificado e hostname.
- Java tem papel próprio do banco fictício. Não recebeu credenciais Pluggy.
- Foram autorizadas regras Trusted Sources apenas Preview ↔ Preview. Production mantém conexão, variáveis e domínio próprios.
- O primeiro deployment de projeto novo seria necessariamente Production na Vercel. Por autorização posterior, o teste Java usa somente Preview do projeto existente. O projeto novo de revisão não recebeu deployment; suas credenciais foram substituídas por valores inválidos, flags desligadas e a regra de acesso redundante removida. A exclusão do projeto vazio depende da confirmação humana exigida pela CLI; ele não executa código.
- Pacote cloud Python explícito em `category-ai/cloud-deploy`, entrypoint `cloud_app:app`; preserva o deployment lexical legado até autorização e publicação específicas.

## Resultados observados

- Java: **113 testes**, 40 unitários e 73 de integração, sem falhas. Incluem desativação durante resposta, correção na própria movimentação e em outra da mesma descrição, persistência das preferências, lease vencida, entrega duplicada, tentativa após falha e isolamento.
- Python: **45 testes** em ambientes separados: 35 do serviço legado, sete novos unitários de API/fila e três integrações reais com o Neon fictício. RLS, snapshots concorrentes e reserva global atômica passaram. O teste de orçamento rejeitou quatro das dez reservas concorrentes, preservando seis nos dois contadores.
- Frontend: lint, três testes e build passaram. Dois testes reais Playwright em Preview passaram: fluxo completo e segundo perfil isolado. O segundo perfil ficou desligado, sem registros e recebeu 404 ao acessar ID alheio.
- Fluxo real: ativação → Java → produtor Python → Vercel Queue → callback Java → pgvector/Groq → categoria AUTO. Repetição exata usou MEMORY; correção humana posterior usou HISTORY. O navegador não confirmou categorias automaticamente por POST; somente o servidor classificou.
- Sem navegador: após fechar o cliente HTTP de ativação, um novo mercado fictício foi categorizado pela fila em aproximadamente **6,8 segundos**, como RULE, sem precisar de LLM. Fingerprints de account/movement/posting/bank_link preservados; opt-in desligado ao terminar.
- Saldos e totais financeiros conferidos antes/depois da classificação ficaram iguais. Cópias de registros foram criadas explicitamente **como fixtures de teste**, para comprovar memória e correção; não houve consulta/importação Nubank.
- Migração **V8 → V13** passou na filha isolada da recuperação confirmada, PostgreSQL 18.7/Flyway 11.20.3: cinco migrações em 4.691 ms, validação e repetição zero. Fingerprints financeiros, saldos, contagens e view preservados; sem aviso de compatibilidade. Compute dessa cópia suspenso após o teste. Produção continua V8.
- Comparação de Production: mesmos deployments Java/Python, metadados de variáveis Java inalterados, ambos UP; Python Production continua lexical. Nenhuma promoção ou merge.
- Varredura pública Gitleaks sem achados. Nove valores secretos protegidos comparados em memória contra fontes e recursos do JAR, zero correspondências. Um falso positivo entre duas variáveis vazias de `.env.example` foi eliminado com comentário explícito; nenhuma chave real estava nesse arquivo.

### Demonstração real do RAG

| Consulta fictícia | Resultado | Exemplos recuperados | Chamadas reais ao LLM |
|---|---|---:|---:|
| Pizza delivery fictícia, com histórico exato | Delivery/HISTORY | 1 | 0 |
| Netflix mensal fictícia | Assinaturas/RULE | 0 | 0 |
| Pizza delivery fictícia em casa | Delivery/LLM | 1 | 1 |
| PIX pessoa fictícia, sem finalidade | Sem categoria/NONE | 0 | 0 |

No terceiro caso, recuperou `pizza delivery jantar ficticio → Delivery`, similaridade **0,8749**. A contagem veio do orçamento persistido antes/depois, não apenas do campo `modelUsed`, que também pode representar cache do adaptador. Similaridade vetorial não é certeza da categoria: esse caso ainda precisou de interpretação. Repetição exata e correções foram reutilizadas no Java, sem chamar Python/LLM.

Não vetorizamos o banco financeiro inteiro. Cada descrição confirmada é um documento curto; não separamos uma frase de estabelecimento em pedaços artificiais. Saldo e valor não entram nos embeddings.

### Ajustes de execução encontrados no teste

- OIDC em runtime vem do cabeçalho da requisição, não de uma variável de build reutilizável. Java usa a identidade atual do request; o consumidor Python usa o SDK oficial por requisição. A primeira rodada ficou bloqueada em 401; a corrigida passou pela fila real. Não armazenar esses tokens no banco, código ou logs.
- Um cold start Java ultrapassou a janela de abertura do container. Somente Preview recebeu `-XX:TieredStopAtLevel=1`, mantendo os flags de memória anteriores. O fluxo real passou depois; é uma redução de compilação inicial com possível custo de desempenho sustentado. A flag não foi aplicada à Production.
- `vercel curl` criou um bypass de automação automaticamente. Esse bypass foi revogado; os testes usam OIDC curto e Trusted Sources Preview ↔ Preview. Nenhuma liberação de Production foi criada.
- A lista é invalidada também ao mudar o opt-in, para mostrar classificações concluídas mesmo ao desligar a atualização periódica.

## Como conferir na tela

Abra [o Preview Java](https://java-finance-category-preview.vercel.app). Ele mantém proteção da Vercel; autentique-se na sua conta Vercel se solicitado. A conta **exclusivamente fictícia** é `nuvem-demo@javafinance.local`, senha `DemoCloud123!`. Ela não existe na produção.
Em Movimentações, o switch começa desligado; as categorias de teste já processadas permanecem visíveis. Pode corrigir, remover ou ativar a classificação nesse perfil fictício. Mercado, Delivery, Assinaturas e PIX representam somente fixtures.

O E2E principal pressupõe a carga fictícia inicial; depois da correção deliberada do teste, recrie as fixtures em um ambiente descartável antes de repetir o cenário completo. Não remova a correção para tentar obter novamente a decisão antiga do modelo. O teste isolado do segundo perfil pode ser repetido independentemente.

### Preparar novamente o Python em outro computador

`pwsh -NoProfile -File scripts/prepare-category-cloud-package.ps1 -Destination .tools/category-cloud-package-novo` exporta somente fontes explicitamente permitidas, `pyproject.toml`, `uv.lock` e configuração cloud. Não copia `.env`, credenciais, pesos ou banco, e não publica nada. O build baixa apenas os artefatos públicos fixados. O pacote antigo `scripts/deploy-category-ai-preview.ps1` é do serviço lexical e não deve ser confundido com a candidata vetorial.

Variáveis necessárias no ambiente de teste, sempre sem valores no Git: `CATEGORY_AI_MODE=candidate`, `CATEGORY_AI_PROVIDER=groq`, modelo Groq fixo do código, `CATEGORY_AI_CLOUD_RAG=true`, expiração do laboratório, token do advisor, token independente do trabalho e chave Groq. Vetores usam `CATEGORY_VECTOR_URL`/`CATEGORY_VECTOR_ALLOWED_HOST` exclusivamente do banco dedicado e papel restrito; callback usa `CATEGORY_JAVA_URL`/`CATEGORY_JAVA_ALLOWED_HOST`. Esta candidata habilitou Large Functions apenas no Preview. Consultar o relatório antes de criar recursos; não reutilizar conexão financeira.

## Rollback

Desligar a preferência do usuário impede novas aplicações, inclusive respostas em andamento. O kill switch global `CATEGORY_AI_AUTOMATIC_CLOUD_ENABLED=false` desliga a automação em todo o deployment. `CATEGORY_AI_ENABLED=false` desliga chamadas ao advisor.
Preservar V9–V13 e categorias já corrigidas. Não apagar migrações aplicadas nem restaurar uma cópia sobre operações posteriores. Rollback de código deve aceitar a estrutura aditiva existente.
Para remover o laboratório, revogar apenas variáveis/regras/aliases criados no Preview e suspender/remover somente a branch fictícia identificada no registro operacional privado. Não alterar Production ou recuperações anteriores.

## Antes de Production

Os testes de ponta a ponta, segurança e V8 → V13 isolada desta candidata foram concluídos. Antes de uma publicação posterior, revalidar recuperação e estado atual do banco correto, pois operações novas podem ter ocorrido desde este teste. Provisionar um banco vetorial duradouro com papel restrito; o usado aqui é temporário, fictício e tem expiração.
A chave Groq anteriormente compartilhada no chat precisa ser substituída antes de ativar com descrições reais; não repetir a chave em código, relatório ou PR.
O usuário precisa ver o resultado e autorizar a publicação posterior. Código no GitHub, health Python ou Preview READY não comprovam integração em Production.

Referências de execução: [OIDC na Vercel](https://vercel.com/docs/oidc), [fila Python oficial](https://github.com/vercel/examples/tree/main/python/queue-subscribers), [Vercel Queues](https://vercel.com/docs/queues).
