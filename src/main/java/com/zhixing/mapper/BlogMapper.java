package com.zhixing.mapper;

import com.zhixing.entity.TravelNote;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author zhixing
 * @since 2026-02-08
 */
public interface BlogMapper extends BaseMapper<TravelNote> {


    void delete(Long contentId);
}










