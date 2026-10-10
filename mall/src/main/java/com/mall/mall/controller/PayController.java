package com.mall.mall.controller;

import com.mall.mall.common.Result;
import com.mall.mall.dto.PayCallbackDTO;
import com.mall.mall.service.PayService;
import com.mall.mall.vo.PayResultVO;
import com.mall.mall.vo.PayStatusVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 模拟支付接口。
 *
 * 注意 /callback 与 /{orderId} 都是 POST：
 * Spring 会优先匹配精确路径 /callback，其余才落到 /{orderId} 模板上，二者不冲突。
 * （和阶段五 /page 与 /{id} 是同一套匹配规则）
 */
@RestController
@RequestMapping("/api/pay")
public class PayController {

    private final PayService payService;

    public PayController(PayService payService) {
        this.payService = payService;
    }

    // 发起支付（返回模拟支付单号 + 假收银台链接，订单置为「支付中」）
    @PostMapping("/{orderId}")
    public Result<PayResultVO> createPay(@PathVariable Long orderId) {
        return Result.success(payService.createPay(orderId));
    }

    // 模拟支付回调（第三方支付平台的"通知"打到这里）
    @PostMapping("/callback")
    public Result<Void> callback(@Valid @RequestBody PayCallbackDTO dto) {
        payService.handleCallback(dto);
        return Result.success();
    }

    // 查询支付状态
    @GetMapping("/{orderId}/status")
    public Result<PayStatusVO> status(@PathVariable Long orderId) {
        return Result.success(payService.getPayStatus(orderId));
    }
}
