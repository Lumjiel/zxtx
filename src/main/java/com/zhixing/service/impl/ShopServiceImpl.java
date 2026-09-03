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
 *  鏈嶅姟瀹炵幇绫?
 * </p>
 */
@Service
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Attraction> implements IShopService {

    @Resource
    private StringRedisTemplate stringRedisTemplate;
    /**
     * 鏍规嵁id鏌ヨ鍟嗛摵鏁版嵁
     *
     * @param id
     * @return
     */
    @Override
    public Result queryById(Long id) {
        String key = CACHE_SHOP_KEY + id;
        // 1銆佷粠Redis涓煡璇㈠簵閾烘暟鎹紝骞跺垽鏂紦瀛樻槸鍚﹀懡涓?
        Result result = getShopFromCache(key);
        if (Objects.nonNull(result)) {
            // 缂撳瓨鍛戒腑锛岀洿鎺ヨ繑鍥?
            return result;
        }
        try {
            // 2銆佺紦瀛樻湭鍛戒腑锛岄渶瑕侀噸寤虹紦瀛橈紝鍒ゆ柇鑳藉惁鑳藉鑾峰彇浜掓枼閿?
            String lockKey = LOCK_SHOP_KEY + id;
            boolean isLock = tryLock(lockKey);
            if (!isLock) {
                // 2.1 鑾峰彇閿佸け璐ワ紝宸叉湁绾跨▼鍦ㄩ噸寤虹紦瀛橈紝鍒欎紤鐪犻噸璇?
                Thread.sleep(50);
                return queryById(id);
            }
            // 2.2 鑾峰彇閿佹垚鍔燂紝鍒ゆ柇缂撳瓨鏄惁閲嶅缓锛岄槻姝㈠爢绉殑绾跨▼鍏ㄩ儴璇锋眰鏁版嵁搴擄紙鎵�浠ヨ鍙屾鏄緢鏈夊繀瑕佺殑锛?
            result = getShopFromCache(key);
            if (Objects.nonNull(result)) {
                // 缂撳瓨鍛戒腑锛岀洿鎺ヨ繑鍥?
                return result;
            }

            // 3銆佷粠鏁版嵁搴撲腑鏌ヨ搴楅摵鏁版嵁锛屽苟鍒ゆ柇鏁版嵁搴撴槸鍚﹀瓨鍦ㄥ簵閾烘暟鎹?
            Attraction Attraction = this.getById(id);
            if (Objects.isNull(Attraction)) {
                // 鏁版嵁搴撲腑涓嶅瓨鍦紝缂撳瓨绌哄璞★紙瑙ｅ喅缂撳瓨绌块�忥級锛岃繑鍥炲け璐ヤ俊鎭?
                stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.SECONDS);
                return Result.fail("店铺不存在");
            }

            // 4銆佹暟鎹簱涓瓨鍦紝閲嶅缓缂撳瓨锛屽搷搴旀暟鎹?
            stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(Attraction),
                    CACHE_SHOP_TTL, TimeUnit.MINUTES);
            return Result.ok(Attraction);
        }catch (Exception e){
            throw new RuntimeException("鍙戠敓寮傚父");
        } finally {
            // 5銆侀噴鏀鹃攣锛堥噴鏀鹃攣涓�瀹氳璁板緱鏀惧湪finally涓紝闃叉姝婚攣锛?
            unlock(key);
        }
    }

    /**
     * 浠庣紦瀛樹腑鑾峰彇搴楅摵鏁版嵁
     * @param key
     * @return
     */
    private Result getShopFromCache(String key) {
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        // 鍒ゆ柇缂撳瓨鏄惁鍛戒腑
        if (StrUtil.isNotBlank(shopJson)) {
            // 缂撳瓨鏁版嵁鏈夊�硷紝璇存槑缂撳瓨鍛戒腑浜嗭紝鐩存帴杩斿洖搴楅摵鏁版嵁
            Attraction Attraction = JSONUtil.toBean(shopJson, Attraction.class);
            return Result.ok(Attraction);
        }
        // 鍒ゆ柇缂撳瓨涓煡璇㈢殑鏁版嵁鏄惁鏄┖瀛楃涓?isNotBlank鎶?null 鍜?绌哄瓧绗︿覆 缁欐帓闄や簡)
        if (Objects.nonNull(shopJson)) {
            // 褰撳墠鏁版嵁鏄┖瀛楃涓诧紝璇存槑缂撳瓨涔熷懡涓簡锛堣鏁版嵁鏄箣鍓嶇紦瀛樼殑绌哄璞★級锛岀洿鎺ヨ繑鍥炲け璐ヤ俊鎭?
            return Result.fail("店铺不存在");
        }
        // 缂撳瓨鏈懡涓紙缂撳瓨鏁版嵁鏃㈡病鏈夊�硷紝鍙堜笉鏄┖瀛楃涓诧級
        return null;
    }

    /**
     * 鑾峰彇閿?
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
     * 閲婃斁閿?
     *
     * @param key
     */
    private void unlock(String key) {
        stringRedisTemplate.delete(key);
    }


}









