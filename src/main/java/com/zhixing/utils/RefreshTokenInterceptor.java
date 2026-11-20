package com.zhixing.utils;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.zhixing.dto.UserDTO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Map;
import java.util.concurrent.TimeUnit;

public class RefreshTokenInterceptor implements HandlerInterceptor {
    private static final Logger log = LoggerFactory.getLogger(RefreshTokenInterceptor.class);

    private StringRedisTemplate stringRedisTemplate;


    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate = stringRedisTemplate;
    }


    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //获取请求头中的token
        String token = request.getHeader("authorization");
        if (StrUtil.isBlank(token)) {
            return true;
        }
        // 基于 token 获取 redis 中的用户
        String key=RedisConstants.LOGIN_USER_KEY+token;
        Map<Object, Object> userMap;
        try {
            userMap = stringRedisTemplate.opsForHash()
                    .entries(key);
        } catch (Exception e) {
            // Redis 故障降级：身份无法确认，按「未装配」放行——本层拦 /**，
            // 若不降级会让白名单接口一起 500；放行后保护路径由 LoginInterceptor 自然 401，
            // 降级语义与白名单语义同构
            log.warn("Redis 不可用，登录态刷新层降级放行（key={}）: {}", key, e.getMessage());
            return true;
        }
        //判断用户
        if (userMap.isEmpty()) {
            return true;
        }
        //将查询到的Hash鏁版嵁杞负UserDto对象
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);

        //保存用户信息到ThreadLocal
        UserHolder.saveUser(userDTO);

        //刷新token鏈夋晥鏈?
        try {
            stringRedisTemplate.expire(key,RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
        } catch (Exception e) {
            // 会话续期失败不撤销身份：entries 已确认 token 有效，仅 TTL 刷新这一副作用
            // 静默失败（等下次请求再刷），把用户随机登出比短暂不续期更伤
            log.warn("Redis 不可用，token TTL 刷新失败（不影响本次已确认的登录态）: {}", e.getMessage());
        }

        //鏀捐
        return true;
    }


    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //移除用户
        UserHolder.removeUser();
    }
}
