# 环境与依赖记账

> 一行一条：装了什么 / 为什么 / 怎么卸。不是请示，是给下一个人留账。

- 本机基线依赖（Python 3.11 / Maven 3.9.11 / JDK 17 / Node 22 / sqlite3）为机器预装，未额外安装，卸载不适用。
- Redis 实际在远程主服务器（隧道 `zxtx-redis-tunnel.service` → 127.0.0.1:6379），测试密码走环境变量，不在本机安装。
- RabbitMQ：单测（Mockito）不依赖；SeckillLuaScriptTest 只依赖 Redis，连不上自动跳过（Assumptions.assumeTrue，src/test/java/com/zhixing/lua/SeckillLuaScriptTest.java:60-61）。
- 2026-11-12（本轮）：未安装任何新依赖——全部工作是文档细化与核查（grep/sed/mvn test 均用现有工具）。
