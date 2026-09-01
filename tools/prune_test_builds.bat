@echo off
setlocal EnableDelayedExpansion
rem ===========================================================================
rem  prune_test_builds.bat -- clear old mod downloads out of Downloads
rem
rem  Testing a push means downloading the pack again and unzipping it, so
rem  Downloads collects both: a stack of near-identical .zip files and a stack of
rem  extracted folders beside them. This removes the old ones. The download from
rem  commit 180 clears out 179 and everything before it.
rem
rem      prune_test_builds.bat            list, then ask before removing
rem      prune_test_builds.bat /list      show what it WOULD do, change nothing
rem      prune_test_builds.bat /y         do not ask
rem      prune_test_builds.bat /keep:0    remove ALL of them, newest included
rem      prune_test_builds.bat /keep:3    leave the three newest
rem      prune_test_builds.bat D:\stuff   somewhere other than Downloads
rem
rem  ---- run it with /list first ---------------------------------------------
rem
rem  It removes FOLDERS as well as files, with rd /s /q, and that does not go to
rem  the Recycle Bin. /list prints exactly what would go and touches nothing.
rem
rem  ---- what it will not touch ----------------------------------------------
rem
rem  Only entries whose name matches PATTERN. Everything else in Downloads --
rem  your tax return, the photos, the installers -- is invisible to it. It also
rem  refuses to run if PATTERN has been widened to something that matches
rem  everything, because "* in Downloads, recursive, no Recycle Bin" is not a
rem  mistake worth making twice.
rem
rem  ---- what "newest" means -------------------------------------------------
rem
rem  The entry's own timestamp, never its name. Downloads arrive as "pack.zip",
rem  then "pack (1).zip" from a browser that will not overwrite, or carrying a
rem  commit as "pack-a1b2c3d.zip". Sorting by name breaks on all three: (2)
rem  sorts after (10), a sha does not order at all, and re-downloading an older
rem  build can land it as (7).
rem
rem  Zips and extracted folders are counted separately, so KEEP=1 leaves you the
rem  newest zip AND the newest unzipped copy rather than whichever of the two
rem  happened to be touched last.
rem ===========================================================================

rem ---- configuration --------------------------------------------------------
set "PATTERN=*enderdragonanthro*"
set "KEEP=1"
rem ---------------------------------------------------------------------------

set "FOLDER=%USERPROFILE%\Downloads"
set "ASSUME=0"
set "DRYRUN=0"

:parse
if "%~1"=="" goto ready
set "ARG=%~1"
if /i "!ARG!"=="/y"    ( set "ASSUME=1" & shift & goto parse )
if /i "!ARG!"=="/list" ( set "DRYRUN=1" & shift & goto parse )
if /i "!ARG!"=="/?"    goto usage
if /i "!ARG!"=="/help" goto usage
if /i "!ARG:~0,6!"=="/keep:" ( set "KEEP=!ARG:~6!" & shift & goto parse )
set "FOLDER=!ARG!"
shift
goto parse

:usage
echo prune_test_builds.bat [/y] [/list] [/keep:N] [folder]
echo   Removes old "%PATTERN%" downloads, keeping the newest %KEEP% zip and folder.
echo   /keep:0 removes all of them.
exit /b 0

:ready
rem A pattern this broad would take the whole folder with it.
if "%PATTERN%"=="" goto toobroad
if "%PATTERN%"=="*" goto toobroad
if "%PATTERN%"=="*.*" goto toobroad
rem /keep:x would otherwise fail halfway through the listing, in the middle of a
rem comparison, with an error that names neither the switch nor the value.
echo %KEEP%| findstr /r "^[0-9][0-9]*$" >nul || ( echo ERROR: /keep: needs a whole number, not "%KEEP%". & exit /b 1 )
if not exist "!FOLDER!\" ( echo ERROR: no such folder: !FOLDER! & exit /b 1 )
pushd "!FOLDER!" 2>nul || ( echo ERROR: cannot enter !FOLDER! & exit /b 1 )

