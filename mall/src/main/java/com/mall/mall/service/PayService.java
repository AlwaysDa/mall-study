package com.mall.mall.service;

import com.mall.mall.common.BusinessException;
import com.mall.mall.dto.PayCallbackDTO;
import com.mall.mall.entity.Order;
import com.mall.mall.vo.PayResultVO;
import com.mall.mall.vo.PayStatusVO;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 模拟支付服务。
 *
 * 复刻真实支付的三段链路：发起支付（生成支付单）→ 第三方回调通知 → 更新订单状态。
 * 差别只在于「第三方」被换成了我们自己的模拟接口，跳过了签名校验。
 *
 * 三个核心设计点（详见阶段七文档第五节）：
 *   1. 幂等 —— 回调可能被重复调用，已支付的订单必须直接忽略重复回调
 *   2. 状态机 —— 每次流转前都校验「当前状态是否允许流转」
 *   3. 一致性 —— 同一事务里同时更新 pay_status 和 status，避免两处状态打架
 */
@Service
public class PayService {

    private final OrderService orderService;

    public PayService(OrderService orderService) {
        this.orderService = orderService;
    }

    /**
     * 发起支付（模拟）。
     *
     * 真实场景这里会调用第三方接口生成支付单、拿到收银台链接；
     * 我们直接拼一个假的支付单号和链接，并把订单置为「支付中」。
     */
    public PayResultVO createPay(Long orderId) {
        Order order = orderService.getById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        // 状态机：只有「待支付」订单能发起支付
        // （已支付订单的 status 已经是 1、已取消是 2，都会被这一条拦下）
        if (order.getStatus() != 0) {
            throw new BusinessException("只有待支付订单才能发起支付");
        }

        // 生成模拟支付单号（真实场景由第三方返回）
        String payNo = "PAY" + System.currentTimeMillis();
        // 真实场景：这里调用第三方支付接口，拿到支付链接
        String mockPayUrl = "https://pay.example.com/mock?payNo=" + payNo;

        order.setPayStatus(1);   // 支付中
        orderService.updateById(order);

        PayResultVO vo = new PayResultVO();
        vo.setOrderId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setPayNo(payNo);
        vo.setPayUrl(mockPayUrl);
        vo.setAmount(order.getTotalAmount());
        return vo;
    }

    /**
     * 模拟支付回调（核心）。
     *
     * 真实场景：第三方支付成功后会回调我们一个地址，我们要验签后再改订单。
     * 这里不验签（学习期从简），但幂等与状态机校验一步都不能少。
     */
    @Transactional(rollbackFor = Exception.class)
    public void handleCallback(PayCallbackDTO dto) {
        // 1. 根据订单ID找到订单（真实场景用支付单号/订单号定位）
        Order order = orderService.getById(dto.getOrderId());
        if (order == null) {
            throw new BusinessException("订单不存在");
        }

        // 2. 幂等校验：已支付的订单不能重复处理
        //    支付平台会在没收到我们的成功响应时反复重试回调，这一步保证重复回调不出事
        if (order.getPayStatus() != null && order.getPayStatus() == 2) {
            return;   // 已支付，直接忽略重复回调
        }
        if (order.getStatus() != null && order.getStatus() == 2) {
            throw new BusinessException("订单已取消，无法支付");
        }

        // 3. 根据回调结果更新状态（订单状态与支付状态在同一事务里一起改，保证一致）
        if (Boolean.TRUE.equals(dto.getSuccess())) {
            order.setPayStatus(2);   // 已支付
            order.setStatus(1);      // 订单已支付
        } else {
            order.setPayStatus(3);   // 支付失败
            // 订单回到待支付状态，可重新发起支付
            order.setStatus(0);
        }
        orderService.updateById(order);
    }

    /**
     * 查询某订单的支付状态。
     */
    public PayStatusVO getPayStatus(Long orderId) {
        Order order = orderService.getById(orderId);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        PayStatusVO vo = new PayStatusVO();
        vo.setOrderId(order.getId());
        vo.setOrderNo(order.getOrderNo());
        vo.setTotalAmount(order.getTotalAmount());
        vo.setStatus(order.getStatus());
        vo.setPayStatus(order.getPayStatus());
        return vo;
    }
}
