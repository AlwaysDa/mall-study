package com.mall.mall.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.mall.mall.entity.OrderItem;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OrderItemMapper extends BaseMapper<OrderItem> {
    // 继承 BaseMapper 后，单表 CRUD 全都自动拥有，无需写任何方法
}
