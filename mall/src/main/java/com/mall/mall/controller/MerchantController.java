package com.mall.mall.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.mall.common.Result;
import com.mall.mall.dto.MerchantLoginDTO;
import com.mall.mall.dto.MerchantRegisterDTO;
import com.mall.mall.service.MerchantService;
import com.mall.mall.vo.MerchantVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/merchants")
public class MerchantController {

    private final MerchantService merchantService;

    public MerchantController(MerchantService merchantService) {
        this.merchantService = merchantService;
    }

    @PostMapping("/register")
    public Result<Void> register(@Valid @RequestBody MerchantRegisterDTO dto) {
        merchantService.register(dto);
        return Result.success();
    }

    @PostMapping("/login")
    public Result<MerchantVO> login(@Valid @RequestBody MerchantLoginDTO dto) {
        return Result.success(merchantService.login(dto));
    }

    @GetMapping("/{id}")
    public Result<MerchantVO> get(@PathVariable Long id) {
        return Result.success(merchantService.getMerchant(id));
    }

    @GetMapping("/page")
    public Result<Page<MerchantVO>> page(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "10") int pageSize,
            @RequestParam(required = false) String keyword) {
        return Result.success(merchantService.page(pageNum, pageSize, keyword));
    }
}