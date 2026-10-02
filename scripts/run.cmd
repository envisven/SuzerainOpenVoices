@echo off
setlocal
cd /d "%~dp0.."
call scripts\jdk.cmd
if errorlevel 1 exit /b 1
call scripts\build.cmd
if errorlevel 1 exit /b 1
"%JDK_BIN%\java.exe" --add-modules javafx.controls --enable-native-access=javafx.graphics -Xmx4g -cp build\classes sordland.Launcher %*
exit /b %errorlevel%
