package com.hmdp.service.impl;

import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.ShopType;
import com.hmdp.mapper.ShopTypeMapper;
import com.hmdp.service.IShopTypeService;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 *  服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class ShopTypeServiceImpl extends ServiceImpl<ShopTypeMapper, ShopType> implements IShopTypeService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    @Override
    public Result queryTypeList() {
        //先查询缓存
        List<String> shopTypeJson = stringRedisTemplate.opsForList().range(CACHE_SHOP_TYPE_KEY, 0, -1);//获取所有缓存的值

        if (!CollectionUtils.isEmpty(shopTypeJson)){
            //缓存命中
            //使用stream 流将json集合转为bean集合
            List<ShopType> shopTypes = shopTypeJson.stream()
                    .map(item -> JSONUtil.toBean(item, ShopType.class))
                    .sorted(Comparator.comparingInt(ShopType::getSort))
                    .collect(Collectors.toList());
            return Result.ok(shopTypes);
        }
        //缓存未命中,从数据库中查询
        List<ShopType> shopTypes = query().orderByAsc("sort").list();
        //判断数据库中是否有数据
        if (CollectionUtils.isEmpty(shopTypes)){
            //数据库不存在，则缓存一个空集合，解决缓存穿透
            stringRedisTemplate.opsForList().leftPushAll(CACHE_SHOP_TYPE_KEY, Collections.emptyList().toString());
            stringRedisTemplate.expire(CACHE_SHOP_TYPE_KEY, CACHE_NULL_TTL, TimeUnit.MINUTES);
            return Result.fail("商户类型信息为空！");
        }
        //使用steam流 将bean集合转为string集合
        List<String> shopTypeList = shopTypes.stream()
                .sorted(Comparator.comparingInt(ShopType::getSort))
                .map(JSONUtil::toJsonStr)//JSONUtil::toJsonStr是方法引用,代表的是一个函数或方法
                .collect(Collectors.toList());

        //存在商户类型，写入redis
        stringRedisTemplate.opsForList().leftPushAll(CACHE_SHOP_TYPE_KEY,shopTypeList);
        stringRedisTemplate.expire(CACHE_SHOP_TYPE_KEY,CACHE_SHOP_TYPE_TTL, TimeUnit.MINUTES);

        return Result.ok(shopTypes);
    }
}
