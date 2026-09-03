package com.zhixing.utils;

/**
 * 缓存 TTL 随机抖动工具
 *
 * <p>缓存雪崩的常见成因之一是「同一批 key 在同一时刻被批量写入、TTL 相同」，
 * 到期瞬间全部失效，回源流量同时打到数据库。给 TTL 叠加 ±10% 的随机抖动，
 * 把过期时间点打散即可低成本规避。</p>
 *
 * @author zhixing
 * @since 2026-09-02
 */
public final class CacheTtlJitter {

    private CacheTtlJitter() {
    }

    /**
     * 在基准 TTL 上叠加 ±10% 的随机抖动
     *
     * @param base 基准 TTL（单位由调用方决定）
     * @return 抖动后的 TTL
     */
    public static long jitter(long base) {
        long spread = Math.max(1L, base / 10);
        return base - spread + (long) (Math.random() * spread * 2);
    }
}
