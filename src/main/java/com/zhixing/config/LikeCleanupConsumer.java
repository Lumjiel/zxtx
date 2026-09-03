package com.zhixing.config;

import com.zhixing.controller.BlogController;
import com.zhixing.service.impl.BlogServiceImpl;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class LikeCleanupConsumer {

    @Autowired
    private BlogServiceImpl blogService; // 鍋囪瀛樺湪鎿嶄綔鐐硅禐淇℃伅鐨?Repository

    @RabbitListener(queues = "TravelNote:liked.queue")
    public void handleCleanupMessage(Long contentId) {
        // 鏍规嵁 contentId 鎵ц鏁版嵁搴撳啓鍏ユ搷浣滐紝姣斿璁板綍琚Щ闄ょ殑鐐硅禐淇℃伅绛?
        blogService.delete_Blog(contentId);
    }
}









