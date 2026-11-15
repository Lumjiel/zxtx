package com.zhixing.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhixing.ai.ItineraryAssistant;
import com.zhixing.ai.RoutePlanStats;
import com.zhixing.dto.RoutePlanRequest;
import com.zhixing.service.impl.RoutePlanServiceImpl;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * AI 线路规划 v2（Tool Calling）主链路与降级链单测。
 *
 * <p>覆盖四条链路：AI 成功 / AI 异常降级 / 解析失败降级 / 模板边界，
 * 全部 mock 掉 {@link ItineraryAssistant}，不依赖真实 API Key（CI 无外部依赖可跑）。
 * v1 的「模型异常必须向上传播」契约在 v2 有意反转：异常一律降级为模板行程，
 * 接口永远有可用输出，区别只在 source / degradeReason 字段。</p>
 *
 * @author zhixing
 * @since 2026-11-15
 */
class RoutePlanServiceImplTest {

    private ItineraryAssistant assistant;
    private RoutePlanStats stats;
    private RoutePlanServiceImpl service;

    @BeforeEach
    void setUp() {
        assistant = mock(ItineraryAssistant.class);
        stats = new RoutePlanStats();
        service = new RoutePlanServiceImpl(assistant, stats, new ObjectMapper());
    }

    private RoutePlanRequest request(String city, Integer days, String budget) {
        RoutePlanRequest r = new RoutePlanRequest();
        r.setCity(city);
        r.setDays(days);
        r.setBudget(budget);
        return r;
    }

    private static final String VALID_AI_JSON = """
            {"city":"南京","days":3,"itineraries":[{"day":1,"sessions":[
            {"period":"上午","activity":"中山陵","hours":3,"cost":0}],"hotelArea":"市中心"}],
            "foods":["盐水鸭"],"totalCost":1500,"tips":["需预约"]}
            """;

    @Test
    @DisplayName("AI 成功链路：带围栏的合法 JSON → source=ai_tool_calling，成功计数 +1")
    void aiSuccessShouldTagSourceAndCount() {
        when(assistant.plan(anyString())).thenReturn("```json\n" + VALID_AI_JSON + "\n```");
        String out = service.planRoute(request("南京", 3, "人均 1500 元以内"));
        JSONObject obj = JSONUtil.parseObj(out);
        assertThat(obj.getStr("source")).isEqualTo("ai_tool_calling");
        assertThat(obj.getStr("degradeReason")).isNull();
        assertThat(obj.getInt("days")).isEqualTo(3);
        assertThat(stats.getAiSuccess()).isEqualTo(1);
        assertThat(stats.getParseFailed()).isEqualTo(0);
        assertThat(stats.failureRate()).isEqualTo(0.0);
    }

    @Test
    @DisplayName("Prompt 契约：用户输入进指令、JSON schema 与工具名出现在指令里")
    void promptShouldContainInputsAndContract() {
        org.mockito.ArgumentCaptor<String> captor = org.mockito.ArgumentCaptor.forClass(String.class);
        when(assistant.plan(captor.capture())).thenReturn(VALID_AI_JSON);
        service.planRoute(request("杭州", 2, "3000 元"));
        String prompt = captor.getValue();
        assertThat(prompt).contains("杭州").contains("2 天").contains("3000 元");
        assertThat(prompt).contains("queryWeatherForecast").contains("searchAttractions")
                .contains("calculateBudget").contains("itineraries");
    }

    @Test
    @DisplayName("模型异常 → 降级模板行程，source=template_fallback，reason=ai_error")
    void aiErrorShouldFallbackWithReason() {
        when(assistant.plan(anyString())).thenThrow(new RuntimeException("401 Unauthorized"));
        String out = service.planRoute(request("南京", 3, "人均 1500 元以内"));
        JSONObject obj = JSONUtil.parseObj(out);
        assertThat(obj.getStr("source")).isEqualTo("template_fallback");
        assertThat(obj.getStr("degradeReason")).isEqualTo("ai_error");
        assertThat(obj.getJSONArray("itineraries").size()).isEqualTo(3);
        assertThat(stats.getAiError()).isEqualTo(1);
        assertThat(stats.failureRate()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("解析失败：模型输出散文（无 JSON）→ 降级，reason=parse_failed")
    void parseFailureShouldFallbackAndCount() {
        when(assistant.plan(anyString())).thenReturn("抱歉，我无法完成该任务。");
        String out = service.planRoute(request("杭州", 2, "预算 800"));
        JSONObject obj = JSONUtil.parseObj(out);
        assertThat(obj.getStr("source")).isEqualTo("template_fallback");
        assertThat(obj.getStr("degradeReason")).isEqualTo("parse_failed");
        assertThat(stats.getParseFailed()).isEqualTo(1);
        assertThat(stats.getAiError()).isEqualTo(0);
    }

    @Test
    @DisplayName("空壳 JSON（itineraries 为空数组）也算解析失败")
    void emptyItinerariesShouldCountAsParseFailure() {
        when(assistant.plan(anyString())).thenReturn("{\"city\":\"南京\",\"itineraries\":[]}");
        String out = service.planRoute(request("南京", 3, null));
        JSONObject obj = JSONUtil.parseObj(out);
        assertThat(obj.getStr("source")).isEqualTo("template_fallback");
        assertThat(obj.getStr("degradeReason")).isEqualTo("parse_failed");
    }

    @Test
    @DisplayName("模板边界：days=45 被压到 30，预算自由文本抠第一个数字")
    void templateShouldCapDaysAndParseBudget() {
        when(assistant.plan(anyString())).thenThrow(new RuntimeException("no key"));
        String out = service.planRoute(request("苏州", 45, "预算 6000 元"));
        JSONObject obj = JSONUtil.parseObj(out);
        assertThat(obj.getInt("days")).isEqualTo(30);
        assertThat(obj.getLong("totalCost")).isEqualTo(6000L);
        assertThat(obj.getJSONArray("itineraries").size()).isEqualTo(30);
    }

    @Test
    @DisplayName("模板边界：days=null 回退默认 3 天，预算无数字回退 1000")
    void templateShouldApplyDefaults() {
        when(assistant.plan(anyString())).thenThrow(new RuntimeException("no key"));
        String out = service.planRoute(request("南京", null, "预算随便"));
        JSONObject obj = JSONUtil.parseObj(out);
        assertThat(obj.getInt("days")).isEqualTo(3);
        assertThat(obj.getLong("totalCost")).isEqualTo(1000L);
    }

    @Test
    @DisplayName("未知城市降级：模板行程走通用兜底景点，不抛异常")
    void unknownCityShouldUseGenericTemplate() {
        when(assistant.plan(anyString())).thenThrow(new RuntimeException("no key"));
        String out = service.planRoute(request("克拉玛依", 2, null));
        JSONObject obj = JSONUtil.parseObj(out);
        assertThat(obj.getStr("source")).isEqualTo("template_fallback");
        assertThat(obj.getJSONArray("itineraries").size()).isEqualTo(2);
        assertThat(obj.getStr("city")).isEqualTo("克拉玛依");
    }
}
