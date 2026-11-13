param([switch]$Build,[switch]$Restart,[switch]$RestartAi,[ValidateSet('off','qwen2.5:1.5b')][string]$Llm)
$ErrorActionPreference='Stop'
$financeReviewRoot=Split-Path $PSScriptRoot -Parent
$financeReviewPrivate=Join-Path $financeReviewRoot '.tools/local-review'
New-Item -ItemType Directory -Path $financeReviewPrivate -Force | Out-Null
$financeReviewConfigPath=Join-Path $financeReviewPrivate 'config.json'
$financeReviewStatePath=Join-Path $financeReviewPrivate 'state.json'
$financeReviewContainer='java-finance-review-db'
Push-Location $financeReviewRoot
try {
    . ./scripts/enter-dev.ps1
    # This launcher never reads .env, .env.banking.local, .cloud or Vercel environment files.
    Get-ChildItem Env: | Where-Object Name -Match '^(DB_|PLUGGY_|CRON_SECRET$|SPRING_|CATEGORY_AI_|POSTGRES_)' | ForEach-Object {Remove-Item -LiteralPath ('Env:'+ $_.Name)}
    if(Test-Path -LiteralPath $financeReviewConfigPath) {
        $financeReviewConfig=Get-Content -LiteralPath $financeReviewConfigPath -Raw | ConvertFrom-Json -AsHashtable
    } else {
        $financeExistingContainer=& docker ps -a --filter ('name=^/'+$financeReviewContainer+'$') --format '{{.ID}}'
        if($financeExistingContainer){throw 'Existing review container has no matching local configuration; no data was changed.'}
        $financeReviewConfig=@{database='java_finance_review';dbUser='review_owner';dbPassword=[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(36));aiToken=[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(36))}
        $financeReviewConfig | ConvertTo-Json | Set-Content -LiteralPath $financeReviewConfigPath -Encoding utf8
    }
    if($financeReviewConfig.database -ne 'java_finance_review' -or $financeReviewConfig.dbUser -ne 'review_owner'){throw 'Unexpected local database configuration.'}
    $financeReviewPreviousModel=$financeReviewConfig.model
    if($PSBoundParameters.ContainsKey('Llm')) {
        $financeReviewConfig.model=if($Llm -eq 'off'){''}else{$Llm}
        $financeReviewConfig | ConvertTo-Json | Set-Content -LiteralPath $financeReviewConfigPath -Encoding utf8
        $RestartAi=$true
    }
    if($financeReviewConfig.model -and $financeReviewConfig.model -ne 'qwen2.5:1.5b'){throw 'Unsupported local review model.'}
    $financeExistingContainer=& docker ps -a --filter ('name=^/'+$financeReviewContainer+'$') --format '{{.ID}}'
    if(!$financeExistingContainer) {
        $env:POSTGRES_PASSWORD=$financeReviewConfig.dbPassword
        docker run -d --name $financeReviewContainer --label java-finance.local-review=true -p 127.0.0.1:55434:5432 --env POSTGRES_PASSWORD --env POSTGRES_USER=review_owner --env POSTGRES_DB=java_finance_review -v java_finance_review_pg:/var/lib/postgresql/data postgres:16.10-alpine | Out-Null
        if($LASTEXITCODE -ne 0){throw 'Local database creation failed.'}
        Remove-Item Env:POSTGRES_PASSWORD
    } else {
        $financeLabel=& docker inspect --format '{{index .Config.Labels "java-finance.local-review"}}' $financeReviewContainer
        if($financeLabel -ne 'true'){throw 'The existing container is not a review database.'}
        docker start $financeReviewContainer | Out-Null
    }
    for($financeAttempt=0;$financeAttempt -lt 30;$financeAttempt++) {
        docker exec $financeReviewContainer pg_isready -U review_owner -d java_finance_review *> $null
        if($LASTEXITCODE -eq 0){break}
        Start-Sleep -Seconds 1
    }
    if($LASTEXITCODE -ne 0){throw 'Local database did not become ready.'}
    $financeReviewState=if(Test-Path -LiteralPath $financeReviewStatePath){Get-Content -LiteralPath $financeReviewStatePath -Raw|ConvertFrom-Json -AsHashtable}else{@{}}
    if(($Build -or $Restart) -and $financeReviewState.backendPid) {
        $financeOldProcess=Get-CimInstance Win32_Process -Filter ('ProcessId='+$financeReviewState.backendPid) -ErrorAction SilentlyContinue
        if($financeOldProcess -and $financeOldProcess.CommandLine -like '*target*local-review*java-finance-0.1.0.jar*') {Stop-Process -Id $financeReviewState.backendPid;Start-Sleep -Seconds 1}
    }
    $financeReviewJar=Join-Path $financeReviewRoot 'backend/target/local-review/java-finance-0.1.0.jar'
    if($Build -or !(Test-Path -LiteralPath $financeReviewJar)) {
        $env:VITE_LOCAL_REVIEW='true'
        Push-Location frontend
        try {npm.cmd run build *> ../.tools/local-review/frontend-build.log;if($LASTEXITCODE -ne 0){throw 'Local frontend build failed.'}} finally {Pop-Location}
        Remove-Item Env:VITE_LOCAL_REVIEW
        Push-Location backend
        try {./mvnw.cmd -o -B -ntp '-Dfinance.build.directory=target/local-review' -Pintegrated -DskipTests package *> ../.tools/local-review/backend-build.log;if($LASTEXITCODE -ne 0){throw 'Local backend build failed.'}} finally {Pop-Location}
    }
    if($financeReviewConfig.model){& pwsh -NoProfile -File (Join-Path $PSScriptRoot 'start-review-ollama.ps1') -Model $financeReviewConfig.model;if($LASTEXITCODE -ne 0){throw 'Local model startup failed.'}}
    elseif($financeReviewPreviousModel -and $PSBoundParameters.ContainsKey('Llm')){& pwsh -NoProfile -File (Join-Path $PSScriptRoot 'start-review-ollama.ps1') -Unload;if($LASTEXITCODE -ne 0){throw 'Local model unload failed.'}}
    if($RestartAi -and $financeReviewState.pythonPid) {
        $financeOldAi=Get-CimInstance Win32_Process -Filter ('ProcessId='+$financeReviewState.pythonPid) -ErrorAction SilentlyContinue
        if($financeOldAi -and $financeOldAi.CommandLine -like '*category-ai-venv* -m uvicorn finance_ai.app:app *') {
            # Stop only this venv's worker and its direct child, preserving other Python apps.
            Get-CimInstance Win32_Process -Filter ('ParentProcessId='+$financeReviewState.pythonPid) | Where-Object CommandLine -Like '*category-ai-venv* -m uvicorn finance_ai.app:app *' | ForEach-Object {Stop-Process -Id $_.ProcessId}
            Stop-Process -Id $financeReviewState.pythonPid -ErrorAction SilentlyContinue
            Start-Sleep -Seconds 1
        }
    }
    $env:CATEGORY_AI_TOKEN=$financeReviewConfig.aiToken
    if($financeReviewConfig.model){$env:CATEGORY_AI_MODEL=$financeReviewConfig.model}
    $financePython=Join-Path $financeReviewRoot '.tools/category-ai-venv/Scripts/python.exe'
    if(!(Test-Path -LiteralPath $financePython)){throw 'The category-ai virtual environment is not installed.'}
    $financeAiListener=Get-NetTCPConnection -LocalPort 8123 -State Listen -ErrorAction SilentlyContinue
    if(!$financeAiListener) {
        $financeAi=Start-Process -WindowStyle Hidden -FilePath $financePython -ArgumentList @('-m','uvicorn','finance_ai.app:app','--app-dir','category-ai','--host','127.0.0.1','--port','8123','--no-access-log') -WorkingDirectory $financeReviewRoot -RedirectStandardOutput (Join-Path $financeReviewPrivate 'python.stdout.log') -RedirectStandardError (Join-Path $financeReviewPrivate 'python.stderr.log') -PassThru
        $financeReviewState.pythonPid=$financeAi.Id
    } else {
        # Windows venv launches a child python.exe; the socket belongs to that child.
        $financeAiOwner=Get-CimInstance Win32_Process -Filter ('ProcessId='+$financeAiListener[0].OwningProcess)
        $financeAiExpectedCommand='*'+$financePython+'* -m uvicorn finance_ai.app:app *'
        if(!$financeReviewState.pythonPid -or !$financeAiOwner -or $financeAiOwner.CommandLine -notlike $financeAiExpectedCommand -or
           ($financeAiOwner.ProcessId -ne $financeReviewState.pythonPid -and $financeAiOwner.ParentProcessId -ne $financeReviewState.pythonPid)) {
            throw 'Port 8123 is occupied by another process.'
        }
    }
    $env:DB_URL='jdbc:postgresql://127.0.0.1:55434/java_finance_review'
    $env:DB_USER='review_owner';$env:DB_PASSWORD=$financeReviewConfig.dbPassword
    $env:SERVER_ADDRESS='127.0.0.1';$env:PORT='8082';$env:COOKIE_SECURE='false'
    $env:REGISTRATION_ENABLED=if($financeReviewConfig.ownerId){'false'}else{'true'}
    $env:APP_HOSTING='local';$env:FRONTEND_ORIGIN='http://127.0.0.1:8082'
    $env:CATEGORY_AI_ENABLED='true';$env:CATEGORY_AI_URL='http://127.0.0.1:8123'
    if($financeReviewConfig.ownerId){$env:PLUGGY_OWNER_ID=$financeReviewConfig.ownerId}
    $financeAppListener=Get-NetTCPConnection -LocalPort 8082 -State Listen -ErrorAction SilentlyContinue
    if(!$financeAppListener) {
        $financeBackend=Start-Process -WindowStyle Hidden -FilePath (Join-Path $env:JAVA_HOME 'bin/java.exe') -ArgumentList @('-jar',('"'+$financeReviewJar+'"')) -WorkingDirectory $financeReviewRoot -RedirectStandardOutput (Join-Path $financeReviewPrivate 'backend.stdout.log') -RedirectStandardError (Join-Path $financeReviewPrivate 'backend.stderr.log') -PassThru
        $financeReviewState.backendPid=$financeBackend.Id
    } elseif(!$financeReviewState.backendPid -or $financeAppListener.OwningProcess -notcontains $financeReviewState.backendPid) {throw 'Port 8082 is occupied by another process.'}
    $financeReviewState | ConvertTo-Json | Set-Content -LiteralPath $financeReviewStatePath -Encoding utf8
    $financeReviewReady=$false
    for($financeAttempt=0;$financeAttempt -lt 45;$financeAttempt++) {
        try {
            $null=Invoke-RestMethod 'http://127.0.0.1:8082/api/auth/config' -TimeoutSec 2
            $null=Invoke-RestMethod 'http://127.0.0.1:8123/health' -TimeoutSec 2
            $financeReviewReady=$true;break
        } catch {Start-Sleep -Seconds 1}
    }
    if(!$financeReviewReady){throw 'Local services did not become ready. Check .tools/local-review logs; no production services were contacted.'}
    $financeReviewHealth=Invoke-RestMethod 'http://127.0.0.1:8123/health' -TimeoutSec 2
    if([bool]$financeReviewHealth.modelEnabled -ne [bool]$financeReviewConfig.model){throw 'Python is running with a different model configuration; use -RestartAi.'}
    Write-Output 'Local app: http://127.0.0.1:8082 — database 127.0.0.1:55434 — Python 127.0.0.1:8123. No production or Pluggy credentials loaded.'
} finally {Pop-Location}
