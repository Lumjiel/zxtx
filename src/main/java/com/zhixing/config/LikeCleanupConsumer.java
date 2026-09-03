package com.zhixing.config;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.rabbitmq.client.Channel;
import com.zhixing.service.IBlogService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;

import static com.zhixing.config.RabbitMQConfig.BLOG_LIKED_QUEUE;

/**
 * 点赞巡检消息消费者
 *
 * <p>接收定时任务发出的对账消息，把 Redis 中的点赞数落库。
 * 对账是「绝对值覆盖」语义，天然幂等：消息重复投递也不会造成数据错误。</p>
 *
 * <p>失败处理：对账任务允许失败——失败仅记日志并确认消息，
 * 下一轮凌晨巡检会发现计数仍不一致并重新发起对账，无需重投。</p>
 *
 * @author zhixing
 * @since 2026-09-02
 */
@Slf4j
@Component
public class LikeCleanupConsumer {

    @Resource
    private IBlogService blogService;

    @RabbitListener(queues = BLOG_LIKED_QUEUE)
    public void handleReconcileMessage(String message, Channel channel,
                                       @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        try {
            JSONObject msg = JSONUtil.parseObj(message);
            Long blogId = msg.getLong("blogId");
            Long likedCount = msg.getLong("likedCount");
            blogService.syncLikedCount(blogId, likedCount);
            log.info("[点赞对账] blogId={} liked={} 落库完成", blogId, likedCount);
        } catch (Exception e) {
            // 对账允许失败：下一轮凌晨巡检会发现仍不一致并重新投递
            log.error("[点赞对账] 消息落库失败: {}", e.getMessage());
        } finally {
            try {
                channel.basicAck(deliveryTag, false);
            } catch (IOException e) {
                log.error("[点赞对账] ack 失败: {}", e.getMessage());
            }
        }
    }
}
