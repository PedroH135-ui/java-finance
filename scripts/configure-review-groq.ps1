$ErrorActionPreference='Stop'
$financeGroqDirectory=Join-Path (Split-Path $PSScriptRoot -Parent) '.tools/local-review'
New-Item -ItemType Directory -Force -Path $financeGroqDirectory | Out-Null
Write-Output 'Configure uma chave da sua conta Groq no plano Free. Não é necessário ativar o plano pago.'
Write-Output 'A chave será protegida pelo Windows (DPAPI), apenas para este usuário e computador.'
$financeGroqSecret=Read-Host 'Cole a chave Groq (entrada oculta; não envie pelo chat)' -AsSecureString
if($financeGroqSecret.Length -lt 20){throw 'Entrada vazia ou curta demais; nenhuma chave foi salva.'}
$financeGroqSecret | Export-Clixml -LiteralPath (Join-Path $financeGroqDirectory 'groq-key.clixml')
$financeGroqSecret.Dispose()
Write-Output 'Chave salva de forma protegida. Você pode avisar no chat: pronto. Nenhuma chamada à API foi feita.'
