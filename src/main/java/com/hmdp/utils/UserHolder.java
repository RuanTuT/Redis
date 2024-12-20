package com.hmdp.utils;

import com.hmdp.dto.UserDTO;

public class UserHolder {
    //在Java中，ThreadLocal 是一个特殊的类，它允许你存储线程本地的变量。
    // 这意味着每个线程都会拥有它自己的变量副本(实际上是ThreadLocalMap)，这些变量对其他线程是不可见的。这对于需要在多线程环境中维护线程特定数据的场景非常有用。
    //在多线程环境中不推荐以静态方式使用 ThreadLocal,尤其是在使用线程池时,如果一个线程在某次任务中设置了 ThreadLocal 变量，但没有清除，那么在后续的任务中，这些变量仍然存在于线程中，可能导致意外的行为或内存泄漏。
    private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();//这个ThreadLocal是静态的,多个线程访问同一个 ThreadLocal 实例，每个线程看到的都是自己独有的变量值。
   //每个线程自己的ThreadLocalMap中插入键值对:键为这个ThreadLocal<UserDTO> tl,值为user
    public static void saveUser(UserDTO user){
        tl.set(user);//user每次请求结束都会由 RefreshTokenInterceptor从ThreadLocalMap中 remove
    }

    public static UserDTO getUser(){
        return tl.get();
    }
//由于 ThreadLocalMap 的键是弱引用，值是强引用，如果未及时调用 remove() 清理数据，会导致值对象无法被回收，从而可能引发内存泄漏。调用 ThreadLocal.remove() 清除不再需要的数据。
    public static void removeUser(){
        tl.remove();
    }
}
