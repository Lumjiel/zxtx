package com.zhixing.controller;


import com.zhixing.dto.Result;
import com.zhixing.entity.Coupon;
import com.zhixing.service.IVoucherService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 * 优惠券前端控制器
 * </p>
 *
 * @author zhixing
 * @since 2026-02-08
 */
@RestController
@RequestMapping("/Coupon")
public class VoucherController {

    @Resource
    private IVoucherService voucherService;

    /**
     * 新增普通券
     * @param Coupon 优惠券信?
     * @return 优惠券id
     */
    @PostMapping
    public Result addVoucher(@RequestBody Coupon Coupon) {
        voucherService.save(Coupon);
        return Result.ok(Coupon.getId());
    }

    /**
     * 新增秒杀券
     * @param Coupon 优惠券信息，包含秒杀信息
     * @return 优惠券id
     */
    @PostMapping("seckill")
    public Result addSeckillVoucher(@RequestBody Coupon Coupon) {
        voucherService.addSeckillVoucher(Coupon);
        return Result.ok(Coupon.getId());
    }

    /**
     * 查询景点的优惠券列表
     * @param shopId 店铺id
     * @return 优惠券列?
     */
    @GetMapping("/list/{shopId}")
    public Result queryVoucherOfShop(@PathVariable("shopId") Long shopId) {
       return voucherService.queryVoucherOfShop(shopId);
    }
}










