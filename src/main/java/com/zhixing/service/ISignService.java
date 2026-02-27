package com.zhixing.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zhixing.dto.Result;
import com.zhixing.entity.Sign;

/**
 * <p>
 * 签到表 服务类
 * </p>
 *
 * @author zhixing
 * @since 2026-02-08
 */
public interface ISignService extends IService<Sign> {

    /**
     * 景点打卡
     * @param attractionId 景点ID
     * @return 打卡结果
     */
    Result signIn(Long attractionId);

    /**
     * 取消打卡
     * @param attractionId 景点ID
     * @return 取消结果
     */
    Result cancelSign(Long attractionId);

    /**
     * 检查用户是否已打卡该景点
     * @param attractionId 景点ID
     * @return 检查结果
     */
    Result checkSignStatus(Long attractionId);

    /**
     * 获取用户打卡记录
     * @param current 当前页码
     * @param size 每页大小
     * @return 打卡记录列表
     */
    Result getUserSigns(Integer current, Integer size);

    /**
     * 获取景点打卡人数
     * @param attractionId 景点ID
     * @return 打卡人数
     */
    Result getAttractionSignCount(Long attractionId);

}
