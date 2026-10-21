#!/bin/bash
export DEBIAN_FRONTEND=noninteractive
echo "=== apt 源可用性 ==="
timeout 30 apt-get update -qq 2>&1 | tail -3
echo "=== 安装压测工具（wrk / ab） ==="
timeout 120 apt-get install -y -qq wrk apache2-utils 2>&1 | tail -5
echo "=== 结果 ==="
which wrk ab
wrk --version 2>&1 | head -2
ab -V 2>&1 | head -1
