# 02 · 秒杀设计（八股 + 本项目实现对照）

> 用法：左侧是「照着讲」的话术，括号内是代码锚点——被追问时能当场翻到实现。
> 追问三层 = ①是什么 ②为什么这么选 ③换方案会怎样。每节末尾按此展开。

## 为什么 Redis + Lua？

纯 DB 秒杀在行锁上排队，2 核机器抗不住瞬时峰值；纯 Redis 又有「扣了库存没落库」的一致性风险。
本项目的取舍：**Redis 原子扣减挡流量 + MQ 异步落库**，DB 用乐观锁和唯一索引兜底。
入口在 `VoucherOrderServiceImpl.seckillVoucher()`（VoucherOrderServiceImpl.java:69-120）：
校验时间窗（:77-87）→ 执行 Lua（:91-95）→ 雪花订单号 + 发 MQ（:109-116）→ 返回订单号（:119）。

## Lua 脚本三返回码（src/main/resources/lua/seckill.lua，全 15 行）

| 返回 | 语义 | 处理 | 代码 |
|:--|:--|:--|:--|
| 1 | 预扣成功 | 生成雪花订单号 → 发 MQ | VoucherOrderServiceImpl.java:109-116 |
| 0 | 库存不足 | 直接拒绝，不发 MQ | VoucherOrderServiceImpl.java:100-102 |
| -1 | 重复下单 | 直接拒绝，不发 MQ | VoucherOrderServiceImpl.java:103-105 |

脚本本体：SISMEMBER 判重（seckill.lua:5-7）→ GET 库存校验（:9-11）→ DECR 扣减（:12）→ SADD 记录已购（:13）。
Java 侧通过 `DefaultRedisScript<Long>` 加载（VoucherOrderServiceImpl.java:54-58），key 为
`seckill:stock:{id}` / `seckill:user:{id}`（RedisConstants.java:16-17）。

面试点：**判重、查库存、扣减三步在一个脚本内**，Redis 单线程执行，天然无 TOCTOU 窗口。
拆成三条命令（GET→SISMEMBER→DECR）就会超卖。

## 三层防超卖

1. **Lua 原子扣减**（第 1 层，挡 99% 流量）— seckill.lua:9-13
2. **DB 乐观锁** `SET stock=stock-1 WHERE stock>0`（第 2 层，MQ 消费侧兜底）—
   SeckillOrderConsumer.java:77-82（`LambdaUpdateWrapper.setSql + gt(stock,0)`）
3. **唯一索引** (user_id, voucher_id)（第 3 层，一人一单 DB 兜底，捕获 DuplicateKeyException）—
   sql/zhixing.sql:1288 `ALTER TABLE tb_voucher_order ADD UNIQUE KEY uk_user_voucher`

## 压测证据（真实运行，2026-11-10）

- 1000 请求 / 100 库存：success=100，oversell=**0**，qps≈2710（SSH 隧道跨地域）
- 5000 请求 / 200 库存：success=200，oversell=**0**，qps≈6485
- 复现：`scripts/seckill-stress.sh 1000 100`（详见 docs/seckill-stress-report.md 第 3 节逐行留存）
- **如实说明**：2026-11-11 决定暂不再压测（docs/requirements.md:5-6），以上数字为 11-10 真实运行结果，
  QPS 含跨地域 RTT 仅作量级参考，不作性能卖点。

## 高频追问（三层展开）

### Q1: 为什么用 Lua 而不是事务/管道/分布式锁？

- **是什么**：一个 Lua 脚本内完成判重→查库存→扣减→记账，Redis 单线程执行保证原子。
- **为什么**：判重和扣减之间不能有窗口——拆开就有「两人同时通过判重、各扣一次」的 TOCTOU 竞态。
- **换方案会怎样**：MULTI/EXEC 事务不能读中间结果做条件判断（SISMEMBER 的结果要参与后续逻辑），
  管道只是批量发送不保证原子；分布式锁能串行化但每次抢锁 RTT 成本高、还有锁过期续期问题；
  Lua 等效于「脚本级串行」且一次网络往返。

### Q2: Redis 扣了库存但 MQ 没发出去，怎么办？

- **是什么**：publisher-confirm 失败打 error 日志（RabbitMQConfig + CorrelationData 携带订单号，
  VoucherOrderServiceImpl.java:116）。
- **为什么**：秒杀消息丢失意味着用户扣了库存却没订单，选择「可观测 + 人工介入」而不是静默自动重发——
  自动重发需要本地消息表，引入新的存储和一致性问题。
- **换方案会怎样**：本地消息表能自动化对账，但要一张表+定时任务+状态机；当前规模下死信+人工补偿
  已覆盖（真实丢消息概率极低，confirm 回调有日志可追）。这是诚实承认的取舍，不是没想到。

### Q3: 为什么不用分布式锁？

- **是什么**：Lua 脚本在 Redis 单线程内串行执行。
- **为什么**：等效于锁但无锁开销、无死锁、无续期。
- **换方案会怎样**：SETNX 锁需要 TTL+看门狗续期（Redisson），且锁粒度是 voucherId——锁错粒度会
  把所有商品串行化。Lua 天然按 key 串行，粒度天然正确。

### Q4: 为什么不用消息去重表代替幂等查重？

- **是什么**：订单主键 selectById 快速路径（SeckillOrderConsumer.java:69-106）+ 唯一索引兜底，两级。
- **为什么**：订单主键本身是幂等键，去重表是第三级冗余，边际收益低。
- **换方案会怎样**：去重表适合「同一业务动作多次投递但订单号不同」的场景；本项目同一订单号
  重投被快速路径拦截，不同订单号重复购买被唯一索引拦截，场景已全覆盖。

### Q5: 已知缺陷？

死信重放会二次扣 DB 库存（订单层面幂等，库存层面有窗口）：重放时 selectById 命中→只 ack 不落库，
**不会重复建单**，但重放路径若走「扣库存」分支会多扣一次。
修复方向是消费者事务化或重放预检，已用测试固化边界（docs/seckill-trace.md badcase 2）。
如实告知面试官反而是加分项——讲得清边界在哪，比声称「完美」可信。

### Q6: 库存预热和售罄怎么处理？（延伸题）

- **是什么**：库存 key 在活动开始前由管理端写入 Redis（新增秒杀券时同步写，
  VoucherServiceImpl.java:60-61）。
- **为什么**：活动开始瞬间才有流量，预热避免第一波请求打到 DB。
- **换方案会怎样**：懒加载 + 缓存击穿防护也可行，但秒杀场景流量可预期、key 可枚举，预热更简单可控。
