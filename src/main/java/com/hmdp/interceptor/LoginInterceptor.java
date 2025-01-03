package com.hmdp.interceptor;

import com.hmdp.dto.UserDTO;
import com.hmdp.utils.UserHolder;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @ClassName LoginInterceptor
 * @Description TODO   登录拦截器校验
 * @Date 2023/5/6 16:31
 */

//Interceptor的作用范围：Interceptor的范围则仅限于Spring，对于静态资源、Servlet、JSP等请求，Interceptor不会生效。
//Filter的作用范围：Filter在Servlet容器的范围内工作，拦截所有进入容器的请求。它的范围更广泛，不仅仅局限于Spring MVC的请求
public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        //先从session 中拿到用户信息
        /*HttpSession session = request.getSession();
        Object user = session.getAttribute("user");*/

        UserDTO user = UserHolder.getUser();

        if (user == null){
            //用户不存在
            response.setStatus(401);
            //拦截
            return false;
        }
        //用户存在 ，将用户信息保存到 线程池中 做到线程隔离
       // UserHolder.saveUser(user);
        //放行
        return true;
    }
//    @Override
//    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
//        //移除用户
//        UserHolder.removeUser();
//    }
}
