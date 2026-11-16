$ErrorActionPreference='Stop'
$financeVectorRoot=Split-Path $PSScriptRoot -Parent
$financeVectorPrivate=Join-Path $financeVectorRoot '.tools/vector-rag'
$financeVectorEnv=Join-Path $financeVectorRoot '.tools/vector-rag-venv'
$financeVectorPython=Join-Path $financeVectorEnv 'Scripts/python.exe'
$financeVectorName='java-finance-vector-lab'
$financeVectorImage='pgvector/pgvector@sha256:00ba258a66dac104fd5171074a0084462a64a1369d8513f3d0a634e2f24d15bc'
New-Item -ItemType Directory -Path $financeVectorPrivate -Force|Out-Null
if(!(Test-Path $financeVectorPython)){
    $financeVectorBaseVersion=& python -c 'import sys; print(f"{sys.version_info.major}.{sys.version_info.minor}")'
    if($LASTEXITCODE -ne 0 -or $financeVectorBaseVersion -ne '3.13'){throw 'Use Python 3.13 on PATH to create this lab.'}
    & python -m venv $financeVectorEnv
    if($LASTEXITCODE -ne 0){throw 'Install Python 3.13 first.'}
}
$financeVectorVersion=& $financeVectorPython -c 'import sys; print(f"{sys.version_info.major}.{sys.version_info.minor}")'
if($financeVectorVersion -ne '3.13'){throw 'The vector lab requires a separate Python 3.13 environment.'}
& $financeVectorPython -m pip install -r (Join-Path $financeVectorRoot 'vector-lab/requirements.lock')
if($LASTEXITCODE -ne 0){throw 'Vector lab dependency installation failed.'}
$financeVectorId=& docker ps -a --filter ('name=^/'+$financeVectorName+'$') --format '{{.ID}}'
if($LASTEXITCODE -ne 0){throw 'Start Docker Desktop first.'}
$financeVectorSecretPath=Join-Path $financeVectorPrivate 'database-password.clixml'
if($financeVectorId){
    $financeVectorLabel=& docker inspect $financeVectorName --format '{{index .Config.Labels "java-finance.scope"}}'
    $financeVectorPorts=(& docker inspect $financeVectorName --format '{{json .HostConfig.PortBindings}}')|ConvertFrom-Json
    $financeVectorBind=@($financeVectorPorts.'5432/tcp')
    if($financeVectorLabel -ne 'vector-lab-synthetic-only' -or $financeVectorBind.Count -ne 1 -or $financeVectorBind[0].HostIp -ne '127.0.0.1' -or $financeVectorBind[0].HostPort -ne '55435' -or !(Test-Path $financeVectorSecretPath)){
        throw 'Existing container does not match the dedicated lab; nothing was changed.'
    }
    & docker start $financeVectorName|Out-Null
}else{
    $financeVectorExistingVolume=& docker volume ls --filter 'name=^java_finance_vector_lab_pg$' --format '{{.Name}}'
    if($financeVectorExistingVolume){throw 'An unattached lab volume exists; inspect it manually rather than overwrite it.'}
    & docker pull $financeVectorImage
    if($LASTEXITCODE -ne 0){throw 'Cannot fetch the pinned pgvector image.'}
    $financeVectorPassword=[Convert]::ToHexString([Security.Cryptography.RandomNumberGenerator]::GetBytes(24))
    $financeVectorPassword|ConvertTo-SecureString -AsPlainText -Force|Export-Clixml $financeVectorSecretPath
    $financeVectorOldPgPassword=$env:POSTGRES_PASSWORD
    $env:POSTGRES_PASSWORD=$financeVectorPassword
    try {
        & docker run -d --name $financeVectorName --label 'java-finance.scope=vector-lab-synthetic-only' --memory 512m --cpus 2 --publish 127.0.0.1:55435:5432 --env POSTGRES_USER=vector_lab --env POSTGRES_DB=finance_vector_lab --env POSTGRES_PASSWORD --mount type=volume,source=java_finance_vector_lab_pg,target=/var/lib/postgresql/data $financeVectorImage|Out-Null
        if($LASTEXITCODE -ne 0){throw 'Could not create the dedicated vector lab.'}
    }finally{$env:POSTGRES_PASSWORD=$financeVectorOldPgPassword;$financeVectorPassword=$null}
}
$financeVectorReady=$false
for($financeVectorAttempt=0;$financeVectorAttempt -lt 20;$financeVectorAttempt++){
    & docker exec $financeVectorName pg_isready -U vector_lab -d finance_vector_lab 2>$null|Out-Null
    if($LASTEXITCODE -eq 0){$financeVectorReady=$true;break}
    Start-Sleep -Seconds 1
}
if(!$financeVectorReady){throw 'The isolated database is not ready.'}
$financeVectorOldPath=$env:PYTHONPATH
try {
    $env:PYTHONPATH=(Join-Path $financeVectorRoot 'vector-lab')+';'+(Join-Path $financeVectorRoot 'category-ai')
    & $financeVectorPython -X utf8 -m finance_vector.setup_model --root $financeVectorRoot
    if($LASTEXITCODE -ne 0){throw 'Pinned model setup failed.'}
}finally{$env:PYTHONPATH=$financeVectorOldPath}
Write-Output 'Lab ready: isolated PostgreSQL on 127.0.0.1:55435 and local 384-dimensional embeddings.'
