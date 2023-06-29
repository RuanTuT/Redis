package com.hmdp.utils;

import cn.hutool.core.util.BooleanUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import static com.hmdp.utils.RedisConstants.CACHE_NULL_TTL;
import static com.hmdp.utils.RedisConstants.LOCK_SHOP_KEY;

/**
 * @ClassName RedisClient
 * @Description TODO  缓存工具类     写入缓存（自动序列化为String类型的key中）
 *                                  写入缓存 设置逻辑过期时间来处理缓存击穿问题
 *                                  解决缓存穿透
 *                                  逻辑过期解决缓存击穿
 *                                  互斥锁解决缓存击穿
 * @Date 2023/5/7 16:00
 */

@Slf4j
@Component
public class RedisClient {
    private static final ExecutorService CACHE_REBUILD_EXECUTOR = Executors.newFixedThreadPool(10);
    private StringRedisTemplate stringRedisTemplate;

    public RedisClient(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }
    //将任意Java对象序列化为json并存储在string类型的key中，并且可以设置TTL过期时间
    public void set(String key,Object obj,Long time,TimeUnit unit){
        stringRedisTemplate.opsForValue().set(key, JSONUtil.toJsonStr(obj),time,unit);
    }

    // 将任意Java对象序列化为json并存储在string类型的key中，并且可以设置逻辑过期时间，用于处理缓存击穿问题
    public void setWithLogicalExpire(String key,Object obj,Long time,TimeUnit unit){
        //设置逻辑过期
        RedisData redisData = new RedisData();
        redisData.setData(obj);
        redisData.setExpireTime(LocalDateTime.now().plusSeconds(unit.toSeconds(time)));
//        写入redis
        stringRedisTemplate.opsForValue().set(key,JSONUtil.toJsonStr(redisData));
    }

    //根据指定的key查询缓存，并反序列化为指定类型，利用缓存空值的方式解决缓存穿透问题
    public  <R, ID> R  queryWithPassThrough(
            String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallBack, Long time,TimeUnit unit ) {
        //1.先查询缓存
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.判断redis中是否存在
        if (StrUtil.isNotBlank(json)){
            //3.redis中存在  命中
            R r = JSONUtil.toBean(json, type);
            return r;
        }
        //3.1 缓存中 命中的是“” 空值 即该数据是缓存穿透数据  不走数据库
        if (json != null){
            return null;
        }
        //4.redis中不存在, 未命中 在数据库中查询
        R r = dbFallBack.apply(id);
        if (r == null){
            //不存在则缓存一个空对象，解决缓存穿透
            stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
            return null;
        }
        //5.店铺存在，写入redis
        this.set(key,r,time,unit);
        return r;
    }


    //根据指定的key查询缓存，并反序列化为指定类型，需要利用逻辑过期解决缓存击穿问题
    public <R,ID> R queryWithLogicalExpire(
            String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallBack, Long time,TimeUnit unit){
        //1.先查询缓存
        String key = keyPrefix + id;
        String json = stringRedisTemplate.opsForValue().get(key);
        //2.判断redis中是否存在
        if (StrUtil.isBlank(json)){
            //3.redis中不存在  未命中
            return null;
        }
        //4.redis存在, 命中 ,需要先将json序列化为java对象
        RedisData data = JSONUtil.toBean(json, RedisData.class);
        LocalDateTime expireTime = data.getExpireTime();
        R r = JSONUtil.toBean((JSONObject) data.getData(), type);
        //5.判断缓存是否过期
        if (expireTime.isAfter(LocalDateTime.now())) {
            //未过期，直接返回商铺信息
            return r;
        }
        //过期了
        //6.缓存重建 先获取锁
        String lockKey = LOCK_SHOP_KEY + id;
        boolean flag = tryLock(lockKey);
        if (flag){
            //7.获取到锁，开启独立线程 查询数据库 并写入redis 设置过期时间

            //dobuleCheck
            String Json2 = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(Json2)){
                //3.redis中存在  命中
                R r1 = JSONUtil.toBean(Json2, type);
                return r1;
            }

            CACHE_REBUILD_EXECUTOR.submit(
                    () -> {
                        try {
                            //重建缓存
                            R r1 = dbFallBack.apply(id);
                            this.setWithLogicalExpire(key, r1, time, unit);
                        } catch (Exception e) {
                            throw new RuntimeException(e);
                        }finally {
                            unLock(lockKey);
                        }
                    });
        }
        //没有获取到锁,返回商铺信息
        return r; //脏数据

    }


    //根据指定的key查询缓存，并反序列化为指定类型，需要利用互斥锁解决缓存击穿问题
    public <R, ID> R queryWithMutex(String keyPrefix, ID id, Class<R> type, Function<ID,R> dbFallBack,Long time, TimeUnit unit){
        //1.先查询缓存
        String key = keyPrefix + id;
        String shopJson = stringRedisTemplate.opsForValue().get(key);
        //2.判断redis中是否存在
        if (StrUtil.isNotBlank(shopJson)){
            //3.redis中存在  命中
            R r = JSONUtil.toBean(shopJson, type);
            return r;
        }
        //3.1 缓存中 命中的是“” 空值 即该数据是缓存穿透数据  不走数据库
        if (shopJson != null){
            return null;
        }
        //4.redis中不存在, 未命中 做缓存重建
        //4.1 获取互斥锁
        String lockKey = LOCK_SHOP_KEY + id;
        R r = null;
        try {
            boolean flag = tryLock(lockKey);
            //4.2 判断是否获取到锁
            if (!flag){
                //4.3 没有获取到锁 等待 再次从缓存中查新
                Thread.sleep(50);  //休眠
                return queryWithMutex(keyPrefix, id, type, dbFallBack, time, unit); //重试
            }
            //4.4 获取到锁  查询数据库 将商铺数据写入缓存 释放互斥锁
            //成功获取到锁，应该再次检测redis缓存是否存在 做doubleCheck如果存在 则不需要查询数据库
            String Json2 = stringRedisTemplate.opsForValue().get(key);
            if (StrUtil.isNotBlank(Json2)){
                //3.redis中存在  命中
                R r1 = JSONUtil.toBean(shopJson, type);
                return r1;
            }

            //查询数据库
            R r1 = dbFallBack.apply(id);
            if (r1 == null){
                //数据库也不存在则缓存一个空对象，解决缓存穿透
                stringRedisTemplate.opsForValue().set(key,"",CACHE_NULL_TTL,TimeUnit.MINUTES);
                return null;
            }
            //5.店铺存在，写入redis
            String jsonStr = JSONUtil.toJsonStr(r1);
            this.set(key,jsonStr,time,unit);
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }finally {
            //6. 释放互斥锁
            unLock(lockKey);
        }
        return r;
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

}


