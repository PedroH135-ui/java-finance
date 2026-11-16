# Offline preparation only. This script never calls Vercel/Neon or publishes anything.
$ErrorActionPreference='Stop'
$financeReleaseRoot=Split-Path $PSScriptRoot -Parent
$financeReleasePrivate=Join-Path $financeReleaseRoot '.tools/ai-production'
New-Item -ItemType Directory -Force -Path $financeReleasePrivate | Out-Null
$financeReleaseSecretPath=Join-Path $financeReleasePrivate 'service-token.clixml'
if(!(Test-Path -LiteralPath $financeReleaseSecretPath)){
    $financeReleaseToken=[Convert]::ToBase64String([Security.Cryptography.RandomNumberGenerator]::GetBytes(36))
    $financeReleaseToken|ConvertTo-SecureString -AsPlainText -Force|Export-Clixml -LiteralPath $financeReleaseSecretPath
    $financeReleaseToken=$null
}
$financeReleaseMigration=Join-Path $financeReleaseRoot 'backend/src/main/resources/db/migration/V9__bank_transfer_and_categories.sql'
$financeReleaseManifest=@{
    status='PREPARED_NOT_PUBLISHED'
    team='pedro135he-7663s-projects'
    baseCommit='a462d694cc824e8f7a30ac18b4e1b0d0876a6644'
    baseDeployment='dpl_7hwmwEg2mXsnbn2PEeD3uhdD99Mz'
    sourceCommit=(& git -C $financeReleaseRoot rev-parse HEAD)
    sourceDirty=[bool](& git -C $financeReleaseRoot status --porcelain)
    projects=@{java=@{name='java-finance';id='prj_grzypzumU1j1X4zUb1EeKLpHnYuq'};python=@{name='java-finance-category-ai-lab';id='prj_AKDwk7l6ep7THx6cTU43VHsxdhVP'}}
    pythonProductionUrl='https://java-finance-category-ai-lab.vercel.app'
    pythonEnvironment=@{CATEGORY_AI_MODE='production';CATEGORY_AI_PROVIDER='groq';CATEGORY_AI_MODEL='openai/gpt-oss-20b';CATEGORY_AI_TOKEN='<new independent production secret>';GROQ_API_KEY='<protected Groq key, configure only after approval>'}
    pythonMustBeAbsent=@('CATEGORY_AI_LAB_EXPIRES_AT','DB_*','POSTGRES_*','PLUGGY_*')
    javaEnvironment=@{CATEGORY_AI_ENABLED='true';CATEGORY_AI_URL='https://java-finance-category-ai-lab.vercel.app';CATEGORY_AI_ALLOWED_HOST='java-finance-category-ai-lab.vercel.app';CATEGORY_AI_TOKEN='<same new independent production secret>'}
    javaMustBeAbsent=@('CATEGORY_AI_PREVIEW_OIDC')
    migration=@{file='V9__bank_transfer_and_categories.sql';sha256=(Get-FileHash -LiteralPath $financeReleaseMigration -Algorithm SHA256).Hash;writesPostings=$false;automaticCategoryConfirmation=$false}
    publicationOrder=@('Obtain final approval for both production targets, credentials and minimized data flow','Confirm database recovery point before V9; do not execute old cloud provisioning scripts','Deploy Python production source and verify stable hostname, mode=production and unauthenticated POST=401','Configure only the Java AI variables, leaving DB/Pluggy/authentication untouched','Deploy reviewed Java container; Flyway V9 executes only at this approved step','Verify UI/session/manual category flow and preserved financial balances without bank synchronization')
    rollback='Disable CATEGORY_AI_ENABLED on Java and redeploy the reviewed version. Preserve V9 and all manually chosen categories; do not drop tables or reverse financial records.'
}
$financeReleaseJar=Join-Path $financeReleaseRoot 'backend/target/ai-production-package/java-finance-0.1.0.jar'
if(Test-Path -LiteralPath $financeReleaseJar){$financeReleaseManifest.localArtifactSha256=(Get-FileHash -LiteralPath $financeReleaseJar -Algorithm SHA256).Hash}
$financeReleaseManifest|ConvertTo-Json -Depth 8|Set-Content -LiteralPath (Join-Path $financeReleasePrivate 'manifest.json') -Encoding utf8
Write-Output 'Prepared .tools/ai-production/manifest.json and an independent protected service token. No production variables, database changes, deployments or credential transfers performed.'
