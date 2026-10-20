package com.mall.mall.controller;

import com.mall.mall.entity.Category;
import com.mall.mall.entity.Merchant;
import com.mall.mall.entity.Order;
import com.mall.mall.entity.OrderItem;
import com.mall.mall.entity.Product;
import com.mall.mall.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("订单管理接口")
class OrderControllerTest extends BaseIntegrationTest {

    /** 单商品下单请求体 */
    private String singleItemBody(Long merchantId, Long productId, int quantity) {
        return """
                {"merchantId":%d,"buyerName":"张三","buyerPhone":"13800000000",
                 "items":[{"productId":%d,"quantity":%d}]}
                """.formatted(merchantId, productId, quantity);
    }

    /** 双商品下单请求体 */
    private String twoItemBody(Long merchantId, Long productId1, int quantity1,
                               Long productId2, int quantity2) {
        return """
                {"merchantId":%d,"buyerName":"张三",
                 "items":[{"productId":%d,"quantity":%d},{"productId":%d,"quantity":%d}]}
                """.formatted(merchantId, productId1, quantity1, productId2, quantity2);
    }

    /** 取库里唯一的那张订单 */
    private Order theOnlyOrder() {
        List<Order> orders = orderMapper.selectList(null);
        assertEquals(1, orders.size(), "预期库中只有一张订单");
        return orders.get(0);
    }

    @Test
    @DisplayName("O-1 下单成功：订单 + 明细 + 库存扣减三处都对")
    void 下单成功() throws Exception {
        Merchant merchant = newMerchant("shop_a", "测试店铺", "123456");
        Category category = newCategory("数码电子");
        Product product = newProduct(merchant.getId(), category.getId(), "iPhone 15", "4999.00", 50);

        perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(singleItemBody(merchant.getId(), product.getId(), 2)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.orderNo").isNotEmpty());

        // 库存扣减
        assertEquals(48, stockOf(product.getId()));

        // 订单
        Order order = theOnlyOrder();
        assertEquals(merchant.getId(), order.getMerchantId());
        assertEquals("张三", order.getBuyerName());
        assertEquals(0, order.getStatus(), "新订单应为待支付");
        assertEquals(0, order.getPayStatus(), "新订单应为未支付");
        assertEquals(0, order.getTotalAmount().compareTo(new BigDecimal("9998.00")));

        // 明细
        List<OrderItem> items = orderItemMapper.selectList(null);
        assertEquals(1, items.size());
        assertEquals(order.getId(), items.get(0).getOrderId());
        assertEquals("iPhone 15", items.get(0).getProductName());
        assertEquals(2, items.get(0).getQuantity());
        assertEquals(0, items.get(0).getAmount().compareTo(new BigDecimal("9998.00")));
    }

    @Test
    @DisplayName("O-2 多商品下单：总额等于各明细小计之和")
    void 多商品下单() throws Exception {
        Merchant merchant = newMerchant("shop_a", "测试店铺", "123456");
        Category category = newCategory("数码电子");
        Product p1 = newProduct(merchant.getId(), category.getId(), "iPhone 15", "1000.00", 10);
        Product p2 = newProduct(merchant.getId(), category.getId(), "小米 14", "500.00", 10);

        perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(twoItemBody(merchant.getId(), p1.getId(), 2, p2.getId(), 3)))
                .andExpect(jsonPath("$.code").value(0));

        assertEquals(2, orderItemMapper.selectCount(null));
        // 1000*2 + 500*3 = 3500
        assertEquals(0, theOnlyOrder().getTotalAmount().compareTo(new BigDecimal("3500.00")));
        assertEquals(8, stockOf(p1.getId()));
        assertEquals(7, stockOf(p2.getId()));
    }

