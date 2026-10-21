#!/usr/bin/env bash
# Git Bash / Linux 启动脚本（一体化，前端产物随 jar 发布）
set -e
ROOT="$(cd "$(dirname "$0")" && pwd)"
export JAVA_HOME="$ROOT/toolchain/jdk-17.0.13+11"
export PATH="$JAVA_HOME/bin:$PATH"
cd "$ROOT/backend"
if [ ! -f target/dgp-boot.jar ]; then
  "$ROOT/toolchain/apache-maven-3.9.16/bin/mvn" -B -s "$ROOT/toolchain/maven-conf/settings.xml" -DskipTests package
fi
exec java -jar target/dgp-boot.jar --server.port=8080
