# 秒杀链路压测报告（Redis Lua 层）

- 日期：2026-11-10（夜）
- 结论先行：**两轮压测超卖 = 0**（1000 请求 / 100 库存、5000 请求 / 200 库存），
  成功数恰好等于库存数、库存恰好归零、已购集合人数与成功数一致、0 请求错误。
- 判定：每轮末行 `VERDICT=PASS`（脚本按 oversell==0 && errors==0 && stock_after==0 && buyers==success 自动判定）。

## 1. 环境

| 项 | 值 |
|:--|:--|
| 被测对象 | `src/main/resources/lua/seckill.lua`（判重→查库存→扣减，生产同款脚本，SCRIPT LOAD 后 EVALSHA） |
| Redis | 上海服务器 `zxtx-redis`，经 SSH 隧道 `127.0.0.1:6379`，密码从 `REDIS_PASSWORD` 环境变量读入（不落盘、不进提交） |
| 客户端 | Lettuce 单连接多路复用（同步 EVALSHA），256 线程池发请求 |
| key 隔离 | 仅使用 `test:seckill:stock:stress` / `test:seckill:user:stress`，userId 走 900000000+ 专用段，跑完即删，不碰任何业务 key |
| 运行机器 | 本机（OpenCloudOS 9.6 / 2 核 / 3.6GB，无 GPU） |
| 网络 | SSH 隧道跨地域，延迟含隧道 RTT（p50 数十 ms 正常） |

## 2. 复现命令

```bash
source /etc/night.env   # 注入 REDIS_PASSWORD（隧道机）
export REDIS_PASSWORD
scripts/seckill-stress.sh 1000 100   # 轮 1：1000 请求 / 100 库存
scripts/seckill-stress.sh 5000 200   # 轮 2：5000 请求 / 200 库存
```

脚本做的事：`mvn test-compile` → 组装 test classpath → 运行
`com.zhixing.lua.SeckillStress`（源码 `src/test/java/com/zhixing/lua/SeckillStress.java`）。

## 3. 原始输出（2026-11-10 夜，真实运行）

### 轮 1：1000 请求 / 100 库存

```
script sha1=52c416dc02a7eedbf2bd78b2df112d3c1151ad34
concurrency_requests=1000
stock=100
success=100
duplicate=0
stockout=900
other=0
errors=0
oversell=0
stock_after=0
buyers=100
wall_ms=369
qps=2710
latency_p50_ms=54.815
latency_p95_ms=73.122
latency_p99_ms=77.450
VERDICT=PASS
```

### 轮 2：5000 请求 / 200 库存

```
script sha1=52c416dc02a7eedbf2bd78b2df112d3c1151ad34
concurrency_requests=5000
stock=200
success=200
duplicate=0
stockout=4800
other=0
errors=0
oversell=0
stock_after=0
buyers=200
wall_ms=771
qps=6485
latency_p50_ms=26.440
latency_p95_ms=48.310
latency_p99_ms=73.418
VERDICT=PASS
```

（轮 1 首次运行 2026-11-10 23:5x，qps=2597 / p50=54.8ms；上表为复跑留存值，两次结果一致：oversell=0。）

## 4. 结果解读

1. **不超卖**：100/200 库存在高并发下被恰好卖出 100/200 份，`success == stock`，库存 key 归零。
   Redis 单线程执行 Lua，判重→查库存→扣减三步在一个脚本内原子完成，不存在 TOCTOU 窗口。
2. **不少卖**：`stock_after=0` 且 `buyers == success`，说明没有「扣了库存没记人」的中间态。
3. **一人一单**：`duplicate` 计数为 0（每个压测 userId 只发一次请求）；判重逻辑由单测
   `SeckillLuaScriptTest.语义_成功_库存不足_重复下单` 单独覆盖（同一 userId 第二次返回 -1）。
4. **无请求错误**：`errors=0`，隧道链路稳定。

## 5. 已知局限（如实记录）

- 压测覆盖的是 **Redis 层原子扣减**（秒杀链路中最核心的并发瓶颈），不含 MQ 落库环节；
  MQ 消费可靠性由 `SeckillOrderConsumerTest`（6 个用例，含幂等/失败转死信）单测覆盖。
- 客户端为单连接多路复用，非每请求独占连接；由于并发压力最终汇聚到 Redis 服务端单线程，
  对「超卖=0」这一结论无影响。
- 延迟数字含 SSH 隧道 RTT，不代表同机房生产延迟。

## 6. 反例（什么情况下这条结论不成立）

- 若有人把判重/扣减逻辑拆出 Lua（如先 GET 再 DECR 分两条命令），原子性即失效，本结论不再成立；
- 若 Redis 部署为主从且主从切换时 `WAIT` 未配置，故障窗口内可能出现少量重复扣减，需运维侧另行验证；
- 压测只覆盖 `test:seckill:*` key，若生产 key 结构与 Lua 参数顺序不一致（key 命名错误），脚本防护无效。
