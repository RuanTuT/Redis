package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.BitFieldSubCommands;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpSession;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static com.hmdp.utils.RedisConstants.*;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {
    @Resource
    private StringRedisTemplate stringRedisTemplate;
    //发送验证码
    @Override
    public Result sendCode(String phone, HttpSession session) {
        //1. 校验手机号
        if(RegexUtils.isPhoneInvalid(phone)){
            //2.无效手机号
            return Result.fail("非法手机号！");
        }

        //3.手机号存在，生成验证码
        String code = RandomUtil.randomNumbers(6);
        /*//4. 保存验证码到session
        session.setAttribute("code_"+phone,code);*/
        stringRedisTemplate.opsForValue().set(LOGIN_CODE_KEY + phone,code,5L,TimeUnit.MINUTES);
        //5，发送验证码
        log.debug("发送短信验证码成功，验证码：{}",code);

        return Result.ok();
    }
    //登录
    @Override
    public Result login(LoginFormDTO loginForm, HttpSession session) {

        //1.获取手机号和验证码
        String phone = loginForm.getPhone();
        String cacheCode = loginForm.getCode();

        //1. 校验手机号
        if(RegexUtils.isPhoneInvalid(phone)){
            //2.无效手机号
            return Result.fail("非法手机号！");
        }

        //2.校验验证码是否一致
//        Object code = session.getAttribute("code_" + phone);
        String code = stringRedisTemplate.opsForValue().get(LOGIN_CODE_KEY + phone);
        if (code == null || !code.equals(cacheCode)){
            //不一致
            return Result.fail("手机号或验证码错误！");
        }
        //3.一致，根据手机号 查询用户信息
        //SELECT * from user where phone = #{phone}
        User user = query().eq("phone", phone).one();

        //4.用户信息不存在，创建该用户信息
        if (user == null){
            user = createUserWithPhone(phone);
        }
        /*5.保存用户信息到session 中
        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));*/


        //5.保存用户信息到redis中
        //5.1 为每个用户信息 随机生成一个登录令牌
        String token = UUID.randomUUID().toString(true);

        //5.2 将user 转换成hashMap存储
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),
                //往Redis中写入用户数据时，有一个问题就是 使用的是StringRedisTemplate，要求写入key value 都是String,这里id是long
                //所以这这里需要对 key 和value做自定义
                CopyOptions.create().setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) ->
                                fieldValue.toString()));
        //5.3存储
        String tokenKey = LOGIN_USER_KEY + token;
        stringRedisTemplate.opsForHash().putAll(tokenKey,userMap);
        //5.4设置token有效期
        stringRedisTemplate.expire(tokenKey,LOGIN_USER_TTL, TimeUnit.MINUTES);

        return Result.ok(token);
    }

    //根据id查询用户
    @Override
    public Result queryById(Long id) {
        //select * from tb_user where id = ?
        User user = query().eq("id", id).one();
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);
        return Result.ok(userDTO);
    }

    // 将当前用户当天签到信息保存到Redis中
    @Override
    public Result sign() {
        //获取当前用户信息
        UserDTO user = UserHolder.getUser();

        //获取当前日期
        LocalDateTime now = LocalDateTime.now();
        String dateStr = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));

        //签到信息key
        String key = USER_SIGN_KEY + user.getId() + dateStr;

        // 4.获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();
        //存入redis
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1,true);

        return Result.ok();
    }
    //统计当前用户截止当前时间在本月的连续签到天数
    @Override
    public Result signCount() {

        //获取当前用户信息
        UserDTO user = UserHolder.getUser();

        //获取当前日期
        LocalDateTime now = LocalDateTime.now();
        String dateStr = now.format(DateTimeFormatter.ofPattern(":yyyyMM"));

        //签到信息key
        String key = USER_SIGN_KEY + user.getId() + dateStr;

        //获取今天是本月的第几天
        int dayOfMonth = now.getDayOfMonth();

        //获取本月的签到数据
//        BITFIELD key GET u[dayOfMonth] 0
        List<Long> dataList = stringRedisTemplate.opsForValue().bitField(key,
                BitFieldSubCommands.create()
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth))
                        .valueAt(0));
        if (dataList == null || dataList.isEmpty()){
            //没有任何签到结果
            return Result.ok(0);
        }
        Long signData = dataList.get(0);
        if (signData == null || signData == 0){
            return Result.ok(0);
        }
        //循环遍历
        int count = 0;
        while (true){
            //让这个数字与1做与运算，得到数字的最后一个bit位  // 判断这个bit位是否为0
            if ((signData & 1) == 0){  //因为1只有遇见1 才是1，其他数字都是0
                // 如果为0，说明未签到，结束
                break;
            }else {
                // 如果不为0，说明已签到，计数器+1
                count++;
            }
//            把数字右移一位，抛弃最后一个bit位，继续下一个bit位
            signData >>>= 1;  //无符号位右移
        }
        return Result.ok(count);
    }

    //登录注销
    @Override
    public Result logout(HttpServletRequest request) {

        UserHolder.removeUser();

        String token = request.getHeader("authorization");

        Boolean isSuccess = stringRedisTemplate.delete(LOGIN_USER_KEY + token);
        return Boolean.TRUE.equals(isSuccess) ? Result.ok("注销成功") : Result.fail("注销失败！");
    }

    private User createUserWithPhone(String phone) {
        User user = new User();
        user.setPhone(phone);
        user.setNickName("user_"+RandomUtil.randomString(10));
        //保存新用户
        // insert into tb_user(phone, nick_name) values(#{phone},#{nick_name})
        save(user);

        return user;
    }
}
