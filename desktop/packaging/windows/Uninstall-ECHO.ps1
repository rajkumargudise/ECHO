<#
  Removes ECHO from this computer. Your music and your ECHO data (favorites, playlists) are not touched,
  unless you add -RemoveData.   ECHO is a product of JARVEX (jarvex.in).
#>
param(
    [string]$TargetDir = (Split-Path -Parent $MyInvocation.MyCommand.Path),
    [string]$StartMenuDir = (Join-Path $env:APPDATA 'Microsoft\Windows\Start Menu\Programs\JARVEX'),
    [string]$DesktopDir = [Environment]::GetFolderPath('Desktop'),
    [switch]$NoRegistry,
    [switch]$RemoveData
)

$ErrorActionPreference = 'Stop'
$running = Get-Process -Name 'ECHO' -ErrorAction SilentlyContinue | Where-Object { $_.Path -like "$TargetDir*" }
if ($running) { throw 'ECHO is running. Close it and run the uninstaller again.' }

Remove-Item -Force -ErrorAction SilentlyContinue (Join-Path $StartMenuDir 'ECHO.lnk'), (Join-Path $DesktopDir 'ECHO.lnk')
if ((Test-Path $StartMenuDir) -and -not (Get-ChildItem $StartMenuDir)) { Remove-Item -Force $StartMenuDir }
if (-not $NoRegistry) { Remove-Item -Path 'HKCU:\Software\Microsoft\Windows\CurrentVersion\Uninstall\ECHO-JARVEX' -Force -ErrorAction SilentlyContinue }
if ($RemoveData) { Remove-Item -Recurse -Force -ErrorAction SilentlyContinue (Join-Path $env:APPDATA 'ECHO') }

# This script lives inside the folder it removes, so the folder is deleted by a short-lived helper after this script ends.
$cmd = "ping 127.0.0.1 -n 3 > nul & rmdir /s /q `"$TargetDir`""
Start-Process -WindowStyle Hidden -FilePath 'cmd.exe' -ArgumentList '/c', $cmd
Write-Host 'ECHO has been removed.'
