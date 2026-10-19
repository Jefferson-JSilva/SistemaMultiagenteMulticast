$ErrorActionPreference = 'Stop'
Push-Location -LiteralPath $PSScriptRoot
try {
    New-Item -ItemType Directory -Force -Path 'build/classes' | Out-Null
    $taskSources = @(Get-ChildItem -LiteralPath 'src' -Filter '*.java' -Recurse | ForEach-Object { $_.FullName })
    $taskSources += @(Get-ChildItem -LiteralPath 'test' -Filter '*.java' -Recurse | ForEach-Object { $_.FullName })
    & javac --release 17 -encoding UTF-8 -Xlint:all -d 'build/classes' @taskSources
    if ($LASTEXITCODE -ne 0) { throw 'Falha de compilacao.' }
    Write-Host 'Compilado com sucesso para Java 17 em build/classes.'
} finally { Pop-Location }
