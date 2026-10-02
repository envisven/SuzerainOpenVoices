@echo off
setlocal EnableExtensions EnableDelayedExpansion
set "JDK_BIN="

if defined JAVA_HOME call :tryJdk "%JAVA_HOME%\bin"

if not defined JDK_BIN (
    for /f "delims=" %%J in ('where javac.exe 2^>nul') do (
        if not defined JDK_BIN call :tryJdk "%%~dpJ"
    )
)

if not defined JDK_BIN (
    echo A JDK 25 distribution containing JavaFX is required. Set JAVA_HOME to BellSoft Liberica JDK 25 FULL or put its bin directory on PATH. 1>&2
    exit /b 1
)

endlocal & set "JDK_BIN=%JDK_BIN%"
exit /b 0

:tryJdk
set "CANDIDATE=%~1"
if not exist "%CANDIDATE%\java.exe" exit /b 0
if not exist "%CANDIDATE%\javac.exe" exit /b 0
"%CANDIDATE%\java.exe" --list-modules 2>nul | findstr /b /c:"javafx.controls@" >nul
if errorlevel 1 exit /b 0
set "JDK_BIN=%CANDIDATE%"
exit /b 0
