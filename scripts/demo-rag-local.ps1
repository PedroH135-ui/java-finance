param([switch]$UseGroq)
$ErrorActionPreference='Stop'
$financeDemoRoot=Split-Path $PSScriptRoot -Parent
$financeDemoPython=Join-Path $financeDemoRoot '.tools/category-ai-venv/Scripts/python.exe'
if(!(Test-Path -LiteralPath $financeDemoPython)){throw 'Prepare the local Python environment before running the demo.'}
$financeDemoOldPath=$env:PYTHONPATH
$financeDemoOldKey=$env:GROQ_API_KEY
try {
    $env:PYTHONPATH=Join-Path $financeDemoRoot 'category-ai'
    $financeDemoProvider='none'
    if($UseGroq){
        $financeDemoSecret=Import-Clixml -LiteralPath (Join-Path $financeDemoRoot '.tools/local-review/groq-key.clixml')
        $env:GROQ_API_KEY=[Net.NetworkCredential]::new('',$financeDemoSecret).Password
        $financeDemoProvider='groq'
    }
    $financeDemoOutput=Join-Path $financeDemoRoot ('.tools/local-review/rag-demo-'+$financeDemoProvider)
    & $financeDemoPython -X utf8 -m finance_ai.explain_demo --provider $financeDemoProvider --output $financeDemoOutput
    if($LASTEXITCODE -ne 0){throw 'The fictitious demonstration had a divergence; inspect its local result.json.'}
    Write-Output ('Report: '+(Join-Path $financeDemoOutput 'index.html'))
} finally {
    $env:PYTHONPATH=$financeDemoOldPath
    $env:GROQ_API_KEY=$financeDemoOldKey
    if($financeDemoSecret){$financeDemoSecret.Dispose()}
}
