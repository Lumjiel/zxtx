package com.zhixing.service.impl;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhixing.dto.Result;
import com.zhixing.entity.Attraction;
import com.zhixing.mapper.ShopMapper;
import com.zhixing.service.IShopService;
import com.zhixing.utils.CacheTtlJitter;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

import static com.zhixing.utils.RedisConstants.*;

/**
 * 景点详情查询：缓存三态 + 互斥锁防击穿
 *
 * <p>查询链路（防穿透与防击穿都在这条链上）：</p>
 * <ol>
 *   <li>先查缓存：命中真实数据直接返回；命中空值（""）说明是不存在的数据，直接返回失败，
 *       挡住对不存在数据的反复查询（缓存穿透）；</li>
 *   <li>缓存未命中：抢互斥锁，抢不到的线程休眠 50ms 后自旋重试，
 *       保证热点 key 过期瞬间只有一个线程回源数据库（缓存击穿）；</li>
 *   <li>拿到锁后「双重检查」缓存——防止在等锁期间缓存已被其他线程重建，堆积线程全部打到数据库；</li>
 *   <li>只有抢到锁的线程才进入 try/finally 释放锁，未抢到锁的递归重试路径绝不触碰锁。</li>
 * </ol>
 *
 * @author zhixing
 * @since 2026-02-08
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Attraction> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Override
    public Result queryById(Long id) {
        String key = CACHE_SHOP_KEY + id;

        // 1. 先查缓存（三态：真实数据 / 空值标记 / 未命中）
        Result result = getShopFromCache(key);
        if (Objects.nonNull(result)) {
            return result;
        }

        // 2. 缓存未命中：抢互斥锁，谁抢到谁重建
        String lockKey = LOCK_SHOP_KEY + id;
        boolean isLock = tryLock(lockKey);
        if (!isLock) {
            // 2.1 抢锁失败：说明有线程正在重建缓存，休眠后自旋重试
            try {
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
            return queryById(id);
        }

        try {
            // 2.2 抢锁成功：双重检查缓存，防止等锁期间缓存已被其他线程重建
            result = getShopFromCache(key);
            if (Objects.nonNull(result)) {
                return result;
            }

            // 3. 查数据库
            Attraction attraction = this.getById(id);
            if (Objects.isNull(attraction)) {
                // 数据库中不存在：写入空值标记并短 TTL 过期，防缓存穿透
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.SECONDS);
                return Result.fail("景点不存在");
            }

            // 4. 数据库中存在：写回缓存（TTL 随机抖动，防止大量 key 同时过期引发缓存雪崩）
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(attraction),
                    CacheTtlJitter.jitter(CACHE_SHOP_TTL), TimeUnit.MINUTES);
            return Result.ok(attraction);
        } finally {
            // 5. 释放锁：必须释放「锁 key」而不是缓存 key，且放在 finally 中防止死锁
            unlock(lockKey);
        }
    }

    /**
     * 从缓存中获取景点数据（三态）
     *
     * @param key 缓存 key
     * @return 命中返回结果（含失败结果）；未命中返回 null，由调用方走重建流程
     */
    private Result getShopFromCache(String key) {
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(shopJson)) {
            // 命中真实数据
            Attraction attraction = JSONUtil.toBean(shopJson, Attraction.class);
            return Result.ok(attraction);
        }
        // isNotBlank 把 null 和空字符串都排除了，能走到这里 shopJson 只可能是空串
        // 命中空值标记：说明数据库中不存在该数据，直接返回失败，挡住穿透请求
        if (Objects.nonNull(shopJson)) {
            return Result.fail("景点不存在");
        }
        // 未命中
        return null;
    }

    /**
     * 尝试获取互斥锁：SETNX + EXPIRE 一条命令原子完成，防止「加锁后进程崩溃，锁永不释放」
     *
     * @param key 锁 key
     */
    private boolean tryLock(String key) {
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", LOCK_SHOP_TTL, TimeUnit.SECONDS);
        // 拆箱要判空，防止 NPE
        return BooleanUtil.isTrue(flag);
    }

    /**
     * 释放互斥锁
     *
     * @param key 锁 key
     */
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }
}
