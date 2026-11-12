# PROGRESS — zxtx#M1 GOAL 推进（2026-11-12 夜，轮次 2/3 本会话）

## 本轮做了什么（features.json 23/23 全部 passes=true）

1. **一致性核查（F13/F19/F20）**：
   - F19 实现找到：点赞对账 = LikeCleanupTask.java:57（凌晨2点巡检）+:93-97（不一致→MQ）
     → LikeCleanupConsumer.java:37-57 → BlogServiceImpl.java:198-200。发现并修正 01 原文
     语义偏差：点赞**写路径是同步 DB**，走 MQ 异步的是**凌晨对账**——文档已按真实实现改写。
   - F20 三态缓存确认：ShopServiceImpl.java:47/:76-83/:97-110/:119，4 用例覆盖。
   - F13 全量核查 01/02/03/06：所有「代码里真有」机制锚到 src/ 实码；03 的
     「Spring AMQP 默认 PERSISTENT」联网查证（rabbitmq.com/tutorials/tutorial-two-spring-amqp，2026-11-12）。
2. **文档细化（F05/F07/F09）**：
   - interview/01 重写：3 分钟开场讲稿（5 段带时间轴）+ 全部数字锚 pom/yaml/代码行号
   - interview/03 重写：三段式机制行号锚点 + 4 个追问三层展开
   - interview/05 增补：jakarta 5 个类清单（grep 实测）、配置差异唯一处、30 秒复述脚本
   - 全部锚点 grep 实核，共修正 5 处行号偏差（含 convertAndSend 实为 :93-97 等）
3. **核查类（F14/F22/F23）**：
   - README 技术栈过时修复（Boot 2.7.17→3.5.6 / MP 3.4.3→3.5.12 / JDK 8→17，与 pom.xml:8,17,73 一致）；AGENTS.md §1 同步
   - 去痕迹终查：grep 黑马/hmdp/heima/慕课/教程 全仓库——src/ 零残留；唯一例外 docs/debrand-plan.md（计划文档自身）与 sql:568 'vhr'（种子随机用户名片段，误报）
   - docs/env.md 新建（本轮未装新依赖，照实记）

## 证据

- check_regress.py：最终 RC=0 ×2 连跑，完成度 23/23（基线 12/23 起步）
- 锚点实核命令：grep -n 各类名（详见 .night/features.json 各条 evidence，可逐条抽检）
- 外部查证：Spring AMQP 默认持久化，URL+日期见 docs/interview/03:16-19

## 审查结论（3 次自查，内存 838MB 未开子 agent）

- 正题：每条断言可追到命令输出或文件:行号；5 处行号偏差当场发现当场修。
- 反题：最大风险是「文档说法与代码语义不符」——唯一命中（点赞 MQ 说法）已改；
  外部事实（PERSISTENT 默认值）不凭印象，查证后才写入。
- 合题：站得住，无需返工。

## 还差什么 / 下一步

- **无未完成条目**（23/23）。唯一悬而未决：debrand-plan 方案选择**等人拍板**（红线不许本轮动数据库/改名）。
  拍板后属新 scope（表名迁移 + 种子数据清洗，plan 见 docs/debrand-plan.md §2-§4）。
- 建议：拍板前不需要再拉起本 GOAL 的执行轮；若拍板通过，开新 GOAL 执行 debrand。

STATUS: done

---

# 收尾轮（2026-11-12 夜，执行轮 2/3 后系统插入）

## 改了什么

- docs/FINAL.md（新建）：这是什么 / 怎么跑 3 条 / 演示三条路径 / 已知缺陷 3 条（表名未迁移等拍板项、Result 改造中、Lua 测试 Redis 跳过）/ 不做的事 / 待清理=无
- PROGRESS.md：本段交接

## 证据

- check_regress.py 连跑两次 RC=0、RC=0，完成度 23/23（基线 23/23）
- 冗余扫描：find 全仓库无 *.bak / *~ / *.tmp / 一次性脚本；blueprint/ 与 seckill-stress-report / seckill-trace 按「历史记录不删」保留
- FINAL.md 锚点抽检：SeckillLuaScriptTest.java:60 assumeTrue 属实

