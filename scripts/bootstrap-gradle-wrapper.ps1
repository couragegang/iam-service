# One-time: create gradle/wrapper/gradle-wrapper.jar and gradlew scripts if missing.
# Requires: PowerShell 5+, network. JDK 21 recommended for later ./gradlew check.
$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
Set-Location $root

$wrapperDir = Join-Path $root 'gradle\wrapper'
$jarPath = Join-Path $wrapperDir 'gradle-wrapper.jar'
if (-not (Test-Path $wrapperDir)) {
    New-Item -ItemType Directory -Path $wrapperDir -Force | Out-Null
}

if (-not (Test-Path $jarPath)) {
    $ver = '8.10.2'
    $url = "https://github.com/gradle/gradle/raw/v$ver/gradle/wrapper/gradle-wrapper.jar"
    Write-Host "Downloading gradle-wrapper.jar ..."
    Invoke-WebRequest -Uri $url -OutFile $jarPath -UseBasicParsing
}

$gradlewUrl = 'https://github.com/gradle/gradle/raw/v8.10.2/gradlew'
$gradlewBatUrl = 'https://github.com/gradle/gradle/raw/v8.10.2/gradlew.bat'
foreach ($pair in @(
        @{ Path = (Join-Path $root 'gradlew'); Url = $gradlewUrl }
        @{ Path = (Join-Path $root 'gradlew.bat'); Url = $gradlewBatUrl }
    )) {
    if (-not (Test-Path $pair.Path)) {
        Write-Host "Downloading $(Split-Path -Leaf $pair.Path) ..."
        Invoke-WebRequest -Uri $pair.Url -OutFile $pair.Path -UseBasicParsing
    }
}

Write-Host "Done. Run: .\gradlew.bat check   (with JDK 21 on PATH or JAVA_HOME)"
