package com.mall.mall.controller;

import com.mall.mall.entity.Category;
import com.mall.mall.entity.Merchant;
import com.mall.mall.entity.Product;
import com.mall.mall.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.not;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("商品管理接口")
class ProductControllerTest extends BaseIntegrationTest {

    @Test
    @DisplayName("P-1 新增商品成功：默认上架（status=1）")
    void 新增商品成功() throws Exception {
        Merchant merchant = newMerchant("shop_a", "测试店铺", "123456");
        Category category = newCategory("数码电子");

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"merchantId":%d,"categoryId":%d,"name":"iPhone 15",
                                 "price":4999.00,"stock":50}
                                """.formatted(merchant.getId(), category.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        Product saved = productMapper.selectList(null).get(0);
        assertEquals("iPhone 15", saved.getName());
        assertEquals(1, saved.getStatus(), "新增商品默认上架");
    }

    @Test
    @DisplayName("P-2 店家不存在：被拦截，不写库")
    void 店家不存在() throws Exception {
        Category category = newCategory("数码电子");

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"merchantId":999999,"categoryId":%d,"name":"iPhone 15",
                                 "price":4999.00,"stock":50}
                                """.formatted(category.getId())))
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("店家不存在"));

        assertEquals(0L, productMapper.selectCount(null));
    }

    @Test
    @DisplayName("P-3 分类不存在：被拦截，不写库")
    void 分类不存在() throws Exception {
        Merchant merchant = newMerchant("shop_a", "测试店铺", "123456");

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"merchantId":%d,"categoryId":999999,"name":"iPhone 15",
                                 "price":4999.00,"stock":50}
                                """.formatted(merchant.getId())))
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("分类不存在"));

        assertEquals(0L, productMapper.selectCount(null));
    }

    @Test
    @DisplayName("P-4 查询商品详情：VO 里带 categoryName")
    void 查询商品详情() throws Exception {
        Merchant merchant = newMerchant("shop_a", "测试店铺", "123456");
        Category category = newCategory("数码电子");
        Product product = newProduct(merchant.getId(), category.getId(), "iPhone 15", "4999.00", 50);

        mockMvc.perform(get("/api/products/{id}", product.getId()))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.name").value("iPhone 15"))
                .andExpect(jsonPath("$.data.categoryName").value("数码电子"));
    }

    @Test
    @DisplayName("P-5 查询不存在的商品：返回业务异常提示")
    void 查询不存在的商品() throws Exception {
        mockMvc.perform(get("/api/products/{id}", 999999))
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("商品不存在"));
    }

    @Test
    @DisplayName("P-6 分页按店家过滤：不返回其他店家的商品")
    void 分页按店家过滤() throws Exception {
        Merchant shopA = newMerchant("shop_a", "测试店铺A", "123456");
        Merchant shopB = newMerchant("shop_b", "测试店铺B", "123456");
        Category category = newCategory("数码电子");
        newProduct(shopA.getId(), category.getId(), "iPhone 15", "4999.00", 50);
        newProduct(shopB.getId(), category.getId(), "小米 14", "4299.00", 40);

        mockMvc.perform(get("/api/products/page").param("merchantId", String.valueOf(shopA.getId())))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].name").value("iPhone 15"));
    }

    @Test
    @DisplayName("P-8 分页关键字搜索：只返回名称匹配的商品")
    void 分页关键字搜索() throws Exception {
        Merchant merchant = newMerchant("shop_a", "测试店铺", "123456");
        Category category = newCategory("数码电子");
        newProduct(merchant.getId(), category.getId(), "iPhone 15", "4999.00", 50);
        newProduct(merchant.getId(), category.getId(), "小米 14", "4299.00", 40);

        mockMvc.perform(get("/api/products/page").param("keyword", "iPhone"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].name").value("iPhone 15"));
    }

    @Test
    @DisplayName("P-9 上架/下架：库中状态已变更")
    void 上下架() throws Exception {
        Merchant merchant = newMerchant("shop_a", "测试店铺", "123456");
        Category category = newCategory("数码电子");
        Product product = newProduct(merchant.getId(), category.getId(), "iPhone 15", "4999.00", 50);

        mockMvc.perform(put("/api/products/{id}/status", product.getId()).param("status", "0"))
                .andExpect(jsonPath("$.code").value(0));

        assertEquals(0, productMapper.selectById(product.getId()).getStatus());
    }

    @Test
    @DisplayName("P-10 库存入库：库存正确增加")
    void 库存入库() throws Exception {
        Merchant merchant = newMerchant("shop_a", "测试店铺", "123456");
        Category category = newCategory("数码电子");
        Product product = newProduct(merchant.getId(), category.getId(), "iPhone 15", "4999.00", 50);

        mockMvc.perform(post("/api/products/{id}/stock-in", product.getId()).param("quantity", "50"))
                .andExpect(jsonPath("$.code").value(0));

        assertEquals(100, stockOf(product.getId()));
    }

    @Test
    @DisplayName("P-11 入库数量非法：被拦截，库存不变")
    void 入库数量非法() throws Exception {
        Merchant merchant = newMerchant("shop_a", "测试店铺", "123456");
        Category category = newCategory("数码电子");
        Product product = newProduct(merchant.getId(), category.getId(), "iPhone 15", "4999.00", 50);

        mockMvc.perform(post("/api/products/{id}/stock-in", product.getId()).param("quantity", "0"))
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("入库数量必须大于0"));

        assertEquals(50, stockOf(product.getId()), "库存不应变化");
    }

    @Test
    @DisplayName("P-12 修改商品：字段更新，未传的 status 不被覆盖")
    void 修改商品() throws Exception {
        Merchant merchant = newMerchant("shop_a", "测试店铺", "123456");
        Category category = newCategory("数码电子");
        Product product = newProduct(merchant.getId(), category.getId(), "iPhone 15", "4999.00", 50);
        // 先下架，再改基本信息，验证下架状态不会被覆盖回上架
        product.setStatus(0);
        productMapper.updateById(product);

        mockMvc.perform(put("/api/products/{id}", product.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"merchantId":%d,"categoryId":%d,"name":"iPhone 15 Pro",
                                 "price":5999.00,"stock":30}
                                """.formatted(merchant.getId(), category.getId())))
                .andExpect(jsonPath("$.code").value(0));

        Product updated = productMapper.selectById(product.getId());
        assertEquals("iPhone 15 Pro", updated.getName());
        assertEquals(0, updated.getStatus(), "未传 status，应保持下架");
    }

    @Test
    @DisplayName("P-13 新增商品缺必填项：请求被拒绝")
    void 新增商品缺必填项() throws Exception {
        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"没有店家也没有价格"}
                                """))
                .andExpect(jsonPath("$.code").value(not(0)));

        assertEquals(0L, productMapper.selectCount(null));
    }

    @Test
    @DisplayName("P-14 删除商品：库中记录消失")
    void 删除商品() throws Exception {
        Merchant merchant = newMerchant("shop_a", "测试店铺", "123456");
        Category category = newCategory("数码电子");
        Product product = newProduct(merchant.getId(), category.getId(), "iPhone 15", "4999.00", 50);

        mockMvc.perform(delete("/api/products/{id}", product.getId()))
                .andExpect(jsonPath("$.code").value(0));

        assertNull(productMapper.selectById(product.getId()));
    }
}
