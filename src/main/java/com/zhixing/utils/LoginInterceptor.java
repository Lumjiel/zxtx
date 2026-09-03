package com.zhixing.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.zhixing.dto.UserDTO;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class LoginInterceptor implements HandlerInterceptor {



    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //鍒ゆ柇鏄惁鎷︽埅,(ThreadLocal涓槸鍚︽湁鐢ㄦ埛)
        if(UserHolder.getUser()==null){
            //娌℃湁,闇�瑕佹嫤鎴?璁剧疆鐘舵�佺爜
            response.setStatus(401);
            //鎷︽埅
            return false;
        }
        //鏈夌敤鎴?鏀捐
        return true;
    }

}









