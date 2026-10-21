package com.mall.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.mall.common.BusinessException;
import com.mall.mall.common.ForbiddenException;
import com.mall.mall.dto.OrderCreateDTO;
import com.mall.mall.entity.Order;
import com.mall.mall.entity.OrderItem;
import com.mall.mall.entity.Product;
import com.mall.mall.mapper.OrderItemMapper;
import com.mall.mall.mapper.OrderMapper;
import com.mall.mall.mapper.ProductMapper;
import com.mall.mall.vo.OrderVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * 订单业务层。
 *
 * <p>下单和取消订单都会改动商品库存，因此除了用数据库事务保证订单与库存一致，
 * 还要在事务提交后清理相关商品的详情缓存，避免缓存里的 stock 长时间是旧值。</p>
 */
@Service
public class OrderService extends ServiceImpl<OrderMapper, Order> {

    private final ProductService productService;
    private final MerchantService merchantService;
    private final OrderItemMapper orderItemMapper;
    private final ProductMapper productMapper;

    public OrderService(ProductService productService,
                        MerchantService merchantService,
                        OrderItemMapper orderItemMapper,
                        ProductMapper productMapper) {
        this.productService = productService;
        this.merchantService = merchantService;
        this.orderItemMapper = orderItemMapper;
        this.productMapper = productMapper;
    }

    /**
     * 下单：生成订单 + 生成明细（快照）+ 扣减库存，三步必须原子化。
     * 任一步抛异常，整个事务回滚 —— 不会出现「订单写了但库存没扣」的中间态。
     *
     * @param merchantId 当前登录店家 id，来自 JWT；订单归属和商品校验都以它为准
     * @param dto        下单请求
     * @throws ForbiddenException 使用了其他店家的商品时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderVO createOrder(Long merchantId, OrderCreateDTO dto) {

        // 0. 校验令牌对应的店家存在，避免为已删除/伪造的店家生成悬空订单
        if (merchantService.getById(merchantId) == null) {
            throw new BusinessException("店家不存在");
        }

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OrderItem> items = new ArrayList<>();
        // 记录本次库存发生变化的商品 id：LinkedHashSet 既能去重（同一商品可能出现在多条明细里），
        // 又能保持插入顺序，方便后续批量、稳定地删除缓存
        Set<Long> affectedProductIds = new LinkedHashSet<>();

        // 1. 逐条校验商品与库存，同时构造明细快照
        for (OrderCreateDTO.OrderItemDTO itemDTO : dto.getItems()) {
            // 这里必须直接读数据库拿实时库存，不能走 getProduct 的详情缓存：
            // 缓存可能比数据库旧，库存判断会失真；真正的扣减还要靠下面的原子 SQL 兜底
            Product product = productService.getById(itemDTO.getProductId());
            if (product == null) {
                throw new BusinessException("商品不存在: " + itemDTO.getProductId());
            }
            // 不能拿其他店家的商品下单：否则可以恶意消耗别人的库存
            if (!merchantId.equals(product.getMerchantId())) {
                throw new ForbiddenException("无权操作该商品: " + itemDTO.getProductId());
            }
            if (product.getStock() < itemDTO.getQuantity()) {
                throw new BusinessException("商品库存不足: " + product.getName());
            }

            OrderItem item = new OrderItem();
            item.setProductId(product.getId());
            item.setProductName(product.getName());
            item.setPrice(product.getPrice());
            item.setQuantity(itemDTO.getQuantity());
            item.setAmount(product.getPrice().multiply(BigDecimal.valueOf(itemDTO.getQuantity())));
            items.add(item);

            totalAmount = totalAmount.add(item.getAmount());

            // 2. 原子扣减库存：靠 WHERE stock >= quantity 兜底，并发下也不会扣成负数
            int rows = productMapper.deductStock(product.getId(), itemDTO.getQuantity());
            if (rows == 0) {
                throw new BusinessException("库存不足: " + product.getName());
            }
            // 扣减成功后登记商品 id，事务提交后再统一删缓存
            affectedProductIds.add(product.getId());
        }

        // 3. 保存订单（先存订单，才能拿到自增 id 给明细用）
        Order order = new Order();
        order.setOrderNo(generateOrderNo());
        // 订单归属强制取自令牌，忽略请求体里可能伪造的 merchantId
        order.setMerchantId(merchantId);
        order.setBuyerName(dto.getBuyerName());
        order.setBuyerPhone(dto.getBuyerPhone());
        order.setTotalAmount(totalAmount);
        order.setStatus(0);      // 待支付
        order.setPayStatus(0);   // 未支付
        this.save(order);

        // 4. 保存明细（关联订单ID）
        for (OrderItem item : items) {
            item.setOrderId(order.getId());
            orderItemMapper.insert(item);
        }

        // 不能在这里立即删缓存：事务还没提交，一旦后面回滚，库存其实没变。
        // evictProductCachesAfterCommit 会把删除动作推迟到 afterCommit，提交失败则不动缓存
        productService.evictProductCachesAfterCommit(affectedProductIds);
        return toVO(order);
    }

    /**
     * 取消订单：只有待支付可取消，取消时回补库存。
     *
     * @throws ForbiddenException 订单属于其他店家时抛出
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(Long id, Long merchantId) {
        Order order = requireOwnedOrder(id, merchantId);
        if (order.getStatus() != 0) {
            throw new BusinessException("只有待支付订单才能取消");
        }

        // 回补库存
        List<OrderItem> items = orderItemMapper.selectList(
                new QueryWrapper<OrderItem>().eq("order_id", id));
        // 同样用 Set 去重：同一商品可能有多条明细
        Set<Long> affectedProductIds = new LinkedHashSet<>();
        for (OrderItem item : items) {
            productMapper.addStock(item.getProductId(), item.getQuantity());
            affectedProductIds.add(item.getProductId());
        }

        order.setStatus(2);   // 已取消
        this.updateById(order);
        // 与下单同理：等事务提交成功后再删缓存，避免回滚时误删仍然有效的缓存
        productService.evictProductCachesAfterCommit(affectedProductIds);
    }

    /**
     * 订单详情：带出明细列表。只能查看自己店家的订单。
     *
     * @throws ForbiddenException 订单属于其他店家时抛出
     */
    public OrderVO getOrderDetail(Long id, Long merchantId) {
        Order order = requireOwnedOrder(id, merchantId);
        OrderVO vo = toVO(order);
        List<OrderItem> items = orderItemMapper.selectList(
                new QueryWrapper<OrderItem>().eq("order_id", id));
        vo.setItems(items);
        return vo;
    }

