-- =============================================================
-- 阶段三 · 商城核心表结构（正式版）
-- 对应文档：docs/04-阶段三-商城项目架构.md 第六节「商城数据库设计」
-- 包含 5 张表：merchant(店家) / category(分类) / product(商品) / `order`(订单) / order_item(订单明细)
-- 设计要点：utf8mb4 + InnoDB + BIGINT 自增主键，金额一律 DECIMAL
-- 用法：整体执行本文件即可（默认不删除任何已存在的表）
-- =============================================================

USE mall;  -- 若 mall 库不存在，请先执行 01-schema.sql 中的建库语句

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
-- 3. 商品表 product（阶段三正式版）
-- 注意：本表的 product 是「正式版」，比 01-schema.sql 中的阶段二简版
-- 多了 merchant_id / category_id / description / status / main_image 字段。
-- 如果你之前已执行过 01-schema.sql，库中 product 表是简版结构，
-- 本文件的 CREATE TABLE IF NOT EXISTS 会直接跳过、不会升级。
-- 如需重建为正式版（简版里的练习数据会丢失，确认无保留价值后）：
--   DROP TABLE IF EXISTS product;
-- 然后重新执行本文件即可。
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
-- 5. 订单明细表 order_item（商品名/价格为快照，改价不影响历史订单）
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
