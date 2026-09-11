package com.mall.mall.entity;

import com.baomidou.mybatisplus.annotation.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data                       // Lombok：自动生成 getter/setter/toString
@TableName("product")                  // 对应哪张表
public class Product {

    @TableId(type = IdType.AUTO)        // 主键自增
    private Long id;

    private Long merchantId;            // 所属店家（驼峰自动映射 merchant_id）
    private Long categoryId;            // 所属分类（驼峰自动映射 category_id）
    private String name;
    private String description;
    private BigDecimal price;           // 金额一律用 BigDecimal，与 DECIMAL(10,2) 对齐
    private Integer stock;
    private Integer status;             // 1上架 0下架
    private String mainImage;           // 主图 URL

    @TableField(fill = FieldFill.INSERT)          // 插入时自动填充
    private LocalDateTime createTime;

    @TableField(fill = FieldFill.INSERT_UPDATE)   // 插入和更新时自动填充
    private LocalDateTime updateTime;

}
