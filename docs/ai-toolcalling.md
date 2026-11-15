# AI 线路规划 v2：Tool Calling 重构设计（验收清单 T1）

> 2026-11-15。对应简历 Z-3 bullet 与 `05-面试准备/深挖审查/` 攻击面审查结论。

## 1. 为什么重构

v1（2026-09-02）端到端直调大模型：用户口语文本直接拼 Prompt 丢给模型，产生三个问题——
意图理解跑偏（口语文本信息杂乱）、输出不可解析（自由 Markdown，后端无法结构化消费）、
模型凭空编造景点与价格（无数据工具约束）。备份笔记 `项目钩子设计（NLU 结构化抽取）.md`
记录了 v1 时代的失控现场与正则兜底方案。

## 2. v2 链路

```
请求 → 组装 JSON 输出契约 → ItineraryAssistant（AiServices）
     → 模型自主决策调用 4 个 @Tool 工具
     → 解析 JSON（容忍围栏/闲话）+ 结构校验 → source=ai_tool_calling
                ↓ 任一环节失败（无 Key / 模型异常 / 解析失败 / 空壳 JSON）
     → 降级：模板行程（内置数据集 + 预算均摊）→ source=template_fallback + degradeReason
```

## 3. 工具集（4 个，模型自主决策调用）

| 工具 | 职责 | 数据来源 | 关键约束 |
|---|---|---|---|
| `queryWeatherForecast(city)` | 三日天气，供排期参考（雨天换室内） | 内置示例数据集 | 返回带 `dataNote`「内置示例数据，非实时」 |
| `searchAttractions(city, interest)` | 景点检索（名称/亮点/时长/门票） | 内置示例数据集（南京/杭州/苏州 + 通用兜底） | 同上 |
| `recommendFood(city)` | 美食推荐，填充晚间时段 | 内置示例数据集 | 同上 |
| `calculateBudget(totalBudget, days, people)` | 预算分摊（人均每天，两位小数） | **纯本地计算** | 费用数字必须取工具结果，禁止模型心算 |

诚实口径：模拟/内置数据强制打标（思想同 IRIS `mock_snapshot.py`——「标签含『模拟』，
保证降级检测不被绕过」）；预算计算是唯一真计算，模型只准搬运不准编。

## 4. 降级链（三层，语义分层）

| 触发 | 计数器 | degradeReason |
|---|---|---|
| 模型/网络异常（含无 api-key 401） | `aiError` | `ai_error` |
| 回复无 JSON / 解析异常 / `itineraries` 空壳 | `parseFailed` | `parse_failed` |
| （成功） | `aiSuccess` | 无 |

失败率 = (aiError + parseFailed) / total，`RoutePlanStats.snapshot()` 打进日志。
v1 的「模型异常向上传播」契约在 v2 **有意反转**：异常一律降级，接口永远有可用输出，
区别只在 `source` 字段——前端可据此渲染「示例行程」标识。

## 5. 边界防护

- 天数上限：`MAX_DAYS=30`（防误传超长天数撑爆上下文），下限 1、默认 3
- 预算解析：自由文本抠第一个数字（`人均 1500 元以内`→1500），无数字回退 1000
- 未知城市：内置数据集 miss → 通用兜底景点，不抛异常

## 6. 测试与验证

- `ItineraryToolsTest`（5 条）：4 工具返回可解析 JSON、诚实标注、预算精确计算、非法输入兜底
- `RoutePlanServiceImplTest`（8 条）：AI 成功 / 异常降级 / 解析失败 / 空壳 JSON / 天数压顶 / 默认值 / 未知城市 / Prompt 契约
- CI：`.github/workflows/ci.yml`（JDK17 + mvn test，无外部依赖），PR 触发

## 7. 已知边界（诚实口径，面试交底用）

- 天气/景点数据是内置示例，未接真实 API（高德/和风）——接真实数据源是下一迭代
- 失败率统计为进程级内存计数，多实例需换 Micrometer
- 模型自主决策的调用次数未强制上限（LangChain4j AiServices 内部有执行循环上限），
  与 IRIS 自研节点 5 轮上限是两种治理思路：框架代管 vs 自研管控
