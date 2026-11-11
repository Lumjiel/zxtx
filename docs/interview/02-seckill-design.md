# 02 · 秒杀设计（八股 + 本项目实现对照）

## 为什么 Redis + Lua？

纯 DB 秒杀在行锁上排队，2 核机器抗不住瞬时峰值；纯 Redis 又有「扣了库存没落库」的一致性风险。
本项目的取舍：**Redis 原子扣减挡流量 + MQ 异步落库**，DB 用乐观锁和唯一索引兜底。

## Lua 脚本三返回码（lua/seckill.lua）

| 返回 | 语义 | 处理 |
|:--|:--|:--|
| 1 | 预扣成功 | 生成雪花订单号 → 发 MQ |
| 0 | 库存不足 | 直接拒绝，不发 MQ |
| -1 | 重复下单 | 直接拒绝，不发 MQ |

面试点：**判重、查库存、扣减三步在一个脚本内**，Redis 单线程执行，天然无 TOCTOU 窗口。
拆成三条命令（GET→SISMEMBER→DECR）就会超卖。

## 三层防超卖

1. **Lua 原子扣减**（第 1 层，挡 99% 流量）
2. **DB 乐观锁** `UPDATE … SET stock=stock-1 WHERE stock>0`（第 2 层，MQ 消费侧兜底）
3. **唯一索引** (user_id, voucher_id)（第 3 层，一人一单 DB 兜底，捕获 DuplicateKeyException）

## 压测证据（真实运行，2026-11-10）

- 1000 请求 / 100 库存：success=100，oversell=**0**，qps≈2710（SSH 隧道跨地域）
- 5000 请求 / 200 库存：success=200，oversell=**0**，qps≈6485
- 复现：`scripts/seckill-stress.sh 1000 100`（详见 docs/seckill-stress-report.md）

## 高频追问预案

- **Q: Redis 挂了怎么办？** 答：库存扣减失败则请求直接失败（fail-fast），不会超卖只会少卖；
  MQ 侧消息 durable + 手动 ack，已扣未落库的订单由死信+人工补偿。
- **Q: 为什么不用分布式锁？** 答：Lua 脚本在 Redis 单线程内串行，等效于锁但无锁开销。
- **Q: 为什么不用消息去重表代替幂等查重？** 答：订单主键本身就是幂等键，selectById 快速路径
  + 唯一索引兜底已经两级，去重表是第三级冗余，边际收益低。
- **Q: 已知缺陷？** 答：死信重放会二次扣 DB 库存（订单层面幂等，库存层面有窗口），
  修复方向是消费者事务化或重放预检，已用测试固化边界（docs/seckill-trace.md badcase 2）。
