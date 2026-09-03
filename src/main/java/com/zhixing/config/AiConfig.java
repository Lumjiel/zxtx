package com.zhixing.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
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
}

