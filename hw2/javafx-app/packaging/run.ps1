$ErrorActionPreference = "Stop"
$project = Split-Path -Parent $PSScriptRoot
$mvn = Join-Path $project "..\tools\apache-maven-3.9.11\bin\mvn.cmd"
Push-Location $project
try {
  & $mvn javafx:run
}
finally {
  Pop-Location
}
