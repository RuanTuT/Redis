package com.hmdp.controller;


import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.service.IBlogService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * <p>
 * 前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/blog")
public class BlogController {

    @Resource
    private IBlogService blogService;


    //发布笔记
    @PostMapping
    public Result saveBlog(@RequestBody Blog blog) {
        return blogService.saveBlog(blog);
    }

    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        // 修改点赞数量
        return blogService.likeBlog(id);
    }

    @GetMapping("/of/me")
    public Result queryMyBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        // 根据用户查询
        Page<Blog> page = blogService.query()
                .eq("user_id", user.getId()).page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        return Result.ok(records);
    }

    @GetMapping("/hot")
    public Result queryHotBlog(@RequestParam(value = "current", defaultValue = "1") Integer current) {
        return blogService.queryHotBlog(current);
    }

    //查看笔记
    @GetMapping("{id}")
    public Result queryBlogById(@PathVariable String id){
        return blogService.queryBlogById(id);
    }


    //点赞列表查询
    @GetMapping("/likes/{blogId}")
    public Result queryBlogLikes(@PathVariable String blogId){
        return blogService.queryBlogLikes(blogId);
    }

    //查询用户的笔记
    @GetMapping("/of/user")
    public Result queryUserBlog(@RequestParam("id") Long id,
                                @RequestParam(value = "current", defaultValue = "1") Integer current){


        return blogService.queryUserBlog(id,current);
    }

    //查看 关注的博主 发布的笔记
    /**
     * @description TODO 实现分页查询收邮箱
     * @param		
     * @return  com.hmdp.dto.Result
    */
    @GetMapping("/of/follow")
    public Result queryBlogOfFollow(@RequestParam("lastId") Long max,
                                    @RequestParam(value = "offset", defaultValue = "0") Integer offset){


        return blogService.queryBlogFollow(max,offset);
    }
}