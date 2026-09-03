package com.zhixing.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.zhixing.dto.Result;
import com.zhixing.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

@Slf4j
@RestController
@RequestMapping("upload")
public class UploadController {

    @PostMapping("TravelNote")
    public Result uploadImage(@RequestParam("file") MultipartFile image) {
        try {
            // 鑾峰彇鍘熷鏂囦欢鍚嶇О
            String originalFilename = image.getOriginalFilename();
            // 鐢熸垚鏂版枃浠跺悕
            String fileName = createNewFileName(originalFilename);
            // 淇濆瓨鏂囦欢
            image.transferTo(new File(SystemConstants.IMAGE_UPLOAD_DIR, fileName));
            // 杩斿洖缁撴灉
            log.debug("鏂囦欢涓婁紶鎴愬姛锛寋}", fileName);
            return Result.ok(fileName);
        } catch (IOException e) {
            throw new RuntimeException("鏂囦欢涓婁紶澶辫触", e);
        }
    }

    @GetMapping("/TravelNote/delete")
    public Result deleteBlogImg(@RequestParam("name") String filename) {
        File file = new File(SystemConstants.IMAGE_UPLOAD_DIR, filename);
        if (file.isDirectory()) {
            return Result.fail("错误的文件名");
        }
        FileUtil.del(file);
        return Result.ok();
    }

    private String createNewFileName(String originalFilename) {
        // 鑾峰彇鍚庣紑
        String suffix = StrUtil.subAfter(originalFilename, ".", true);
        // 鐢熸垚鐩綍
        String name = UUID.randomUUID().toString();
        int hash = name.hashCode();
        int d1 = hash & 0xF;
        int d2 = (hash >> 4) & 0xF;
        // 鍒ゆ柇鐩綍鏄惁瀛樺湪
        File dir = new File(SystemConstants.IMAGE_UPLOAD_DIR, StrUtil.format("/blogs/{}/{}", d1, d2));
        if (!dir.exists()) {
            dir.mkdirs();
        }
        // 鐢熸垚鏂囦欢鍚?
        return StrUtil.format("/blogs/{}/{}/{}.{}", d1, d2, name, suffix);
    }
}









