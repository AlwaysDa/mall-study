package com.mall.mall.controller;

import com.mall.mall.entity.Merchant;
import com.mall.mall.entity.Order;
import com.mall.mall.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.startsWith;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 模拟支付接口测试（docs/11 的 Y-1 ~ Y-10）。
 *
 * 前提：本项目没有独立的支付流水表，支付状态记在 order.pay_status 上，
 * 所以断言的都是订单上的 status / pay_status 两个字段。
 */
@DisplayName("模拟支付接口")
class PayControllerTest extends BaseIntegrationTest {

    private String callbackBody(Long orderId, boolean success) {
        return """
                {"orderId":%d,"success":%s}
                """.formatted(orderId, success);
    }

    /** 新订单：待支付、未支付 */
    private Order pendingOrder(Merchant merchant) {
        return newOrder(merchant.getId(), "100.00", 0, 0);
    }

    @Test
    @DisplayName("Y-1 待支付订单发起支付：返回支付单号与链接，订单置为支付中")
    void 发起支付成功() throws Exception {
        Merchant merchant = newMerchant("shop_a", "测试店铺", "123456");
        Order order = pendingOrder(merchant);

        mockMvc.perform(post("/api/pay/{orderId}", order.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.payNo", startsWith("PAY")))
                .andExpect(jsonPath("$.data.payUrl").isNotEmpty())
                .andExpect(jsonPath("$.data.amount").value(100.00))
                .andExpect(jsonPath("$.data.orderNo").value(order.getOrderNo()));

        Order reloaded = reloadOrder(order.getId());
        assertEquals(1, reloaded.getPayStatus(), "发起支付后支付状态应为支付中");
        assertEquals(0, reloaded.getStatus(), "订单状态应仍是待支付");
    }

    @Test
    @DisplayName("Y-2 对不存在的订单发起支付：被拦截")
    void 对不存在的订单发起支付() throws Exception {
        mockMvc.perform(post("/api/pay/{orderId}", 999999))
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("订单不存在"));
    }

    @Test
    @DisplayName("Y-3 已支付订单再次发起支付：被拦截")
    void 已支付订单再次发起支付() throws Exception {
        Merchant merchant = newMerchant("shop_a", "测试店铺", "123456");
        Order order = newOrder(merchant.getId(), "100.00", 1, 2);   // 已支付

        mockMvc.perform(post("/api/pay/{orderId}", order.getId()))
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("只有待支付订单才能发起支付"));

        assertEquals(2, reloadOrder(order.getId()).getPayStatus(), "状态不应被改动");
    }

    @Test
    @DisplayName("Y-4 已取消订单发起支付：被拦截")
    void 已取消订单发起支付() throws Exception {
        Merchant merchant = newMerchant("shop_a", "测试店铺", "123456");
        Order order = newOrder(merchant.getId(), "100.00", 2, 0);   // 已取消

        mockMvc.perform(post("/api/pay/{orderId}", order.getId()))
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("只有待支付订单才能发起支付"));

