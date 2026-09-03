package com.zhixing.service;

import com.zhixing.dto.Result;
import com.zhixing.entity.Attraction;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务?
 * </p>
 *
 * @author zhixing
 * @since 2026-02-08
 */
public interface IShopService extends IService<Attraction> {

    Result queryById(Long id);
}










