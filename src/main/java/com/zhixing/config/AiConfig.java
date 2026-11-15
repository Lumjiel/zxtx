package com.zhixing.config;

import com.zhixing.ai.ItineraryAssistant;
import com.zhixing.ai.ItineraryTools;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.service.AiServices;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Duration;

/**
 * AI 大模型配置
 *
 * <p>通过 LangChain4j 的 OpenAI 兼容客户端对接阿里云百炼 DashScope
 * （compatible-mode 兼容 OpenAI 协议，可直接复用 Chat Completions 接口）。</p>
 *
 * @author zhixing
 * @since 2026-09-02
 */
@Slf4j
@Configuration
public class AiConfig {

    @Value("${ai.api-key:}")
    private String apiKey;

    @Value("${ai.base-url:https://dashscope.aliyuncs.com/compatible-mode/v1}")
    private String baseUrl;

    @Value("${ai.model:qwen-plus}")
    private String model;

    @Value("${ai.temperature:0.7}")
    private Double temperature;

    @Bean
    public ChatLanguageModel chatLanguageModel() {
        if (apiKey == null || apiKey.trim().isEmpty()) {
            log.warn("未配置 ai.api-key（DASHSCOPE_API_KEY），AI 线路规划将不可用");
        }
        return OpenAiChatModel.builder()
                .apiKey(apiKey)
                .baseUrl(baseUrl)
                .modelName(model)
                .temperature(temperature)
                .timeout(Duration.ofSeconds(60))
                .build();
    }

    /**
     * 带工具的行程规划助手。
     *
     * <p>{@code AiServices} 把 {@link ItineraryTools} 的 4 个 @Tool 方法注册为模型可调用的工具，
     * 模型自主决策调用（天气/景点/美食/预算），最终按 JSON 契约输出。
     * 注意：即使 api-key 为空也正常装配——调用时会抛异常，由
     * {@code RoutePlanServiceImpl} 的降级链兜底为模板行程。</p>
     */
    @Bean
    public ItineraryAssistant itineraryAssistant(ChatLanguageModel chatLanguageModel,
                                                 ItineraryTools itineraryTools) {
        return AiServices.builder(ItineraryAssistant.class)
                .chatLanguageModel(chatLanguageModel)
                .tools(itineraryTools)
                .systemMessage("""
                        你是一位资深旅行规划师。你可以调用工具查询天气、景点、美食并计算预算分摊。
                        规划行程前必须先调用工具获取数据，禁止凭空编造景点名称与价格；
                        费用数字必须使用 calculateBudget 工具的计算结果。
                        最终回答只输出一个 JSON 对象，不要输出任何 Markdown 代码块标记或解释文字。
                        """)
                .build();
    }
}

