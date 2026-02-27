package com.zhixing.controller;


import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhixing.dto.Result;
import com.zhixing.entity.Attraction;
import com.zhixing.service.IShopService;
import com.zhixing.utils.SystemConstants;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 * 前端控制�?
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/Attraction")
public class ShopController {

    @Resource
    public IShopService shopService;

    /**
     * 根据id查询商铺信息
     * @param id 商铺id
     * @return 商铺详情数据
     */
    @GetMapping("/{id}")
    public Result queryShopById(@PathVariable("id") Long id) {

        return shopService.queryById(id);
    }

    /**
     * 新增商铺信息
     * @param Attraction 商铺数据
     * @return 商铺id
     */
    @PostMapping
    public Result saveShop(@RequestBody Attraction Attraction) {
        // 写入数据�?
        shopService.save(Attraction);
        // 返回店铺id
        return Result.ok(Attraction.getId());
    }

    /**
     * 更新商铺信息
     * @param Attraction 商铺数据
     * @return �?
     */
    @PutMapping
    public Result updateShop(@RequestBody Attraction Attraction) {
        // 写入数据�?
        shopService.updateById(Attraction);
        return Result.ok();
    }

    /**
     * 根据商铺类型分页查询商铺信息
     * @param typeId 商铺类型
     * @param current 页码
     * @return 商铺列表
     */
    @GetMapping("/of/type")
    public Result queryShopByType(
            @RequestParam("typeId") Integer typeId,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        // 根据类型分页查询
        Page<Attraction> page = shopService.query()
                .eq("type_id", typeId)
                .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
        // 返回数据
        return Result.ok(page.getRecords());
    }

    /**
     * 根据商铺名称关键字分页查询商铺信�?
     * @param name 商铺名称关键�?
     * @param current 页码
     * @return 商铺列表
     */
    @GetMapping("/of/name")
    public Result queryShopByName(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        // 根据类型分页查询
        Page<Attraction> page = shopService.query()
                .like(StrUtil.isNotBlank(name), "name", name)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 返回数据
        return Result.ok(page.getRecords());
    }
}









