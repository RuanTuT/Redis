package com.hmdp;

import cn.hutool.core.bean.BeanUtil;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.Shop;
import com.hmdp.entity.Voucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.service.impl.ShopServiceImpl;
import com.hmdp.service.impl.VoucherServiceImpl;
import com.hmdp.utils.RedisIdWorker;
import org.junit.jupiter.api.Test;
import org.redisson.api.RLock;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.connection.stream.*;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;

import javax.annotation.Resource;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

//MockMvc 由org.springframework.boot.test包提供，实现了对Http请求的模拟，一般用于我们测试 controller 层。
@AutoConfigureMockMvc(addFilters = false)
@SpringBootTest
class HmDianPingApplicationTests {
    @Autowired
    MockMvc mockMvc;



    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @Autowired
    private RedisIdWorker redisIdWorker;

    @Resource
    private RedissonClient redissonClient;

    @Resource
    private VoucherServiceImpl voucherService;

    @Test
    void should_return_400_if_param_not_valid() throws Exception {
        mockMvc.perform(get("/api/illegalArgumentException"))
                .andExpect(status().is(400))
                .andExpect( jsonPath("$.message").value("参数错误!"));
    }
    /*@Test
    public void testSaveShop() {
        service.rebuildShopCache(1L,20L);
    }*/
    @Test
    void testStream(){
        List<MapRecord<String, Object, Object>> list = stringRedisTemplate.opsForStream().read(
                Consumer.from("g1", "c1"),//Redis 在 Pending List 中记录每条消息的状态，包括：消息 ID。分配的消费者（如 c1）。
                StreamReadOptions.empty().count(1).block(Duration.ofSeconds(2)),
                StreamOffset.create("stream.orders", ReadOffset.lastConsumed())//从存储的位点继续读取!!!
        );
        // 2.判断订单信息是否为空
        if (list == null || list.isEmpty()) {
            System.out.println("没有消息");
            return;
        }
        // 解析数据  record<String,Map<Object,Object>> getvalue就是获得Map
        //消息Id直接附属于 MapRecord 对象，用来标识这条消息,通过getId获得.
        MapRecord<String, Object, Object> record = list.get(0);//获得第一条消息
         //4.确认消息 XACK,Redis 的消息确认 (acknowledge) 是基于 消费组 的，而不是具体的消费者。
        System.out.println(stringRedisTemplate.opsForStream().acknowledge(Objects.requireNonNull(record.getStream()), "g1", record.getId()));
    }
    @Test
    void testaddSeckillVoucher() throws Exception {
//        Voucher voucher = new Voucher();
//        voucher.setId(20L);
//        Voucher voucher=voucherService.testSeckillVoucher(1L);
//        voucher.setStock(3);
//        LocalDateTime BeginTime = LocalDateTime.of(2024,11,25,00,00,00);
//        LocalDateTime EndTime = LocalDateTime.of(2024,11,25,23,59,59);
//        voucher.setBeginTime(BeginTime);
//        voucher.setEndTime(EndTime);
//        voucherService.addSeckillVoucher(voucher);

    }
    @Test
    void testIdWorker() throws InterruptedException {
        CountDownLatch latch = new CountDownLatch(300);
        ExecutorService es= Executors.newFixedThreadPool(300);
        Runnable task = () -> {
            for (int i = 0; i < 10; i++) {
                long id = redisIdWorker.nextId("order");
                System.out.println("id = " + id);
            }
            latch.countDown();//一个分线程生成一百个id，分线程执行完则减1
        };
        long begin = System.currentTimeMillis();
        for (int i = 0; i < 300; i++) {
            es.submit(task);
        }
        latch.await();
        long end = System.currentTimeMillis();
        System.out.println("time = " + (end - begin));
    }

    @Test
    void testRedisson() throws Exception{
        //获取锁(可重入)，指定锁的名称
        RLock lock = redissonClient.getLock("anyLock");
        //尝试获取锁，参数分别是：获取锁的最大等待时间(期间会重试)，锁自动释放时间，时间单位
        //boolean tryLock(long waitTime, long leaseTime, TimeUnit unit) throws //InterruptedException：尝试获取锁，在指定的等待时间内重试，如果成功则持有锁一段时间（leaseTime），等待
        //时间期间会阻塞线程。
        boolean isLock = lock.tryLock(1,10, TimeUnit.SECONDS);

        //判断获取锁成功
        if(isLock){
            try{
                System.out.println("执行业务");
            }finally{
                //释放锁
                lock.unlock();
            }

        }
    }


    /**
     * @description TODO  查询数据库中商铺数据 将商铺数据存入 redis 的geo数据类型中
     *                      key用 shopID 用于区分商铺类型, 值传经纬度 和 manber shopId
     * @param
     * @return  void
    */
    @Test
    void loadShopData() {

        //直接遍历 存入redis
        /*List<Shop> list = shopService.list();
        for (Shop shop : list) {
            Long typeId = shop.getTypeId();
            String key = SHOP_GEO_KEY + typeId;
            stringRedisTemplate.opsForGeo()
                    .add(key, new Point(shop.getX(),shop.getY()), String.valueOf(shop.getId()));
        }*/


        //使用Steam 流   存入时 创建对象 一次性存入redis  效率高

        // 1.查询店铺信息
        List<Shop> list = shopService.list();
        // 2.把店铺分组，按照typeId分组，typeId一致的放到一个集合
        Map<Long, List<Shop>> map = list.stream()
                .collect(Collectors.groupingBy(Shop::getTypeId));
        // 3.分批完成写入Redis
        for (Map.Entry<Long, List<Shop>> mapEntry : map.entrySet()) {
            // 3.1.获取类型id
            Long typeId = mapEntry.getKey();
            String key = SHOP_GEO_KEY + typeId;
            // 3.2.获取同类型的店铺的集合
            List<Shop> shopList = mapEntry.getValue();
            List<RedisGeoCommands.GeoLocation<String>> locations = new ArrayList<>();
            // 3.3.写入redis GEOADD key 经度 纬度 member
            //Redis 使用 GeoHash 将经纬度转换为 52 位的整数，作为 Sorted Set 中的 score。
            for (Shop shop : shopList) {
                locations.add(new RedisGeoCommands.GeoLocation<>(
                        shop.getId().toString(),   //member
                        new Point(shop.getX(),shop.getY()) //经纬度
                ));
            }
            stringRedisTemplate.opsForGeo()
                    .add(key,locations);
        }
    }

//    UV统计-测试百万数据的统计

//    向HyperLogLog中添加100万条数据
    //HyperLogLog 是一种用于基数估计的数据结构，它可以在非常小的内存空间内估计集合中不重复元素的数量。
    //HyperLogLog 的数据存储格式是二进制的，但可以通过 Redis 命令将其转换为十六进制（hex）字符串进行查看和传输。
    //HyperLogLog 在 Redis 内部是以二进制格式存储的。它使用一个固定大小的数组来存储数据，这个数组的大小通常是 12k 字节
    //这个估算的基数并不一定准确，是一个带有 0.81% 标准错误的近似值（对于可以接受一定容错的业务场景，比如IP数统计，UV等，是可以忽略不计的）。
    @Test
    void addDataTest() {
        String[] user = new String[1000];
        int j = 0;
        for (int i = 1; i <= 1000000; i++) {
            user[j++] = "user_"+ i;
            //每1000条发送一次
            if (i % 1000 == 0){
                j = 0;
                stringRedisTemplate.opsForHyperLogLog().add("hll1",user);
            }

        }
        Long count = stringRedisTemplate.opsForHyperLogLog().size("hll1");
        System.out.println(count);
    }
}

