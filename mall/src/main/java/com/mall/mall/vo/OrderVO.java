package com.mall.mall.vo;

import com.mall.mall.entity.OrderItem;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 订单展示对象：字段与 Order 实体一致，额外带一个订单明细列表。
 *
 * items 只在「订单详情」接口里填充；分页列表不查明细，保持为 null，
 * 避免为了列表多做 N 次查询。
 */
@Data
public class OrderVO {

    private Long id;
    private String orderNo;
    private Long merchantId;
    private String buyerName;
    private String buyerPhone;
    private BigDecimal totalAmount;
    private Integer status;        // 0待支付 1已支付 2已取消
    private Integer payStatus;     // 0未支付 1已支付
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    private List<OrderItem> items; // 订单明细
}
