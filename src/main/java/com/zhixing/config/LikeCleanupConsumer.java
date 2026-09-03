package com.zhixing.config;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.zhixing.service.IBlogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;

/**
 * 点赞巡检消息消费者
 *
 * <p>接收定时任务发出的对账消息，把 Redis 中的点赞数落库。
 * 对账是「绝对值覆盖」语义，天然幂等：消息重复投递也不会造成数据错误。</p>
 *
 * @author zhixing
 * @since 2026-09-02
 */
@Slf4j
@Component
public class LikeCleanupConsumer {

    @Resource
    private IBlogService blogService;

    @RabbitListener(queues = "TravelNote:liked.queue")
    public void handleReconcileMessage(String message) {
        JSONObject msg = JSONUtil.parseObj(message);
        Long blogId = msg.getLong("blogId");
        Long likedCount = msg.getLong("likedCount");
        try {
            blogService.syncLikedCount(blogId, likedCount);
            log.info("[点赞对账] blogId={} liked={} 落库完成", blogId, likedCount);
        } catch (Exception e) {
            // 对账任务允许失败：下一轮凌晨巡检会发现仍不一致并重新投递
            log.error("[点赞对账] blogId={} 落库失败: {}", blogId, e.getMessage());
        }
    }
}
