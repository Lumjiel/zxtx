package com.zhixing.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.conditions.query.QueryChainWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhixing.dto.LoginFormDTO;
import com.zhixing.dto.Result;
import com.zhixing.dto.UserDTO;
import com.zhixing.entity.User;
import com.zhixing.mapper.UserMapper;
import com.zhixing.service.IUserService;
import com.zhixing.utils.RedisConstants;
import com.zhixing.utils.RegexUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现�?
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    private final StringRedisTemplate stringRedisTemplate;

    public UserServiceImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    /**
     * 发送手机验证码
     * @param phone
     * @param session
     * @return
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //校验手机�?
        if(RegexUtils.isPhoneInvalid(phone)){
            //如果不符�?返回错误信息
            return  Result.fail("手机号格式错�?!!");
        }
        //符合,生成验证�?
        String code= RandomUtil.randomNumbers(6);
        //保存验证码到redis
        stringRedisTemplate.opsForValue().set("login:code"+phone,code,2, TimeUnit.MINUTES);
        // 发送验证码
        log.debug("验证�?{}" ,code);

        return Result.ok();
    }

    /**
     * 登录
     * @param loginForm
     * @param session
     * @return
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //校验手机�?
        String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)){
            //如果不符�?返回错误信息
            return  Result.fail("手机号格式错�?!!");
        }
        //校验验证�?从redis
        String code1 = stringRedisTemplate.opsForValue().get("login:code"+phone);
        String code = loginForm.getCode();
        if (code1==null||!code1.toString().equals(code)){
            //不一�?抛出异常
            return Result.fail("验证码错�?");
        }
        // 一�?根据手机号查询用�?由于是mybatis plus,可以使用query()简化代�?
        User user = query().eq("phone", phone).one();

        // 判断用户是否存在
        if (user == null) {
            // 不存�?创建新用户并保存
            user = createUserWithPhone(phone);
        }

        // 保存 UserDTO �?redis
        //随机生成token,作为登录令牌
        String token = UUID.randomUUID().toString(true);

        // 4、保存用户信息到Redis中，便于后面逻辑的判断（比如登录判断、随时取用户信息，减少对数据库的查询�?
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        // 将对象中字段全部转成string类型，StringRedisTemplate只能存字符串类型的数�?
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true).
                        setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        //存到redis�?
        String tokenKey= RedisConstants.LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey,userMap);
        //设置token有效�?
        stringRedisTemplate.expire(tokenKey,30,TimeUnit.MINUTES);
        //返回token
        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        //创建用户
        User user = new User();
        user.setPhone(phone);
        user.setNickName("user_"+RandomUtil.randomString(10));
        //保存用户
        save(user);
        return user;
    }
}









