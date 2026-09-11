package com.mall.mall.dto;

import lombok.Data;

@Data
public class ProductQueryDTO {
    private Long merchantId;     // 按店家过滤
    private Long categoryId;     // 按分类过滤
    private Integer status;      // 按状态过滤（1上架 0下架）
    private String keyword;      // 名称模糊搜索
    private Integer pageNum = 1;
    private Integer pageSize = 10;
}