    /**
     * 分页查询：店家维度强制来自当前登录身份，另可按状态过滤。
     */
    public Page<OrderVO> page(Long merchantId, Integer status, int pageNum, int pageSize) {
        QueryWrapper<Order> wrapper = new QueryWrapper<>();
        wrapper.eq("merchant_id", merchantId);
        if (status != null) {
            wrapper.eq("status", status);
        }
        wrapper.orderByDesc("id");

        Page<Order> page = this.page(new Page<>(pageNum, pageSize), wrapper);

        Page<OrderVO> voPage = new Page<>(pageNum, pageSize, page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(this::toVO).toList());
        return voPage;
    }

    /**
     * 按 id 取订单，并确认它属于当前登录店家。
     *
     * @throws BusinessException  订单不存在时抛出
     * @throws ForbiddenException 订单属于其他店家时抛出
     */
    private Order requireOwnedOrder(Long id, Long merchantId) {
        Order order = this.getById(id);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (!merchantId.equals(order.getMerchantId())) {
            throw new ForbiddenException("无权操作该订单");
        }
        return order;
    }

    // 实体 -> VO。注意：这里不带明细，需要明细请用 getOrderDetail
    public OrderVO toVO(Order o) {
        OrderVO vo = new OrderVO();
        BeanUtils.copyProperties(o, vo);
        return vo;
    }

    // 订单号：时间戳 + 随机数，学习期够用；生产环境建议用雪花算法
    private String generateOrderNo() {
        return "ORD" + System.currentTimeMillis() + (int) (Math.random() * 1000);
    }
}
