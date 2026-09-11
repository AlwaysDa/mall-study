-- =============================================================
-- 阶段二 · CRUD 练习脚本
-- 对应文档：docs/03-阶段二-数据库设计与持久层.md 第二节「常用 SQL 速记」
-- 作用：手动练习基础增删改查，熟悉语法后再交给 MyBatis-Plus
-- 用法：先执行 01-schema.sql，再逐条执行下面的语句
-- =============================================================

USE mall;

-- 增：插入一条商品
INSERT INTO product (name, price, stock) VALUES ('手机', 3999.00, 10);

-- 查单个：按主键查询
SELECT * FROM product WHERE id = 1;

-- 模糊查：商品名包含「手机」
SELECT * FROM product WHERE name LIKE '%手机%';

-- 改：修改 id=1 的商品库存
UPDATE product SET stock = 8 WHERE id = 1;

-- 删：删除 id=1 的商品
DELETE FROM product WHERE id = 1;

-- 补充练习：看最终结果（删除后数据已无，可再插入几条验证）
SELECT * FROM product;
