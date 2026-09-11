package com.mall.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.mall.common.BusinessException;
import com.mall.mall.dto.ProductCreateDTO;
import com.mall.mall.dto.ProductQueryDTO;
import com.mall.mall.entity.Category;
import com.mall.mall.entity.Product;
import com.mall.mall.mapper.ProductMapper;
import com.mall.mall.vo.ProductVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;

@Service
public class ProductService extends ServiceImpl<ProductMapper, Product> {
    // 继承 ServiceImpl 后，直接拥有 list/getById/save/updateById/removeById 等方法

    private final MerchantService merchantService;
    private final CategoryService categoryService;

    public ProductService(MerchantService merchantService, CategoryService categoryService) {
        this.merchantService = merchantService;
        this.categoryService = categoryService;
    }

    // 新增商品：校验店家与分类存在
    public void createProduct(ProductCreateDTO dto) {
        if (merchantService.getById(dto.getMerchantId()) == null) {
            throw new BusinessException("店家不存在");
        }
        if (categoryService.getById(dto.getCategoryId()) == null) {
            throw new BusinessException("分类不存在");
        }
        Product product = new Product();
        BeanUtils.copyProperties(dto, product);
        product.setStatus(1);   // 默认上架
        this.save(product);
    }

    // 查询商品详情（查不到直接抛异常，由全局处理器转成统一返回）
    public ProductVO getProduct(Long id) {
        Product product = this.getById(id);
        if (product == null) {
            throw new BusinessException("商品不存在");
        }
        return toVO(product);
    }

    // 修改商品：校验存在性后更新
    public void updateProduct(Long id, ProductCreateDTO dto) {
        if (this.getById(id) == null) {
            throw new BusinessException("商品不存在");
        }
        if (merchantService.getById(dto.getMerchantId()) == null) {
            throw new BusinessException("店家不存在");
        }
        if (categoryService.getById(dto.getCategoryId()) == null) {
            throw new BusinessException("分类不存在");
        }
        Product product = new Product();
        BeanUtils.copyProperties(dto, product);
        product.setId(id);      // 用路径参数指定的 id
        // status 为 null，updateById 默认只更新非 null 字段，所以上架状态不会被覆盖
        this.updateById(product);
    }

    // 分页 + 多条件查询
    public Page<ProductVO> page(ProductQueryDTO query) {
        QueryWrapper<Product> wrapper = new QueryWrapper<>();
        if (query.getMerchantId() != null) {
            wrapper.eq("merchant_id", query.getMerchantId());
        }
        if (query.getCategoryId() != null) {
            wrapper.eq("category_id", query.getCategoryId());
        }
        if (query.getStatus() != null) {
            wrapper.eq("status", query.getStatus());
        }
        if (query.getKeyword() != null && !query.getKeyword().isBlank()) {
            wrapper.like("name", query.getKeyword());
        }
        wrapper.orderByDesc("id");

        Page<Product> page = this.page(
                new Page<>(query.getPageNum(), query.getPageSize()), wrapper);

        // 转 VO，并填充分类名（展示用）
        Page<ProductVO> voPage = new Page<>(query.getPageNum(), query.getPageSize(), page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(this::toVO).toList());
        return voPage;
    }

    // 上架/下架
    public void changeStatus(Long id, Integer status) {
        Product product = this.getById(id);
        if (product == null) {
            throw new BusinessException("商品不存在");
        }
        product.setStatus(status);
        this.updateById(product);
    }

    // 库存入库
    public void stockIn(Long id, Integer quantity) {
        if (quantity == null || quantity <= 0) {
            throw new BusinessException("入库数量必须大于0");
        }
        Product product = this.getById(id);
        if (product == null) {
            throw new BusinessException("商品不存在");
        }
        product.setStock(product.getStock() + quantity);
        this.updateById(product);
    }

    // 实体 -> VO 的转换（Controller 里的 get 接口会间接用到，故为 public）
    public ProductVO toVO(Product p) {
        ProductVO vo = new ProductVO();
        BeanUtils.copyProperties(p, vo);
        Category c = categoryService.getById(p.getCategoryId());
        vo.setCategoryName(c != null ? c.getName() : null);
        return vo;
    }
}