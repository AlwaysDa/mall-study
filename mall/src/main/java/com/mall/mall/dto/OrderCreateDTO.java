package com.mall.mall.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;

/**
 * 下单请求参数。
 *
 * 买家信息用的是「姓名 + 电话」的文本快照，而不是买家 ID：
 * 本项目没有买家表（买家不是登录主体，系统定位是店家后台），
 * 且订单是历史凭证，必须固定下单那一刻的买家信息。
 */
@Data
public class OrderCreateDTO {

    // 店家身份不再从请求体读取：由 JWT 解析后从 request attribute 传入，避免替别人下单

    @NotBlank(message = "买家姓名不能为空")
    private String buyerName;

    private String buyerPhone;      // 可选

    @Valid                          // 嵌套校验：不加这行，下面 OrderItemDTO 里的注解不会生效
    @NotEmpty(message = "订单明细不能为空")
    private List<OrderItemDTO> items;

    @Data
    public static class OrderItemDTO {

        @NotNull(message = "商品ID不能为空")
        private Long productId;

        @NotNull(message = "数量不能为空")
        @Min(value = 1, message = "数量至少为1")
        private Integer quantity;
    }
}
