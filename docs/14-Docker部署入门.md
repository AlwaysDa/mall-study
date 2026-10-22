# 14 · Docker 部署入门

> 目标：用最少的命令把本项目打包成镜像、跑成容器，并知道 Docker 适合解决什么问题、不适合解决什么问题。
> 前置：已经能用 `mvn clean package` 打出 jar，并成功用 `java -jar` 启动过应用。
>
> Docker 是**选学内容**。本地开发直接 `java -jar` + Memurai 更轻量；Docker 的价值在于「换一台机器也能用同样的方式跑起来」。

## 一、Docker 解决什么问题

同一个 Java 项目，换一台电脑经常遇到：

- 这台机器是 JDK 21，那台机器还是 JDK 17；
- MySQL 版本、字符集、时区配置不一样；
- 部署时要手工装 JDK、传 jar、配环境变量，步骤容易漏；
- 想同时跑 MySQL、Redis 和应用，端口和配置越来越乱。

Docker 把这些东西打包成**镜像**，运行时变成**容器**。镜像里已经包含应用和它需要的运行环境，换机器只要拉取镜像并启动容器。

| 概念 | 通俗理解 | 本项目对应 |
| --- | --- | --- |
| 镜像 image | 只读的安装包/模板 | 包含 JDK 21 + `mall-0.0.1-SNAPSHOT.jar` 的 `mall-app:1.0` |
| 容器 container | 镜像跑起来后的进程 | 正在监听 8080 的商城应用 |
| Dockerfile | 构建镜像的步骤说明 | `FROM ...`、`COPY ...`、`ENTRYPOINT ...` |
| 数据卷 volume | 独立于容器生命周期的数据目录 | MySQL 数据目录，避免删容器就丢数据 |
| 端口映射 | 把容器端口暴露到宿主机 | `-p 8080:8080` |
| Docker Compose | 用 YAML 一次启动多个容器 | 应用 + Redis + MySQL 一起启动 |

**适合用 Docker 的场景**：部署到服务器、团队统一环境、需要同时跑多个中间件。
**不适合的场景**：只是想本地改代码看效果。改一次代码就要重新构建镜像，反而不如 IDE 直接运行快。

## 二、安装与检查

Windows / macOS 安装 **Docker Desktop**；Linux 安装 Docker Engine 和 Compose 插件。这里以 Windows 为例。

### 1. Windows 前置条件

- Windows 10 64 位（建议 21H2 及以上）或 Windows 11；
- BIOS 中开启 CPU 虚拟化；
- 安装 WSL2 或启用 Hyper-V（Docker Desktop 现在默认使用 WSL2）；
- 安装时需要管理员权限，装完通常要重启。

如果提示「Virtual Machine Platform 未启用」，在**管理员 PowerShell**执行：

```powershell
wsl --install
```

然后重启电脑。也可以手动在「启用或关闭 Windows 功能」中勾选「适用于 Linux 的 Windows 子系统」和「虚拟机平台」。

> C 盘空间紧张时要注意：Docker Desktop、WSL 发行版和镜像默认都在 C 盘，可能占用数 GB。可以在 Docker Desktop 的 **Settings → Resources → Advanced** 中把磁盘镜像位置改到其他盘；如果空间实在不够，跳过 Docker，继续用 jar + Memurai 即可。

### 2. 验证安装

```bash
docker version
docker run --rm hello-world
```

能正常输出版本信息并看到 `Hello from Docker!`，说明 Docker 可用。

## 三、给项目构建镜像

### 1. 先打出 jar

Docker 镜像里只放运行产物，不在镜像里跑 Maven：

```bash
cd mall
mvn clean package
# 产物：target/mall-0.0.1-SNAPSHOT.jar
```

### 2. 新建 Dockerfile

在 `mall/` 目录下新建 `Dockerfile`：

```dockerfile
# 运行环境：项目使用 Java 21，必须选 21 的 JRE 镜像
FROM eclipse-temurin:21-jre

WORKDIR /app

# 只复制 Spring Boot 重新打包后的可执行 jar
COPY target/mall-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app.jar"]
```

几点说明：

- 用 `eclipse-temurin:21-jre` 而不是 `jdk`，运行阶段不需要编译器，镜像更小；
- 不要直接复制 `target/classes`，Spring Boot 的依赖都在可执行 jar 里；
- 不要把数据库密码、JWT 密钥写进 Dockerfile，运行时用环境变量注入；
- 如果项目改了 jar 名称，`COPY` 那一行要同步修改。

