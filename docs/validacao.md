# Validação da fundação

## Conexões bancárias pessoais — 14/10/2026

- 6 testes unitários backend e 29 integrações PostgreSQL passaram (35 no total). Incluem 4 testes de protocolo HTTP com respostas simuladas e 10 integrações do módulo bancário.
- Cobertura nova: vínculo sem mudar saldo, leitura idempotente, categoria/natureza explícita, sugestão de duplicidade,
  associação de transferência nas duas contas, confirmação concorrente única, alterações POSTED/PENDING, mudança pós-importação,
  sessão/CSRF/proprietário/itens permitidos, ausência de segredos em config, falhas, intervalo, desativação/reativação e saldo inicial.
- ESLint, TypeScript/Vite e 2 testes unitários frontend passaram. Bundle continua com aviso de tamanho (~320 kB gzip).
- 5 cenários Playwright passaram: configuração ausente, fluxo bancário com rotas do provedor simuladas,
  orçamentos, fundação e PWA/offline. Capturas mobile/desktop revisadas. A tela bancária não solicita senha ou chave.
- Banco de testes: PostgreSQL 16.14 dedicado em 127.0.0.1:55433; nenhuma consulta ao banco pessoal, Neon ou instituição bancária.
- Os testes automatizados usam dados fictícios. Registros de instâncias permanecem privados.

## Módulo Orçamentos — 13/10/2026

- Backend: 2 testes unitários e 19 integrações passaram; 7 integrações novas cobrem cálculo por competência,
  previstos/cancelados, categorias sem limite, zero, saldo inalterado, concorrência, cópia idempotente,
  arquivamento, valores inválidos, sessão/CSRF/isolamento e atualização do schema V4 → V5 preservando registros.
- Frontend: ESLint, TypeScript/Vite e 2 testes unitários passaram.
- Playwright: 3 cenários passaram (orçamentos, fundação financeira e PWA/offline). A tela foi conferida em
  360, 390, 768 e 1440 pixels, sem rolagem horizontal; imagens desktop/mobile revisadas.
- Docker não respondeu nesta sessão. Foi usado PostgreSQL 16.14 em cluster temporário dedicado,
  restrito a 127.0.0.1:55433; bancos `java_finance_verify` e `java_finance_browser_verify` contêm somente fixtures.
  Não foi usada a base pessoal nem o Neon. O caminho Docker continua disponível para os testes padrão,
  mas não foi revalidado nesta sessão.
- Alterações ainda não publicadas na Vercel. V5 é aditiva: cria a tabela de orçamentos sem alterar lançamentos existentes.

Fallback de integração (somente após iniciar um PostgreSQL local descartável na porta 55433 e criar `java_finance_verify`):

```powershell
cd backend
.\mvnw.cmd -B -ntp -Pintegrated '-Dfinance.test.url=jdbc:postgresql://127.0.0.1:55433/java_finance_verify' verify
```

`TestDatabase` rejeita outros URLs de fallback para evitar rodar testes em uma base pessoal/cloud.
Sem a propriedade `finance.test.url`, o padrão continua sendo PostgreSQL descartável via Testcontainers.
Para E2E, executar o JAR integrado contra `java_finance_browser_verify` na porta 8081, com cadastro habilitado,
depois `cd frontend; npm run test:e2e`. Nunca usar os cenários de cadastro/escrita no endereço de produção.

## Verificações automatizadas

- Backend: `backend/mvnw.cmd verify`, PostgreSQL real descartável via Testcontainers.
- Validação final: `mvnw.cmd -B -ntp -Pintegrated verify`, BUILD SUCCESS em 07/10/2026 às 20:38 (São Paulo).
- 2 testes unitários monetários e 11 testes de integração passaram após as migrações V2/V3.
- Cobertura: competência/caixa/saldo inicial; transferências; cancelamento; isolamento entre usuários;
  CSRF/login/logout/invalidação de sessões; concorrência; contas arquivadas; controle de versão;
  migrações sem perda; auditoria do texto anterior; chave idempotente preservada após mudanças de estado;
  configuração de cadastro através do proxy Spring e filtro por competência diferente da data da operação.
