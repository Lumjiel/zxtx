package com.zhixing.controller;


import com.zhixing.dto.LoginFormDTO;
import com.zhixing.dto.Result;
import com.zhixing.dto.UserDTO;
import com.zhixing.entity.UserInfo;
import com.zhixing.service.IUserInfoService;
import com.zhixing.service.IUserService;
import com.zhixing.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.HttpSession;

/**
 * <p>
 * 鍓嶇鎺у埗鍣?
 * </p>
 *
 * @author 铏庡摜
 * @since 2021-12-22
 */
@Slf4j
@RestController
@RequestMapping("/user")
public class UserController {

    @Resource
    private IUserService userService;

    @Resource
    private IUserInfoService userInfoService;

    /**
     * 鍙戦�佹墜鏈洪獙璇佺爜
     */
    @PostMapping("code")
    public Result sendCode(@RequestParam("phone") String phone, HttpSession session) {
        //  鍙戦�佺煭淇￠獙璇佺爜骞朵繚瀛橀獙璇佺爜
        return userService.sendCode(phone,session);
    }

    /**
     * 鐧诲綍鍔熻兘
     * @param loginForm 鐧诲綍鍙傛暟锛屽寘鍚墜鏈哄彿銆侀獙璇佺爜锛涙垨鑰呮墜鏈哄彿銆佸瘑鐮?
     */
    @PostMapping("/login")
    public Result login(@RequestBody LoginFormDTO loginForm, HttpSession session){
        //  瀹炵幇鐧诲綍鍔熻兘
        return userService.login(loginForm,session);
    }

    /**
     * 鐧诲嚭鍔熻兘
     * @return 鏃?
     */
    @PostMapping("/logout")
    public Result logout(){
        // TODO 瀹炵幇鐧诲嚭鍔熻兘
        return Result.fail("功能未完成");
    }

    @GetMapping("/me")
    public Result me(){
        // 鑾峰彇褰撳墠鐧诲綍鐨勭敤鎴峰苟杩斿洖
        UserDTO user = UserHolder.getUser();
        log.info("褰撳墠鐢ㄦ埛淇℃伅:{}",user);
        return Result.ok(user);
    }

    @GetMapping("/info/{id}")
    public Result info(@PathVariable("id") Long userId){
        // 鏌ヨ璇︽儏
        UserInfo info = userInfoService.getById(userId);
        if (info == null) {
            // 娌℃湁璇︽儏锛屽簲璇ユ槸绗竴娆℃煡鐪嬭鎯?
            return Result.ok();
        }
        info.setCreateTime(null);
        info.setUpdateTime(null);
        // 杩斿洖
        return Result.ok(info);
    }


}









