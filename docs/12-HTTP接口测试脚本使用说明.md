# 12 · HTTP 接口测试脚本使用说明

> 目标：能独立运行 [`mall/http/mall-api.http`](../mall/http/mall-api.http)，知道每个请求在做什么、变量从哪来、结果怎么看；没有付费版 IDEA 时，也知道有哪些免费替代方案。
> 前置：MySQL 已启动、`mall` 库已建表、应用已在 `8080` 端口启动。这个脚本用于**手工回归**，不替代 `mvn test` 自动化测试。

> **一句话结论**：`.http` 文件本身只是纯文本，任何编辑器都能打开；但要在 IDEA 里点绿色箭头执行，需要 Ultimate 订阅。没有付费 IDEA 时，可以用 JetBrains 官方**免费**的 `ijhttp` 命令行工具原样运行本文件，或者用 VS Code 的 REST Client（需要删掉脚本块、手工填变量）。

## 一、这个文件是什么

[`mall-api.http`](../mall/http/mall-api.http) 采用 **JetBrains HTTP Client** 的 `.http` 格式，是一个纯文本的 HTTP 请求脚本：

- 一个文件放多个请求，用 `###` 分隔；
- 支持变量：`@host = ...` 定义，`{{host}}` 引用；
- 支持响应处理脚本：请求下方的 `> {% ... %}` 里写 JavaScript，可以做断言（`client.test` / `client.assert`）和保存变量（`client.global.set`）；
- 纯文本，可直接提交到 Git，像读代码一样 Review 接口调用，不需要导入导出。

它不是 JUnit 测试，不会在 `mvn test` 时运行，也不在 `src/test` 目录下。它适合：

- 自动化测试跑完后，手工走一遍主链路，肉眼确认响应；
- 临时改参数（数量、库存、状态）调试某个接口；
- 演示接口的真实请求和响应。

两者的分工：

| | `mvn test` 自动化测试 | `.http` 手工脚本 |
| --- | --- | --- |
| 运行方式 | Maven + JUnit，无人值守 | 在 IDE / CLI 里逐条或整体执行 |
| 数据库 | 独立测试库 `mall_test`，每个用例前清库 | 开发库 `mall`，会产生真实数据 |
| 覆盖重点 | 业务规则、事务回滚、边界条件 | 主链路是否顺畅、响应是否直观 |
| 什么时候跑 | 每次改完代码 | 想手工确认、演示、调参时 |

## 二、需要付费的 IntelliJ IDEA 吗？

**在 IDEA 编辑器里直接执行 `.http`，需要 Ultimate 订阅。** HTTP Client 是 JetBrains 官方文档中标注为 Ultimate 的功能，原来的 Community Edition 不包含它。2025.3 起 IDEA 虽然合并成了统一安装包，但 HTTP Client 仍属于 Ultimate 订阅解锁的高级功能；所有用户可免费试用 Ultimate 30 天。

> 判断方法：打开 `.http` 文件，看每个请求左侧有没有绿色箭头、`GET` 有没有语法高亮。没有就是当前版本/授权不包含 HTTP Client。免费版不用在插件市场里找「HTTP Client」——它不是一个可以单独安装到免费版的插件。

如果你已经装了 WebStorm、PyCharm Professional 等 JetBrains 商业版 IDE，它们也内置 HTTP Client，可以直接打开本文件。

下面几种方式按推荐顺序排列：

| 方式 | 费用 | 能否直接运行本文件 | 适合谁 |
| --- | --- | --- | --- |
| IDEA Ultimate（订阅 / 试用 / 教育许可） | 付费，或免费试用 / 教育许可 | ✅ 原样运行，体验最好 | 已用 IDEA 开发、想要图形界面 |
| 官方 `ijhttp` CLI | 免费 | ✅ 原样运行（注意执行顺序） | 不想买 IDEA、能接受命令行 |
| VS Code + REST Client | 免费 | ⚠️ 需要改造（不支持响应脚本） | 喜欢 VS Code、想要图形界面 |
| Postman / curl | 免费 | ⚠️ 需要手工搬请求 | 只验证单个接口 |

### 方式 A：IDEA Ultimate（最顺手）

1. 打开 `mall/http/mall-api.http`。
2. 每个请求左侧有绿色箭头；把光标放在请求里按 `Ctrl+Enter`（macOS `Cmd+Enter`）也可以发送。
3. 响应在底部 **Services** 工具窗口：`Response` 看响应体，`Tests` 看 `client.test` 的通过/失败。
4. 编辑器顶部还有「运行文件中全部请求」的入口，但**本文件不建议整体运行**：第 10 步会取消订单，导致后面的支付步骤失败，原因见第四节。

