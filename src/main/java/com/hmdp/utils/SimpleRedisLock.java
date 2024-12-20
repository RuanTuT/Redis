package com.hmdp.utils;

import cn.hutool.core.lang.UUID;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.util.Collections;
import java.util.concurrent.TimeUnit;

/**
 * @ClassName SimpleRedisLock
 * @Description TODO  利用redis构建一个分布式锁
 * @Date 2023/5/9 10:36
 */
//SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
public class SimpleRedisLock implements ILock{

    private String name;  //锁的名称
    private StringRedisTemplate stringRedisTemplate;
    public SimpleRedisLock(String name, StringRedisTemplate stringRedisTemplate) {
        this.name = name;
        this.stringRedisTemplate = stringRedisTemplate;
    }

    private static final  String KEY_PREFIX = "lock:";

    private static final  String ID_PREFIX = UUID.randomUUID(true) + "-";  //给每一个锁加上uuid标识，用于比较判断锁,避免误删锁

    //加载lua脚本
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT;
    static {
        UNLOCK_SCRIPT = new DefaultRedisScript<>();
        UNLOCK_SCRIPT.setLocation(new ClassPathResource("unlock.lua"));
        UNLOCK_SCRIPT.setResultType(Long.class);
    }

    @Override
    public boolean tryLock(long timeoutSec) {
        //获取线程标识，即加锁时的唯一值
        String  threadId = ID_PREFIX + Thread.currentThread().getId();

        Boolean success = stringRedisTemplate.opsForValue()
                .setIfAbsent(KEY_PREFIX + name, threadId, timeoutSec, TimeUnit.SECONDS);
        return Boolean.TRUE.equals(success);//，如果你知道 success 永远不会为 null，并且你更喜欢更简洁的代码，
        // 那么使用 success == true 或 success（因为 if (success) 就足够了）也是可以的。
        // 但在处理可能为 null 的值时，使用 Boolean.TRUE.equals(success) 会更安全。
    }
    @Override
    public void unlock() {
        //调用lua脚本，在脚本中执行，满足原子性的
        stringRedisTemplate.execute(
                UNLOCK_SCRIPT,
                Collections.singletonList(KEY_PREFIX + name), //锁的key,获取锁的标识
                ID_PREFIX + Thread.currentThread().getId()//判断锁的键KEY_PREFIX + name对应的值是否等于Thread.currentThread().getId()
        );
        //经过以上代码改造后，我们就能够实现 拿锁比锁删锁的原子性动作了~
    }

    /*@Override
    public void unlock() {
        //获取线程标识
        String threadId = ID_PREFIX + Thread.currentThread().getId();
        //获取锁上的标识
        String id = stringRedisTemplate.opsForValue().get(KEY_PREFIX + name);

        if (threadId.equals(id)){
            //释放锁
            stringRedisTemplate.delete(KEY_PREFIX + name);
        }
    }*/
}
