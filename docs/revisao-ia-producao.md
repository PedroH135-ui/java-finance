# Revisão antes da publicação — IA de categorias

## Estado da entrega

Preparada e testada em Preview e no ambiente local. **Não publicada no Java Finance de produção.** Nenhuma variável Production foi criada ou alterada, nenhuma consulta bancária foi iniciada e o Neon não foi acessado nesta etapa.

Base confirmada por metadados da Vercel em 13/11/2026:

- Aplicação: `java-finance`, projeto `prj_grzypzumU1j1X4zUb1EeKLpHnYuq`.
- Commit em produção: `a462d694cc824e8f7a30ac18b4e1b0d0876a6644`.
- Deployment atual: `dpl_7hwmwEg2mXsnbn2PEeD3uhdD99Mz`, READY.
- Serviço Python separado: `java-finance-category-ai-lab`, projeto `prj_AKDwk7l6ep7THx6cTU43VHsxdhVP`.
- Equipe Hobby: `pedro135he-7663s-projects`. Nenhum plano pago foi ativado.

## Tudo que a publicação levará

| Área | Comportamento revisado |
|---|---|
| Sugestões | Histórico consistente e regras antes do LLM; Groq via LangChain somente quando necessário |
| Aplicação de categoria | Somente após clicar em Aplicar categoria escolhida; pode corrigir ou remover depois |
| Banco e saldos | A categoria fica no registro bancário, sem criar movimento ou posting e sem descontar valores novamente |
| Relatórios e orçamento | Passam a usar a categoria escolhida manualmente |
| Transferências | Inclui a identificação manual entre contas próprias já desenvolvida: confirmar/desfazer, sem identificação automática |
| Autenticação dos usuários | Login, sessões e CSRF existentes preservados; Keycloak fica fora desta entrega |
| Falha na IA | Sem sugestão; seleção manual permanece disponível; tela de movimentações e saldos continuam funcionando |

O diff completo em relação ao commit de produção inclui o trabalho de categorização e transferências desenvolvido nas etapas anteriores. Não é apenas uma troca de URL do Python.

## Arquitetura definitiva proposta

Java e React continuam na Vercel; somente o Java acessa o Neon. O Python executa separadamente como FastAPI na Vercel e consulta a Groq. Ele não recebe credenciais do banco ou do Pluggy e recusa iniciar no modo de produção se essas variáveis forem disponibilizadas.

Endereço estável preparado para o Python após a publicação:

`https://java-finance-category-ai-lab.vercel.app`

O domínio foi confirmado nos metadados do projeto. O nome contém `lab`, mas Production e Preview são ambientes distintos, com tokens diferentes. Não renomeamos ou criamos outro destino para as credenciais.

Produção usará **HTTPS e um token de serviço próprio**, armazenado como Secret em ambos os projetos. O navegador não recebe esse token. O Java recusa habilitar IA em produção com HTTP local, segredo curto ou token OIDC de desenvolvimento. Não dependerá do computador ou de um token temporário obtido pela CLI.

