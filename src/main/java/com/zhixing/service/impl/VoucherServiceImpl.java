package com.zhixing.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhixing.dto.Result;
import com.zhixing.entity.Coupon;
import com.zhixing.mapper.VoucherMapper;
import com.zhixing.entity.SeckillVoucher;
import com.zhixing.service.ISeckillVoucherService;
import com.zhixing.service.IVoucherService;
import com.zhixing.utils.RedisConstants;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

/**
 * 优惠券服务实现
 *
 * <p>新增秒杀券时，除了写 MySQL 两张表（tb_voucher + tb_seckill_voucher），
 * 还必须把库存预热到 Redis——秒杀链路的 Lua 脚本完全依赖 Redis 中的库存计数，
 * 不预热的话脚本 get 到 nil，所有请求都会被判「库存不足」。</p>
 *
 * @author zhixing
 * @since 2026-02-08
 */
@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Coupon> implements IVoucherService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryVoucherOfShop(Long shopId) {
        // 查询优惠券信息
        List<Coupon> vouchers = getBaseMapper().queryVoucherOfShop(shopId);
        // 返回结果
        return Result.ok(vouchers);
    }

    @Override
    @Transactional
    public void addSeckillVoucher(Coupon Coupon) {
        // 保存优惠券
        save(Coupon);
        // 保存秒杀信息
        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(Coupon.getId());
        seckillVoucher.setStock(Coupon.getStock());
        seckillVoucher.setBeginTime(Coupon.getBeginTime());
        seckillVoucher.setEndTime(Coupon.getEndTime());
        seckillVoucherService.save(seckillVoucher);

        // 库存预热：秒杀 Lua 脚本从 Redis 读库存，必须与 DB 同步初始化
        // 注意：这里在事务内写 Redis，若事务回滚 Redis 会残留脏库存，重新创建同 id 券时会被覆盖
        stringRedisTemplate.opsForValue().set(
                RedisConstants.SECKILL_STOCK_KEY + Coupon.getId(),
                Coupon.getStock().toString());
    }
}










