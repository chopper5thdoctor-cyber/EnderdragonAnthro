@echo off
setlocal EnableDelayedExpansion
REM ===========================================================================
REM  run.bat -- a server and two players, ready to look at each other
REM
REM  Everything in this mod is drawn on the player it belongs to, and all of it
REM  has been checked from inside that player's own head. The wings, the tail,
REM  the form and the court are things somebody ELSE sees, and a bug there is
REM  invisible in singleplayer by construction. So the default is now three
REM  windows rather than one.
REM
REM      run.bat              server, then both clients
REM      run.bat /single      ONE client, the way this script used to work
REM      run.bat /server      just the server
REM      run.bat /clients     just the clients (a server is already up)
REM      run.bat /stop        kill everything and free the folder
REM      run.bat /build       compile first, and stop if that fails
REM
REM  ---- the two players -----------------------------------------------------
REM
REM      Jean_Wide   the normal four-pixel arm
REM      Jean_Slim   the three-pixel arm
REM
REM  Those names are not decoration. An offline server derives a UUID from the
REM  name, and Minecraft picks the default skin from that UUID, so the model is
REM  a pure function of the name -- these two were computed, not guessed. Every
REM  layer this mod hangs off an arm has only ever been seen on a wide one, so
REM  the nameplate tells you which model a bug belongs to.
REM
REM  Each window is titled, so they are tellable apart on the taskbar, and each
REM  stays open when its game exits so a crash log is still readable.
REM ===========================================================================

pushd "%~dp0"

if not exist "gradlew.bat" (
    echo ERROR: no gradlew.bat here. Run this from the repository root.
    popd
    exit /b 1
)

if /i "%~1"=="/?"       goto usage
if /i "%~1"=="/help"    goto usage
if /i "%~1"=="/stop"    goto cleanup
if /i "%~1"=="/single"  goto single

set "WANT_SERVER=1"
set "WANT_CLIENTS=1"
if /i "%~1"=="/server"  set "WANT_CLIENTS=0"
if /i "%~1"=="/clients" set "WANT_SERVER=0"

if /i "%~1"=="/build" (
    echo Compiling first...
    call gradlew.bat build --console=plain
    if errorlevel 1 goto buildfailed
)

if "%WANT_SERVER%"=="0" goto clients

REM ---- the server ----------------------------------------------------------
REM
REM online-mode has to be off or the server rejects both dev clients with
REM "Failed to verify username", which reads as a mod problem and is not one.
call gradlew.bat prepareMultiplayer --console=plain -q

REM The EULA is an agreement, so it is not something this script accepts on
REM anybody's behalf. It IS something it can refuse to waste five minutes on.
if not exist "run\eula.txt" (
    echo.
    echo The server has never run here, so there is no run\eula.txt yet.
    echo Starting it once to create one. It will stop immediately, which is
    echo correct: put  eula=true  in that file and run this again.
    echo.
    call gradlew.bat runServer --console=plain
    popd
    exit /b 0
)
findstr /i /c:"eula=true" "run\eula.txt" >nul
if errorlevel 1 (
    echo.
    echo run\eula.txt does not say eula=true, so the server will refuse to
    echo start. That line is yours to add, not this script's:
    echo   %CD%\run\eula.txt
    popd
    exit /b 1
)

echo Starting the server...
start "EnderdragonAnthro server" cmd /k gradlew.bat runServer --console=plain

REM ---- wait for it to actually be listening --------------------------------
REM
REM A fixed sleep is a guess that is wrong on both sides: too short and the
REM clients open on "Connection refused", too long and you sit watching a
REM countdown. The port either answers or it does not.
echo Waiting for port 25565...
set "TRIES=0"
:waitport
set /a "TRIES+=1"
netstat -an | findstr /r /c:":25565 .*LISTENING" >nul
if not errorlevel 1 goto listening
if !TRIES! GTR 150 (
    echo.
    echo No answer on port 25565 after two and a half minutes. The server
    echo window is still open and it will say why.
    popd
    exit /b 1
)
REM ~1s a try, without needing a sleep binary.
ping -n 2 127.0.0.1 >nul
goto waitport

:listening
echo Server is up.

:clients
if "%WANT_CLIENTS%"=="0" goto holdopen

echo Starting Jean_Wide...
start "Jean_Wide (wide arms)" cmd /k gradlew.bat runClientOne --console=plain
REM Staggered: two clients unpacking natives and stitching an atlas at the same
REM moment is the slowest way to start either of them.
ping -n 6 127.0.0.1 >nul
echo Starting Jean_Slim...
start "Jean_Slim (slim arms)" cmd /k gradlew.bat runClientTwo --console=plain

:holdopen
echo.
echo Both clients auto-connect to localhost. If one lands on the title screen
echo instead, the server was not up yet -- Multiplayer, Direct Connect,
echo localhost.
echo.
echo Leave this window open. Closing everything from here is what keeps the
echo folder deletable afterwards.
echo.
pause
goto cleanup

REM ---- one client, in the foreground, the way this used to work -------------
:single
call gradlew.bat runClient
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
call :killall
popd
if not "%EXITCODE%"=="0" (
    echo.
    pause
)
exit /b %EXITCODE%

:buildfailed
echo.
echo ============================================================
echo  BUILD FAILED.
echo  Scroll up to the first line containing "error:" - that line
echo  names the file and what is wrong. Paste it to me.
echo ============================================================
echo.
pause
popd
exit /b 1

:cleanup
echo Cleaning up...
call :killall
echo Done.
popd
exit /b 0

REM Stop the daemon, then any java still holding a file under this folder --
REM which is what makes the directory immediately deletable rather than
REM "in use by another process" for the next ten minutes.
:killall
call gradlew.bat --stop >nul 2>&1
set "ROOT=%~dp0"
if "%ROOT:~-1%"=="\" set "ROOT=%ROOT:~0,-1%"
powershell -NoProfile -ExecutionPolicy Bypass -Command "$r='%ROOT%'; Get-CimInstance Win32_Process | Where-Object { ($_.Name -eq 'java.exe' -or $_.Name -eq 'javaw.exe') -and $_.CommandLine -and $_.CommandLine.Contains($r) } | ForEach-Object { Stop-Process -Id $_.ProcessId -Force }" 2>nul
exit /b 0

:usage
echo run.bat [/single ^| /server ^| /clients ^| /stop ^| /build]
echo   (no switch)  the server, then both clients: Jean_Wide and Jean_Slim
echo   /single      one client in this window, the way this script used to work
echo   /server      just the server
echo   /clients     just the clients, against a server already running
echo   /stop        kill the daemon and any stray java, freeing the folder
echo   /build       compile first, and stop if that fails
popd
exit /b 0
