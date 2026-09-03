package com.zhixing.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.zhixing.dto.Result;
import com.zhixing.entity.Attraction;
import com.zhixing.mapper.ShopMapper;
import com.zhixing.service.IShopService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.security.Key;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.zhixing.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现?
 * </p>
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Attraction> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    /**
     * 根据id鏌ヨ鍟嗛摵鏁版嵁
     *
     * @param id
     * @return
     */
    @Override
    public Result queryById(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 1、从Redis涓煡璇㈠簵閾烘暟鎹紝骞跺垽鏂紦瀛樻槸鍚﹀懡涓?
        Result result = getShopFromCache(key);
        if (Objects.nonNull(result)) {
            // 缓存命中，直接返?
            return result;
        }
        try {
            // 2、缓存未命中，需要重建缓存，判断能否?
            String lockKey = LOCK_SHOP_KEY + id;
            boolean isLock = tryLock(lockKey);
            if (!isLock) {
                // 2.1 获取锁失败，已有线程在重建缓存，则休眠重?
                Thread.sleep(50);
                return queryById(id);
            }
            // 2.2 获取锁成功，判断缓存?
            result = getShopFromCache(key);
            if (Objects.nonNull(result)) {
                // 缓存命中，直接返?
                return result;
            }

            // 3、从数据库中?
            Attraction Attraction = this.getById(id);
            if (Objects.isNull(Attraction)) {
                // 数据库中不存?
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.SECONDS);
                return Result.fail("店铺不存在");
            }

            // 4、数?
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(Attraction),
                    CACHE_SHOP_TTL, TimeUnit.MINUTES);
            return Result.ok(Attraction);
        }catch (Exception e){
            throw new RuntimeException("发生异常");
        } finally {
            // 5、释放锁（释放锁finally涓紝闃叉姝婚攣锛?
            unlock(key);
        }
    }

    /**
     * 从缓存中获取店铺数据
     * @param key
     * @return
     */
    private Result getShopFromCache(String key) {
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 判断缓存
        if (StrUtil.isNotBlank(shopJson)) {
            // 缓存数据
            Attraction Attraction = JSONUtil.toBean(shopJson, Attraction.class);
            return Result.ok(Attraction);
        }
        // 判断缓存?isNotBlank鎶?null 鍜?空字符串 给排除了)
        if (Objects.nonNull(shopJson)) {
            // 当前数据?
            return Result.fail("店铺不存在");
        }
        // 缓存
        return null;
    }

    /**
     * 获取?
     *
     * @param key
     * @return
     */
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        // 鎷嗙瑕佸垽绌猴紝闃叉NPE
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放?
     *
     * @param key
     */
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }


}










