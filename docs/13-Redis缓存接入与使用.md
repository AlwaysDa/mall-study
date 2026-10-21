# 13 · Redis 缓存：接入、使用与三大问题

> 目标：把 Redis 真正接进项目——能启动、能连上、能缓存商品详情、能验证失效，并知道使用时容易踩哪些坑。
> 前置：读过 [09-阶段八-进阶与工程化](./09-阶段八-进阶与工程化.md) 的 Redis 缓存一节，本地能启动 mall 应用和 MySQL。
> 说明：环境部分同时给出 Windows 原生（Memurai）和 Docker 两种方式，Linux / macOS 同理；代码按本项目 Spring Boot 4.1.1 + Java 21 编写。

## 一、Redis 在后端项目里做什么

Redis 是一个内存键值数据库。后端项目里常见的用途：

| 用途 | 说明 | 典型数据结构 |
| --- | --- | --- |
| 缓存热点数据 | 读多写少、查询代价高的数据，如商品详情 | String（JSON） |
| 分布式锁 | 多实例之间互斥，如缓存重建、库存操作 | String（SET NX） |
| 计数器 | 访问量、点赞数，自增是原子操作 | String（INCR） |
| 排行榜 / 延时任务 | 按分数排序、按时间取任务 | ZSet |
| 会话共享 | 多实例共享登录状态（本项目用 JWT，不需要） | String / Hash |

本项目的定位要先说清楚：

- **MySQL 是事实来源，Redis 是加速层**。缓存可以过期、可以丢、可以因内存不足被淘汰；
- 订单、支付、库存的最终正确性必须由数据库事务和唯一约束保证，不能只放在 Redis 里；
- 适合缓存：读多写少、查询代价高、能接受短暂旧数据；
- 不适合缓存：强一致、频繁写、必须实时准确的数据（如余额、库存扣减的唯一依据）。

## 二、环境准备

### 1. 选择安装方式

Redis 官方不提供 Windows 原生版本，网上的旧版 Redis for Windows 多为 3.x，不建议用于新项目。Windows 上可选：

- **Memurai（Windows 原生，推荐）**：以 Windows 服务方式运行，不依赖 WSL / Docker，安装和占用都小；适合 C 盘空间紧张、只想本地跑 Redis 的情况；
- **Docker Desktop**：版本可控、卸载干净，但需要 WSL2，通常占用 GB 级空间；
- **WSL2**：不想装 Docker 时使用，需要启用 WSL 和虚拟机平台。

> 三者只需要选一种，不要让 Memurai 和 Docker 里的 Redis 同时占用 6379 端口。

### 2. Memurai 安装（Windows 原生，推荐）

1. 打开 <https://www.memurai.com/get-memurai>，下载 **Developer Edition**（免费，仅限开发/测试；官方限制包括 10 天会重启、最多使用 50% 内存和 10 个 IP，生产环境需要 Enterprise Edition）；
2. 右键安装包，以管理员身份运行。安装时可以：
   - 修改安装目录，C 盘紧张可装到 `D:\Memurai`；
   - 保持端口 `6379`；
   - 勾选安装为 Windows 服务（默认会随 Windows 自动启动）；
   - 本地开发可以不创建防火墙规则；如果创建，只允许本机访问。
3. 安装完成后验证：

```powershell
Get-Service Memurai
memurai-cli ping
```

返回 `PONG` 即成功。服务没有启动时，在管理员 PowerShell 中执行：

```powershell
Start-Service Memurai
# 或
net start Memurai
```

`memurai-cli` 不在 PATH 时，用安装目录下的完整路径：

```powershell
& "C:\Program Files\Memurai\memurai-cli.exe" ping
# 装在其他目录时换成实际路径，例如 F:\Program Files\Memurai
```

常用服务命令：

```powershell
Restart-Service Memurai
Stop-Service Memurai
```

