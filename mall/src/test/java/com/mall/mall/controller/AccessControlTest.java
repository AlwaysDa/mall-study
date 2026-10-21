package com.mall.mall.controller;

import com.mall.mall.entity.Category;
import com.mall.mall.entity.Merchant;
import com.mall.mall.entity.Order;
import com.mall.mall.entity.Product;
import com.mall.mall.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 越权控制（A-5）：已登录店家只能操作自己的商品和订单。
 *
 * <p>核心思路：身份来自 JWT，而不是请求参数或请求体。
 * 跨店访问时返回 HTTP 403 + code=403，并且原数据不能发生变化。</p>
 */
@DisplayName("越权控制（A-5）")
class AccessControlTest extends BaseIntegrationTest {

    @Test
    @DisplayName("A-5-1 商品详情：不能查看其他店家的商品（缓存命中时也不行）")
    void 商品详情跨店被拒() throws Exception {
        Merchant shopA = newMerchant("shop_a", "测试店铺A", "123456");
        String tokenA = token;
        Category category = newCategory("数码电子");
        Product productA = newProduct(shopA.getId(), category.getId(), "iPhone 15", "4999.00", 50);

        newMerchant("shop_b", "测试店铺B", "123456");

        // 缓存未命中，走数据库：跨店访问应被拒绝
        perform(get("/api/products/{id}", productA.getId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权操作该商品"));

        // 店家 A 自己查询一次，把商品写入缓存
        token = tokenA;
        perform(get("/api/products/{id}", productA.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.name").value("iPhone 15"));

        // 店家 B 再查，即使命中缓存也必须被拒绝
        newMerchant("shop_b_2", "测试店铺B2", "123456");
        perform(get("/api/products/{id}", productA.getId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    @DisplayName("A-5-2 商品写操作：不能修改、上下架、入库或删除其他店家的商品")
    void 商品写操作跨店被拒() throws Exception {
        Merchant shopA = newMerchant("shop_a", "测试店铺A", "123456");
        Category category = newCategory("数码电子");
        Product productA = newProduct(shopA.getId(), category.getId(), "iPhone 15", "4999.00", 50);

        newMerchant("shop_b", "测试店铺B", "123456");

        perform(put("/api/products/{id}", productA.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"categoryId":%d,"name":"被篡改","price":1.00,"stock":1}
                                """.formatted(category.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        perform(put("/api/products/{id}/status", productA.getId()).param("status", "0"))
                .andExpect(status().isForbidden());

        perform(post("/api/products/{id}/stock-in", productA.getId()).param("quantity", "10"))
                .andExpect(status().isForbidden());

        perform(delete("/api/products/{id}", productA.getId()))
                .andExpect(status().isForbidden());

        Product unchanged = productMapper.selectById(productA.getId());
        assertEquals("iPhone 15", unchanged.getName(), "商品名不应被跨店修改");
        assertEquals(1, unchanged.getStatus(), "上下架状态不应被跨店修改");
        assertEquals(50, unchanged.getStock(), "库存不应被跨店修改");
    }

    @Test
    @DisplayName("A-5-3 下单：不能使用其他店家的商品，避免恶意消耗库存")
    void 跨店商品下单被拒() throws Exception {
        Merchant shopA = newMerchant("shop_a", "测试店铺A", "123456");
        Category category = newCategory("数码电子");
        Product productA = newProduct(shopA.getId(), category.getId(), "iPhone 15", "4999.00", 50);

        newMerchant("shop_b", "测试店铺B", "123456");

        perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"buyerName":"张三","items":[{"productId":%d,"quantity":1}]}
                                """.formatted(productA.getId())))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权操作该商品: " + productA.getId()));

        assertEquals(0L, orderMapper.selectCount(null), "不应留下订单");
        assertEquals(50, stockOf(productA.getId()), "其他店家的库存不应被扣减");
    }

    @Test
    @DisplayName("A-5-4 订单：详情、取消和分页都只能看到自己店家的数据")
    void 订单跨店访问被拒() throws Exception {
        Merchant shopA = newMerchant("shop_a", "测试店铺A", "123456");
        Merchant shopB = newMerchant("shop_b", "测试店铺B", "123456");
        Order orderA = newOrder(shopA.getId(), "100.00", 0, 0);
        Order orderB = newOrder(shopB.getId(), "200.00", 0, 0);

        // 当前令牌属于 shopB，查 shopA 的订单应被拒绝
        perform(get("/api/orders/{id}", orderA.getId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权操作该订单"));

        perform(put("/api/orders/{id}/cancel", orderA.getId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        // 自己的订单可以正常查看
        perform(get("/api/orders/{id}", orderB.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.orderNo").value(orderB.getOrderNo()));

        // 分页同样按 token 过滤；故意传 merchantId=shopA 也不生效
        perform(get("/api/orders/page")
                        .param("merchantId", String.valueOf(shopA.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].orderNo").value(orderB.getOrderNo()));

        assertEquals(0, reloadOrder(orderA.getId()).getStatus(), "跨店取消不能改变订单状态");
    }

    @Test
    @DisplayName("A-5-5 支付：不能对他人订单发起支付或查询支付状态")
    void 支付跨店被拒() throws Exception {
        Merchant shopA = newMerchant("shop_a", "测试店铺A", "123456");
        Order orderA = newOrder(shopA.getId(), "100.00", 0, 0);

        newMerchant("shop_b", "测试店铺B", "123456");

        perform(post("/api/pay/{orderId}", orderA.getId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.message").value("无权操作该订单"));

        perform(get("/api/pay/{orderId}/status", orderA.getId()))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(403));

        assertEquals(0, reloadOrder(orderA.getId()).getPayStatus(), "支付状态不应被跨店修改");
    }
}
