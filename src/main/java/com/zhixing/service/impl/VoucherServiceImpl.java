package com.zhixing.service.impl;

import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhixing.dto.Result;
import com.zhixing.entity.Coupon;
import com.zhixing.mapper.VoucherMapper;
import com.zhixing.entity.SeckillVoucher;
import com.zhixing.service.ISeckillVoucherService;
import com.zhixing.service.IVoucherService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 *  服务实现�?
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Coupon> implements IVoucherService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Override
    public Result queryVoucherOfShop(Long shopId) {
        // 查询优惠券信�?
        List<Coupon> vouchers = getBaseMapper().queryVoucherOfShop(shopId);
        // 返回结果
        return Result.ok(vouchers);
    }

    @Override
    @Transactional
    public void addSeckillVoucher(Coupon Coupon) {
        // 保存优惠�?
        save(Coupon);
        // 保存秒杀信息
        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(Coupon.getId());
        seckillVoucher.setStock(Coupon.getStock());
        seckillVoucher.setBeginTime(Coupon.getBeginTime());
        seckillVoucher.setEndTime(Coupon.getEndTime());
        seckillVoucherService.save(seckillVoucher);
    }
}









