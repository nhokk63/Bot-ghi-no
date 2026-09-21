@echo off
setlocal EnableExtensions
set "GV=9.6.0"
set "ROOT=%~dp0"
set "CACHE=%TEMP%\sono-gradle-%GV%"
set "ZIP=%CACHE%\gradle-%GV%-bin.zip"
set "GRADLE=%CACHE%\gradle-%GV%\bin\gradle.bat"

if exist "%ROOT%gradle\wrapper\gradle-wrapper.jar" goto done
if not exist "%CACHE%" mkdir "%CACHE%"
if not exist "%GRADLE%" (
  echo [So No] Dang tai Gradle %GV%...
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Invoke-WebRequest -UseBasicParsing 'https://services.gradle.org/distributions/gradle-%GV%-bin.zip' -OutFile '%ZIP%'"
  if errorlevel 1 goto fail
  powershell -NoProfile -ExecutionPolicy Bypass -Command "Expand-Archive -Force '%ZIP%' '%CACHE%'"
  if errorlevel 1 goto fail
)

echo [So No] Tao Gradle Wrapper...
call "%GRADLE%" -p "%ROOT%" wrapper --gradle-version %GV% --distribution-type bin
if errorlevel 1 goto fail

echo.
echo Xong. Mo thu muc nay bang Android Studio va bam Sync Project with Gradle Files.
goto end

:done
echo Gradle Wrapper da ton tai. Khong can tao lai.
goto end

:fail
echo.
echo Loi tao Gradle Wrapper. Kiem tra mang va JDK 17 cua Android Studio.
exit /b 1

:end
endlocal
