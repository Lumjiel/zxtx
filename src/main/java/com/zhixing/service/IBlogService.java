package com.zhixing.service;

import com.zhixing.dto.Result;
import com.zhixing.entity.TravelNote;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  服务�?
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
public interface IBlogService extends IService<TravelNote> {

    /**
     * 修改点赞
     * @param id
     * @return
     */
    Result likeBlog(Long id);

    /**
     * 获取点赞列表
     * @param id
     * @return
     */
    Result queryBlogLikes(Long id);

    /**
     * 删除点赞数最少的文章
     * @param contentId
     */
    void delete_Blog(Long contentId);
}









