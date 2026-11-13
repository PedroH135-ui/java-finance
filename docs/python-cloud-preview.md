# Python hospedado na nuvem — laboratório isolado

## Banco de dados e hospedagem são funções diferentes

O Python **não foi hospedado no Neon**. O Neon continua sendo o banco PostgreSQL utilizado pelo Java Finance de produção. O serviço de categorias não recebe conexão de banco, não executa SQL e não tem credenciais do Nubank.

Nesta validação, o fluxo é:

| Componente | Onde executa | Responsabilidade |
|---|---|---|
| React e Java de teste | Computador, porta 8082 | Tela e regras financeiras |
| PostgreSQL de teste | Docker local, porta 55434 | Somente dados fictícios |
| Python/FastAPI/LangChain | Vercel, projeto separado, Preview | Recuperar exemplos e propor categoria |
| Modelo GPT-OSS 20B | GroqCloud | Inferência sobre textos fictícios permitidos |
| Neon e Java Finance de produção | Infraestrutura existente | Não foram alterados ou acessados pelo teste |

Quando uma futura publicação for autorizada, o Java poderá continuar utilizando o mesmo Neon. Não é necessário criar outro banco para hospedar o Python.

## Plataforma escolhida

**Vercel Hobby**, plano gratuito confirmado na equipe `pedro135he-7663s-projects`. A Vercel suporta FastAPI como uma Function Python. Não houve upgrade de plano, novo banco ou recurso pago. O script de deploy recusa equipes fora do Hobby.

O serviço escala sob demanda e está sujeito às cotas e inicializações da plataforma. Não é uma máquina dedicada ligada permanentemente. O aplicativo não depende de disco persistente ou de pesos de modelo: a inferência é feita na Groq.

