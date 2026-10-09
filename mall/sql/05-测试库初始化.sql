-- =============================================================
-- 测试库初始化脚本（只跑一次）
-- 用途：为自动化测试准备一个独立数据库 mall_test，避免测试把开发库 mall 的数据冲掉。
-- 对应文档：docs/10-测试方案与实施.md 第三节「测试库必须隔离」
--
-- 说明：本文件的表结构与 03-阶段三-商城建表.sql 完全一致，唯一区别是库名。
--      以后若改了表结构，这两个文件都要同步改。
-- =============================================================

CREATE DATABASE IF NOT EXISTS mall_test
    DEFAULT CHARSET utf8mb4
    DEFAULT COLLATE utf8mb4_general_ci;

USE mall_test;

-- ------------------------------------------------------------
-- 1. 店家表 merchant
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS merchant (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '店家ID',
    username    VARCHAR(50)  NOT NULL                COMMENT '登录账号',
    password    VARCHAR(100) NOT NULL                COMMENT '密码（BCrypt加密）',
    shop_name   VARCHAR(100) NOT NULL                COMMENT '店铺名称',
    contact     VARCHAR(50)                          COMMENT '联系人',
    phone       VARCHAR(20)                          COMMENT '联系电话',
    status      TINYINT      NOT NULL DEFAULT 1      COMMENT '状态：1正常 0禁用',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_username (username)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='店家表';

-- ------------------------------------------------------------
-- 2. 商品分类表 category
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS category (
    id          BIGINT       NOT NULL AUTO_INCREMENT COMMENT '分类ID',
    name        VARCHAR(50)  NOT NULL                COMMENT '分类名',
    sort        INT          NOT NULL DEFAULT 0      COMMENT '排序值，越小越靠前',
    create_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time DATETIME     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品分类表';

-- ------------------------------------------------------------
-- 3. 商品表 product
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS product (
    id           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '商品ID',
    merchant_id  BIGINT        NOT NULL                COMMENT '所属店家ID',
    category_id  BIGINT        NOT NULL                COMMENT '分类ID',
    name         VARCHAR(100)  NOT NULL                COMMENT '商品名',
    description  VARCHAR(500)                          COMMENT '商品描述',
    price        DECIMAL(10,2) NOT NULL                COMMENT '价格',
    stock        INT           NOT NULL DEFAULT 0      COMMENT '库存',
    status       TINYINT       NOT NULL DEFAULT 1      COMMENT '状态：1上架 0下架',
    main_image   VARCHAR(255)                          COMMENT '主图URL',
    create_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time  DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_merchant (merchant_id),
    KEY idx_category (category_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='商品表';

-- ------------------------------------------------------------
-- 4. 订单表 `order`（order 是 SQL 关键字，必须用反引号包裹）
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS `order` (
    id            BIGINT        NOT NULL AUTO_INCREMENT COMMENT '订单ID',
    order_no      VARCHAR(32)   NOT NULL                COMMENT '订单号',
    merchant_id   BIGINT        NOT NULL                COMMENT '店家ID',
    buyer_name    VARCHAR(50)                           COMMENT '买家姓名',
    buyer_phone   VARCHAR(20)                           COMMENT '买家电话',
    total_amount  DECIMAL(10,2) NOT NULL                COMMENT '订单总额',
    status        TINYINT       NOT NULL DEFAULT 0      COMMENT '订单状态：0待支付 1已支付 2已取消',
    pay_status    TINYINT       NOT NULL DEFAULT 0      COMMENT '支付状态：0未支付 1已支付',
    create_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    update_time   DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_no (order_no),
    KEY idx_merchant (merchant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单表';

-- ------------------------------------------------------------
-- 5. 订单明细表 order_item
-- ------------------------------------------------------------
CREATE TABLE IF NOT EXISTS order_item (
    id           BIGINT        NOT NULL AUTO_INCREMENT COMMENT '明细ID',
    order_id     BIGINT        NOT NULL                COMMENT '订单ID',
    product_id   BIGINT        NOT NULL                COMMENT '商品ID',
    product_name VARCHAR(100)  NOT NULL                COMMENT '商品名（快照）',
    price        DECIMAL(10,2) NOT NULL                COMMENT '成交单价（快照）',
    quantity     INT           NOT NULL                COMMENT '数量',
    amount       DECIMAL(10,2) NOT NULL                COMMENT '小计 = price * quantity',
    PRIMARY KEY (id),
    KEY idx_order (order_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='订单明细表';

-- ------------------------------------------------------------
-- 执行结果确认：应看到 5 张表
-- ------------------------------------------------------------
SELECT TABLE_NAME, TABLE_COMMENT
FROM information_schema.TABLES
WHERE TABLE_SCHEMA = 'mall_test'
ORDER BY TABLE_NAME;
