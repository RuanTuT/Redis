package com.hmdp.service.impl;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.Shop;
import com.hmdp.mapper.ShopMapper;
import com.hmdp.service.IShopService;
import com.hmdp.utils.RedisClient;
import com.hmdp.utils.SystemConstants;
import org.springframework.data.geo.Distance;
import org.springframework.data.geo.GeoResult;
import org.springframework.data.geo.GeoResults;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.domain.geo.GeoReference;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.TimeUnit;

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
public class ShopServiceImpl extends ServiceImpl<ShopMapper, Shop> implements IShopService {

    @Resource
    private RedisClient redisClient;
    @Resource
    private StringRedisTemplate stringRedisTemplate;

    //根据id查询商户信息
    @Override
    public Result queryShopById(Long id) {
        //缓存击穿
//        Shop shop = queryWithPassThrough(id);
        //利用互斥锁 解决缓存穿透
//        Shop shop = queryWithMutex(id);
        //利用逻辑删除 解决缓存穿透
//        Shop shop = queryWithLogicalExpire(id);

        //利用封装好的工具类
        Shop shop = redisClient.queryWithPassThrough(
                CACHE_SHOP_KEY, id, Shop.class, this::getById, CACHE_SHOP_TTL, TimeUnit.MINUTES);


        if (shop == null){
            return Result.fail("店铺不存在！");
        }

        return Result.ok(shop) ;
    }
/*

    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    private Shop queryWithLogicalExpire(Long id) {
        //1.先查询缓存
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断redis中是否存在
        if (StrUtil.isBlank(shopJson)){
            //3.redis中不存在  未命中
            return null;
        }

        //4.redis存在, 命中 ,需要先将json序列化为java对象
        RedisData data = JSONUtil.toBean(shopJson, RedisData.class);
        LocalDateTime expireTime = data.getExpireTime();
        Shop shop = JSONUtil.toBean((JSONObject) data.getData(), Shop.class);
        //5.判断缓存是否过期

        if (expireTime.isAfter(LocalDateTime.now())) {
            //未过期，直接返回商铺信息
            return shop;
        }
        //过期了
        //6.缓存重建 先获取锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean flag = tryLock(lockKey);
        if (flag){
            //7.获取到锁，开启独立线程 查询数据库 并写入redis 设置过期时间
            CACHE_REBUILD_EXECUTOR.submit(
                    () -> {
                        try {
                            this.rebuildShopCache(id,20L);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }finally {
                            unLock(lockKey);
                        }
                    });
        }
        //没有获取到锁,返回商铺信息
        return shop; //脏数据
    }

    //利用互斥锁解决缓存穿透
    private Shop queryWithMutex(Long id) {
        //1.先查询缓存
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断redis中是否存在
        if (StrUtil.isNotBlank(shopJson)){
            //3.redis中存在  命中
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //3.1 缓存中 命中的是“” 空值 即该数据是缓存穿透数据  不走数据库
        if (shopJson != null){
            return null;
        }
        //4.redis中不存在, 未命中 做缓存重建
        //4.1 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        Shop shop = null;
        try {
            boolean flag = tryLock(lockKey);
            //4.2 判断是否获取到锁
            if (!flag){
                //4.3 没有获取到锁 等待 再次从缓存中查新
                Thread.sleep(50);  //休眠
                return queryWithMutex(id); //重试
            }
            //4.4 获取到锁  查询数据库 将商铺数据写入缓存 释放互斥锁
            //成功获取到锁，应该再次检测redis缓存是否存在 做doubleCheck如果存在 则不需要查询数据库
            String shopJson2 = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(shopJson2)){
                //3.redis中存在  命中
                Shop shop2 = JSONUtil.toBean(shopJson, Shop.class);
                return shop2;
            }
            //查询数据库
            shop = getById(id);
            if (shop == null){
                //数据库也不存在则缓存一个空对象，解决缓存穿透
                stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
                return null;
            }
            //5.店铺存在，将shop写入redis
            String jsonStr = JSONUtil.toJsonStr(shop);
            stringRedisTemplate.opsForValue().set(key,jsonStr,CACHE_SHOP_TTL, TimeUnit.MINUTES);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            //6. 释放互斥锁
            unLock(lockKey);
        }
        return shop;
    }


    */
/**
     * @description TODO 缓存击穿
    *//*

    private Shop queryWithPassThrough(Long id) {
        //1.先查询缓存
        String key = CACHE_SHOP_KEY + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断redis中是否存在
        if (StrUtil.isNotBlank(shopJson)){
            //3.redis中存在  命中
            Shop shop = JSONUtil.toBean(shopJson, Shop.class);
            return shop;
        }
        //3.1 缓存中 命中的是“” 空值 即该数据是缓存穿透数据  不走数据库
        if (shopJson != null){
            return null;
        }
        //4.redis中不存在, 未命中 在数据库中查询
        Shop shop = getById(id);
        if (shop == null){
            //不存在则缓存一个空对象，解决缓存穿透
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }
        //5.店铺存在，将shop写入redis
        String jsonStr = JSONUtil.toJsonStr(shop);
        stringRedisTemplate.opsForValue().set(key,jsonStr,CACHE_SHOP_TTL, TimeUnit.MINUTES);
        return shop;
    }

    //获取锁  利用redis的setnx方法来表示获取锁
    private boolean tryLock(String key){
        Boolean flag = stringRedisTemplate.opsForValue().setIfAbsent(key, "1", 10, TimeUnit.SECONDS);
        return BooleanUtil.isTrue(flag);  //将包装类 转为基本类 防止空指针！！！
    }
    //删除锁
    private void unLock(String key){
        stringRedisTemplate.delete(key);
    }


    //重建缓存  查询数据库 写入缓存
    public void rebuildShopCache(Long id,Long expireSecond){
        //直接查询数据库
        Shop shop = getById(id);
        //封装逻辑过期时间
        RedisData redisData = new RedisData();
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(expireSecond));
        redisData.setData(shop);
        //写入redis
        stringRedisTemplate.opsForValue().set(CACHE_SHOP_KEY + id, JSONUtil.toJsonStr(redisData));//逻辑过期
    }
*/



