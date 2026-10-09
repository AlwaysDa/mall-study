-- =============================================================
-- 阶段六 · 测试数据脚本
-- 用途：一键造出「2 个店家 + 每个店家 6 个商品（含库存）」，
--       用于测试下单 / 取消订单 / 分页查询 / 库存扣减。
-- 前置：已执行 03-阶段三-商城建表.sql（merchant / category / product 表已存在）
-- 执行：在 IDEA Database 面板或 DBeaver 中【整体执行】本文件
--       （脚本用到了会话变量 @shop_a 等，逐条复制到不同窗口执行会失效）
-- 可重复执行：每次先清理本脚本自己造的旧数据，再重新插入
-- =============================================================

USE mall;

-- ------------------------------------------------------------
-- 登录信息（两个店家账号，密码统一 123456）
--   test_shop_a / 123456   测试数码专营店
--   test_shop_b / 123456   测试生活优选店
--
-- 下面 password 存的是 BCrypt 密文，不是明文。该密文由与项目同版本的
-- BCryptPasswordEncoder 生成，并已本地校验 matches("123456") == true。
-- 两个密文不一样但都对应 123456 —— 这正是 BCrypt「随机盐」的体现：
-- 同一个密码每次加密结果都不同，但都能验证通过。
-- ------------------------------------------------------------


-- ------------------------------------------------------------
-- 1. 清理本脚本上次造的数据
--    只删 username 为 test_shop_a / test_shop_b 的店家及其商品，不动其他数据。
--    说明：order_item 存的是商品名/价格【快照】，删商品不影响历史订单明细，
--          所以这里不碰订单表。但若这些测试商品上还挂着「待支付」订单，
--          取消时回补库存会找不到商品（影响行数为 0），
--          建议重新执行本脚本前，先把相关测试订单取消或删除。
-- ------------------------------------------------------------
DELETE FROM product
WHERE merchant_id IN (
    SELECT id FROM merchant WHERE username IN ('test_shop_a', 'test_shop_b')
);

DELETE FROM merchant WHERE username IN ('test_shop_a', 'test_shop_b');


-- ------------------------------------------------------------
-- 2. 插入 2 个店家
-- ------------------------------------------------------------
INSERT INTO merchant (username, password, shop_name, contact, phone, status) VALUES
('test_shop_a', '$2a$10$4BtFY2/blV8IXyIE5iwElOLHDnOj/rdeT7.soVllWkP0kafxbze06', '测试数码专营店', '张老板', '13800000001', 1),
('test_shop_b', '$2a$10$hwGWTDWabZErBogGnrg8heEIXLGEXqYL6DDKVeNy.VMzFUvLZ/HDO', '测试生活优选店', '李老板', '13800000002', 1);

-- 取出店家 id 备用（下面插商品时要用）
SET @shop_a = (SELECT id FROM merchant WHERE username = 'test_shop_a');
SET @shop_b = (SELECT id FROM merchant WHERE username = 'test_shop_b');


-- ------------------------------------------------------------
-- 3. 插入商品分类
--    商品表的 category_id 是 NOT NULL，所以商品必须先有分类。
--    INSERT ... SELECT ... WHERE NOT EXISTS 是 MySQL 里「存在就跳过」的惯用写法，
--    保证重复执行本脚本不会插入重复分类（category 表没有唯一键，不能靠 ON DUPLICATE KEY）。
--    注意：若你库里已有同名分类，会直接复用，不会新建。
-- ------------------------------------------------------------
INSERT INTO category (name, sort)
SELECT * FROM (SELECT '数码电子' AS name, 1 AS sort) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM category WHERE name = '数码电子');

INSERT INTO category (name, sort)
SELECT * FROM (SELECT '家用电器' AS name, 2 AS sort) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM category WHERE name = '家用电器');

INSERT INTO category (name, sort)
SELECT * FROM (SELECT '服饰鞋包' AS name, 3 AS sort) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM category WHERE name = '服饰鞋包');

INSERT INTO category (name, sort)
SELECT * FROM (SELECT '食品生鲜' AS name, 4 AS sort) AS tmp
WHERE NOT EXISTS (SELECT 1 FROM category WHERE name = '食品生鲜');

