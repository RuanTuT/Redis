package com.hmdp.utils;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * @ClassName RedisIdWorker
 * @Description TODO  redis实现全局唯一id  占位符+时间戳+自增序列
 * @Date 2023/5/7 21:34
 */
@Component
public class RedisIdWorker {

    /**
     * 开始时间戳
     */
    private static final long BEGIN_TIMESTAMP = 1640995200L;  //2022-1-1-1-1
    /**
     * 序列号的位数
     */
    private static final int COUNT_BITS = 32;

    private StringRedisTemplate stringRedisTemplate;

    public RedisIdWorker(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    public long nextId(String keyPrefix) {
        // 1.生成时间戳
        LocalDateTime now = LocalDateTime.now();
        long nowSecond = now.toEpochSecond(ZoneOffset.UTC);
        long timestamp = nowSecond - BEGIN_TIMESTAMP;

        // 2.生成序列号
        // 2.1.获取当前日期，精确到天
        String date = now.format(DateTimeFormatter.ofPattern("yyyy:MM:dd"));
        // 2.2.自增长，如果Redis中没有这个键，那么increment方法会创建一个新的键，并将其值设置为1（因为自增了0）。如果键已经存在，那么它的值会增加1。
        //increment("someKey", 5)会将键someKey的值增加5。
        long count = stringRedisTemplate.opsForValue().increment("icr:" + keyPrefix + ":" + date);

        // 3.拼接并返回，合并结果：最终的结果是一个整数，它同时包含了原始的 timestamp（左移后）和 count 的信息。左移 加 按位或
        return timestamp << COUNT_BITS | count;
    }
}
