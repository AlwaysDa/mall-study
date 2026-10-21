package com.mall.mall.support;

import com.mall.mall.entity.Category;
import com.mall.mall.entity.Merchant;
import com.mall.mall.entity.Order;
import com.mall.mall.entity.Product;
import com.mall.mall.mapper.CategoryMapper;
import com.mall.mall.mapper.MerchantMapper;
import com.mall.mall.mapper.OrderItemMapper;
import com.mall.mall.mapper.OrderMapper;
import com.mall.mall.mapper.ProductMapper;
import com.mall.mall.service.ProductService;
import com.mall.mall.util.JwtUtil;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpHeaders;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.util.Set;

/**
 * 集成测试基类：启动完整容器 + 指向测试库 mall_test + 每个用例前清库。
 *
 * 注意：这里【故意不加】@Transactional。
 * 加了的话，业务代码里的事务会「加入」测试的事务，
 * 于是「库存不足应回滚」这类用例测到的其实是测试事务的回滚，失去意义。
 * 改用 @BeforeEach 清库来保证用例之间互不干扰。
 *
 * 关于登录鉴权（阶段八新增）：
 * 加了 AuthInterceptor 之后，/api/** 的接口都要带 JWT，否则返回 401。
 * 为了让「业务用例」不必每个都手写登录流程，基类提供 {@link #perform} ——
 * 它会自动带上当前用例的令牌（token 字段），语义就是「已登录客户端发的请求」。
 * 想测未登录/过期/篡改，直接调用 mockMvc.perform(...) 自己控制请求头即可。
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
    protected JwtUtil jwtUtil;

    /**
     * 当前用例使用的登录令牌。
     * 默认是「店家 ID=1」的令牌（很多用例只是查分类/商品，并没有建店家，但接口仍需令牌）。
     * 调用 newMerchant(...) 后会切换成该店家的令牌。
     */
    protected String token;

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
     * 测试专用 Redis 客户端：application-test.yml 已把 database 固定为 1。
     * 既用于每个用例前清缓存，也用于缓存用例里直接断言 key、value 和 TTL。
     */
    @Autowired
    protected StringRedisTemplate stringRedisTemplate;

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
        // 清掉测试库(1)里的商品缓存，避免上一个用例的缓存影响下一个用例。
        // 这里用 keys 只是为了测试方便；生产环境禁止 KEYS 遍历（会阻塞 Redis），应使用 SCAN。
        Set<String> cacheKeys = stringRedisTemplate.keys(ProductService.PRODUCT_KEY_PREFIX + "*");
        if (cacheKeys != null && !cacheKeys.isEmpty()) {
            stringRedisTemplate.delete(cacheKeys);
        }
        // 清库后重置令牌：默认身份是「店家 ID=1」
        this.token = jwtUtil.generate(1L, "test-merchant");
    }

    // ---------------- 登录令牌相关 ----------------

    /**
     * 「已登录客户端」的请求：自动带上 Authorization: Bearer xxx。
     * 业务用例统一用它；要测鉴权失败，直接用 mockMvc.perform(...) 自己构造请求头。
     */
    protected ResultActions perform(MockHttpServletRequestBuilder builder) throws Exception {
        return mockMvc.perform(builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token));
    }

    // ---------------- 造数据的快捷方法 ----------------

    /** 新建店家，密码会用 BCrypt 真实加密（这样登录用例才能通过），并把当前令牌切换成该店家 */
    protected Merchant newMerchant(String username, String shopName, String rawPassword) {
        Merchant merchant = new Merchant();
        merchant.setUsername(username);
        merchant.setPassword(passwordEncoder.encode(rawPassword));
        merchant.setShopName(shopName);
        merchant.setContact("测试联系人");
        merchant.setPhone("13800000000");
        merchant.setStatus(1);
        merchantMapper.insert(merchant);
        // 登录态跟着当前店家走，后续 perform(...) 发出的请求身份就是它
        this.token = jwtUtil.generate(merchant.getId(), merchant.getUsername());
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

    /**
     * 直接插入一张订单，用于支付等「不关心下单流程」的用例。
     * orderNo 用纳秒时间戳保证唯一（有 uk_order_no 唯一索引，重复会插不进去）。
     *
     * @param status    订单状态：0待支付 1已支付 2已取消
     * @param payStatus 支付状态：0未支付 1支付中 2已支付 3支付失败
     */
    protected Order newOrder(Long merchantId, String totalAmount, int status, int payStatus) {
        Order order = new Order();
        order.setOrderNo("ORD" + System.nanoTime());
        order.setMerchantId(merchantId);
        order.setBuyerName("张三");
        order.setBuyerPhone("13800000000");
        order.setTotalAmount(new BigDecimal(totalAmount));
        order.setStatus(status);
        order.setPayStatus(payStatus);
        orderMapper.insert(order);
        return order;
    }

    /** 查订单当前状态，返回 [status, payStatus]，用于支付用例断言 */
    protected Order reloadOrder(Long orderId) {
        return orderMapper.selectById(orderId);
    }
}
