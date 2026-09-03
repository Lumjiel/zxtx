package com.zhixing.config;

import cn.hutool.json.JSONUtil;
import com.zhixing.entity.TravelNote;
import com.zhixing.service.IBlogService;
import com.zhixing.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.Cursor;
import org.springframework.data.redis.core.ScanOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

/**
 * 点赞数据巡检定时任务（每天凌晨 2 点）
 *
 * <p>背景：点赞明细只存 Redis ZSet（TravelNote:liked:{blogId}），DB 只存计数。
 * 博客被逻辑删除后，它的 ZSet 成了永远无人读取的内存垃圾；同时 ZSet 计数
 * 与 DB 计数在极端并发下可能产生偏差。两者都需要一个兜底机制。</p>
 *
 * <p>职责：</p>
 * <ol>
 *   <li><b>清理</b>：已逻辑删除（或不存在）的攻略，其点赞 ZSet 直接删除，管控内存驻留；</li>
 *   <li><b>对账</b>：存量攻略的 Redis 点赞数（ZCARD）与 DB liked 字段不一致时，
 *       通过 MQ 异步落库修正，削峰且不阻塞巡检。</li>
 * </ol>
 *
 * <p>实现要点：用 SCAN 渐进遍历而不是 KEYS——KEYS 会阻塞 Redis 主线程，
 * 线上大库执行可能拖垮整个缓存（面试高频点）。</p>
 *
 * @author zhixing
 * @since 2026-09-02
 */
@Slf4j
@Component
public class LikeCleanupTask {

    private static final String BLOG_LIKED_KEY_PREFIX = RedisConstants.BLOG_LIKED_KEY;
    private static final String LIKED_QUEUE = RabbitMQConfig.BLOG_LIKED_QUEUE;
    /** 单次巡检最多处理的 key 数，防止凌晨任务长时间占用连接 */
    private static final long MAX_SCAN_KEYS = 1000;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RabbitTemplate rabbitTemplate;

    @Resource
    private IBlogService blogService;

    @Scheduled(cron = "0 0 2 * * ?")
    public void patrolLikedData() {
        long scanned = 0;
        long cleaned = 0;
        long reconciled = 0;

        ScanOptions options = ScanOptions.scanOptions()
                .match(BLOG_LIKED_KEY_PREFIX + "*")
                .count(200)
                .build();
        try (Cursor<String> cursor = stringRedisTemplate.scan(options)) {
            while (cursor.hasNext() && scanned < MAX_SCAN_KEYS) {
                String key = cursor.next();
                scanned++;
                // key 形如 TravelNote:liked:123，截取攻略 id
                String blogIdStr = key.substring(BLOG_LIKED_KEY_PREFIX.length());
                Long blogId;
                try {
                    blogId = Long.valueOf(blogIdStr);
                } catch (NumberFormatException e) {
                    continue;
                }

                TravelNote blog = blogService.getById(blogId);
                if (blog == null || Boolean.TRUE.equals(blog.getIsDeleted())) {
                    // 攻略已删除：点赞 ZSet 是无人读取的内存垃圾，直接清理
                    stringRedisTemplate.delete(key);
                    cleaned++;
                    continue;
                }

                // 存量攻略：对账 Redis 计数与 DB 计数，不一致走 MQ 异步落库
                Long redisCount = stringRedisTemplate.opsForZSet().zCard(key);
                if (redisCount == null) {
                    continue;
                }
                if (redisCount != (blog.getLiked() == null ? 0 : blog.getLiked())) {
                    Map<String, Object> msg = new HashMap<>(4);
                    msg.put("blogId", blogId);
                    msg.put("likedCount", redisCount);
                    rabbitTemplate.convertAndSend(LIKED_QUEUE, JSONUtil.toJsonStr(msg));
                    reconciled++;
                }
            }
        }
        log.info("[点赞巡检] 扫描 {} 个 ZSet，清理残留 {} 个，发起对账 {} 次", scanned, cleaned, reconciled);
    }
}
