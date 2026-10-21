package com.mall.mall.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.mall.common.Result;
import com.mall.mall.config.OpenApiConfig;
import com.mall.mall.dto.OrderCreateDTO;
import com.mall.mall.interceptor.AuthInterceptor;
import com.mall.mall.service.OrderService;
import com.mall.mall.vo.OrderVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "订单管理", description = "下单、查询、取消订单")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME)
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @Operation(summary = "创建订单", description = "事务内创建订单、明细并扣减库存；店家身份取自 JWT")
    @PostMapping
    public Result<OrderVO> create(@RequestAttribute(AuthInterceptor.ATTR_MERCHANT_ID) Long merchantId,
                                  @Valid @RequestBody OrderCreateDTO dto) {
        return Result.success(orderService.createOrder(merchantId, dto));
    }

    @Operation(summary = "查询订单详情", description = "返回订单及其明细列表；只能查看自己店家的订单")
    @GetMapping("/{id}")
    public Result<OrderVO> get(@PathVariable Long id,
                               @RequestAttribute(AuthInterceptor.ATTR_MERCHANT_ID) Long merchantId) {
        return Result.success(orderService.getOrderDetail(id, merchantId));
    }

    @Operation(summary = "分页查询订单", description = "自动按当前店家过滤，可按状态过滤")
    @GetMapping("/page")
    public Result<Page<OrderVO>> page(@RequestAttribute(AuthInterceptor.ATTR_MERCHANT_ID) Long merchantId,
                                      @RequestParam(required = false) Integer status,
                                      @RequestParam(defaultValue = "1") Integer pageNum,
                                      @RequestParam(defaultValue = "10") Integer pageSize) {
        return Result.success(orderService.page(merchantId, status, pageNum, pageSize));
    }

    @Operation(summary = "取消订单", description = "仅待支付订单可取消，取消后回补库存；只能操作自己的订单")
    @PutMapping("/{id}/cancel")
    public Result<Void> cancel(@PathVariable Long id,
                               @RequestAttribute(AuthInterceptor.ATTR_MERCHANT_ID) Long merchantId) {
        orderService.cancelOrder(id, merchantId);
        return Result.success();
    }
}
