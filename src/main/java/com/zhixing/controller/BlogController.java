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
 * 前端控制器
 * </p>
 *
 * @author zhixing
 * @since 2026-02-08
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
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        TravelNote.setUserId(user.getId());
        // 保存探店博文
        blogService.save(TravelNote);
        // 返回id
        return Result.ok(TravelNote.getId());
    }

    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        // 修改点赞数量
        return blogService.likeBlog(id);
    }

    @GetMapping("/likes/{id}")
    public Result queryBlogLikes(@PathVariable("id") Long id) {

        return blogService.queryBlogLikes(id);
    }

    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户
        Page<TravelNote> page = blogService.query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数?
        List<TravelNote> records = page.getRecords();
        return Result.ok(records);
    }

    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 根据用户
        Page<TravelNote> page = blogService.query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数?
        List<TravelNote> records = page.getRecords();
        // 查询用户
        records.forEach(TravelNote ->{
            Long userId = TravelNote.getUserId();
            User user = userService.getById(userId);
            TravelNote.setName(user.getNickName());
            TravelNote.setIcon(user.getIcon());
        });
        return Result.ok(records);
    }
}










