package com.mall.mall.interceptor;

import com.mall.mall.common.ErrorCode;
import com.mall.mall.common.Result;
import com.mall.mall.util.JwtUtil;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 登录鉴权拦截器：校验请求头里的 JWT，把「你是谁」放进 request attribute。
 *
 * 请求格式：Authorization: Bearer <token>
 * 校验通过后可以把店家 ID 交给后续业务代码，Controller 里这样取：
 *
 *     public Result<XxxVO> xxx(@RequestAttribute(AuthInterceptor.ATTR_MERCHANT_ID) Long merchantId)
 *
 * 注意这里是「认证」（你是谁）而不是「授权」（你能改哪些数据）。
 * 想做到「店家只能操作自己的商品/订单」，还要在业务查询里按 merchantId 过滤，见阶段八文档。
 *
 * 失败时返回 HTTP 401 + 统一 Result JSON（与全局异常处理保持同一套返回结构）。
 * 细节：为什么不用抛异常交给 GlobalExceptionHandler？
 *   因为它会给业务异常返回 HTTP 200（靠 code 区分），而鉴权失败标准做法是 HTTP 401，
 *   所以这里直接自己写响应体。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    /** 校验通过后，店家 ID 会以这个 key 放进 request attribute */
    public static final String ATTR_MERCHANT_ID = "merchantId";

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtUtil jwtUtil;
    private final ObjectMapper objectMapper;

    public AuthInterceptor(JwtUtil jwtUtil, ObjectMapper objectMapper) {
        this.jwtUtil = jwtUtil;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler)
            throws IOException {
        String header = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            // 没带令牌，或格式不对（不是 "Bearer xxx"）
            writeUnauthorized(response, "未登录，请先登录");
            return false;
        }

        String token = header.substring(BEARER_PREFIX.length());
        try {
            Long merchantId = jwtUtil.parseMerchantId(token);
            request.setAttribute(ATTR_MERCHANT_ID, merchantId);
            return true;   // 放行
        } catch (ExpiredJwtException e) {
            writeUnauthorized(response, "登录已过期，请重新登录");
            return false;
        } catch (JwtException | IllegalArgumentException e) {
            // 签名不对（被篡改）、格式错误、密钥不匹配等
            // 注意：对外只说「凭证无效」，不要把 JJWT 的内部报错抛给调用方
            writeUnauthorized(response, "登录凭证无效");
            return false;
        }
    }

    /** 写回 HTTP 401 + 统一返回体的 JSON */
    private void writeUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        Result<Void> body = Result.fail(ErrorCode.UNAUTHORIZED.getCode(), message);
        response.getWriter().write(objectMapper.writeValueAsString(body));
    }
}
