package com.zhixing.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.zhixing.dto.LoginFormDTO;
import com.zhixing.dto.Result;
import com.zhixing.entity.User;

import javax.servlet.http.HttpSession;

/**
 * <p>
 *  鏈嶅姟绫?
 * </p>
 *
 * @author 铏庡摜
 * @since 2021-12-22
 */
public interface IUserService extends IService<User> {

    /**
     * 鍙戦�佹墜鏈洪獙璇佺爜
     * @param phone
     * @param session
     * @return
     */
    Result sendCode(String phone, HttpSession session);

    /**
     * 鐧诲綍
     * @param loginForm
     * @param session
     * @return
     */
    Result login(LoginFormDTO loginForm, HttpSession session);


}









