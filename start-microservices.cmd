@echo off
chcp 65001 >nul
setlocal
set "ROOT=%~dp0"
if exist "%USERPROFILE%\.jdks\ms-17.0.18" set "JAVA_HOME=%USERPROFILE%\.jdks\ms-17.0.18"
set "PATH=%JAVA_HOME%\bin;%PATH%"
set "DGP_H2_FILE=%ROOT%data\dgp"
set "DGP_REDIS_ENABLED=true"
set "DGP_LOCAL_SCHEDULER=false"
set "DGP_SCHEDULER_TOKEN=dev-scheduler-token-change-me"

echo 启动：core -^> auth -^> metadata -^> quality -^> migration -^> security -^> scheduler -^> search -^> gateway
echo Redis：docker compose -f deploy\compose\docker-compose.yml up -d redis

if not exist "%ROOT%data" mkdir "%ROOT%data"

start "dgp-core" cmd /k "cd /d %ROOT%dgp-core && set DGP_H2_FILE=%DGP_H2_FILE%&& set DGP_REDIS_ENABLED=%DGP_REDIS_ENABLED%&& set DGP_LOCAL_SCHEDULER=%DGP_LOCAL_SCHEDULER%&& set DGP_SCHEDULER_TOKEN=%DGP_SCHEDULER_TOKEN%&& java -jar target\dgp-core.jar"
echo 等待 core 建库/种子...
timeout /t 45 /nobreak >nul
start "dgp-auth" cmd /k "cd /d %ROOT%dgp-auth && set DGP_H2_FILE=%DGP_H2_FILE%&& set DGP_REDIS_ENABLED=%DGP_REDIS_ENABLED%&& java -jar target\dgp-auth.jar"
timeout /t 10 /nobreak >nul
start "dgp-metadata" cmd /k "cd /d %ROOT%dgp-metadata && set DGP_H2_FILE=%DGP_H2_FILE%&& java -jar target\dgp-metadata.jar"
timeout /t 8 /nobreak >nul
start "dgp-quality" cmd /k "cd /d %ROOT%dgp-quality && set DGP_H2_FILE=%DGP_H2_FILE%&& java -jar target\dgp-quality.jar"
timeout /t 8 /nobreak >nul
start "dgp-migration" cmd /k "cd /d %ROOT%dgp-migration && set DGP_H2_FILE=%DGP_H2_FILE%&& java -jar target\dgp-migration.jar"
timeout /t 8 /nobreak >nul
start "dgp-security" cmd /k "cd /d %ROOT%dgp-security && set DGP_H2_FILE=%DGP_H2_FILE%&& java -jar target\dgp-security.jar"
timeout /t 6 /nobreak >nul
start "dgp-scheduler" cmd /k "cd /d %ROOT%dgp-scheduler && set DGP_CORE_URI=http://127.0.0.1:8082&& set DGP_SCHEDULER_TOKEN=%DGP_SCHEDULER_TOKEN%&& java -jar target\dgp-scheduler.jar"
timeout /t 6 /nobreak >nul
start "dgp-search" cmd /k "cd /d %ROOT%dgp-search && set DGP_H2_FILE=%DGP_H2_FILE%&& java -jar target\dgp-search.jar"
timeout /t 8 /nobreak >nul
start "dgp-gateway" cmd /k "cd /d %ROOT%dgp-gateway && java -jar target\dgp-gateway.jar"
echo 已启动。metadata=8083 quality=8087 migration=8088 gateway=8080
echo 冒烟：verify\smoke_microservices.ps1
