package com.mall.mall.handler;

import com.mall.mall.common.BusinessException;
import com.mall.mall.common.Result;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.servlet.resource.NoResourceFoundException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    // 业务异常
    @ExceptionHandler(BusinessException.class)
    public Result<Void> handleBusinessException(BusinessException e) {
        return Result.fail(e.getCode(), e.getMessage());
    }

    // 参数校验失败（DTO 上的 @Valid + @NotBlank/@NotNull/@Size 等）
    // 不加这一条的话，它会落到下面的 Exception 兜底，返回 code=500「系统内部错误」，
    // 调用方看不出是哪个字段不合法。这里取第一个字段错误信息，返回 code=400。
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public Result<Void> handleValidException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .findFirst()
                .orElse("参数错误");
        return Result.fail(400, message);
    }

    // 其他未知异常
    @ExceptionHandler(Exception.class)
    public Result<Void> handleException(Exception e) {
        // 生产环境应记录日志，这里先打印
        e.printStackTrace();
        return Result.fail(500, "系统内部错误");
    }

    // 未匹配到任何接口的请求（如外部扫描的 /mcp）：返回 404，不打堆栈
    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Result<Void>> handleNoResourceFound(NoResourceFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(Result.fail(404, "接口不存在"));
    }
}