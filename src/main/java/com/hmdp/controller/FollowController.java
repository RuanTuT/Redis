package com.hmdp.controller;


import com.hmdp.dto.Result;
import com.hmdp.service.IFollowService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;

/**
 * <p>
 *  前端控制器
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@RestController
@RequestMapping("/follow")
public class FollowController {
    @Resource
    private IFollowService followService;

    //尝试关注用户
    @PutMapping("/{id}/{isFollow}")
    public Result follow(@PathVariable("id") Long followId, @PathVariable("isFollow") Boolean isFollow){
        return followService.follow(followId,isFollow);
    }


    //查看是否关注用户
    @GetMapping("/or/not/{id}")
    public Result isFollow(@PathVariable("id") String id){

        return followService.isFollow(id);
    }

    //查看共同关注列表
    @GetMapping("/common/{id}")
    public Result commonFollow(@PathVariable("id") String id){


        return followService.commonFollow(id);

    }

}
