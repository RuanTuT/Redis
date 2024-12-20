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
@Slf4j//使用 @Slf4j 注解后，Lombok 会在编译时自动生成一个名为 log 的日志记录器字段，这样你就可以直接在类中使用 log 变量来记录日志，而无需手动创建日志记录器。
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

        //token过期重新登录,但能在数据库中查到用户信息,则不需要在数据库中创建新数据
        //4.用户信息不存在，创建该用户信息
        if (user == null){
            user = createUserWithPhone(phone);
        }

        //不用session,而用redis,实现了session的共享
        /*5.保存用户信息到session 中
        session.setAttribute("user", BeanUtil.copyProperties(user, UserDTO.class));*/


        //5.保存用户信息到redis中
        //5.1 为每个用户信息 随机生成一个登录令牌
        String token = UUID.randomUUID().toString(true);

        //5.2 将user 转换成hashMap存储
        //copyProperties 方法要求源对象和目标对象的属性名称和类型必须匹配。如果属性名称或类型不匹配，该属性将不会被复制。
        UserDTO userDTO = BeanUtil.copyProperties(user, UserDTO.class);//用userDTO将user封装起来,只保留特定几个属性,防止user的信息被泄露出去
        //这个方法可以将对象的属性名称作为键，属性值作为值，存储在一个 Map 中。
        Map<String, Object> userMap = BeanUtil.beanToMap(userDTO, new HashMap<>(),//hashmap保存着一个用户的各种信息的键值对
                //往Redis中写入用户数据时，有一个问题就是 使用的是StringRedisTemplate，要求写入key value 都是String,这里id是long
                //所以这这里需要对 key 和value做自定义
                CopyOptions.create().setIgnoreNullValue(true)
                        .setFieldValueEditor((fieldName, fieldValue) ->
                                fieldValue.toString()));
        //5.3存储
        String tokenKey = LOGIN_USER_KEY + token;
        //第一次登录时,记录以tokenKey登录令牌为键的用户信息值
        stringRedisTemplate.opsForHash().putAll(tokenKey,userMap);
        //5.4设置token有效期
        stringRedisTemplate.expire(tokenKey,LOGIN_USER_TTL, TimeUnit.MINUTES);

        return Result.ok(token);//这里返回的token到客户端后,之后客户端的请求的authorization都会带有token
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
        //存入redis,对key的值(初始为空字符串)偏移量的位上设置为1
        //Redis中是利用string类型数据结构实现BitMap，因此最大上限是512M，转换为bit则是 2^32个bit位。
        stringRedisTemplate.opsForValue().setBit(key, dayOfMonth - 1,true);//年月的字符串为键，这个月的第几天签到就在对应位置置为1

        return Result.ok();
    }
    //统计当前用户截止当前时间在本月的连续签到天数,即从现在往前遍历的连续签到天数
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
        //返回一个 List<Long>，其中包含读取到的位字段值。每个元素都是一个 Long 类型的值，表示读取到的位字段内容。
        List<Long> dataList = stringRedisTemplate.opsForValue().bitField(key,
                BitFieldSubCommands.create()//创建一个新的 BitFieldSubCommands 对象，用于构建位字段操作命令。
                        .get(BitFieldSubCommands.BitFieldType.unsigned(dayOfMonth))//指定要读取的位字段类型为无符号整数，并且位宽为 dayOfMonth 位。dayOfMonth 应该是一个整数，表示你要读取的位字段的宽度。
                        .valueAt(0));//指定要读取的位字段在字符串中的起始位置。
        if (dataList == null || dataList.isEmpty()){
            //没有任何签到结果
            return Result.ok(0);
        }
        //dataList只包含一个元素！！！
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
            signData >>>= 1;  //无符号位右移，注意无符号右移是>>>
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
