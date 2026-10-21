package com.mall.mall.controller;

import com.mall.mall.common.Result;
import com.mall.mall.config.OpenApiConfig;
import com.mall.mall.entity.Category;
import com.mall.mall.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@Tag(name = "分类管理", description = "商品分类的增删改查")
@SecurityRequirement(name = OpenApiConfig.SECURITY_SCHEME)
@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService){
        this.categoryService = categoryService;
    }

    @Operation(summary = "新增分类")
    @PostMapping
    public Result<Category> create(@RequestBody Category category) {
        categoryService.save(category);            // 新增（id 自动回填）
        return Result.success(category);
    }

    @Operation(summary = "查询分类列表")
    @GetMapping
    public Result<List<Category>> list(){
        return Result.success(categoryService.list());
    }

    @Operation(summary = "修改分类", description = "分类改名后会清理该分类下所有商品的详情缓存")
    @PutMapping("/{id}")
    public Result<Category> update(@PathVariable Long id, @RequestBody Category category) {
        categoryService.updateCategory(id, category);   // 按 id 更新，并清理相关商品缓存
        return Result.success(category);
    }

    @Operation(summary = "删除分类", description = "分类下存在商品时不允许删除")
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return Result.success();
    }
}
