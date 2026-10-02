@echo off
setlocal
cd /d "%~dp0.."
call scripts\jdk.cmd
if errorlevel 1 exit /b 1
call scripts\build.cmd
if errorlevel 1 exit /b 1
if not exist build\test-classes mkdir build\test-classes
type nul > build\test-sources.txt
for /r tests %%F in (*.java) do echo "%%F">>build\test-sources.txt
"%JDK_BIN%\javac.exe" --release 25 --add-modules javafx.controls -encoding UTF-8 -cp build\classes -d build\test-classes @build\test-sources.txt
if errorlevel 1 exit /b 1
"%JDK_BIN%\java.exe" --add-modules javafx.controls --enable-native-access=javafx.graphics -Xmx4g -cp "build\classes;build\test-classes" sordland.AllTests %*
exit /b %errorlevel%
