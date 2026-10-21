package com.mall.mall.controller;

import com.mall.mall.support.BaseIntegrationTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("接口文档")
class OpenApiDocsTest extends BaseIntegrationTest {

    @Test
    @DisplayName("A-7 OpenAPI 文档可访问：包含接口与 JWT 安全方案")
    void OpenAPI文档可访问() throws Exception {
        // 故意不用 perform(...)，验证文档接口本身不需要 token
        mockMvc.perform(get("/v3/api-docs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.openapi").exists())
                .andExpect(jsonPath("$.paths['/api/products/{id}']").exists())
                .andExpect(jsonPath("$.components.securitySchemes.bearerAuth").exists())
                .andExpect(jsonPath("$.paths['/api/products/{id}'].get.security[0].bearerAuth").exists());
    }

    @Test
    @DisplayName("A-7 Swagger UI 页面可访问")
    void Swagger页面可访问() throws Exception {
        mockMvc.perform(get("/swagger-ui/index.html"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML));

        // 访问 /swagger-ui.html 会重定向到实际的静态页面
        mockMvc.perform(get("/swagger-ui.html"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/swagger-ui/index.html"));
    }
}
