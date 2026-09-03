package com.zhixing.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.zhixing.dto.Result;
import com.zhixing.dto.UserDTO;
import com.zhixing.entity.TravelNote;
import com.zhixing.mapper.BlogMapper;
import com.zhixing.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhixing.service.IUserService;
import com.zhixing.utils.UserHolder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import static com.zhixing.utils.RedisConstants.BLOG_LIKED_KEY;

/**
 * <p>
 *  鏈嶅姟瀹炵幇绫?
 * </p>
 *
 * @author 铏庡摜
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, TravelNote> implements IBlogService {

    private final StringRedisTemplate stringRedisTemplate;
    private final IUserService userService;
    @Autowired
    private BlogMapper blogMapper;

    public BlogServiceImpl(StringRedisTemplate stringRedisTemplate, UserServiceImpl userService) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.userService = userService;
    }

    @Override
    public Result likeBlog(Long id) {
        // 1.鑾峰彇鐧诲綍鐢ㄦ埛
        Long userId = UserHolder.getUser().getId();
        // 2.鍒ゆ柇褰撳墠鐧诲綍鐢ㄦ埛鏄惁宸茬粡鐐硅禐
        String key = BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if (score == null) {
            // 3.濡傛灉鏈偣璧烇紝鍙互鐐硅禐
            // 3.1.鏁版嵁搴撶偣璧炴暟 + 1
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            // 3.2.淇濆瓨鐢ㄦ埛鍒癛edis鐨剆et闆嗗悎  zadd key value score
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        } else {
            // 4.濡傛灉宸茬偣璧烇紝鍙栨秷鐐硅禐
            // 4.1.鏁版嵁搴撶偣璧炴暟 -1
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            // 4.2.鎶婄敤鎴蜂粠Redis鐨剆et闆嗗悎绉婚櫎
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        String key = BLOG_LIKED_KEY + id;
        // 1.鏌ヨtop5鐨勭偣璧炵敤鎴?zrange key 0 4
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        // 2.瑙ｆ瀽鍑哄叾涓殑鐢ㄦ埛id
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr = StrUtil.join(",", ids);
        // 3.鏍规嵁鐢ㄦ埛id鏌ヨ鐢ㄦ埛 WHERE id IN ( 5 , 1 ) ORDER BY FIELD(id, 5, 1)
        List<UserDTO> userDTOS = userService.query()
                .in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        // 4.杩斿洖
        return Result.ok(userDTOS);
    }

    /**
     * 鍒犻櫎鐐硅禐鏁版渶灏戠殑鏂囩珷(閫昏緫鍒犻櫎)
     * @param contentId
     */
    @Override
    public void delete_Blog(Long contentId) {
        blogMapper.delete(contentId);
    }


    private void isBlogLiked(TravelNote TravelNote) {
        // 1.鑾峰彇鐧诲綍鐢ㄦ埛
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            // 鐢ㄦ埛鏈櫥褰曪紝鏃犻渶鏌ヨ鏄惁鐐硅禐
            return;
        }
        Long userId = user.getId();
        // 2.鍒ゆ柇褰撳墠鐧诲綍鐢ㄦ埛鏄惁宸茬粡鐐硅禐
        String key = "TravelNote:liked:" + TravelNote.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        TravelNote.setIsLike(score != null);
    }

}









