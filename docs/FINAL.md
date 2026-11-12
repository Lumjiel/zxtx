# FINAL — 智行天下（zxtx · M1 阶段收尾）

> 收尾日期：2026-11-12（夜）。GOAL：「一个能不看稿、在白板上把秒杀链路讲透的系统」。

## 这是什么

智能旅游服务平台（面试作品）：Spring Boot 3.5.6 / Java 17 / MyBatis-Plus 3.5.12 / MySQL 8 + Redis（Lua 秒杀）+ RabbitMQ（可靠投递 + 死信）+ LangChain4j。核心叙事是秒杀链路：
请求 → Redis Lua 原子扣减 → 雪花订单号 → RabbitMQ（publisher-confirm）→ 手动 ack 消费（幂等 + 乐观锁兜底）→ 死信队列 → 落库。

## 怎么跑（≤3 条）

```bash
mvn -q -DskipTests package        # 编译打包
mvn -q test                       # 单测（SeckillLuaScriptTest 需 Redis，连不上自动跳过，非 @Disabled）
python3 /root/night/tools/check_regress.py   # 夜间验收（RC=0）
```

本地起服务需 MySQL / Redis / RabbitMQ：`mvn -q spring-boot:run`。

## 演示三条路径（照着 docs/interview/ 能讲）

1. **开场 3 分钟**：`docs/interview/01-project-overview.md` — 五段带时间轴的项目介绍，每个数字锚 pom/yaml/代码行号。
2. **秒杀链路白板图**：`docs/interview/02-seckill-design.md` + `06-whiteboard-script.md` — 一张图讲 Lua 扣减 / MQ 下单 / 死信兜底 / DB 落库，配套真实压测与 trace：`docs/seckill-stress-report.md`、`docs/seckill-trace.md`。
3. **深挖追问**：`docs/interview/03-mq-reliability-and-idempotency.md`（MQ 可靠性 + 幂等三层追问）、`05-boot3-upgrade-notes.md`（Boot 2.7/Java 8 → 3.5/17 升级复盘，30 秒复述脚本）、`04-evidence-map.md`（断言 → 证据映射）。

## 已知缺陷（如实）

- **表名仍是老 schema**（`tb_blog` / `tb_shop` / `tb_seckill_voucher` / `tb_sign` / `tb_follow`）——去痕迹的最后一公里，方案与风险已写在 `docs/debrand-plan.md` §2–§4，**等人拍板**，拍板前不动数据库。
- **统一返回体 `Result<T>` 改造中**：新接口用统一返回体，老接口保持兼容（AGENTS.md §5）。
- **SeckillLuaScriptTest 依赖真实 Redis**：走 `Assumptions.assumeTrue` 连不上跳过（src/test/java/com/zhixing/lua/SeckillLuaScriptTest.java:60-61），不是 @Disabled；全绿需 Redis 可达。
- `blueprint/`（审计台账 ZX-A001~A005 + 早期路线图 F-001~F-008）为历史调研产物，部分条目已演化（如 F-006 实际升到 Java 17 而非 21），仅留档，不作为现行计划；现行计划以 `docs/requirements.md` 为准。

## 不做的事

- 不改表名 / 不动数据库（等 debrand 拍板，属新 scope）。
- 不新增压测、不编数字（GOAL 边界；已有报告为 2026-11-10 真实运行，原始输出可复现）。
- 不加新技术栈（要加一个，先删一个）。
- 不用 @Disabled / 删测试 / 放宽断言让测试变绿。

## 待清理

无（本轮盘点：全仓库无 *.bak / *~ / 一次性脚本 / 空目录；blueprint/ 与历史压测/trace 文档按「历史记录不删」原则保留）。
