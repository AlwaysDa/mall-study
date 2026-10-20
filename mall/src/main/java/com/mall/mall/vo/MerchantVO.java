package com.mall.mall.vo;

import com.fasterxml.jackson.annotation.JsonInclude;
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

    /**
     * 登录令牌，只有「登录接口」返回的 VO 里才有值。
     * 查询/分页等接口不带令牌，加上 NON_NULL 避免响应里出现无意义的 "token": null。
     */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    private String token;
}