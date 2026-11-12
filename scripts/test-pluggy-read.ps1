param(
    [Parameter(Mandatory)][datetime]$From,
    [Parameter(Mandatory)][datetime]$To,
    [switch]$Execute
)
$ErrorActionPreference = 'Stop'
if (!$Execute) { throw 'Consulta real desativada. Informe -Execute para autorizar uma unica leitura, sem persistencia.' }
if ($To.Date -lt $From.Date -or ($To.Date - $From.Date).Days -gt 89) { throw 'Informe um periodo de ate 90 dias.' }
$probeRoot = Split-Path $PSScriptRoot -Parent
. (Join-Path $PSScriptRoot 'enter-dev.ps1')
Push-Location (Join-Path $probeRoot 'backend')
try {
    # Compile before requesting credentials. This starts no application or database.
    & .\mvnw.cmd -B -ntp '-Dfinance.build.directory=target/pluggy-probe' '-DskipTests' test-compile dependency:build-classpath '-Dmdep.includeScope=runtime' '-Dmdep.outputFile=target/pluggy-probe/runtime-classpath.txt'
    if ($LASTEXITCODE -ne 0) { throw 'Falha na preparacao do cliente Java; nenhuma consulta executada.' }
} finally { Pop-Location }
$probeBuild = Join-Path $probeRoot 'backend/target/pluggy-probe'
$probeClasspath = @((Join-Path $probeBuild 'test-classes'), (Join-Path $probeBuild 'classes'), ([IO.File]::ReadAllText((Join-Path $probeBuild 'runtime-classpath.txt')).Trim())) -join ';'
$probeStart = [Diagnostics.ProcessStartInfo]::new()
$probeStart.FileName = Join-Path $env:JAVA_HOME 'bin/java.exe'
$probeStart.UseShellExecute = $false
$probeStart.RedirectStandardOutput = $true
$probeStart.RedirectStandardError = $true
$probeStart.CreateNoWindow = $true
# Do not pass application, database or Vercel credentials into this isolated process.
$probeStart.Environment.Clear()
foreach ($name in @('SystemRoot','TEMP','TMP','USERPROFILE')) {
    $value = [Environment]::GetEnvironmentVariable($name)
    if ($value) { $probeStart.Environment[$name] = $value }
}
foreach ($name in @('PLUGGY_CLIENT_ID','PLUGGY_CLIENT_SECRET','PLUGGY_PROBE_ITEM_ID')) {
    $secureValue = Read-Host "Digite $name (entrada oculta, nao salva em arquivo)" -AsSecureString
    $pointer = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secureValue)
    try { $probeStart.Environment[$name] = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($pointer) }
    finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($pointer); $secureValue.Dispose() }
}
$probeStart.ArgumentList.Add('-cp')
$probeStart.ArgumentList.Add($probeClasspath)
$probeStart.ArgumentList.Add('br.com.javafinance.banking.PluggyReadProbe')
$probeStart.ArgumentList.Add($From.ToString('yyyy-MM-dd'))
$probeStart.ArgumentList.Add($To.ToString('yyyy-MM-dd'))
$probeProcess = [Diagnostics.Process]::new()
$probeProcess.StartInfo = $probeStart
try {
    [void]$probeProcess.Start()
    $probeStart.Environment.Clear()
    $probeOutputTask = $probeProcess.StandardOutput.ReadToEndAsync()
    $probeErrorTask = $probeProcess.StandardError.ReadToEndAsync()
    if (!$probeProcess.WaitForExit(100000)) { $probeProcess.Kill(); throw 'Tempo limite da consulta; nao houve persistencia.' }
    $probeOutput = $probeOutputTask.GetAwaiter().GetResult()
    [void]$probeErrorTask.GetAwaiter().GetResult()
    $resultLine = @($probeOutput -split "`r?`n" | Where-Object { $_.StartsWith('PLUGGY_PROBE_RESULT=') })
    if ($resultLine.Count -ne 1) { throw 'O cliente nao retornou um diagnostico seguro; saida bruta omitida.' }
    $safeResult = $resultLine[0].Substring('PLUGGY_PROBE_RESULT='.Length)
    [IO.File]::WriteAllText((Join-Path $probeRoot '.tools/pluggy-read-result.json'), $safeResult, [Text.UTF8Encoding]::new($false))
    Write-Output $safeResult
    if ($probeProcess.ExitCode -ne 0) { throw 'Consulta nao concluida. Consulte apenas o resultado seguro acima.' }
} finally { $probeStart.Environment.Clear(); $probeProcess.Dispose() }
