# 01 · 项目介绍与架构（面试开场 3 分钟版）

> 每个数字/断言都带锚点（文件:行号），讲之前扫一遍即可；锚点已逐条实测核对（2026-11-12）。

## 3 分钟开场讲稿（照着讲）

**【0:00-0:30 是什么】**
「智行天下是一个智能旅游服务平台，四个模块：AI 线路规划、社交组队（关注/动态/点赞）、
攻略整合、门票秒杀。我重点打磨的是**秒杀高并发链路**——核心目标一句话：
**N 并发下超卖 = 0**，外加三级幂等、死信兜底、全链路可追溯，证据全部可本地复现。」

**【0:30-1:00 技术栈，只报跟亮点相关的】**
「Spring Boot 3.5.6 / Java 17（pom.xml:8,17），MyBatis-Plus 3.5.12（pom.xml:73）+
MySQL 8.0，Redis 用 **Lua 原子脚本**做扣减（src/main/resources/lua/seckill.lua），
RabbitMQ 做异步落库（publisher-confirm + 手动 ack + 死信队列，
application.yaml:34-42、RabbitMQConfig.java:53-54）。AI 部分是 LangChain4j 0.36.2
对接阿里云百炼 qwen-plus，OpenAI 兼容模式（pom.xml:92）。」

**【1:00-2:00 秒杀链路（画图讲）】**
「请求进来先过 **Redis Lua 脚本**，一个脚本里原子完成三件事：判重、查库存、扣减
（seckill.lua:5-13）。挡住的直接返回，不碰数据库；放行的生成雪花订单号，发 RabbitMQ
（持久化 + confirm），消费者手动 ack：先幂等查重，再 DB 乐观锁扣库存（stock>0），
唯一索引兜底一人一单；异常时回补 Redis、nack 进死信队列。
这样数据库永远只收到「已经过 Redis 梳理过」的流量。」

**【2:00-2:40 除了秒杀还有什么拿得出手的】**
「攻略模块做了**三态缓存**：命中真实数据 / 命中空值标记防穿透 / 未命中抢互斥锁重建
（ShopServiceImpl.java:47,76-83,119），热榜分页缓存带 TTL 随机抖动防雪崩
（BlogServiceImpl.java:90-107）。点赞计数用 Redis ZSet 存明细、DB 存总数，
一致性由每天凌晨的巡检任务走 MQ 异步对账兜底（LikeCleanupTask.java:57-97）。
另外我完成了一次 Boot 2.7/Java 8 → 3.5.6/17 的全量升级，踩坑记录见 05 号文档。」

**【2:40-3:00 收尾抛钩子】**
「这个项目我最想展开的是两个地方：一是**为什么扣减要放 Lua 里**而不是先 get 后 decr，
二是**死信重放为什么是安全的**——这两点我准备了完整的追问应答。」

## 一句话定位

智行天下：智能旅游服务平台，技术亮点集中在**秒杀高并发链路**——
「N 并发下超卖 = 0」+ 三级幂等 + 死信兜底 + 全链路可追溯。证据全部可本地复现。

## 技术栈（数字全部实测锚定）

| 层 | 选型 | 证据 |
|:--|:--|:--|
| 框架 | Spring Boot 3.5.6 / Java 17（2026-11 从 2.7.17/Java 8 升级） | pom.xml:8,17 |
| 数据 | MyBatis-Plus 3.5.12（Boot3 专用 starter）+ MySQL 8.0 + Redis（Lua 原子脚本） | pom.xml:72-73 |
| 中间件 | RabbitMQ（publisher-confirm / 手动 ack / 死信队列） | application.yaml:34-42；RabbitMQConfig.java:53-54 |
| AI | LangChain4j 0.36.2 → 阿里云百炼（qwen-plus，OpenAI 兼容模式） | pom.xml:92 |

## 四大功能模块（说法已按真实实现校准）

1. **门票秒杀**：Redis Lua 原子扣减 + MQ 异步落库（核心亮点，见 02/03 号文档）
2. **社交组队**：关注 / 动态 / 点赞——注意：**点赞写路径是同步的**（DB 计数 ±1 直接 update，
   BlogServiceImpl.java:139-148），走 MQ 异步的是**凌晨对账**：巡检发现 Redis ZSet 计数
   与 DB 不一致时投消息，消费者落库（LikeCleanupTask.java:93-97 →
   LikeCleanupConsumer.java:37-57 → BlogServiceImpl.java:198-200 syncLikedCount）。
   被追问时别把「点赞走 MQ」说成写路径，说成「一致性兜底走 MQ」
3. **攻略整合**：详情三态缓存（真实数据 / 空值标记 / 未命中，ShopServiceImpl.java:47 注释
   与 :76-83,97-110 实现）+ 热榜分页缓存（BlogServiceImpl.java:90-107）；
   ShopCacheServiceTest 4 用例覆盖三态 + 互斥锁
4. **AI 线路规划**：结构化 Prompt 组装 + 模型调用，纯逻辑部分 4 个 mock 用例覆盖
   （RoutePlanServiceImplTest），不花真实 key

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
- Boot 2.7→3.5.6 + Java 8→17，javax→jakarta 全量迁移，13→17 个单测全绿（mvn -B test 复现）
- 1000/5000 并发压测上海 Redis：超卖 = 0（docs/seckill-stress-report.md）
- 补死信重放故障注入用例、AI mock 用例、GitHub Actions CI、本套面试文档
