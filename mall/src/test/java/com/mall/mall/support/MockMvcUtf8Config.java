package com.mall.mall.support;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.context.annotation.Bean;

import java.nio.charset.StandardCharsets;

/**
 * MockMvc 默认用 ISO-8859-1 解码响应体，直接断言中文会乱码。
 * 这里把默认响应编码改成 UTF-8，测试里才能放心断言中文提示。
 */
@TestConfiguration
public class MockMvcUtf8Config {

    @Bean
    public MockMvcBuilderCustomizer utf8ResponseEncodingCustomizer() {
        return builder -> builder.defaultResponseCharacterEncoding(StandardCharsets.UTF_8);
    }
}
