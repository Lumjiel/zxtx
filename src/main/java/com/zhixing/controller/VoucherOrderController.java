package com.zhixing.controller;

import com.zhixing.dto.Result;
import com.zhixing.service.IVoucherOrderService;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;

/**
 * 优惠券订单控制器
 *
 * @author zhixing
 * @since 2026-09-02
 */
@RestController
@RequestMapping("/Coupon-order")
public class VoucherOrderController {

    @Resource
    private IVoucherOrderService voucherOrderService;

    /**
     * 秒杀下单
     *
     * @param voucherId 秒杀券 id
     * @return 成功返回订单号
     */
    @PostMapping("seckill/{id}")
    public Result seckillVoucher(@PathVariable("id") Long voucherId) {
        return voucherOrderService.seckillVoucher(voucherId);
    }
}

