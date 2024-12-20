package com.hmdp.interceptor;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.StrUtil;
import com.hmdp.dto.UserDTO;
import com.hmdp.utils.UserHolder;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.LOGIN_USER_KEY;
import static com.hmdp.utils.RedisConstants.LOGIN_USER_TTL;

/**
 * @ClassName RefreshTokenInterceptor  全局拦截器 拦截所有请求
 * @Description TODO  token 刷新拦截器
 * @Date 2023/5/6 17:32
 */
public class RefreshTokenInterceptor implements HandlerInterceptor {

    private StringRedisTemplate stringRedisTemplate;

    public RefreshTokenInterceptor(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
          //拦截的思路:从redis中取出token对应的value，判断是否存在这个数据，如果没有则拦截，如果存在则将其保存到threadLocal中，并且放行。
        //Authorization 头主要用于身份验证，特别是当使用基于令牌的认证机制（如 JWT、OAuth 2.0）时。Cookie 头主要用于会话管理，特别是在使用基于会话的认证机制（如 JSESSIONID）时。
        // 客户端通常将令牌存储在内存中或使用 localStorage、sessionStorage 等.
        //Cookie 是跨标签页和窗口共享的。Session Storage 的数据仅在同一个标签页内共享。
        //1.从请求头中获取token
        String token = request.getHeader("authorization");
        //没有token说明还没登录成功过!!!
        //检查字符串token是否为空
        if (StrUtil.isBlank(token)){
            return true;//说明之前没有会话信息,放行.
        }
        //2.基于token 获取redis 中的user
        String tokenKey = LOGIN_USER_KEY+token;
        Map<Object, Object> userMap = stringRedisTemplate.opsForHash().entries(tokenKey);

        //有可能token过期了,所以请求头带有token,但是redis中没有存储userMap了,过期了就代表需要重新登录,先放行,在loginInterceptor中被拦截
        //3.判断用户是否存在
        if (userMap.isEmpty()){//此时用户不存在,所以为空,放行,但是在loginInterceptor中会被拦截
            return true;
        }
        //从这里开始的逻辑是处理之前已经登录的情况,就是从redis中拿出用户信息,并且刷新token
        //4. 将用户信息 存储到线程池中
        UserDTO userDTO = BeanUtil.fillBeanWithMap(userMap, new UserDTO(), false);
        UserHolder.saveUser(userDTO);
        //5.刷新token有效期
        stringRedisTemplate.expire(tokenKey,LOGIN_USER_TTL, TimeUnit.MINUTES);

        //6.放行
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        // 移除用户
        UserHolder.removeUser();//user每次请求结束都会删除,请求进来的时候又从userMap中注入到threadlocal<userDTO>中
    }
}
