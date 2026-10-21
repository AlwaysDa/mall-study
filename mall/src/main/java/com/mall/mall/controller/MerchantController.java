package com.mall.mall.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.mall.common.Result;
import com.mall.mall.config.OpenApiConfig;
import com.mall.mall.dto.MerchantLoginDTO;
import com.mall.mall.dto.MerchantRegisterDTO;
import com.mall.mall.service.MerchantService;
import com.mall.mall.vo.MerchantVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@Tag(name = "店家管理", description = "店家注册、登录与查询")
@RestController
@RequestMapping("/api/merchants")
public class MerchantController {

    private final MerchantService merchantService;

    public MerchantController(MerchantService merchantService) {
        this.merchantService = merchantService;
    }

    @Operation(summary = "店家注册", description = "公开接口，无需 token")
    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody MerchantRegisterDTO dto) {
        merchantService.register(dto);
        return Result.success();
    }

    @Operation(summary = "店家登录", description = "公开接口，返回 JWT，供后续请求使用")
    @PostMapping("/login")
    public Result<MerchantVO> login(@Valid @RequestBody MerchantLoginDTO dto) {
        return Result.success(merchantService.login(dto));
    }

    @Operation(summary = "查询店家详情")
    @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME)
    @GetMapping("/{id}")
    public Result<MerchantVO> get(@PathVariable Long id) {
        return Result.success(merchantService.getMerchant(id));
    }

    @Operation(summary = "分页查询店家")
    @SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME)
    @GetMapping("/page")
    public Result<Page<MerchantVO>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword) {
        return Result.success(merchantService.page(pageNum, pageSize, keyword));
    }
}