package com.zhixing.utils;

/**
 * @author zhixing
 */
public abstract class RegexPatterns {
    /**
     * 手机?
     */
    public static final String PHONE_REGEX = "^1([38][0-9]|4[579]|5[0-3,5-9]|6[6]|7[0135678]|9[89])\\d{8}$";
    /**
     * 閭姝ｅ垯
     */
    public static final String EMAIL_REGEX = "^[a-zA-Z0-9_-]+@[a-zA-Z0-9_-]+(\\.[a-zA-Z0-9_-]+)+$";
    /**
     * 密码正则?~32位的字母、数
     */
    public static final String PASSWORD_REGEX = "^\\w{4,32}$";
    /**
     * 验证? 6位数字或字母
     */
    public static final String VERIFY_CODE_REGEX = "^[a-zA-Z\\d]{6}$";

}