-- 取出分类 id 备用
SET @cat_digital   = (SELECT id FROM category WHERE name = '数码电子' LIMIT 1);
SET @cat_appliance = (SELECT id FROM category WHERE name = '家用电器' LIMIT 1);
SET @cat_clothing  = (SELECT id FROM category WHERE name = '服饰鞋包' LIMIT 1);
SET @cat_food      = (SELECT id FROM category WHERE name = '食品生鲜' LIMIT 1);


-- ------------------------------------------------------------
-- 4. 店家 A（测试数码专营店）6 个商品
-- ------------------------------------------------------------
INSERT INTO product (merchant_id, category_id, name, description, price, stock, status) VALUES
(@shop_a, @cat_digital,   'iPhone 15 128G',  'A16 芯片，双摄，蓝色',        4999.00,  50, 1),
(@shop_a, @cat_digital,   '小米 14 Pro',      '骁龙 8 Gen3，2K 直屏',       4299.00,  40, 1),
(@shop_a, @cat_digital,   '华为 MatePad 11',  '11 英寸 120Hz 高刷平板',      2499.00,  30, 1),
(@shop_a, @cat_digital,   '索尼 WH-1000XM5',  '头戴式主动降噪耳机',          1999.00,  60, 1),
(@shop_a, @cat_appliance, '美的电饭煲 4L',    '家用智能预约电饭煲',           399.00, 100, 1),
(@shop_a, @cat_appliance, '戴森 V12 吸尘器',  '轻量无线手持吸尘器',          3299.00,  20, 1);


-- ------------------------------------------------------------
-- 5. 店家 B（测试生活优选店）6 个商品
-- ------------------------------------------------------------
INSERT INTO product (merchant_id, category_id, name, description, price, stock, status) VALUES
(@shop_b, @cat_clothing, '男士纯棉短袖 T 恤', '夏季透气，多色可选',            89.00, 200, 1),
(@shop_b, @cat_clothing, '女士双肩通勤背包',   '15.6 英寸电脑仓，防泼水',       259.00, 120, 1),
(@shop_b, @cat_clothing, '轻量运动跑步鞋',     '减震回弹，41-45 码',            459.00,  80, 1),
(@shop_b, @cat_food,     '赣南脐橙 5 斤装',    '现摘现发，甜度 13 度',           39.90, 300, 1),
(@shop_b, @cat_food,     '有机大米 10kg',      '东北五常产区，当季新米',          89.90, 150, 1),
(@shop_b, @cat_food,     '智利车厘子 2 斤',    'JJ 级，顺丰冷链',               129.00, 100, 1);


-- ------------------------------------------------------------
-- 6. 库存调整（按需启用，默认注释掉）
--    测试「库存不足 / 防超卖 / 取消回补」时很有用。
-- ------------------------------------------------------------
-- 给店家 A 的所有商品统一补货 100 件：
-- UPDATE product SET stock = stock + 100 WHERE merchant_id = @shop_a;

-- 把某个商品库存改成 3，用来测「库存不足」：
-- UPDATE product SET stock = 3 WHERE merchant_id = @shop_a AND name = 'iPhone 15 128G';

-- 只给某一类商品补货（按名字定位，不用记 id）：
-- UPDATE product SET stock = 500 WHERE merchant_id = @shop_b AND name LIKE '%车厘子%';


-- ------------------------------------------------------------
-- 7. 执行结果确认
-- ------------------------------------------------------------
-- 7.1 店家列表（password 是密文，显示一长串是正常的）
SELECT id, username, shop_name, contact, phone, status
FROM merchant
WHERE username LIKE 'test_shop%';

-- 7.2 每个店家的商品数 + 库存合计（期望：各 6 个商品）
SELECT m.username, m.shop_name,
       COUNT(p.id) AS product_count,
       IFNULL(SUM(p.stock), 0) AS total_stock
FROM merchant m
LEFT JOIN product p ON p.merchant_id = m.id
WHERE m.username LIKE 'test_shop%'
GROUP BY m.id, m.username, m.shop_name;

-- 7.3 商品明细（期望：12 行）
SELECT p.id, m.username, c.name AS category, p.name, p.price, p.stock, p.status
FROM product p
JOIN merchant m ON m.id = p.merchant_id
JOIN category c ON c.id = p.category_id
WHERE m.username LIKE 'test_shop%'
ORDER BY m.id, p.id;
