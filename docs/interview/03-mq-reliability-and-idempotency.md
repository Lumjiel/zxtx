# 03 · MQ 可靠性与幂等（三段式）

> 锚点已逐条实测核对（2026-11-12）。消费者类在 `src/main/java/com/zhixing/config/SeckillOrderConsumer.java`。

## 生产端不丢：publisher-confirm + return

- `publisher-confirm-type: correlated`（application.yaml:34）：消息是否**到达 broker**，
  未到达打 error 日志（RabbitMQConfig.java:76-82 注册 ConfirmCallback）——
  秒杀消息丢失意味着用户扣了库存却没订单，所以选择「可观测 + 人工介入」而不是静默自动重发。
- `publisher-returns: true`（application.yaml:35）：到达了但路由不到队列（routing key 配错）时回调
  （RabbitMQConfig.java:71-82 ReturnsCallback）。
- 发送时 `CorrelationData` 携带订单号，confirm 失败能定位到具体订单
  （VoucherOrderServiceImpl.java:108,114-116）。
- 队列 durable（RabbitMQConfig.java:47-55 `QueueBuilder.durable`）+ 消息持久化
  （Spring AMQP 默认 deliveryMode=PERSISTENT，本项目未显式覆盖——
  代码 grep 无 deliveryMode/MessagePostProcessor；外部佐证：
  RabbitMQ 官方教程「Messages are persistent by default with Spring AMQP」
  https://www.rabbitmq.com/tutorials/tutorial-two-spring-amqp ，查证日期 2026-11-12）。

**追问三层 · 为什么 confirm 失败不自动重发？**
① 是什么：ConfirmCallback 收到 ack=false 只打 error 日志。
② 为什么：自动重发有重复扣减风险——Lua 已扣库存但消息没发出去时，盲目重发与幂等设计的职责纠缠；
   人工介入能先核对 Redis/DB 状态。
③ 换方案会怎样：把「Redis 扣减 + 发消息」包进本地事务/事务消息（如 RocketMQ half message）
   能自动化，但引入新中间件，当前单机 RabbitMQ 场景收益不抵复杂度。

## 消费端不丢：手动 ack + 分场景处理

`acknowledge-mode: manual`（application.yaml:42），处理成功才 ack
（config/SeckillOrderConsumer.java:59 @RabbitListener）：

| 场景 | 动作 | 代码锚点 | 证据（测试用例） |
|:--|:--|:--|:--|
| 重复消息 | 幂等拦截，只 ack 不落库 | :70-74 selectById 命中直接 ack | 重复消息_幂等拦截_只ack不落库 |
| DB 库存不足 | 回补 Redis + ack | :79-86 `stock>0` 乐观锁 updated==0 分支 | 数据库库存不足_回补Redis并ack |
| 唯一索引拦截 | 回补 Redis + ack | :102-104 DuplicateKeyException 捕获 | 唯一索引拦截重复购买_回补Redis并ack |
| 未知异常（已扣库存） | 回补 Redis + nack 进死信 | :111-116 + :140-146 nack(requeue=false) | 未知异常_回补Redis并nack进死信 |
| 扣库存前就异常 | 不回补（Redis 未预扣，回补会凭空加库存） | :62,:82 `stockDeducted` 标志位控制 | 扣库存前就异常_不回补_Redis未预扣 |
| 死信重放全流程 | 失败→重放成功→再重复投递幂等拦截 | 幂等路径 :70-74 | 死信重放_故障恢复后重投_最终幂等成功 |

回补具体动作：库存 +1、已购集合移除该用户（:126-129 compensateRedis）。

**追问三层 · 为什么「扣库存前异常」不回补？**
① 是什么：`stockDeducted` 标志只记录「DB 乐观锁扣过」。
② 为什么：回补 = Redis 库存 +1。若 DB 没扣成功就回补，等于凭空多了一张票，可能超卖。
③ 换方案会怎样：也可以把「是否预扣」写进消息体重放判断，但标志位更简单且单消费者串行下足够。

## 幂等设计（两级）

1. **快速路径**：订单主键 `selectById` 命中直接 ack（:70-74；雪花订单号 = 天然幂等键）
2. **兜底路径**：DB 唯一索引 (user_id, voucher_id)，捕获 DuplicateKeyException（:102-104）；
   建索引见 sql/zhixing.sql

面试点：幂等键选订单号而不是 userId——userId 幂等会阻止「重试同一订单」与「正常二次购买」的区分；
订单号幂等只防消息重复，一人一单交给唯一索引，职责分离。

**追问三层 · 唯一索引拦截时为什么还要回补 Redis？**
① 是什么：DuplicateKeyException 说明这条消息之前已成功建过单，但 Redis 侧也已预扣过一次。
② 为什么：不回补则该用户的预扣份额永久丢失（少卖）。
③ 边界：如果是恶意构造的「预扣后从未真正下单」消息，回补是安全的——已购集合 sismember 会拦住重复预扣。

## 死信队列设计

nack(requeue=false)（:143）+ 队列声明 `x-dead-letter-exchange/routing-key`
（RabbitMQConfig.java:53-54）→ `seckill.order.dlx.queue`（RabbitMQConfig.java:40,58-61）。
不无限重投的原因：毒消息会阻塞整个消费队列；死信人工排查后重放，重放安全性由幂等保证
（库存层面的边界见 docs/seckill-trace.md badcase 2，如实告知面试官，反而是加分项）。

**追问三层 · 死信重放会不会重复扣库存？**
① 是什么：会——重放消息若此前 DB 扣减已成功，重放会再扣一次（详见 badcase 2）。
② 为什么现在可接受：消费单线程 + 死信量人工可控，重放前先核对订单表即可判别。
③ 修复方向：给消息体加「扣减状态」或以订单号为唯一键做 DB 侧 upsert，重放即幂等。

## 反例

- Mockito 单测验证的是行为契约（verify 调用次数），不是真实 MQ 端到端；
  若 ack 语义变更（如改 auto ack），本套测试中 basicAck/basicNack 的 verify 会全部失效报警——这正是设计意图。