    @Override
    @Transactional  //保证数据库和缓存的原子性
    public Result update(Shop shop) {
        if (shop.getId() == null){
            return Result.fail("修改商户不存在！");
        }
        //1.先修改数据库
        updateById(shop);
        //2.删除缓存
        stringRedisTemplate.delete(CACHE_SHOP_KEY+shop.getId());
        return Result.ok();
    }

    /**
     * @description TODO  按地理坐标 升序 分页查询 商铺
     * @param	typeId 商铺类型
     * @param	current 当前页
     * @param	x 精度
     * @param	y 维度
     * @return  com.hmdp.dto.Result
    */
    @Override
    public Result queryShopByType(Integer typeId, Integer current, Double x, Double y) {
        /*//1.判断是否 按照地理坐标 查询
        if (x == null || y == null){
            // 根据类型分页查询
            Page<Shop> page = query()
                    .eq("type_id", typeId)
                    .page(new Page<>(current, DEFAULT_PAGE_SIZE));
            // 返回数据
            return Result.ok(page.getRecords());
        }
        // 2.计算分页参数
        Integer from = (current - 1) * DEFAULT_PAGE_SIZE;
        Integer end = current * DEFAULT_PAGE_SIZE;

        //3.按照地理坐标 分页查询 查询结果 shopId distance
        //GEOSEARCH key BYLONLAT x y BYRADIUS 10 WITHDISTANCE
        String key = SHOP_GEO_KEY + typeId;

        GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo().search(
                key,
                GeoReference.fromCoordinate(x, y),
                new Distance(5000), //米
                RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
        );
        // 4.解析出id
        if (results == null){
            return Result.ok(Collections.emptyList());
        }
        List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();

        if (list.size() < end){
            //没有下一页了
            return Result.ok(Collections.emptyList());
        }
        // 4.1.截取 from ~ end的部分  做逻辑分页
        // 4.1.截取 from ~ end的部分
        List<Long> ids = new ArrayList<>(list.size());
        Map<String, Distance> distanceMap = new HashMap<>(list.size());
        list.stream().skip(from).forEach(result -> {
            // 4.2.获取店铺id
            String shopIdStr = result.getContent().getName();
            ids.add(Long.valueOf(shopIdStr));
            // 4.3.获取距离
            Distance distance = result.getDistance();
            distanceMap.put(shopIdStr, distance);
        });
        // 5.根据id查询Shop
        StrUtil.join(",",ids);
        List<Shop> shops = query().in("id", ids).last("order by field(id," + ids + ")").list();
        for (Shop shop : shops) {
            shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
        }

        return Result.ok(shops);
    }*/
            // 1.判断是否需要根据坐标查询
            if (x == null || y == null) {
                // 不需要坐标查询，按数据库查询
                Page<Shop> page = query()
                        .eq("type_id", typeId)
                        .page(new Page<>(current, SystemConstants.DEFAULT_PAGE_SIZE));
                // 返回数据
                return Result.ok(page.getRecords());
            }

            // 2.计算分页参数
            int from = (current - 1) * SystemConstants.DEFAULT_PAGE_SIZE;
            int end = current * SystemConstants.DEFAULT_PAGE_SIZE;

            // 3.查询redis、按照距离排序、分页。结果：shopId、distance
            String key = SHOP_GEO_KEY + typeId;
            GeoResults<RedisGeoCommands.GeoLocation<String>> results = stringRedisTemplate.opsForGeo() // GEOSEARCH key BYLONLAT x y BYRADIUS 10 WITHDISTANCE
                    .search(
                            key,
                            GeoReference.fromCoordinate(x, y),
                            new Distance(5000),
                            RedisGeoCommands.GeoSearchCommandArgs.newGeoSearchArgs().includeDistance().limit(end)
                    );
            // 4.解析出id
            if (results == null) {
                return Result.ok(Collections.emptyList());
            }
            List<GeoResult<RedisGeoCommands.GeoLocation<String>>> list = results.getContent();
            if (list.size() <= from) {
                // 没有下一页了，结束
                return Result.ok(Collections.emptyList());
            }
            // 4.1.截取 from ~ end的部分
            List<Long> ids = new ArrayList<>(list.size());
            Map<String, Distance> distanceMap = new HashMap<>(list.size());
            list.stream().skip(from).forEach(result -> {
                // 4.2.获取店铺id
                String shopIdStr = result.getContent().getName();
                ids.add(Long.valueOf(shopIdStr));
                // 4.3.获取距离
                Distance distance = result.getDistance();
                distanceMap.put(shopIdStr, distance);
            });
            // 5.根据id查询Shop
            String idStr = StrUtil.join(",", ids);
            List<Shop> shops = query().in("id", ids).last("ORDER BY FIELD(id," + idStr + ")").list();
            for (Shop shop : shops) {
                shop.setDistance(distanceMap.get(shop.getId().toString()).getValue());
            }
            // 6.返回
            return Result.ok(shops);
        }
}
