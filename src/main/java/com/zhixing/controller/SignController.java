package com.zhixing.controller;

import com.zhixing.dto.Result;
import com.zhixing.service.ISignService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 * 签到相关接口
 * </p>
 *
 * @author zhixing
 * @since 2026-02-08
 */
@RestController
@RequestMapping("/sign")
public class SignController {

    @Resource
    private ISignService signService;

    /**
     * 景点打卡
     * @param attractionId 景点ID
     * @return 打卡结果
     */
    @PostMapping("/in/{attractionId}")
    public Result signIn(@PathVariable("attractionId") Long attractionId) {
        return signService.signIn(attractionId);
    }

    /**
     * 取消打卡
     * @param attractionId 景点ID
     * @return 取消结果
     */
    @PostMapping("/cancel/{attractionId}")
    public Result cancelSign(@PathVariable("attractionId") Long attractionId) {
        return signService.cancelSign(attractionId);
    }

    /**
     * 检查打卡状态
     * @param attractionId 景点ID
     * @return 打卡状态
     */
    @GetMapping("/status/{attractionId}")
    public Result checkSignStatus(@PathVariable("attractionId") Long attractionId) {
        return signService.checkSignStatus(attractionId);
    }

    /**
     * 获取用户打卡记录
     * @param current 当前页码
     * @param size 每页大小
     * @return 打卡记录列表
     */
    @GetMapping("/user/list")
    public Result getUserSigns(@RequestParam(value = "current", defaultValue = "1") Integer current,
                               @RequestParam(value = "size", defaultValue = "10") Integer size) {
        return signService.getUserSigns(current, size);
    }

    /**
     * 获取景点打卡人数
     * @param attractionId 景点ID
     * @return 打卡人数
     */
    @GetMapping("/count/{attractionId}")
    public Result getAttractionSignCount(@PathVariable("attractionId") Long attractionId) {
        return signService.getAttractionSignCount(attractionId);
    }

}

