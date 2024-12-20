package com.hmdp.controller;

import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.Result;
import com.hmdp.utils.SystemConstants;
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

    @PostMapping("blog")
    public Result uploadImage(@RequestParam("file") MultipartFile image) {
        //@RequestParam 是处理文件上传的常用方式，因为上传的文件通常是表单数据（multipart/form-data）。
        //@RequestBody 不适合文件上传，主要用于 JSON 格式的请求体。若需要上传文件和 JSON，可以结合 @RequestPart 和 @RequestParam。
        //multipart/form-data一种支持文件上传的格式，常用于包含文件和字段的表单提交。application/x-www-form-urlencoded表单数据被编码为键值对的形式（类似查询字符串）。
        try {
            // 获取原始文件名称
            String originalFilename = image.getOriginalFilename();
            // 生成新文件名
            String fileName = createNewFileName(originalFilename);
            log.info("命名成功");
            // 保存文件
            image.transferTo(new File(SystemConstants.IMAGE_UPLOAD_DIR, fileName));
            // 返回结果
            log.debug("文件上传成功，{}", fileName);
            return Result.ok(fileName);
        } catch (IOException e) {
            throw new RuntimeException("文件上传失败", e);
        }
    }

    @GetMapping("/blog/delete")
    public Result deleteBlogImg(@RequestParam("name") String filename) {
        // 创建文件对象
        File file = new File(SystemConstants.IMAGE_UPLOAD_DIR, filename.substring(5));

        // 检查是否为目录
        if (file.isDirectory()) {
            return Result.fail("错误的文件名称");
        }

        // 检查文件是否存在
        if (!file.exists()) {
            return Result.fail("文件不存在，无法删除"+file.toPath()+file.getName());
        }


        // 尝试删除文件并捕获删除结果
        boolean deleted = FileUtil.del(file);
        if (!deleted) {
            return Result.fail("文件删除失败");
        }

        return Result.ok();
    }

    private String createNewFileName(String originalFilename) {
        // 获取后缀
        String suffix = StrUtil.subAfter(originalFilename, ".", true);
        // 生成目录
        String name = UUID.randomUUID().toString();
        int hash = name.hashCode();
        int d1 = hash & 0xF;
        int d2 = (hash >> 4) & 0xF;
        // 判断目录是否存在
        File dir = new File(SystemConstants.IMAGE_UPLOAD_DIR, StrUtil.format("/blogs/{}/{}", d1, d2));
        if (!dir.exists()) {
            dir.mkdirs();
        }
        // 生成文件名
        return StrUtil.format("/blogs/{}/{}/{}.{}", d1, d2, name, suffix);
    }
}
