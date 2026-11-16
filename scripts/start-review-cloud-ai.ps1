param([string]$VercelCli,[switch]$Build)
$ErrorActionPreference='Stop'
$financeCloudReviewRoot=Split-Path $PSScriptRoot -Parent
$financeCloudReviewSource=Join-Path $financeCloudReviewRoot '.tools/category-ai-cloud-preview/source'
$financeCloudReviewConfig=Get-Content -LiteralPath (Join-Path $financeCloudReviewRoot '.tools/category-ai-cloud-preview/config.json') -Raw|ConvertFrom-Json
if(!$VercelCli){$VercelCli=$financeCloudReviewConfig.vercelCliPath}
if(!$VercelCli -or !(Test-Path -LiteralPath $VercelCli)){throw 'Pass -VercelCli with the installed Vercel vc.js path.'}
$financeCloudReviewArgs=@($VercelCli,'env','run','--cwd',$financeCloudReviewSource,'--scope','pedro135he-7663s-projects','-e','development','--','pwsh','-NoProfile','-File',(Join-Path $PSScriptRoot 'start-review-local.ps1'),'-RemoteAi','-Restart')
if($Build){$financeCloudReviewArgs+='-Build'}
# Vercel injects short-lived OIDC in memory; it is never written to source or passed as an argument.
& node @financeCloudReviewArgs
if($LASTEXITCODE -ne 0){throw 'Remote-advisor local app startup failed.'}