可选：在 `mall/` 下新建 `.dockerignore`，减少发送给 Docker 的文件：

```text
.idea
target/classes
target/test-classes
target/surefire-reports
target/generated-sources
target/generated-test-sources
```

不要忽略整个 `target/`，因为 `target/mall-0.0.1-SNAPSHOT.jar` 正是镜像需要的文件。

### 3. 构建镜像

在 `mall/` 目录执行：

```bash
docker build -t mall-app:1.0 .
```

- `-t mall-app:1.0`：给镜像起名字和版本；
- 最后的 `.`：构建上下文是当前目录，Docker 会读取当前目录的 `Dockerfile`；
- 第一次构建会下载基础镜像，之后会复用缓存，速度快很多。

查看镜像：

```bash
docker images | grep mall-app
```

## 四、运行容器

容器里的 `localhost` 指**容器自己**，不是你的电脑。连接宿主机上的 MySQL / Redis 时，Windows 和 macOS 的 Docker Desktop 用 `host.docker.internal`：

```bash
docker run -d --name mall-app \
  -p 8080:8080 \
  -e DB_PASSWORD=你的MySQL密码 \
  -e JWT_SECRET=至少32字符的随机密钥 \
  -e SPRING_DATASOURCE_URL='jdbc:mysql://host.docker.internal:3306/mall?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai' \
  -e REDIS_HOST=host.docker.internal \
  mall-app:1.0
```

参数解释：

| 参数 | 作用 |
| --- | --- |
| `-d` | 后台运行 |
| `--name mall-app` | 给容器起名，方便查看日志和停止 |
| `-p 8080:8080` | 宿主机 8080 → 容器 8080 |
| `-e` | 注入环境变量，对应 `application.yml` 里的占位符 |
| `mall-app:1.0` | 使用的镜像 |

Linux 上默认没有 `host.docker.internal`，可以加：

```bash
--add-host=host.docker.internal:host-gateway
```

或者改用 `--network host`，让容器直接使用宿主机网络。

### 1. 验证

```bash
docker ps
docker logs -f mall-app

curl http://localhost:8080/v3/api-docs
```

浏览器打开 `http://localhost:8080/swagger-ui.html`，能看到接口文档说明应用启动成功。

如果日志里报数据库连接失败，先确认：

- MySQL 正在宿主机运行；
- 连接地址用的是 `host.docker.internal`，不是 `localhost`；
- MySQL 用户允许从容器所在网段连接。MySQL 的 `root@localhost` 只允许本机登录，容器访问时来源会变成 Docker 网段，需要单独建用户并授权，例如：
  ```sql
  CREATE USER 'mall'@'%' IDENTIFIED BY '你的密码';
  GRANT ALL PRIVILEGES ON mall.* TO 'mall'@'%';
  FLUSH PRIVILEGES;
  ```
  然后把 `DB_USERNAME` 改成 `mall`。生产环境不要给 `root` 开放 `%`；
- 3306 端口没有被防火墙拦截。

同理，如果宿主机的 Memurai 只监听 `127.0.0.1`，容器通过 `host.docker.internal` 也可能连不上。最省事的做法是让 MySQL、Redis 和应用都跑在 Compose 的同一个网络里（见下一节），或者继续在宿主机用 `java -jar` 运行应用。

### 2. 常用命令

```bash
docker ps                         # 查看运行中的容器
docker logs -f mall-app           # 持续查看日志，Ctrl+C 退出
docker exec -it mall-app sh       # 进入容器排查问题
docker stop mall-app              # 停止
docker rm mall-app                # 删除容器（镜像仍在）
docker rmi mall-app:1.0           # 删除镜像
```

代码改动后需要重新打包并重建镜像：

```bash
mvn clean package
docker stop mall-app && docker rm mall-app
docker build -t mall-app:1.0 .
# 再执行上面的 docker run
```

## 五、用 Compose 管理多个容器（可选）

如果应用、Redis、MySQL 都要跑在 Docker 里，`docker run` 会越来越长。可以在项目根目录写 `compose.yaml`，用一条命令启动：

