package com.hmdp.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.hmdp.entity.Voucher;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * <p>
 *  Mapper 接口
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
//继承了BaseMapper,BaseMapper提前写好了很多的curd方法,简化了单表查询
public interface VoucherMapper extends BaseMapper<Voucher> {

    //自定义方法!!!
    List<Voucher> queryVoucherOfShop(@Param("shopId") Long shopId);
}
