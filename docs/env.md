# 环境与依赖记账

> 一行一条：装了什么 / 为什么 / 怎么卸。不是请示，是给下一个人留账。

- 本机基线依赖（Python 3.11 / Maven 3.9.11 / JDK 17 / Node 22 / sqlite3）为机器预装，未额外安装，卸载不适用。
- Redis 实际在远程主服务器（隧道 `zxtx-redis-tunnel.service` → 127.0.0.1:6379），测试密码走环境变量，不在本机安装。
- RabbitMQ：单测（Mockito）不依赖；SeckillLuaScriptTest 只依赖 Redis，连不上自动跳过（Assumptions.assumeTrue，src/test/java/com/zhixing/lua/SeckillLuaScriptTest.java:60-61）。
- 2026-11-12（本轮）：未安装任何新依赖——全部工作是文档细化与核查（grep/sed/mvn test 均用现有工具）。
- 2026-11-21（goal round 7）：`apt install mysql-server-8.0`（8.0.46）——为复验迁移包幂等验收（单据第 2 条「连跑两次不报错」，双跑实锤 1050 → 幂等化 e5efa42）。dpkg 状态 iF（postinst 因 3306 被既有 SSH 隧道占用启动失败），二进制完好：手动 `mysqld --user=mysql --port=3307 --socket=/var/run/mysqld/mysqld.sock` 起停，测试库用完已 DROP、已 shutdown。卸载：`apt purge mysql-server-8.0 mysql-server mysql-client-8.0* && rm -rf /var/lib/mysql`（datadir 182MB）。
