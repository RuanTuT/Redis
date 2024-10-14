package com.hmdp.utils;

import com.hmdp.dto.UserDTO;

public class UserHolder {
    //在Java中，ThreadLocal 是一个特殊的类，它允许你存储线程本地的变量。
    // 这意味着每个线程都会拥有它自己的变量副本，这些变量对其他线程是不可见的。这对于需要在多线程环境中维护线程特定数据的场景非常有用。
    private static final ThreadLocal<UserDTO> tl = new ThreadLocal<>();

    public static void saveUser(UserDTO user){
        tl.set(user);
    }

    public static UserDTO getUser(){
        return tl.get();
    }

    public static void removeUser(){
        tl.remove();
    }
}
