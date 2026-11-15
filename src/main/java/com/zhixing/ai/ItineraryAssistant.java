package com.zhixing.ai;

import dev.langchain4j.service.UserMessage;

/**
 * AI 线路规划助手（LangChain4j AiServices 接口）。
 *
 * <p>由 {@code AiConfig} 组装：绑定 ChatLanguageModel 与 {@link ItineraryTools}，
 * 模型自主决策调用工具后返回结构化 JSON 文本。解析与降级由
 * {@code RoutePlanServiceImpl} 负责——本接口只负责「带工具的对话」这一层。</p>
 *
 * @author zhixing
 * @since 2026-11-15
 */
public interface ItineraryAssistant {

    /**
     * 生成行程规划（模型可能先调用工具再作答）
     *
     * @param userMessage 已组装好的规划指令（含 JSON 输出契约）
     * @return 模型原始回复，约定为纯 JSON 文本
     */
    String plan(@UserMessage String userMessage);
}
