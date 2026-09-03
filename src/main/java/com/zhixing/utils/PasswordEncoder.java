package com.zhixing.utils;


import cn.hutool.core.util.RandomUtil;
import org.springframework.util.DigestUtils;

import java.nio.charset.StandardCharsets;

public class PasswordEncoder {

    public static String encode(String password) {
        // 鐢熸垚鐩?
        String salt = RandomUtil.randomString(20);
        // 鍔犲瘑
        return encode(password,salt);
    }
    private static String encode(String password, String salt) {
        // 鍔犲瘑
        return salt + "@" + DigestUtils.md5DigestAsHex((password + salt).getBytes(StandardCharsets.UTF_8));
    }
    public static Boolean matches(String encodedPassword, String rawPassword) {
        if (encodedPassword == null || rawPassword == null) {
            return false;
        }
        if(!encodedPassword.contains("@")){
            throw new RuntimeException("瀵嗙爜鏍煎紡涓嶆纭紒");
        }
        String[] arr = encodedPassword.split("@");
        // 鑾峰彇鐩?
        String salt = arr[0];
        // 姣旇緝
        return encodedPassword.equals(encode(rawPassword, salt));
    }
}









