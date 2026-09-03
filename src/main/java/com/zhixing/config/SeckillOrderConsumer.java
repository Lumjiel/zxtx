package com.zhixing.config;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.zhixing.entity.CouponOrder;
import com.zhixing.entity.SeckillVoucher;
import com.zhixing.mapper.SeckillVoucherMapper;
import com.zhixing.mapper.VoucherOrderMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * 秒杀订单异步消费者
 *
 * <p>接收 MQ 中的秒杀下单消息，负责真正落库：
 * 先以乐观锁（stock &gt; 0 条件）扣减数据库库存，再写入订单记录。</p>
 *
 * @author zhixing
 * @since 2026-09-02
 */
@Slf4j
@Component
public class SeckillOrderConsumer {

    @Resource
    private SeckillVoucherMapper seckillVoucherMapper;

    @Resource
    private VoucherOrderMapper voucherOrderMapper;

    @RabbitListener(queues = "seckill.order.queue")
    @Transactional(rollbackFor = Exception.class)
    public void handleSeckillOrder(String message) {
        JSONObject msg = JSONUtil.parseObj(message);
        Long orderId = msg.getLong("orderId");
        Long userId = msg.getLong("userId");
        Long voucherId = msg.getLong("voucherId");

        // 1. 乐观锁扣减数据库库存：只有 stock > 0 才允许扣减，DB 层兜底保证不超卖
        int updated = seckillVoucherMapper.update(null,
                new LambdaUpdateWrapper<SeckillVoucher>()
                        .setSql("stock = stock - 1")
                        .eq(SeckillVoucher::getVoucherId, voucherId)
                        .gt(SeckillVoucher::getStock, 0));
        if (updated == 0) {
            log.warn("秒杀券{}数据库库存不足，订单{}无法创建", voucherId, orderId);
            return;
        }

        // 2. 创建订单（status=1 未支付）
        CouponOrder order = new CouponOrder();
        order.setId(orderId);
        order.setUserId(userId);
        order.setVoucherId(voucherId);
        order.setStatus(1);
        order.setCreateTime(LocalDateTime.now());
        voucherOrderMapper.insert(order);
        log.info("秒杀订单创建成功: orderId={}, userId={}, voucherId={}", orderId, userId, voucherId);
    }
}

