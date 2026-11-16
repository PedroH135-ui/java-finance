param([string]$VercelCli,[switch]$WithoutModel,[switch]$ReleaseCandidate)
$ErrorActionPreference='Stop'
if($ReleaseCandidate -and $WithoutModel){throw 'The release candidate requires the configured model; use the lab for a model-free preview.'}
$financePreviewRoot=Split-Path $PSScriptRoot -Parent
$financePreviewPrivate=Join-Path $financePreviewRoot '.tools/category-ai-cloud-preview'
$financePreviewConfigPath=Join-Path $financePreviewPrivate 'config.json'
$financePreviewConfig=Get-Content -LiteralPath $financePreviewConfigPath -Raw|ConvertFrom-Json -AsHashtable
if(!$VercelCli){$VercelCli=$financePreviewConfig.vercelCliPath}
if(!$VercelCli -or !(Test-Path -LiteralPath $VercelCli)){throw 'Pass -VercelCli with the installed Vercel vc.js path.'}
if($financePreviewConfig.projectName -ne 'java-finance-category-ai-lab' -or $financePreviewConfig.teamSlug -ne 'pedro135he-7663s-projects'){throw 'Unexpected project; no deployment was started.'}
$financePreviewTeams=& node $VercelCli api /v2/teams
if($LASTEXITCODE -ne 0){throw 'Cannot verify the free hosting plan.'}
$financePreviewTeam=($financePreviewTeams|ConvertFrom-Json).teams|Where-Object id -EQ $financePreviewConfig.teamId
if($financePreviewTeam.billing.plan -ne 'hobby'){throw 'This deployment is restricted to the free Hobby team.'}
$financePreviewConfig.vercelCliPath=$VercelCli
$financePreviewProject=& node $VercelCli api ('/v9/projects/'+$financePreviewConfig.projectId+'?teamId='+$financePreviewConfig.teamId)
if($LASTEXITCODE -ne 0){throw 'Unable to verify the isolated project.'}
$financePreviewProject=$financePreviewProject|ConvertFrom-Json
if($financePreviewProject.name -ne $financePreviewConfig.projectName -or $financePreviewProject.link){throw 'Unexpected project name or Git connection.'}
$financePreviewSource=Join-Path $financePreviewPrivate 'source'
New-Item -ItemType Directory -Force -Path $financePreviewSource,(Join-Path $financePreviewSource 'finance_ai'),(Join-Path $financePreviewSource '.vercel') | Out-Null
foreach($financePreviewFile in @('app.py','.python-version','vercel.json','.vercelignore','requirements.lock')){
    Copy-Item -LiteralPath (Join-Path $financePreviewRoot ('category-ai/'+$financePreviewFile)) -Destination $financePreviewSource
}
Copy-Item -Path (Join-Path $financePreviewRoot 'category-ai/finance_ai/*.py') -Destination (Join-Path $financePreviewSource 'finance_ai')
Copy-Item -LiteralPath (Join-Path $financePreviewRoot 'category-ai/requirements.lock') -Destination (Join-Path $financePreviewSource 'requirements.txt')
@{projectId=$financePreviewConfig.projectId;orgId=$financePreviewConfig.teamId;projectName=$financePreviewConfig.projectName}|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $financePreviewSource '.vercel/project.json') -Encoding utf8
$financePreviewSecretPath=Join-Path $financePreviewPrivate 'service-token.clixml'
if(!(Test-Path -LiteralPath $financePreviewSecretPath)){
    $financePreviewNewToken=[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(36))
    $financePreviewNewToken|ConvertTo-SecureString -AsPlainText -Force|Export-Clixml -LiteralPath $financePreviewSecretPath
    $financePreviewNewToken=$null
}
if(!$WithoutModel){$financePreviewGroq=Import-Clixml -LiteralPath (Join-Path $financePreviewRoot '.tools/local-review/groq-key.clixml')}
$financePreviewToken=Import-Clixml -LiteralPath $financePreviewSecretPath
try {
    $financePreviewExpires=[DateTimeOffset]::UtcNow.AddDays(2).ToUnixTimeSeconds().ToString()
    $financePreviewVariables=@(
        @{key='CATEGORY_AI_TOKEN';value=[Net.NetworkCredential]::new('',$financePreviewToken).Password;type='sensitive'},
        @{key='CATEGORY_AI_PROVIDER';value=$(if($WithoutModel){'none'}else{'groq'});type='plain'},
        @{key='CATEGORY_AI_MODEL';value=$(if($WithoutModel){''}else{'openai/gpt-oss-20b'});type='plain'},
        @{key='CATEGORY_AI_LAB_EXPIRES_AT';value=$financePreviewExpires;type='plain'}
        @{key='CATEGORY_AI_MODE';value=$(if($ReleaseCandidate){'candidate'}else{'lab'});type='plain'}
    )
    if(!$WithoutModel){$financePreviewVariables+=@{key='GROQ_API_KEY';value=[Net.NetworkCredential]::new('',$financePreviewGroq).Password;type='sensitive'}}
    foreach($financePreviewVariable in $financePreviewVariables){
        $financePreviewVariable.target=@('preview')
        # Secrets travel over stdin to the authenticated CLI, never process arguments or source files.
        $financePreviewVariable|ConvertTo-Json -Compress|& node $VercelCli api ('/v10/projects/'+$financePreviewConfig.projectId+'/env?teamId='+$financePreviewConfig.teamId+'&upsert=true') --method POST --input - --silent
        if($LASTEXITCODE -ne 0){throw 'Preview environment configuration failed.'}
    }
    $financePreviewConfig.expiresAt=$financePreviewExpires
    $financePreviewConfig|ConvertTo-Json|Set-Content -LiteralPath $financePreviewConfigPath -Encoding utf8
} finally {
    if($financePreviewGroq){$financePreviewGroq.Dispose()};$financePreviewToken.Dispose();$financePreviewVariables=$null;$financePreviewVariable=$null
}
$financePreviewOutput=& node $VercelCli deploy --cwd $financePreviewSource --scope $financePreviewConfig.teamSlug --environment preview --yes 2> (Join-Path $financePreviewPrivate 'deploy.log')
if($LASTEXITCODE -ne 0){throw 'Preview deployment failed; inspect .tools/category-ai-cloud-preview/deploy.log.'}
$financePreviewText=($financePreviewOutput -join "`n").Trim()
if($financePreviewText.StartsWith('{')){
    $financePreviewResult=$financePreviewText|ConvertFrom-Json
    $financePreviewUrl=$financePreviewResult.deployment.url
    $financePreviewConfig.deploymentId=$financePreviewResult.deployment.id
    if($financePreviewResult.deployment.readyState -ne 'READY'){throw 'Preview is not ready.'}
}else{
    $financePreviewUrl=($financePreviewOutput|Where-Object {$_ -match '^https://[^/]+\.vercel\.app/?$'}|Select-Object -Last 1)
}
if(!$financePreviewUrl){throw 'No preview URL returned.'}
$financePreviewConfig.url=$financePreviewUrl
$financePreviewConfig|ConvertTo-Json|Set-Content -LiteralPath $financePreviewConfigPath -Encoding utf8
Write-Output ('Python preview: '+$financePreviewUrl+' — no production alias, database or bank credentials.')
