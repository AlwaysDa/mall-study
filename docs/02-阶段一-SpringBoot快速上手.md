# 02 · 阶段一：Spring Boot 快速上手

> 目标：跑通第一个 Spring Boot 项目，理解后端开发的「分层」与「REST API」，并写出一个基于内存数据的 CRUD 接口。  
> 验收标准：启动服务后，能用工具调用 4 个接口（增删改查）且数据在内存中正确变化。

## 一、本阶段要建立的世界观

后端开发的核心链路只有一句话：

```
HTTP 请求 → Controller 接收 → Service 处理 → 返回响应
```

- **Controller（控制层）**：只负责「接请求、回响应」，不做业务逻辑。
- **Service（业务层）**：负责业务逻辑、数据处理，是代码的重心。
- 数据暂时存在内存里（`List`/`Map`），阶段二才会真正落到数据库。

> 前端视角：Controller 就像你写的路由函数，Service 就像抽出来的业务逻辑模块。区别在于，后端多了「数据持久化」这一层（阶段二讲）。

## 二、创建第一个项目

### 方式 1：Spring Initializr（推荐）

1. 打开 <https://start.spring.io/>
2. 按如下配置：

| 配置项          | 值                                   |
| ------------ | ----------------------------------- |
| Project      | Maven                               |
| Language     | Java                                |
| Spring Boot  | 4.0.8（正式发布版；3.x 已从页面下架，有 4.1.1 也可选） |
| Group        | `com.mall`                          |
| Artifact     | `mall`                              |
| Java         | 17                                  |
| Dependencies | `Spring Web`、`Lombok`               |

1. 点击 Generate 下载，解压后用 IDEA 打开（`File → Open` 选解压目录）。

### 方式 2：IDEA 内置向导

`New Project → Spring Initializr`，配置同上。

### 项目结构初识

```
mall/
├── src/main/java/com/mall/
│   └── MallApplication.java        # 启动类（入口）
├── src/main/resources/
│   └── application.yml             # 配置文件
└── pom.xml                         # 依赖清单
```

先双击运行 `MallApplication.java` 的 `main` 方法，看到类似日志即启动成功：

```
Tomcat started on port 8080
```

## 三、第一个接口：Hello World

新建 `controller/HelloController.java`：

```java
package com.mall.mall.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/hello")
public class HelloController {

    @GetMapping
    public String hello() {
        return "Hello Mall!";
    }
}
```

用浏览器或工具访问 `http://localhost:8080/api/hello`，看到 `Hello Mall!` 即成功。

**注解速记：**

| 注解                              | 作用                 |
| ------------------------------- | ------------------ |
| `@RestController`               | 标记这是一个返回 JSON 的控制器 |
| `@RequestMapping("/api/hello")` | 给整个类加统一前缀          |
| `@GetMapping`                   | 处理 GET 请求          |

## 四、核心概念：分层与依赖注入

### 为什么分层

一个接口如果所有代码都写在 Controller 里，逻辑一复杂就变成「意大利面条」。分层是为了**职责分离**：

```
Controller（接请求） → Service（业务） → （阶段二的 Mapper，取数据）
```

### 依赖注入（Spring 的魔法）

Spring 容器负责创建和管理对象（叫 **Bean**）。你需要一个 Service 时，不用 `new`，而是用 `@Autowired` 注入：

```java
@RestController
@RequestMapping("/api/hello")
public class HelloController {

    private final HelloService helloService;

    // 构造器注入（推荐写法）
    public HelloController(HelloService helloService) {
        this.helloService = helloService;
    }

    @GetMapping
    public String hello() {
        return helloService.greet();   // 业务逻辑交给 Service
    }
}
```

```java
@Service
public class HelloService {
    public String greet() {
        return "Hello Mall from Service!";
    }
}
```

> 前端视角：`@Service` 约等于把逻辑抽成独立模块，`@Autowired` 约等于 `import` 并实例化——只不过实例化交给 Spring 自动完成了。

## 五、REST API 设计规范

REST 用「HTTP 方法 + 资源路径」表达操作意图：

| HTTP 方法 | 语义 | 示例                                                |
| ------- | -- | ------------------------------------------------- |
| GET     | 查询 | `GET /api/products` 查列表，`GET /api/products/1` 查单个 |
| POST    | 新增 | `POST /api/products`                              |
| PUT     | 更新 | `PUT /api/products/1`                             |
| DELETE  | 删除 | `DELETE /api/products/1`                          |

