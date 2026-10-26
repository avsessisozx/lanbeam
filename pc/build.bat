@echo off
rem Builds dist\LanBeam.jar (runs on Java 8+).  Usage: pc\build.bat [test]
setlocal enabledelayedexpansion
set ROOT=%~dp0..
set JAVAC=javac
where javac >nul 2>nul || (
  for /d %%D in ("C:\Program Files\Eclipse Adoptium\jdk-*" "C:\Program Files\Java\jdk-*") do set JAVAC=%%D\bin\javac.exe
)
for %%F in ("!JAVAC!") do set BIN=%%~dpF
if "!BIN!"=="%CD%\" set BIN=

set BUILD=%ROOT%\build\pc
if exist "%BUILD%" rmdir /s /q "%BUILD%"
mkdir "%BUILD%" 2>nul
mkdir "%ROOT%\dist" 2>nul

dir /s /b "%ROOT%\core\src\main\java\*.java" "%ROOT%\pc\src\main\java\*.java" > "%ROOT%\build\sources.txt"
"!JAVAC!" --release 8 -encoding UTF-8 -Xlint:-options -d "%BUILD%" @"%ROOT%\build\sources.txt" || exit /b 1

echo Main-Class: com.lanbeam.pc.PcApp> "%ROOT%\build\manifest.txt"
"!BIN!jar" cfm "%ROOT%\dist\LanBeam.jar" "%ROOT%\build\manifest.txt" -C "%BUILD%" . || exit /b 1
(echo @echo off& echo start "" javaw -jar "%%~dp0LanBeam.jar") > "%ROOT%\dist\LanBeam.bat"
echo OK: %ROOT%\dist\LanBeam.jar

if /i "%1"=="test" (
  set TB=%ROOT%\build\test
  mkdir "!TB!" 2>nul
  dir /s /b "%ROOT%\core\src\test\java\*.java" > "%ROOT%\build\tests.txt"
  "!JAVAC!" --release 8 -encoding UTF-8 -Xlint:-options -cp "%BUILD%" -d "!TB!" @"%ROOT%\build\tests.txt" || exit /b 1
  "!BIN!java" -cp "%BUILD%;!TB!" com.lanbeam.core.LoopbackTest
)
