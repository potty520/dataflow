@echo off
chcp 65001 >nul
setlocal
set "ROOT=%~dp0"
set "JAVA_HOME=%ROOT%toolchain\jdk-17.0.13+11"
set "PATH=%JAVA_HOME%\bin;%PATH%"
cd /d "%ROOT%frontend"
call npm install
call npm run build
cd /d "%ROOT%backend"
call "%ROOT%toolchain\apache-maven-3.9.16\bin\mvn.cmd" -B -s "%ROOT%toolchain\maven-conf\settings.xml" -DskipTests package
echo 构建完成： backend\target\dgp-boot.jar
