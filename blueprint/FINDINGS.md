# 智行天下 审计发现台账

> 每轮审计把新发现追加到这里。卡必须引用这里的 ID（如 ZX-A001）。
> 状态：open / carded / fixed / wontfix

| ID | 透镜 | 严重度 | 位置 | 发现 | 特性 | 状态 |
|:--|:--|:--|:--|:--|:--|:--|
| ZX-A001 | 安全/配置 | 高 | src/main/resources/application.yml:10,14,29 | MySQL/Redis/RabbitMQ 密码明文入库（root / 123456 / guest） | F-010 | open |
| ZX-A002 | 正确性 | 高 | src/main/java 约 10 处空 catch | 异常被吞，失败既没日志也没上抛，线上只能看结果不对 | F-004 | open |
| ZX-A003 | 正确性 | 中 | 全仓库仅 3 处 @Transactional | 下单/库存/秒杀多写方法缺事务边界，失败会留半截数据 | F-003 | open |
| ZX-A004 | 工程化 | 高 | 全仓库 | 无 CI、无 lint、行覆盖 14.9%、checkstyle 1570 条 | F-001/F-002 | open |
| ZX-A005 | 可观测性 | 中 | 全仓库 | 无 actuator、无健康检查、无慢接口统计 | F-005 | open |

## 已扫过、确认干净
System.out/e.printStackTrace 0 处 · 字符串拼 SQL 0 处（2026-11-09 快扫）
