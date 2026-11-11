package com.zhixing.lua;

import io.lettuce.core.RedisClient;
import io.lettuce.core.RedisURI;
import io.lettuce.core.api.StatefulRedisConnection;
import io.lettuce.core.protocol.ProtocolVersion;
import org.springframework.core.io.ClassPathResource;
import org.springframework.util.StreamUtils;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * 秒杀 Lua 脚本压测（独立 main，不进 mvn test）。
 *
 * 直连生产链路同款 Lua 脚本（src/main/resources/lua/seckill.lua），在上海 Redis 上
 * 用 test:seckill:* 前缀 key 模拟 N 并发抢购，验证「超卖 = 0」。
 *
 * 运行：scripts/seckill-stress.sh  （密码从 REDIS_PASSWORD 环境变量读，不落盘）
 */
public final class SeckillStress {

    private static final String STOCK_KEY = "test:seckill:stock:stress";
    private static final String USER_KEY = "test:seckill:user:stress";

    private SeckillStress() {
    }

    public static void main(String[] args) throws Exception {
        int threads = args.length > 0 ? Integer.parseInt(args[0]) : 1000;
        int stock = args.length > 1 ? Integer.parseInt(args[1]) : 100;

        String password = System.getenv("REDIS_PASSWORD");
        if (password == null || password.isEmpty()) {
            System.err.println("REDIS_PASSWORD 未设置，拒绝运行");
            System.exit(2);
        }
        String host = System.getenv().getOrDefault("SECKILL_REDIS_HOST", "127.0.0.1");
        int port = Integer.parseInt(System.getenv().getOrDefault("SECKILL_REDIS_PORT", "6379"));

        RedisURI uri = RedisURI.builder()
                .withHost(host).withPort(port).withPassword(password)
                .withTimeout(Duration.ofSeconds(10)).build();
        RedisClient client = RedisClient.create(uri);
        client.setOptions(io.lettuce.core.ClientOptions.builder()
                .protocolVersion(ProtocolVersion.RESP2).build());

        String lua;
        try (InputStream in = new ClassPathResource("lua/seckill.lua").getInputStream()) {
            lua = StreamUtils.copyToString(in, StandardCharsets.UTF_8);
        }

        try (StatefulRedisConnection<String, String> conn = client.connect()) {
            String sha = conn.sync().scriptLoad(lua);
            System.out.println("script sha1=" + sha);

            // 重置压测 key（只碰 test:seckill:* 前缀）
            conn.sync().del(STOCK_KEY, USER_KEY);
            conn.sync().set(STOCK_KEY, String.valueOf(stock));

            AtomicInteger success = new AtomicInteger();
            AtomicInteger duplicate = new AtomicInteger();
            AtomicInteger stockOut = new AtomicInteger();
            AtomicInteger other = new AtomicInteger();
            AtomicInteger errors = new AtomicInteger();
            AtomicLong totalLatencyNanos = new AtomicLong();
            long[] latencies = new long[threads];

            CountDownLatch done = new CountDownLatch(threads);
            ExecutorService pool = Executors.newFixedThreadPool(Math.min(threads, 256));
            long t0 = System.nanoTime();

            for (int i = 0; i < threads; i++) {
                final int idx = i;
                final String userId = String.valueOf(900_000_000L + i); // 压测专用 userId 段
                pool.submit(() -> {
                    try {
                        long s = System.nanoTime();
                        // EVALSHA 与生产 StringRedisTemplate.execute(script,...) 走同一原子脚本
                        Long r = conn.sync().evalsha(sha,
                                io.lettuce.core.ScriptOutputType.INTEGER,
                                new String[]{STOCK_KEY, USER_KEY}, userId);
                        long e = System.nanoTime();
                        latencies[idx] = e - s;
                        totalLatencyNanos.addAndGet(e - s);
                        if (r != null) {
                            switch (r.intValue()) {
                                case 1 -> success.incrementAndGet();
                                case -1 -> duplicate.incrementAndGet();
                                case 0 -> stockOut.incrementAndGet();
                                default -> other.incrementAndGet();
                            }
                        }
                    } catch (Exception ex) {
                        errors.incrementAndGet();
                        System.err.println("req#" + idx + " error: " + ex.getMessage());
                    } finally {
                        done.countDown();
                    }
                });
            }

            if (!done.await(10, TimeUnit.MINUTES)) {
                System.err.println("压测超时未完成");
                pool.shutdownNow();
                System.exit(3);
            }
            pool.shutdown();
            long wallMillis = (System.nanoTime() - t0) / 1_000_000;

            String stockAfter = conn.sync().get(STOCK_KEY);
            long buyers = conn.sync().scard(USER_KEY);
            long oversell = Math.max(0, success.get() - stock); // 超卖定义：成功扣减数 > 库存数

            // 延迟分位数
            List<Long> sorted = new ArrayList<>();
            for (long l : latencies) {
                if (l > 0) sorted.add(l);
            }
            sorted.sort(Long::compare);
            long p50 = sorted.isEmpty() ? 0 : sorted.get((int) (sorted.size() * 0.50));
            long p95 = sorted.isEmpty() ? 0 : sorted.get((int) (sorted.size() * 0.95));
            long p99 = sorted.isEmpty() ? 0 : sorted.get(sorted.size() - 1);

            System.out.println("---- RESULT ----");
            System.out.println("concurrency_requests=" + threads);
            System.out.println("stock=" + stock);
            System.out.println("success=" + success.get());
            System.out.println("duplicate=" + duplicate.get());
            System.out.println("stockout=" + stockOut.get());
            System.out.println("other=" + other.get());
            System.out.println("errors=" + errors.get());
            System.out.println("oversell=" + oversell);
            System.out.println("stock_after=" + stockAfter);
            System.out.println("buyers=" + buyers);
            System.out.println("wall_ms=" + wallMillis);
            System.out.println("qps=" + (wallMillis > 0 ? threads * 1000L / wallMillis : 0));
            System.out.println("latency_p50_ms=" + String.format("%.3f", p50 / 1_000_000.0));
            System.out.println("latency_p95_ms=" + String.format("%.3f", p95 / 1_000_000.0));
            System.out.println("latency_p99_ms=" + String.format("%.3f", p99 / 1_000_000.0));

            // 清理压测 key
            conn.sync().del(STOCK_KEY, USER_KEY);

            boolean pass = oversell == 0 && errors.get() == 0
                    && "0".equals(stockAfter) && buyers == success.get();
            System.out.println(pass ? "VERDICT=PASS" : "VERDICT=FAIL");
            if (!pass) {
                System.exit(1);
            }
        } finally {
            client.shutdown();
        }
    }
}
