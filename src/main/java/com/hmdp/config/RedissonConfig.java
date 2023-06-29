package com.hmdp.config;

import org.redisson.Redisson;
import org.redisson.api.RedissonClient;
import org.redisson.config.Config;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * @ClassName RedissonConfig
 * @Description TODO
 * @Date 2023/5/9 11:40
 */
@Configuration
public class RedissonConfig {


    @Bean
    public RedissonClient redissonClient() {
        // 配置
        Config config = new Config();
        config.useSingleServer().setAddress("redis://localhost:6379").setPassword("wang");

        // 创建RedissonClient对象
        return Redisson.create(config);
    }

}