        assertEquals(2, reloadOrder(order.getId()).getStatus(), "订单状态不应被改动");
        assertEquals(0, reloadOrder(order.getId()).getPayStatus(), "支付状态不应被改动");
    }

    @Test
    @DisplayName("Y-5 回调 success=true：订单变为已支付")
    void 回调成功() throws Exception {
        Merchant merchant = newMerchant("shop_a", "测试店铺", "123456");
        Order order = pendingOrder(merchant);

        mockMvc.perform(post("/api/pay/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(callbackBody(order.getId(), true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        Order reloaded = reloadOrder(order.getId());
        assertEquals(1, reloaded.getStatus(), "订单状态应为已支付");
        assertEquals(2, reloaded.getPayStatus(), "支付状态应为已支付");
    }

    @Test
    @DisplayName("Y-6 回调 success=false：订单回到待支付，可重新发起支付")
    void 回调失败后可重试() throws Exception {
        Merchant merchant = newMerchant("shop_a", "测试店铺", "123456");
        Order order = pendingOrder(merchant);

        mockMvc.perform(post("/api/pay/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(callbackBody(order.getId(), false)))
                .andExpect(jsonPath("$.code").value(0));

        Order reloaded = reloadOrder(order.getId());
        assertEquals(0, reloaded.getStatus(), "订单应回到待支付");
        assertEquals(3, reloaded.getPayStatus(), "支付状态应为支付失败");

        // 失败之后可以重新发起支付
        mockMvc.perform(post("/api/pay/{orderId}", order.getId()))
                .andExpect(jsonPath("$.code").value(0));
        assertEquals(1, reloadOrder(order.getId()).getPayStatus(), "重新发起支付后应为支付中");
    }

    @Test
    @DisplayName("Y-7 重复回调（幂等）：第二次静默返回，状态不再变化")
    void 重复回调幂等() throws Exception {
        Merchant merchant = newMerchant("shop_a", "测试店铺", "123456");
        Order order = pendingOrder(merchant);

        mockMvc.perform(post("/api/pay/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(callbackBody(order.getId(), true)))
                .andExpect(jsonPath("$.code").value(0));

        // 支付平台重试：再回调一次同样的成功结果
        // 注意本项目对重复回调是「静默忽略」，所以仍然是 code=0，而不是抛异常
        mockMvc.perform(post("/api/pay/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(callbackBody(order.getId(), true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        Order reloaded = reloadOrder(order.getId());
        assertEquals(1, reloaded.getStatus(), "重复回调不应改变订单状态");
        assertEquals(2, reloaded.getPayStatus(), "重复回调不应改变支付状态");
    }

    @Test
    @DisplayName("Y-7b 已支付订单收到失败回调：被幂等拦截，不会被改回未支付")
    void 已支付订单收到失败回调() throws Exception {
        Merchant merchant = newMerchant("shop_a", "测试店铺", "123456");
        Order order = newOrder(merchant.getId(), "100.00", 1, 2);   // 已支付

        mockMvc.perform(post("/api/pay/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(callbackBody(order.getId(), false)))
                .andExpect(jsonPath("$.code").value(0));

        Order reloaded = reloadOrder(order.getId());
        assertEquals(1, reloaded.getStatus(), "已支付订单不应被失败回调改回待支付");
        assertEquals(2, reloaded.getPayStatus(), "支付状态不应被改动");
    }

    @Test
    @DisplayName("Y-8 对已取消订单回调：被拦截")
    void 已取消订单回调被拦截() throws Exception {
        Merchant merchant = newMerchant("shop_a", "测试店铺", "123456");
        Order order = newOrder(merchant.getId(), "100.00", 2, 0);   // 已取消

        mockMvc.perform(post("/api/pay/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(callbackBody(order.getId(), true)))
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("订单已取消，无法支付"));

        assertEquals(2, reloadOrder(order.getId()).getStatus(), "订单状态不应被改动");
    }

    @Test
    @DisplayName("Y-9 回调缺少 success 参数：返回 code=400 + 字段提示")
    void 回调缺参数() throws Exception {
        Merchant merchant = newMerchant("shop_a", "测试店铺", "123456");
        Order order = pendingOrder(merchant);

        mockMvc.perform(post("/api/pay/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderId":%d}
                                """.formatted(order.getId())))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("支付结果不能为空"));

        assertEquals(0, reloadOrder(order.getId()).getPayStatus(), "校验失败不应改动状态");
    }

    @Test
    @DisplayName("Y-10 查询支付状态：返回订单状态与支付状态")
    void 查询支付状态() throws Exception {
        Merchant merchant = newMerchant("shop_a", "测试店铺", "123456");
        Order order = pendingOrder(merchant);

        mockMvc.perform(get("/api/pay/{orderId}/status", order.getId()))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.orderId").value(order.getId()))
                .andExpect(jsonPath("$.data.orderNo").value(order.getOrderNo()))
                .andExpect(jsonPath("$.data.status").value(0))
                .andExpect(jsonPath("$.data.payStatus").value(0));

        // 查不存在的订单
        mockMvc.perform(get("/api/pay/{orderId}/status", 999999))
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("订单不存在"));
    }
}
