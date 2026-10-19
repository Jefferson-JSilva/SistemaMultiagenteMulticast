param(
    [Parameter(Mandatory=$true, Position=0)]
    [ValidateSet('Coordenador','Tabagismo','Sedentarismo','Pressao','Imc')]
    [string]$Processo,
    [Parameter(ValueFromRemainingArguments=$true)] [string[]]$Opcoes
)
$ErrorActionPreference = 'Stop'
Push-Location -LiteralPath $PSScriptRoot
try {
    & java -cp 'build/classes' "br.edu.cardio.$Processo" @Opcoes
    if ($LASTEXITCODE -ne 0) { throw "Processo terminou com codigo $LASTEXITCODE" }
} finally { Pop-Location }
