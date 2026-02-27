package com.zhixing.config;

import com.zhixing.controller.BlogController;
import com.zhixing.service.impl.BlogServiceImpl;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LikeCleanupConsumer {

    @Autowired
    private BlogServiceImpl blogService; // 假设存在操作点赞信息�?Repository

    @RabbitListener(queues = "TravelNote:liked.queue")
    public void handleCleanupMessage(Long contentId) {
        // 根据 contentId 执行数据库写入操作，比如记录被移除的点赞信息�?
        blogService.delete_Blog(contentId);
    }
}









