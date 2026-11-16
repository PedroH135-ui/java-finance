param([switch]$HealthOnly)
$ErrorActionPreference='Stop'
$financeRemoteRoot=Split-Path $PSScriptRoot -Parent
$financeRemotePrivate=Join-Path $financeRemoteRoot '.tools/category-ai-cloud-preview'
$financeRemoteConfig=Get-Content -LiteralPath (Join-Path $financeRemotePrivate 'config.json') -Raw|ConvertFrom-Json
$financeRemoteUri=[uri]$financeRemoteConfig.url
if($financeRemoteConfig.projectName -ne 'java-finance-category-ai-lab' -or $financeRemoteUri.Scheme -ne 'https' -or !$financeRemoteUri.Host.EndsWith('.vercel.app') -or $financeRemoteUri.UserInfo){throw 'Unexpected lab destination.'}
if(!$env:VERCEL_OIDC_TOKEN){throw 'Run this test through vercel env run in the linked lab directory. No protection settings need to change.'}
$financeRemoteHeaders=@{'x-vercel-trusted-oidc-idp-token'=$env:VERCEL_OIDC_TOKEN}
$financeRemoteWatch=[Diagnostics.Stopwatch]::StartNew()
$financeRemoteHealth=Invoke-RestMethod ($financeRemoteConfig.url+'/health') -Headers $financeRemoteHeaders -MaximumRedirection 0 -TimeoutSec 40
$financeRemoteWatch.Stop()
if($HealthOnly){$financeRemoteHealth|ConvertTo-Json;return}
$financeRemotePayload=@{description='Mercado Alfa';categories=@(@{id='food';name='Alimentação'});history=@()}|ConvertTo-Json -Depth 5
$financeRemoteUnauthorized=Invoke-WebRequest ($financeRemoteConfig.url+'/suggest') -Headers $financeRemoteHeaders -Method Post -ContentType 'application/json' -Body $financeRemotePayload -MaximumRedirection 0 -TimeoutSec 20 -SkipHttpErrorCheck
if($financeRemoteUnauthorized.StatusCode -ne 401){throw 'Classification without the service token was not denied.'}
$financeRemoteSecret=Import-Clixml -LiteralPath (Join-Path $financeRemotePrivate 'service-token.clixml')
try {
    $financeRemoteHeaders.Authorization='Bearer '+[Net.NetworkCredential]::new('',$financeRemoteSecret).Password
    $financeRemoteRule=Invoke-RestMethod ($financeRemoteConfig.url+'/suggest') -Headers $financeRemoteHeaders -Method Post -ContentType 'application/json' -Body $financeRemotePayload -MaximumRedirection 0 -TimeoutSec 20
    if($financeRemoteRule.categoryId -ne 'food' -or $financeRemoteRule.method -ne 'RULE' -or $financeRemoteRule.modelUsed){throw 'Remote deterministic classification failed.'}
    $financeRemoteRestaurant=@{description='Restaurante Sabor da Vila';categories=@(@{id='food';name='Alimentação'},@{id='transport';name='Transporte'});history=@()}|ConvertTo-Json -Depth 5
    $financeRemoteChoice=Invoke-RestMethod ($financeRemoteConfig.url+'/suggest') -Headers $financeRemoteHeaders -Method Post -ContentType 'application/json' -Body $financeRemoteRestaurant -MaximumRedirection 0 -TimeoutSec 20
    if($financeRemoteHealth.modelEnabled){
        if($financeRemoteChoice.categoryId -ne 'food' -or $financeRemoteChoice.provider -ne 'groq'){throw 'Remote fictitious LLM classification failed.'}
    }elseif($financeRemoteChoice.categoryId -or $financeRemoteChoice.modelUsed){throw 'Disabled LLM unexpectedly ran.'}
    $financeRemoteLarge='{"description":"'+('x'*70000)+'"}'
    $financeRemoteTooLarge=Invoke-WebRequest ($financeRemoteConfig.url+'/suggest') -Headers $financeRemoteHeaders -Method Post -ContentType 'application/json' -Body $financeRemoteLarge -MaximumRedirection 0 -TimeoutSec 20 -SkipHttpErrorCheck
    if($financeRemoteTooLarge.StatusCode -ne 413){throw 'Remote payload limit failed.'}
    $financeRemoteResult=@{url=$financeRemoteConfig.url;target='preview';status='UP';provider=$financeRemoteHealth.provider;mode=$financeRemoteHealth.mode;modelEnabled=$financeRemoteHealth.modelEnabled;healthElapsedMs=$financeRemoteWatch.ElapsedMilliseconds;withoutServiceToken=401;rule='Alimentação';restaurantCategory=$financeRemoteChoice.categoryId;largePayload=413;databaseAccess=$false}
    $financeRemoteResult|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $financeRemotePrivate 'test-result.json') -Encoding utf8
    $financeRemoteResult|ConvertTo-Json
} finally {
    $financeRemoteSecret.Dispose();$financeRemoteHeaders=$null
}
