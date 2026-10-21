@echo off
chcp 65001 >nul
setlocal
set "ROOT=%~dp0"
set "JAVA_HOME=%ROOT%toolchain\jdk-17.0.13+11"
set "PATH=%JAVA_HOME%\bin;%PATH%"
cd /d "%ROOT%backend"
if not exist "target\dgp-boot.jar" (
  echo [1/2] 首次运行，正在构建后端 ...
  call "%ROOT%toolchain\apache-maven-3.9.16\bin\mvn.cmd" -B -s "%ROOT%toolchain\maven-conf\settings.xml" -DskipTests package
  if errorlevel 1 ( echo 构建失败 & pause & exit /b 1 )
)
echo [2/2] 启动 DGP 后端： http://127.0.0.1:8080
java -jar target\dgp-boot.jar --server.port=8080
