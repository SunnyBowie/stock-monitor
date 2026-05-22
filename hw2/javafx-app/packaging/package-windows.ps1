param(
  [switch]$SkipMaven
)

$ErrorActionPreference = "Stop"

$project = Split-Path -Parent $PSScriptRoot
$mvn = Join-Path $project "..\tools\apache-maven-3.9.11\bin\mvn.cmd"
$javaHome = "D:\java21"
$jpackage = Join-Path $javaHome "bin\jpackage.exe"

Push-Location $project
try {
  if (-not $SkipMaven) {
    & $mvn -DskipTests package
  }

  $target = Join-Path $project "target"
  $installer = Join-Path $target "installer"
  $input = Join-Path $target "jpackage-input"
  Remove-Item -LiteralPath $installer -Recurse -Force -ErrorAction SilentlyContinue
  Remove-Item -LiteralPath $input -Recurse -Force -ErrorAction SilentlyContinue
  New-Item -ItemType Directory -Force -Path $installer, $input | Out-Null

  Copy-Item -LiteralPath (Join-Path $target "stock-monitor-javafx-0.4.0.jar") -Destination $input
  Copy-Item -Path (Join-Path $target "dependency\*.jar") -Destination $input

  $modulePath = "$input"
  & $jpackage `
    --type app-image `
    --name StockMonitorJavaFX `
    --dest $installer `
    --input $input `
    --module-path $modulePath `
    --module com.stockmonitor/com.stockmonitor.ui.StockMonitorApp `
    --add-modules jdk.crypto.ec `
    --java-options "-Dfile.encoding=UTF-8" `
    --java-options "-Djava.net.preferIPv4Stack=true" `
    --vendor "Stock Monitor Course Project" `
    --app-version "0.4.0"

  Write-Host "Packaged app image:" (Join-Path $installer "StockMonitorJavaFX")
}
finally {
  Pop-Location
}
