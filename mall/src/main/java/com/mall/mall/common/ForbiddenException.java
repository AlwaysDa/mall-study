package com.mall.mall.common;

import lombok.Getter;

/**
 * 授权失败：用户已经登录，但无权访问或修改目标资源。
 *
 * <p>与 401 的区别：401 表示「没登录 / 凭证无效」，不知道你是谁；
 * 403 表示「知道你是谁，但这个资源不属于你」。</p>
 */
@Getter
public class ForbiddenException extends RuntimeException {

    private final Integer code = ErrorCode.FORBIDDEN.getCode();

    public ForbiddenException(String message) {
        super(message);
    }
}
