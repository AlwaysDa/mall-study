# mall · 商城后端学习项目

一个从零开始、由浅入深的 Java 后端学习项目。目标是用 Spring Boot 实现一个商城后端服务，包含**店家管理、货物管理、订单管理**三大核心模块，并配有**模拟支付**流程（不含真实支付，不接入任何支付渠道）。

项目按 9 个阶段推进（阶段 0 是环境搭建，阶段 1~8 是开发），每个阶段的完整教程放在 [`docs/`](./docs) 目录，可运行代码在 [`mall/`](./mall) 目录。每完成一个阶段都有对应的验收清单，保证「每阶段都能跑起来」。

## 技术栈

| 层次 | 技术 | 版本 |
| --- | --- | --- |
| 语言 | Java | 21（LTS） |
| 框架 | Spring Boot | 4.1.1 |
| 持久层 | MyBatis-Plus | 3.5.15（boot4 starter + jsqlparser） |
| 登录鉴权 | JJWT（JWT 令牌） | 0.12.5 |
| 缓存 | Redis（Windows 可用 Memurai） | 商品详情缓存 |
| 数据库 | MySQL | 8.4 LTS |
| 密码加密 | Spring Security Crypto | BCrypt |
| 测试 | JUnit 5 + Mockito + MockMvc | 随 Spring Boot 4 |
| 构建工具 | Maven | 3.9.16（自带 Wrapper） |
| 开发工具 | IntelliJ IDEA | 2025.3 |

## 项目结构

```
mall-study/
├── README.md                          # 本文件
├── docs/                              # 教程与专题文档
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
│   ├── 09-阶段八-进阶与工程化.md
│   ├── 10-测试方案与实施.md
│   ├── 11-测试用例清单.md
│   ├── 12-HTTP接口测试脚本使用说明.md
│   └── 13-Redis缓存接入与使用.md
└── mall/                              # Spring Boot 工程
    ├── http/
    │   └── mall-api.http              # 手工回归脚本（用法见 docs/12）
    ├── sql/                           # 建表与 SQL 练习脚本
    │   ├── 01-schema.sql              # 建库 + 基础商品表
    │   ├── 02-crud-practice.sql       # 基础 SQL 练习
    │   ├── 03-阶段三-商城建表.sql      # 商城 5 张核心表
    │   ├── 04-测试数据.sql             # 演示数据（2 个店家，各 6 个商品）
    │   └── 05-测试库初始化.sql         # 独立测试库 mall_test
    ├── src/main/java/com/mall/mall/
    │   ├── MallApplication.java       # 启动类
    │   ├── common/                    # Result 统一返回、ErrorCode、BusinessException
    │   ├── config/                    # MyBatis-Plus、Web 拦截器、Swagger 文档等配置
    │   ├── controller/                # 接口层（店家 / 分类 / 商品 / 订单 / 支付）
    │   ├── dto/                       # 入参对象（含校验注解）
    │   ├── entity/                    # 数据库实体
    │   ├── handler/                   # 全局异常处理
    │   ├── interceptor/               # 登录鉴权拦截器（JWT 校验）
    │   ├── mapper/                    # 持久层接口
    │   ├── service/                   # 业务层
    │   ├── util/                      # JwtUtil 等工具类
    │   └── vo/                        # 出参对象
    ├── src/main/resources/application.yml
    ├── src/test/                      # 自动化测试（72 条）
    ├── mvnw / mvnw.cmd                # Maven Wrapper
    └── pom.xml
```

## 接口一览

所有接口统一返回 `{ code, message, data }`。

| 模块 | 接口前缀 | 主要接口 |
| --- | --- | --- |
| 店家管理 | `/api/merchants` | 注册、登录、详情、分页 |
| 分类管理 | `/api/categories` | 新增、列表、修改、删除 |
| 商品管理 | `/api/products` | 增删改查、分页、上下架、入库 |
| 订单管理 | `/api/orders` | 下单、详情、分页、取消 |
| 模拟支付 | `/api/pay` | 发起支付、回调、查询支付状态 |

## 快速开始

### 1. 环境要求

JDK 21、MySQL 8.4、Redis（Windows 可用 Memurai）；Maven 3.9+ 可以不装，工程自带 Wrapper。

### 2. 初始化数据库

按顺序在 MySQL 客户端中执行 `mall/sql/` 下的脚本：

```
01-schema.sql          # 建库 mall + 基础商品表
03-阶段三-商城建表.sql   # 商城核心表：merchant / category / product / order / order_item
```

另外两个脚本按需执行：

