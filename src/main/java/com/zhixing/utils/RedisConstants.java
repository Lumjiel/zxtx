package com.zhixing.utils;

public class RedisConstants {
    public static final String LOGIN_CODE_KEY = "login:code:";
    public static final Long LOGIN_CODE_TTL = 2L;
    public static final String LOGIN_USER_KEY = "login:token:";
    public static final Long LOGIN_USER_TTL = 36000L;

    public static final Long CACHE_NULL_TTL = 2L;

    public static final Long CACHE_SHOP_TTL = 30L;
    public static final String CACHE_SHOP_KEY = "cache:Attraction:";

    public static final String LOCK_SHOP_KEY = "lock:Attraction:";
    public static final Long LOCK_SHOP_TTL = 10L;

    public static final String SECKILL_STOCK_KEY = "seckill:stock:";
    public static final String SECKILL_USER_KEY = "seckill:user:";
    public static final String SECKILL_ORDER_QUEUE = "seckill.order.queue";
    public static final String BLOG_LIKED_KEY = "TravelNote:liked:";

    public static final String CACHE_TRAVELNOTE_KEY = "cache:TravelNote:";
    public static final String CACHE_TRAVELNOTE_HOT_KEY = "cache:TravelNote:hot:";
    public static final Long CACHE_TRAVELNOTE_TTL = 30L;

    public static final String USER_SIGN_KEY = "sign:";
}










