package com.zhixing.ai;

import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 行程工具集单测：验证每个工具返回可解析 JSON、口径诚实标注、预算计算正确。
 *
 * @author zhixing
 * @since 2026-11-15
 */
class ItineraryToolsTest {

    private final ItineraryTools tools = new ItineraryTools();

    @Test
    @DisplayName("天气工具：返回三天预报并标注内置示例数据")
    void weatherShouldReturnForecastWithHonestNote() {
        String json = tools.queryWeatherForecast("南京");
        JSONObject obj = JSONUtil.parseObj(json);
        assertThat(obj.getStr("city")).isEqualTo("南京");
        assertThat(obj.getJSONArray("forecast")).isNotNull();
        assertThat(obj.getJSONArray("forecast").size()).isEqualTo(3);
        assertThat(obj.getStr("dataNote")).contains("内置示例数据");
    }

    @Test
    @DisplayName("景点工具：已知城市返回景点库，未知城市返回通用兜底")
    void attractionsShouldFallbackForUnknownCity() {
        JSONObject known = JSONUtil.parseObj(tools.searchAttractions("南京", "历史人文"));
        assertThat(known.getJSONArray("attractions").size()).isGreaterThanOrEqualTo(3);

        JSONObject unknown = JSONUtil.parseObj(tools.searchAttractions("克拉玛依", null));
        assertThat(unknown.getJSONArray("attractions").size()).isGreaterThanOrEqualTo(3);
        assertThat(unknown.getStr("dataNote")).contains("内置示例数据");
    }

    @Test
    @DisplayName("美食工具：返回非空列表")
    void foodShouldReturnNonEmptyList() {
        JSONObject obj = JSONUtil.parseObj(tools.recommendFood("杭州"));
        JSONArray foods = obj.getJSONArray("foods");
        assertThat(foods).isNotNull();
        assertThat(foods.size()).isGreaterThanOrEqualTo(3);
    }

    @Test
    @DisplayName("预算工具：1500 元 / 3 天 / 2 人 = 人均每天 250.0")
    void budgetShouldComputeExactly() {
        JSONObject obj = JSONUtil.parseObj(tools.calculateBudget(1500, 3, 2));
        assertThat(obj.getDouble("perPersonPerDay")).isEqualTo(250.0);
        assertThat(obj.getDouble("perDayTotal")).isEqualTo(500.0);
    }

    @Test
    @DisplayName("预算工具：天数/人数非法输入按 1 兜底，不抛异常")
    void budgetShouldTolerateInvalidInput() {
        JSONObject obj = JSONUtil.parseObj(tools.calculateBudget(1000, 0, -2));
        assertThat(obj.getInt("days")).isEqualTo(1);
        assertThat(obj.getInt("people")).isEqualTo(1);
        assertThat(obj.getDouble("perPersonPerDay")).isEqualTo(1000.0);
    }
}
