package com.mall.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.mall.common.BusinessException;
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
import java.util.List;

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
     */
    @Transactional(rollbackFor = Exception.class)
    public OrderVO createOrder(OrderCreateDTO dto) {

        // 0. 校验店家存在（和 ProductService.createProduct 同样的做法）。
        //    不校验的话，传一个不存在的 merchantId 也能下单成功，
        //    订单会带一个悬空的店家 id —— 查询、对账时都对不上人。
        if (merchantService.getById(dto.getMerchantId()) == null) {
            throw new BusinessException("店家不存在");
        }

        BigDecimal totalAmount = BigDecimal.ZERO;
        List<OrderItem> items = new ArrayList<>();

        // 1. 逐条校验商品与库存，同时构造明细快照
        for (OrderCreateDTO.OrderItemDTO itemDTO : dto.getItems()) {
            Product product = productService.getById(itemDTO.getProductId());
            if (product == null) {
                throw new BusinessException("商品不存在: " + itemDTO.getProductId());
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
        }

        // 3. 保存订单（先存订单，才能拿到自增 id 给明细用）
        Order order = new Order();
        order.setOrderNo(generateOrderNo());
        order.setMerchantId(dto.getMerchantId());
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

        return toVO(order);
    }

    /**
     * 取消订单：只有待支付可取消，取消时回补库存。
     */
    @Transactional(rollbackFor = Exception.class)
    public void cancelOrder(Long id) {
        Order order = this.getById(id);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        if (order.getStatus() != 0) {
            throw new BusinessException("只有待支付订单才能取消");
        }

        // 回补库存
        List<OrderItem> items = orderItemMapper.selectList(
                new QueryWrapper<OrderItem>().eq("order_id", id));
        for (OrderItem item : items) {
            productMapper.addStock(item.getProductId(), item.getQuantity());
        }

        order.setStatus(2);   // 已取消
        this.updateById(order);
    }

    /**
     * 订单详情：带出明细列表。
     */
    public OrderVO getOrderDetail(Long id) {
        Order order = this.getById(id);
        if (order == null) {
            throw new BusinessException("订单不存在");
        }
        OrderVO vo = toVO(order);
        List<OrderItem> items = orderItemMapper.selectList(
                new QueryWrapper<OrderItem>().eq("order_id", id));
        vo.setItems(items);
        return vo;
    }

    /**
     * 分页查询：可按店家、状态过滤。
     */
    public Page<OrderVO> page(Long merchantId, Integer status, int pageNum, int pageSize) {
        QueryWrapper<Order> wrapper = new QueryWrapper<>();
        if (merchantId != null) {
            wrapper.eq("merchant_id", merchantId);
        }
        if (status != null) {
            wrapper.eq("status", status);
        }
        wrapper.orderByDesc("id");

        Page<Order> page = this.page(new Page<>(pageNum, pageSize), wrapper);

        Page<OrderVO> voPage = new Page<>(pageNum, pageSize, page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(this::toVO).toList());
        return voPage;
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
