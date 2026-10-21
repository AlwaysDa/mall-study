package com.mall.mall.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.mall.common.Result;
import com.mall.mall.config.OpenApiConfig;
import com.mall.mall.dto.ProductCreateDTO;
import com.mall.mall.dto.ProductQueryDTO;
import com.mall.mall.interceptor.AuthInterceptor;
import com.mall.mall.service.ProductService;
import com.mall.mall.vo.ProductVO;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@Tag(name = "商品管理", description = "商品的增删改查、上下架与库存入库")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME)
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @Operation(summary = "新增商品", description = "新增后默认上架（status=1），店家身份取自 JWT")
    @PostMapping
    public Result<Void> create(@RequestAttribute(AuthInterceptor.ATTR_MERCHANT_ID) Long merchantId,
                               @Valid @RequestBody ProductCreateDTO dto) {
        productService.createProduct(merchantId, dto);
        return Result.success();
    }

    // 查不到由 Service 抛 BusinessException，走全局异常处理
    @Operation(summary = "查询商品详情", description = "优先读取 Redis 缓存；只能查看自己店家的商品")
    @GetMapping("/{id}")
    public Result<ProductVO> get(@PathVariable Long id,
                                 @RequestAttribute(AuthInterceptor.ATTR_MERCHANT_ID) Long merchantId) {
        return Result.success(productService.getProduct(id, merchantId));
    }

    @Operation(summary = "分页查询商品", description = "自动按当前店家过滤，支持分类、状态和关键字")
    @GetMapping("/page")
    public Result<Page<ProductVO>> page(ProductQueryDTO query,
                                       @RequestAttribute(AuthInterceptor.ATTR_MERCHANT_ID) Long merchantId) {
        return Result.success(productService.page(query, merchantId));
    }

    @Operation(summary = "修改商品", description = "只能修改自己店家的商品；未传 status 时不会覆盖原上下架状态")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id,
                               @RequestAttribute(AuthInterceptor.ATTR_MERCHANT_ID) Long merchantId,
                               @Valid @RequestBody ProductCreateDTO dto) {
        productService.updateProduct(id, merchantId, dto);
        return Result.success();
    }

    // Service 内部会同时清理详情缓存，Controller 不直接操作 Mapper
    @Operation(summary = "删除商品", description = "只能删除自己店家的商品，并清理详情缓存")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id,
                               @RequestAttribute(AuthInterceptor.ATTR_MERCHANT_ID) Long merchantId) {
        productService.deleteProduct(id, merchantId);
        return Result.success();
    }

    @Operation(summary = "上架/下架商品", description = "status=1 上架，status=0 下架；只能操作自己的商品")
    @PutMapping("/{id}/status")
    public Result<Void> changeStatus(@PathVariable Long id,
                                     @RequestAttribute(AuthInterceptor.ATTR_MERCHANT_ID) Long merchantId,
                                     @RequestParam Integer status) {
        productService.changeStatus(id, merchantId, status);
        return Result.success();
    }

    @Operation(summary = "库存入库", description = "quantity 必须大于 0；只能操作自己的商品")
    @PostMapping("/{id}/stock-in")
    public Result<Void> stockIn(@PathVariable Long id,
                                @RequestAttribute(AuthInterceptor.ATTR_MERCHANT_ID) Long merchantId,
                                @RequestParam Integer quantity) {
        productService.stockIn(id, merchantId, quantity);
        return Result.success();
    }
}
