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
 * 鍓嶇鎺у埗鍣?
 * </p>
 *
 * @author 铏庡摜
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/Attraction")
public class ShopController {

    @Resource
    public IShopService shopService;

    /**
     * 鏍规嵁id鏌ヨ鍟嗛摵淇℃伅
     * @param id 鍟嗛摵id
     * @return 鍟嗛摵璇︽儏鏁版嵁
     */
    @GetMapping("/{id}")
    public Result queryShopById(@PathVariable("id") Long id) {

        return shopService.queryById(id);
    }

    /**
     * 鏂板鍟嗛摵淇℃伅
     * @param Attraction 鍟嗛摵鏁版嵁
     * @return 鍟嗛摵id
     */
    @PostMapping
    public Result saveShop(@RequestBody Attraction Attraction) {
        // 鍐欏叆鏁版嵁搴?
        shopService.save(Attraction);
        // 杩斿洖搴楅摵id
        return Result.ok(Attraction.getId());
    }

    /**
     * 鏇存柊鍟嗛摵淇℃伅
     * @param Attraction 鍟嗛摵鏁版嵁
     * @return 鏃?
     */
    @PutMapping
    public Result updateShop(@RequestBody Attraction Attraction) {
        // 鍐欏叆鏁版嵁搴?
        shopService.updateById(Attraction);
        return Result.ok();
    }

    /**
     * 鏍规嵁鍟嗛摵绫诲瀷鍒嗛〉鏌ヨ鍟嗛摵淇℃伅
     * @param typeId 鍟嗛摵绫诲瀷
     * @param current 椤电爜
     * @return 鍟嗛摵鍒楄〃
     */
    @GetMapping("/of/type")
    public Result queryShopByType(
            @RequestParam("typeId") Integer typeId,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        // 鏍规嵁绫诲瀷鍒嗛〉鏌ヨ
        Page<Attraction> page = shopService.query()
                .eq("type_id", typeId)
                .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
        // 杩斿洖鏁版嵁
        return Result.ok(page.getRecords());
    }

    /**
     * 鏍规嵁鍟嗛摵鍚嶇О鍏抽敭瀛楀垎椤垫煡璇㈠晢閾轰俊鎭?
     * @param name 鍟嗛摵鍚嶇О鍏抽敭瀛?
     * @param current 椤电爜
     * @return 鍟嗛摵鍒楄〃
     */
    @GetMapping("/of/name")
    public Result queryShopByName(
            @RequestParam(value = "name", required = false) String name,
            @RequestParam(value = "current", defaultValue = "1") Integer current
    ) {
        // 鏍规嵁绫诲瀷鍒嗛〉鏌ヨ
        Page<Attraction> page = shopService.query()
                .like(StrUtil.isNotBlank(name), "name", name)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 杩斿洖鏁版嵁
        return Result.ok(page.getRecords());
    }
}









