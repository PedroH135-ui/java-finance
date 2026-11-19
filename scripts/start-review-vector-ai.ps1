param([switch]$Build,[switch]$UseGroq,[switch]$AutomaticCategories)
$ErrorActionPreference='Stop'
$financeVectorRoot=Split-Path $PSScriptRoot -Parent
$financeVectorPrivate=Join-Path $financeVectorRoot '.tools/vector-rag'
$financeVectorPython=Join-Path $financeVectorRoot '.tools/vector-rag-venv/Scripts/python.exe'
$financeVectorStatePath=Join-Path $financeVectorPrivate 'ui-state.json'
$financeVectorConfig=Get-Content (Join-Path $financeVectorRoot '.tools/local-review/config.json') -Raw|ConvertFrom-Json
if(!$financeVectorConfig.demoSeeded -or $financeVectorConfig.database -ne 'java_finance_review'){throw 'Prepare the fictitious local review first.'}
$financeVectorOwner=([guid]$financeVectorConfig.ownerId).ToString()
$financeVectorOldEnv=@{}
$financeVectorKeys=@('PYTHONPATH','VECTOR_UI_ROOT','VECTOR_UI_OWNER','VECTOR_UI_SYNTHETIC_ONLY','VECTOR_LAB_PASSWORD','CATEGORY_AI_TOKEN','CATEGORY_AI_PROVIDER','GROQ_API_KEY')
$financeVectorInherited=@(Get-ChildItem Env:|Where-Object Name -Match '^(DB_|DATABASE_|PLUGGY_|POSTGRES_|NEON_|CATEGORY_AI_|GROQ_|CRON_SECRET$)')
foreach($entry in $financeVectorInherited){$financeVectorOldEnv[$entry.Name]=$entry.Value;Remove-Item -LiteralPath ('Env:'+$entry.Name)}
foreach($key in $financeVectorKeys){if(!$financeVectorOldEnv.ContainsKey($key)){$financeVectorOldEnv[$key]=[Environment]::GetEnvironmentVariable($key,'Process')}}
try{
    if($env:VERCEL_ENV){throw 'This launcher cannot run in a Vercel environment.'}
    $env:PYTHONPATH=(Join-Path $financeVectorRoot 'vector-lab')+';'+(Join-Path $financeVectorRoot 'category-ai')
    & $financeVectorPython -X utf8 -m finance_vector.seed_ui --root $financeVectorRoot
    if($LASTEXITCODE -ne 0){throw 'The synthetic-only fixture guard failed.'}
    $listeners=Get-NetTCPConnection -LocalPort 8126 -State Listen -ErrorAction SilentlyContinue
    if($listeners){
        if(!(Test-Path $financeVectorStatePath)){throw 'Port 8126 is owned by another service.'}
        $old=Get-Content $financeVectorStatePath -Raw|ConvertFrom-Json
        foreach($listener in $listeners){
            $process=Get-CimInstance Win32_Process -Filter ('ProcessId='+$listener.OwningProcess)
            if(!$process -or $process.CommandLine -notlike '*uvicorn finance_vector.ui_service:create_app*' -or ($process.ProcessId -ne $old.pid -and $process.ParentProcessId -ne $old.pid)){throw 'Refusing to stop another Python application.'}
            Stop-Process -Id $process.ProcessId
        }
        $parent=Get-CimInstance Win32_Process -Filter ('ProcessId='+$old.pid) -ErrorAction SilentlyContinue
        if($parent -and $parent.CommandLine -like '*uvicorn finance_vector.ui_service:create_app*'){Stop-Process -Id $old.pid -ErrorAction SilentlyContinue}
    }
    $financeVectorPassword=Import-Clixml (Join-Path $financeVectorPrivate 'database-password.clixml')
    $env:VECTOR_LAB_PASSWORD=[Net.NetworkCredential]::new('',$financeVectorPassword).Password
    $env:VECTOR_UI_ROOT=$financeVectorRoot
    $env:VECTOR_UI_OWNER=$financeVectorOwner
    $env:VECTOR_UI_SYNTHETIC_ONLY='true'
    $env:CATEGORY_AI_TOKEN=$financeVectorConfig.aiToken
    $env:CATEGORY_AI_PROVIDER=if($UseGroq){'groq'}else{'none'}
    if($UseGroq){$financeVectorGroq=Import-Clixml (Join-Path $financeVectorRoot '.tools/local-review/groq-key.clixml');$env:GROQ_API_KEY=[Net.NetworkCredential]::new('',$financeVectorGroq).Password}
    $proc=Start-Process -WindowStyle Hidden -FilePath $financeVectorPython -ArgumentList @('-m','uvicorn','finance_vector.ui_service:create_app','--factory','--host','127.0.0.1','--port','8126','--no-access-log') -WorkingDirectory $financeVectorRoot -RedirectStandardOutput (Join-Path $financeVectorPrivate 'ui.stdout.log') -RedirectStandardError (Join-Path $financeVectorPrivate 'ui.stderr.log') -PassThru
    @{pid=$proc.Id;port=8126;syntheticOnly=$true}|ConvertTo-Json|Set-Content $financeVectorStatePath
}finally{
    foreach($key in $financeVectorOldEnv.Keys){[Environment]::SetEnvironmentVariable($key,$financeVectorOldEnv[$key],'Process')}
    if($financeVectorPassword){$financeVectorPassword.Dispose()};if($financeVectorGroq){$financeVectorGroq.Dispose()}
}
$financeVectorReady=$false
for($attempt=0;$attempt -lt 45;$attempt++){
    try{$health=Invoke-RestMethod 'http://127.0.0.1:8126/health' -TimeoutSec 2;if($health.mode -eq 'local-vector-demo'){$financeVectorReady=$true;break}}catch{}
    Start-Sleep -Seconds 1
}
if(!$financeVectorReady){throw 'The local vector service did not start; inspect .tools/vector-rag/ui.stderr.log.'}
$financeVectorArgs=@('-NoProfile','-File',(Join-Path $PSScriptRoot 'start-review-local.ps1'),'-VectorAi','-Restart')
if($Build){$financeVectorArgs+='-Build'}
if($AutomaticCategories){$financeVectorArgs+='-AutomaticCategories'}
& pwsh @financeVectorArgs
if($LASTEXITCODE -ne 0){throw 'The local Java application did not start.'}
# Organization fixtures require the locally migrated V14 schema. Reuse the same synthetic-only guards.
$financeOrganizationPreviousPath=$env:PYTHONPATH
try {
    $env:PYTHONPATH=(Join-Path $financeVectorRoot 'vector-lab')+';'+(Join-Path $financeVectorRoot 'category-ai')
    & $financeVectorPython -X utf8 -m finance_vector.seed_ui --root $financeVectorRoot --organization-demo
    if($LASTEXITCODE -ne 0){throw 'The guarded organization fixtures failed.'}
} finally { $env:PYTHONPATH=$financeOrganizationPreviousPath }
