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
