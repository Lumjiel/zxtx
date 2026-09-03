package com.zhixing.config;

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

    // 杩欓噷璁剧疆涓烘瘡澶╁噷鏅?2 鐐规墽琛岋紝鍙牴鎹渶姹傝皟鏁?cron 琛ㄨ揪寮?
    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupLeastLiked() {
        // 鍋囪鐐硅禐淇℃伅瀛樺偍鍦ㄥ悕涓?"TravelNote:liked:" 鐨?ZSet 涓?
        String key = "TravelNote:liked:";
        // 鑾峰彇鐐硅禐鏁版渶灏戠殑锛坰core 鏈�灏忕殑锛変竴鏉¤褰?
        Set<Object> leastLiked = redisTemplate.opsForZSet().range(key, 0, 0);
        if (leastLiked != null && !leastLiked.isEmpty()) {
            Object contentId = leastLiked.iterator().next();
            // 浠?Redis 涓Щ闄よ鐐硅禐鏁版渶灏戠殑璁板綍
            redisTemplate.opsForZSet().remove(key, contentId);
            // 鍙戦�佹秷鎭埌 RabbitMQ锛屽紓姝ュ啓鍏ユ暟鎹簱
            rabbitTemplate.convertAndSend("TravelNote:liked.queue", contentId);
        }
    }
}









