package com.hmdp;

import com.hmdp.entity.Shop;
import com.hmdp.service.impl.ShopServiceImpl;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.geo.Point;
import org.springframework.data.redis.connection.RedisGeoCommands;
import org.springframework.data.redis.core.StringRedisTemplate;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.hmdp.utils.RedisConstants.SHOP_GEO_KEY;

@SpringBootTest
class HmDianPingApplicationTests {

    @Resource
    private ShopServiceImpl shopService;

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    /*@Test
    public void testSaveShop() {
        service.rebuildShopCache(1L,20L);
    }*/

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

