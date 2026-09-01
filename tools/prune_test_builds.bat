@echo off
setlocal EnableDelayedExpansion
rem ===========================================================================
rem  prune_test_builds.bat -- keep the newest test build, bin the rest
rem
rem  Testing a mod means downloading it again, and the copies pile up. This
rem  keeps the newest build and deletes the ones you will never run again, so
rem  the build from commit 180 clears out 179 and everything before it.
rem
rem  Drop it in the folder you download into and double-click it, or:
rem
rem      prune_test_builds.bat                 ask before deleting
rem      prune_test_builds.bat /y              do not ask
rem      prune_test_builds.bat /list           show what it would do, change nothing
rem      prune_test_builds.bat /keep:3         leave the three newest
rem      prune_test_builds.bat /bygroup        see below
rem      prune_test_builds.bat D:\mc\mods      work on that folder instead
rem
rem  Combine freely:  prune_test_builds.bat /y /keep:2 D:\mc\mods
rem
rem  ---- what it will not touch ----------------------------------------------
rem
rem  Only files matching PATTERN, minus anything containing EXCLUDE. Every other
rem  mod in the folder is invisible to it, which is the point: fabric-api and
rem  sodium live in the same directory and must never be considered old copies
rem  of anything.
rem
rem  ---- newest by TIMESTAMP, not by name ------------------------------------
rem
rem  Builds arrive under all sorts of names -- "mod.jar", then "mod (1).jar"
rem  from a browser that will not overwrite, or "mod-a1b2c3d.jar" and
rem  "mod-180.jar" if the name carries the commit. Sorting by name gets every
rem  one of those wrong in a different way: (2) sorts after (10), a sha does not
rem  order at all, and a re-download of an older build can land as (7).
rem
rem  So the file's own modification time decides, and the naming scheme stops
rem  mattering.
rem
rem  ---- /bygroup -------------------------------------------------------------
rem
rem  By default every match is one family: newest kept, rest deleted. That is
rem  what you want when the folder holds one mod under many names.
rem
rem  /bygroup instead groups by name up to the first "(" -- so "mod.jar" and
rem  "mod (1).jar" are one group while "mod-sources.jar" is another, each
rem  keeping its own newest. Use it if the folder legitimately holds several
rem  different artifacts you all want to keep.
rem
rem  ---- the rename at the end -----------------------------------------------
rem
rem  The first download is the one with the clean name, so it is also the oldest
rem  and the first to go. Without the rename you would be left holding
rem  "mod (3).jar", then "mod (7).jar", with the number climbing forever. The
rem  survivor is put back to the plain name so the next round starts from one.
rem ===========================================================================

rem ---- configuration --------------------------------------------------------
set "PATTERN=enderdragonanthro*.jar"
rem Anything containing this is left alone entirely. The build produces a
rem sources jar beside the mod jar, and in family mode it would otherwise be
rem deleted as an older copy of it. Set to nothing to disable.
set "EXCLUDE=-sources"
set "KEEP=1"
rem ---------------------------------------------------------------------------

set "FOLDER=%~dp0"
set "ASSUME=0"
set "DRYRUN=0"
set "BYGROUP=0"

:parse
if "%~1"=="" goto ready
set "ARG=%~1"
if /i "!ARG!"=="/y"       ( set "ASSUME=1"  & shift & goto parse )
if /i "!ARG!"=="/list"    ( set "DRYRUN=1"  & shift & goto parse )
if /i "!ARG!"=="/bygroup" ( set "BYGROUP=1" & shift & goto parse )
if /i "!ARG!"=="/?"       goto usage
if /i "!ARG!"=="/help"    goto usage
if /i "!ARG:~0,6!"=="/keep:" ( set "KEEP=!ARG:~6!" & shift & goto parse )
set "FOLDER=!ARG!"
shift
goto parse

:usage
echo prune_test_builds.bat [/y] [/list] [/keep:N] [/bygroup] [folder]
echo   Keeps the newest %KEEP% build matching "%PATTERN%" and deletes the rest.
exit /b 0

:ready
if not exist "!FOLDER!\" ( echo ERROR: no such folder: !FOLDER! & exit /b 1 )
pushd "!FOLDER!" 2>nul || ( echo ERROR: cannot enter !FOLDER! & exit /b 1 )

