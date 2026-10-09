package com.mall.mall.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;

/**
 * 订单明细，对应表 order_item。
 *
 * productName / price 是下单那一刻的「快照」：商品之后改名或改价，
 * 都不会影响已经生成的历史订单（与订单里存 buyerName 是同一个设计原则）。
 *
 * 该表没有 create_time / update_time 字段，所以不需要自动填充注解。
 */
@Data
@TableName("order_item")
public class OrderItem {

    @TableId(type = IdType.AUTO)
    private Long id;

    private Long orderId;          // 所属订单
    private Long productId;        // 商品ID
    private String productName;    // 商品名（快照）
    private BigDecimal price;      // 成交单价（快照）
    private Integer quantity;      // 数量
    private BigDecimal amount;     // 小计 = price * quantity
}
