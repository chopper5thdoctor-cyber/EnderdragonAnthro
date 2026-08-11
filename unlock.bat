@echo off
REM Stops any Java process this project started, so the folder can be deleted.
REM Only touches processes whose command line points at THIS folder - your own
REM Minecraft launcher and other Java apps are left alone.
setlocal
set "ROOT=%~dp0"
if "%ROOT:~-1%"=="\" set "ROOT=%ROOT:~0,-1%"

echo Stopping Gradle daemons...
call "%~dp0gradlew.bat" --stop >nul 2>&1

echo Stopping Java processes launched from this folder...
powershell -NoProfile -ExecutionPolicy Bypass -Command "$r='%ROOT%'; $p = Get-CimInstance Win32_Process | Where-Object { ($_.Name -eq 'java.exe' -or $_.Name -eq 'javaw.exe') -and $_.CommandLine -and $_.CommandLine.Contains($r) }; if ($p) { $p | ForEach-Object { Write-Host ('  stopping PID ' + $_.ProcessId); Stop-Process -Id $_.ProcessId -Force } } else { Write-Host '  none found' }"

echo.
echo Done - this folder can be deleted now.
pause
