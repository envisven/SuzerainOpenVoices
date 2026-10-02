@echo off
setlocal EnableDelayedExpansion

cd /d "%~dp0.."

call scripts\jdk.cmd
if errorlevel 1 exit /b 1

if not exist build\classes mkdir build\classes

type nul > build\sources.txt

for /r src %%F in (*.java) do (
    set "SOURCE=%%F"
    set "SOURCE=!SOURCE:\=/!"
    echo "!SOURCE!">>build\sources.txt
)

"%JDK_BIN%\javac.exe" ^
    --release 25 ^
    --add-modules javafx.controls ^
    -encoding UTF-8 ^
    -d build\classes ^
    @build\sources.txt

if errorlevel 1 exit /b 1

echo Compiled Sordland Tree Viewer.
exit /b 0