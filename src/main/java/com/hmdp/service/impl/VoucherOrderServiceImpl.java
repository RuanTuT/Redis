package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.RedisIdWorker;
import com.hmdp.utils.UserHolder;
import lombok.extern.slf4j.Slf4j;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.aop.framework.AopContext;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Slf4j
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    private ISeckillVoucherService seckillVoucherService;

    @Resource
    private RedisIdWorker redisIdWorker;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Resource
    private RedissonClient redissonClient;



    //加载lua脚本，当类被加载到JVM时，静态初始化块会被执行一次，且仅执行一次。
    // 这个DefaultRedisScript对象在类被加载到JVM时就被初始化，并且之后不能被重新赋值。
    private static final DefaultRedisScript<Long> SECKILL_SCRIPT;
    //这是一个静态初始化块，它在类被加载到 JVM 时执行，并且只会执行一次。
    // 在静态初始化块中，我们可以进行静态变量的初始化等操作。
    static {
        SECKILL_SCRIPT = new DefaultRedisScript<>();
        SECKILL_SCRIPT.setLocation(new ClassPathResource("seckill.lua"));
        SECKILL_SCRIPT.setResultType(Long.class);
    }

    //异步处理线程池
    private static final ExecutorService SECKILL_ORDER_EXECUTOR = Executors.newSingleThreadExecutor();

    //在类初始化之后执行，因为当这个类初始化好了之后，随时都是有可能要执行的
    @PostConstruct
    private void init() {
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }

    private class VoucherOrderHandler implements Runnable {

        @Override
        public void run() {
            while (true) {
                try {
                    // 1.获取消息队列中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS s1 >
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create("stream.orders", ReadOffset.lastConsumed())
                    );
                    // 2.判断订单信息是否为空
                    if (list == null || list.isEmpty()) {
                        // 如果为null，说明没有消息，继续下一次循环
                        continue;
                    }
                    // 解析数据
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    // 3.创建订单
                    handleVoucherOrder(voucherOrder);
                    // 4.确认消息 XACK
                    stringRedisTemplate.opsForStream().acknowledge("s1", "g1", record.getId());
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                    //处理异常消息
                    handlePendingList();
                }
            }
        }

        private void handlePendingList() {
            while (true) {
                try {
                    // 1.获取pending-list中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS s1 0
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),
                            StreamReadOptions.empty().count(1),
                            StreamOffset.create("stream.orders", ReadOffset.from("0"))
                    );
                    // 2.判断订单信息是否为空
                    if (list == null || list.isEmpty()) {
                        // 如果为null，说明没有异常消息，结束循环
                        break;
                    }
                    // 解析数据
                    MapRecord<String, Object, Object> record = list.get(0);
                    Map<Object, Object> value = record.getValue();
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    // 3.创建订单
                    handleVoucherOrder(voucherOrder);
                    // 4.确认消息 XACK
                    stringRedisTemplate.opsForStream().acknowledge("s1", "g1", record.getId());
                } catch (Exception e) {
                    log.error("处理pendding订单异常", e);
                    try{
                        Thread.sleep(20);
                    }catch(Exception ex){
                        ex.printStackTrace();
                    }
                }
            }
        }
    }

    //从阻塞队列中获取 订单数据 创建订单
    /*//阻塞队列
    private BlockingQueue<VoucherOrder> orderTasks =new ArrayBlockingQueue<>(1024 * 1024);
    private class VoucherOrderHandler implements Runnable {
        @Override
        public void run() {
            while (true){
                try {
                    // 1.获取队列中的订单信息
                    VoucherOrder voucherOrder = orderTasks.take();
                    // 2.创建订单
                    handleVoucherOrder(voucherOrder);
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                }
            }
        }
    }*/

    private void handleVoucherOrder(VoucherOrder voucherOrder) {
        //这里是一个新的线程，不是原来的主线程，所以不能在threadlocal中取到userId
        Long userId = voucherOrder.getUserId();

        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean flag = lock.tryLock();
        if (!flag){
            //获取锁失败
            log.error("不允许重复下单！");
            return;  //异步处理 不用返回给前端什么
        }
        try {
            //获取代理对象（和事务相关的）
            proxy.createVoucherOrder(voucherOrder);   //将该方法交由spring管理
            //这里存在一个事务失效问题  调用下面创建订单的方法 默认是用this调的，而this这里就是VoucherOrderServiceImpl，而不是代理对象
            //而spring的事务要想生效，是spring对当前VoucherOrderServiceImpl对象做了动态代理，用代理对象做的事务处理
        } finally {
            //释放锁
            lock.unlock();
        }
    }

    @Autowired
    //其中一种获取代理对象的方法，注入自身的代理对象，从而调用加了事务注解的方法
    private IVoucherOrderService proxy;

    // 基于Redis的Stream结构作为消息队列，实现异步秒杀下单
    @Override
    public Result seckillVoucher(Long voucherId) {
        //获取用户id
        Long userId = UserHolder.getUser().getId();
        //获取订单id
        long orderId = redisIdWorker.nextId("order");
        //1.执行lua脚本 判断秒杀库存、一人一单，决定用户是否抢购成功
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString() ,String.valueOf(orderId)
        );

        int r = result.intValue();
        //2.判断结果是否为0
        if (r != 0){
            //不为零，代表没有购买资格
            return Result.fail(r == 1 ? "库存不足！" : "重复下单！");
        }
        //有购买资格，把下单信息保存到消息队列  这一步在lua脚本中实现
        //获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        //3.返回订单id
        return Result.ok(orderId);
    }


    //使用阻塞队列实现异步秒杀
    /*@Override
    public Result seckillVoucher(Long voucherId) {
        //获取用户id
        Long userId = UserHolder.getUser().getId();
        //1.执行lua脚本 判断秒杀库存、一人一单，决定用户是否抢购成功
        Long result = stringRedisTemplate.execute(
                SECKILL_SCRIPT,
                Collections.emptyList(),
                voucherId.toString(), userId.toString()
        );

        int r = result.intValue();
        //2.判断结果是否为0
        if (r != 0){
            //不为零，代表没有购买资格
            return Result.fail(r == 1 ? "库存不足！" : "重复下单！");
        }
        //有购买资格，把下单信息保存到阻塞队列
        //  5. 创建订单
        VoucherOrder voucherOrder = new VoucherOrder();
        //5.1 订单id
        long orderId = redisIdWorker.nextId("order");
        voucherOrder.setId(orderId);
        //5.2 用户id
        voucherOrder.setUserId(userId);
        //5.3 代金卷id
        voucherOrder.setVoucherId(voucherId);
        //放入阻塞队列
        orderTasks.add(voucherOrder);

        //获取代理对象
        proxy = (IVoucherOrderService) AopContext.currentProxy();
        //3.返回订单id
        return Result.ok(orderId);
    }*/

    //秒杀卷下单业务  同步的方式
    /*@Override
    public Result seckillVoucher(Long voucherId) {
        //1.查询秒杀优惠卷
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        //2.判断秒杀是否开始
        if (seckillVoucher.getBeginTime().isAfter(LocalDateTime.now())) {
            //2.1.秒杀没有开始 返回异常
            return Result.fail("秒杀活动还没有开始！");
        }
        //2.判断秒杀是否结束
        if (seckillVoucher.getEndTime().isBefore(LocalDateTime.now())) {
            //活动结束
            return Result.fail("秒杀活动已经结束！");
        }

        //3.判断库存是否充足
        if (seckillVoucher.getStock() < 1) {
            //3.1 不充足，返回异常
            return Result.fail("库存不足！");
        }
        //4.库存充足
        //Long userId = UserHolder.getUser().getId();
        //控制 锁粒度  intern() 这个方法是从常量池中拿到数据，
        // 如果我们直接使用userId.toString() 他拿到的对象实际上是不同的对象，new出来的对象，
        // 我们使用锁必须保证锁必须是同一把，所以我们需要使用intern()方法
//        1.synchronized (userId.toString().intern()){   // 替换sync加锁
            //创建锁对象 这个代码不用了，因为我们现在要使用分布式锁
//        2.SimpleRedisLock lock = new SimpleRedisLock("order:" + userId, stringRedisTemplate);
//        3.RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean flag = lock.tryLock();
        if (!flag){
            //获取锁失败
            return Result.fail("不允许重复下单！");
        }
        try {
            //获取代理对象（和事务相关的）
            IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();
            return proxy.createVoucherOrder(voucherId);   //将该方法交由spring管理
            //这里存在一个事务失效问题  调用下面创建订单的方法 默认是用this调的，而this这里就是VoucherOrderServiceImpl，而不是代理对象
            //而spring的事务要想生效，是spring对当前VoucherOrderServiceImpl对象做了动态代理，用代理对象做的事务处理
        } finally {
            //释放锁
            lock.unlock();
        }

    }*/

    @Transactional
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        //5. 一人一单
        // 根据优惠券id 和用户id 查询订单
        Long userId = voucherOrder.getUserId();

            Integer count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
            if (count > 0) {
                // 该订单存在存在 直接返回异常
                log.error("你已经下过单了！");
                return;
            }
            //乐观锁解决超卖问题
            // 订单不存在  扣减库存。
            boolean success = seckillVoucherService
                    .update()
                    .setSql("stock = stock - 1 ")
                    .eq("voucher_id", voucherOrder.getVoucherId())
                    .gt("stock", 0)
                    .update();
            if (!success) {
                log.error("库存不足！");
                return;
            }
            save(voucherOrder);

        }

    }


