package com.zhixing.ai;

import cn.hutool.json.JSONUtil;
import dev.langchain4j.agent.tool.P;
import dev.langchain4j.agent.tool.Tool;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 行程规划工具集（LangChain4j Tool Calling）。
 *
 * <p>注册给 {@code AiServices} 后，由模型自主决策调用。数据来源为内置示例数据集
 * （天气/景点/美食），预算计算为纯本地计算——口径在返回值里透明标注。</p>
 *
 * <p>四个工具的职责边界（面试口径）：
 * <ul>
 *   <li>{@link #queryWeatherForecast}：天气查询，供行程排期参考（雨天换室内）</li>
 *   <li>{@link #searchAttractions}：景点检索，模型按兴趣偏好挑选</li>
 *   <li>{@link #recommendFood}：美食推荐，填充晚上时段</li>
 *   <li>{@link #calculateBudget}：预算分摊，纯计算零外部依赖，数字必须来自这里而不是模型心算</li>
 * </ul></p>
 *
 * @author zhixing
 * @since 2026-11-15
 */
@Slf4j
@Component
public class ItineraryTools {

    @Tool("查询目的地城市未来三天的天气概况，用于行程排期参考（雨天建议室内场馆）")
    public String queryWeatherForecast(@P("目的地城市，如：南京") String city) {
        log.info("[Tool] queryWeatherForecast city={}", city);
        List<Map<String, Object>> forecast = ItineraryDataSet.weatherOf(city);
        return JSONUtil.toJsonStr(Map.of(
                "city", city,
                "forecast", forecast,
                "dataNote", ItineraryDataSet.dataNote()));
    }

    @Tool("检索目的地城市的景点列表，返回名称/亮点/建议游玩时长/门票")
    public String searchAttractions(@P("目的地城市") String city,
                                    @P("兴趣偏好，如：历史人文、自然风光，可为空") String interest) {
        log.info("[Tool] searchAttractions city={}, interest={}", city, interest);
        List<Map<String, Object>> list = ItineraryDataSet.attractionsOf(city);
        return JSONUtil.toJsonStr(Map.of(
                "city", city,
                "interest", interest == null ? "" : interest,
                "attractions", list,
                "dataNote", ItineraryDataSet.dataNote()));
    }

    @Tool("推荐目的地城市的特色美食列表，用于填充晚间时段")
    public String recommendFood(@P("目的地城市") String city) {
        log.info("[Tool] recommendFood city={}", city);
        return JSONUtil.toJsonStr(Map.of(
                "city", city,
                "foods", ItineraryDataSet.foodsOf(city),
                "dataNote", ItineraryDataSet.dataNote()));
    }

    @Tool("计算旅行预算分摊：把总预算按天数与人数拆解为人均每天花费，返回精确到两位小数")
    public String calculateBudget(@P("总预算（元）") double totalBudget,
                                  @P("游玩天数") int days,
                                  @P("出行人数") int people) {
        log.info("[Tool] calculateBudget total={}, days={}, people={}", totalBudget, days, people);
        int safeDays = Math.max(1, days);
        int safePeople = Math.max(1, people);
        double perPersonPerDay = Math.round(totalBudget / safeDays / safePeople * 100) / 100.0;
        double perDayTotal = Math.round(totalBudget / safeDays * 100) / 100.0;
        return JSONUtil.toJsonStr(new LinkedHashMap<String, Object>() {{
            put("totalBudget", totalBudget);
            put("days", safeDays);
            put("people", safePeople);
            put("perPersonPerDay", perPersonPerDay);
            put("perDayTotal", perDayTotal);
            put("note", "纯本地计算，未调用外部接口");
        }});
    }
}
