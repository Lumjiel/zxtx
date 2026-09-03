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

public class RefreshTokenInterceptor implements HandlerInterceptor {
    private StringRedisTemplate stringRedisTemplate;


    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate = stringRedisTemplate;
    }


    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //鑾峰彇璇锋眰澶翠腑鐨則oken
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            return true;
        }
        //鍩轰簬token鑾峰彇redis涓殑鐢ㄦ埛
        String key=RedisConstants.LOGIN_USER_KEY+token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash()
                .entries(key);
        //鍒ゆ柇鐢ㄦ埛鏄惁瀛樺湪
        if (userMap.isEmpty()) {
            return true;
        }
        //灏嗘煡璇㈠埌鐨凥ash鏁版嵁杞负UserDto瀵硅薄
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);

        //淇濆瓨鐢ㄦ埛淇℃伅鍒癟hreadLocal
        UserHolder.saveUser(userDTO);

        //鍒锋柊token鏈夋晥鏈?
        stringRedisTemplate.expire(key,RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);

        //鏀捐
        return true;
    }


    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //绉婚櫎鐢ㄦ埛
        UserHolder.removeUser();
    }
}









