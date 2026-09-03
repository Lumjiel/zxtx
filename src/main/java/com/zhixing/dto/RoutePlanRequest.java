package com.zhixing.dto;

import lombok.Data;

/**
 * AI 智能线路规划请求参数
 *
 * @author zhixing
 * @since 2026-09-02
 */
@Data
public class RoutePlanRequest {

    /** 目的地城市，如：南京 */
    private String city;

    /** 游玩天数 */
    private Integer days;

    /** 同游人员，如：亲子 / 情侣 / 朋友 / 独自 */
    private String companions;

    /** 兴趣偏好，如：历史人文、美食、自然风光 */
    private String interests;

    /** 预算范围，如：人均 1500 元以内 */
    private String budget;

    /** 出发地，如：上海 */
    private String startLocation;
}