    @Test
    @DisplayName("O-3 库存不足：订单与明细一条都没落库（事务回滚）")
    void 库存不足时回滚() throws Exception {
        Merchant merchant = newMerchant("shop_a", "测试店铺", "123456");
        Category category = newCategory("数码电子");
        Product product = newProduct(merchant.getId(), category.getId(), "iPhone 15", "4999.00", 5);

        perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(singleItemBody(merchant.getId(), product.getId(), 10)))
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("商品库存不足: iPhone 15"));

        assertEquals(0L, orderMapper.selectCount(null), "失败的下单不应留下订单");
        assertEquals(0L, orderItemMapper.selectCount(null), "失败的下单不应留下明细");
        assertEquals(5, stockOf(product.getId()), "库存不应被扣减");
    }

    @Test
    @DisplayName("O-3b 前一件已扣库存、后一件失败：前面的扣减也要回滚")
    void 部分成功也要回滚() throws Exception {
        Merchant merchant = newMerchant("shop_a", "测试店铺", "123456");
        Category category = newCategory("数码电子");
        Product enough = newProduct(merchant.getId(), category.getId(), "库存充足", "100.00", 10);
        Product notEnough = newProduct(merchant.getId(), category.getId(), "库存不足", "100.00", 1);

        perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(twoItemBody(merchant.getId(), enough.getId(), 2, notEnough.getId(), 5)))
                .andExpect(jsonPath("$.code").value(500));

        assertEquals(10, stockOf(enough.getId()), "第一件的扣减必须回滚");
        assertEquals(1, stockOf(notEnough.getId()));
        assertEquals(0L, orderMapper.selectCount(null));
    }

    @Test
    @DisplayName("O-4 商品不存在：被拦截，不写库")
    void 商品不存在() throws Exception {
        Merchant merchant = newMerchant("shop_a", "测试店铺", "123456");

        perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(singleItemBody(merchant.getId(), 999999L, 1)))
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("商品不存在: 999999"));

        assertEquals(0L, orderMapper.selectCount(null));
        assertEquals(0L, orderItemMapper.selectCount(null));
    }

    @Test
    @DisplayName("O-5 明细是快照：商品改名改价后，历史明细不变")
    void 明细是快照() throws Exception {
        Merchant merchant = newMerchant("shop_a", "测试店铺", "123456");
        Category category = newCategory("数码电子");
        Product product = newProduct(merchant.getId(), category.getId(), "iPhone 15", "4999.00", 50);

        perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(singleItemBody(merchant.getId(), product.getId(), 1)))
                .andExpect(jsonPath("$.code").value(0));

        // 下单之后商品被改名、改价
        Product changed = productMapper.selectById(product.getId());
        changed.setName("改名后的商品");
        changed.setPrice(new BigDecimal("1.00"));
        productMapper.updateById(changed);

        OrderItem item = orderItemMapper.selectList(null).get(0);
        assertEquals("iPhone 15", item.getProductName(), "明细商品名应是下单时的快照");
        assertEquals(0, item.getPrice().compareTo(new BigDecimal("4999.00")), "明细价格应是下单时的快照");
    }

    @Test
    @DisplayName("O-6 下单明细为空：返回 code=400 + 字段提示，不写库")
    void 下单明细为空() throws Exception {
        Merchant merchant = newMerchant("shop_a", "测试店铺", "123456");

        perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"merchantId":%d,"buyerName":"张三","items":[]}
                                """.formatted(merchant.getId())))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("订单明细不能为空"));

        assertEquals(0L, orderMapper.selectCount(null));
    }

    @Test
    @DisplayName("O-7 订单号唯一：连续两单的 orderNo 不同")
    void 订单号唯一() throws Exception {
        Merchant merchant = newMerchant("shop_a", "测试店铺", "123456");
        Category category = newCategory("数码电子");
        Product product = newProduct(merchant.getId(), category.getId(), "iPhone 15", "4999.00", 50);

        perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                .content(singleItemBody(merchant.getId(), product.getId(), 1)));
        perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                .content(singleItemBody(merchant.getId(), product.getId(), 1)));

        List<Order> orders = orderMapper.selectList(null);
        assertEquals(2, orders.size());
        assertNotEquals(orders.get(0).getOrderNo(), orders.get(1).getOrderNo());
    }

    @Test
    @DisplayName("O-8 订单详情：带出明细列表")
    void 订单详情带明细() throws Exception {
        Merchant merchant = newMerchant("shop_a", "测试店铺", "123456");
        Category category = newCategory("数码电子");
        Product product = newProduct(merchant.getId(), category.getId(), "iPhone 15", "4999.00", 50);

        perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                .content(singleItemBody(merchant.getId(), product.getId(), 2)));

        Order order = theOnlyOrder();

        perform(get("/api/orders/{id}", order.getId()))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.orderNo").value(order.getOrderNo()))
                .andExpect(jsonPath("$.data.items.length()").value(1))
                .andExpect(jsonPath("$.data.items[0].productName").value("iPhone 15"));
    }

    @Test
    @DisplayName("O-9 查询不存在的订单：返回业务异常提示")
    void 查询不存在的订单() throws Exception {
        perform(get("/api/orders/{id}", 999999))
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("订单不存在"));
    }

    @Test
    @DisplayName("O-11 分页按状态过滤：只返回待支付订单")
    void 分页按状态过滤() throws Exception {
        Merchant merchant = newMerchant("shop_a", "测试店铺", "123456");
        Category category = newCategory("数码电子");
        Product product = newProduct(merchant.getId(), category.getId(), "iPhone 15", "4999.00", 50);

        perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                .content(singleItemBody(merchant.getId(), product.getId(), 1)));
        perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                .content(singleItemBody(merchant.getId(), product.getId(), 1)));

        // 把其中一单改成已支付
        List<Order> orders = orderMapper.selectList(null);
        Order paid = orders.get(0);
        paid.setStatus(1);
        orderMapper.updateById(paid);

        perform(get("/api/orders/page").param("status", "0"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].status").value(0));
    }

    @Test
    @DisplayName("O-12 取消待支付订单：状态变已取消，库存回补")
    void 取消订单回补库存() throws Exception {
        Merchant merchant = newMerchant("shop_a", "测试店铺", "123456");
        Category category = newCategory("数码电子");
        Product product = newProduct(merchant.getId(), category.getId(), "iPhone 15", "4999.00", 50);

        perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                .content(singleItemBody(merchant.getId(), product.getId(), 3)));
        assertEquals(47, stockOf(product.getId()));

        Order order = theOnlyOrder();
        perform(put("/api/orders/{id}/cancel", order.getId()))
                .andExpect(jsonPath("$.code").value(0));

        assertEquals(2, orderMapper.selectById(order.getId()).getStatus(), "状态应为已取消");
        assertEquals(50, stockOf(product.getId()), "库存应回补到下单前");
    }

    @Test
    @DisplayName("O-15 下单店家不存在：被拦截，不写库、不扣库存")
    void 下单店家不存在() throws Exception {
        Merchant merchant = newMerchant("shop_a", "测试店铺", "123456");
        Category category = newCategory("数码电子");
        Product product = newProduct(merchant.getId(), category.getId(), "iPhone 15", "4999.00", 50);

        // 商品是真实存在的，但 merchantId 传一个不存在的
        perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(singleItemBody(999999L, product.getId(), 1)))
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("店家不存在"));

        assertEquals(0L, orderMapper.selectCount(null), "不应留下订单");
        assertEquals(0L, orderItemMapper.selectCount(null), "不应留下明细");
        assertEquals(50, stockOf(product.getId()), "库存不应被扣减");
    }

    @Test
    @DisplayName("O-13 取消已支付订单：被拦截，状态与库存都不变")
    void 取消已支付订单被拦截() throws Exception {
        Merchant merchant = newMerchant("shop_a", "测试店铺", "123456");
        Category category = newCategory("数码电子");
        Product product = newProduct(merchant.getId(), category.getId(), "iPhone 15", "4999.00", 50);

        perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                .content(singleItemBody(merchant.getId(), product.getId(), 3)));

        Order order = theOnlyOrder();
        order.setStatus(1);          // 模拟已支付
        orderMapper.updateById(order);

        perform(put("/api/orders/{id}/cancel", order.getId()))
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("只有待支付订单才能取消"));

        assertEquals(1, orderMapper.selectById(order.getId()).getStatus(), "状态不应变化");
        assertEquals(47, stockOf(product.getId()), "库存不应回补");
    }

    @Test
    @DisplayName("O-14 重复取消：第二次被拦截")
    void 重复取消被拦截() throws Exception {
        Merchant merchant = newMerchant("shop_a", "测试店铺", "123456");
        Category category = newCategory("数码电子");
        Product product = newProduct(merchant.getId(), category.getId(), "iPhone 15", "4999.00", 50);

        perform(post("/api/orders").contentType(MediaType.APPLICATION_JSON)
                .content(singleItemBody(merchant.getId(), product.getId(), 1)));
        Order order = theOnlyOrder();

        perform(put("/api/orders/{id}/cancel", order.getId()))
                .andExpect(jsonPath("$.code").value(0));
        perform(put("/api/orders/{id}/cancel", order.getId()))
                .andExpect(jsonPath("$.code").value(500));

        assertEquals(50, stockOf(product.getId()), "库存只应回补一次");
    }
}
