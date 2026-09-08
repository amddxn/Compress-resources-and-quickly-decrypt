@echo off
title Archive Recovery Assistant
setlocal EnableExtensions EnableDelayedExpansion
cd /d "%~dp0"

if not defined JAVA_HOME (
    echo JAVA_HOME is not configured.
    pause
    exit /b 1
)

if not exist "%JAVA_HOME%\bin\javac.exe" (
    echo Java compiler was not found under JAVA_HOME: %JAVA_HOME%
    pause
    exit /b 1
)

if not exist "target\classes" mkdir "target\classes"
(for /r "src\main\java" %%F in (*.java) do (
    set "SOURCE_FILE=%%F"
    set "SOURCE_FILE=!SOURCE_FILE:\=/!"
    echo "!SOURCE_FILE!"
)) > "target\sources.txt"

"%JAVA_HOME%\bin\javac.exe" -encoding UTF-8 --release 17 -d "target\classes" @"target\sources.txt"
if errorlevel 1 (
    echo Compilation failed.
    pause
    exit /b 1
)

if /i "%~1"=="--compile-only" exit /b 0

"%JAVA_HOME%\bin\java.exe" -cp "target\classes" app.archiverecovery.Main