> Memurai 与 Redis 协议兼容（本次实际安装为 Memurai 8.2.0，API 8.2.10；具体版本以安装包为准），本项目用到的 `GET`、`SET`、`DEL`、`TTL`、`EXPIRE` 等命令与 Redis 完全一致；配置文件是安装目录下的 `memurai.conf`，修改后需要重启服务。若安装时没有勾选服务，也可以用 `memurai.exe` 手动运行。
>
> 安全软件提示：360 等杀毒软件可能把 `memurai-cli.exe` 误判并隔离。发现安装目录缺少这个文件时，从隔离区恢复，并把 Memurai 安装目录加入信任区/白名单。

### 3. Docker 启动（可选）

```bash
docker run -d --name mall-redis \
  -p 127.0.0.1:6379:6379 \
  redis:7.4
```

说明：

- `127.0.0.1:6379:6379` 只把端口绑定到本机，避免无密码的 Redis 暴露到局域网；
- `--name mall-redis` 方便后续进入容器；
- `redis:7.4` 固定版本，避免 `latest` 悄悄升级。

验证：

```bash
docker ps --filter name=mall-redis
docker exec -it mall-redis redis-cli ping
```

返回 `PONG` 即启动成功。

### 4. WSL2 启动（备选）

```bash
sudo apt update
sudo apt install -y redis-server
sudo service redis-server start
redis-cli ping
```

### 5. 基本命令

下面以 Memurai 的 `memurai-cli` 为例；Docker 方案把 `memurai-cli` 换成 `docker exec -it mall-redis redis-cli` 即可：

```bash
memurai-cli ping
memurai-cli set mall:test hello
memurai-cli get mall:test
memurai-cli ttl mall:test     # -1 表示没有设置过期时间；-2 表示 key 不存在
memurai-cli expire mall:test 60
memurai-cli ttl mall:test     # 0~60 秒
memurai-cli del mall:test
```

两种方式的命令语义完全一致，只是客户端入口不同。Windows 下装了 Memurai 后通常可以直接使用 `memurai-cli`；没有加入 PATH 时用完整路径。

### 6. 设置密码（可选）

本地学习可以不设密码。Memurai 的做法是修改安装目录下的 `memurai.conf`，找到或添加：

```conf
requirepass dev-only-password
```

保存后在管理员 PowerShell 中重启服务：

```powershell
Restart-Service Memurai
memurai-cli -a "dev-only-password" ping
```

Docker 方式则在启动容器时传入密码：

```bash
docker run -d --name mall-redis \
  -p 127.0.0.1:6379:6379 \
  redis:7.4 \
  redis-server --requirepass "dev-only-password"

docker exec -it mall-redis redis-cli -a "dev-only-password" ping
```

密码要通过环境变量传给应用，不要写进提交到仓库的配置文件。

### 7. Docker 与 Memurai 的差异

| 维度 | Memurai（Windows 原生） | Docker Desktop |
| --- | --- | --- |
| 安装 | MSI 安装包，可装成 Windows 服务 | 先装 Docker Desktop，再拉取 Redis 镜像 |
| 启动方式 | 随 Windows 自动启动，用服务管理 | 先启动 Docker Desktop，再启动容器 |
| 命令行 | `memurai-cli` | `docker exec -it mall-redis redis-cli` |
| 配置 | 改 `memurai.conf` 后重启服务 | 容器参数、环境变量或挂载配置文件 |
| 数据 | 由 Windows 服务管理，默认保留 | 容器删除后，未挂载卷的数据会丢失 |
| 端口 | 直接监听 6379，不需要映射 | 需要 `-p 6379:6379` 映射到宿主机 |
| 资源占用 | 原生进程，占用较小 | WSL2 + Docker，通常 GB 级 |
| 版本 | 示例为 Memurai 8.2.0（Redis API 8.2.10） | 示例使用 Redis 7.4 |
| 适合场景 | Windows 本地开发、C 盘空间紧张 | 跨平台、CI、部署演练 |

> 切换方案时先停掉另一个：`Stop-Service Memurai` 或 `docker stop mall-redis`，否则 6379 端口会冲突。Memurai 的配置修改需要管理员权限，改完必须重启服务；Docker 的配置改动通常需要重建容器。

## 三、Spring Boot 接入

