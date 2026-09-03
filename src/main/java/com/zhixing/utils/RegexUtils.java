package com.zhixing.utils;

import cn.hutool.core.util.StrUtil;

/**
 * @author 铏庡摜
 */
public class RegexUtils {
    /**
     * 鏄惁鏄棤鏁堟墜鏈烘牸寮?
     * @param phone 瑕佹牎楠岀殑鎵嬫満鍙?
     * @return true:绗﹀悎锛宖alse锛氫笉绗﹀悎
     */
    public static boolean isPhoneInvalid(String phone){
        return mismatch(phone, RegexPatterns.PHONE_REGEX);
    }
    /**
     * 鏄惁鏄棤鏁堥偖绠辨牸寮?
     * @param email 瑕佹牎楠岀殑閭
     * @return true:绗﹀悎锛宖alse锛氫笉绗﹀悎
     */
    public static boolean isEmailInvalid(String email){
        return mismatch(email, RegexPatterns.EMAIL_REGEX);
    }

    /**
     * 鏄惁鏄棤鏁堥獙璇佺爜鏍煎紡
     * @param code 瑕佹牎楠岀殑楠岃瘉鐮?
     * @return true:绗﹀悎锛宖alse锛氫笉绗﹀悎
     */
    public static boolean isCodeInvalid(String code){
        return mismatch(code, RegexPatterns.VERIFY_CODE_REGEX);
    }

    // 鏍￠獙鏄惁涓嶇鍚堟鍒欐牸寮?
    private static boolean mismatch(String str, String regex){
        if (StrUtil.isBlank(str)) {
            return true;
        }
        return !str.matches(regex);
    }
}









