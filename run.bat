@echo off
REM Launches the dev Minecraft client, then cleans up any leftover Java
REM processes so the folder is immediately deletable.
setlocal
pushd "%~dp0"

call gradlew.bat runClient %*
set "EXITCODE=%ERRORLEVEL%"

echo.
if not "%EXITCODE%"=="0" (
    echo ============================================================
    echo  BUILD FAILED ^(exit code %EXITCODE%^).
    echo  Scroll up to the first line containing "error:" - that line
    echo  names the file and what is wrong. Paste it to me.
    echo ============================================================
) else (
    echo Client closed - cleaning up...
)

call gradlew.bat --stop >nul 2>&1

set "ROOT=%~dp0"
if "%ROOT:~-1%"=="\" set "ROOT=%ROOT:~0,-1%"
powershell -NoProfile -ExecutionPolicy Bypass -Command "$r='%ROOT%'; Get-CimInstance Win32_Process | Where-Object { ($_.Name -eq 'java.exe' -or $_.Name -eq 'javaw.exe') -and $_.CommandLine -and $_.CommandLine.Contains($r) } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }" 2>nul

popd
echo Done.

REM Hold the window open on failure - otherwise the error scrolls past and
REM the console closes with it, which is no help to anybody.
if not "%EXITCODE%"=="0" (
    echo.
    pause
)
exit /b %EXITCODE%
