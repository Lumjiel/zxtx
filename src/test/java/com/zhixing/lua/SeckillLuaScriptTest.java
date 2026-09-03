package com.zhixing.lua;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Assumptions;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.core.io.ClassPathResource;

import java.util.Arrays;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 秒杀 Lua 脚本测试（依赖本地 Redis，连不上自动跳过）
 *
 * <p>覆盖两点：</p>
 * <ol>
 *   <li>语义正确性：成功 / 库存不足 / 重复下单三种返回码；</li>
 *   <li>并发正确性：100 库存、500 并发（每人不同 userId），成功数必须恰好 100，
 *       Redis 库存归零、已购集合恰好 100 人——Redis 层防超卖的直接证据。</li>
 * </ol>
 *
 * <p>运行方式：本地起 Redis 后执行 mvn test -Dtest=SeckillLuaScriptTest</p>
 */
class SeckillLuaScriptTest {

    private static final String STOCK_KEY = "test:seckill:stock:1";
    private static final String USER_KEY = "test:seckill:user:1";

    private static LettuceConnectionFactory factory;
    private static StringRedisTemplate redis;
    private static DefaultRedisScript<Long> script;

    @BeforeAll
    static void setUp() {
        factory = new LettuceConnectionFactory("localhost", 6379);
        factory.afterPropertiesSet();
        try {
            redis = new StringRedisTemplate(factory);
            redis.afterPropertiesSet();
            redis.opsForValue().get("__ping__");
        } catch (Exception e) {
            // 本地没有 Redis：跳过全部用例而不是报错
            Assumptions.assumeTrue(false, "本地 Redis 不可用，跳过 Lua 测试: " + e.getMessage());
        }
        script = new DefaultRedisScript<>();
        script.setLocation(new ClassPathResource("lua/seckill.lua"));
        script.setResultType(Long.class);
    }

    @AfterAll
    static void tearDown() {
        if (redis != null) {
            redis.delete(Arrays.asList(STOCK_KEY, USER_KEY));
        }
        if (factory != null) {
            factory.destroy();
        }
    }

    private Long run(String userId) {
        return redis.execute(script, Arrays.asList(STOCK_KEY, USER_KEY), userId);
    }

    @Test
    void 语义_成功_库存不足_重复下单() {
        redis.delete(Arrays.asList(STOCK_KEY, USER_KEY));
        redis.opsForValue().set(STOCK_KEY, "2");

        assertEquals(1L, run("101"), "第一个用户应当扣减成功");
        assertEquals(-1L, run("101"), "同一用户再次秒杀应判重复下单");
        assertEquals(1L, run("102"), "第二个用户（未买过）应当成功——不是被别人的 Set 挡掉");
        assertEquals(0L, run("103"), "库存耗尽后应返回库存不足");
        assertEquals("0", redis.opsForValue().get(STOCK_KEY), "库存应恰好归零");
    }

    @Test
    void 并发_100库存500请求_不超卖不少卖() throws InterruptedException {
        redis.delete(Arrays.asList(STOCK_KEY, USER_KEY));
        redis.opsForValue().set(STOCK_KEY, "100");

        int threads = 500;
        AtomicInteger success = new AtomicInteger();
        CountDownLatch ready = new CountDownLatch(threads);
        CountDownLatch done = new CountDownLatch(threads);
        ExecutorService pool = Executors.newFixedThreadPool(64);

        for (int i = 0; i < threads; i++) {
            final String userId = String.valueOf(10000 + i);
            pool.submit(() -> {
                ready.countDown();
                try {
                    // 所有线程在闩上对齐后同时开抢，模拟瞬时峰值
                    ready.await();
                    if (run(userId) == 1L) {
                        success.incrementAndGet();
                    }
                } catch (InterruptedException ignored) {
                } finally {
                    done.countDown();
                }
            });
        }
        done.await();
        pool.shutdown();

        assertEquals(100, success.get(), "成功数必须恰好等于库存数");
        assertEquals("0", redis.opsForValue().get(STOCK_KEY), "库存必须恰好归零");
        Set<String> buyers = redis.opsForSet().members(USER_KEY);
        assertEquals(100, buyers == null ? 0 : buyers.size(), "已购用户数必须恰好等于库存数（一人一单）");
    }
}
