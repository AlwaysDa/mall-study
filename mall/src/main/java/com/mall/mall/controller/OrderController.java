package com.mall.mall.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.mall.common.Result;
import com.mall.mall.dto.OrderCreateDTO;
import com.mall.mall.service.OrderService;
import com.mall.mall.vo.OrderVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    // 下单（事务：订单 + 明细 + 扣库存）
    @PostMapping
    public Result<OrderVO> create(@Valid @RequestBody OrderCreateDTO dto) {
        return Result.success(orderService.createOrder(dto));
    }

    // 订单详情（含明细列表）
    @GetMapping("/{id}")
    public Result<OrderVO> get(@PathVariable Long id) {
        return Result.success(orderService.getOrderDetail(id));
    }

    // 订单分页（可按店家、状态过滤）
    @GetMapping("/page")
    public Result<Page<OrderVO>> page(@RequestParam(required = false) Long merchantId,
                                      @RequestParam(required = false) Integer status,
                                      @RequestParam(defaultValue = "1") Integer pageNum,
                                      @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(orderService.page(merchantId, status, pageNum, pageSize));
    }

    // 取消订单（仅待支付可取消，取消后回补库存）
    @PutMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id) {
        orderService.cancelOrder(id);
        return Result.success();
    }
}
