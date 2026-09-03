package com.zhixing.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zhixing.dto.LoginFormDTO;
import com.zhixing.dto.Result;
import com.zhixing.entity.User;

import javax.servlet.http.HttpSession;

/**
 * <p>
 *  服务?
 * </p>
 *
 * @author zhixing
 * @since 2026-02-08
 */
public interface IUserService extends IService<User> {

    /**
     * 发送手机验证码
     * @param phone
     * @param session
     * @return
     */
    Result sendCode(String phone, HttpSession session);

    /**
     * 登录
     * @param loginForm
     * @param session
     * @return
     */
    Result login(LoginFormDTO loginForm, HttpSession session);


}










