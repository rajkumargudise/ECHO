@echo off
rem Double-click to install ECHO for the current user. ECHO is a product of JARVEX (jarvex.in).
powershell.exe -NoProfile -ExecutionPolicy Bypass -File "%~dp0Install-ECHO.ps1"
if errorlevel 1 (
  echo.
  echo The installation did not finish. See the message above.
) else (
  echo.
  echo Done.
)
pause