关键点：**路径用名词（资源），动作用 HTTP 方法表达**，不要写 `GET /api/getProducts` 这种动词路径。

## 六、实战：内存版商品 CRUD

用「商品」做第一个完整 CRUD（数据暂存内存，阶段二再落库）。

### 1. 建实体类（Entity）

```java
package com.mall.mall.entity;

import lombok.Data;

@Data                       // Lombok：自动生成 getter/setter/toString
public class Product {
    private Long id;
    private String name;
    private Double price;
    private Integer stock;
}
```

### 2. Service 层（内存存储）

```java
package com.mall.mall.service;

import com.mall.mall.entity.Product;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Service
public class ProductService {

    private final List<Product> store = new ArrayList<>();
    private final AtomicLong idGen = new AtomicLong(0);

    public List<Product> list() {
        return store;
    }

    public Product get(Long id) {
        return store.stream()
                .filter(p -> p.getId().equals(id))
                .findFirst()
                .orElseThrow(() -> new RuntimeException("商品不存在"));
    }

    public Product create(Product product) {
        product.setId(idGen.incrementAndGet());
        store.add(product);
        return product;
    }

    public Product update(Long id, Product product) {
        Product old = get(id);
        old.setName(product.getName());
        old.setPrice(product.getPrice());
        old.setStock(product.getStock());
        return old;
    }

    public void delete(Long id) {
        store.removeIf(p -> p.getId().equals(id));
    }
}
```

### 3. Controller 层

```java
package com.mall.mall.controller;

import com.mall.mall.entity.Product;
import com.mall.mall.service.ProductService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public List<Product> list() {
        return productService.list();
    }

    @GetMapping("/{id}")
    public Product get(@PathVariable Long id) {
        return productService.get(id);
    }

    @PostMapping
    public Product create(@RequestBody Product product) {
        return productService.create(product);
    }

    @PutMapping("/{id}")
    public Product update(@PathVariable Long id, @RequestBody Product product) {
        return productService.update(id, product);
    }

    @DeleteMapping("/{id}")
    public void delete(@PathVariable Long id) {
        productService.delete(id);
    }
}
```

**新注解速记：**

| 注解                      | 作用                |
| ----------------------- | ----------------- |
| `@PathVariable Long id` | 从 URL 路径取参数（`/1`） |
| `@RequestBody`          | 把请求体 JSON 自动转成对象  |
| `@Service`              | 标记业务层 Bean        |

### 4. 测试（用 Apifox / Postman）

| 操作  | 方法     | 地址                | 请求体示例                                       |
| --- | ------ | ----------------- | ------------------------------------------- |
| 新增  | POST   | `/api/products`   | `{"name":"手机","price":3999.0,"stock":10}`   |
| 查列表 | GET    | `/api/products`   | -                                           |
| 查单个 | GET    | `/api/products/1` | -                                           |
| 修改  | PUT    | `/api/products/1` | `{"name":"手机Pro","price":4299.0,"stock":8}` |
| 删除  | DELETE | `/api/products/1` | -                                           |

## 七、application.yml 基础配置

`src/main/resources/application.yml`：

```yaml
server:
  port: 8080          # 服务端口

spring:
  application:
    name: mall        # 应用名
```

改端口、改应用名都可以在这里完成。后续数据库、Redis 连接也会配在这里。

## 八、验收清单

- [x] 项目能启动，端口 8080
- [x] `GET /api/hello` 返回 `Hello Mall!`
- [x] 商品 CRUD 5 个接口全部调用成功
- [x] 能说清楚 Controller 和 Service 的职责区别
- [x] 能解释 `@Autowired` 依赖注入做了什么

## 九、常见坑

| 问题                               | 原因与解决                                    |
| -------------------------------- | ---------------------------------------- |
| 启动报 `Web server failed to start` | 端口被占用，改 `server.port` 或关掉占用进程            |
| 返回 404                           | 检查路径前缀（类上的 `@RequestMapping`）和方法上的路径是否拼对 |
| `@Data` 不生效 / 编译报找不到 getter      | 未安装或未启用 Lombok 插件                        |
| JSON 请求体没接到值                     | 忘记加 `@RequestBody`                       |
| 内存数据重启就没了                        | 正常现象，阶段二落库后解决                            |

---

完成后进入 [阶段二：数据库与持久层](./03-阶段二-数据库设计与持久层.md)。
