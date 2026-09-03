package com.zhixing.service;

import com.zhixing.dto.Result;
import com.zhixing.entity.TravelNote;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * 攻略（TravelNote）服务接口
 *
 * @author zhixing
 * @since 2026-02-08
 */
public interface IBlogService extends IService<TravelNote> {

    /**
     * 点赞 / 取消点赞
     *
     * @param id 攻略 id
     * @return 操作结果
     */
    Result likeBlog(Long id);

    /**
     * 查询点赞列表（最早点赞的 top5 用户）
     *
     * @param id 攻略 id
     * @return 点赞用户列表
     */
    Result queryBlogLikes(Long id);

    /**
     * 攻略详情：三态缓存（真实数据 / 空值防穿透 / 未命中回源）+ 随机 TTL 防雪崩
     *
     * @param id 攻略 id
     * @return 攻略详情（含作者昵称/头像、当前用户是否已点赞）
     */
    Result queryBlogDetail(Long id);

    /**
     * 热门攻略分页（按点赞数排序），分页结果缓存
     *
     * @param current 页码
     * @return 热门攻略列表
     */
    Result queryHotBlog(int current);

    /**
     * 逻辑删除攻略
     *
     * @param blogId 攻略 id
     */
    void deleteBlog(Long blogId);

    /**
     * 对账点赞数：以 Redis ZSet 的成员数为准修正数据库 liked 字段。
     * 定时任务发现缓存与 DB 计数不一致时，经 MQ 异步调用到这里。
     *
     * @param blogId     攻略 id
     * @param likedCount Redis 中的实际点赞数
     */
    void syncLikedCount(Long blogId, long likedCount);
}
