# mvn test 基线留档 — 2026-11-17

## 环境

| 项 | 值 |
|:--|:--|
| 日期 | 2026-11-17 |
| 命令 | `mvn -B test`（在 master `bdfa17a`，即 PR #1 合并后） |
| Maven | Apache Maven 3.9.11 (`/usr/local/bin/mvn`) |
| Java | 17.0.20-ga, TencentKonaJDK（`/usr/lib/jvm/java-17-konajdk-17.0.20-3.oc9`） |
| 结果 | **BUILD SUCCESS**，退出码 0 |

## 统计行原文（默认环境，无 REDIS_PASSWORD）

```
[INFO] Tests run: 24, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS
```

分测试类：

| 测试类 | 原文 |
|:--|:--|
| SeckillOrderConsumerTest | `Tests run: 7, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 4.925 s` |
| SeckillLuaScriptTest | `Tests run: 0, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 3.260 s` |
| RoutePlanServiceImplTest | `Tests run: 8, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 1.165 s` |
| ShopCacheServiceTest | `Tests run: 4, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.497 s` |
| ItineraryToolsTest | `Tests run: 5, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 0.099 s` |

## 跳过用例及原因

- **SeckillLuaScriptTest 全部 2 个用例未执行**（类级 `Tests run: 0`）。
- 机制：`@BeforeAll` 里对 `localhost:6379` 探活失败，走
  `Assumptions.assumeTrue(false, "本地 Redis 不可用，跳过 Lua 测试: ...")`
  （`src/test/java/com/zhixing/lua/SeckillLuaScriptTest.java:64`），
  JUnit 5 在类初始化阶段放弃，2 个 `@Test` 不计入运行。
- 与 issue 预期一致：无 Redis 时 Lua 用例 Assumptions 跳过属正常，跳过 2 个 ≤ 2 可接受。

## 补充验证：Redis 可用时 Lua 用例全绿

本机 `zxtx-redis-tunnel.service` 在 127.0.0.1:6379 有隧道。带上
`REDIS_PASSWORD`（读自 /etc/night.env，仅作环境变量，不入代码/日志）单独重跑：

```
mvn -B test -Dtest=SeckillLuaScriptTest
[INFO] Tests run: 2, Failures: 0, Errors: 0, Skipped: 0, Time elapsed: 5.522 s -- in com.zhixing.lua.SeckillLuaScriptTest
[INFO] BUILD SUCCESS
```

即：全部 26 个用例（24 + 2 Lua）在 Redis 可用时均为绿。

## 与 issue 预期数的偏差说明

issue 预期 `Tests run: 17`（消费者 7 + AI mock 4 + 缓存 4 + Lua 2）。实际默认跑出 24，
原因是 PR #1（feat/tool-calling-route）合入后新增了 `RoutePlanServiceImplTest`（8）与
`ItineraryToolsTest`（5），AI mock 由 4 增至 5、去掉 Lua 2 个跳过：7 + 8 + 4 + 5 = 24。
未修任何业务代码、未删用例、未放宽断言；全部绿，无失败原文需要贴 issue。