### 1. 添加依赖

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-data-redis</artifactId>
</dependency>
```

版本由 Spring Boot 管理，不需要手写版本号。默认客户端是 Lettuce。

### 2. 配置连接

在 `application.yml` 中增加：

```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD:}
      database: 0
      timeout: 3s
```

几个点：

- `REDIS_HOST` / `REDIS_PORT` / `REDIS_PASSWORD` 都可以用环境变量覆盖，和数据库密码一个套路；
- `timeout` 很重要。Redis 不可用时，请求不能一直挂着，学习项目设 2~3 秒即可；
- `database: 0` 是默认库。Redis 的 database 编号不是严格隔离，多个项目共用实例时更推荐用 key 前缀区分。

### 3. 用哪个模板

Spring Boot 会自动提供两个模板：

| 模板 | 特点 | 建议 |
| --- | --- | --- |
| `StringRedisTemplate` | key 和 value 都按字符串处理 | **推荐**，配合 JSON 存对象，`redis-cli` 里也能直接看懂 |
| `RedisTemplate` | 可以存任意对象，默认使用 JDK 序列化 | 初学先别用；默认序列化后 `redis-cli` 看到的是二进制乱码 |

本文统一使用 `StringRedisTemplate`：对象先用 Jackson 转成 JSON 字符串，再存入 Redis。

### 4. 验证 Spring 能否连上

写一个临时测试（需要 Redis 正在运行）：

```java
@SpringBootTest
class RedisConnectionTest {

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Test
    void 能读写Redis() {
        redisTemplate.opsForValue().set("mall:test", "ok", Duration.ofMinutes(1));
        assertThat(redisTemplate.opsForValue().get("mall:test")).isEqualTo("ok");
        redisTemplate.delete("mall:test");
    }
}
```

> 这类测试会让 `mvn test` 依赖 Redis。验证完可以删掉，或加 `@Disabled` 只在本地手动运行。不要让整个测试套件默认依赖一个外部服务，除非 CI 里也会启动 Redis。

## 四、用缓存改造商品详情

商品详情是典型的读多写少场景，[ProductService](../mall/src/main/java/com/mall/mall/service/ProductService.java) 里的 `getProduct` 适合先做缓存。

### 1. 先定 Key 和 TTL

```java
private static final String PRODUCT_KEY_PREFIX = "mall:product:";
private static final String NULL_VALUE = "__NULL__";
private static final Duration NULL_TTL = Duration.ofMinutes(2);
private static final long PRODUCT_TTL_SECONDS = 30 * 60;
```

命名约定：

- 用冒号分层：`mall:product:123`，第一段是项目/业务命名空间；
- 中间不要有空格，长度适中，不要用用户输入直接拼 key；
- key 里带业务含义，方便排查。

TTL 建议 30 分钟左右，并加一点随机抖动；`NULL_VALUE` 是「查不到」的占位值，用来防缓存穿透。

### 2. 改造读逻辑（Cache-Aside）

先查缓存，命中就返回；未命中再查数据库，把结果写回缓存：

```java
private final StringRedisTemplate redisTemplate;
private final ObjectMapper objectMapper;

// 构造器里注入 redisTemplate 和 objectMapper

