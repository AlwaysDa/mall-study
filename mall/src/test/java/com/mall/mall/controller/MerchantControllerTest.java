package com.mall.mall.controller;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.mall.mall.entity.Merchant;
import com.mall.mall.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("店家管理接口")
class MerchantControllerTest extends BaseIntegrationTest {

    @Test
    @DisplayName("M-1 注册成功：返回 code=0，且密码以 BCrypt 密文入库")
    void 注册成功() throws Exception {
        perform(post("/api/merchants/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"shop_a","password":"123456","shopName":"测试店铺"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        Merchant saved = merchantMapper.selectOne(
                new QueryWrapper<Merchant>().eq("username", "shop_a"));
        assertNotNull(saved, "店家应该已入库");
        assertNotEquals("123456", saved.getPassword(), "密码不能以明文入库");
        assertTrue(saved.getPassword().startsWith("$2"), "密码应是 BCrypt 密文");
    }

    @Test
    @DisplayName("M-2 账号重复注册：被拦截，且库中仍只有一条")
    void 账号重复注册() throws Exception {
        newMerchant("shop_a", "测试店铺", "123456");

        perform(post("/api/merchants/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"shop_a","password":"123456","shopName":"另一个店铺"}
                                """))
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("账号已存在"));

        assertEquals(1L, merchantMapper.selectCount(null));
    }

    @Test
    @DisplayName("M-3 注册缺必填项：返回 code=400 + 字段提示，不写库")
    void 注册缺必填项() throws Exception {
        perform(post("/api/merchants/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"password":"123456","shopName":"测试店铺"}
                                """))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("账号不能为空"));

        assertEquals(0L, merchantMapper.selectCount(null), "校验失败不应写库");
    }

    @Test
    @DisplayName("M-4 密码过短：返回 code=400 + 字段提示，不写库")
    void 密码过短() throws Exception {
        perform(post("/api/merchants/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"shop_a","password":"123","shopName":"测试店铺"}
                                """))
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.message").value("密码长度需在6-20位之间"));

        assertEquals(0L, merchantMapper.selectCount(null));
    }

    @Test
    @DisplayName("M-5 登录成功：返回店家信息，且不泄露密码")
    void 登录成功() throws Exception {
        newMerchant("shop_a", "测试店铺", "123456");

        perform(post("/api/merchants/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"shop_a","password":"123456"}
                                """))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value("shop_a"))
                .andExpect(jsonPath("$.data.shopName").value("测试店铺"))
                .andExpect(jsonPath("$.data.password").doesNotExist());
    }

    @Test
    @DisplayName("M-6 密码错误：返回统一错误提示")
    void 密码错误() throws Exception {
        newMerchant("shop_a", "测试店铺", "123456");

        perform(post("/api/merchants/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"shop_a","password":"wrong-password"}
                                """))
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("账号或密码错误"));
    }

    @Test
    @DisplayName("M-8 账号被禁用：登录被拦截")
    void 禁用账号登录() throws Exception {
        Merchant merchant = newMerchant("shop_a", "测试店铺", "123456");
        merchant.setStatus(0);
        merchantMapper.updateById(merchant);

        perform(post("/api/merchants/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"shop_a","password":"123456"}
                                """))
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("账号已被禁用"));
    }

    @Test
    @DisplayName("M-9 查询店家详情：返回对应数据")
    void 查询店家详情() throws Exception {
        Merchant merchant = newMerchant("shop_a", "测试店铺", "123456");

        perform(get("/api/merchants/{id}", merchant.getId()))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value("shop_a"))
                .andExpect(jsonPath("$.data.shopName").value("测试店铺"));
    }

    @Test
    @DisplayName("M-10 查询不存在的店家：返回业务异常提示")
    void 查询不存在的店家() throws Exception {
        perform(get("/api/merchants/{id}", 999999))
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.message").value("店家不存在"));
    }

    @Test
    @DisplayName("M-12 分页关键字搜索：只返回匹配的店家")
    void 分页关键字搜索() throws Exception {
        newMerchant("shop_a", "数码专营店", "123456");
        newMerchant("shop_b", "生活优选店", "123456");

        perform(get("/api/merchants/page").param("keyword", "数码"))
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].username").value("shop_a"));
    }
}
