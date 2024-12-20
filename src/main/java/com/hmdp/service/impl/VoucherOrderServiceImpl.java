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
import org.springframework.context.annotation.Lazy;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.time.Duration;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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

    @Resource
    //其中一种获取代理对象的方法，注入自身的代理对象，从而调用加了事务注解的方法
    //你需要在一个方法中调用另一个带有 @Transactional 注解的方法(createVoucherOrder方法)。你可以通过 @Autowired 注入自身代理对象来实现这一点。
    //有了这个就不需要proxy = (IVoucherOrderService) AopContext.currentProxy()了
    @Lazy// @Lazy 延迟注入 避免形成循环依赖
    private IVoucherOrderService proxy;

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
        //实则是new VoucherOrderServiceImpl().new VoucherOrderHandler(voucherOrder);
        SECKILL_ORDER_EXECUTOR.submit(new VoucherOrderHandler());
    }
//这个是内部类,不是函数
    private class VoucherOrderHandler implements Runnable {

        @Override
        public void run() {
            while (true) {
                try {
                    // 1.获取消息队列中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS s1 $/0/>(最新消息，仅关注实时数据，忽略未确认或旧的消息/第一个放入消息队列中的消息，用于读取整个消息流的历史记录，适合进行数据恢复或初始化时读取完整数据流。/从下一个未消费的消息开始，表示只读取未被消费的新消息）
                    //消费组内每个消费者都有自己的 Pending 消息列表,添加到对应消费者的 Pending List 中。
                    //当 XREADGROUP 被调用时：Redis 首先检查 Pending List,有 Pending 消息（未确认的消息），会优先返回这些消息.让这些消息确认,没有则分配新的消费者处理新消息.
                    List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                            Consumer.from("g1", "c1"),//Redis 在 Pending List 中记录每条消息的状态，包括：消息 ID。分配的消费者（如 c1）。
                            StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                            StreamOffset.create("stream.orders", ReadOffset.lastConsumed())//从存储的位点继续读取!!!
                    );
                    // 2.判断订单信息是否为空
                    if (list == null || list.isEmpty()) {
                        // 如果为null，说明没有消息，继续下一次循环
                        continue;
                    }
                    // 解析数据  record<String,Map<Object,Object>> getvalue就是获得Map
                    //消息Id直接附属于 MapRecord 对象，用来标识这条消息,通过getId获得.
                    MapRecord<String, Object, Object> record = list.get(0);//获得第一条消息
                    Map<Object, Object> value = record.getValue();//voucherOrder的各个属性对
                    VoucherOrder voucherOrder = BeanUtil.fillBeanWithMap(value, new VoucherOrder(), true);
                    // 3.创建订单
                    //编译器会为内部类生成一个对外部类实例的引用。由于 handleVoucherOrder 是外部类的方法，内部类会通过持有的外部类引用隐式调用这个方法。
                    handleVoucherOrder(voucherOrder);
                    // 4.确认消息 XACK,Redis 的消息确认 (acknowledge) 是基于 消费组 的，而不是具体的消费者。
                    System.out.println(stringRedisTemplate.opsForStream().acknowledge(Objects.requireNonNull(record.getStream()), "g1", record.getId()));
                } catch (Exception e) {
                    log.error("处理订单异常", e);
                    //处理异常消息,发生了异常说明消息有可能在读取过程后为正常ACK,所以需要处理pending消息
                    handlePendingList();
                }
            }
        }
         //当读取消息时，如果未调用 acknowledge 方法确认消息，消息会留在消费组的 Pending List 中。
         //如果超时未确认，Redis 可能会将消息重新分配给该消费组的其他消费者（如 c2）。
        // Redis 在 Pending List 中记录每条消息的状态，包括：消息 ID。分配的消费者（如 c1）。消息的读取时间。如果消息未被确认，Redis 会继续保留它在 Pending List 中。
        private void handlePendingList() {
            while (true) {
                try {
                    // 1.获取pending-list中的订单信息 XREADGROUP GROUP g1 c1 COUNT 1 BLOCK 2000 STREAMS s1 0
                    //消费者调用 XREADGROUP，Redis 优先返回 Pending 消息。
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
                    //stringRedisTemplate.opsForStream().acknowledge("s1", "g1", record.getId());
                    stringRedisTemplate.opsForStream().acknowledge(Objects.requireNonNull(record.getStream()), "g1", record.getId());
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
//获取可重入锁,可重入指的是同一线程获取锁再次获取锁,不会死锁!!!而这里同一线程中没有再次获取锁的逻辑,所以没有可重入的情况发生.如果另一个线程这时也是获得的是这个userId的锁,那是不会获得锁的.但如果获得的是另一个userId的锁,那就能获取
        RLock lock = redissonClient.getLock("lock:order:" + userId);
        boolean flag = lock.tryLock();
        //这里加上分布式锁只能保证一个用户只在一个时刻下单一次,其他用户这个时刻下单也是可以的,因为锁的是userId。但不能保证数据库写入的一致性和处理数据库操作中的回滚需求。
        if (!flag){
            //获取锁失败
            log.error("不允许重复下单！");//是同一时刻不允许重复下单！
            return;  //异步处理 不用返回给前端什么
        }
        try {
            //获取代理对象（和事务相关的）
            //保证数据库操作的事务性,保证数据库写入的一致性和处理数据库操作中的回滚需求。
            //Spring AOP 使用动态代理来实现事务的管理，它会在运行的时候为带有 @Transactional 注解的方法生成代理对象，并在方法调用的前后应用事务逻辑。
            // 如果该方法被其他类调用,我们的代理对象就会拦截方法调用并处理事务。但是在一个类中的其他方法内部调用的时候，我们代理对象就无法拦截到这个内部调用，因此事务也就失效了。
            //或者用IVoucherOrderService proxy = (IVoucherOrderService) AopContext.currentProxy();并且@EnableAspectJAutoProxy(exposeProxy = true) .因为使用了 AopContext.currentProxy() 方法来获取当前类的代理对象，然后通过代理对象调用方法,这样就相当于从外部调用了方法，所以事务注解才会生效。
            proxy.createVoucherOrder(voucherOrder);   //将该方法交由spring管理
            //这里存在一个事务失效问题  调用下面创建订单的方法 默认是用this调的，而this这里就是VoucherOrderServiceImpl，而不是代理对象
            //而spring的事务要想生效，是spring对当前VoucherOrderServiceImpl对象做了动态代理，用代理对象做的事务处理
        } finally {
            //释放锁
            lock.unlock();
        }
    }

    // 基于Redis的Stream结构作为消息队列，实现异步秒杀下单
    @Override
    public Result seckillVoucher(Long voucherId) {
        //获取用户id
        Long userId = UserHolder.getUser().getId();
        //获取订单id
        long orderId = redisIdWorker.nextId("order");
        //1.执行lua脚本 判断秒杀库存是否够、一人一单，决定用户是否抢购成功,原子性操作.
        //秒杀劵库存会在添加劵时保存在redis中
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
        //获取代理对象,为了避免循环依赖,在方法内部需要根据条件动态获取代理对象时。
        //这是为了解决自调用问题存在的,有了这个就不需要注入IVoucherOrderService代理对象了!!!!
        //proxy = (IVoucherOrderService) AopContext.currentProxy();
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

    //仅保证数据库操作的事务性,保证数据库写入的一致性和处理数据库操作中的回滚需求。但不限制线程对数据库的并发行为,未提交事务时另一个线程只会读取以前的快照
    //如果一个类或者一个类中的 public 方法上被标注@Transactional 注解的话，Spring 容器就会在启动的时候为其创建一个代理类，在调用被@Transactional 注解的 public 方法的时候，实际调用的是，TransactionInterceptor 类中的 invoke()方法。
    // 这个方法的作用就是在目标方法之前开启事务，方法执行过程中如果遇到异常的时候回滚事务，方法调用完成之后提交事务。
    //@Transactional注解默认使用就是这个事务传播行为PROPAGATION_REQUIRED,如果外部存在事务，则内部加入该事务；如果外部没有事务，则创建一个新的事务。
    @Transactional
    //这个方法上有了@Transactional,说明通过切面拦截方法调用，在方法开始前开启事务.在方法调用时，Spring 会通过 AOP 拦截调用，并将事务信息存储到 ThreadLocal。
    //同一个线程中的方法调用都能访问到 ThreadLocal 中的事务信息，因此事务能够正确传播。开启一个新线程就不行了.因为事务范围局限于当前线程，无法扩展到其他线程。
    //@Async//这个注解的作用,表明这个方法是异步任务,异步任务通常是由框架自动管理线程的生命周期
    public void createVoucherOrder(VoucherOrder voucherOrder) {
        //5. 一人一单
        // 根据优惠券id 和用户id 查询订单
        Long userId = voucherOrder.getUserId();
            //查询的是tb_voucher_order表
            Integer count = query().eq("user_id", userId).eq("voucher_id", voucherOrder.getVoucherId()).count();
            if (count > 0) {
                // 该订单存在存在 直接返回异常
                log.error("你已经下过单了！");
                return;
            }
            //乐观锁解决超卖问题,乐观锁比较适合更新数据，而但如果是插入数据，所以我们需要使用悲观锁操作,这里使用了事务Transactional
            // 订单不存在  扣减库存。
        //乐观锁先读取stock是否大于0，然后stcok减一，然后用新stock提交到数据库更新，但是在提交前还会判断stock是否大于0，如不大于0，则失败。
            boolean success = seckillVoucherService//操作的是tb_seckill_voucher表
                    .update()
                    .setSql("stock = stock - 1 ")//减一的操作目前还不是在数据库中进行的
                    .eq("voucher_id", voucherOrder.getVoucherId())
                    .gt("stock", 0)//乐观锁,先扣除库存（未在数据库中扣除）,再判断.然后提交更新的数据。
                    .update();//这里是提交更新后的stock数据，提交时会判断gt("stock", 0)条件
            if (!success) {
                log.error("库存不足！");
                return;
            }
            save(voucherOrder);

        }

    }