```yaml
services:
  app:
    build: ./mall
    ports:
      - "8080:8080"
    environment:
      DB_PASSWORD: ${DB_PASSWORD}
      JWT_SECRET: ${JWT_SECRET}
      SPRING_DATASOURCE_URL: jdbc:mysql://mysql:3306/mall?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai
      REDIS_HOST: redis
    depends_on:
      - mysql
      - redis

  mysql:
    image: mysql:8.4
    environment:
      MYSQL_ROOT_PASSWORD: ${DB_PASSWORD}
      MYSQL_DATABASE: mall
    volumes:
      - mysql-data:/var/lib/mysql
      - ./mall/sql/01-schema.sql:/docker-entrypoint-initdb.d/01-schema.sql:ro
      - ./mall/sql/03-阶段三-商城建表.sql:/docker-entrypoint-initdb.d/03-schema.sql:ro

  redis:
    image: redis:7.4-alpine

volumes:
  mysql-data:
```

MySQL 镜像会在数据卷为空时按文件名顺序执行 `/docker-entrypoint-initdb.d` 下的 SQL，所以首次启动会自动建库建表。数据卷已经有数据后不会重复执行；想重新初始化，需要 `docker compose down -v` 后再启动（会清空数据库）。

```bash
docker compose up -d --build
docker compose logs -f app
docker compose down          # 停止并删除容器，保留数据卷
docker compose down -v       # 连数据卷一起删除，数据库数据会丢失
```

> `depends_on` 只保证启动顺序，不保证 MySQL 已经能接受连接。应用启动后如果第一次请求失败，等 MySQL 初始化完成再重试即可。
>
> 本机已经用 Memurai 跑 Redis 时，不要再启动 Compose 里的 Redis，否则 6379 端口冲突。Redis 的 Docker 用法和密码配置见 [13-Redis缓存接入与使用](./13-Redis缓存接入与使用.md)。

## 六、常见问题

| 问题 | 原因与处理 |
| --- | --- |
| `docker: command not found` | Docker 没装好或没加入 PATH；重开终端，确认 `docker version` 可用 |
| `Cannot connect to the Docker daemon` | Docker Desktop 没启动；先启动 Docker Desktop，等鲸鱼图标变绿 |
| `port is already allocated` | 8080/3306/6379 被占用；停掉宿主机上的应用，或改用 `-p 8081:8080` |
| 容器连不上 MySQL | 容器里的 `localhost` 不是宿主机；用 `host.docker.internal`，并检查 MySQL 用户权限和防火墙 |
| `no main manifest attribute` | 复制了普通 jar，不是 Spring Boot 重新打包的可执行 jar；用 `target/mall-0.0.1-SNAPSHOT.jar` |
| 容器一停数据就没了 | 数据库数据没挂载 volume；给 MySQL 配数据卷，不要依赖容器可写层 |
| 构建很慢 / 镜像很大 | 每次都在下载依赖；先本地打 jar，只把 jar 复制进镜像；不要用完整 JDK 镜像做运行环境 |
| 改了代码但容器没变化 | 镜像不会自动更新；需要重新 `mvn package`、`docker build`、`docker run` |

## 七、安全与清理

- 不要把密码、JWT 密钥写进 Dockerfile 或镜像；用 `-e`、Compose 环境变量或密钥管理工具注入；
- 本地只映射必要的端口，数据库和 Redis 不要直接暴露到公网；
- 不要长期保留无用镜像和容器，定期查看 `docker ps -a`、`docker images`；
- 清理前确认不会误删数据卷：

```bash
docker container prune     # 删除已停止的容器
docker image prune         # 删除悬空镜像
docker system df           # 查看 Docker 占用空间
```

## 八、小结

Docker 不是本项目运行的必需品，它解决的是「环境一致、部署可重复」的问题：

- 本地学习：`mvn spring-boot:run` 或 `java -jar` + Memurai 最直接；
- 交付/部署：把 jar 做成镜像，用 `docker run` 或 Compose 启动，环境参数通过环境变量传入；
- 数据类中间件（MySQL、Redis）：用 volume 持久化，不要把数据留在容器里。

打包与 jar 运行见 [09-阶段八-进阶与工程化](./09-阶段八-进阶与工程化.md)，Redis 的 Docker/Memurai 安装见 [13-Redis缓存接入与使用](./13-Redis缓存接入与使用.md)。
