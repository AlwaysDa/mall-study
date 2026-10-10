package com.mall.mall.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 支付状态查询结果。
 *
 * 只暴露「状态查询」需要的字段：订单状态 + 支付状态 + 金额，
 * 不把整个订单（含买家信息、明细）吐出去。
 *
 * 注意区分两个状态：
 *   status    订单状态：0待支付 1已支付 2已取消
 *   payStatus 支付状态：0未支付 1支付中 2已支付 3支付失败
 * 两者同时更新（在同一个事务里），但含义不同 —— 订单是"业务单据"，支付是"资金动作"。
 */
@Data
public class PayStatusVO {

    private Long orderId;
    private String orderNo;
    private BigDecimal totalAmount;   // 应付金额
    private Integer status;           // 订单状态
    private Integer payStatus;        // 支付状态
}
