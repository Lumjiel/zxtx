package com.zhixing.service;

import com.zhixing.dto.RoutePlanRequest;

/**
 * AI 智能线路规划服务接口
 *
 * @author zhixing
 * @since 2026-09-02
 */
public interface IRoutePlanService {

    /**
     * 基于目的地、天数、偏好与预算生成多日旅行线路
     *
     * @param request 规划请求
     * @return 结构化行程规划文本
     */
    String planRoute(RoutePlanRequest request);
}