public ProductVO getProduct(Long id) {
    String key = PRODUCT_KEY_PREFIX + id;
    String cached = redisTemplate.opsForValue().get(key);

    if (cached != null) {
        if (NULL_VALUE.equals(cached)) {
            throw new BusinessException("商品不存在");
        }
        try {
            return objectMapper.readValue(cached, ProductVO.class);
        } catch (Exception e) {
            redisTemplate.delete(key);   // 缓存内容损坏：删掉，按未命中处理
        }
    }

    Product product = this.getById(id);
    if (product == null) {
        // 空值缓存：短 TTL，避免同一个不存在的 id 反复打库
        redisTemplate.opsForValue().set(key, NULL_VALUE, NULL_TTL);
        throw new BusinessException("商品不存在");
    }

    ProductVO vo = toVO(product);
    long ttlSeconds = PRODUCT_TTL_SECONDS + ThreadLocalRandom.current().nextLong(300);
    try {
        redisTemplate.opsForValue().set(
                key, objectMapper.writeValueAsString(vo), Duration.ofSeconds(ttlSeconds));
    } catch (Exception e) {
        // 缓存写失败不影响本次返回，下次读还会回源数据库
    }
    return vo;
}
```

说明：

- 本项目使用 Spring Boot 4，`ObjectMapper` 是 Jackson 3 的 `tools.jackson.databind.ObjectMapper`，不要误用旧包 `com.fasterxml.jackson.databind.ObjectMapper`；
- 缓存里存 `ProductVO` 的 JSON，而不是实体 `Product`，因为 VO 多了一个 `categoryName`，而且不会把数据库实体直接暴露出去；
- 示例让缓存读异常直接暴露，方便学习时发现环境问题。生产环境更稳妥的做法是捕获异常并回源数据库，见第七节；
- `ThreadLocalRandom.current().nextLong(300)` 给 TTL 加 0~5 分钟随机值，避免一批商品缓存同时过期。

### 3. 写操作要删缓存

更新数据库后，缓存里的旧值必须失效。**先更新数据库，再删除缓存**：

```java
private void evictProductCache(Long id) {
    redisTemplate.delete(PRODUCT_KEY_PREFIX + id);
}
```

本项目中需要调用它的位置：

| 操作 | 为什么 |
| --- | --- |
| `updateProduct` | 名称、价格、描述、分类等字段都可能变化 |
| `changeStatus` | 上架/下架状态在 `ProductVO` 里 |
| `stockIn` | 库存也在 `ProductVO` 里 |
| 删除商品 | 商品不存在后，缓存不能继续返回旧数据；`deleteProduct` 删除数据库记录后清理缓存 |
| 修改分类名 | `ProductVO.categoryName` 来自分类表，分类改名后商品缓存也会变旧；`updateCategory` 会清掉该分类下所有商品缓存 |
| 下单 / 取消订单 | 扣减或回补库存后，商品缓存里的 `stock` 会变旧；`OrderService` 在事务提交后统一清理受影响商品的缓存 |

为什么是「删除」而不是「更新缓存」：

- 更新缓存需要处理并发写的顺序，容易把旧值写回去；
- 删除更简单，下一次读自然重建；代价是下一次读会多一次数据库查询。

> 如果更新方法带 `@Transactional`，更稳妥的做法是**事务提交后再删缓存**。在事务里删缓存，可能出现「缓存已删、事务还没提交，另一个请求读到旧数据库值又写回缓存」的竞态；如果是在事务里写缓存，事务回滚后还会留下与数据库不一致的值。本项目的下单/取消订单就属于这种情况：`OrderService` 收集受影响的商品 id，通过 `ProductService.evictProductCachesAfterCommit` 注册 `TransactionSynchronization.afterCommit()`，事务提交成功后才删缓存。

> 缓存删除属于「尽力而为」：删除失败只吞掉异常，不回滚数据库。数据库已是新值，最坏情况是旧缓存多存活到 TTL 到期。

### 4. 验证缓存是否生效

下面以 Memurai 为例；Docker 方案把 `memurai-cli` 换成 `docker exec -it mall-redis redis-cli`：

```bash
# 1. 第一次请求前，key 不存在
memurai-cli get mall:product:1

# 2. 调用 GET /api/products/1，然后再查
memurai-cli get mall:product:1      # 应看到 JSON
memurai-cli ttl mall:product:1      # 应看到 1800 左右的秒数

# 3. 调用修改接口后，key 应被删除
memurai-cli exists mall:product:1   # 返回 0

