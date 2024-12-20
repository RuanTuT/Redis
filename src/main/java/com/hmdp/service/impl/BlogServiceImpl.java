package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.dto.ScrollResult;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.Blog;
import com.hmdp.entity.Follow;
import com.hmdp.entity.User;
import com.hmdp.mapper.BlogMapper;
import com.hmdp.service.IBlogService;
import com.hmdp.service.IFollowService;
import com.hmdp.service.IUserService;
import com.hmdp.utils.SystemConstants;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ZSetOperations;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.*;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.BLOG_LIKED_KEY;
import static com.hmdp.utils.RedisConstants.FEED_KEY;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class BlogServiceImpl extends ServiceImpl<BlogMapper, Blog> implements IBlogService {

    @Resource
    private IUserService userService;
    @Resource
    private StringRedisTemplate stringRedisTemplate;


    @Resource
    private IFollowService followService;
    //保存探店博文 并推送博文id 到所有粉丝的redis，只有在关注后才能收到推送的blog，如果在创建blog后关注就不能收到了
    @Override
    public Result saveBlog(Blog blog) {
        // 获取登录用户
        UserDTO user = UserHolder.getUser();
        blog.setUserId(user.getId());
        // 保存探店博文
        boolean isSuccess = save(blog);
        if (!isSuccess){
            return Result.fail("新增笔记失败！");
        }
        //获取当前用户的粉丝，然后把数据推送到粉丝的redis中去
        //select user_id from tb_follow where follow_user_id = #{id}
        //粉丝列表
        List<Follow> followList = followService.query().eq("follow_user_id", user.getId()).list();
        //推送数据给粉丝
        for (Follow follow : followList) {
            //获取粉丝id
            Long userId = follow.getUserId();

            //存储 笔记id 的key
            String key = FEED_KEY + userId;
            //向一个有序集合（Sorted Set）中添加一个元素。这段代码将博客的 ID 作为成员（member）添加到指定的键（key）对应的有序集合中，并使用当前时间戳作为该成员的分数（score）
            stringRedisTemplate.opsForZSet().add(key,blog.getId().toString(),System.currentTimeMillis());//将每个新save的blogId保存到关注了发布这个blog的用户的粉丝的邮箱里
        }
        // 返回id
        return Result.ok(blog.getId());
    }

    //每次在点过赞后前端会发出请求触发这个方法，在这个方法中，会根据blogid对返回一个blog，这个blog包含创建的用户和发出请求的用户是否点赞等信息。其中，会对这个blog自己是否点赞进行判断，若是在redis中以这个blog为键的zset中找到自己，则点过赞了。
    @Override
    public Result queryBlogById(String id) {
        //查询博文
        Blog blog = getById(id);

        if (blog == null) {
            return Result.fail("笔记不存在！");
        }
        //查询blog 发布的用户
        queryBlogUser(blog);
        //查看当前用户是否点过赞，返回时给前端，前端可以在页面上呈现自己点赞的高亮效果
        IsBlogLiked(blog);
        return Result.ok(blog);
    }

    //查询某个blog此用户是否点赞，若点赞则设置为true
    private void IsBlogLiked(Blog blog) {
        String likedKey = BLOG_LIKED_KEY + blog.getId();

        // 1.获取登录用户
        UserDTO user = UserHolder.getUser();
        if (user == null) {
            // 用户未登录，无需查询是否点赞
            return;
        }
        Long userId = user.getId();
        //查看该用户是否已经点过赞
        //这个redis里的记录会在点赞数为0时也就是点赞用户为空时删除
        Double score = stringRedisTemplate.opsForZSet().score(likedKey, userId.toString());//判断某个blogid里是否有本用户
        blog.setIsLike(score != null);
    }

    //给笔记点赞，点赞时会将以blogid为键的用户id为成员，时间戳为分数放入redis中
    @Override
    public Result likeBlog(Long id) {
        String likedKey = BLOG_LIKED_KEY + id;

        Long userId = UserHolder.getUser().getId();

        //查看该用户是否已经点过赞
        Double score = stringRedisTemplate.opsForZSet().score(likedKey, userId.toString());
        if (score != null) {
            //已经点过赞了,再点一次赞就相当于取消点赞
            boolean isSuccess = update().setSql("liked = liked - 1").eq("id", id).update();
            if (isSuccess) {
                //数据库更新成功，更新redis
                stringRedisTemplate.opsForZSet().remove(likedKey, userId.toString());
            }
        } else {
            //还没有点过赞 点赞
            boolean isSuccess = update().setSql("liked = liked + 1").eq("id", id).update();
            if (isSuccess) {
                //数据库更新成功，更新redis
                stringRedisTemplate.opsForZSet().add(likedKey, userId.toString(), System.currentTimeMillis());
            }
        }
        return Result.ok();
    }

    @Override
    public Result queryHotBlog(Integer current) {
        // 根据用户查询
        Page<Blog> page = query()
                .orderByDesc("liked")
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        // 获取当前页数据
        List<Blog> records = page.getRecords();
        // 查询用户
        records.forEach((blog -> {
            queryBlogUser(blog);
            IsBlogLiked(blog);
        }));
        return Result.ok(records);
    }

    @Override
    public Result queryBlogLikes(String blogId) {
        String key = BLOG_LIKED_KEY + blogId;
        //查询top5中的点赞用户  之前点赞已经按时间戳顺序 存入redis的zset中
        Set<String> top5 = stringRedisTemplate.opsForZSet().range(key, 0, 4);
        if (top5 == null || top5.isEmpty()) {
            return Result.ok(Collections.emptyList());
        }
        //解析出查询到的用户id
        List<Long> ids = top5.stream().map(Long::valueOf).collect(Collectors.toList());
        String idsStr = StrUtil.join(",", ids);
        //根据用户id查询用户信息   WHERE id IN ( 5 , 1 ) ORDER BY FIELD(id, 5, 1)
        List<UserDTO> userDTOS = userService.query().in("id",ids).last("order by field(id,"+idsStr+")")
                .list().stream().map(
                user -> BeanUtil.copyProperties(user, UserDTO.class)
        ).collect(Collectors.toList());

        return Result.ok(userDTOS);
    }

    //查询用户笔记
    @Override
    public Result queryUserBlog(Long id, Integer current) {
        //select * from tb_blog where user_id = id limit ?,?

        Page<Blog> blogPage = query().eq("user_id", id)
                .page(new Page<>(current, SystemConstants.MAX_PAGE_SIZE));
        List<Blog> records = blogPage.getRecords();
        return Result.ok(records);
    }

    //实现分页 查询关注的所有人的blog邮箱，所以我自己是粉丝
    @Override
    public Result queryBlogFollow(Long max, Integer offset) {//请求的lastid也就是这个max就是这个minTime，offset就是os
        //获取当前用户
        Long userId = UserHolder.getUser().getId();

        //查询收件邮箱 key  ZREVRANGEBYSCORE key Max Min LIMIT offset count
        String key = FEED_KEY + userId;
        //这段代码会从指定的键对应的有序集合中获取分数在 0 到 max 之间的成员，并且从 offset 开始，最多获取 2 个成员。
        Set<ZSetOperations.TypedTuple<String>> typedTuples = stringRedisTemplate.opsForZSet()
                .reverseRangeByScoreWithScores(key, 0, max, offset, 2);//reverseRangeByScoreWithScores: 按照分数降序返回结果，同时包含分数值。0, max: 分数范围，表示查询分数介于 0 和 max 之间的元素。
        if (typedTuples == null || typedTuples.isEmpty()){
            return Result.ok();
        }
        //Feed流中的数据会不断更新，所以数据的角标也在变化，因此不能采用传统的分页模式。
        //采用Feed流的滚动分页，我们需要记录每次操作的最后一条，然后从这个位置开始去读取数据
        //解析 笔记id 、 minTime 、 offset
        List<Long> ids = new ArrayList<>(typedTuples.size());
        long minTime = 0;
        int os = 1;
        for (ZSetOperations.TypedTuple<String> typedTuple : typedTuples) {
            //获取id
            ids.add(Long.valueOf(Objects.requireNonNull(typedTuple.getValue())));//getValue()：返回成员值。
            //获取时间戳，score
            long time = typedTuple.getScore().longValue();  //最小 score。getScore()：返回成员的分数,也就是时间戳。
            if (time == minTime){
                os++;
            }else {
                minTime = time;//每次查询完成后，我们要分析出查询出数据的最小时间戳，这个值会作为下一次查询的条件
                os = 1;//我们需要找到与上一次查询相同的查询个数作为偏移量，下次查询时，跳过这些查询过的数据，拿到我们需要的数据
            }
        }
        os = minTime == max ? os : os + offset;
        // 5.根据id查询blog
        String idStr = StrUtil.join(",", ids);
        List<Blog> blogs = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();

        for (Blog blog : blogs) {
            // 5.1.查询blog有关的用户
            queryBlogUser(blog);
            // 5.2.查询blog是否被点赞，并且若点过赞了设置blog的isLiked为true
            IsBlogLiked(blog);
        }
        //封装并返回
        ScrollResult scrollResult = new ScrollResult();
        scrollResult.setList(blogs);
        scrollResult.setOffset(os);
        scrollResult.setMinTime(minTime);

        return Result.ok(scrollResult);
    }

    private void queryBlogUser(Blog blog) {
        User user = userService.getById(blog.getUserId());
        blog.setName(user.getNickName());
        blog.setIcon(user.getIcon());
    }
}
