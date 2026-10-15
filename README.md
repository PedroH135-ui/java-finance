# Java Finance
Aplicação de finanças pessoais, com execução local e publicação web/PWA. React + TypeScript + Material UI; Java 21 + Spring Boot 3.5.16; PostgreSQL 16 + JPA + Flyway.

**Entrega incremental:** fundação da V1. Não é a V1 inteira. Consulte [roadmap](docs/roadmap.md).

## Iniciar neste computador

Com Docker Desktop aberto, execute na pasta ativa:

```powershell
.\scripts\start-local.ps1 -EnableRegistration -SkipBuild
```

Abra http://127.0.0.1:8080 e crie sua própria conta. Não há senha padrão nem dados fictícios no banco pessoal.
Depois do cadastro, encerre com Ctrl+C e execute `./scripts/start-local.ps1 -SkipBuild` para desabilitar novos cadastros
(mantenha `REGISTRATION_ENABLED=false` no .env). O parâmetro `-SkipBuild` usa o JAR já compilado;
omita-o depois de alterar código. Esse modo executa PostgreSQL no Docker e o Java com React integrado no Windows.

## O que está implementado
- Cadastro controlado por configuração, login, logout, perfil e alteração de senha.
- Sessão HttpOnly, CSRF, limite de tentativas e isolamento por proprietário.
- Contas com saldo inicial separado das receitas, arquivamento e ajustes rastreáveis.
- Categorias de receita/despesa com indicador de essencial.
- Orçamentos mensais por categoria, efetivados/previstos separados, saldo do limite, percentual e cópia sem sobrescrita.
- Conexões bancárias pessoais via Meu Pluggy: vínculo, consulta e revisão de lançamentos, desativadas até configurar credenciais privadas.
- Movimentações previstas/efetivadas, confirmação, cancelamento com motivo e histórico.
- Transferência atômica entre contas próprias, reenvio idempotente e concorrência.
- Dashboard com saldo realizado, competência, fluxo de caixa, economia, categorias e previsões.
- Listagem com filtros, ordenação, paginação, detalhes e formulários pt-BR.
- PWA instalável em Android/iPhone, com orientação de instalação e aviso sem conexão.
- Na nuvem: Vercel para a aplicação e Neon para PostgreSQL. O modo local continua independente desses serviços.

## Executar integrado com Docker (Windows/PowerShell)
Pré-requisitos: Docker Desktop com engine Linux iniciada, Compose e espaço livre para imagens/build.
Na raiz:

```powershell
if (!(Test-Path .env)) { Copy-Item .env.example .env }
# Edite .env: escolha DB_PASSWORD e habilite REGISTRATION_ENABLED=true para criar suas contas.
docker compose up --build -d
```

Abra http://localhost:8080. Crie sua conta na interface; não há usuário ou senha padrão.
Depois do cadastro, altere REGISTRATION_ENABLED=false e execute:
```powershell
docker compose up -d app
```

A aplicação só é publicada no loopback. O banco NÃO publica porta na configuração integrada.
Dados ficam no volume finance-data. Para parar sem apagar: `docker compose stop`.
Não use `docker compose down -v` se quiser preservar dados.

## Desenvolvimento separado
Java 21, Node 24 e Docker. Maven é baixado pelo Wrapper. Dependências requerem internet na instalação; o uso local não depende de internet.
Configure .env como acima, depois:

```powershell
docker compose -f compose.yaml -f compose.dev.yaml up -d db
# No terminal do backend, defina a MESMA senha que colocou em .env:
$env:DB_URL='jdbc:postgresql://localhost:55432/java_finance'
$env:DB_USER='java_finance'
$env:DB_PASSWORD='sua-senha-local'
$env:REGISTRATION_ENABLED='true'
cd backend
.\mvnw.cmd spring-boot:run
```

Em outro terminal:
```powershell
cd frontend
npm ci
npm run dev
```
Abra http://localhost:5173. O Vite encaminha /api ao Java. A porta 55432 é restrita a 127.0.0.1 e existe apenas no override de desenvolvimento.

## Empacotar sem Docker
```powershell
cd frontend
npm ci
npm run build
cd ../backend
.\mvnw.cmd -Pintegrated package
java -jar target/java-finance-0.1.0.jar
```
Configure DB_URL, DB_USER e DB_PASSWORD para o PostgreSQL. Esse JAR contém o React compilado.

## Testes
```powershell
cd backend
.\mvnw.cmd verify
cd ../frontend
npm ci
npm run lint
npm test
npm run build
```
`verify` executa testes unitários e de integração em PostgreSQL descartável via Testcontainers; exige Docker por padrão.
Há um fallback restrito a um cluster local exclusivo de testes; veja `docs/validacao.md`.
Nunca usa o banco financeiro local para esses testes.
Teste de navegador: [roteiro e instruções](docs/validacao.md).

## Documentação
- [Conexão pessoal com instituição bancária/Meu Pluggy](docs/conexoes-bancarias.md)
- [Publicação e aplicativo mobile](docs/publicacao.md)
- [Arquitetura](docs/arquitetura.md)
- [Regras financeiras](docs/regras-financeiras.md)
- [API interna](docs/api.md)
- [Backup e restauração](docs/backup.md)
- [Estado e próximos módulos](docs/roadmap.md)
- [Dependências e licenças](docs/dependencias.md)
- [Verificações desta entrega](docs/validacao.md)

## Limites da fundação
Dinheiro somente em BRL. Não há cartões, importação de arquivos CSV/OFX, investimentos ou metas nesta etapa.
Integração bancária pessoal exige configuração e consentimento; não há acesso real sem credenciais e não há pagamentos bancários.
Os módulos futuros não aparecem como funcionalidades prontas na navegação.
O painel calcula somente os domínios implementados; saldo de contas não é patrimônio líquido.
Senha perdida não possui recuperação na interface; não existe fluxo fictício.

