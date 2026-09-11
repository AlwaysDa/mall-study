# mall · 商城后端学习项目

一个从零开始、由浅入深的 Java 后端学习项目。目标是用 Spring Boot 实现一个商城后端服务，包含**店家管理、货物管理、订单管理**三大核心模块，并配有**模拟支付**流程（不含真实支付，不接入任何支付渠道）。

项目按 9 个阶段推进，每个阶段的完整教程放在 [`docs/`](./docs) 目录，可运行代码在 [`mall/`](./mall) 目录。每完成一个阶段都有对应的验收清单，保证「每阶段都能跑起来」。

## 技术栈

| 层次 | 技术 | 版本 |
| --- | --- | --- |
| 语言 | Java | 21（LTS） |
| 框架 | Spring Boot | 4.1.1 |
| 持久层 | MyBatis-Plus | 3.5.15 |
| 数据库 | MySQL | 8.4 LTS |
| 构建工具 | Maven | 3.9.16 |
| 开发工具 | IntelliJ IDEA | 2025.3 |

## 项目结构

```
studyProject/
├── docs/                              # 分阶段开发文档
│   ├── README.md                      # 文档导航
│   ├── 00-学习路线总览.md
│   ├── 01-环境搭建.md
│   ├── 02-阶段一-SpringBoot快速上手.md
│   ├── 03-阶段二-数据库设计与持久层.md
│   ├── 04-阶段三-商城项目架构.md
│   ├── 05-阶段四-店家管理模块.md
│   ├── 06-阶段五-货物管理模块.md
│   ├── 07-阶段六-订单管理模块.md
│   ├── 08-阶段七-模拟支付.md
│   └── 09-阶段八-进阶与工程化.md
└── mall/                              # Spring Boot 工程
    ├── sql/                           # 建表与 SQL 练习脚本
    │   ├── 01-schema.sql              # 建库 + 基础商品表
    │   ├── 02-crud-practice.sql       # 基础 SQL 练习
    │   └── 03-阶段三-商城建表.sql      # 商城 5 张核心表
    ├── src/main/java/com/mall/mall/
    │   ├── MallApplication.java       # 启动类
    │   ├── common/                    # Result 统一返回、ErrorCode、BusinessException
    │   ├── config/                    # MyBatis-Plus、自动填充、密码加密等配置
    │   ├── controller/                # 接口层
    │   ├── dto/                       # 入参对象（含校验注解）
    │   ├── entity/                    # 数据库实体
    │   ├── handler/                   # 全局异常处理
    │   ├── mapper/                    # 持久层接口
    │   ├── service/                   # 业务层
    │   └── vo/                        # 出参对象
    └── pom.xml
```

## 快速开始

### 1. 环境要求

JDK 21、Maven 3.9+、MySQL 8.4。

### 2. 初始化数据库

按顺序在 MySQL 客户端中执行 `mall/sql/` 下的脚本：

```
01-schema.sql          # 建库 mall + 基础商品表
03-阶段三-商城建表.sql   # 商城核心表：merchant / category / product / order / order_item
```

### 3. 配置数据库密码

`mall/src/main/resources/application.yml` 中的密码使用环境变量注入，仓库中**不包含任何真实凭据**：

```yaml
username: ${DB_USERNAME:root}
password: ${DB_PASSWORD:}
```

运行前需设置 `DB_PASSWORD` 环境变量。二选一：

```bash
# 方式一：命令行启动时设置（Windows PowerShell）
$env:DB_PASSWORD="你的MySQL密码"
mvn spring-boot:run
```

```
# 方式二：IDEA 中设置
Run → Edit Configurations → 选中 MallApplication
→ Environment variables → 添加 DB_PASSWORD=你的MySQL密码
```

> `DB_USERNAME` 默认是 `root`，如需修改同样用环境变量覆盖。

### 4. 启动

在 IDEA 中运行 `MallApplication`，或命令行执行：

```bash
cd mall
mvn spring-boot:run
```

服务默认监听 `http://localhost:8080`。可先用 `GET /api/products/page?pageNum=1&pageSize=10` 验证是否启动成功。

## 学习路线

| 阶段 | 主题 | 文档 |
| --- | --- | --- |
| 0 | 环境搭建 | [01-环境搭建](./docs/01-环境搭建.md) |
| 1 | Spring Boot 快速上手（分层 + REST） | [02-阶段一](./docs/02-阶段一-SpringBoot快速上手.md) |
| 2 | 数据库设计与持久层（MyBatis-Plus） | [03-阶段二](./docs/03-阶段二-数据库设计与持久层.md) |
| 3 | 商城项目架构（统一返回 / 全局异常 / 建表） | [04-阶段三](./docs/04-阶段三-商城项目架构.md) |
| 4 | 店家管理（注册登录、BCrypt、分页） | [05-阶段四](./docs/05-阶段四-店家管理模块.md) |
| 5 | 货物管理（分类、商品 CRUD、上下架、库存） | [06-阶段五](./docs/06-阶段五-货物管理模块.md) |
| 6 | 订单管理（事务、状态机、防超卖） | [07-阶段六](./docs/07-阶段六-订单管理模块.md) |
| 7 | 模拟支付（支付流转、幂等、回调） | [08-阶段七](./docs/08-阶段七-模拟支付.md) |
| 8 | 进阶与工程化（JWT、Redis、接口文档、部署） | [09-阶段八](./docs/09-阶段八-进阶与工程化.md) |

## 当前进度

- [x] 阶段一 · Spring Boot 快速上手
- [x] 阶段二 · 数据库设计与持久层
- [x] 阶段三 · 商城项目架构
- [x] 阶段四 · 店家管理模块
- [x] 阶段五 · 货物管理模块
- [ ] 阶段六 · 订单管理模块
- [ ] 阶段七 · 模拟支付
- [ ] 阶段八 · 进阶与工程化

## 说明

- 本项目为个人学习用途，支付环节为**模拟实现**，不涉及任何真实资金流转。
- 所有接口统一返回 `{ code, message, data }` 结构；业务异常由全局处理器统一兜底。
- 金额字段一律使用 `BigDecimal`，与数据库的 `DECIMAL` 类型对齐。
