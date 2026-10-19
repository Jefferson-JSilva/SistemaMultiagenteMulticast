param([string]$Interface, [int]$Grupo=247)
$ErrorActionPreference = 'Stop'
Push-Location -LiteralPath $PSScriptRoot
try {
    & "$PSScriptRoot/build.ps1"
    & java -cp 'build/classes' br.edu.cardio.UnitTests
    if ($LASTEXITCODE -ne 0) { throw 'Testes unitarios falharam.' }
    if ($Interface) {
        & java -cp 'build/classes' br.edu.cardio.IntegrationTests --interface $Interface --grupo $Grupo
        if ($LASTEXITCODE -ne 0) { throw 'Testes multicast falharam. Verifique interface e rede.' }
    } else { Write-Host 'Para testar multicast real: ./test.ps1 -Interface IP_LOCAL' }
} finally { Pop-Location }