A configuração atual da Vercel é Standard Protection: Preview e URLs geradas ficam protegidas, enquanto o domínio de produção fica fora dessa camada de login. O próprio `/suggest` continuará exigindo o Bearer token da aplicação. Não será necessário desativar a proteção do projeto. O escopo será conferido novamente antes da publicação; se estiver diferente, não se deve removê-lo automaticamente. [Proteção de deployments](https://vercel.com/docs/deployment-protection).

## Configuração a aplicar somente após aprovação

No **Production do Python**:

```text
CATEGORY_AI_MODE=production
CATEGORY_AI_PROVIDER=groq
CATEGORY_AI_MODEL=openai/gpt-oss-20b
CATEGORY_AI_TOKEN=<Secret novo, exclusivo de produção>
GROQ_API_KEY=<Secret Groq autorizado para produção>
```

`CATEGORY_AI_LAB_EXPIRES_AT` não poderá existir em produção. O modo de produção não expira após 48 horas e exige configuração explícita; o laboratório não pode ser publicado acidentalmente como produção.

No **Production do Java**:

```text
CATEGORY_AI_ENABLED=true
CATEGORY_AI_URL=https://java-finance-category-ai-lab.vercel.app
CATEGORY_AI_ALLOWED_HOST=java-finance-category-ai-lab.vercel.app
CATEGORY_AI_TOKEN=<o mesmo Secret novo de produção>
```

`CATEGORY_AI_PREVIEW_OIDC` deve ficar ausente. Nenhuma variável DB/Pluggy, configuração de login ou domínio principal será substituída por esse procedimento.

Um token novo já foi preparado **somente neste computador**, protegido por DPAPI em `.tools/ai-production/service-token.clixml`; não foi enviado a nenhum projeto. `scripts/prepare-ai-production.ps1` gera um manifesto sem valores secretos, com projetos, variáveis, hash da V9 e hash do JAR local. Esse script não chama Vercel ou Neon e não publica nada.

A chave Groq fornecida anteriormente funciona nos testes. Recomenda-se substituí-la antes da ativação em produção por ter sido compartilhada no chat; `configure-review-groq.ps1` permite inserir outra chave com entrada oculta. Nenhuma chave de API faz parte do Git.

## Dados que sairão do Java

O Java continua consultando somente o histórico do próprio perfil e categorias ativas da mesma natureza. Antes da chamada externa:

- Reutiliza histórico exato consistente localmente; conflitos e naturezas financeiras ambíguas não acionam o LLM.
- Normaliza descrição, nomes de categorias e exemplos, removendo números, URLs e e-mails.
- Envia ao Python no máximo 20 categorias e cinco exemplos relevantes, em vez das até 100 classificações consultadas localmente.
- Preserva exemplos de categorias conflitantes na seleção para não criar uma falsa unanimidade por corte de contexto.

O Python recupera contexto e envia ao modelo a descrição normalizada, nomes/chaves semânticas de categorias e exemplos relevantes. UUIDs de categorias são mapeados fora do prompt. Não há valores, saldos, datas, IDs de conta/usuário, senhas, tokens bancários ou ferramentas de escrita no contrato do modelo.

**Normalização não garante anonimização:** nomes de pessoas ou estabelecimentos podem continuar presentes nas descrições. Após a publicação, esses textos normalizados poderão ser processados na Vercel e na Groq. A candidata usa somente dados fictícios; nenhum histórico real foi enviado nesta revisão.

A Groq informa que conteúdo de inferência não é retido por padrão, com exceções temporárias para confiabilidade/abuso e retenção de metadados. Não verificamos nem presumimos retenção zero na conta. [Política e controles de dados](https://console.groq.com/docs/your-data).

## Limites, recuperação e logs

O modo `candidate` usa a mesma política de classificação e controle de recursos de produção, acrescentando apenas a expiração do laboratório. A lista fixa de quatro descrições permanece no modo `lab`; candidata/produção aceitam descrições e categorias próprias dentro do contrato minimizado.

- Cache de cinco minutos, até 128 resultados em memória, indexado pelo hash do contexto completo e IDs de categorias. Os perfis ficam separados pelos IDs; decisões novas mudam o contexto e invalidam o resultado anterior.
- Até 12 tentativas externas por minuto **por instância**, sem bloqueio permanente após 24 consultas. A janela se recupera sozinha.
- Backoff de 30 segundos após falha do provedor; zero retries automáticos e timeout HTTP de seis segundos.
- Java aplica uma pausa de 30 segundos após falha de transporte/status do serviço, preservando o fallback de histórico e a escolha manual.
- Essas proteções não são uma cota global; as cotas gratuitas da Groq/Vercel continuam valendo, inclusive sob escala horizontal. Não há upgrade ou fallback para modelo pago.
- Contexto e saída são limitados; JSON Schema restringe a resposta a uma categoria permitida ou `null`. A indicação de consistência baixa continua sendo uma heurística, não uma probabilidade calibrada.

Java e Python compartilham `X-Correlation-ID` válido como UUID. Logs registram somente ID, status e duração, por exemplo:

```text
category_ai_request correlation_id=<uuid> status=200 duration_ms=<tempo>
```

Não registram prompt, descrição, categoria, saldo, valor, token ou resposta bruta do modelo. Tracing externo permanece desativado. O Python recusa o token ausente/incorreto, corpos acima de 64 KiB e campos financeiros fora do contrato.

## Migração V9 e rollback

`V9__bank_transfer_and_categories.sql` cria as colunas de categoria e snapshots no `bank_entry`, a estrutura para pares de transferências e atualiza a view `financial_record`. Não executa importação, confirmação, débito, atualização de saldo ou criação de postings. Inicialmente, os novos campos ficam sem classificação.

**A V9 ainda não está aplicada no Neon.** Ela será executada pelo Flyway ao iniciar o Java novo, portanto é uma alteração de produção que precisa estar incluída na aprovação. Não se deve fazer um deploy Production com o banco real para “testar antes”: mesmo sem promover o domínio, a inicialização poderia executar a migração.

Antes dessa etapa, confirmar um ponto de recuperação/backup do Neon. Não executar `cloud-provision.mjs`, copiar dados locais para produção ou alterar credenciais bancárias.

Rollback principal: definir `CATEGORY_AI_ENABLED=false` no Java e redeployar a versão revisada. Isso interrompe as consultas de IA e mantém categorias manuais, relatórios, saldos e dados já escolhidos. Não remover V9 automaticamente, não apagar pares/categorias e não tentar desfazer decisões humanas como parte do rollback da IA. Um retorno à aplicação antiga exige conferir sua compatibilidade com a V9 e o backup, e não substitui esse desligamento simples.

## Resultado da revisão e onde conferir

A candidata está em Preview no Python remoto, com a mesma política de classificação de produção e expiração apenas para o teste. A tela em http://127.0.0.1:8082/#/movements?month=2026-11 usa o PostgreSQL fictício e chama esse Python por HTTPS. Perfil de demonstração: `demo@javafinance.local`, senha fictícia `DemoLocal123!`. A URL e a expiração correntes ficam em `.tools/category-ai-cloud-preview/config.json`; o launcher sempre usa essa configuração, sem fixar uma URL antiga.

Abra Restaurante Sabor da Vila, de R$ 35: deve mostrar Alimentação, com origem Modelo na nuvem e consistência baixa. Abrir os detalhes não salva; o clique é obrigatório. O registro foi deixado sem categoria para revisão manual.

Validação concluída em 13/11/2026:

- 35 testes Python: política de release, autenticação (inclusive credencial malformada), cache, isolamento entre contextos, recuperação de quota/backoff, contexto minimizado e logs/correlação.
- 85 testes Java únicos: 30 unitários e 55 de integração. A suíte completa passou antes da adição do último cenário de falha, que também passou na execução dirigida. Inclui migrações, isolamento, categorias manuais, transferências, relatórios e saldos.
- Três testes frontend, lint e seis cenários de navegador passaram. O cenário novo confirma que escolher uma categoria manualmente continua funcionando sem sugestão de IA.
- Pacote Java/React com configuração de produção gerado localmente e conferido sem o banner de dados fictícios. O container de produção mantém o `Dockerfile.vercel` existente; não foi publicado.
- Preview: regra e LLM responderam com os exemplos fictícios; sem token do serviço retornou 401 e corpo excessivo retornou 413.
- Na tela, a candidata sugeriu Alimentação em 5.651 ms na primeira abertura observada. A chamada seguinte ao serviço teve 72 ms no cliente Java, aproveitando o contexto em cache. São observações, não SLA; inicialização/carga/rede e provedor podem variar.
- Aplicar/remover a categoria preservou os R$ 3.705 de saldo, R$ 2.505 de resultado e a quantidade de movimentos, sem importação. O Python local permaneceu desligado.

O domínio estável, os Secrets Production e a migração no Neon **não foram ativados** para antecipar o smoke: o teste final nesses destinos só pode ocorrer no passo aprovado de publicação.

## Ordem proposta para publicar depois da aprovação

1. Conferir commit revisado, plano Hobby, projetos/destinos, escopo dos Secrets e ponto de recuperação do Neon.
2. Configurar os Secrets de produção autorizados no serviço Python; publicar Python em Production e verificar `/health` com `mode=production`, domínio estável e POST sem token retornando 401.
3. Configurar somente as variáveis IA do Java; publicar o container Java revisado e executar a V9 nesse passo já aprovado.
4. Verificar sessão, listagem, sugestão e escolha manual, comparando saldos/quantidade de movimentos sem sincronização bancária ou confirmação automática.
5. Se a IA falhar, desligar a flag e preservar o restante do sistema. Não promover uma build cujo smoke de produção falhe.

Essa ordem e todos os valores não secretos estão no manifesto local. A configuração real, o smoke no domínio estável e a migração no Neon só ocorrerão depois de o usuário revisar e autorizar a publicação.
