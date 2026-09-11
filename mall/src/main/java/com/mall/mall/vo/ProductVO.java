package com.mall.mall.vo;

import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 商品展示对象：在实体基础上多一个 categoryName。
 * 数据库只存 category_id，但前端列表要显示分类名，所以在 VO 里补上。
 */
@Data
public class ProductVO {

    private Long id;
    private Long merchantId;
    private Long categoryId;
    private String categoryName;   // 额外字段：补查 category 表得到，前端展示用
    private String name;
    private String description;
    private BigDecimal price;
    private Integer stock;
    private Integer status;        // 1上架 0下架
    private String mainImage;
    private LocalDateTime createTime;
}
