package com.mall.mall.vo;

import lombok.Data;

import java.math.BigDecimal;

/**
 * 发起支付的返回结果（模拟）。
 *
 * 真实场景：调用第三方支付接口后拿到「支付单号 + 收银台跳转链接」，
 * 前端拿 payUrl 跳过去让用户付款。学习期这两个值都是我们拼出来的假数据，
 * 但字段和真实流程保持一致，方便以后替换成真实支付。
 */
@Data
public class PayResultVO {

    private Long orderId;         // 订单ID
    private String orderNo;       // 订单号（方便对账时定位）
    private String payNo;         // 模拟支付单号
    private String payUrl;        // 模拟收银台链接
    private BigDecimal amount;    // 应付金额（取订单总额）
}
