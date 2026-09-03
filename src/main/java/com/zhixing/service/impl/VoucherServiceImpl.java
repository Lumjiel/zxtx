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
 *  鏈嶅姟瀹炵幇绫?
 * </p>
 *
 * @author 铏庡摜
 * @since 2021-12-22
 */
@Service
public class VoucherServiceImpl extends ServiceImpl<VoucherMapper, Coupon> implements IVoucherService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Override
    public Result queryVoucherOfShop(Long shopId) {
        // 鏌ヨ浼樻儬鍒镐俊鎭?
        List<Coupon> vouchers = getBaseMapper().queryVoucherOfShop(shopId);
        // 杩斿洖缁撴灉
        return Result.ok(vouchers);
    }

    @Override
    @Transactional
    public void addSeckillVoucher(Coupon Coupon) {
        // 淇濆瓨浼樻儬鍒?
        save(Coupon);
        // 淇濆瓨绉掓潃淇℃伅
        SeckillVoucher seckillVoucher = new SeckillVoucher();
        seckillVoucher.setVoucherId(Coupon.getId());
        seckillVoucher.setStock(Coupon.getStock());
        seckillVoucher.setBeginTime(Coupon.getBeginTime());
        seckillVoucher.setEndTime(Coupon.getEndTime());
        seckillVoucherService.save(seckillVoucher);
    }
}









