@echo off
chcp 65001 >nul
setlocal
set "ROOT=%~dp0"
if exist "%USERPROFILE%\.jdks\ms-17.0.18" set "JAVA_HOME=%USERPROFILE%\.jdks\ms-17.0.18"
if exist "%ROOT%toolchain\jdk-17.0.13+11" set "JAVA_HOME=%ROOT%toolchain\jdk-17.0.13+11"
set "PATH=%JAVA_HOME%\bin;%PATH%"
cd /d "%ROOT%frontend"
call npm install
call npm run build
cd /d "%ROOT%"
if exist "%ROOT%toolchain\apache-maven-3.9.16\bin\mvn.cmd" (
  call "%ROOT%toolchain\apache-maven-3.9.16\bin\mvn.cmd" -B -s "%ROOT%toolchain\maven-conf\settings.xml" -DskipTests package
) else (
  call mvn -B -DskipTests package
)
echo 构建完成：
echo   dgp-gateway\target\dgp-gateway.jar
echo   dgp-auth\target\dgp-auth.jar
echo   dgp-core\target\dgp-core.jar