# 4. 查询不存在的商品后，应出现空值占位
memurai-cli get mall:product:999999 # 应看到 "__NULL__"
memurai-cli ttl mall:product:999999 # 约 120 秒
```

想确认「第二次请求没有查数据库」，可以临时打开 MyBatis 的 SQL 日志，或在开发环境用 `memurai-cli monitor` 观察读写。生产环境不要长期开着 `monitor`，它会影响性能。

## 五、Key、序列化与内存注意事项

- **key 必须有业务前缀**：`mall:product:`、`mall:order:`，不要直接用 `user:1` 这种容易冲突的名字；
- **不要用 `KEYS *` 遍历**：Redis 是单线程处理命令，`KEYS` 会阻塞其他请求；开发时可以用 `SCAN`，生产环境禁止使用；
- **避免大 key**：不要把整个商品列表塞进一个 key；单个 value 过大时，读写和删除都会变慢；
- **避免热 key**：某个 key 的 QPS 远高于其他 key 时，考虑本地缓存、拆分 key 或读写分离；
- **TTL 不是可选项**：纯缓存数据都应设置过期时间，否则内存会一直增长到被淘汰；
- **不要把 Redis 当数据库**：内存满了会按淘汰策略删数据，重启也可能丢失（取决于持久化配置），数据库才是最终依据；
- **序列化要稳定**：用 JSON 存对象时，字段增删要能兼容旧缓存；解析失败就删掉缓存回源，不要让坏数据一直卡住接口；
- **不同环境加前缀**：本地、测试、生产如果共用一个 Redis，用 `mall:dev:`、`mall:test:` 之类的前缀隔离，比只靠 database 编号可靠。

## 六、缓存三大问题

前面的空值缓存和随机 TTL 已经用到了两大问题的解法，这里把三个问题系统梳理一遍。

### 1. 缓存穿透：查不存在的数据

**现象**：请求的 id 在缓存和数据库里都不存在，每次请求都穿过缓存直接打数据库。

**典型场景**：

- 恶意用户用随机 id 反复请求，例如 `/api/products/99999999`；
- 已删除的商品被旧链接反复访问。

**常用方案**：

1. **缓存空值**：查不到也写一个占位值，TTL 设短一些（如 1~5 分钟）；上面的 `NULL_VALUE` 就是这种做法。
2. **布隆过滤器**：在查缓存前判断 key 是否可能存在。它说「不存在」就一定不存在，说「存在」有误判概率，能挡掉大部分非法请求。
3. **参数校验与限流**：id 为空、为负数、格式不对的直接拒绝，从入口减少无效请求。

> 空值缓存会带来短暂不一致：如果这个 id 后来真的创建了数据，旧空值要等 TTL 到期或被主动删除，所以 TTL 不宜太长。

### 2. 缓存击穿：一个热点 key 失效

**现象**：某个访问量很高的 key 恰好过期，大量并发请求同时未命中，一起涌向数据库。

**典型场景**：首页爆款商品的缓存设了 30 分钟过期，过期瞬间来了一万次请求，全部去查数据库。

**和穿透的区别**：数据是**存在**的，只是缓存暂时没有。它是「一个点」被击穿，不是「不存在的数据」反复穿过去。

**常用方案**：

1. **互斥锁**：只让一个请求去数据库重建缓存，其他请求等待或短暂重试。单机可以用 `synchronized`，多实例部署要用 Redis 分布式锁（如 `SET key value NX PX`，或用 Redisson）。
2. **逻辑过期**：缓存不设物理 TTL，而是在 value 里额外存一个过期时间；发现过期后先返回旧值，同时异步刷新缓存。实现更复杂，但能避免请求等待。
3. **热点数据永不过期 + 后台刷新**：适合能接受短暂旧值的场景，运维和代码复杂度也更高。

> 互斥锁要设置锁超时和释放逻辑，否则一个请求卡住，其他请求会一直等；释放锁时还要确认锁是自己的，避免误删别人的锁。

### 3. 缓存雪崩：大面积失效或缓存不可用

**现象**：大量 key 在同一时间过期，或者 Redis 整体宕机，所有读请求都落到数据库上。

**和击穿的区别**：击穿是「一个热点 key」，雪崩是「一批 key 或整个缓存层」。后者影响范围更大，严重时会把数据库一起拖垮。

**常用方案**：

1. **TTL 加随机抖动**：不要让一批缓存同时过期，例如 30 分钟 + 随机 0~5 分钟；
2. **Redis 高可用**：主从、哨兵或集群，降低整体不可用的概率；持久化和故障转移也要配合；
3. **限流、熔断、降级**：缓存不可用时保护数据库，必要时返回默认值或友好提示，而不是放全部流量进去；
4. **多级缓存**：本地缓存（如 Caffeine）+ Redis，减少对单一缓存层的依赖；
5. **缓存预热**：服务启动或活动开始前提前加载热点数据，避免冷启动时集中打库。

### 4. 三者对比

| 问题 | 数据是否存在 | 触发点 | 影响范围 | 常用方案 |
| --- | --- | --- | --- | --- |
| 缓存穿透 | 不存在 | 查询根本不存在的 key | 单个或大量伪造请求 | 空值缓存、布隆过滤器、参数校验 |
| 缓存击穿 | 存在 | 单个热点 key 过期 | 一个热点 | 互斥锁、逻辑过期、热点永不过期 |
| 缓存雪崩 | 存在 | 大量 key 同时过期或 Redis 故障 | 整体 | 随机 TTL、高可用、限流降级、多级缓存 |

记忆方法：**穿透是「没有」，击穿是「一个」，雪崩是「一片」**。

## 七、其他常见问题与使用注意

### 1. 常见故障排查

| 现象 | 常见原因 | 处理 |
| --- | --- | --- |
| `Connection refused` | Memurai 服务没启动、端口不对、Docker 没映射端口 | 先 `memurai-cli ping`（Docker 用 `docker exec -it mall-redis redis-cli ping`），再检查 host/port |
| `Start-Service` 报 `Cannot start service` / 系统找不到指定的文件 | Windows 服务注册项被清理（常见于安全软件误处理） | 把安装目录加入安全软件信任区，管理员运行安装包选择「修复」或重装，并勾选安装为 Windows 服务 |
| `memurai-cli` 找不到或安装目录缺少该文件 | 360 等杀毒软件误隔离 | 从隔离区恢复，并把 Memurai 安装目录加入信任区/白名单 |
| `NOAUTH Authentication required` | Redis 设了密码，应用没配 | 配置 `spring.data.redis.password` 或环境变量 |
| 请求一直卡住 | Redis 连接没有超时 | 配置 `spring.data.redis.timeout`，并检查网络 |
| `memurai-cli` / `redis-cli` 看到乱码 | 用了 `RedisTemplate` 的默认 JDK 序列化 | 改用 `StringRedisTemplate` + JSON |
| 更新后仍读到旧值 | 忘了删缓存、TTL 太长、分类名等关联数据变了 | 检查失效点，尤其是 `categoryName` 这类联表字段 |
| 不存在的 id 反复打库 | 没有缓存空值 | 加短 TTL 的 `__NULL__` 占位 |
| 内存占用持续上涨 | key 没设 TTL，或数据量超出预期 | 给缓存加 TTL，检查大 key，规划内存和淘汰策略 |
| Redis 挂了业务全挂 | 把缓存当成了强依赖 | 捕获缓存异常并回源数据库，设置超时和降级 |

### 2. Redis 不可用时怎么办

缓存是加速层，不是业务的前提。生产环境更推荐：

```java
try {
    String cached = redisTemplate.opsForValue().get(key);
    // ... 命中处理
} catch (Exception e) {
    // 记录日志，继续走数据库
}
```

写缓存失败也不应该让主流程失败，最坏情况是这次请求变慢一些。学习阶段可以先让异常暴露，方便发现 Redis 没启动；上线前再补上降级逻辑。

### 3. 内存与淘汰策略

Redis 内存是有限的，需要关注：

- `maxmemory`：给 Redis 设置最大内存；
- `maxmemory-policy`：纯缓存场景通常选 `allkeys-lru` 或 `allkeys-lfu`；默认的 `noeviction` 在内存满时会拒绝写入；
- 监控大 key 和热 key：开发时可用 `memurai-cli --bigkeys`（Docker 用 `docker exec -it mall-redis redis-cli --bigkeys`），生产环境用监控工具，不要在高峰期跑扫描命令。

### 4. 安全

**无密码的风险取决于「谁能连上」**：Redis 默认 `bind 127.0.0.1` + `protected-mode yes`，只有本机能连，本地学习不设密码是可以接受的；一旦改成 `bind 0.0.0.0`、在路由器/云安全组开放 6379，或 Docker 用 `-p 6379:6379` 暴露到公网，又没密码，就很容易被扫描并利用（读写数据、通过 `CONFIG SET dir` 写文件、用 `MODULE LOAD` 加载恶意模块等）。需要远程访问时，优先用 SSH 隧道或 VPN，并同时启用密码、ACL、TLS 和 IP 白名单。

检查当前暴露面：

```bash
memurai-cli config get bind
memurai-cli config get protected-mode
netstat -ano | findstr :6379
```

- 本地开发把 Redis 绑定在 `127.0.0.1`，不要直接暴露公网；
- 设置 `requirepass`，密码用环境变量注入；
- 生产环境加防火墙，跨机器访问时考虑 TLS；
- 禁用或重命名 `FLUSHALL`、`FLUSHDB`、`KEYS` 等危险命令；
- 不要用 Redis 存明文密码、支付凭证等敏感信息。

### 5. 测试注意事项

商品详情接入 Redis 后，商品相关测试也会走到缓存逻辑。本项目当前的处理方式：

- 测试配置使用 Redis `database 1`，避免影响开发用的 `database 0`；
- `BaseIntegrationTest` 在每个用例前清理 `mall:product:*`，避免缓存跨用例污染；
- `ProductControllerTest` 的 P-15 ~ P-18 覆盖缓存写入、写后失效、空值占位和命中不回源；
- 本地跑 `mvn test` 前要先启动 Memurai / Redis，否则测试会因连接失败而挂；
- 当前没有对 Redis 异常做降级，Redis 停止时商品详情接口会直接报连接错误；学习阶段先保证 Redis 运行，生产环境再按第七节补降级；
- 不希望对 Redis 有依赖时，可以在测试里 mock `StringRedisTemplate`，或使用单独的测试配置；以后也可以用 Testcontainers 自动启动临时 Redis。

## 八、接入步骤与验收

按顺序做：

1. 启动 Redis（Memurai 服务或 Docker 容器），`ping` 返回 `PONG`；
2. 加依赖、配连接，启动应用不报 connection refused；
3. 改造 `getProduct` 的读逻辑，确认第一次请求后 key 出现、TTL 正确；
4. 在修改、上下架、入库、删除等写操作后删缓存，确认 `exists` 返回 0；
5. 查询不存在的商品，确认出现短 TTL 的 `__NULL__`；
6. 可选：补 Redis 异常时的降级，保证 Redis 挂掉时还能回源数据库；
7. 跑一遍商品相关测试，确认原有功能没有被缓存改坏。

> 本项目进度：依赖、连接配置、商品缓存读取与各类失效点已落地；实现位置是 `ProductService`（缓存读取与商品写操作失效）、`OrderService`（下单/取消后的库存失效）、`CategoryService`（分类改名后的失效）。自动化用例 P-15 ~ P-18 已覆盖读写与失效，`mvn test` 共 70 条全绿。Redis 异常降级（第 6 步）暂未实现；集成测试需要在本地启动 Memurai / Redis 并配置 `DB_PASSWORD` 后执行。

完成后的自检：

- 能说清楚这个 key 为什么这样命名、TTL 为什么是这个值；
- 能指出商品数据变化时，哪些入口需要让缓存失效；
- 能解释空值缓存和随机 TTL 分别解决了哪个问题；
- 知道 Redis 挂掉时接口应该怎样表现。

## 九、小结

- Redis 是加速层，MySQL 才是事实来源；缓存数据可以丢，业务正确性不能只靠缓存；
- 先从 `StringRedisTemplate` + JSON + TTL 开始，不要一上来就引入复杂的序列化配置和缓存框架；
- 写操作记住「先更新数据库，再删除缓存」，并覆盖所有会影响展示数据的入口；
- 穿透用空值缓存或布隆过滤器，击穿用互斥锁或逻辑过期，雪崩用随机 TTL、高可用和限流降级；
- 先把缓存跑通并验证失效，再考虑锁、布隆过滤器、多级缓存这些更复杂的方案。