### 方式 B：官方免费 CLI `ijhttp`（不改文件）

JetBrains 提供独立的 HTTP Client CLI，**不需要 Ultimate 许可证**，能执行同样的 `.http` 文件和 `> {% %}` 响应脚本。要求本机有 JDK 21+（本项目正好要求 JDK 21）。

安装，任选一种：

```bash
# 1. 下载 ZIP 后解压（官方短链）
curl -fL -o ijhttp.zip "https://jb.gg/ijhttp/latest"

# 2. macOS
brew install ijhttp

# 3. Docker（注意容器里的 localhost 不是宿主机，见下面的说明）
docker pull jetbrains/intellij-http-client
```

解压后会得到一个 `ijhttp/` 目录，里面有 `ijhttp`（macOS/Linux）和 `ijhttp.bat`（Windows）。运行，在 `mall/http` 目录下：

```bash
# Windows：写你实际解压的路径
D:\tools\ijhttp\ijhttp.bat mall-api.http

# macOS / Linux
/path/to/ijhttp/ijhttp mall-api.http
```

终端会打印每个请求的状态和测试结果。加 `--report` 会在 `reports/report.xml` 生成 JUnit 格式报告；加 `-L HEADERS` 可以打印请求/响应头。

**重要限制**：CLI 会按文件顺序执行**所有**请求。本文件第 10 步会取消订单，紧跟其后的第 12 步「发起支付」就会失败。所以：

- 想完整跑主链路：复制一份文件，把第 10 步「取消订单」的每一行前面加 `#`（包括 `PUT ...` 请求行和后面的脚本块），或直接删掉这一节；
- 想验证取消：单独用 IDEA、VS Code 或 Postman 执行第 10 步，或另建一个只含取消请求的小文件；
- 运行前确认 `@productId` 指向一个真实存在的商品（见第三节）。

> **Docker 的坑**：容器里的 `localhost` 指容器自己，连不到宿主机上的应用。用 Docker 时要把 `@host` 改成 `http://host.docker.internal:8080`（Windows/macOS 的 Docker Desktop），或直接使用 ZIP 版在宿主机运行。对本地学习项目，**推荐 ZIP 版**，最省事。

### 方式 C：VS Code + REST Client（免费图形界面）

在 VS Code 扩展市场搜索 **REST Client**，作者 **Huachao Mao**（扩展 ID `humao.rest-client`）。

它支持 `.http` 文件、`###` 分隔、`@变量` 和 `{{变量}}`，点请求上方的 `Send Request`（或 `Ctrl+Alt+R`）即可发送。

但它**不支持** IntelliJ 的 `> {% ... %}` 响应处理脚本。直接用它打开本文件有两个问题：

1. 脚本不会执行，`client.global.set` 不会帮你保存 token / id；
2. 对 POST 请求，脚本块可能被当成请求体的一部分，导致 JSON 解析失败。

要用 VS Code，需要把脚本块删掉，改成手工填变量：

1. 执行登录请求，从响应里复制 `data.token` 和 `data.id`，填到文件顶部的 `@token`、`@merchantId`；
2. 执行新增分类，复制 `data.id` 填到 `@categoryId`；
3. 执行商品分页，找一个商品的 `id` 填到 `@productId`；
4. 执行下单，复制 `data.id` 填到 `@orderId`；
5. 后续请求照常发送。

只做单接口调试时，更简单的做法是：复制某一个请求块到临时 `.http` 文件，删掉脚本块，手工填 `{{token}}`。

> 也可以用 REST Client 的「请求变量」自动取 token（给登录请求加 `# @name login`，再用 `{{login.response.body.$.data.token}}`），但那需要改写请求，和本文件的 IntelliJ 语法不通用。初学阶段手工复制更直观。

### 方式 D：Postman / curl

只验证单个接口时，直接从 `.http` 里抄 URL、Header 和 Body 即可。注意先登录拿 token，再加 `Authorization: Bearer <token>`。这种方式不适合跑完整回归，因为变量要手工传。

> **学生/教师提示**：JetBrains 提供免费教育许可证，可以合法使用 Ultimate 的全部功能（含 HTTP Client）。如果符合条件，这是最舒服的方案。申请入口：<https://www.jetbrains.com/community/education/>

## 三、运行前准备

按顺序检查：

1. **数据库**：MySQL 已启动，已执行 `mall/sql/01-schema.sql` 和 `mall/sql/03-阶段三-商城建表.sql`。
2. **测试数据（可选但推荐）**：执行 `mall/sql/04-测试数据.sql`，会造出：
   - 店家 `test_shop_a` / `test_shop_b`，密码都是 `123456`；
   - 12 个商品，带库存，可直接用来下单。
