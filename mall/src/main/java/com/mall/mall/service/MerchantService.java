package com.mall.mall.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.mall.mall.common.BusinessException;
import com.mall.mall.dto.MerchantLoginDTO;
import com.mall.mall.dto.MerchantRegisterDTO;
import com.mall.mall.entity.Merchant;
import com.mall.mall.mapper.MerchantMapper;
import com.mall.mall.vo.MerchantVO;
import org.springframework.beans.BeanUtils;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class MerchantService extends ServiceImpl<MerchantMapper, Merchant> {

    private final BCryptPasswordEncoder passwordEncoder;

    public MerchantService(BCryptPasswordEncoder passwordEncoder) {
        this.passwordEncoder = passwordEncoder;
    }

    // 注册
    public void register(MerchantRegisterDTO dto) {
        // 1. 账号唯一性校验
        long count = this.count(new QueryWrapper<Merchant>().eq("username", dto.getUsername()));
        if (count > 0) {
            throw new BusinessException("账号已存在");
        }
        // 2. 组装实体，密码加密
        Merchant merchant = new Merchant();
        BeanUtils.copyProperties(dto, merchant);
        merchant.setPassword(passwordEncoder.encode(dto.getPassword()));
        merchant.setStatus(1);
        // 3. 入库
        this.save(merchant);
    }

    // 登录
    public MerchantVO login(MerchantLoginDTO dto) {
        Merchant merchant = this.getOne(
                new QueryWrapper<Merchant>().eq("username", dto.getUsername()));
        if (merchant == null || !passwordEncoder.matches(dto.getPassword(), merchant.getPassword())) {
            throw new BusinessException("账号或密码错误");
        }
        if (merchant.getStatus() == 0) {
            throw new BusinessException("账号已被禁用");
        }
        MerchantVO vo = new MerchantVO();
        BeanUtils.copyProperties(merchant, vo);
        return vo;
    }

    // 查单个
    public MerchantVO getMerchant(Long id) {
        Merchant merchant = this.getById(id);
        if (merchant == null) {
            throw new BusinessException("店家不存在");
        }
        MerchantVO vo = new MerchantVO();
        BeanUtils.copyProperties(merchant, vo);
        return vo;
    }

    // 分页查询
    public Page<MerchantVO> page(int pageNum, int pageSize, String keyword) {
        Page<Merchant> page = new Page<>(pageNum, pageSize);
        QueryWrapper<Merchant> wrapper = new QueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            wrapper.like("shop_name", keyword).or().like("username", keyword);
        }
        wrapper.orderByDesc("id");
        this.page(page, wrapper);

        // 转成 VO（隐藏密码）
        Page<MerchantVO> voPage = new Page<>(pageNum, pageSize, page.getTotal());
        voPage.setRecords(page.getRecords().stream().map(m -> {
            MerchantVO vo = new MerchantVO();
            BeanUtils.copyProperties(m, vo);
            return vo;
        }).toList());
        return voPage;
    }
}