package com.zhixing.service;

import com.zhixing.dto.Result;
import com.zhixing.entity.Attraction;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务�?
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IShopService extends IService<Attraction> {

    Result queryById(Long id);
}