3. **数据库密码**：设置环境变量 `DB_PASSWORD`（见根 README 的「快速开始」）。
4. **启动应用**：运行 `MallApplication`，确认 `http://localhost:8080` 已监听。
5. **确认 token 机制**：`/api/**` 下除注册、登录、支付回调外都要 JWT；脚本第 2 步登录的响应脚本会把 token 写入全局变量，后续请求的 `Authorization: Bearer {{token}}` 自动生效。

### 两种起步方式

**方式一：从零开始（脚本默认写法）**

- 保持第 1 步注册 `demo_shop`（重复执行会提示「账号已存在」，正常）；
- 第 2 步登录 `demo_shop`，自动拿到 `merchantId` 和 `token`；
- 第 3 步建分类、第 4 步建商品；
- 第 5 步分页找到刚建的「演示商品」，把它的 `id` 填到文件顶部 `@productId = ...`；
- 继续第 6 步及以后。

**方式二：直接用测试数据**

- 跳过第 1 步；
- 把第 2 步的登录账号改成 `test_shop_a` / `123456`（或者保留 `demo_shop`，但那样需要先注册）；
- 从 `04-测试数据.sql` 的查询结果里挑一个属于该店家的商品 id，填到 `@productId`；
- 第 3、4 步可以跳过（也可以执行，会再建一个新分类/商品）；
- 从第 5 步继续。

> 分类是全局的，不区分店家；商品和订单才归属某个店家。脚本里的 `merchantId` 会在登录后由响应脚本覆盖为当前登录店家的 id。

## 四、请求顺序与变量传递

先看脚本顶部的变量：

```http
@host = http://localhost:8080
@token =
@merchantId = 1
@categoryId = 1
@productId = 1
@orderId = 1
```

它们只是**默认值/占位值**。真正运行时会这样变化：

| 步骤 | 请求 | 依赖 | 运行后发生什么 |
| --- | --- | --- | --- |
| 1 | 注册 | 无 | 创建 `demo_shop`；重复执行会提示账号已存在，属正常 |
| 2 | 登录 | 账号存在 | 响应脚本写入全局 `merchantId`、`token` |
| 3 | 新增分类 | token | 响应脚本写入全局 `categoryId` |
| 4 | 新增商品 | merchantId、categoryId | 创建商品，但接口返回 `Result<Void>`，**不返回商品 id** |
| 5 | 商品分页 | token、merchantId | 找到「演示商品」的 id，**手工**填到顶部 `@productId` |
| 6 | 商品入库 | productId、token | 库存 +50 |
| 7 | 下单 | merchantId、productId、token | 响应脚本写入全局 `orderId` |
| 8 | 订单详情 | orderId、token | 校验明细存在 |
| 9 | 订单分页 | merchantId、token | 查看订单列表 |
| 10 | 取消订单 | orderId、token | **订单变为已取消（status=2），库存回补** |
| 11 | 查支付状态 | orderId、token | 脚本只断言 `payStatus=0`；若刚取消，订单 status 是 2 |
| 12 | 发起支付 | orderId、token | **只能对「待支付」订单发起**；执行过第 10 步的话，先回到第 7 步重新下一单 |
| 13 | 支付回调 success=true | orderId | 订单变已支付（status=1，payStatus=2） |
| 13b | 再查支付状态 | orderId | 断言 status=1、payStatus=2 |
| 13c | 支付回调 success=false | orderId | 对已支付订单会被**幂等忽略**，不会改回未支付；想验证「失败回到待支付」，要单独用一笔新订单先发起支付再回调 false |
| 14 | 参数校验 | 无 | 缺 username，返回 code=400 + 「账号不能为空」 |
| 15 | 路径不存在 | token | 返回 HTTP 404 + code=404；不带 token 会先被拦成 401 |

**变量优先级**（为什么登录能覆盖顶部的默认值）：在 HTTP Client 中，同名变量的优先级为 **环境变量 > 全局变量（`client.global.set`）> 文件内 `@` 变量 > 请求级变量**。所以第 2 步脚本设置的 `merchantId` / `token` 会压过文件顶部的 `@merchantId = 1` 和空的 `@token =`。

**最省事的执行顺序**：

- 主链路：1 → 2 → 3 → 4 → 5（填 productId）→ 6 → 7 → 8 → 9 → 11 → 12 → 13 → 13b → 14 → 15；
- 取消订单单独验证：主链路跑完后，回到第 7 步再下一单，执行第 10 步；或用另一笔订单单独跑；
- 如果只是想验证取消，不要在支付前执行第 10 步。

## 五、怎么读结果

