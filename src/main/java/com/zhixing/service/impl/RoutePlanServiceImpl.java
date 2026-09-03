package com.zhixing.service.impl;

import com.zhixing.dto.RoutePlanRequest;
import com.zhixing.service.IRoutePlanService;
import dev.langchain4j.model.chat.ChatLanguageModel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;

/**
 * AI 智能线路规划实现
 *
 * <p>基于 LangChain4j 调用大模型，将用户的城市/天数/同游人员/偏好/预算
 * 组装为结构化 Prompt，生成按天组织、含交通与费用的多日行程。</p>
 *
 * @author zhixing
 * @since 2026-09-02
 */
@Slf4j
@Service
public class RoutePlanServiceImpl implements IRoutePlanService {

    @Resource
    private ChatLanguageModel chatLanguageModel;

    @Override
    public String planRoute(RoutePlanRequest request) {
        String prompt = buildPrompt(request);
        log.info("AI 线路规划请求: city={}, days={}", request.getCity(), request.getDays());
        String result = chatLanguageModel.generate(prompt);
        log.info("AI 线路规划完成, 结果长度={}", result == null ? 0 : result.length());
        return result;
    }

    /**
     * 组装结构化 Prompt：把用户需求约束成可执行的行程规划指令
     */
    private String buildPrompt(RoutePlanRequest request) {
        StringBuilder sb = new StringBuilder();
        sb.append("你是一位资深的旅行规划师。请根据以下需求，为游客制定一份详细、可直接执行的旅行线路规划。\n\n");
        sb.append("【目的地】").append(defaultIfBlank(request.getCity(), "未指定")).append("\n");
        sb.append("【游玩天数】").append(request.getDays() == null ? 3 : request.getDays()).append(" 天\n");
        sb.append("【同游人员】").append(defaultIfBlank(request.getCompanions(), "未指定")).append("\n");
        sb.append("【兴趣偏好】").append(defaultIfBlank(request.getInterests(), "无特别偏好")).append("\n");
        sb.append("【预算范围】").append(defaultIfBlank(request.getBudget(), "未指定")).append("\n");
        sb.append("【出发地】").append(defaultIfBlank(request.getStartLocation(), "未指定")).append("\n\n");
        sb.append("【输出要求】\n");
        sb.append("1. 按天组织行程，每天拆分为上午、下午、晚上三个时段；\n");
        sb.append("2. 结合景点的地理位置和交通便利度合理安排顺序，避免往返折返；\n");
        sb.append("3. 每个景点标注预计游玩时长与大致门票/交通费用；\n");
        sb.append("4. 给出当地特色美食推荐与合适的住宿区域建议；\n");
        sb.append("5. 最后附 3-5 条出行贴士（天气、预约、避坑等）；\n");
        sb.append("6. 使用中文、以 Markdown 结构化输出。");
        return sb.toString();
    }

    private String defaultIfBlank(String value, String defaultValue) {
        return (value == null || value.trim().isEmpty()) ? defaultValue : value.trim();
    }
}
