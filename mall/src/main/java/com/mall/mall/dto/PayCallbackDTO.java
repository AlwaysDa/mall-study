package com.mall.mall.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * 模拟支付回调的入参。
 *
 * 真实场景里这个请求由第三方支付平台（微信/支付宝）发过来，
 * 携带支付单号、金额、签名等；学习期简化为「订单ID + 支付结果」两个字段。
 */
@Data
public class PayCallbackDTO {

    @NotNull(message = "订单ID不能为空")
    private Long orderId;

    @NotNull(message = "支付结果不能为空")
    private Boolean success;   // true=支付成功 false=支付失败
}
