package com.mall.mall.handler;

import com.mall.mall.common.BusinessException;
import com.mall.mall.common.Result;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 业务异常
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        return Result.fail(e.getCode(), e.getMessage());
    }

    // 其他未知异常
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        // 生产环境应记录日志，这里先打印
        e.printStackTrace();
        return Result.fail(500, "系统内部错误");
    }
}