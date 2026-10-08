$ErrorActionPreference = 'Stop'
$projectRoot = Split-Path $PSScriptRoot -Parent
$fontDirectory = Join-Path $projectRoot 'app/src/main/res/font'
$licenseDirectory = Join-Path $projectRoot 'third-party'
New-Item -ItemType Directory -Force -Path $fontDirectory, $licenseDirectory | Out-Null
Invoke-WebRequest -UseBasicParsing 'https://raw.githubusercontent.com/google/fonts/main/ofl/inter/Inter%5Bopsz%2Cwght%5D.ttf' -OutFile (Join-Path $fontDirectory 'inter.ttf')
Invoke-WebRequest -UseBasicParsing 'https://raw.githubusercontent.com/google/fonts/main/ofl/inter/OFL.txt' -OutFile (Join-Path $licenseDirectory 'Inter-OFL.txt')
Invoke-WebRequest -UseBasicParsing 'https://raw.githubusercontent.com/lucide-icons/lucide/main/LICENSE' -OutFile (Join-Path $licenseDirectory 'Lucide-LICENSE.txt')
Get-FileHash (Join-Path $fontDirectory 'inter.ttf') -Algorithm SHA256 | Select-Object Hash
