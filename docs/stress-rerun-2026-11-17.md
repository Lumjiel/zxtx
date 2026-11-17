# 秒杀压测复跑留档（2026-11-17）

- 目的：复现 `docs/seckill-stress-report.md`（2026-11-10）的压测结论，证明结果可复现。
- 结论先行：**两轮均 `VERDICT=PASS`，正确性数字与 2026-11-10 完全一致**——
  success 恰等于库存、oversell=0、stock_after=0、buyers==success、duplicate=0、errors=0。
  吞吐/延迟数字有波动（见 §4 对照），属环境噪声，不影响「不超卖、不少卖、一人一单」结论。
- 复现命令（与本报告逐字一致）：

```bash
source /etc/night.env   # 注入 REDIS_PASSWORD（隧道机），密码不落盘、不进提交
export REDIS_PASSWORD
scripts/seckill-stress.sh 1000 100   # 轮 1
scripts/seckill-stress.sh 5000 200   # 轮 2
```

- 前提检查：`127.0.0.1:6379`（zxtx-redis SSH 隧道）PING 正常；跑前后 `test:seckill:*` key 均为空
  （跑完即删，脚本自动清理，另经 `KEYS test:seckill:*` 独立复核）。
- 红线遵守：仅使用 `test:seckill:stock:stress` / `test:seckill:user:stress`；
  数字为原始输出逐行照录，未做任何修改。

## 1. 原始输出（逐行照录，真实运行 2026-11-17 夜）

### 轮 1：1000 请求 / 100 库存（退出码 0）

```
script sha1=52c416dc02a7eedbf2bd78b2df112d3c1151ad34
---- RESULT ----
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
wall_ms=692
qps=1445
latency_p50_ms=66.639
latency_p95_ms=123.330
latency_p99_ms=147.825
VERDICT=PASS
```

### 轮 2：5000 请求 / 200 库存（退出码 0）

```
script sha1=52c416dc02a7eedbf2bd78b2df112d3c1151ad34
---- RESULT ----
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
wall_ms=1134
qps=4409
latency_p50_ms=30.509
latency_p95_ms=104.520
latency_p99_ms=130.777
VERDICT=PASS
```

## 2. 环境输出（`lscpu` / `free -h`，2026-11-17 夜实测）

### lscpu

```
Architecture:                            x86_64
CPU op-mode(s):                          32-bit, 64-bit
Address sizes:                           48 bits physical, 48 bits virtual
Byte Order:                              Little Endian
CPU(s):                                  2
On-line CPU(s) list:                     0,1
Vendor ID:                               AuthenticAMD
BIOS Vendor ID:                          Smdbmds
Model name:                              AMD EPYC 7K83 64-Core Processor
BIOS Model name:                         3.0  CPU @ 2.0GHz
BIOS CPU family:                         1
CPU family:                              25
Model:                                   1
Thread(s) per core:                      2
Core(s) per socket:                      1
Socket(s):                               1
Stepping:                                0
BogoMIPS:                                5090.43
Flags:                                   fpu vme de pse tsc msr pae mce cx8 apic sep mtrr pge mca cmov pat pse36 clflush mmx fxsr sse sse2 ht syscall nx mmxext fxsr_opt pdpe1gb rdtscp lm rep_good nopl cpuid extd_apicid tsc_known_freq pni pclmulqdq ssse3 fma cx16 sse4_1 sse4_2 x2apic movbe popcnt aes xsave avx f16c rdrand hypervisor lahf_lm cmp_legacy cr8_legacy abm sse4a misalignsse 3dnowprefetch osvw topoext ibpb vmmcall fsgsbase bmi1 avx2 smep bmi2 erms rdseed adx smap clflushopt sha_ni xsaveopt xsavec xgetbv1 arat fsrm
Hypervisor vendor:                       KVM
Virtualization type:                     full
L1d cache:                               32 KiB (1 instance)
L1i cache:                               32 KiB (1 instance)
L2 cache:                                512 KiB (1 instance)
L3 cache:                                32 MiB (1 instance)
NUMA node(s):                            1
NUMA node0 CPU(s):                       0,1
```

### free -h

```
               total        used        free      shared  buff/cache   available
Mem:           3.5Gi       3.1Gi       164Mi       4.7Mi       525Mi       428Mi
Swap:          2.0Gi       1.9Gi       103Mi
```

（压测执行时点内存已恢复至可用 ~1.3GB 后才开跑；上表为压测结束后的快照，
本机另有多个 zcode 会话进程常驻，swap 使用偏高是当晚常态。）

## 3. 与 2026-11-10 报告对照

### 轮 1：1000 请求 / 100 库存

| 指标 | 2026-11-10 | 2026-11-17 | 一致？ |
|:--|--:|--:|:--|
| script sha1 | `52c416dc02a7eedbf2bd78b2df112d3c1151ad34` | 同左 | ✅ |
| success | 100 | 100 | ✅ |
| duplicate | 0 | 0 | ✅ |
| stockout | 900 | 900 | ✅ |
| other | 0 | 0 | ✅ |
| errors | 0 | 0 | ✅ |
| oversell | 0 | 0 | ✅ |
| stock_after | 0 | 0 | ✅ |
| buyers | 100 | 100 | ✅ |
| VERDICT | PASS | PASS | ✅ |
| wall_ms | 369 | 692 | 波动 |
| qps | 2710 | 1445 | 波动 |
| latency p50/p95/p99 (ms) | 54.815 / 73.122 / 77.450 | 66.639 / 123.330 / 147.825 | 波动 |

### 轮 2：5000 请求 / 200 库存

| 指标 | 2026-11-10 | 2026-11-17 | 一致？ |
|:--|--:|--:|:--|
| script sha1 | `52c416dc02a7eedbf2bd78b2df112d3c1151ad34` | 同左 | ✅ |
| success | 200 | 200 | ✅ |
| duplicate | 0 | 0 | ✅ |
| stockout | 4800 | 4800 | ✅ |
| other | 0 | 0 | ✅ |
| errors | 0 | 0 | ✅ |
| oversell | 0 | 0 | ✅ |
| stock_after | 0 | 0 | ✅ |
| buyers | 200 | 200 | ✅ |
| VERDICT | PASS | PASS | ✅ |
| wall_ms | 771 | 1134 | 波动 |
| qps | 6485 | 4409 | 波动 |
| latency p50/p95/p99 (ms) | 26.440 / 48.310 / 73.418 | 30.509 / 104.520 / 130.777 | 波动 |

## 4. 波动解读（如实记录）

- **正确性指标两轮逐项一致**：超卖、少卖、重复、错误均为 0，与 2026-11-10 结论完全吻合，
  可复现性成立。
- **吞吐/延迟低于上次**（qps 2710→1445 / 6485→4409，p95 近乎翻倍）：当晚本机另有多个
  zcode 会话进程，内存吃紧（swap 已用 1.9Gi）、CPU 2 核争抢；压力来自客户端与隧道两端
  的本机侧，而「超卖=0」由 Redis 服务端 Lua 原子性保证，不受客户端资源噪声影响。
  这与原报告 §5 已知局限（隧道 RTT、单连接多路复用）口径一致。
- Lua 脚本 sha1 与上次相同（`52c416dc02a7eedbf2bd78b2df112d3c1151ad34`），
  即两次压测跑的是同一份生产脚本。
