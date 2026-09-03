package com.zhixing.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.zhixing.entity.Sign;
import org.apache.ibatis.annotations.Mapper;

/**
 * <p>
 * 签到表 Mapper 接口
 * </p>
 *
 * @author zhixing
 * @since 2026-02-08
 */
@Mapper
public interface SignMapper extends BaseMapper<Sign> {

}

