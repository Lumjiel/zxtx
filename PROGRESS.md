# PROGRESS — zxtx#E1 全量（2026-11-10 夜）

- [x] T1 修 SeckillLuaScriptTest NOAUTH（密码走 REDIS_PASSWORD env）→ mvn test 全绿（实际 12/12，NOAUTH 修复后暴露并修复并发测试死锁）（验收：mvn -B -q test）
- [x] T2 Boot 3.5 + Java 17 升级，javax→jakarta 清零（验收：mvn test 绿 + grep -rl 'javax\.' src 为空）
- [x] T3 压测上海 Redis（只碰 test:seckill:*，REDIS_PASSWORD env）→ docs/seckill-stress-report.md（超卖=0）
- [x] T4 幂等 + 死信故障注入测试（重复消息/消费失败/死信重放）
- [x] T5 全链路 trace 说明 + badcase 复盘（docs/）
- [x] T6 AI 线路规划 mock 测试（不花真实 key）
- [x] T7 CI：.github/workflows 跑 mvn test
- [x] T8 docs/interview/ 5 份文档（含 04-evidence-map.md）
- [x] T9 终验：完整验收命令 rc=0，跑两遍确认

备注：内存 available 1107MB < 1500MB，review 子 agent 不开，改为收尾自查。
