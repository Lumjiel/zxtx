package com.zhixing.ai;

import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicLong;

/**
 * AI 线路规划的调用结果统计（进程级内存计数）。
 *
 * <p>用于回答「解析失败率怎么控」：失败率 = (aiError + parseFailed) / total，
 * 快照通过 {@link #snapshot()} 读取并打进日志。单机部署下的轻量实现，
 * 多实例场景应换 Micrometer 指标（当前项目定位单机，够用且透明）。</p>
 *
 * @author zhixing
 * @since 2026-11-15
 */
@Component
public class RoutePlanStats {

    private final AtomicLong total = new AtomicLong();
    private final AtomicLong aiSuccess = new AtomicLong();
    private final AtomicLong aiError = new AtomicLong();
    private final AtomicLong parseFailed = new AtomicLong();

    public void incTotal() {
        total.incrementAndGet();
    }

    public void incAiSuccess() {
        aiSuccess.incrementAndGet();
    }

    public void incAiError() {
        aiError.incrementAndGet();
    }

    public void incParseFailed() {
        parseFailed.incrementAndGet();
    }

    public long getTotal() {
        return total.get();
    }

    public long getAiSuccess() {
        return aiSuccess.get();
    }

    public long getAiError() {
        return aiError.get();
    }

    public long getParseFailed() {
        return parseFailed.get();
    }

    /** 失败率（百分比，两位小数）= (aiError + parseFailed) / total * 100 */
    public double failureRate() {
        long t = total.get();
        if (t == 0) {
            return 0.0;
        }
        long failed = aiError.get() + parseFailed.get();
        return Math.round(failed * 10000.0 / t) / 100.0;
    }

    public String snapshot() {
        return String.format("total=%d, aiSuccess=%d, aiError=%d, parseFailed=%d, failureRate=%.2f%%",
                getTotal(), getAiSuccess(), getAiError(), getParseFailed(), failureRate());
    }
}
