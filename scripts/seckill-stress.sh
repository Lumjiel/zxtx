#!/usr/bin/env bash
# 秒杀压测一键脚本：只碰 test:seckill:* 前缀 key，密码走 REDIS_PASSWORD 环境变量。
# 用法：SECKILL_REDIS_HOST=127.0.0.1 REDIS_PASSWORD=xxx scripts/seckill-stress.sh [请求数] [库存]
set -euo pipefail
cd "$(dirname "$0")/.."

: "${REDIS_PASSWORD:?REDIS_PASSWORD 未设置（值来自 /etc/night.env，不写进任何文件）}"
HOST="${SECKILL_REDIS_HOST:-127.0.0.1}"
PORT="${SECKILL_REDIS_PORT:-6379}"
REQS="${1:-1000}"
STOCK="${2:-100}"

# 编译测试代码（含压测 main 类）
mvn -B -q test-compile

# 组装 classpath（一次生成缓存）
CP_FILE=target/.stress-cp
if [ ! -f "$CP_FILE" ]; then
  mvn -B -q dependency:build-classpath -Dmdep.outputFile="$CP_FILE" -DincludeScope=test
fi
CP="target/classes:target/test-classes:$(cat "$CP_FILE")"

java -Xmx256m -cp "$CP" com.zhixing.lua.SeckillStress "$REQS" "$STOCK"
