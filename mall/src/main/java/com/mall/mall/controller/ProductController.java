package com.mall.mall.controller;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.mall.mall.common.Result;
import com.mall.mall.dto.ProductCreateDTO;
import com.mall.mall.dto.ProductQueryDTO;
import com.mall.mall.service.ProductService;
import com.mall.mall.vo.ProductVO;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    // 新增商品
    @PostMapping
    public Result<Void> create(@Valid @RequestBody ProductCreateDTO dto) {
        productService.createProduct(dto);
        return Result.success();
    }

    // 商品详情（查不到由 Service 抛 BusinessException，走全局异常处理）
    @GetMapping("/{id}")
    public Result<ProductVO> get(@PathVariable Long id) {
        return Result.success(productService.getProduct(id));
    }

    // 分页 + 多条件查询
    @GetMapping("/page")
    public Result<Page<ProductVO>> page(ProductQueryDTO query) {
        return Result.success(productService.page(query));
    }

    // 修改商品
    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody ProductCreateDTO dto) {
        productService.updateProduct(id, dto);
        return Result.success();
    }

    // 删除商品
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        productService.removeById(id);
        return Result.success();
    }

    // 上架/下架（status=1 上架，0 下架）
    @PutMapping("/{id}/status")
    public Result<Void> changeStatus(@PathVariable Long id, @RequestParam Integer status) {
        productService.changeStatus(id, status);
        return Result.success();
    }

    // 库存入库
    @PostMapping("/{id}/stock-in")
    public Result<Void> stockIn(@PathVariable Long id, @RequestParam Integer quantity) {
        productService.stockIn(id, quantity);
        return Result.success();
    }
}
