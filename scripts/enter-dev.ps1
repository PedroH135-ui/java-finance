# Execute com dot-source: . .\scripts\enter-dev.ps1
$financeRoot = Split-Path $PSScriptRoot -Parent
$financeJdk = Get-ChildItem -LiteralPath (Join-Path $financeRoot '.tools') -Directory -Filter 'jdk-21*' -ErrorAction SilentlyContinue | Select-Object -First 1
if ($financeJdk) {
    $env:JAVA_HOME = $financeJdk.FullName
} elseif (!$env:JAVA_HOME -or !(Test-Path -LiteralPath (Join-Path $env:JAVA_HOME 'bin/javac.exe'))) {
    throw 'Instale um JDK 21 e configure JAVA_HOME, ou extraia-o em .tools/jdk-21*.'
}
$financeJavaBin = Join-Path $env:JAVA_HOME 'bin'
$financeJavacVersion = & (Join-Path $financeJavaBin 'javac.exe') -version 2>&1
if ($LASTEXITCODE -ne 0 -or "$financeJavacVersion" -notmatch '^javac 21\.') {
    throw "JDK 21 necessario. Encontrado: $financeJavacVersion"
}
if (($env:PATH -split ';') -notcontains $financeJavaBin) {
    $env:PATH = "$financeJavaBin;$env:PATH"
}
$env:MAVEN_USER_HOME = Join-Path $financeRoot '.tools/maven'
# Evita falhas AF_UNIX em diretorios temporarios redirecionados no Windows.
$financeSocketDirectory = Join-Path $financeRoot '.tools/tmp'
New-Item -ItemType Directory -Path $financeSocketDirectory -Force | Out-Null
$financeSocketOption = "`"-Djdk.net.unixdomain.tmpdir=$financeSocketDirectory`""
if (!$env:JAVA_TOOL_OPTIONS -or !$env:JAVA_TOOL_OPTIONS.Contains($financeSocketOption)) {
    $env:JAVA_TOOL_OPTIONS = "$env:JAVA_TOOL_OPTIONS $financeSocketOption".Trim()
}
$financeMavenRepository = "-Dmaven.repo.local=`"$(Join-Path $financeRoot '.tools/m2')`""
if (!$env:MAVEN_OPTS -or !$env:MAVEN_OPTS.Contains($financeMavenRepository)) {
    $env:MAVEN_OPTS = "$env:MAVEN_OPTS $financeMavenRepository".Trim()
}
Write-Host "Ambiente Java Finance ativo neste terminal: $env:JAVA_HOME"
