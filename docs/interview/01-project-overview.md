# 01 · 项目介绍与架构（面试开场 3 分钟版）

## 一句话定位

智行天下：智能旅游服务平台，技术亮点集中在**秒杀高并发链路**——
「N 并发下超卖 = 0」+ 三层幂等 + 死信兜底 + 全链路可追溯。证据全部可本地复现。

## 技术栈

| 层 | 选型 |
|:--|:--|
| 框架 | Spring Boot 3.5.6 / Java 17（2026-11 从 2.7.17/Java 8 升级） |
| 数据 | MyBatis-Plus 3.5.12 + MySQL 8.0 + Redis（Lua 原子脚本） |
| 中间件 | RabbitMQ（publisher-confirm / 手动 ack / 死信队列） |
| AI | LangChain4j 0.36.2 → 阿里云百炼（qwen-plus，OpenAI 兼容模式） |

## 四大功能模块

1. **门票秒杀**：Redis Lua 原子扣减 + MQ 异步落库（核心亮点，见 02/03 号文档）
2. **社交组队**：关注 / 动态 / 点赞，点赞计数走 MQ 异步对账
3. **攻略整合**：详情三态缓存 + 热榜分页缓存（`ShopCacheServiceTest` 4 用例覆盖）
4. **AI 线路规划**：结构化 Prompt 组装 + 模型调用，纯逻辑部分 4 个 mock 用例覆盖，不花真实 key

## 架构图（文字版）

```
客户端 → Controller → Redis(Lua 原子扣减) ─┬─ 失败：直接拒绝（库存不足/重复）
                                          └─ 成功：发 RabbitMQ → 消费者
                                                        ├─ 幂等查重 → ack
                                                        ├─ DB 乐观锁扣库存（stock>0）
                                                        ├─ 唯一索引兜底一人一单
                                                        └─ 异常 → 回补 Redis → 死信队列
```

## 我做了什么（升级夜动作，均有据可查）

- 修复 Lua 测试 NOAUTH（密码走环境变量）→ 暴露并修复并发测试死锁（docs/seckill-trace.md badcase 1）
- Boot 2.7→3.5.6 + Java 8→17，javax→jakarta 全量迁移，13→17 个单测全绿
- 1000/5000 并发压测上海 Redis：超卖 = 0（docs/seckill-stress-report.md）
- 补死信重放故障注入用例、AI mock 用例、GitHub Actions CI、本套面试文档
