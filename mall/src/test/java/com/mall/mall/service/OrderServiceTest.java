package com.mall.mall.service;

import com.mall.mall.common.BusinessException;
import com.mall.mall.dto.OrderCreateDTO;
import com.mall.mall.entity.Merchant;
import com.mall.mall.entity.OrderItem;
import com.mall.mall.entity.Product;
import com.mall.mall.mapper.OrderItemMapper;
import com.mall.mall.mapper.ProductMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 纯单元测试：不启动 Spring 容器，用假的 Mapper 替代数据库。
 *
 * 它的价值不是「断言返回值」，而是 verify —— 确认【不该发生的事没有发生】。
 * 至于「库存到底有没有扣对」「事务有没有回滚」，那是集成测试的活（见 OrderControllerTest）。
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService 单元测试（不启动容器）")
class OrderServiceTest {

    @Mock
    private ProductService productService;

    @Mock
    private MerchantService merchantService;

    @Mock
    private OrderItemMapper orderItemMapper;

    @Mock
    private ProductMapper productMapper;

    @InjectMocks
    private OrderService orderService;

    /** 存在的店家，用于让「店家存在」这道校验先通过 */
    private Merchant merchant(Long id) {
        Merchant merchant = new Merchant();
        merchant.setId(id);
        merchant.setUsername("shop_a");
        merchant.setShopName("测试店铺");
        return merchant;
    }

    private OrderCreateDTO dtoWith(Long productId, int quantity) {
        OrderCreateDTO dto = new OrderCreateDTO();
        dto.setMerchantId(1L);
        dto.setBuyerName("张三");
        OrderCreateDTO.OrderItemDTO item = new OrderCreateDTO.OrderItemDTO();
        item.setProductId(productId);
        item.setQuantity(quantity);
        dto.setItems(List.of(item));
        return dto;
    }

    private Product product(Long id, String name, String price, int stock) {
        Product product = new Product();
        product.setId(id);
        product.setName(name);
        product.setPrice(new BigDecimal(price));
        product.setStock(stock);
        return product;
    }

    @Test
    @DisplayName("店家不存在：抛业务异常，且不写明细、不扣库存")
    void 店家不存在时抛异常() {
        when(merchantService.getById(1L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> orderService.createOrder(dtoWith(1L, 1)));
        assertTrue(exception.getMessage().contains("店家不存在"));

        verify(orderItemMapper, never()).insert(any(OrderItem.class));
        verify(productMapper, never()).deductStock(anyLong(), anyInt());
    }

    @Test
    @DisplayName("商品不存在：抛业务异常，且不写明细、不扣库存")
    void 商品不存在时抛异常() {
        when(merchantService.getById(1L)).thenReturn(merchant(1L));
        when(productService.getById(999L)).thenReturn(null);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> orderService.createOrder(dtoWith(999L, 1)));
        assertTrue(exception.getMessage().contains("商品不存在"));

        verify(orderItemMapper, never()).insert(any(OrderItem.class));
        verify(productMapper, never()).deductStock(anyLong(), anyInt());
    }

    @Test
    @DisplayName("库存不足：抛业务异常，且不执行原子扣减")
    void 库存不足时抛异常() {
        when(merchantService.getById(1L)).thenReturn(merchant(1L));
        when(productService.getById(1L)).thenReturn(product(1L, "iPhone 15", "4999.00", 1));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> orderService.createOrder(dtoWith(1L, 5)));
        assertTrue(exception.getMessage().contains("库存不足"));

        verify(productMapper, never()).deductStock(anyLong(), anyInt());
        verify(orderItemMapper, never()).insert(any(OrderItem.class));
    }
}
