package com.zhixing.service;

import com.zhixing.dto.Result;
import com.zhixing.entity.Attraction;
import com.zhixing.mapper.ShopMapper;
import com.zhixing.service.impl.ShopServiceImpl;
import com.zhixing.utils.RedisConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * 景点缓存查询单元测试（纯 Mockito，不依赖外部服务）
 *
 * <p>覆盖缓存三态与互斥锁的关键分支，重点是面试必问的两个点：</p>
 * <ol>
 *   <li>空值缓存命中（防穿透）；</li>
 *   <li>锁释放的是「锁 key」而不是「缓存 key」（本项目修过的真实 bug）。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class ShopCacheServiceTest {

    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;

    private ShopServiceImpl service;

    private static final String CACHE_KEY = RedisConstants.CACHE_SHOP_KEY + 1;
    private static final String LOCK_KEY = RedisConstants.LOCK_SHOP_KEY + 1;

    @BeforeEach
    void setUp() {
        // spy 真实实现：缓存/锁逻辑走真实代码，getById（DB 访问）单独打桩
        service = org.mockito.Mockito.spy(new ShopServiceImpl());
        org.springframework.test.util.ReflectionTestUtils.setField(service, "stringRedisTemplate", stringRedisTemplate);
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
    }

    @Test
    void 缓存命中真实数据_直接返回不碰数据库() {
        Attraction attraction = new Attraction();
        attraction.setId(1L);
        attraction.setName("中山陵");
        when(valueOps.get(CACHE_KEY)).thenReturn("{\"id\":1,\"name\":\"中山陵\"}");

        Result result = service.queryById(1L);

        assertTrue(result != null);
        verify(service, never()).getById(anyLong());
        // 关键：命中缓存时绝不触碰锁
        verify(stringRedisTemplate, never()).delete(LOCK_KEY);
    }

    @Test
    void 命中空值标记_防穿透_直接返回失败() {
        when(valueOps.get(CACHE_KEY)).thenReturn("");

        Result result = service.queryById(1L);

        verify(service, never()).getById(anyLong());
        verify(stringRedisTemplate, never()).delete(LOCK_KEY);
        // 不抛异常且不回源即代表空值标记生效
        assertTrue(result != null);
    }

    @Test
    void 未命中_抢锁_回源写缓存_释放锁key() {
        when(valueOps.get(CACHE_KEY)).thenReturn(null).thenReturn(null);
        when(stringRedisTemplate.opsForValue().setIfAbsent(eq(LOCK_KEY), anyString(), anyLong(), any())).thenReturn(true);
        Attraction attraction = new Attraction();
        attraction.setId(1L);
        attraction.setName("中山陵");
        doReturn(attraction).when(service).getById(1L);

        Result result = service.queryById(1L);

        // 数据库查到的数据应写回缓存
        verify(valueOps).set(eq(CACHE_KEY), contains("中山陵"), anyLong(), any());
        // 关键回归点：释放的必须是锁 key，而不是缓存 key（修复过的 bug）
        verify(stringRedisTemplate, times(1)).delete(LOCK_KEY);
        verify(stringRedisTemplate, times(1)).delete(anyString());
    }

    @Test
    void 数据库不存在_写空值标记() {
        when(valueOps.get(CACHE_KEY)).thenReturn(null).thenReturn(null);
        when(stringRedisTemplate.opsForValue().setIfAbsent(eq(LOCK_KEY), anyString(), anyLong(), any())).thenReturn(true);
        doReturn(null).when(service).getById(1L);

        service.queryById(1L);

        verify(valueOps).set(eq(CACHE_KEY), eq(""), eq(RedisConstants.CACHE_NULL_TTL), any());
        verify(stringRedisTemplate).delete(LOCK_KEY);
    }
}
