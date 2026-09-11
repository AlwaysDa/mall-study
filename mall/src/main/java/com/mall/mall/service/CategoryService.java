package com.mall.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.mall.common.BusinessException;
import com.mall.mall.entity.Category;
import com.mall.mall.entity.Product;
import com.mall.mall.mapper.CategoryMapper;
import com.mall.mall.mapper.ProductMapper;
import org.springframework.stereotype.Service;

@Service
public class CategoryService extends ServiceImpl<CategoryMapper, Category> {

    private final ProductMapper productMapper;      // 删除前要查 product 表，需注入 ProductMapper

    public CategoryService(ProductMapper productMapper) {
        this.productMapper = productMapper;
    }

    // 删除前校验：分类下有商品则不允许删除
    public void deleteCategory(Long id) {
        long count = productMapper.selectCount(
                new QueryWrapper<Product>().eq("category_id", id));
        if (count > 0) {
            throw new BusinessException("该分类下存在商品，无法删除");
        }
        this.removeById(id);
    }
}
