# Ambiente de desenvolvimento no Windows

Em cada novo terminal PowerShell, na raiz do projeto:

```powershell
. .\scripts\enter-dev.ps1
```

O script seleciona o JDK 21 em `.tools` (ou utiliza `JAVA_HOME`) e mantém Maven e suas dependências nessa pasta, ignorada pelo Git. A configuração vale somente para o terminal atual. Node 24 e Docker Desktop com engine Linux são os outros pré-requisitos.

Os sockets temporários do Java também usam `.tools/tmp`, evitando a falha `Unable to establish loopback connection` em processos Windows com diretório temporário redirecionado.

O `.env` local deve conter uma senha aleatória exclusiva para o PostgreSQL. Não envie esse arquivo ao GitHub. Integrações bancárias permanecem desativadas sem suas credenciais.

Para compilar e iniciar o sistema integrado, com Docker Desktop pronto:

```powershell
.\scripts\start-local.ps1 -EnableRegistration
```

Abra http://127.0.0.1:8080 e crie sua conta. Encerre com Ctrl+C e inicie com `-SkipBuild` para usar o JAR compilado sem habilitar novos cadastros. Os dados ficam no volume Docker `finance-data`.

Para validar alterações:

```powershell
Push-Location frontend
npm.cmd run lint
npm.cmd test
npm.cmd run build
Pop-Location
Push-Location backend
.\mvnw.cmd -B -ntp -Pintegrated verify
Pop-Location
```

O `verify` exige Docker e utiliza um PostgreSQL descartável. Consulte [validação](validacao.md) para testes de navegador e o fallback de integração.

## Preparação validada em 12/11/2026

- JDK Temurin 21.0.13 com checksum SHA-256 conferido; Node 24.16.0 e Docker 29.8.0.
- Backend integrado: 6 testes unitários e 38 integrações aprovados com PostgreSQL descartável no Docker.
- Frontend: lint, 2 testes unitários e build aprovados; instalação npm sem vulnerabilidades reportadas. Permanece o aviso de bundle acima de 500 kB.
- Scripts: 3 testes de configuração aprovados e verificação de arquivos privados versionados sem ocorrências.
- Aplicação integrada iniciada com PostgreSQL local e resposta HTTP 200 na página inicial. Testes de navegador não foram executados nesta preparação.
