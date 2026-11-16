$ErrorActionPreference='Stop'
$financeRoot=Split-Path $PSScriptRoot -Parent
$financeSecretDirectory=Join-Path $financeRoot '.tools/ai-production'
New-Item -ItemType Directory -Force -Path $financeSecretDirectory|Out-Null
Write-Output 'Cole somente a NOVA chave Groq gerada no painel oficial. A entrada fica oculta.'
Write-Output 'Nao envie a chave pelo chat. Ela sera salva protegida pelo Windows (DPAPI), fora do Git.'
$financeNewKey=Read-Host 'Nova chave Groq de producao' -AsSecureString
try {
    $financeNewValue=[Net.NetworkCredential]::new('',$financeNewKey).Password
    if($financeNewValue.Length -lt 30 -or !$financeNewValue.StartsWith('gsk_')){throw 'Formato invalido; nenhuma chave foi salva.'}
    $financeOldPath=Join-Path $financeRoot '.tools/local-review/groq-key.clixml'
    if(Test-Path -LiteralPath $financeOldPath){
        $financeOldKey=Import-Clixml -LiteralPath $financeOldPath
        try {
            if($financeNewValue -eq [Net.NetworkCredential]::new('',$financeOldKey).Password){throw 'Esta e a chave antiga. Gere uma nova chave no painel; nenhuma alteracao foi salva.'}
        }finally{$financeOldKey.Dispose()}
    }
    $financeNewKey|Export-Clixml -LiteralPath (Join-Path $financeSecretDirectory 'groq-key.clixml')
    @{configuredAtUtc=[DateTime]::UtcNow.ToString('o');differentFromPreviousKey=$true}|ConvertTo-Json|Set-Content -LiteralPath (Join-Path $financeSecretDirectory 'groq-key-status.json')
    Write-Output 'Nova chave protegida. Feche a janela que exibe a chave na Groq e avise: pronto.'
    Write-Output 'A chave antiga ainda nao foi revogada; isso sera feito depois de validar a substituicao.'
}finally{$financeNewValue=$null;$financeNewKey.Dispose()}
Read-Host 'Pressione Enter para fechar'|Out-Null
