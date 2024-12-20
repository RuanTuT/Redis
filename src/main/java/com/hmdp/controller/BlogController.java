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


    //给指定blog点赞
    @PutMapping("/like/{id}")
    public Result likeBlog(@PathVariable("id") Long id) {
        // 点赞，若是之前点过赞了就相当于取消点赞，修改数据库以及删除redis中的数据
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
        //在首页按照点赞数多少展示blog
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
        //查询top5中的点赞用户，并将点赞用户返回
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
    public Result queryBlogOfFollow(@RequestParam("lastId") Long max,//就是minTime
                                    @RequestParam(value = "offset", defaultValue = "0") Integer offset){//就是os
    //这里lastId的作用，表示从zset中读取的数据的范围时0-lastid之间，读取时先从最新的blog读取也就是最大的值开始读，保存最后结束的读取点，下次从0-这个读取点之间再次从大到小读，记住要跳过offset个blog往后读取
        //如果这段时间有新的blog产生，那就刷新页面，否则读取不到新的blog，刷新后就从头开始读取，即0-默认的lastid（最新的时间戳）

        return blogService.queryBlogFollow(max,offset);
    }
}
