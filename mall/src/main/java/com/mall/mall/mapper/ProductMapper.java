package com.mall.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.mall.entity.Product;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface ProductMapper extends BaseMapper<Product> {
    // 继承 BaseMapper 后，单表 CRUD 全都自动拥有，无需写任何方法

    /**
     * 原子扣减库存（防超卖）：WHERE 里带 stock >= quantity，
     * 并发下也不会把库存扣成负数。
     *
     * @return 受影响行数：1 = 扣减成功，0 = 库存不足
     */
    @Update("UPDATE product SET stock = stock - #{quantity} " +
            "WHERE id = #{productId} AND stock >= #{quantity}")
    int deductStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);

    /**
     * 回补库存（取消订单时用）。
     *
     * @return 受影响行数
     */
    @Update("UPDATE product SET stock = stock + #{quantity} WHERE id = #{productId}")
    int addStock(@Param("productId") Long productId, @Param("quantity") Integer quantity);
}