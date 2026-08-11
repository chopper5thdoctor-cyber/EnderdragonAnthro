@echo off
REM Launches the dev Minecraft client, then cleans up any leftover Java
REM processes so the folder is immediately deletable.
setlocal
pushd "%~dp0"

call gradlew.bat runClient %*

echo.
echo Client closed - cleaning up...
call gradlew.bat --stop >nul 2>&1

set "ROOT=%~dp0"
if "%ROOT:~-1%"=="\" set "ROOT=%ROOT:~0,-1%"
powershell -NoProfile -ExecutionPolicy Bypass -Command "$r='%ROOT%'; Get-CimInstance Win32_Process | Where-Object { ($_.Name -eq 'java.exe' -or $_.Name -eq 'javaw.exe') -and $_.CommandLine -and $_.CommandLine.Contains($r) } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }" 2>nul

popd
echo Done.
