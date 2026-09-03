package com.zhixing.controller;

import com.zhixing.dto.Result;
import com.zhixing.dto.RoutePlanRequest;
import com.zhixing.service.IRoutePlanService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * AI 智能线路规划控制器
 *
 * @author zhixing
 * @since 2026-09-02
 */
@Slf4j
@RestController
@RequestMapping("/api/route")
public class RoutePlanController {

    @Resource
    private IRoutePlanService routePlanService;

    /**
     * AI 生成多日旅行线路
     *
     * @param request 规划请求
     * @return 行程规划文本
     */
    @PostMapping("/plan")
    public Result planRoute(@RequestBody RoutePlanRequest request) {
        if (request == null || request.getCity() == null || request.getCity().trim().isEmpty()) {
            return Result.fail("目的地城市不能为空");
        }
        try {
            String plan = routePlanService.planRoute(request);
            return Result.ok(plan);
        } catch (Exception e) {
            log.error("AI 线路规划失败", e);
            return Result.fail("AI 线路规划服务暂时不可用，请稍后重试");
        }
    }
}

