package com.mall.mall.config;

import io.swagger.v3.oas.annotations.OpenAPIDefinition;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.info.Info;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import org.springframework.context.annotation.Configuration;

/**
 * OpenAPI / Swagger 文档配置。
 *
 * <p>springdoc 会扫描所有 {@code @RestController} 自动生成接口文档；
 * 这里补充文档基本信息，并声明 JWT 安全方案，
 * 让 Swagger UI 右上角出现 Authorize 按钮，方便直接带着令牌调试接口。</p>
 */
@Configuration
@OpenAPIDefinition(
        info = @Info(
                title = "Mall Study API",
                version = "1.0.0",
                description = "商城后端学习项目接口文档。除注册、登录和支付回调外，其余 /api/** 接口都需要 JWT。"
        )
)
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        scheme = "bearer",
        bearerFormat = "JWT",
        description = "先调用登录接口获取 token，再点 Authorize 填入 token（不需要手写 Bearer 前缀）"
)
public class OpenApiConfig {

    /** 供 @SecurityRequirement 引用的安全方案名称，必须与上面 @SecurityScheme 的 name 一致 */
    public static final String SECURITY_SCHEME = "bearerAuth";
}
