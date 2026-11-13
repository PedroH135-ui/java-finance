param([ValidateSet('qwen2.5:1.5b')][string]$Model='qwen2.5:1.5b',[switch]$Pull,[switch]$Unload)
$ErrorActionPreference='Stop'
$financeLlmRoot=Split-Path $PSScriptRoot -Parent
$financeLlmDirectory=Join-Path $financeLlmRoot '.tools/ollama-local'
$financeLlmExecutable=Join-Path $financeLlmDirectory 'bin/ollama.exe'
$financeLlmStatePath=Join-Path $financeLlmDirectory 'state.json'
if(!(Test-Path -LiteralPath $financeLlmExecutable)){throw 'Install the portable Ollama package first; see category-ai/README.md.'}
# Process-scoped configuration. No global install, PATH change, login or cloud models.
Get-ChildItem Env: | Where-Object Name -Match '^(DB_|PLUGGY_|SPRING_|CATEGORY_AI_|POSTGRES_|OLLAMA_|CRON_SECRET$)' | ForEach-Object {Remove-Item -LiteralPath ('Env:'+ $_.Name)}
$env:OLLAMA_HOST='127.0.0.1:11434'
$env:OLLAMA_MODELS=Join-Path $financeLlmDirectory 'models'
$env:OLLAMA_NO_CLOUD='1'
$env:OLLAMA_CONTEXT_LENGTH='2048'
$env:OLLAMA_NUM_PARALLEL='1'
$env:OLLAMA_MAX_LOADED_MODELS='1'
$env:OLLAMA_KEEP_ALIVE='30m'
$env:OLLAMA_MAX_QUEUE='2'
$financeLlmState=if(Test-Path -LiteralPath $financeLlmStatePath){Get-Content -LiteralPath $financeLlmStatePath -Raw|ConvertFrom-Json}else{$null}
$financeLlmListener=Get-NetTCPConnection -LocalPort 11434 -State Listen -ErrorAction SilentlyContinue
if($Unload -and !$financeLlmListener){Write-Output 'Local model is already stopped.';return}
if($financeLlmListener){
    $financeLlmOwner=Get-CimInstance Win32_Process -Filter ('ProcessId='+$financeLlmListener[0].OwningProcess)
    if(!$financeLlmState -or $financeLlmState.pid -ne $financeLlmOwner.ProcessId -or $financeLlmOwner.ExecutablePath -ne $financeLlmExecutable){
        throw 'Port 11434 belongs to another service; it was not changed.'
    }
} else {
    $financeLlmProcess=Start-Process -WindowStyle Hidden -FilePath $financeLlmExecutable -ArgumentList 'serve' -WorkingDirectory $financeLlmDirectory -RedirectStandardOutput (Join-Path $financeLlmDirectory 'stdout.log') -RedirectStandardError (Join-Path $financeLlmDirectory 'stderr.log') -PassThru
    @{pid=$financeLlmProcess.Id}|ConvertTo-Json|Set-Content -LiteralPath $financeLlmStatePath -Encoding utf8
}
$financeLlmReady=$false
for($financeLlmAttempt=0;$financeLlmAttempt -lt 20;$financeLlmAttempt++){
    try {$null=Invoke-RestMethod http://127.0.0.1:11434/api/version -TimeoutSec 2;$financeLlmReady=$true;break} catch {Start-Sleep -Seconds 1}
}
if(!$financeLlmReady){throw 'Ollama local did not become ready; check .tools/ollama-local logs.'}
if($Unload){
    $null=Invoke-RestMethod http://127.0.0.1:11434/api/generate -Method Post -ContentType 'application/json' -Body (@{model=$Model;stream=$false;keep_alive=0}|ConvertTo-Json) -TimeoutSec 10
    Write-Output 'Local model unloaded from memory; no data was removed.'
    return
}
$financeLlmModels=Invoke-RestMethod http://127.0.0.1:11434/api/tags -TimeoutSec 5
if($Model -notin $financeLlmModels.models.name){
    if(!$Pull){throw 'The model is not downloaded yet. Run start-review-ollama.ps1 -Pull once.'}
    & $financeLlmExecutable pull $Model *> (Join-Path $financeLlmDirectory 'pull.log')
    if($LASTEXITCODE -ne 0){throw 'Model download failed; check .tools/ollama-local/pull.log.'}
}
$financeLlmInstalled=(Invoke-RestMethod http://127.0.0.1:11434/api/tags -TimeoutSec 5).models | Where-Object name -EQ $Model
if($financeLlmInstalled.digest -ne '65ec06548149b04c096a120e4a6da9d4017ea809c91734ea5631e89f96ddc57b'){
    throw 'The model digest differs from the evaluated version; review it before enabling.'
}
# Warm loading is outside the interactive request budget. No financial prompt is sent.
$null=Invoke-RestMethod http://127.0.0.1:11434/api/generate -Method Post -ContentType 'application/json' -Body (@{model=$Model;stream=$false;keep_alive='30m';options=@{num_ctx=2048;num_thread=4;num_gpu=0}}|ConvertTo-Json -Depth 3) -TimeoutSec 60
Write-Output ('Ollama local ready: '+$Model+' — loopback only; no cloud mode.')
