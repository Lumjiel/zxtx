package com.zhixing.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zhixing.dto.Result;
import com.zhixing.entity.CouponOrder;

/**
 * 优惠券订单服务接口
 *
 * @author zhixing
 * @since 2026-09-02
 */
public interface IVoucherOrderService extends IService<CouponOrder> {

    /**
     * 秒杀下单：Lua 原子扣减 + MQ 异步落库
     *
     * @param voucherId 秒杀券 id
     * @return 成功返回订单号
     */
    Result seckillVoucher(Long voucherId);
}

