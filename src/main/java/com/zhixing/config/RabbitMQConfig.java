package com.zhixing.config;

import org.springframework.amqp.core.Queue;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    @Bean
    public Queue blogLikedQueue() {
        return new Queue("TravelNote:liked.queue", true); // durable=true
    }

    @Bean
    public Queue seckillOrderQueue() {
        return new Queue("seckill.order.queue", true); // durable=true，秒杀订单异步落库
    }
}









