package com.mall.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.mall.common.BusinessException;
import com.mall.mall.entity.Category;
import com.mall.mall.entity.Product;
import com.mall.mall.mapper.CategoryMapper;
import com.mall.mall.mapper.ProductMapper;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class CategoryService extends ServiceImpl<CategoryMapper, Category> {

    private final ProductMapper productMapper;       // 分类更新/删除时需要查询该分类下的商品
    private final StringRedisTemplate redisTemplate; // 分类改名后用于删除相关商品详情缓存

    public CategoryService(ProductMapper productMapper, StringRedisTemplate redisTemplate) {
        this.productMapper = productMapper;
        this.redisTemplate = redisTemplate;
    }

    /**
     * 修改分类，并清理该分类下所有商品的详情缓存。
     *
     * <p>商品详情 VO 里包含 categoryName，分类改名后即使商品表本身没变，
     * 缓存中的展示数据也会变旧，因此必须按分类批量失效，而不是只删某个商品。</p>
     *
     * @param id       分类 id
     * @param category 新的分类信息
     */
    public void updateCategory(Long id, Category category) {
        category.setId(id);                 // 以路径参数为准，避免请求体中的 id 覆盖它
        this.updateById(category);          // 先更新数据库
        evictProductCachesByCategory(id);   // 再让受影响的商品缓存失效
    }

    /**
     * 删除某个分类下所有商品的详情缓存。
     *
     * <p>只查询 id 列即可拼出缓存 key，避免把整行商品数据读出来；
     * key 的拼接方式必须与 {@link ProductService#PRODUCT_KEY_PREFIX} 保持一致。</p>
     *
     * @param categoryId 分类 id
     */
    private void evictProductCachesByCategory(Long categoryId) {
        List<Product> products = productMapper.selectList(
                new QueryWrapper<Product>().select("id").eq("category_id", categoryId));
        if (products.isEmpty()) {
            return;
        }
        List<String> keys = products.stream()
                .map(p -> ProductService.PRODUCT_KEY_PREFIX + p.getId())
                .toList();
        try {
            redisTemplate.delete(keys);
        } catch (Exception e) {
            // 缓存维护失败不影响分类更新；旧缓存会在 TTL 到期后自然过期
        }
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
