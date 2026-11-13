param([switch]$UseGroq,[switch]$Tests)
$ErrorActionPreference='Stop'
$financeVectorRoot=Split-Path $PSScriptRoot -Parent
$financeVectorPython=Join-Path $financeVectorRoot '.tools/vector-rag-venv/Scripts/python.exe'
if(!(Test-Path -LiteralPath $financeVectorPython)){throw 'Install the separate vector-lab requirements first.'}
$financeVectorOldPath=$env:PYTHONPATH
$financeVectorOldPassword=$env:VECTOR_LAB_PASSWORD
$financeVectorOldGroq=$env:GROQ_API_KEY
try {
    $env:PYTHONPATH=(Join-Path $financeVectorRoot 'vector-lab')+';'+(Join-Path $financeVectorRoot 'category-ai')
    $financeVectorSecret=Import-Clixml -LiteralPath (Join-Path $financeVectorRoot '.tools/vector-rag/database-password.clixml')
    $env:VECTOR_LAB_PASSWORD=[Net.NetworkCredential]::new('',$financeVectorSecret).Password
    if($UseGroq){
        $financeVectorGroq=Import-Clixml -LiteralPath (Join-Path $financeVectorRoot '.tools/local-review/groq-key.clixml')
        $env:GROQ_API_KEY=[Net.NetworkCredential]::new('',$financeVectorGroq).Password
    }
    if($Tests){
        & $financeVectorPython -X utf8 -m pytest (Join-Path $financeVectorRoot 'vector-lab/tests') -q -p no:cacheprovider
    }else{
        $financeVectorArgs=@('-X','utf8','-m','finance_vector.compare','--root',$financeVectorRoot)
        if($UseGroq){$financeVectorArgs+='--groq'}
        & $financeVectorPython @financeVectorArgs
    }
    if($LASTEXITCODE -ne 0){throw 'The local vector experiment failed; no production fallback is permitted.'}
}finally{
    $env:PYTHONPATH=$financeVectorOldPath
    $env:VECTOR_LAB_PASSWORD=$financeVectorOldPassword
    $env:GROQ_API_KEY=$financeVectorOldGroq
    if($financeVectorSecret){$financeVectorSecret.Dispose()}
    if($financeVectorGroq){$financeVectorGroq.Dispose()}
}
