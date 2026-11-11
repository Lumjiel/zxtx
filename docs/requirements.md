# zxtx · 细化需求（从旧夜间 GOAL 搬入，2026-11-11）

> 终局看仓库根目录 `GOAL.md`。本文只是把原来写在夜间队列里、agent 不该丢的具体事项留档。
> 已完成的打勾，未完成的照旧有效。

## 验收清单

1. `mvn test` **11/11 绿**（先修 Redis NOAUTH）
2. Boot 3.5 + Java 17 + `javax.` 清零（`grep -rl "javax\." src/` 为空）
3. ~~`docs/seckill-stress-report.md`：压测上海服务器 zxtx-redis，**N 并发超卖 = 0**~~
   → **2026-11-11 决定暂不压测**；真要做需另开访谈定口径（机器/并发/时长/超卖统计）
4. 幂等 + 死信队列故障注入测试（重复消息 / 消费失败 / 死信重放）
5. 全链路 trace（Redis → MQ → DB）+ 一个真实 badcase 复盘
6. AI 线路规划 mock 测试（证明逻辑正确，不花真实 key）

## 红线（与 GOAL.md 一致，此处补充实现层）

- 不删测试、不放宽断言、不 `@Disabled` 跳过、不 mock 被测对象、不改验收命令
- 不把密码写进任何提交的文件（Redis 密码只从 `REDIS_PASSWORD` 环境变量读，值由 `/etc/night.env` 注入）
- 压测只碰 `test:seckill:*` 前缀的 key，不许污染生产数据
- 同一验收连败 3 次换下一项；结果比开工差就回滚并如实报告

## 已知现状

- 测试：11 条，10 绿 1 红（`SeckillLuaScriptTest` 需 Redis）
- 覆盖率：行 14.9%（114/763）· 分支 1.5%
- 未迁 Boot 3.5（当前 Spring Boot 2.7.17 / Java 8）
