package com.zhixing.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.zhixing.ai.ItineraryAssistant;
import com.zhixing.ai.ItineraryDataSet;
import com.zhixing.ai.RoutePlanStats;
import com.zhixing.dto.RoutePlanRequest;
import com.zhixing.service.IRoutePlanService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AI 智能线路规划实现（Tool Calling 版）。
 *
 * <p>v2 重构（2026-11-15，验收清单 T1）：旧实现端到端直调大模型拼 Prompt，
 * 用户口语文本导致意图跑偏、输出不可解析。新版链路：</p>
 *
 * <pre>
 * 请求 → 组装 JSON 输出契约 → ItineraryAssistant（模型自主调用
 *   天气/景点/美食/预算 4 个工具）→ 解析 JSON → 结构化结果
 *              ↓ 任一环节失败（无 Key / 模型异常 / 解析失败）
 *        降级：模板行程（内置数据集 + 预算均摊）+ 降级原因字段
 * </pre>
 *
 * <p>失败率统计见 {@link RoutePlanStats#snapshot()}；降级是功能不是故障——
 * 接口永远有可用输出，区别只在 {@code source} 字段。</p>
 *
 * @author zhixing
 * @since 2026-09-02（v1）/ 2026-11-15（v2 Tool Calling 重构）
 */
@Slf4j
@Service
public class RoutePlanServiceImpl implements IRoutePlanService {

    /** 单次行程最大天数：超出按 30 天规划（防误传超长天数撑爆模型上下文） */
    static final int MAX_DAYS = 30;
    private static final int DEFAULT_DAYS = 3;

    private static final Pattern FIRST_NUMBER = Pattern.compile("\\d+");

    private final ItineraryAssistant assistant;
    private final RoutePlanStats stats;
    private final ObjectMapper objectMapper;

    public RoutePlanServiceImpl(ItineraryAssistant assistant,
                                RoutePlanStats stats,
                                ObjectMapper objectMapper) {
        this.assistant = assistant;
        this.stats = stats;
        this.objectMapper = objectMapper;
    }

    @Override
    public String planRoute(RoutePlanRequest request) {
        stats.incTotal();
        int days = normalizeDays(request.getDays());
        String city = defaultIfBlank(request.getCity(), "南京");
        String result;
        try {
            result = planByAi(request, city, days);
            stats.incAiSuccess();
            log.info("AI 线路规划成功(工具调用链路): city={}, days={}", city, days);
        } catch (ParseFailure e) {
            stats.incParseFailed();
            log.warn("AI 输出解析失败，降级模板行程: city={}, reason={}", city, e.getMessage());
            log.info("RoutePlanStats {}", stats.snapshot());
            result = templatePlan(city, days, request.getBudget(), "parse_failed");
        } catch (Exception e) {
            stats.incAiError();
            log.warn("AI 调用异常，降级模板行程: city={}, reason={}", city, e.getMessage());
            log.info("RoutePlanStats {}", stats.snapshot());
            result = templatePlan(city, days, request.getBudget(), "ai_error");
        }
        return result;
    }

    // ---------- AI 主链路 ----------

    private String planByAi(RoutePlanRequest request, String city, int days) {
        String raw = assistant.plan(buildPrompt(request, city, days));
        JsonNode root = parsePlanJson(raw);
        validateShape(root);
        ObjectNode out = (ObjectNode) root;
        out.put("source", "ai_tool_calling");
        out.putNull("degradeReason");
        out.put("city", city);
        out.put("days", days);
        return objectMapper.writeValueAsString(out);
    }

    private String buildPrompt(RoutePlanRequest request, String city, int days) {
        StringBuilder sb = new StringBuilder();
        sb.append("请为一次旅行制定线路规划，先调用工具获取数据再作答。\n\n");
        sb.append("【目的地】").append(city).append("\n");
        sb.append("【游玩天数】").append(days).append(" 天\n");
        sb.append("【同游人员】").append(defaultIfBlank(request.getCompanions(), "未指定")).append("\n");
        sb.append("【兴趣偏好】").append(defaultIfBlank(request.getInterests(), "无特别偏好")).append("\n");
        sb.append("【预算范围】").append(defaultIfBlank(request.getBudget(), "未指定")).append("\n");
        sb.append("【出发地】").append(defaultIfBlank(request.getStartLocation(), "未指定")).append("\n\n");
        sb.append("【要求】\n");
        sb.append("1. 先调用 queryWeatherForecast 查天气，雨天优先安排室内场馆；\n");
        sb.append("2. 调用 searchAttractions 检索景点，结合兴趣偏好与地理位置排序，避免折返；\n");
        sb.append("3. 调用 recommendFood 取美食填充晚间时段；\n");
        sb.append("4. 调用 calculateBudget 计算预算分摊，费用数字必须取其返回值，禁止心算；\n");
        sb.append("5. 只输出一个 JSON 对象，结构如下（不要 Markdown 标记、不要解释文字）：\n");
        sb.append("{\"city\":\"\",\"days\":3,\"itineraries\":[{\"day\":1,\"sessions\":[{\"period\":\"上午\",\"activity\":\"\",\"hours\":3,\"cost\":0}],\"hotelArea\":\"\"}],");
        sb.append("\"foods\":[\"\"],\"totalCost\":0,\"tips\":[\"\"]}");
        return sb.toString();
    }

    /** 从模型回复中剥离出 JSON（容忍 ```json 围栏与前后闲话） */
    private JsonNode parsePlanJson(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            throw new ParseFailure("模型回复为空");
        }
        int start = raw.indexOf('{');
        int end = raw.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new ParseFailure("回复中不含 JSON 对象");
        }
        try {
            return objectMapper.readTree(raw.substring(start, end + 1));
        } catch (Exception e) {
            throw new ParseFailure("JSON 解析异常: " + e.getMessage());
        }
    }

    /** 结构校验：itineraries 必须是非空数组（空壳 JSON 也算解析失败） */
    private void validateShape(JsonNode root) {
        JsonNode it = root.get("itineraries");
        if (it == null || !it.isArray() || it.isEmpty()) {
            throw new ParseFailure("itineraries 缺失或为空");
        }
    }

    // ---------- 降级链：模板行程 ----------

    /**
     * 模板行程：内置数据集排期 + 预算均摊。降级是功能不是故障，
     * source 字段让前端/调用方明确知道这一版不是 AI 生成。
     */
    private String templatePlan(String city, int days, String budget, String reason) {
        ObjectNode out = objectMapper.createObjectNode();
        out.put("source", "template_fallback");
        out.put("degradeReason", reason);
        out.put("city", city);
        out.put("days", days);

        List<Map<String, Object>> attractions = ItineraryDataSet.attractionsOf(city);
        List<String> foods = ItineraryDataSet.foodsOf(city);
        ArrayNode itineraries = objectMapper.createArrayNode();
        for (int d = 0; d < days; d++) {
            ObjectNode day = objectMapper.createObjectNode();
            day.put("day", d + 1);
            ArrayNode sessions = objectMapper.createArrayNode();
            Map<String, Object> spot = attractions.get(d % attractions.size());
            sessions.add(session("上午", spot.get("name") + "（" + spot.get("highlight") + "）",
                    (Integer) spot.get("hours"), (Integer) spot.get("ticket")));
            Map<String, Object> spot2 = attractions.get((d + 1) % attractions.size());
            sessions.add(session("下午", spot2.get("name") + "（" + spot2.get("highlight") + "）",
                    (Integer) spot2.get("hours"), (Integer) spot2.get("ticket")));
            sessions.add(session("晚上", foods.get(d % foods.size()) + " + 夜市散步", 2, 0));
            day.set("sessions", sessions);
            day.put("hotelArea", "市中心交通便利区域");
            itineraries.add(day);
        }
        out.set("itineraries", itineraries);
        ArrayNode foodArr = objectMapper.createArrayNode();
        foods.forEach(foodArr::add);
        out.set("foods", foodArr);

        int total = extractBudget(budget);
        out.put("totalCost", total);
        out.put("budgetPerDay", Math.round(total * 100.0 / days) / 100.0);
        ArrayNode tips = objectMapper.createArrayNode();
        tips.add("天气/景点数据为内置示例数据，非实时");
        out.set("tips", tips);
        return out.toString();
    }

    private ObjectNode session(String period, String activity, Integer hours, Integer cost) {
        ObjectNode n = objectMapper.createObjectNode();
        n.put("period", period);
        n.put("activity", activity);
        n.put("hours", hours == null ? 2 : hours);
        n.put("cost", cost == null ? 0 : cost);
        return n;
    }

    // ---------- 工具方法 ----------

    static int normalizeDays(Integer days) {
        if (days == null || days < 1) {
            return DEFAULT_DAYS;
        }
        return Math.min(days, MAX_DAYS);
    }

    /** 从「人均 1500 元以内」这类自由文本里抠出第一个数字当总预算，抠不出给 1000 */
    static int extractBudget(String budget) {
        if (budget != null) {
            Matcher m = FIRST_NUMBER.matcher(budget);
            if (m.find()) {
                return Integer.parseInt(m.group());
            }
        }
        return 1000;
    }

    private static String defaultIfBlank(String value, String defaultValue) {
        return value == null || value.trim().isEmpty() ? defaultValue : value.trim();
    }

    /** 解析失败专用异常：与模型/网络异常区分开，进不同的失败计数器 */
    static class ParseFailure extends RuntimeException {
        ParseFailure(String message) {
            super(message);
        }
    }
}
