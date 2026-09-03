package com.zhixing.consumer;

import com.rabbitmq.client.Channel;
import com.zhixing.config.SeckillOrderConsumer;
import com.zhixing.entity.CouponOrder;
import com.zhixing.mapper.SeckillVoucherMapper;
import com.zhixing.mapper.VoucherOrderMapper;
import com.zhixing.utils.RedisConstants;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.SetOperations;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * 秒杀订单消费者单元测试（纯 Mockito，不依赖外部服务）
 *
 * <p>覆盖消费者最容易被面试官追问的分支：</p>
 * <ol>
 *   <li>重复消息（幂等拦截）；</li>
 *   <li>DB 库存不足 → Redis 回补 + ack；</li>
 *   <li>唯一索引拦截重复购买（一人一单 DB 兜底）→ 回补 + ack；</li>
 *   <li>未知异常 → 回补 + nack 进死信。</li>
 * </ol>
 */
@ExtendWith(MockitoExtension.class)
class SeckillOrderConsumerTest {

    @Mock
    private SeckillVoucherMapper seckillVoucherMapper;
    @Mock
    private VoucherOrderMapper voucherOrderMapper;
    @Mock
    private StringRedisTemplate stringRedisTemplate;
    @Mock
    private ValueOperations<String, String> valueOps;
    @Mock
    private SetOperations<String, String> setOps;
    @Mock
    private Channel channel;

    private SeckillOrderConsumer consumer;

    private static final String MSG = "{\"orderId\":1,\"userId\":10,\"voucherId\":100}";

    @BeforeEach
    void setUp() {
        consumer = new SeckillOrderConsumer();
        // 字段是 @Resource 注入的，测试里用反射注入
        org.springframework.test.util.ReflectionTestUtils.setField(consumer, "seckillVoucherMapper", seckillVoucherMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(consumer, "voucherOrderMapper", voucherOrderMapper);
        org.springframework.test.util.ReflectionTestUtils.setField(consumer, "stringRedisTemplate", stringRedisTemplate);
        lenient().when(stringRedisTemplate.opsForValue()).thenReturn(valueOps);
        lenient().when(stringRedisTemplate.opsForSet()).thenReturn(setOps);
    }

    @Test
    void 重复消息_幂等拦截_只ack不落库() throws Exception {
        when(voucherOrderMapper.selectById(1L)).thenReturn(new CouponOrder());

        consumer.handleSeckillOrder(MSG, channel, 1L);

        verify(seckillVoucherMapper, never()).update(any(), any());
        verify(channel).basicAck(1L, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void 数据库库存不足_回补Redis并ack() throws Exception {
        when(voucherOrderMapper.selectById(1L)).thenReturn(null);
        when(seckillVoucherMapper.update(any(), any())).thenReturn(0);

        consumer.handleSeckillOrder(MSG, channel, 2L);

        // 回补 = Redis 库存 +1 且移除已购记录
        verify(valueOps).increment(RedisConstants.SECKILL_STOCK_KEY + 100);
        verify(setOps).remove(RedisConstants.SECKILL_USER_KEY + 100, "10");
        verify(voucherOrderMapper, never()).insert(any(CouponOrder.class));
        verify(channel).basicAck(2L, false);
    }

    @Test
    void 唯一索引拦截重复购买_回补Redis并ack() throws Exception {
        when(voucherOrderMapper.selectById(1L)).thenReturn(null);
        when(seckillVoucherMapper.update(any(), any())).thenReturn(1);
        when(voucherOrderMapper.insert(any(CouponOrder.class))).thenThrow(new DuplicateKeyException("uk_user_voucher"));

        consumer.handleSeckillOrder(MSG, channel, 3L);

        verify(valueOps).increment(RedisConstants.SECKILL_STOCK_KEY + 100);
        verify(setOps).remove(RedisConstants.SECKILL_USER_KEY + 100, "10");
        verify(channel).basicAck(3L, false);
        verify(channel, never()).basicNack(anyLong(), anyBoolean(), anyBoolean());
    }

    @Test
    void 未知异常_回补Redis并nack进死信() throws Exception {
        when(voucherOrderMapper.selectById(1L)).thenReturn(null);
        when(seckillVoucherMapper.update(any(), any())).thenReturn(1);
        // 扣库存成功后插入订单阶段崩溃：Redis 已预扣，必须回补
        when(voucherOrderMapper.insert(any(CouponOrder.class))).thenThrow(new RuntimeException("db down"));

        consumer.handleSeckillOrder(MSG, channel, 4L);

        verify(valueOps).increment(RedisConstants.SECKILL_STOCK_KEY + 100);
        verify(channel).basicNack(4L, false, false);
        verify(channel, never()).basicAck(anyLong(), anyBoolean());
    }

    @Test
    void 扣库存前就异常_不回补_Redis未预扣() throws Exception {
        when(voucherOrderMapper.selectById(1L)).thenReturn(null);
        // update 本身抛异常：Redis 未预扣成功，不应回补（回补会凭空加库存）
        when(seckillVoucherMapper.update(any(), any())).thenThrow(new RuntimeException("db down"));

        consumer.handleSeckillOrder(MSG, channel, 6L);

        verify(valueOps, never()).increment(anyString());
        verify(channel).basicNack(6L, false, false);
    }

    @Test
    void 正常链路_扣库存插订单并ack() throws Exception {
        when(voucherOrderMapper.selectById(1L)).thenReturn(null);
        when(seckillVoucherMapper.update(any(), any())).thenReturn(1);
        when(voucherOrderMapper.insert(any(CouponOrder.class))).thenReturn(1);

        consumer.handleSeckillOrder(MSG, channel, 5L);

        ArgumentCaptor<CouponOrder> captor = ArgumentCaptor.forClass(CouponOrder.class);
        verify(voucherOrderMapper).insert(captor.capture());
        assertEquals(1L, captor.getValue().getId());
        assertEquals(10L, captor.getValue().getUserId());
        assertEquals(100L, captor.getValue().getVoucherId());
        verify(channel).basicAck(5L, false);
    }
}
