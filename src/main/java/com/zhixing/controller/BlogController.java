package com.zhixing.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.zhixing.dto.Result;
import com.zhixing.dto.UserDTO;
import com.zhixing.entity.TravelNote;
import com.zhixing.entity.User;
import com.zhixing.service.IBlogService;
import com.zhixing.service.IUserService;
import com.zhixing.utils.SystemConstants;
import com.zhixing.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 鍓嶇鎺у埗鍣?
 * </p>
 *
 * @author 铏庡摜
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/TravelNote")
public class BlogController {

    @Resource
    private IBlogService blogService;
    @Resource
    private IUserService userService;

    @PostMapping
    public Result saveBlog(@RequestBody TravelNote TravelNote) {
        // 鑾峰彇鐧诲綍鐢ㄦ埛
        UserDTO user = UserHolder.getUser();
        TravelNote.setUserId(user.getId());
        // 淇濆瓨鎺㈠簵鍗氭枃
        blogService.save(TravelNote);
        // 杩斿洖id
        return Result.ok(TravelNote.getId());
    }

    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        // 淇敼鐐硅禐鏁伴噺
        return blogService.likeBlog(id);
    }

    @GetMapping("/likes/{id}")
    public Result queryBlogLikes(@PathVariable("id") Long id) {

        return blogService.queryBlogLikes(id);
    }

    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 鑾峰彇鐧诲綍鐢ㄦ埛
        UserDTO user = UserHolder.getUser();
        // 鏍规嵁鐢ㄦ埛鏌ヨ
        Page<TravelNote> page = blogService.query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 鑾峰彇褰撳墠椤垫暟鎹?
        List<TravelNote> records = page.getRecords();
        return Result.ok(records);
    }

    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 鏍规嵁鐢ㄦ埛鏌ヨ
        Page<TravelNote> page = blogService.query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 鑾峰彇褰撳墠椤垫暟鎹?
        List<TravelNote> records = page.getRecords();
        // 鏌ヨ鐢ㄦ埛
        records.forEach(TravelNote ->{
            Long userId = TravelNote.getUserId();
            User user = userService.getById(userId);
            TravelNote.setName(user.getNickName());
            TravelNote.setIcon(user.getIcon());
        });
        return Result.ok(records);
    }
}









