param([string]$Destination='.tools/category-cloud-package')
$ErrorActionPreference='Stop'
$financeRoot=Split-Path $PSScriptRoot -Parent
$financePrivateRoot=[IO.Path]::GetFullPath((Join-Path $financeRoot '.tools'))+[IO.Path]::DirectorySeparatorChar
$financeDestination=[IO.Path]::GetFullPath((Join-Path $financeRoot $Destination))
if(!$financeDestination.StartsWith($financePrivateRoot,[StringComparison]::OrdinalIgnoreCase)){
    throw 'O pacote precisa ficar em uma subpasta de .tools neste projeto.'
}
if(Test-Path -LiteralPath $financeDestination){throw 'Escolha um destino novo para evitar arquivos privados ou antigos no pacote.'}
$financeSource=Join-Path $financeRoot 'category-ai'
$financeFiles=@('app.py','cloud_app.py','tasks.py','build_cloud_rag.py','.python-version','.vercelignore')
foreach($financeFile in $financeFiles){
    if(!(Test-Path -LiteralPath (Join-Path $financeSource $financeFile))){throw 'Fonte pública obrigatória ausente.'}
}
New-Item -ItemType Directory -Path $financeDestination -Force|Out-Null
New-Item -ItemType Directory -Path (Join-Path $financeDestination 'finance_ai')|Out-Null
foreach($financeFile in $financeFiles){Copy-Item -LiteralPath (Join-Path $financeSource $financeFile) -Destination $financeDestination}
foreach($financeFile in Get-ChildItem -LiteralPath (Join-Path $financeSource 'finance_ai') -File -Filter '*.py'){
    Copy-Item -LiteralPath $financeFile.FullName -Destination (Join-Path $financeDestination 'finance_ai')
}
foreach($financeFile in @('pyproject.toml','uv.lock','vercel.json')){
    Copy-Item -LiteralPath (Join-Path $financeSource "cloud-deploy/$financeFile") -Destination $financeDestination
}
Write-Output "Pacote cloud preparado em $financeDestination. Nenhum ambiente foi vinculado, alterado ou publicado."
