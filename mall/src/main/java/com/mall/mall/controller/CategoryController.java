package com.mall.mall.controller;

import com.mall.mall.common.Result;
import com.mall.mall.entity.Category;
import com.mall.mall.service.CategoryService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
public class CategoryController {

    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService){
        this.categoryService = categoryService;
    }

    @PostMapping
    public Result<Category> create(@RequestBody Category category) {
        categoryService.save(category);            // 新增（id 自动回填）
        return Result.success(category);
    }

    @GetMapping
    public Result<List<Category>> list(){
        return Result.success(categoryService.list());
    }

    @PutMapping("/{id}")
    public Result<Category> update(@PathVariable Long id, @RequestBody Category category) {
        categoryService.updateCategory(id, category);   // 按 id 更新，并清理相关商品缓存
        return Result.success(category);
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        categoryService.deleteCategory(id);
        return Result.success();
    }
}
