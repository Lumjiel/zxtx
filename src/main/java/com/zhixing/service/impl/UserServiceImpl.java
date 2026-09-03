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
 * 鏈嶅姟瀹炵幇绫?
 * </p>
 *
 * @author 铏庡摜
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
     * 鍙戦�佹墜鏈洪獙璇佺爜
     * @param phone
     * @param session
     * @return
     */
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //鏍￠獙鎵嬫満鍙?
        if(RegexUtils.isPhoneInvalid(phone)){
            //濡傛灉涓嶇鍚?杩斿洖閿欒淇℃伅
            return  Result.fail("鎵嬫満鍙锋牸寮忛敊璇?!!");
        }
        //绗﹀悎,鐢熸垚楠岃瘉鐮?
        String code= RandomUtil.randomNumbers(6);
        //淇濆瓨楠岃瘉鐮佸埌redis
        stringRedisTemplate.opsForValue().set("login:code"+phone,code,2, TimeUnit.MINUTES);
        // 鍙戦�侀獙璇佺爜
        log.debug("楠岃瘉鐮?{}" ,code);

        return Result.ok();
    }

    /**
     * 鐧诲綍
     * @param loginForm
     * @param session
     * @return
     */
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {
        //鏍￠獙鎵嬫満鍙?
        String phone = loginForm.getPhone();
        if(RegexUtils.isPhoneInvalid(phone)){
            //濡傛灉涓嶇鍚?杩斿洖閿欒淇℃伅
            return  Result.fail("鎵嬫満鍙锋牸寮忛敊璇?!!");
        }
        //鏍￠獙楠岃瘉鐮?浠巖edis
        String code1 = stringRedisTemplate.opsForValue().get("login:code"+phone);
        String code = loginForm.getCode();
        if (code1==null||!code1.toString().equals(code)){
            //涓嶄竴鑷?鎶涘嚭寮傚父
            return Result.fail("楠岃瘉鐮侀敊璇?");
        }
        // 涓�鑷?鏍规嵁鎵嬫満鍙锋煡璇㈢敤鎴?鐢变簬鏄痬ybatis plus,鍙互浣跨敤query()绠�鍖栦唬鐮?
        User user = query().eq("phone", phone).one();

        // 鍒ゆ柇鐢ㄦ埛鏄惁瀛樺湪
        if (user == null) {
            // 涓嶅瓨鍦?鍒涘缓鏂扮敤鎴峰苟淇濆瓨
            user = createUserWithPhone(phone);
        }

        // 淇濆瓨 UserDTO 鍒?redis
        //闅忔満鐢熸垚token,浣滀负鐧诲綍浠ょ墝
        String token = UUID.randomUUID().toString(true);

        // 4銆佷繚瀛樼敤鎴蜂俊鎭埌Redis涓紝渚夸簬鍚庨潰閫昏緫鐨勫垽鏂紙姣斿鐧诲綍鍒ゆ柇銆侀殢鏃跺彇鐢ㄦ埛淇℃伅锛屽噺灏戝鏁版嵁搴撶殑鏌ヨ锛?
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        // 灏嗗璞′腑瀛楁鍏ㄩ儴杞垚string绫诲瀷锛孲tringRedisTemplate鍙兘瀛樺瓧绗︿覆绫诲瀷鐨勬暟鎹?
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                CopyOptions.create().setIgnoreNullValue(true).
                        setFieldValueEditor((fieldName, fieldValue) -> fieldValue.toString()));
        //瀛樺埌redis涓?
        String tokenKey= RedisConstants.LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey,userMap);
        //璁剧疆token鏈夋晥鏈?
        stringRedisTemplate.expire(tokenKey,30,TimeUnit.MINUTES);
        //杩斿洖token
        return Result.ok(token);
    }

    private User createUserWithPhone(String phone) {
        //鍒涘缓鐢ㄦ埛
        User user = new User();
        user.setPhone(phone);
        user.setNickName("user_"+RandomUtil.randomString(10));
        //淇濆瓨鐢ㄦ埛
        save(user);
        return user;
    }
}









