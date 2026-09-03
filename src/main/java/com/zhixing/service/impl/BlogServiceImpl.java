package com.zhixing.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhixing.dto.Result;
import com.zhixing.dto.UserDTO;
import com.zhixing.entity.TravelNote;
import com.zhixing.entity.User;
import com.zhixing.mapper.BlogMapper;
import com.zhixing.service.IBlogService;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.zhixing.service.IUserService;
import com.zhixing.utils.CacheTtlJitter;
import com.zhixing.utils.SystemConstants;
import com.zhixing.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.zhixing.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.zhixing.utils.RedisConstants.CACHE_NULL_TTL;
import static com.zhixing.utils.RedisConstants.CACHE_TRAVELNOTE_KEY;
import static com.zhixing.utils.RedisConstants.CACHE_TRAVELNOTE_HOT_KEY;
import static com.zhixing.utils.RedisConstants.CACHE_TRAVELNOTE_TTL;

/**
 * 攻略（TravelNote）服务实现
 *
 * <p>点赞数据模型：DB 只存计数（tb_blog.liked），Redis ZSet（TravelNote:liked:{blogId}）
 * 存点赞明细（member=userId, score=点赞时间戳），判重和「最早点赞 top5」都走 ZSet，
 * 避免每次点赞都查 DB。两者一致性由每天凌晨的对账任务兜底（见 LikeCleanupTask）。</p>
 *
 * @author zhixing
 * @since 2026-02-08
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, TravelNote> implements IBlogService {

    private final StringRedisTemplate stringRedisTemplate;
    private final IUserService userService;
    private final BlogMapper blogMapper;

    public BlogServiceImpl(StringRedisTemplate stringRedisTemplate, IUserService userService, BlogMapper blogMapper) {
        this.stringRedisTemplate = stringRedisTemplate;
        this.userService = userService;
        this.blogMapper = blogMapper;
    }

    @Override
    public Result queryBlogDetail(Long id) {
        String key = CACHE_TRAVELNOTE_KEY + id;
        // 1. 查缓存（三态）
        String blogJson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(blogJson)) {
            // 命中真实数据：作者信息与「是否已点赞」是随请求变化的个性化数据，不进缓存
            TravelNote blog = JSONUtil.toBean(blogJson, TravelNote.class);
            fillBlogAuthor(blog);
            fillBlogLikedFlag(blog);
            return Result.ok(blog);
        }
        // 命中空值标记：数据库中不存在该攻略，直接返回失败，挡住穿透请求
        if (blogJson != null) {
            return Result.fail("攻略不存在");
        }

        // 2. 未命中：查数据库
        TravelNote blog = this.getById(id);
        if (blog == null) {
            // 写入空值标记，短 TTL 过期，防缓存穿透
            stringRedisTemplate.opsForValue().set(key, "", CACHE_NULL_TTL, TimeUnit.SECONDS);
            return Result.fail("攻略不存在");
        }

        // 3. 写回缓存，TTL 随机抖动防雪崩
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(blog),
                CacheTtlJitter.jitter(CACHE_TRAVELNOTE_TTL), TimeUnit.MINUTES);
        fillBlogAuthor(blog);
        fillBlogLikedFlag(blog);
        return Result.ok(blog);
    }

    @Override
    public Result queryHotBlog(int current) {
        String key = CACHE_TRAVELNOTE_HOT_KEY + current;
        // 1. 查缓存（热榜分页是全站流量最高的接口）
        String hotJson = stringRedisTemplate.opsForValue().get(key);
        if (StrUtil.isNotBlank(hotJson)) {
            return Result.ok(JSONUtil.parseArray(hotJson).toList(TravelNote.class));
        }

        // 2. 未命中：按点赞数倒序分页查库
        Page<TravelNote> page = this.query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        List<TravelNote> records = page.getRecords();
        records.forEach(this::fillBlogAuthor);

        // 3. 写回缓存（TTL 随机抖动防雪崩）。点赞引起的排序变化接受最长约 30 分钟延迟，
        //    换取热榜接口基本不打数据库
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(records),
                CacheTtlJitter.jitter(CACHE_TRAVELNOTE_TTL), TimeUnit.MINUTES);
        return Result.ok(records);
    }

    /**
     * 填充攻略作者昵称与头像（按请求实时查，不进缓存，避免用户改名后缓存展示旧数据）
     */
    private void fillBlogAuthor(TravelNote blog) {
        User user = userService.getById(blog.getUserId());
        if (user != null) {
            blog.setName(user.getNickName());
            blog.setIcon(user.getIcon());
        }
    }

    /**
     * 填充当前登录用户的「是否已点赞」标记（未登录时静默跳过）
     */
    private void fillBlogLikedFlag(TravelNote blog) {
        isBlogLiked(blog);
    }

    @Override
    public Result likeBlog(Long id) {
        // 1.获取登录用户
        Long userId = UserHolder.getUser().getId();
        // 2.判断当前登录用户
        String key = BLOG_LIKED_KEY + id;
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        if (score == null) {
            // 3.如果
            // 3.1.数据库点赞数 + 1
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            // 3.2.保存用户到Redis的set集合  zadd key value score
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().add(key, userId.toString(), System.currentTimeMillis());
            }
        } else {
            // 4.如果已点赞，取消点赞
            // 4.1.数据库点赞数 -1
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            // 4.2.把用户从Redis的set集合移除
            if (isSuccess) {
                stringRedisTemplate.opsForZSet().remove(key, userId.toString());
            }
        }
        // 5.点赞数已变化，失效攻略详情缓存，下次查询回源拿最新计数
        //   （热榜分页的排序变化接受 TTL 内延迟，不做全量失效）
        stringRedisTemplate.delete(CACHE_TRAVELNOTE_KEY + id);
        return Result.ok();
    }

    @Override
    public Result queryBlogLikes(Long id) {
        String key = BLOG_LIKED_KEY + id;
        // 1. 查询 top5 点赞用户：zrange key 0 4
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        // 2.解析出其id
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        String idStr = StrUtil.join(",", ids);
        // 3. 根据用户id查询用户 WHERE id IN (5, 1) ORDER BY FIELD(id, 5, 1)
        List<UserDTO> userDTOS = userService.query()
                .in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list()
                .stream()
                .map(user -> BeanUtil.copyProperties(user, UserDTO.class))
                .collect(Collectors.toList());
        // 4.返回
        return Result.ok(userDTOS);
    }

    /**
     * 逻辑删除攻略
     *
     * @param blogId 攻略 id
     */
    @Override
    public void deleteBlog(Long blogId) {
        blogMapper.delete(blogId);
    }

    /**
     * 对账点赞数：定时任务发现 Redis 计数与 DB 不一致时异步调用，以 Redis 为准
     *
     * @param blogId     攻略 id
     * @param likedCount Redis 中的实际点赞数
     */
    @Override
    public void syncLikedCount(Long blogId, long likedCount) {
        update().set("liked", (int) likedCount).eq("id", blogId).update();
    }


    private void isBlogLiked(TravelNote TravelNote) {
        // 1.获取登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            // 用户
            return;
        }
        Long userId = user.getId();
        // 2.判断当前登录用户
        String key = BLOG_LIKED_KEY + TravelNote.getId();
        Double score = stringRedisTemplate.opsForZSet().score(key, userId.toString());
        TravelNote.setIsLike(score != null);
    }

}