echo Folder : !CD!
if "!BYGROUP!"=="1" (
    echo Pattern: %PATTERN%   keeping the newest %KEEP% per name
) else (
    echo Pattern: %PATTERN%   keeping the newest %KEEP% overall
)
if defined EXCLUDE echo Skipping anything containing "%EXCLUDE%"
echo.

set "DOOMED=0"
set "KEPT=0"
set "MATCHED=0"

rem /o-d is "order by date, descending" -- newest first, so the first time a
rem group is seen it is that group's survivor and everything after it is older.
for /f "delims=" %%F in ('dir /b /a-d /o-d "%PATTERN%" 2^>nul') do (
    set "SKIP=0"
    if defined EXCLUDE (
        set "CHECK=%%F"
        if not "!CHECK:%EXCLUDE%=!"=="%%F" set "SKIP=1"
    )
    if "!SKIP!"=="0" (
        set /a "MATCHED+=1"
        if "!BYGROUP!"=="1" (
            call :groupkey "%%~nF" "%%~xF"
        ) else (
            set "KEY=%%F" & set "VKEY=ALL"
        )

        rem Indirect read: the inner FOR pastes the key in as literal text, so
        rem !COUNT_<key>! becomes a name delayed expansion can actually resolve.
        set "N="
        for /f "delims=" %%K in ("!VKEY!") do set "N=!COUNT_%%K!"
        if not defined N set "N=0"
        set /a "N+=1"
        for /f "delims=" %%K in ("!VKEY!") do set "COUNT_%%K=!N!"

        if !N! LEQ %KEEP% (
            set /a "KEPT+=1"
            echo   keep    %%F
        ) else (
            set /a "DOOMED+=1"
            rem Numbered variables rather than one delimited string: a filename
            rem holding an ampersand would turn string-splitting into a command.
            set "DOOM_!DOOMED!=%%F"
            echo   delete  %%F
        )
    )
)

echo.
if !MATCHED! EQU 0 ( echo Nothing matched "%PATTERN%" here. & popd & exit /b 0 )
if !DOOMED! EQU 0 (
    echo Nothing to prune -- !MATCHED! file^(s^) matched, none were stale.
    popd & exit /b 0
)
if "%DRYRUN%"=="1" (
    echo /list: !DOOMED! file^(s^) would be deleted. Nothing was changed.
    popd & exit /b 0
)
if "%ASSUME%"=="0" (
    echo About to delete !DOOMED! file^(s^) listed above.
    set /p "OK=Type Y to go ahead: "
    if /i not "!OK!"=="Y" ( echo Cancelled. Nothing was changed. & popd & exit /b 0 )
)

set "GONE=0"
set "STUCK=0"
for /l %%I in (1,1,!DOOMED!) do (
    for /f "delims=" %%F in ("!DOOM_%%I!") do (
        del /f /q "%%F" 2>nul
        if exist "%%F" (
            echo   FAILED  %%F   -- is Minecraft running?
            set /a "STUCK+=1"
        ) else (
            set /a "GONE+=1"
        )
    )
)

rem Put the survivor back to its clean name, so the next round starts from one.
set "RENAMED=0"
for /f "delims=" %%F in ('dir /b /a-d "%PATTERN%" 2^>nul') do (
    call :groupkey "%%~nF" "%%~xF"
    if /i not "%%F"=="!KEY!" if not exist "!KEY!" (
        ren "%%F" "!KEY!" 2>nul
        if not exist "%%F" (
            echo   renamed %%F -^> !KEY!
            set /a "RENAMED+=1"
        )
    )
)

echo.
echo Deleted !GONE! of !DOOMED!, renamed !RENAMED!, kept !KEPT!.
if !STUCK! GTR 0 echo !STUCK! could not be deleted -- close Minecraft and run it again.
popd
exit /b 0

rem ---------------------------------------------------------------------------
rem  KEY  = the clean filename this one is a copy of ("mod.jar")
rem  VKEY = the same, safe as a variable name (no spaces, dots or dashes)
rem ---------------------------------------------------------------------------
:groupkey
set "STEM=%~1"
for /f "tokens=1 delims=(" %%A in ("!STEM!") do set "STEM=%%A"
if "!STEM:~-1!"==" " set "STEM=!STEM:~0,-1!"
set "KEY=!STEM!%~2"
set "VKEY=!KEY: =_!"
set "VKEY=!VKEY:.=_!"
set "VKEY=!VKEY:-=_!"
exit /b 0