```
04-测试数据.sql         # 可选：一键造演示数据（2 个店家，各 6 个商品）
05-测试库初始化.sql     # 跑测试前必须执行：建独立测试库 mall_test
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

没装 Maven 的话用自带的 Wrapper：Windows 下执行 `mvnw.cmd spring-boot:run`，macOS / Linux 下执行 `./mvnw spring-boot:run`。

服务默认监听 `http://localhost:8080`。验证是否启动成功可以直接调白名单接口，例如：

```
POST /api/merchants/login
{"username":"test_shop_a","password":"123456"}
```

返回 `code=0` 说明服务正常，响应里的 `data.token` 就是后续访问受保护接口要带的 JWT。
（`mall/sql/04-测试数据.sql` 里有现成的测试店家；未导入数据的话先调注册接口建一个。）

JWT 密钥默认可直接跑，生产环境请用环境变量 `JWT_SECRET` 覆盖（长度需 ≥ 32 字符）。

商品详情已接入 Redis 缓存，启动前请确保 Memurai / Redis 在 `6379` 端口运行；Redis 不可用时商品详情接口会报连接错误，其他接口不受影响。

### 5. 接口文档（Swagger UI）

启动后访问 `http://localhost:8080/swagger-ui.html`（会自动跳转到 Swagger UI 页面）。接口按模块分组，点开任意接口即可查看参数和响应结构。

除注册、登录和支付回调外，`/api/**` 接口都需要 JWT：先调用登录接口拿到 `data.token`，再点页面右上角的 **Authorize**，把 token 填进去（不用手写 `Bearer ` 前缀），之后就能直接在页面上调试受保护接口。

原始 OpenAPI 描述可通过 `http://localhost:8080/v3/api-docs` 获取。

### 6. 手工回归接口（可选）

仓库里带了一份纯文本接口脚本 [`mall/http/mall-api.http`](./mall/http/mall-api.http)，覆盖注册登录、商品、订单、支付的主链路。

- 在 IDEA Ultimate 中打开后，点击请求左侧的绿色箭头即可执行；
- IDEA 免费版没有 HTTP Client，可用官方免费 CLI `ijhttp` 原样运行，或用 VS Code 的 REST Client（需要删掉响应脚本、手工填变量）；
- 完整用法、执行顺序、免费替代方案和常见问题见 [12-HTTP接口测试脚本使用说明](./docs/12-HTTP接口测试脚本使用说明.md)。

## 运行测试

测试共 72 条：68 条接口集成测试（覆盖店家、分类、商品、订单、支付、鉴权、接口文档七个模块）、3 条纯单元测试，外加 1 条上下文加载测试。主力是集成测试，用 `@SpringBootTest` + MockMvc 打真实 MySQL。

跑测试前先执行 `05-测试库初始化.sql` 建好 `mall_test` 库，测试只清空这个库，不会动开发库 `mall`。

商品详情缓存测试还会访问 Redis：请先启动本地 Memurai / Redis。测试使用 Redis `database 1`，并在每个用例前清理 `mall:product:*`，不会影响开发用的 `database 0`。

```powershell
cd mall
$env:DB_PASSWORD="你的MySQL密码"; mvn test
mvn test -Dtest=OrderControllerTest
mvn test "-Dtest=OrderControllerTest#下单成功"
```

三行命令依次是：跑全部、只跑一个测试类、只跑一个用例。

测试写成什么样、为什么以集成测试为主、事务回滚怎么验证，见 [10-测试方案与实施](./docs/10-测试方案与实施.md)；各模块该测哪些点，见 [11-测试用例清单](./docs/11-测试用例清单.md)。

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
- [x] 阶段六 · 订单管理模块
- [x] 阶段七 · 模拟支付
- [ ] 阶段八 · 进阶与工程化（进行中：参数校验统一处理、自动化测试、**JWT 登录鉴权**、**Redis 商品详情缓存**、**Swagger 接口文档**已完成；待做打包部署）

自动化测试 72 条全绿（`mvn test`，跑在独立测试库 `mall_test`）。

## 说明

- 本项目为个人学习用途，支付环节为**模拟实现**，不涉及任何真实资金流转。
- 所有接口统一返回 `{ code, message, data }` 结构；业务异常由全局处理器统一兜底，参数校验失败返回 `code=400` 并带具体字段提示。
- 金额字段一律使用 `BigDecimal`，与数据库的 `DECIMAL` 类型对齐。
- 开发库 `mall` 与测试库 `mall_test` 相互隔离，测试不会污染手工准备的数据。
- `/api/**` 接口除「注册、登录、支付回调」外均需登录：先调登录接口拿 JWT，后续请求带上 `Authorization: Bearer <token>`，否则返回 401。
