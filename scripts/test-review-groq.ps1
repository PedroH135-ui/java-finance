$ErrorActionPreference='Stop'
$financeCloudRoot=Split-Path $PSScriptRoot -Parent
$financeCloudPrivate=Join-Path $financeCloudRoot '.tools/local-review'
try {
    $financeCloudSecret=Import-Clixml -LiteralPath (Join-Path $financeCloudPrivate 'groq-key.clixml')
    $env:GROQ_API_KEY=[Net.NetworkCredential]::new('',$financeCloudSecret).Password
    $env:PYTHONPATH=Join-Path $financeCloudRoot 'category-ai'
    & (Join-Path $financeCloudRoot '.tools/category-ai-venv/Scripts/python.exe') -X utf8 -m finance_ai.evaluate_local --provider groq > (Join-Path $financeCloudPrivate 'groq-evaluation.json')
    $financeCloudExit=$LASTEXITCODE
} finally {
    Remove-Item Env:GROQ_API_KEY -ErrorAction SilentlyContinue
    if($financeCloudSecret){$financeCloudSecret.Dispose();$financeCloudSecret=$null}
}
Get-Content -LiteralPath (Join-Path $financeCloudPrivate 'groq-evaluation.json')
if($financeCloudExit -ne 0){throw 'A avaliação não correspondeu a todos os resultados esperados; consulte o relatório local.'}
