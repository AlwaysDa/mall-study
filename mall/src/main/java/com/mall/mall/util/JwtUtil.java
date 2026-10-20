package com.mall.mall.util;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;

/**
 * JWT 工具类：负责「签发令牌」和「解析令牌」两件事。
 *
 * JWT 由三段 Base64 用「.」拼起来：header.payload.signature
 *   - header/payload 是明文（Base64 不是加密！），任何人都能解开看内容，所以别往里放敏感信息；
 *   - signature 是用密钥对前两段做的签名，用于防篡改——改了内容签名就对不上。
 *
 * 密钥与有效期都从 application.yml 的 mall.jwt.* 读取（支持环境变量 JWT_SECRET 覆盖），
 * 不写死在代码里，避免密钥随代码进入仓库。
 *
 * 注意：HS256 要求密钥长度 >= 256 位（即 32 字节），太短会在启动时抛 WeakKeyException。
 */
@Component
public class JwtUtil {

    /** 自定义载荷的键名：店家 ID（subject 里放的是用户名） */
    public static final String CLAIM_MERCHANT_ID = "merchantId";

    private final SecretKey secretKey;
    /** 令牌有效期（秒） */
    private final long expireSeconds;

    public JwtUtil(@Value("${mall.jwt.secret}") String secret,
                   @Value("${mall.jwt.expire-seconds:604800}") long expireSeconds) {
        this.secretKey = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.expireSeconds = expireSeconds;
    }

    /** 签发令牌：subject 放用户名，另带一个 merchantId 自定义载荷 */
    public String generate(Long merchantId, String username) {
        return generate(merchantId, username, expireSeconds);
    }

    /**
     * 签发指定有效期的令牌。
     * 传正数表示「多少秒后过期」；传负数即可造出一个「已过期」的令牌（单元测试用）。
     */
    public String generate(Long merchantId, String username, long expireSeconds) {
        long now = System.currentTimeMillis();
        return Jwts.builder()
                .subject(username)
                .claim(CLAIM_MERCHANT_ID, merchantId)
                .issuedAt(new Date(now))
                .expiration(new Date(now + expireSeconds * 1000))
                .signWith(secretKey)
                .compact();
    }

    /**
     * 解析并校验令牌，返回载荷。
     * 令牌无效（签名不对）、已过期、格式错误时都会抛 JwtException，由调用方处理。
     */
    public Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(secretKey)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    /** 从令牌里取出店家 ID */
    public Long parseMerchantId(String token) {
        Object value = parse(token).get(CLAIM_MERCHANT_ID);
        // JJWT 反序列化 JSON 数字时可能给 Integer 也可能给 Long，这里统一按 Number 取 longValue，
        // 直接写 get(..., Long.class) 在值为 Integer 时会抛异常。
        return value == null ? null : ((Number) value).longValue();
    }
}
