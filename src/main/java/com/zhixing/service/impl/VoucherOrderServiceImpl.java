package com.zhixing.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.toolkit.IdWorker;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhixing.dto.Result;
import com.zhixing.dto.UserDTO;
import com.zhixing.entity.CouponOrder;
import com.zhixing.entity.SeckillVoucher;
import com.zhixing.mapper.VoucherOrderMapper;
import com.zhixing.service.ISeckillVoucherService;
import com.zhixing.service.IVoucherOrderService;
import com.zhixing.utils.RedisConstants;
import com.zhixing.utils.UserHolder;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

/**
 * 优惠券订单服务：秒杀下单核心实现
 *
 * <p>秒杀流程（四道防线，从入口到落库层层兜底）：</p>
 * <ol>
 *   <li>秒杀时间校验：开始前/结束后直接拒绝；</li>
 *   <li>Redis Lua 脚本原子扣减：库存校验 + 一人一单校验 + 扣库存一次性原子完成，防止并发超卖与重复下单；</li>
 *   <li>RabbitMQ 异步下单：Lua 通过后仅生成订单号并投递消息，订单落库异步进行，削峰填谷；</li>
 *   <li>数据库乐观锁兜底：UPDATE ... SET stock = stock - 1 WHERE stock &gt; 0，DB 层保证库存不为负。</li>
 * </ol>
 *
 * @author zhixing
 * @since 2026-09-02
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, CouponOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RabbitTemplate rabbitTemplate;

    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;

    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("lua/seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    /**
     * 秒杀下单
     *
     * @param voucherId 秒杀券 id
     * @return 成功返回订单号，失败返回错误信息
     */
    @Override
    public Result seckillVoucher(Long voucherId) {
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            return Result.fail("用户未登录");
        }
        Long userId = user.getId();

        // 1. 校验秒杀时间
        SeckillVoucher voucher = seckillVoucherService.getById(voucherId);
        if (voucher == null) {
            return Result.fail("秒杀券不存在");
        }
        LocalDateTime now = LocalDateTime.now();
        if (now.isBefore(voucher.getBeginTime())) {
            return Result.fail("秒杀尚未开始");
        }
        if (now.isAfter(voucher.getEndTime())) {
            return Result.fail("秒杀已结束");
        }

        // 2. 执行 Lua 脚本：原子完成「库存校验 + 一人一单校验 + 库存扣减 + 用户记录」
        //    返回 1=成功，0=库存不足，-1=重复下单
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Arrays.asList(RedisConstants.SECKILL_STOCK_KEY + voucherId,
                        RedisConstants.SECKILL_USER_KEY + voucherId),
                userId.toString());

        if (result == null) {
            return Result.fail("秒杀系统繁忙，请重试");
        }
        if (result == 0L) {
            return Result.fail("库存不足");
        }
        if (result == -1L) {
            return Result.fail("不能重复下单");
        }

        // 3. Lua 通过：生成订单号，投递 MQ 消息，异步创建订单
        long orderId = IdWorker.getId();
        Map<String, Object> msg = new HashMap<>(4);
        msg.put("orderId", orderId);
        msg.put("userId", userId);
        msg.put("voucherId", voucherId);
        rabbitTemplate.convertAndSend(RedisConstants.SECKILL_ORDER_QUEUE, JSONUtil.toJsonStr(msg));

        // 4. 返回订单号
        return Result.ok(orderId);
    }
}
