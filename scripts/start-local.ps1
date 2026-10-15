param([switch]$EnableRegistration, [switch]$SkipBuild)
$ErrorActionPreference = 'Stop'
$taskRoot = Split-Path $PSScriptRoot -Parent
Push-Location $taskRoot
try {
    if (!(Test-Path -LiteralPath '.env')) { throw 'Crie .env a partir de .env.example e defina uma senha local para o banco.' }
    @('.env', '.env.banking.local') | Where-Object { Test-Path -LiteralPath $_ } | ForEach-Object { Get-Content -LiteralPath $_ } | ForEach-Object {
        if ($_ -match '^(DB_NAME|DB_USER|DB_PASSWORD|APP_PORT|REGISTRATION_ENABLED|COOKIE_SECURE|PLUGGY_CLIENT_ID|PLUGGY_CLIENT_SECRET|PLUGGY_OWNER_ID|PLUGGY_ITEM_IDS)=(.*)$') {
            [Environment]::SetEnvironmentVariable($matches[1], $matches[2], 'Process')
        }
    }
    if (!$env:DB_NAME) { $env:DB_NAME = 'java_finance' }
    if (!$env:DB_USER) { $env:DB_USER = 'java_finance' }
    if (!$env:DB_PASSWORD) { throw 'DB_PASSWORD não foi definida.' }
    if (!$env:APP_PORT) { $env:APP_PORT = '8080' }
    docker compose -f compose.yaml -f compose.dev.yaml up -d --wait db
    if ($LASTEXITCODE -ne 0) { throw 'Falha ao iniciar o PostgreSQL. Confira o Docker Desktop.' }
    $env:DB_URL = "jdbc:postgresql://127.0.0.1:55432/$($env:DB_NAME)"
    $env:PORT = $env:APP_PORT
    $env:SERVER_ADDRESS = '127.0.0.1'
    if ($EnableRegistration) { $env:REGISTRATION_ENABLED = 'true' }
    if (!$SkipBuild) {
        Push-Location frontend
        try {
            npm.cmd ci --no-fund
            if ($LASTEXITCODE -ne 0) { throw 'Falha na instalação da interface.' }
            npm.cmd run build
            if ($LASTEXITCODE -ne 0) { throw 'Falha ao compilar a interface.' }
        } finally { Pop-Location }
        Push-Location backend
        try {
            .\mvnw.cmd -B -ntp -Pintegrated -DskipTests package
            if ($LASTEXITCODE -ne 0) { throw 'Falha ao compilar o backend.' }
        } finally { Pop-Location }
    }
    if (!(Test-Path -LiteralPath 'backend/target/java-finance-0.1.0.jar')) { throw 'Execute sem -SkipBuild na primeira vez.' }
    Write-Host "Abra http://127.0.0.1:$($env:PORT). Ctrl+C encerra a aplicação sem apagar seus dados."
    java -jar backend/target/java-finance-0.1.0.jar
    if ($LASTEXITCODE -ne 0) { throw 'O servidor encerrou com erro.' }
} finally { Pop-Location }
