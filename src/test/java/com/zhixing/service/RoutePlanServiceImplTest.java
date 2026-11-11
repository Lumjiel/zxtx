package com.zhixing.service;

import com.zhixing.dto.RoutePlanRequest;
import com.zhixing.service.impl.RoutePlanServiceImpl;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AI 线路规划单元测试（Mockito 模拟 ChatLanguageModel，不花真实 API key）
 *
 * <p>被测逻辑是「需求 → 结构化 Prompt → 调模型 → 透传结果」这条链路的正确性：
 * 用户输入必须完整进入 Prompt、空字段必须落到默认值、模型只被调一次、结果原样返回。</p>
 */
@ExtendWith(MockitoExtension.class)
class RoutePlanServiceImplTest {

    @Mock
    private ChatLanguageModel chatModel;

    private RoutePlanServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RoutePlanServiceImpl();
        // 字段是 @Resource 注入的，测试里用反射注入
        org.springframework.test.util.ReflectionTestUtils.setField(service, "chatLanguageModel", chatModel);
    }

    @Test
    void 完整请求_prompt包含全部用户输入_模型恰好调一次() {
        when(chatModel.generate(anyString())).thenReturn("# 第 1 天行程……");

        RoutePlanRequest req = new RoutePlanRequest();
        req.setCity("杭州");
        req.setDays(2);
        req.setCompanions("情侣");
        req.setInterests("西湖、古镇");
        req.setBudget("3000 元");
        req.setStartLocation("上海");

        String result = service.planRoute(req);

        assertEquals("# 第 1 天行程……", result, "模型返回结果应原样透传给调用方");
        org.mockito.ArgumentCaptor<String> captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(chatModel, times(1)).generate(captor.capture());
        String prompt = captor.getValue();
        assertTrue(prompt.contains("杭州"), "Prompt 必须包含目的地");
        assertTrue(prompt.contains("2 天"), "Prompt 必须包含天数");
        assertTrue(prompt.contains("情侣"), "Prompt 必须包含同游人员");
        assertTrue(prompt.contains("西湖、古镇"), "Prompt 必须包含兴趣偏好");
        assertTrue(prompt.contains("3000 元"), "Prompt 必须包含预算");
        assertTrue(prompt.contains("上海"), "Prompt 必须包含出发地");
    }

    @Test
    void 空字段_落到默认值_不产生空白占位() {
        when(chatModel.generate(anyString())).thenReturn("ok");

        RoutePlanRequest req = new RoutePlanRequest();
        req.setCity("  "); // 空白串视同未填
        // days、其余字段全部 null
        service.planRoute(req);

        org.mockito.ArgumentCaptor<String> captor = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(chatModel, times(1)).generate(captor.capture());
        String prompt = captor.getValue();
        assertTrue(prompt.contains("未指定"), "空白目的地应落到默认占位「未指定」");
        assertTrue(prompt.contains("3 天"), "天数缺省应为 3 天");
        assertTrue(prompt.contains("无特别偏好"), "偏好缺省占位应生效");
    }

    @Test
    void 模型抛异常_异常向上传播_不吞不降级() {
        when(chatModel.generate(anyString())).thenThrow(new RuntimeException("api key invalid"));

        RoutePlanRequest req = new RoutePlanRequest();
        req.setCity("北京");

        try {
            service.planRoute(req);
            org.junit.jupiter.api.Assertions.fail("模型异常必须向上传播，不能被吞掉");
        } catch (RuntimeException expected) {
            assertEquals("api key invalid", expected.getMessage());
        }
    }

    @Test
    void 模型返回null_透传null_调用方拿到可判空结果() {
        when(chatModel.generate(anyString())).thenReturn(null);
        assertEquals(null, service.planRoute(new RoutePlanRequest()));
        verify(chatModel, times(1)).generate(anyString());
    }
}
