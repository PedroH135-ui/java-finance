# Publicação e aplicativo mobile

Endereço público: [Java Finance](https://java-finance.vercel.app).

## Estrutura

O mesmo React e Spring Boot são executados juntos em um container na Vercel. O PostgreSQL fica no projeto dedicado `java-finance` do Neon, em São Paulo (`aws-sa-east-1`). A execução cloud usa sessões JDBC, portanto o login não depende da memória de uma única instância.

- Vercel: projeto `java-finance`, equipe `VERCEL_SCOPE`.
- Neon: projeto `NEON_PROJECT_ID`, banco `java_finance`, PostgreSQL 16.
- Imagem: `Dockerfile.vercel`; detecção automática de container pela Vercel.
- Perfil Spring: `cloud`; sessão de 30 minutos, cookie Secure/HttpOnly/SameSite=Strict e CSRF.
- TLS do JDBC valida certificado e nome do servidor (`sslmode=verify-full`).
- Cadastro público desabilitado na produção. A conta local existente foi migrada; não há senha padrão.

As variáveis `DB_URL`, `DB_USER`, `DB_PASSWORD`, `COOKIE_SECURE`, `SERVER_ADDRESS`, `PORT`, `SPRING_PROFILES_ACTIVE` e `REGISTRATION_ENABLED` ficam somente no ambiente de produção da Vercel. Nunca colocar credenciais em variáveis `VITE_*`, arquivos públicos ou commits. `.cloud`, `.vercel` e `.env*` são excluídos da publicação.

## Migração inicial

O script `scripts/cloud-provision.mjs` recebe caminhos das CLIs autenticadas Neon e Vercel. Antes de copiar, exige que o destino esteja vazio, gera backup em `.cloud/local-before-cloud.dump` e preserva o banco local. A restauração usa TLS validado e compara contagens de usuários/movimentações. Não copia o banco de testes. Não apaga nem sobrescreve tabelas existentes.

Depois da migração, os bancos local e cloud são independentes: alterações em um não são sincronizadas automaticamente com o outro. Faça backups do banco cloud antes de mudanças importantes. As migrações do schema são aplicadas pelo Flyway na inicialização.

## Instalar no celular

1. Abra o endereço HTTPS publicado e entre com a mesma conta.
2. Android/Chrome: menu ⋮ → **Instalar aplicativo** ou **Adicionar à tela inicial**.
3. iPhone/Safari: **Compartilhar** → **Adicionar à Tela de Início**; ative **Abrir como App**, se disponível.

Em Configurações há **Instalar aplicativo** e instruções. É um PWA, sem APK e sem publicação nas lojas. Requer internet para consultar e salvar dados. O service worker armazena somente arquivos públicos e oferece uma página explicativa sem conexão; não armazena respostas de `/api/`, dados financeiros ou sessões em cache offline.

## Republicar

Conexões bancárias: consultar `conexoes-bancarias.md`. O novo módulo depende das quatro variáveis PLUGGY_* apenas no backend.
`.env.banking.local` é privado e não é enviado no build; configurar as variáveis de produção explicitamente, sem imprimir segredos.
Antes de ativar, validar o proprietário pessoal e o conector MeuPluggy correto. Não usar trial comercial como garantia de gratuidade.

Na pasta ativa, com Vercel CLI atual e autenticação válida:

```powershell
vercel deploy --prod --yes --scope VERCEL_SCOPE
```

A Vercel compila o frontend e o JAR remotamente pelo Dockerfile; a instalação de Docker no computador não é necessária para esse build remoto. `vercel.json` solicita região `gru1`. Validar `/api/health`, `/api/auth/config`, manifest, sessão HTTPS e fluxo financeiro após cada publicação.

Os serviços têm limites de conta e uso. Não há garantia de disponibilidade ou gratuidade permanente. Instâncias podem suspender e a primeira abertura pode demorar. A conta Neon não permite alterar o intervalo de suspensão; nenhum upgrade pago foi feito.

## Verificações

Backend: testes unitários e de integração, incluindo login persistido em JDBC e revogação no logout. Frontend: lint, testes unitários e build. Navegador: fluxo financeiro em banco descartável, telas responsivas e teste de PWA/ícones/ausência de APIs no cache/modo sem conexão.

Referências: [Vercel Container Images](https://vercel.com/docs/functions/container-images), [Spring Session JDBC](https://docs.spring.io/spring-session/reference/configuration/jdbc.html).