echo Folder : !CD!
echo Pattern: %PATTERN%
if "%KEEP%"=="0" (
    echo Keeping: nothing -- every match will be removed
) else (
    echo Keeping: the newest %KEEP% zip^(s^) and the newest %KEEP% folder^(s^)
)
echo.

set "DOOMED=0"
set "KEPT=0"
set "MATCHED=0"
set "FILES=0"
set "DIRS=0"
set "KB=0"

rem /o-d is "order by date, descending" -- newest first, so counting as we go
rem means the survivors are simply the first ones seen.
for /f "delims=" %%E in ('dir /b /o-d "%PATTERN%" 2^>nul') do (
    set /a "MATCHED+=1"
    set "KIND=file"
    if exist "%%E\" set "KIND=dir"

    if "!KIND!"=="dir" ( set /a "DIRS+=1" & set "N=!DIRS!" ) else ( set /a "FILES+=1" & set "N=!FILES!" )

    if !N! GTR %KEEP% (
        set /a "DOOMED+=1"
        rem Numbered variables rather than one delimited string: a name holding
        rem an ampersand would turn string-splitting into a command.
        set "DOOM_!DOOMED!=%%E"
        set "KIND_!DOOMED!=!KIND!"
        if "!KIND!"=="dir" ( echo   remove  [folder] %%E ) else ( echo   remove  [zip]    %%E )
    ) else (
        set /a "KEPT+=1"
        if "!KIND!"=="dir" ( echo   KEEP    [folder] %%E ) else ( echo   KEEP    [zip]    %%E )
    )
)

echo.
if !MATCHED! EQU 0 ( echo Nothing here matches "%PATTERN%". & popd & exit /b 0 )
if !DOOMED! EQU 0 ( echo Nothing to remove -- !MATCHED! matched, none were stale. & popd & exit /b 0 )

rem Measure before removing, so the report is a fact rather than an estimate.
rem Summed in KB: batch arithmetic is 32-bit and bytes would overflow at 2 GB.
for /l %%I in (1,1,!DOOMED!) do (
    for /f "delims=" %%E in ("!DOOM_%%I!") do (
        if "!KIND_%%I!"=="dir" (
            for /f "delims=" %%F in ('dir /b /s /a-d "%%E" 2^>nul') do set /a "KB+=%%~zF/1024"
        ) else (
            set /a "KB+=%%~zE/1024"
        )
    )
)
set /a "MB=!KB!/1024"

echo !DOOMED! item^(s^) to remove, about !MB! MB.
if "%DRYRUN%"=="1" ( echo /list: nothing was changed. & popd & exit /b 0 )

if "%ASSUME%"=="0" (
    echo Folders are removed with rd /s /q and do NOT go to the Recycle Bin.
    set /p "OK=Type Y to go ahead: "
    if /i not "!OK!"=="Y" ( echo Cancelled. Nothing was changed. & popd & exit /b 0 )
)

set "GONE=0"
set "STUCK=0"
for /l %%I in (1,1,!DOOMED!) do (
    for /f "delims=" %%E in ("!DOOM_%%I!") do (
        if "!KIND_%%I!"=="dir" ( rd /s /q "%%E" 2>nul ) else ( del /f /q "%%E" 2>nul )
        if exist "%%E" (
            echo   FAILED  %%E   -- open in Explorer, or still in use?
            set /a "STUCK+=1"
        ) else (
            set /a "GONE+=1"
        )
    )
)

echo.
echo Removed !GONE! of !DOOMED!, about !MB! MB back. Kept !KEPT!.
if !STUCK! GTR 0 echo !STUCK! could not be removed -- close anything using them and run it again.
popd
exit /b 0

:toobroad
echo REFUSING TO RUN.
echo PATTERN is "%PATTERN%", which matches everything in the folder.
echo This removes folders recursively and without the Recycle Bin, so it will
echo not act on a pattern that broad. Edit PATTERN near the top of this file.
exit /b 1
