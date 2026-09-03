package com.zhixing.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.connection.CorrelationData;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.core.RabbitTemplate.ConfirmCallback;
import org.springframework.amqp.rabbit.core.RabbitTemplate.ReturnsCallback;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * RabbitMQ 可靠性配置
 *
 * <p>消息从生产到消费的三段可靠性，各自的责任边界：</p>
 * <ol>
 *   <li><b>生产端</b>：publisher-confirm（broker 是否收到）+ publisher-return
 *       （路由是否成功）。confirm 失败只打日志告警——秒杀消息丢失意味着用户扣了库存
 *       却没有订单，必须人工介入，所以这里选择「可观测」而不是「自动重发」；</li>
 *   <li><b>broker 端</b>：队列 durable + 消息落盘；</li>
 *   <li><b>消费端</b>：手动 ack（application.yaml acknowledge-mode=manual），
 *       处理成功才确认；失败消息 nack 进死信队列，不无限重投。</li>
 * </ol>
 *
 * @author zhixing
 * @since 2026-09-02
 */
@Slf4j
@Configuration
public class RabbitMQConfig {

    public static final String SECKILL_ORDER_QUEUE = "seckill.order.queue";
    public static final String SECKILL_ORDER_DLQ = "seckill.order.dlx.queue";
    public static final String BLOG_LIKED_QUEUE = "TravelNote:liked.queue";

    @Resource
    private RabbitTemplate rabbitTemplate;

    /**
     * 秒杀订单队列：durable + 绑定死信交换机（默认交换机按 routing-key 直投死信队列）。
     * 消费端 nack(requeue=false) 的消息会进入 seckill.order.dlx.queue，可人工排查补偿。
     */
    @Bean
    public Queue seckillOrderQueue() {
        Map<String, Object> args = new LinkedHashMap<>();
        args.put("x-dead-letter-exchange", "");
        args.put("x-dead-letter-routing-key", SECKILL_ORDER_DLQ);
        return QueueBuilder.durable(SECKILL_ORDER_QUEUE).withArguments(args).build();
    }

    /** 死信队列：存放消费失败、无法自动恢复的秒杀订单消息 */
    @Bean
    public Queue seckillOrderDlxQueue() {
        return QueueBuilder.durable(SECKILL_ORDER_DLQ).build();
    }

    /** 点赞对账队列：durable */
    @Bean
    public Queue blogLikedQueue() {
        return QueueBuilder.durable(BLOG_LIKED_QUEUE).build();
    }

    /**
     * 注册生产端确认回调。ConfirmCallback 管「消息是否到达 broker」，
     * ReturnsCallback 管「到达了但路由不到队列」（如 routing key 配错）。
     */
    @PostConstruct
    public void initReliabilityCallbacks() {
        ConfirmCallback confirmCallback = (CorrelationData correlationData, boolean ack, String cause) -> {
            if (!ack) {
                String id = correlationData == null ? "unknown" : correlationData.getId();
                log.error("[MQ确认] 消息未到达 broker，correlationId={}，原因: {}", id, cause);
            }
        };
        rabbitTemplate.setConfirmCallback(confirmCallback);

        ReturnsCallback returnsCallback = (returned) -> {
            Message message = returned.getMessage();
            log.error("[MQ退回] 消息路由失败，exchange={} routingKey={} body={}",
                    returned.getExchange(), returned.getRoutingKey(),
                    new String(message.getBody()));
        };
        rabbitTemplate.setReturnsCallback(returnsCallback);
    }
}
