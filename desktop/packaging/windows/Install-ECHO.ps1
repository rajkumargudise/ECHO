<#
  ECHO installer for Windows (per user, no administrator needed).
  Installs the app from the ECHO zip that sits next to this file, adds a Start menu entry and a desktop shortcut,
  and registers ECHO in Settings > Apps so it can be removed there.
  ECHO is a product of JARVEX (jarvex.in).
#>
param(
    [string]$TargetDir = (Join-Path $env:LOCALAPPDATA 'Programs\ECHO'),
    [string]$StartMenuDir = (Join-Path $env:APPDATA 'Microsoft\Windows\Start Menu\Programs\JARVEX'),
    [string]$DesktopDir = [Environment]::GetFolderPath('Desktop'),
    [switch]$NoDesktopShortcut,
    [switch]$NoRegistry,
    [switch]$Quiet
)

$ErrorActionPreference = 'Stop'
$here = Split-Path -Parent $MyInvocation.MyCommand.Path
$zip = Get-ChildItem -Path $here -Filter 'ECHO-*-windows-portable.zip' | Select-Object -First 1
if (-not $zip) { throw "Cannot find the ECHO zip next to this installer ($here)." }
$version = ($zip.BaseName -replace '^ECHO-', '' -replace '-windows-portable$', '')

function Say($text) { if (-not $Quiet) { Write-Host $text } }

Say "Installing ECHO $version to $TargetDir"
$running = Get-Process -Name 'ECHO' -ErrorAction SilentlyContinue | Where-Object { $_.Path -like "$TargetDir*" }
if ($running) { throw 'ECHO is running. Close it and run the installer again.' }

$temp = Join-Path ([IO.Path]::GetTempPath()) ('echo-setup-' + [guid]::NewGuid().ToString('N'))
try {
    Expand-Archive -Path $zip.FullName -DestinationPath $temp -Force
    $app = Join-Path $temp 'ECHO'
    if (-not (Test-Path (Join-Path $app 'ECHO.exe'))) { throw 'The zip does not contain ECHO.exe.' }
    if (Test-Path $TargetDir) { Remove-Item -Recurse -Force $TargetDir }   # replaces an older ECHO; your music and ECHO's data are elsewhere
    New-Item -ItemType Directory -Force -Path (Split-Path -Parent $TargetDir) | Out-Null
    Move-Item -Path $app -Destination $TargetDir
} finally {
    if (Test-Path $temp) { Remove-Item -Recurse -Force $temp }
}

Copy-Item -Path (Join-Path $here 'Uninstall-ECHO.ps1') -Destination (Join-Path $TargetDir 'Uninstall-ECHO.ps1') -Force
$exe = Join-Path $TargetDir 'ECHO.exe'

function New-Shortcut($folder, $name) {
    New-Item -ItemType Directory -Force -Path $folder | Out-Null
    $shell = New-Object -ComObject WScript.Shell
    $link = $shell.CreateShortcut((Join-Path $folder "$name.lnk"))
    $link.TargetPath = $exe
    $link.WorkingDirectory = $TargetDir
    $link.Description = 'ECHO music player by JARVEX'
    $link.Save()
}
New-Shortcut $StartMenuDir 'ECHO'
if (-not $NoDesktopShortcut) { New-Shortcut $DesktopDir 'ECHO' }

if (-not $NoRegistry) {
    $key = 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\ECHO-JARVEX'
    New-Item -Path $key -Force | Out-Null
    $props = @{
        DisplayName     = 'ECHO'
        DisplayVersion  = $version
        Publisher       = 'JARVEX'
        URLInfoAbout    = 'https://jarvex.in'
        InstallLocation = $TargetDir
        DisplayIcon     = $exe
        UninstallString = "powershell.exe -NoProfile -ExecutionPolicy Bypass -File `"$(Join-Path $TargetDir 'Uninstall-ECHO.ps1')`""
    }
    foreach ($name in $props.Keys) { Set-ItemProperty -Path $key -Name $name -Value $props[$name] }
    Set-ItemProperty -Path $key -Name 'NoModify' -Value 1 -Type DWord
    Set-ItemProperty -Path $key -Name 'NoRepair' -Value 1 -Type DWord
}

Say "ECHO $version is installed. Open it from the Start menu (JARVEX > ECHO)."
