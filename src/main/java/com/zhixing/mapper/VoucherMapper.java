package com.zhixing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixing.entity.Coupon;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author zhixing
 * @since 2026-02-08
 */
public interface VoucherMapper extends BaseMapper<Coupon> {

    List<Coupon> queryVoucherOfShop(@Param("shopId") Long shopId);
}










