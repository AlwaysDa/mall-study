package com.mall.mall.controller;

import com.jayway.jsonpath.JsonPath;
import com.mall.mall.entity.Order;
import com.mall.mall.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 登录鉴权（JWT）用例，对应测试清单里的 A-1 ~ A-4、A-9 ~ A-11。
 *
 * 这里【故意不用】基类的 perform 助手，而是直接用 mockMvc.perform 自己拼请求头，
 * 因为要测的正是「请求头里的令牌」这件事：有没有带、带的对不对。
 */
@DisplayName("登录鉴权（JWT）")
class JwtAuthTest extends BaseIntegrationTest {

    private static final String PROTECTED_API = "/api/categories";

    @Test
    @DisplayName("A-1 登录成功返回 token，且该 token 能访问受保护接口")
    void 登录签发令牌且令牌可用() throws Exception {
        newMerchant("shop_a", "测试店铺", "123456");

        // 1. 走真实登录接口拿令牌（登录接口在白名单里，不需要令牌）
        String body = mockMvc.perform(post("/api/merchants/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"shop_a","password":"123456"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.username").value("shop_a"))
                .andReturn().getResponse().getContentAsString();

        String issuedToken = JsonPath.read(body, "$.data.token");
        assertTrue(issuedToken.split("\\.").length == 3, "JWT 应由 header.payload.signature 三段组成");

        // 2. 用这个令牌访问受保护接口，应当放行
        mockMvc.perform(get(PROTECTED_API)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + issuedToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("A-2 无 token 访问受保护接口：HTTP 401 + code=401")
    void 无令牌被拦截() throws Exception {
        mockMvc.perform(get(PROTECTED_API))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("未登录，请先登录"));
    }

    @Test
    @DisplayName("A-2b 请求头格式不对（没有 Bearer 前缀）：同样 401")
    void 请求头格式不对被拦截() throws Exception {
        mockMvc.perform(get(PROTECTED_API)
                        .header(HttpHeaders.AUTHORIZATION, "abc.def.ghi"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));
    }

    @Test
    @DisplayName("A-3 token 已过期：HTTP 401 + 提示重新登录")
    void 令牌过期被拦截() throws Exception {
        // 有效期传负数，造出一个「签出来就已经过期」的令牌
        String expiredToken = jwtUtil.generate(1L, "shop_a", -60);

        mockMvc.perform(get(PROTECTED_API)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + expiredToken))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("登录已过期，请重新登录"));
    }

    @Test
    @DisplayName("A-4 token 被篡改：HTTP 401，且不泄露内部错误细节")
    void 令牌被篡改被拦截() throws Exception {
        String validToken = jwtUtil.generate(1L, "shop_a");
        // 只改最后一个字符，签名就对不上了 —— 这正是 JWT 防篡改的关键
        char last = validToken.charAt(validToken.length() - 1);
        String tampered = validToken.substring(0, validToken.length() - 1) + (last == 'A' ? 'B' : 'A');

        String body = mockMvc.perform(get(PROTECTED_API)
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + tampered))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401))
                .andExpect(jsonPath("$.message").value("登录凭证无效"))
                .andReturn().getResponse().getContentAsString();

        // 不能把 JJWT 抛出的内部异常信息（含类名/算法细节）返回给调用方
        assertFalse(body.contains("io.jsonwebtoken"), "不应泄露解析库内部信息：" + body);
        assertFalse(body.contains("Exception"), "不应泄露异常类型：" + body);
    }

    @Test
    @DisplayName("A-9 白名单：注册、登录接口无需 token")
    void 白名单接口无需令牌() throws Exception {
        // 注册
        mockMvc.perform(post("/api/merchants/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"shop_a","password":"123456","shopName":"测试店铺"}
                                """))
                .andExpect(jsonPath("$.code").value(0));
        // 登录
        mockMvc.perform(post("/api/merchants/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"username":"shop_a","password":"123456"}
                                """))
                .andExpect(jsonPath("$.code").value(0));
    }

    @Test
    @DisplayName("A-10 支付回调在无需 token 的白名单里（第三方服务器不可能持有店家令牌）")
    void 支付回调无需令牌() throws Exception {
        // 造一笔「待支付 + 支付中」的订单，模拟已经发起过支付
        Order order = newOrder(1L, "100.00", 0, 1);

        mockMvc.perform(post("/api/pay/callback")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"orderId":%d,"success":true}
                                """.formatted(order.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        assertEquals(2, reloadOrder(order.getId()).getPayStatus(), "回调应把订单置为已支付");
    }

    @Test
    @DisplayName("A-11 鉴权先于路由：不存在的路径没带 token 时也先返回 401")
    void 鉴权先于路由() throws Exception {
        // 拦截器在「找到处理这个路径的方法」之前执行，所以路径本身不存在也先看有没有登录
        mockMvc.perform(get("/api/not-exists"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(401));

        // 带上令牌后，才会走到「接口不存在」，由全局异常处理返回 404
        mockMvc.perform(get("/api/not-exists")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
                .andExpect(jsonPath("$.code").value(404));
    }
}
