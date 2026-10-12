<#
.SYNOPSIS
  Build, install et pousse les donnees du jeu sur le telephone.

.EXAMPLE
  .\tools\deploy.ps1 -GameDir "D:\Games\Umineko"          # tout : build + install + donnees
  .\tools\deploy.ps1 -SkipPush                            # build + install seulement
  .\tools\deploy.ps1 -SkipBuild -GameDir "D:\Games\Umineko"  # install + donnees
#>
param(
    [string]$GameDir,          # dossier Steam patche 07th-Mod (0.u, arc*.nsa, BGM, movie...)
    [switch]$SkipBuild,
    [switch]$SkipInstall,
    [switch]$SkipPush,
    [string]$Serial            # optionnel : numero de serie adb si plusieurs appareils
)

$ErrorActionPreference = 'Stop'
$root = Split-Path -Parent $PSScriptRoot
$pkg = 'dev.umineko.android'
$remote = "/sdcard/Android/data/$pkg/files/game"

$sdk = if ($env:ANDROID_HOME) { $env:ANDROID_HOME } else { "$env:LOCALAPPDATA\Android\Sdk" }
$adb = Join-Path $sdk 'platform-tools\adb.exe'
if (-not (Test-Path $adb)) { throw "adb introuvable : $adb" }
$adbArgs = @()
if ($Serial) { $adbArgs += @('-s', $Serial) }
function Adb { & $adb @adbArgs @args; if ($LASTEXITCODE -ne 0) { throw "adb a echoue : $args" } }

if (-not $SkipPush) {
    if (-not $GameDir) { throw "Indique -GameDir (ou utilise -SkipPush)." }
    if (-not ((Test-Path (Join-Path $GameDir '0.u')) -or (Test-Path (Join-Path $GameDir '0.utf')))) {
        throw "Ni 0.u ni 0.utf dans $GameDir (mauvais dossier ?)"
    }
}

Write-Host '== Appareil =='
Adb devices

if (-not $SkipBuild) {
    Write-Host '== Build =='
    Push-Location $root
    try {
        & .\gradlew.bat :app:assembleDebug
        if ($LASTEXITCODE -ne 0) { throw 'Build echoue.' }
    } finally { Pop-Location }
}

if (-not $SkipInstall) {
    Write-Host '== Installation =='
    $apk = Get-ChildItem (Join-Path $root 'app\build\outputs\apk\debug') -Filter *.apk | Select-Object -First 1
    if (-not $apk) { throw 'APK introuvable : lance le build.' }
    Adb install -r $apk.FullName
}

if (-not $SkipPush) {
    Write-Host '== Dossier de donnees =='
    # L'appli doit creer le dossier elle-meme, sinon il appartient a shell (EACCES).
    $exists = (& $adb @adbArgs shell "[ -d $remote ] && echo yes") -match 'yes'
    if (-not $exists) {
        Write-Host "Premier lancement de l'appli pour creer le dossier..."
        Adb shell am start -n "$pkg/.MainActivity" | Out-Null
        for ($i = 0; $i -lt 20 -and -not $exists; $i++) {
            Start-Sleep -Seconds 1
            $exists = (& $adb @adbArgs shell "[ -d $remote ] && echo yes") -match 'yes'
        }
        if (-not $exists) { throw "Le dossier $remote n'a pas ete cree par l'appli." }
    }

    Write-Host '== Donnees (environ 9 Go, --sync saute les fichiers deja a jour) =='
    Adb push --sync "$GameDir\." "$remote/"
}

Write-Host '== Lancement =='
Adb shell am start -n "$pkg/.MainActivity" | Out-Null
Write-Host 'Termine.'
