# 04 · 证据映射（简历话术 → 可复现证据）

> 原则：每个数字都能用一条命令复现。日期均为 2026-11-10（Boot 3.5 升级夜）。

## 简历话术对照表

| 简历/面试话术 | 证据类型 | 位置 | 复现命令 |
|:--|:--|:--|:--|
| 「N 并发下超卖 = 0」 | 压测数字（真实运行） | docs/seckill-stress-report.md | `source /etc/night.env && export REDIS_PASSWORD && scripts/seckill-stress.sh 1000 100` |
| 「Redis Lua 原子扣减防超卖」 | 代码 + 并发单测 | src/main/resources/lua/seckill.lua；src/test/java/com/zhixing/lua/SeckillLuaScriptTest.java | `mvn test -Dtest=SeckillLuaScriptTest` |
| 「消息零丢失三段式可靠性」 | 7 个故障注入单测 | src/test/java/com/zhixing/consumer/SeckillOrderConsumerTest.java | `mvn test -Dtest=SeckillOrderConsumerTest` |
| 「幂等设计：订单号快速路径 + 唯一索引兜底」 | 代码 + 单测 | SeckillOrderConsumer.java:69-106 | 同上 |
| 「死信队列兜底 + 重放」 | 单测 + 复盘文档 | SeckillOrderConsumerTest.java:155；docs/seckill-trace.md badcase 2 | 同上 |
| 「全链路 trace（Redis→MQ→DB）」 | 文档 + orderId 串联日志 | docs/seckill-trace.md | — |
| 「AI 线路规划逻辑有测试保障」 | 4 个 mock 单测（不花 key） | src/test/java/com/zhixing/service/RoutePlanServiceImplTest.java | `mvn test -Dtest=RoutePlanServiceImplTest` |
| 「Boot 2.7→3.5 + Java 17 平滑升级」 | 升级笔记 + javax 清零 | docs/interview/05-boot3-upgrade-notes.md | `grep -rl 'javax\.' src/`（应为空） |
| 「CI 门禁」 | GitHub Actions | .github/workflows/ci.yml | push 触发；本地等价：`mvn -B test` |

## 全量测试基线

```
mvn -B test
→ Tests run: 17, Failures: 0, Errors: 0, Skipped: 0
   （消费者 7 + AI mock 4 + 缓存 4 + Lua 2）
→ BUILD SUCCESS
```

注：SeckillLuaScriptTest 依赖本地 Redis（密码走 REDIS_PASSWORD 环境变量），
Redis 不可用时自动跳过（Assumptions），CI 环境不会误报。

## 压测数字明细

| 轮次 | 请求数 | 库存 | 成功 | 超卖 | 错误 | 库存终值 | QPS* |
|:--|:--|:--|:--|:--|:--|:--|:--|
| 1 | 1000 | 100 | 100 | 0 | 0 | 0 | 2710 |
| 2 | 5000 | 200 | 200 | 0 | 0 | 0 | 6485 |

*QPS 含 SSH 隧道跨地域 RTT，仅作量级参考，不作性能卖点。

## 编造红线自查

- 压测输出原文（script sha1、VERDICT=PASS）在 docs/seckill-stress-report.md 第 3 节逐行留存；
- 所有测试断言未放宽、未删用例（git diff 可查）；
- 死信重放的已知缺陷没有掩盖，写入面试预案（02/03 号文档）。
