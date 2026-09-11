package com.mall.mall.common;

import lombok.Data;

@Data
public class Result<T> {

    private Integer code;      // 状态码：0 成功，非 0 失败
    private String message;    // 提示信息
    private T data;            // 数据

    public static <T> Result<T> success(T data) {
        Result<T> r = new Result<>();
        r.code = 0;
        r.message = "success";
        r.data = data;
        return r;
    }

    public static <T> Result<T> success() {
        return success(null);
    }

    public static <T> Result<T> fail(Integer code, String message) {
        Result<T> r = new Result<>();
        r.code = code;
        r.message = message;
        return r;
    }
}