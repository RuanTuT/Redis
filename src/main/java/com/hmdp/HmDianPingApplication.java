package com.hmdp;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

//AspectJ支持编译时,加载时和运行时织入.正常不用AspectJ,这里仅是为了解决Spring AOP 自调用问题.
// 由于 AspectJ 是直接修改目标类的字节码，切面逻辑被编译进了目标类的方法中,无论是外部调用还是内部调用，切面逻辑都能正常生效,从而支持了自调用.
//当 Spring AOP 配置了代理模式（@EnableAspectJAutoProxy），它会为每个代理的 Bean 创建一个 ThreadLocal 变量，用来存储当前代理对象。
// AopContext.currentProxy() 会从这个 ThreadLocal 中获取当前代理对象。
@EnableAspectJAutoProxy(exposeProxy = true)  //暴露代理对象  proxy
@MapperScan("com.hmdp.mapper")
@SpringBootApplication
public class HmDianPingApplication {

    public static void main(String[] args) {
        SpringApplication.run(HmDianPingApplication.class, args);
    }

}
