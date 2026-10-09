package com.mall.mall.support;

import com.mall.mall.entity.Category;
import com.mall.mall.entity.Merchant;
import com.mall.mall.entity.Product;
import com.mall.mall.mapper.CategoryMapper;
import com.mall.mall.mapper.MerchantMapper;
import com.mall.mall.mapper.OrderItemMapper;
import com.mall.mall.mapper.OrderMapper;
import com.mall.mall.mapper.ProductMapper;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import java.math.BigDecimal;

/**
 * 集成测试基类：启动完整容器 + 指向测试库 mall_test + 每个用例前清库。
 *
 * 注意：这里【故意不加】@Transactional。
 * 加了的话，业务代码里的事务会「加入」测试的事务，
 * 于是「库存不足应回滚」这类用例测到的其实是测试事务的回滚，失去意义。
 * 改用 @BeforeEach 清库来保证用例之间互不干扰。
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Import(MockMvcUtf8Config.class)
public abstract class BaseIntegrationTest {

    @Autowired
    protected MockMvc mockMvc;

    @Autowired
    protected BCryptPasswordEncoder passwordEncoder;

    @Autowired
    protected MerchantMapper merchantMapper;

    @Autowired
    protected CategoryMapper categoryMapper;

    @Autowired
    protected ProductMapper productMapper;

    @Autowired
    protected OrderMapper orderMapper;

    @Autowired
    protected OrderItemMapper orderItemMapper;

    /**
     * 专职测试库，每个用例开始前清空，保证从零开始。
     * 删除顺序与外键无关（本项目没有建外键约束），先删子表更符合习惯。
     */
    @BeforeEach
    void cleanDatabase() {
        orderItemMapper.delete(null);
        orderMapper.delete(null);
        productMapper.delete(null);
        categoryMapper.delete(null);
        merchantMapper.delete(null);
    }

    // ---------------- 造数据的快捷方法 ----------------

    /** 新建店家，密码会用 BCrypt 真实加密（这样登录用例才能通过） */
    protected Merchant newMerchant(String username, String shopName, String rawPassword) {
        Merchant merchant = new Merchant();
        merchant.setUsername(username);
        merchant.setPassword(passwordEncoder.encode(rawPassword));
        merchant.setShopName(shopName);
        merchant.setContact("测试联系人");
        merchant.setPhone("13800000000");
        merchant.setStatus(1);
        merchantMapper.insert(merchant);
        return merchant;
    }

    protected Category newCategory(String name) {
        Category category = new Category();
        category.setName(name);
        category.setSort(0);
        categoryMapper.insert(category);
        return category;
    }

    protected Product newProduct(Long merchantId, Long categoryId, String name, String price, int stock) {
        Product product = new Product();
        product.setMerchantId(merchantId);
        product.setCategoryId(categoryId);
        product.setName(name);
        product.setPrice(new BigDecimal(price));
        product.setStock(stock);
        product.setStatus(1);
        productMapper.insert(product);
        return product;
    }

    /** 查商品当前库存，用于断言扣减/回补是否正确 */
    protected int stockOf(Long productId) {
        return productMapper.selectById(productId).getStock();
    }
}