- **HTTP 状态码**：大多数业务失败仍是 HTTP 200，真正的结果看响应体 `code`；只有鉴权失败是 HTTP 401、路径不存在是 HTTP 404。
- **统一返回结构**：`{ "code": 0, "message": "success", "data": ... }`；`code != 0` 表示业务失败。
- **Tests 面板**：`client.test(...)` 会显示通过/失败；失败时看 `client.assert` 的 message。它不是 JUnit 报告，只是脚本内的轻量断言。
- **变量**：可以在响应脚本里用 `client.log(...)` 打印，或在后续请求里引用 `{{token}}` 等。

## 六、常见问题

| 现象 | 原因 / 解决 |
| --- | --- |
| 请求左侧没有绿色箭头，`GET` 没有高亮 | 你在用免费版 IDEA，或 HTTP Client 被禁用；换 Ultimate / `ijhttp` / VS Code |
| `401 未登录，请先登录` | 没执行第 2 步登录，或 `@token` 为空；先登录，让脚本写入 token |
| `401 登录已过期 / 凭证无效` | token 过期或被篡改；重新登录 |
| `Connection refused` | 应用没启动、端口不是 8080、代理配置不对 |
| 第 1 步提示「账号已存在」 | 正常，重复执行注册的预期结果；直接去第 2 步登录 |
| 第 4 步成功但不知道 productId | 接口不返回 id；用第 5 步分页结果里的 id |
| 第 6 / 7 步失败「商品不存在」 | `@productId` 还是默认值 1，或该商品已被删除；改成真实 id |
| 第 12 步「只有待支付订单才能发起支付」 | 你刚执行过第 10 步取消订单；重新执行第 7 步下一单，再执行第 12 步 |
| 第 13c 步后订单还是已支付 | 这是幂等设计，已支付订单会忽略失败回调（同自动化用例 Y-7b）；不是 bug |
| 第 14 步返回 code=500 | 检查是否用了旧代码；当前版本应返回 400 + 字段提示 |
| `ijhttp` 跑到第 12 步失败 | CLI 按顺序执行全部请求，第 10 步已取消订单；复制文件注释掉第 10 步，或单独跑取消 |
| VS Code 里 POST 请求报 JSON 解析错误 | 响应脚本块被当成 body；删掉 `> {% ... %}` 块并手工填变量 |
| 中文注释乱码 | 把文件编码设为 UTF-8；IDEA 右下角可改 |
| 想改 host / 端口 | 改顶部 `@host`；或用 `ijhttp -V host=...` 覆盖 |

## 七、语法速查

```http
@host = http://localhost:8080

### 登录
# 也可以写注释
POST {{host}}/api/merchants/login
Content-Type: application/json

{
  "username": "demo_shop",
  "password": "123456"
}

> {%
    client.test("登录成功", function () {
        client.assert(response.body.code === 0, "登录失败");
    });
    client.global.set("token", response.body.data.token);
%}

### 受保护接口
GET {{host}}/api/products/page?pageNum=1&pageSize=10
Authorization: Bearer {{token}}
```

- `###`：请求分隔符；同一文件可以放任意多个请求；
- `#` 或 `//`：注释；
- `@name = value`：文件内变量（只在当前文件生效）；
- `{{name}}`：引用变量；
- 请求行后先写 Header，**空一行**再写 Body；
- `> {% ... %}`：响应处理脚本，必须放在请求（含 Body）之后；
- `client.test(name, fn)` / `client.assert(condition, message)`：断言；
- `client.global.set(name, value)`：把值存成全局变量，后续请求可用；
- 环境变量和 `http-client.env.json`：多环境时用，本文件不需要。

## 八、最后

- 自动化测试是主力：`mvn test` 覆盖业务规则和事务；`.http` 脚本是手工补充，不要用它替代自动化测试。
- 这个文件会对 `mall` 开发库产生真实数据；反复执行会积累 demo 店家、商品和订单，介意的话定期清理，或使用测试数据脚本。
- 改接口后，记得同步更新 `.http` 脚本和 [11-测试用例清单](./11-测试用例清单.md)。

## 九、参考资料

- [JetBrains HTTP Client 文档](https://www.jetbrains.com/help/idea/http-client-in-product-code-editor.html)
- [JetBrains HTTP Client CLI 文档](https://www.jetbrains.com/help/idea/http-client-cli.html)
- [HTTP Client 变量与优先级](https://www.jetbrains.com/help/idea/http-client-variables.html)
- [VS Code REST Client 扩展](https://marketplace.visualstudio.com/items?itemName=humao.rest-client)
- [JetBrains 教育许可证申请](https://www.jetbrains.com/community/education/)
