package com.mall.mall.controller;

import com.mall.mall.entity.Category;
import com.mall.mall.entity.Merchant;
import com.mall.mall.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("分类管理接口")
class CategoryControllerTest extends BaseIntegrationTest {

    @Test
    @DisplayName("K-1 新增分类：返回自增 id")
    void 新增分类() throws Exception {
        mockMvc.perform(post("/api/categories")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"数码电子","sort":1}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.id").isNumber())
                .andExpect(jsonPath("$.data.name").value("数码电子"));

        assertEquals(1L, categoryMapper.selectCount(null));
    }

    @Test
    @DisplayName("K-2 分类列表：返回数组")
    void 分类列表() throws Exception {
        newCategory("数码电子");
        newCategory("家用电器");

        mockMvc.perform(get("/api/categories"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    @DisplayName("K-3 修改分类：库中名字已更新")
    void 修改分类() throws Exception {
        Category category = newCategory("数码电子");

        mockMvc.perform(put("/api/categories/{id}", category.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"name":"数码3C","sort":9}
                                """))
                .andExpect(jsonPath("$.code").value(0));

        Category updated = categoryMapper.selectById(category.getId());
        assertEquals("数码3C", updated.getName());
    }

    @Test
    @DisplayName("K-4 删除空分类：删除成功")
    void 删除空分类() throws Exception {
        Category category = newCategory("数码电子");

        mockMvc.perform(delete("/api/categories/{id}", category.getId()))
                .andExpect(jsonPath("$.code").value(0));

        assertNull(categoryMapper.selectById(category.getId()), "分类应已被删除");
    }

    @Test
    @DisplayName("K-5 删除有商品的分类：被拦截，分类仍然存在")
    void 删除有商品的分类() throws Exception {
        Merchant merchant = newMerchant("shop_a", "测试店铺", "123456");
        Category category = newCategory("数码电子");
        newProduct(merchant.getId(), category.getId(), "iPhone 15", "4999.00", 50);

        mockMvc.perform(delete("/api/categories/{id}", category.getId()))
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("该分类下存在商品，无法删除"));

        assertNotNull(categoryMapper.selectById(category.getId()), "分类不应被删除");
    }
}
