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
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.ObjectMapper;

import java.time.Duration;
import java.util.Collection;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 商品业务层。
 *
 * <p>商品详情采用 Cache-Aside（旁路缓存）模式：
 * 读请求先查 Redis，未命中再查 MySQL，并把结果写回 Redis；
 * 写请求先更新 MySQL，再删除对应缓存，让下一次读自然重建。</p>
 *
 * <p>缓存 key 为 {@code mall:product:{id}}，value 是 {@link ProductVO} 的 JSON。
 * 本项目只缓存商品详情，分页列表仍然直接查数据库。</p>
 */
@Service
public class ProductService extends ServiceImpl<ProductMapper, Product> {
    // 继承 ServiceImpl 后，直接拥有 list/getById/save/updateById/removeById 等方法

    /** 商品详情缓存 key 前缀，完整 key 形如 {@code mall:product:123} */
    public static final String PRODUCT_KEY_PREFIX = "mall:product:";

    /** 数据库查不到商品时写入的占位值，用于防止同一个不存在的 id 反复打数据库（缓存穿透） */
    private static final String NULL_VALUE = "__NULL__";

    /** 空值占位的过期时间：比正常缓存短，避免商品后来被创建后仍长时间返回“不存在” */
    private static final Duration NULL_TTL = Duration.ofMinutes(2);

    /** 商品缓存的基准过期时间（30 分钟）；实际 TTL 还会加 0~5 分钟随机值 */
    private static final long PRODUCT_TTL_SECONDS = 30 * 60;

    private final MerchantService merchantService;
    private final CategoryService categoryService;
    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;

    public ProductService(MerchantService merchantService,
                          CategoryService categoryService,
                          StringRedisTemplate redisTemplate,
                          ObjectMapper objectMapper) {
        this.merchantService = merchantService;
        this.categoryService = categoryService;
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
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

    /**
     * 查询商品详情（Cache-Aside）。
     *
     * <p>处理顺序：</p>
     * <ol>
     *   <li>先读 Redis：命中空值占位直接判定不存在，命中 JSON 则反序列化返回；</li>
     *   <li>缓存未命中或内容损坏：回源数据库；</li>
     *   <li>数据库不存在：写入短 TTL 的空值占位，再抛业务异常；</li>
     *   <li>数据库存在：转成 {@link ProductVO} 写入缓存后返回。</li>
     * </ol>
     *
     * <p>注意：缓存读异常不在这里捕获。Redis 不可用时接口会直接报错，
     * 便于本地开发及时发现环境问题；生产环境可按需增加降级回源。</p>
     *
     * @param id 商品 id
     * @return 商品详情（含分类名 categoryName）
     * @throws BusinessException 商品不存在时抛出
     */
    public ProductVO getProduct(Long id) {
        String key = PRODUCT_KEY_PREFIX + id;

        // 1. 先查缓存，避免每次详情请求都访问数据库
        String cached = redisTemplate.opsForValue().get(key);
        if (cached != null) {
            if (NULL_VALUE.equals(cached)) {
                // 之前查过且数据库没有这个商品，直接按“不存在”处理，不再打库
                throw new BusinessException("商品不存在");
            }
            try {
                return objectMapper.readValue(cached, ProductVO.class);
            } catch (Exception e) {
                // 缓存内容损坏（例如字段结构变更）：删掉后按未命中处理，避免坏数据一直卡住接口
                redisTemplate.delete(key);
            }
        }

        // 2. 缓存未命中，回源数据库
        Product product = this.getById(id);
        if (product == null) {
            // 3. 数据库也没有：写入短 TTL 的空值占位，防止同一个不存在的 id 反复打库（缓存穿透）
            redisTemplate.opsForValue().set(key, NULL_VALUE, NULL_TTL);
            throw new BusinessException("商品不存在");
        }

        ProductVO vo = toVO(product);
        // 4. 回写缓存。TTL 加 0~5 分钟随机抖动，避免一批商品缓存同时过期（缓存雪崩）
        long ttlSeconds = PRODUCT_TTL_SECONDS + ThreadLocalRandom.current().nextLong(300);
        try {
            redisTemplate.opsForValue().set(
                    key, objectMapper.writeValueAsString(vo), Duration.ofSeconds(ttlSeconds));
        } catch (Exception e) {
            // 缓存写失败不影响本次返回：数据库才是事实来源，下次读会重新回源并尝试重建缓存
        }
        return vo;
    }

    /**
     * 删除商品，并清理详情缓存。
     *
     * <p>先删数据库再删缓存：即使删缓存失败，旧缓存最多存活到 TTL 到期，
     * 不会重新出现一个已经被删除的商品。</p>
     *
     * @param id 商品 id
     */
    public void deleteProduct(Long id) {
        this.removeById(id);
        evictProductCache(id);
    }

    /**
     * 修改商品：校验存在性后更新数据库，再删除缓存。
     *
     * <p>缓存里存的是完整 {@link ProductVO}，名称、价格、分类、库存等任一字段变化，
     * 旧缓存都会失真，因此更新后直接删除，而不是尝试更新缓存。</p>
     *
     * @param id 商品 id
     * @param dto 新的商品字段
     * @throws BusinessException 商品、店家或分类不存在时抛出
     */
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
        // 数据库更新成功后删除旧缓存，下一次查询会按新数据重建
        evictProductCache(id);
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

    /** 上架/下架，并清理商品详情缓存（status 会出现在 {@link ProductVO} 中）。 */
    public void changeStatus(Long id, Integer status) {
        Product product = this.getById(id);
        if (product == null) {
            throw new BusinessException("商品不存在");
        }
        product.setStatus(status);
        this.updateById(product);
        evictProductCache(id);
    }

    /** 库存入库，并清理商品详情缓存（stock 会出现在 {@link ProductVO} 中）。 */
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
        evictProductCache(id);
    }

    /**
     * 删除单个商品的详情缓存（尽力而为）。
     *
     * <p>缓存只是加速层，删除失败不应该让已经完成的数据库写操作报错。
     * 最坏情况是旧缓存继续存在到 TTL 到期，之后再次查询会自然恢复一致。</p>
     *
     * @param id 商品 id
     */
    public void evictProductCache(Long id) {
        try {
            redisTemplate.delete(PRODUCT_KEY_PREFIX + id);
        } catch (Exception e) {
            // 缓存维护失败不能影响主流程；数据库已经写入成功，旧缓存会随 TTL 自然过期
        }
    }

    /**
     * 在事务提交后批量删除商品缓存；没有事务时立即删除。
     *
     * <p>为什么不在事务里直接删：如果缓存已删而事务还没提交，
     * 并发读可能读到旧数据库值并把它重新写回缓存，造成缓存与数据库长期不一致。
     * 注册到 {@code afterCommit} 可以保证只有数据库提交成功才让缓存失效；
     * 事务回滚时不会误删本来仍然有效的缓存。</p>
     *
     * @param ids 受本次操作影响的商品 id 集合
     */
    public void evictProductCachesAfterCommit(Collection<Long> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            // 当前存在事务同步（通常说明方法处于 @Transactional 中）：等事务提交成功后再删
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    ids.forEach(ProductService.this::evictProductCache);
                }
            });
        } else {
            // 没有事务：数据库操作已经是最终结果，直接删除即可
            ids.forEach(this::evictProductCache);
        }
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