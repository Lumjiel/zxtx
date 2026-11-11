# 03 · MQ 可靠性与幂等（三段式）

## 生产端不丢：publisher-confirm + return

- `publisher-confirm-type: correlated`：消息是否**到达 broker**，未到达打 error 日志——
  秒杀消息丢失意味着用户扣了库存却没订单，所以选择「可观测 + 人工介入」而不是静默自动重发。
- `publisher-returns: true`：到达了但路由不到队列（routing key 配错）时回调。
- 队列 durable + 消息持久化。

## 消费端不丢：手动 ack + 三级异常处理

`acknowledge-mode: manual`，处理成功才 ack（SeckillOrderConsumer.java）：

| 场景 | 动作 | 证据（测试用例） |
|:--|:--|:--|
| 重复消息 | 幂等拦截，只 ack 不落库 | 重复消息_幂等拦截_只ack不落库 |
| DB 库存不足 | 回补 Redis（库存+1、已购集合移除）+ ack | 数据库库存不足_回补Redis并ack |
| 唯一索引拦截 | 回补 Redis + ack（一人一单兜底） | 唯一索引拦截重复购买_回补Redis并ack |
| 未知异常（已扣库存） | 回补 Redis + nack(requeue=false) 进死信 | 未知异常_回补Redis并nack进死信 |
| 扣库存前就异常 | 不回补（Redis 未预扣，回补会凭空加库存） | 扣库存前就异常_不回补_Redis未预扣 |
| 死信重放全流程 | 失败→重放成功→再重复投递幂等拦截 | 死信重放_故障恢复后重投_最终幂等成功 |

## 幂等设计（两级）

1. **快速路径**：订单主键 `selectById` 命中直接 ack（雪花订单号 = 天然幂等键）
2. **兜底路径**：DB 唯一索引 (user_id, voucher_id)，捕获 DuplicateKeyException

面试点：幂等键选订单号而不是 userId——userId 幂等会阻止「重试同一订单」与「正常二次购买」的区分；
订单号幂等只防消息重复，一人一单交给唯一索引，职责分离。

## 死信队列设计

nack(requeue=false) + 队列声明 `x-dead-letter-exchange/routing-key` → `seckill.order.dlx.queue`。
不无限重投的原因：毒消息会阻塞整个消费队列；死信人工排查后重放，重放安全性由幂等保证（库存层面
的边界见 docs/seckill-trace.md badcase 2，如实告知面试官，反而是加分项）。

## 反例

- Mockito 单测验证的是行为契约（verify 调用次数），不是真实 MQ 端到端；
  若 ack 语义变更（如改 auto ack），本套测试中 basicAck/basicNack 的 verify 会全部失效报警——这正是设计意图。
