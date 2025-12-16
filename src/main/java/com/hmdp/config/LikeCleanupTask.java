package com.hmdp.config;

import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Set;

@Component
public class LikeCleanupTask {
    @Autowired
    private RedisTemplate<String, Object> redisTemplate;
    @Autowired
    private RabbitTemplate rabbitTemplate;

    // 这里设置为每天凌晨 2 点执行，可根据需求调整 cron 表达式
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupLeastLiked() {
        // 假设点赞信息存储在名为 "blog:liked:" 的 ZSet 中
        String key = "blog:liked:";
        // 获取点赞数最少的（score 最小的）一条记录
        Set<Object> leastLiked = redisTemplate.opsForZSet().range(key, 0, 0);
        if (leastLiked != null && !leastLiked.isEmpty()) {
            Object contentId = leastLiked.iterator().next();
            // 从 Redis 中移除该点赞数最少的记录
            redisTemplate.opsForZSet().remove(key, contentId);
            // 发送消息到 RabbitMQ，异步写入数据库
            rabbitTemplate.convertAndSend("blog:liked.queue", contentId);
        }
    }
}
