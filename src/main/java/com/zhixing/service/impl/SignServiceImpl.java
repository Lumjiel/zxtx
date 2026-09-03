package com.zhixing.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhixing.dto.Result;
import com.zhixing.entity.Sign;
import com.zhixing.mapper.SignMapper;
import com.zhixing.service.IShopService;
import com.zhixing.service.ISignService;
import com.zhixing.utils.UserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDate;

/**
 * <p>
 * 签到表服务实现类
 * </p>
 *
 * @author zhixing
 * @since 2026-02-08
 */
@Service
public class SignServiceImpl extends ServiceImpl<SignMapper, Sign> implements ISignService {

    @Resource
    private IShopService shopService;

    @Override
    @Transactional
    public Result signIn(Long attractionId) {
        // 检查用户是否登录
        Long userId = UserHolder.getUser().getId();
        if (userId == null) {
            return Result.fail("用户未登录");
        }

        // 检查景点是否存在
        if (shopService.getById(attractionId) == null) {
            return Result.fail("景点不存在");
        }

        // 检查今天是否已经打卡过该景点
        LocalDate today = LocalDate.now();
        LambdaQueryWrapper<Sign> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Sign::getUserId, userId)
                .eq(Sign::getAttractionId, attractionId)
                .eq(Sign::getDate, today);

        if (baseMapper.selectCount(queryWrapper) > 0) {
            return Result.fail("今天已经打卡过该景点");
        }

        // 创建打卡记录
        Sign sign = new Sign();
        sign.setUserId(userId);
        sign.setAttractionId(attractionId);
        sign.setYear(today.getYear());
        sign.setMonth(today.getMonthValue());
        sign.setDate(today);
        sign.setIsBackup(false);

        // 保存打卡记录
        baseMapper.insert(sign);

        return Result.ok("打卡成功");
    }

    @Override
    @Transactional
    public Result cancelSign(Long attractionId) {
        // 检查用户是否登录
        Long userId = UserHolder.getUser().getId();
        if (userId == null) {
            return Result.fail("用户未登录");
        }

        // 检查景点是否存在
        if (shopService.getById(attractionId) == null) {
            return Result.fail("景点不存在");
        }

        // 查找今天的打卡记录
        LocalDate today = LocalDate.now();
        LambdaQueryWrapper<Sign> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Sign::getUserId, userId)
                .eq(Sign::getAttractionId, attractionId)
                .eq(Sign::getDate, today);

        Sign sign = baseMapper.selectOne(queryWrapper);
        if (sign == null) {
            return Result.fail("今天未打卡该景点");
        }

        // 删除打卡记录
        baseMapper.deleteById(sign.getId());

        return Result.ok("取消打卡成功");
    }

    @Override
    public Result checkSignStatus(Long attractionId) {
        // 检查用户是否登录
        Long userId = UserHolder.getUser().getId();
        if (userId == null) {
            return Result.fail("用户未登录");
        }

        // 检查景点是否存在
        if (shopService.getById(attractionId) == null) {
            return Result.fail("景点不存在");
        }

        // 检查今天是否已经打卡过该景点
        LocalDate today = LocalDate.now();
        LambdaQueryWrapper<Sign> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Sign::getUserId, userId)
                .eq(Sign::getAttractionId, attractionId)
                .eq(Sign::getDate, today);

        boolean hasSigned = baseMapper.selectCount(queryWrapper) > 0;
        return Result.ok(hasSigned);
    }

    @Override
    public Result getUserSigns(Integer current, Integer size) {
        // 检查用户是否登录
        Long userId = UserHolder.getUser().getId();
        if (userId == null) {
            return Result.fail("用户未登录");
        }

        // 分页查询用户的打卡记录
        Page<Sign> page = new Page<>(current, size);
        LambdaQueryWrapper<Sign> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Sign::getUserId, userId)
                .orderByDesc(Sign::getDate);

        Page<Sign> signPage = baseMapper.selectPage(page, queryWrapper);

        return Result.ok(signPage);
    }

    @Override
    public Result getAttractionSignCount(Long attractionId) {
        // 检查景点是否存在
        if (shopService.getById(attractionId) == null) {
            return Result.fail("景点不存在");
        }

        // 查询景点的打卡人数
        LambdaQueryWrapper<Sign> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Sign::getAttractionId, attractionId);

        int count = baseMapper.selectCount(queryWrapper);
        return Result.ok(count);
    }

}
