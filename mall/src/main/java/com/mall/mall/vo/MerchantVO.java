package com.mall.mall.vo;

import lombok.Data;

import java.time.LocalDateTime;

@Data
public class MerchantVO {

    private Long id;
    private String username;
    private String shopName;
    private String contact;
    private String phone;
    private Integer status;
    private LocalDateTime createTime;
}