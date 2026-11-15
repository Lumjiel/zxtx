package com.zhixing.ai;

import java.util.List;
import java.util.Map;

/**
 * 行程工具的内置数据集。
 *
 * <p>真实数据源（天气 API / POI 接口）未接入前的过渡实现：数据为内置示例，
 * 所有工具返回值都会带 {@code dataNote} 字段标注「内置示例数据」，
 * 保证下游不会把模拟数据当真实数据引用（口径诚实优先于功能好看）。</p>
 *
 * @author zhixing
 * @since 2026-11-15
 */
public final class ItineraryDataSet {

    private ItineraryDataSet() {
    }

    private static final String DATA_NOTE = "内置示例数据，非实时";

    /** 城市 → 景点列表（名称 / 亮点 / 建议时长小时 / 门票元） */
    public static final Map<String, List<Map<String, Object>>> ATTRACTIONS = Map.of(
            "南京", List.of(
                    attraction("中山陵", "民国历史地标，需预约", 3, 0),
                    attraction("夫子庙-秦淮河", "夜游秦淮画舫，小吃集中", 3, 0),
                    attraction("南京博物院", "民国馆复原街景，适合历史爱好者", 4, 0),
                    attraction("总统府", "近代史遗址", 2, 35),
                    attraction("牛首山", "佛顶宫建筑震撼", 4, 98)),
            "杭州", List.of(
                    attraction("西湖", "环湖步行或骑行，免费", 3, 0),
                    attraction("灵隐寺", "飞来峰造像", 3, 75),
                    attraction("西溪湿地", "摇橹船体验", 3, 80)),
            "苏州", List.of(
                    attraction("拙政园", "江南园林之首", 2, 80),
                    attraction("平江路", "古街巷弄", 2, 0),
                    attraction("虎丘", "斜塔", 2, 60)));

    /** 城市 → 美食列表 */
    public static final Map<String, List<String>> FOODS = Map.of(
            "南京", List.of("盐水鸭", "鸭血粉丝汤", "牛肉锅贴", "汤包"),
            "杭州", List.of("西湖醋鱼", "东坡肉", "片儿川", "定胜糕"),
            "苏州", List.of("松鼠桂鱼", "响油鳝糊", "蟹粉小笼", "桂花糖粥"));

    /** 城市 → 三日天气样本（日期偏移天 / 天气 / 温度） */
    public static final Map<String, List<Map<String, Object>>> WEATHER = Map.of(
            "南京", List.of(
                    weather("+0", "多云", "12~20℃"),
                    weather("+1", "晴", "10~21℃"),
                    weather("+2", "小雨", "11~18℃")),
            "杭州", List.of(
                    weather("+0", "晴", "14~22℃"),
                    weather("+1", "多云", "13~21℃"),
                    weather("+2", "阴", "12~19℃")),
            "苏州", List.of(
                    weather("+0", "阴", "13~20℃"),
                    weather("+1", "小雨", "12~18℃"),
                    weather("+2", "多云", "13~20℃")));

    /** 未知城市的兜底景点（通用模板行程素材） */
    public static final List<Map<String, Object>> GENERIC_ATTRACTIONS = List.of(
            attraction("市历史博物馆", "了解当地历史", 3, 0),
            attraction("老城步行街", "本地生活气息", 2, 0),
            attraction("滨水公园", "休闲散步", 2, 0));

    public static List<Map<String, Object>> attractionsOf(String city) {
        return ATTRACTIONS.getOrDefault(city, GENERIC_ATTRACTIONS);
    }

    public static List<String> foodsOf(String city) {
        return FOODS.getOrDefault(city, List.of("当地特色面食", "本地小吃拼盘", "夜市烧烤"));
    }

    public static List<Map<String, Object>> weatherOf(String city) {
        return WEATHER.getOrDefault(city, List.of(
                weather("+0", "多云", "10~20℃"),
                weather("+1", "晴", "10~21℃"),
                weather("+2", "多云", "11~20℃")));
    }

    public static String dataNote() {
        return DATA_NOTE;
    }

    private static Map<String, Object> attraction(String name, String highlight, int hours, int ticket) {
        return Map.of("name", name, "highlight", highlight, "hours", hours, "ticket", ticket);
    }

    private static Map<String, Object> weather(String offset, String condition, String temp) {
        return Map.of("dayOffset", offset, "condition", condition, "temp", temp);
    }
}
