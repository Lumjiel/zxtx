package com.zhixing.service;

import com.zhixing.dto.Result;
import com.zhixing.entity.Coupon;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务�?
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IVoucherService extends IService<Coupon> {

    Result queryVoucherOfShop(Long shopId);

    void addSeckillVoucher(Coupon Coupon);
}









