package com.zhixing.ai;

import dev.langchain4j.service.SystemMessage;
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
     * 生成行程规划（模型可能先调用工具再作答）。
     * 系统消息约束：先查工具再作答、费用必须取工具结果、只输出 JSON。
     *
     * @param userMessage 已组装好的规划指令（含 JSON 输出契约）
     * @return 模型原始回复，约定为纯 JSON 文本
     */
    @SystemMessage("""
            你是一位资深旅行规划师。你可以调用工具查询天气、景点、美食并计算预算分摊。
            规划行程前必须先调用工具获取数据，禁止凭空编造景点名称与价格；
            费用数字必须使用 calculateBudget 工具的计算结果。
            最终回答只输出一个 JSON 对象，不要输出任何 Markdown 代码块标记或解释文字。
            """)
    String plan(@UserMessage String userMessage);
}
