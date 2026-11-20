package com.zhixing.utils;

import com.zhixing.dto.UserDTO;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.data.redis.core.HashOperations;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 登录态刷新层降级单测（纯 Mockito，不依赖外部服务）
 *
 * <p>覆盖 Redis 故障时 refresh 层的两级降级语义——这是本项目登记过工程债后补上的修复：</p>
 * <ol>
 *   <li>entries 失败 = 身份无法确认 → 按「未装配」放行（保护路径自然 401，白名单不受累，
 *       降级语义与白名单语义同构，而不是 500）；</li>
 *   <li>expire 失败 = 身份已确认、仅 TTL 刷新失败 → 不撤销已装配的登录态（把用户随机登出
 *       比短暂不续期更伤）。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class RefreshTokenInterceptorTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private HashOperations<String, Object, Object> hashOps;
    @Mock
    private HttpServletRequest request;
    @Mock
    private HttpServletResponse response;

    private RefreshTokenInterceptor interceptor;

    private static final String TOKEN = "token-abc";
    private static final String KEY = RedisConstants.LOGIN_USER_KEY + TOKEN;

    @BeforeEach
    void setUp() {
        interceptor = new RefreshTokenInterceptor(stringRedisTemplate);
        when(stringRedisTemplate.opsForHash()).thenReturn(hashOps);
        UserHolder.removeUser();
    }

    @AfterEach
    void tearDown() {
        UserHolder.removeUser();
    }

    private Map<String, String> userMap() {
        return Map.of("id", "1", "nickName", "杰哥", "icon", "/imgs/a.png");
    }

    @Test
    void 无token_直接放行不碰Redis() throws Exception {
        when(request.getHeader("authorization")).thenReturn(null);

        assertTrue(interceptor.preHandle(request, response, new Object()));

        assertNull(UserHolder.getUser());
        verify(stringRedisTemplate, never()).opsForHash();
    }

    @Test
    void Redis故障_entries抛异常_按未装配放行且不装配用户() throws Exception {
        when(request.getHeader("authorization")).thenReturn(TOKEN);
        when(hashOps.entries(KEY)).thenThrow(new RuntimeException("connection refused"));

        assertTrue(interceptor.preHandle(request, response, new Object()));

        assertNull(UserHolder.getUser());
    }

    @Test
    void Redis故障_降级链组合_refresh放行后LoginInterceptor拦截返回401() throws Exception {
        // 完整降级链：refresh 层 Redis 故障放行 → LoginInterceptor 对未装配用户拦下（对应 401 响应），
        // 降级语义与白名单语义同构——白名单接口不被拖累，保护接口拿到 401 而不是 500
        when(request.getHeader("authorization")).thenReturn(TOKEN);
        when(hashOps.entries(KEY)).thenThrow(new RuntimeException("connection refused"));

        assertTrue(interceptor.preHandle(request, response, new Object()));
        assertNull(UserHolder.getUser());

        LoginInterceptor loginInterceptor = new LoginInterceptor();
        jakarta.servlet.http.HttpServletResponse resp401 =
                org.mockito.Mockito.mock(jakarta.servlet.http.HttpServletResponse.class);
        assertFalse(loginInterceptor.preHandle(request, resp401, new Object()));
        verify(resp401).setStatus(401);
    }

    @Test
    void 正常链路_装配用户并刷新TTL() throws Exception {
        when(request.getHeader("authorization")).thenReturn(TOKEN);
        when(hashOps.entries(KEY)).thenReturn(Map.copyOf(userMap()));

        assertTrue(interceptor.preHandle(request, response, new Object()));

        UserDTO user = UserHolder.getUser();
        assertNotNull(user);
        assertEquals(1L, user.getId());
        assertEquals("杰哥", user.getNickName());
        verify(stringRedisTemplate).expire(eq(KEY), eq(RedisConstants.LOGIN_CODE_TTL), eq(TimeUnit.MINUTES));
    }

    @Test
    void Redis故障_expire抛异常_身份已确认不撤销登录态() throws Exception {
        // TTL 刷新失败是副作用失败：user 已确认装配，必须继续放行
        when(request.getHeader("authorization")).thenReturn(TOKEN);
        when(hashOps.entries(KEY)).thenReturn(Map.copyOf(userMap()));
        doThrow(new RuntimeException("connection lost"))
                .when(stringRedisTemplate).expire(anyString(), anyLong(), eq(TimeUnit.MINUTES));

        assertTrue(interceptor.preHandle(request, response, new Object()));

        assertNotNull(UserHolder.getUser());
        assertEquals("杰哥", UserHolder.getUser().getNickName());
    }

    @Test
    void entries为空_未登录token_放行且不装配() throws Exception {
        when(request.getHeader("authorization")).thenReturn("expired-token");
        when(hashOps.entries(RedisConstants.LOGIN_USER_KEY + "expired-token"))
                .thenReturn(Map.of());

        assertTrue(interceptor.preHandle(request, response, new Object()));

        assertNull(UserHolder.getUser());
        verify(stringRedisTemplate, never()).expire(anyString(), anyLong(), eq(TimeUnit.MINUTES));
    }

    @Test
    void afterCompletion_清理ThreadLocal防内存泄漏() throws Exception {
        UserHolder.saveUser(new UserDTO());
        interceptor.afterCompletion(request, response, new Object(), null);

        assertNull(UserHolder.getUser());
    }
}