## 审查结论

正题：§8 收尾六件事全部落地。反题：FINAL.md 已知缺陷如实写、未夸大；blueprint/ 过时条目标注「仅留档」。合题：站得住。

## 没解决

- debrand（表名迁移）等人拍板，属新 scope（docs/debrand-plan.md §2–§4 已备好方案）

## 建议下一步

- 若拍板通过 → 开新 GOAL 执行 debrand；否则本项目归档，时间片让出

## 复核轮（2026-11-12 夜，done 后系统再拉起一次）

- 仅复核，未改任何文件：check_regress.py 两次 RC=0、23/23；FINAL.md / debrand-plan.md / env.md 在位
- 无值得做的增量：唯一悬置项 debrand 等人拍板（新 scope），其余均为低价值微调

STATUS: done

## 复核轮 2（2026-11-12 夜，done 后系统再拉起）

- 未改任何代码/文档。Redis 隧道今晚是通的（127.0.0.1:6379 可达），实跑 `mvn -q test`：
  **17/17 全绿，Failures=0，Errors=0，Skipped=0**——含 SeckillLuaScriptTest 2 用例
  （真连 Redis 执行 lua/seckill.lua，非 assumeTrue 跳过；Surefire 汇总可抽检）。
- check_regress.py RC=0，完成度 23/23。
- **给人的待办**：领导原话触发条件已满足——请把 `--test "mvn -q test"` 写进 oracle
  （注意：本机 Redis 依赖 zxtx-redis-tunnel.service，隧道不通时该测试会 skip 而非红，
  oracle 侧如需严格「0 skipped」判定需考虑这点）。
- debrand 仍等人拍板，无其他未决项。

STATUS: done

## 复核轮 3（2026-11-12 夜，done 后系统再拉起）

- 仅核实，未改任何文件：check_regress.py RC=0、完成度 23/23；features.json 全部 23 条
  均含可抽检 evidence（脚本核查 missing=[]）；工作区无临时文件/冗余产物。
- 无值得做的增量：mvn test 全绿已在复核轮 2 实证（17/17，含 Lua 真 Redis 用例）；
  debrand 等人拍板（新 scope）、oracle 写入 `--test` 是给人的待办——均已留档。
- 本 GOAL 无待执行项，请归档并让出时间片。

STATUS: done

## 复核轮 4（2026-11-12 夜，done 后系统再拉起）

- 仅核实，未改任何文件：check_regress.py RC=0、完成度 23/23（基线 23/23）。
- oracle 仍显示「跳过测试（未给命令）」——`--test "mvn -q test"` 写入 oracle 是给人的待办，
  mvn test 17/17 全绿已在复核轮 2 实证，本轮不重复跑。
- 无未决项：debrand 等人拍板（新 scope，见 docs/debrand-plan.md）。本 GOAL 请归档。
STATUS: done

## 复核轮 5（2026-11-12 夜，done 后系统再拉起）

- 仅核实，未改任何文件：check_regress.py RC=0、完成度 23/23（基线 23/23）。
- 结论与复核轮 4 相同：无待执行项。mvn test 全绿证据在复核轮 2；debrand 与
  oracle `--test` 写入均为人的待办，已留档（docs/debrand-plan.md、PROGRESS 复核轮 2）。
- 本 GOAL 请归档并让出时间片，无需再拉起。
STATUS: done

---

# 确认轮（2026-11-12 夜，系统再次拉起，复核后维持 done）

- 仅复核未改动：check_regress.py 连跑两次 RC=0，完成度 23/23（基线 23/23）。
- 产物齐全：docs/FINAL.md、docs/debrand-plan.md、docs/interview/ 六份（01-06）。
- 无高价值增量。两项待人的事不变：① debrand 方案拍板（docs/debrand-plan.md §2-§4）；
  ② mvn test 全绿后由人把 `--test "mvn -q test"` 写进 oracle（我无权改验收命令）。
- 本 GOAL 维持归档，让出时间片。
STATUS: done
