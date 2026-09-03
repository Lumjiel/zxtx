package com.zhixing.service;

import com.zhixing.dto.Result;
import com.zhixing.entity.Coupon;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  鏈嶅姟绫?
 * </p>
 *
 * @author 铏庡摜
 * @since 2021-12-22
 */
public interface IVoucherService extends IService<Coupon> {

    Result queryVoucherOfShop(Long shopId);

    void addSeckillVoucher(Coupon Coupon);
}









