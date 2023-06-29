package com.hmdp.utils;

/**
 * @ClassName ILock
 * @Description TODO 锁的基本接口
 * @Date 2023/5/9 10:33
 */
public interface ILock {

    /**
     * @description TODO  尝试获取锁
     * @param timeoutSec 锁持有的超时时间，过期后会自动释放
     * @return  true代表获取锁成功 false代表获取失败
    */
    boolean tryLock(long timeoutSec);



    /*
    * 释放锁
    *
    * */
    void unlock();

}