Fontes: [FastAPI na Vercel](https://vercel.com/docs/frameworks/backend/fastapi), [Python runtime](https://vercel.com/docs/functions/runtimes/python), [Hobby e limites](https://vercel.com/docs/plans/hobby).

## Projeto e deployment de teste

- Projeto: `java-finance-category-ai-lab`.
- ID: `prj_AKDwk7l6ep7THx6cTU43VHsxdhVP`.
- Painel: https://vercel.com/pedro135he-7663s-projects/java-finance-category-ai-lab
- Python: https://java-finance-category-ai-o4dtndvj6-pedro135he-7663s-projects.vercel.app
- Saúde: `/health`.
- Classificação: `POST /suggest`, autenticado.
- Deployment Python: `dpl_8Cx4YmVnJ7xta51ei7pVnPgHZhmk`, **Preview**, READY.
- Runtime Python 3.13, região configurada `gru1`, duração máxima da função de 30 segundos.

O projeto não tem repositório Git conectado. O deploy envia apenas uma pasta de staging com os arquivos Python e dependências fixadas, sem `.tools`, chaves locais, frontend, backend Java ou arquivos de ambiente de produção. Não há promoção de domínio do Java Finance.

A Vercel exige que o primeiro deployment de um projeto novo ocupe o alvo Production. Para liberar Preview, criamos **somente uma página estática inerte** no projeto de laboratório, com `--skip-domain`, sem IA, segredos ou banco. Seu deployment é `dpl_FP5xDQVF6Q3Ersig2vuq5TVuQbu3`. O Python foi enviado depois para Preview; a produção do projeto `java-finance` não foi alterada. O primeiro build Python falhou ao interpretar um arquivo de requisitos incluído; a pasta de staging passou a usar diretamente o conteúdo do lock como `requirements.txt`, e o build seguinte passou.

## Proteção e credenciais

O serviço mantém a proteção Vercel Authentication. Para testes automatizados, usamos `vercel env run -e development` no próprio projeto, que fornece um token OIDC temporário em memória. Não desativamos proteção nem criamos segredo permanente de bypass.

Além da proteção da plataforma, `/suggest` exige um Bearer token aleatório, exclusivo do laboratório, diferente do token do Python local. Ele fica como Secret de Preview; a cópia local está protegida por DPAPI em `.tools/category-ai-cloud-preview/service-token.clixml`.

A revisão automática inicialmente bloqueou o envio da chave Groq para o novo destino. O usuário autorizou explicitamente essa disponibilização ao projeto de laboratório; só então `GROQ_API_KEY` foi configurada como Secret exclusivo de Preview. Não foi enviada nenhuma credencial de Neon/Nubank.

Variáveis verificadas no projeto:

| Nome | Tipo | Ambiente |
|---|---|---|
| `GROQ_API_KEY` | Secret | Preview |
| `CATEGORY_AI_TOKEN` | Secret | Preview |
| `CATEGORY_AI_PROVIDER` | Config: `groq` | Preview |
| `CATEGORY_AI_MODEL` | Config: `openai/gpt-oss-20b` | Preview |
| `CATEGORY_AI_LAB_EXPIRES_AT` | Config: expiração | Preview |

Não há variáveis `DB_`, `POSTGRES` ou `PLUGGY_`, nem segredos configurados para Production. O cliente Groq mantém a lista explícita de textos e categorias fictícias descrita no [guia Groq](llm-nuvem.md).

O limite de 24 tentativas é **por processo/instância**, não uma cota global: a plataforma pode criar várias instâncias. Não substitui os limites gratuitos da Groq/Vercel. A classificação deste laboratório tem expiração fixa: **15/11/2026 às 14:40:46, horário de Brasília**. Depois disso, `/suggest` retorna 503. A expiração não apaga o projeto ou os segredos; apenas bloqueia a classificação até uma renovação explícita do deploy.

## Como testar

A pasta privada `.tools/category-ai-cloud-preview` guarda IDs/URL, caminho da CLI e o token DPAPI. Nenhum desses arquivos privados faz parte do commit ou do deploy. Na estação já preparada, execute na raiz:

```powershell
# Reabrir o app local apontando para o Python remoto, com OIDC temporário.
pwsh -NoProfile -File scripts/start-review-cloud-ai.ps1

# Após alterar Java/React, recompile o app local:
pwsh -NoProfile -File scripts/start-review-cloud-ai.ps1 -Build
```

Abra http://127.0.0.1:8082/#/movements?month=2026-11, usando `demo@javafinance.local` / senha fictícia `DemoLocal123!`. Abra Restaurante Sabor da Vila de R$ 35. Deve aparecer Alimentação, com origem “Modelo na nuvem”, sem gravação antes do clique.

Se a autenticação temporária expirar, execute novamente o launcher remoto para renová-la. Esse token de desenvolvimento serve ao laboratório; não é o mecanismo de autenticação que será adotado automaticamente em produção.

Para testar diretamente o Preview por HTTP:

```powershell
$financeLabConfig = Get-Content .tools/category-ai-cloud-preview/config.json -Raw | ConvertFrom-Json
node $financeLabConfig.vercelCliPath env run --cwd .tools/category-ai-cloud-preview/source --scope pedro135he-7663s-projects -e development -- pwsh -NoProfile -File "$PWD/scripts/test-category-ai-preview.ps1"
```

Esse teste usa apenas exemplos fictícios, valida autenticação e tamanho de corpo e salva um relatório sem segredos em `.tools/category-ai-cloud-preview/test-result.json`.

## Evidências observadas em 13/11/2026

- 23 testes Python passaram, incluindo expiração e proteção de contexto fictício.
- Seis testes Java passaram, incluindo HTTPS com host explicitamente permitido e rejeição de outros hosts, HTTP remoto, portas alternativas, caminhos, usuário na URL, query e fragmento.
- Build integrado Java/React passou; permanece o aviso existente sobre tamanho do bundle frontend.
- Preview sem modelo: saúde respondeu em 707 ms; regra Mercado Alfa → Alimentação; restaurante sem sugestão, sem chamar Groq.
- Preview com Groq: saúde respondeu em 647 ms; restaurante → Alimentação; chamadas sem o token do serviço retornaram 401; corpo acima de 64 KiB retornou 413.
- No fluxo React local → Java local → HTTPS autenticado → Python Vercel → Groq, o restaurante recebeu Alimentação em 2.213 ms, com o **Python local desligado** e a porta 8123 sem listener.
- Aplicar/remover categoria preservou saldo de R$ 3.705, resultado de R$ 2.505 e quantidade de movimentos. O exemplo foi deixado sem categoria para teste manual.

As durações são observações de uma amostra pequena, não garantias de cold start ou latência sob carga.

## Retorno ao modo local e produção futura

Para o Java local voltar ao Python local, sem apagar dados ou tocar no serviço remoto:

```powershell
pwsh -NoProfile -File scripts/start-review-local.ps1 -Restart
```

Para desativar IA no laboratório remoto, faça um novo Preview com `-WithoutModel` (isso mantém a proteção e o token do serviço). Para remover a chave do projeto, remova apenas a variável `GROQ_API_KEY` de Preview no painel desse laboratório. Secrets já presentes em deployments antigos não são apagados por alterar variáveis: se for preciso revogação imediata, remova os deployments que os receberam e revogue/substitua a chave no provedor.

O script `deploy-category-ai-preview.ps1` limita-se ao projeto de laboratório, configura apenas Preview, confirma o plano Hobby e renova a janela de teste para 48 horas. O uso sem `-WithoutModel` disponibiliza a chave Groq a esse destino já autorizado. Nunca use esse script para o projeto principal.

O Python e o LLM agora funcionam com o computador desligado. A **tela e o Java desta demonstração ainda são locais**. A aplicação de produção continua como antes: antes de habilitar IA nela, falta autorizar e validar a configuração de comunicação/autenticação de produção, manter a revisão manual e decidir quando ampliar a lista de textos permitidos. Não houve push, merge, migração, acesso ao Neon, importação bancária ou publicação das alterações no Java Finance em produção.
