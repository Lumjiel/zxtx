package com.zhixing.controller;


import com.zhixing.dto.Result;
import com.zhixing.entity.Coupon;
import com.zhixing.service.IVoucherService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 *  鍓嶇鎺у埗鍣?
 * </p>
 *
 * @author 铏庡摜
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/Coupon")
public class VoucherController {

    @Resource
    private IVoucherService voucherService;

    /**
     * 鏂板鏅�氬埜
     * @param Coupon 浼樻儬鍒镐俊鎭?
     * @return 浼樻儬鍒竔d
     */
    @PostMapping
    public Result addVoucher(@RequestBody Coupon Coupon) {
        voucherService.save(Coupon);
        return Result.ok(Coupon.getId());
    }

    /**
     * 鏂板绉掓潃鍒?
     * @param Coupon 浼樻儬鍒镐俊鎭紝鍖呭惈绉掓潃淇℃伅
     * @return 浼樻儬鍒竔d
     */
    @PostMapping("seckill")
    public Result addSeckillVoucher(@RequestBody Coupon Coupon) {
        voucherService.addSeckillVoucher(Coupon);
        return Result.ok(Coupon.getId());
    }

    /**
     * 鏌ヨ搴楅摵鐨勪紭鎯犲埜鍒楄〃
     * @param shopId 搴楅摵id
     * @return 浼樻儬鍒稿垪琛?
     */
    @GetMapping("/list/{shopId}")
    public Result queryVoucherOfShop(@PathVariable("shopId") Long shopId) {
       return voucherService.queryVoucherOfShop(shopId);
    }
}









