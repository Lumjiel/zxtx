package com.zhixing.service;

import com.zhixing.dto.Result;
import com.zhixing.entity.TravelNote;
import com.baomidou.mybatisplus.extension.service.IService;

/**
 * <p>
 *  鏈嶅姟绫?
 * </p>
 *
 * @author 铏庡摜
 * @since 2021-12-22
 */
public interface IBlogService extends IService<TravelNote> {

    /**
     * 淇敼鐐硅禐
     * @param id
     * @return
     */
    Result likeBlog(Long id);

    /**
     * 鑾峰彇鐐硅禐鍒楄〃
     * @param id
     * @return
     */
    Result queryBlogLikes(Long id);

    /**
     * 鍒犻櫎鐐硅禐鏁版渶灏戠殑鏂囩珷
     * @param contentId
     */
    void delete_Blog(Long contentId);
}