- Interface: compilação TypeScript/Vite, ESLint e 2 testes de formatação passaram com as dependências atualizadas.
- Navegador: `frontend/e2e/foundation.spec.ts`; 1 cenário completo passou (11,7 segundos no último teste).
  Inclui perfil, troca de senha, rejeição da senha antiga, novo login e cookie HttpOnly/SameSite Strict.
- npm audit: zero vulnerabilidades reportadas. Não cobre dependências Maven/imagens Docker.
- Backup/restore fictício validado em banco separado, com contagens e soma dos lançamentos coincidentes.
- Script `scripts/start-local.ps1 -EnableRegistration -SkipBuild` validado: PostgreSQL saudável,
  Java + React no mesmo JAR, HTTP 200 em 127.0.0.1:8080, cadastro habilitado apenas nessa execução.
- Capturas desktop/mobile revisadas visualmente; gráfico sem animação evita uma leitura transitória incompleta.

## Limites desta validação

Atualização cloud/PWA em 07/10/2026: 2 testes unitários e 12 integrações backend passaram,
incluindo persistência de sessão JDBC entre repositórios e logout. Lint, 2 testes unitários frontend,
build e os 2 cenários Playwright (financeiro/responsivo e PWA/offline/cache) passaram.
A imagem completa foi compilada remotamente pela Vercel e publicada com runtime em `gru1`.
A migração cloud exige backup e verificação de integridade em ambiente privado.
Produção: health 200, perfil sem login 401, login inválido 401, cadastro público 403,
sessão HTTPS Secure/HttpOnly/SameSite=Strict. Não foi feito login usando a senha pessoal do usuário.
O cenário Playwright de PWA também passou no endereço público HTTPS (`java-finance.vercel.app`),
incluindo service worker, dimensões dos ícones, modo sem conexão e ausência de APIs no cache.

- O build completo da imagem Docker da aplicação não foi executado **localmente**: C: está com espaço limitado.
  O modo validado usa PostgreSQL no Docker e Java/React integrado no Windows, restritos ao loopback.
- O Vite avisa que o bundle principal supera 500 kB (aproximadamente 312 kB gzip). Divisão por rotas é melhoria pendente.
- Não houve auditoria completa de acessibilidade nem de segurança das dependências do backend.
- Orçamentos e conexão bancária pessoal foram implementados incrementalmente. Importação CSV, metas, cartões, investimentos e demais etapas da V1 ainda estão pendentes.

## Verificação para publicação — 14/10/2026

- Backend: 35 testes aprovados (6 unitários e 29 de integração), usando PostgreSQL descartável na porta 55433.
- Frontend: lint, build, 2 testes unitários e 7 testes de navegador aprovados.
- Banco de produção: backup privado gerado antes da aplicação das migrações aditivas V5/V6.
- Homologações de instâncias são registradas em ambiente privado.
- Chaves, banco descartável, backups, traces e artefatos gerados excluídos do Git e do envio à Vercel.

## Teste de navegador

Usar exclusivamente um banco separado chamado `java_finance_e2e`, nunca o banco pessoal.
Criá-lo no PostgreSQL local (`createdb -U java_finance java_finance_e2e`).
Compilar o frontend e o JAR com perfil `integrated`. Executar o JAR com:

```powershell
$env:DB_URL='jdbc:postgresql://127.0.0.1:55432/java_finance_e2e'
$env:DB_USER='java_finance'
# DB_PASSWORD deve vir do .env local; não gravá-la no código.
$env:PORT='8081'
$env:REGISTRATION_ENABLED='true'
java -jar backend/target/java-finance-0.1.0.jar
```

Em outro terminal, `cd frontend; npm run test:e2e`.
O teste usa Chrome instalado; para outro executável, definir BROWSER_PATH.
E2E_URL permite mudar o endereço (padrão http://127.0.0.1:8081).
As contas usam endereços fictícios example.test e existem somente no banco de testes.
Capturas e traces ficam em frontend/test-results e são ignorados pelo Git.
Não publicar traces de sessões reais, pois podem conter cookies e dados financeiros.

O roteiro verifica cadastro, login, contas, categoria, receita pela interface, transferência pela API,
cancelamento pela interface e reconciliação dos dois saldos, descarte de formulário, logout e
ausência de rolagem horizontal nas cinco páginas, nas larguras 360, 390, 768, 1024 e 1440.
Isso não equivale a uma auditoria completa de acessibilidade.
