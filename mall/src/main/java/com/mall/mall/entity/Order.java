package com.mall.mall.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 订单实体，对应表 `order`。
 *
 * 注意：order 是 SQL 关键字，@TableName 里必须用反引号包裹，
 * 否则 MyBatis-Plus 生成的 SQL 会报语法错误（见阶段六「常见坑」）。
 *
 * 买家信息（buyerName / buyerPhone）是下单那一刻的文本快照，不是买家 ID ——
 * 本项目没有买家表，且订单是历史凭证，必须固定当时的买家信息。
 */
@Data
@TableName("`order`")
public class Order {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String orderNo;          // 订单号（唯一索引 uk_order_no）
    private Long merchantId;         // 所属店家
    private String buyerName;        // 买家姓名（快照）
    private String buyerPhone;       // 买家电话（快照）
    private BigDecimal totalAmount;  // 订单总额
    private Integer status;          // 0待支付 1已支付 2已取消
    private Integer payStatus;       // 0未支付 1已支付

    @TableField(fill = FieldFill.INSERT)
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)
    private LocalDateTime updateTime;
}
