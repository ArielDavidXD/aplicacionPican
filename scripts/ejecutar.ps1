param(
    [Parameter(Mandatory = $true)]
    [ValidateSet('memoria', 'postgresql')]
    [string]$Modo
)

$ErrorActionPreference = 'Stop'
$raiz = Split-Path -Parent $PSScriptRoot
Set-Location $raiz

if (-not (Test-Path (Join-Path $raiz 'out\cu\pican\app\PicanApp.class'))) {
    throw 'No existe la aplicación compilada. Ejecute primero scripts\compilar.ps1'
}

if ($Modo -eq 'memoria') {
    $env:PICAN_PERSISTENCE = 'memory'
} else {
    $env:PICAN_PERSISTENCE = 'postgresql'
    if (-not $env:PICAN_DB_PASSWORD) {
        Write-Warning 'Defina PICAN_DB_PASSWORD antes de ejecutar en postgresql; este script no pasa -Dpican.db.password a Java.'
    }
}

& java -cp "out;lib\*" cu.pican.app.PicanApp
exit $LASTEXITCODE
