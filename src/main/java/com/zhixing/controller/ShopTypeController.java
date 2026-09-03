package com.zhixing.controller;


import com.zhixing.dto.Result;
import com.zhixing.entity.AttractionType;
import com.zhixing.service.IShopTypeService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 鍓嶇鎺у埗鍣?
 * </p>
 *
 * @author 铏庡摜
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/Attraction-type")
public class ShopTypeController {
    @Resource
    private IShopTypeService typeService;

    @GetMapping("list")
    public Result queryTypeList() {
        List<AttractionType> typeList = typeService
                .query().orderByAsc("sort").list();
        return Result.ok(typeList);
    }
}









