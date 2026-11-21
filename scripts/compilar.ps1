$ErrorActionPreference = 'Stop'
$raiz = Split-Path -Parent $PSScriptRoot
Set-Location $raiz
$salida = Join-Path $raiz 'out'
if (-not (Test-Path $salida)) { New-Item -ItemType Directory -Path $salida | Out-Null }
$fuentes = Get-ChildItem -Recurse (Join-Path $raiz 'src') -Filter *.java | ForEach-Object { $_.FullName }
& javac -encoding UTF-8 -cp "lib\*" -d $salida $fuentes
if ($LASTEXITCODE -ne 0) { throw "Compilación fallida ($LASTEXITCODE)." }
Write-Output "OK: $($fuentes.Count) fuentes compiladas en out."
