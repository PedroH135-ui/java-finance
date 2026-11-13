$ErrorActionPreference='Stop'
$financeInstallRoot=Split-Path $PSScriptRoot -Parent
$financeInstallDirectory=Join-Path $financeInstallRoot '.tools/ollama-local'
$financeInstallArchive=Join-Path $financeInstallDirectory 'ollama-windows-amd64.zip'
$financeInstallBin=Join-Path $financeInstallDirectory 'bin'
$financeInstallHash='50fcf5ad54277f2fa5d173ebfc93bcb017adbc0c9ef2314daabc96ca9e6aa64e'
New-Item -ItemType Directory -Force -Path $financeInstallDirectory | Out-Null
if(!(Test-Path -LiteralPath $financeInstallArchive)){
    Invoke-WebRequest 'https://github.com/ollama/ollama/releases/download/v0.40.16/ollama-windows-amd64.zip' -OutFile $financeInstallArchive
}
if((Get-FileHash -LiteralPath $financeInstallArchive -Algorithm SHA256).Hash -ne $financeInstallHash){throw 'Unexpected archive hash; no executables were extracted.'}
if(Get-Process ollama -ErrorAction SilentlyContinue | Where-Object Path -EQ (Join-Path $financeInstallBin 'ollama.exe')){throw 'Stop this portable Ollama instance before reinstalling.'}
New-Item -ItemType Directory -Force -Path $financeInstallBin | Out-Null
$financeInstallZip=[IO.Compression.ZipFile]::OpenRead($financeInstallArchive)
try {
    # CPU-only files and licences; skip CUDA/Vulkan binaries unused on this station.
    foreach($financeInstallEntry in $financeInstallZip.Entries){
        if($financeInstallEntry.FullName -ne 'ollama.exe' -and $financeInstallEntry.FullName -notmatch '^lib/ollama/[^/]+$'){continue}
        $financeInstallTarget=[IO.Path]::GetFullPath((Join-Path $financeInstallBin $financeInstallEntry.FullName))
        if(!$financeInstallTarget.StartsWith([IO.Path]::GetFullPath($financeInstallBin)+[IO.Path]::DirectorySeparatorChar,[StringComparison]::OrdinalIgnoreCase)){throw 'Unexpected archive path.'}
        New-Item -ItemType Directory -Force -Path (Split-Path $financeInstallTarget -Parent) | Out-Null
        [IO.Compression.ZipFileExtensions]::ExtractToFile($financeInstallEntry,$financeInstallTarget,$true)
    }
} finally {$financeInstallZip.Dispose()}
Write-Output 'Portable Ollama v0.40.16 extracted with verified SHA-256. No global installation or PATH modification.'
