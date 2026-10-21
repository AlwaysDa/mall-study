package com.mall.mall.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.mall.common.Result;
import com.mall.mall.config.OpenApiConfig;
import com.mall.mall.dto.ProductCreateDTO;
import com.mall.mall.dto.ProductQueryDTO;
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

    @Operation(summary = "新增商品", description = "新增后默认上架（status=1）")
    @PostMapping
    public Result<Void> create(@Valid @RequestBody ProductCreateDTO dto) {
        productService.createProduct(dto);
        return Result.success();
    }

    // 查不到由 Service 抛 BusinessException，走全局异常处理
    @Operation(summary = "查询商品详情", description = "优先读取 Redis 缓存，未命中再查数据库")
    @GetMapping("/{id}")
    public Result<ProductVO> get(@PathVariable Long id) {
        return Result.success(productService.getProduct(id));
    }

    @Operation(summary = "分页查询商品", description = "支持按店家、分类、状态和关键字过滤")
    @GetMapping("/page")
    public Result<Page<ProductVO>> page(ProductQueryDTO query) {
        return Result.success(productService.page(query));
    }

    @Operation(summary = "修改商品", description = "未传 status 时不会覆盖原上下架状态")
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ProductCreateDTO dto) {
        productService.updateProduct(id, dto);
        return Result.success();
    }

    // Service 内部会同时清理详情缓存，Controller 不直接操作 Mapper
    @Operation(summary = "删除商品", description = "删除数据库记录并清理详情缓存")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        productService.deleteProduct(id);
        return Result.success();
    }

    @Operation(summary = "上架/下架商品", description = "status=1 上架，status=0 下架")
    @PutMapping("/{id}/status")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestParam Integer status) {
        productService.changeStatus(id, status);
        return Result.success();
    }

    @Operation(summary = "库存入库", description = "quantity 必须大于 0")
    @PostMapping("/{id}/stock-in")
    public Result<Void> stockIn(@PathVariable Long id, @RequestParam Integer quantity) {
        productService.stockIn(id, quantity);
        return Result.success();
    }
}
