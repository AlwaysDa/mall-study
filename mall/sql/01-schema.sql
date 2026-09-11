-- =============================================================
-- 阶段二 · 数据库初始化脚本
-- 对应文档：docs/03-阶段二-数据库设计与持久层.md 第二节
-- 作用：创建 mall 数据库 + product 商品表
-- 用法：在 IDEA Database 面板 / DBeaver / 命令行中整体执行
-- 注意：本脚本可重复执行（建库建表均带 IF NOT EXISTS）
-- =============================================================

-- 1. 建库（utf8mb4 是能存 emoji 的完整 UTF-8，建库建表一律用 utf8mb4）
CREATE DATABASE IF NOT EXISTS mall
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

-- 2. 切换到 mall 库
USE mall;

-- 3. 建表：商品表
CREATE TABLE IF NOT EXISTS product (
    id          BIGINT        NOT NULL AUTO_INCREMENT COMMENT '主键',
    name        VARCHAR(100)  NOT NULL                COMMENT '商品名',
    price       DECIMAL(10,2) NOT NULL                COMMENT '价格',
    stock       INT           NOT NULL DEFAULT 0      COMMENT '库存',
    create_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    update_time DATETIME      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (id)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4 COMMENT = '商品表';

-- 设计要点回顾：
--   主键 BIGINT AUTO_INCREMENT  -> 自增主键，Java 中用 Long 对应
--   金额用 DECIMAL               -> 金额绝不能用 float/double，会有精度丢失
--   ENGINE=InnoDB               -> 支持事务（阶段六下单会用到）
--   每张表都加 create_time/update_time -> 好习惯，方便排查与审计
