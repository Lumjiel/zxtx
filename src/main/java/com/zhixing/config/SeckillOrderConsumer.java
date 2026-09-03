package com.zhixing.config;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.rabbitmq.client.Channel;
import com.zhixing.entity.CouponOrder;
import com.zhixing.entity.SeckillVoucher;
import com.zhixing.mapper.SeckillVoucherMapper;
import com.zhixing.mapper.VoucherOrderMapper;
import com.zhixing.utils.RedisConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.support.AmqpHeaders;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.stereotype.Component;

import javax.annotation.Resource;
import java.io.IOException;
import java.time.LocalDateTime;

import static com.zhixing.config.RabbitMQConfig.SECKILL_ORDER_QUEUE;

/**
 * 秒杀订单异步消费者
 *
 * <p>接收 MQ 中的秒杀下单消息，负责真正落库：先以乐观锁（stock &gt; 0 条件）扣减
 * 数据库库存，再写入订单记录。手动 ack + 三层异常处理：</p>
 * <ol>
 *   <li><b>幂等拦截</b>：按订单主键查重（快速路径），配合 tb_voucher_order 的
 *       (user_id, voucher_id) 唯一索引（兜底路径，捕获 DuplicateKeyException），
 *       消息重复投递也不会产生重复订单；</li>
 *   <li><b>Redis 回补</b>：Redis 预扣成功但 DB 扣减失败（库存不足/订单已存在）时，
 *       把 Redis 库存加回、已购记录移除，用户可以重新发起秒杀，避免「库存凭空蒸发」；</li>
 *   <li><b>死信兜底</b>：无法自动恢复的异常 nack(requeue=false) 进死信队列，
 *       不无限重投阻塞消费，可人工排查补偿。</li>
 * </ol>
 *
 * <p>已知权衡：ack 在本地事务提交前发出，极端时序下（ack 后进程崩溃、事务未提交）
 * 可能丢一条订单，严格方案是事务同步 ack 或本地消息表——面试可展开。</p>
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

    @Resource
    private org.springframework.data.redis.core.StringRedisTemplate stringRedisTemplate;

    @RabbitListener(queues = SECKILL_ORDER_QUEUE)
    public void handleSeckillOrder(String message, Channel channel,
                                   @Header(AmqpHeaders.DELIVERY_TAG) long deliveryTag) {
        boolean stockDeducted = false;
        try {
            JSONObject msg = JSONUtil.parseObj(message);
            Long orderId = msg.getLong("orderId");
            Long userId = msg.getLong("userId");
            Long voucherId = msg.getLong("voucherId");

            // 1. 幂等快速路径：同一订单号的消息重复投递时直接确认
            if (voucherOrderMapper.selectById(orderId) != null) {
                log.info("[秒杀消费] 订单{}已存在（重复消息），幂等拦截", orderId);
                ack(channel, deliveryTag);
                return;
            }

            // 2. 乐观锁扣减数据库库存：只有 stock > 0 才允许扣减，DB 层兜底保证不超卖
            int updated = seckillVoucherMapper.update(null,
                    new LambdaUpdateWrapper<SeckillVoucher>()
                            .setSql("stock = stock - 1")
                            .eq(SeckillVoucher::getVoucherId, voucherId)
                            .gt(SeckillVoucher::getStock, 0));
            stockDeducted = updated > 0;

            if (updated == 0) {
                // DB 库存不足但 Redis 预扣了：回补 Redis，让用户可以重试
                log.warn("[秒杀消费] 秒杀券{}数据库库存不足，订单{}未创建，回补 Redis 库存", voucherId, orderId);
                compensateRedis(voucherId, userId);
                ack(channel, deliveryTag);
                return;
            }

            // 3. 创建订单（status=1 未支付）；一人一单的 DB 层兜底由唯一索引完成
            try {
                CouponOrder order = new CouponOrder();
                order.setId(orderId);
                order.setUserId(userId);
                order.setVoucherId(voucherId);
                order.setStatus(1);
                order.setCreateTime(LocalDateTime.now());
                voucherOrderMapper.insert(order);
                log.info("[秒杀消费] 秒杀订单创建成功: orderId={}, userId={}, voucherId={}", orderId, userId, voucherId);
            } catch (DuplicateKeyException e) {
                // 唯一索引拦截：该用户已买过该券（Redis 判重与 DB 之间的极端并发窗口）
                log.warn("[秒杀消费] 用户{}重复购买秒杀券{}（唯一索引拦截），回补 Redis", userId, voucherId);
                compensateRedis(voucherId, userId);
            }
            ack(channel, deliveryTag);
        } catch (Exception e) {
            log.error("[秒杀消费] 消息处理失败，转入死信队列: {}", e.getMessage(), e);
            if (stockDeducted) {
                // DB 已扣但订单没建成：回补 Redis 库存，差额由人工按死信消息处理
                try {
                    JSONObject msg = JSONUtil.parseObj(message);
                    compensateRedis(msg.getLong("voucherId"), msg.getLong("userId"));
                } catch (Exception ignore) {
                    // 死信场景下的回补失败只记日志，人工介入
                }
            }
            nack(channel, deliveryTag);
        }
    }

    /**
     * Redis 回补：库存 +1，已购记录移除该用户
     */
    private void compensateRedis(Long voucherId, Long userId) {
        stringRedisTemplate.opsForValue().increment(RedisConstants.SECKILL_STOCK_KEY + voucherId);
        stringRedisTemplate.opsForSet().remove(RedisConstants.SECKILL_USER_KEY + voucherId, userId.toString());
        log.info("[秒杀消费] Redis 回补完成: voucherId={}, userId={}", voucherId, userId);
    }

    private void ack(Channel channel, long deliveryTag) {
        try {
            channel.basicAck(deliveryTag, false);
        } catch (IOException e) {
            log.error("[秒杀消费] ack 失败: {}", e.getMessage());
        }
    }

    private void nack(Channel channel, long deliveryTag) {
        try {
            // requeue=false：消息进入死信队列，避免无限重投
            channel.basicNack(deliveryTag, false, false);
        } catch (IOException e) {
            log.error("[秒杀消费] nack 失败: {}", e.getMessage());
        }
    }
}
