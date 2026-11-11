# 秒杀全链路 trace（Redis → MQ → DB）+ badcase 复盘

- 日期：2026-11-10（Boot 3.5 升级当晚）
- 配套：`docs/seckill-stress-report.md`（压测数字）、`src/test/java/com/zhixing/consumer/SeckillOrderConsumerTest.java`（7 个故障注入用例）

## 1. 一条秒杀请求的完整生命周期（带观测点）

```
[0] HTTP POST /voucher-order/seckill
      │  观测点：Controller 日志 orderId（雪花 ID，全局可追）
[1] Redis Lua（lua/seckill.lua）：判重 → 查库存 → 扣减，返回 1/0/-1
      │  观测点：EVALSHA sha1=52c416dc…（压测脚本与生产加载同一脚本）
      │  返回 0 = 库存不足（直接拒绝）；-1 = 重复下单（直接拒绝）；1 = 预扣成功
[2] 订单号生成 + 消息投递 RabbitMQ（seckill.order.queue，durable）
      │  观测点：publisher-confirm（到达 broker）+ publisher-return（路由成功）
      │  失败时 RabbitMQConfig 打 [MQ确认]/[MQ退回] error 日志——扣了库存没订单必须人工介入
[3] SeckillOrderConsumer.handleSeckillOrder（手动 ack）
      ├─ 3a 幂等快速路径：selectById(orderId) 命中 → ack（重复投递无副作用）
      ├─ 3b DB 乐观锁扣库存：UPDATE … SET stock=stock-1 WHERE stock>0（DB 层防超卖兜底）
      ├─ 3c 插订单：唯一索引 (user_id, voucher_id) 兜底一人一单
      │     DuplicateKeyException → 回补 Redis + ack
      ├─ 3d DB 库存不足 → 回补 Redis（库存+1、已购集合移除）+ ack（用户可重试）
      └─ 3e 未知异常 → （若已扣库存则回补 Redis）+ basicNack(requeue=false) → 死信
[4] 死信队列 seckill.order.dlx.queue
      │  观测点：nack 日志 [秒杀消费] 消息处理失败，转入死信队列
      │  补偿路径：人工排查修复后，把死信消息重投回业务队列（幂等保证重放安全）
```

三层防超卖互为兜底：Lua 原子扣减（第 1 层）→ DB 乐观锁 stock>0（第 2 层）→ 唯一索引一人一单（第 3 层）。

## 2. badcase 复盘（本晚真实发生的 2 个）

### badcase 1：并发测试死锁——500 任务 vs 64 线程池的 CountDownLatch 陷阱

- **现象**：修好 Redis NOAUTH 后 `mvn test` 首次真正执行 `并发_100库存500请求_不超卖不少卖`，
  测试挂起超过 20 分钟，jstack 显示全部 worker 卡在 `ready.await()`。
- **根因**：500 个任务提交进 64 线程池，只有 64 个线程能执行 `ready.countDown()`，
  闩永远凑不满 500，剩下 436 个任务在队列里永远不执行——经典闩死锁。
  此前该测试因 Redis 连不上被 Assumptions 跳过，bug 从未暴露；**NOAUTH 修复让测试真正跑起来，才让这个死锁现形**。
- **修复**：`SeckillLuaScriptTest.java` 池大小改为等于请求数，并给 `done.await` 加 2 分钟超时
  （超时主动 fail 而非无限挂起——加强了测试，不是放宽）。
- **教训**：「连接不上就跳过」的测试会让测试体本身的 bug 长期潜伏；修复环境问题后必须确认
  原本 skip 的用例真的执行过一遍。

### badcase 2：死信重放会造成 DB 库存二次扣减（已知权衡，用测试固化）

- **现象**：消费者第 1 次处理时「DB 扣库存成功 → insert 阶段异常 → nack 进死信 + 回补 Redis」；
  人工把死信消息重放回队列后，消费者会**再次执行 DB 扣库存**——该订单号最终只插一条订单（幂等按主键），
  但 DB 库存被多扣了 1。
- **根因**：消费者的回补只补 Redis，不回滚 DB 扣减（方法上无 @Transactional，
  「乐观锁 UPDATE + INSERT」跨两条语句没有包进一个本地事务）。
- **处理**：本轮不改实现（升级窗口不动核心语义，风险最高），而是新增用例
  `死信重放_故障恢复后重投_最终幂等成功`（SeckillOrderConsumerTest.java:155），
  用 `times(2)` 把「重放会二次扣减」这一现状如实固化，防止未来误以为重放零副作用。
- **正确修法（待排期）**：3b+3c 包进本地事务、ack 用事务同步（消费者 javadoc 已注明此权衡）；
  或重放工具按 orderId 先 `selectById` 预检再重投。
- **面试话术**：被问「死信重放安全吗」——答：订单层面幂等（主键+唯一索引）保证不会重复下单，
  但库存层面存在二次扣减窗口，修复方向是事务化或重放预检，当前用测试固化了该边界。

## 3. 反例（什么情况下这份 trace 不成立）

- 以上观测点基于日志与测试，未接入分布式 trace 组件（如 SkyWalking）；
  跨进程追一条真实请求仍靠 orderId 串联日志，多实例场景需要集中日志才可用。
- badcase 2 的「二次扣减」结论来自 Mockito 单测（mock 侧按真实代码路径统计调用次数），
  未在真实 MySQL 上复现断言；逻辑等价但环境不同。
