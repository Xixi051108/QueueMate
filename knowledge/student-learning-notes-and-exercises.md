# QueueMate 个人学习笔记与习题

> 用途：集中记录每一课的知识笔记、术语补充、课堂习题、个人答案、批改结果和薄弱点。  
> 维护方式：每完成一课，在本文件末尾继续追加，不为每课拆分新的个人笔记文件。  
> 教学偏好：先完整讲授本课知识，练习统一放在课末；讲解中提前融入完成练习需要的知识。

## 学习进度

| 课次 | 主题 | 状态 | 成绩 | 日期 |
| --- | --- | --- | --- | --- |
| 0.1 | 用最直白的话讲清 QueueMate 是什么 | 暂缓 | 未验收 | - |
| 0.2 | 仓库结构与一次预约请求的完整技术链路 | 已完成 | 9.0 / 10 | 2026-08-22 |
| 1.1 | 进程、端口和 localhost | 已完成 | 8.2 / 10 | 2026-09-01 |
| 1.2 | HTTP 请求、响应、JSON 和状态码 | 已完成 | 8.5 / 10 | 2026-09-07 |
| 1.3 | 从“登录”按钮追一次完整请求 | 学习中（待补强验收） | 3.2 / 10 | 2026-09-15 |

---

## 前置概念：QueueMate 的业务范围

### QueueMate 是什么

QueueMate 是一个生活服务场所的预约与排队系统，可模拟奶茶店、自习室、羽毛球场等场景。

它有两条相互独立的业务链：

- 预约：提前占用未来某个日期和时段的名额。
- 排队：用户到店或准备到店时取号，等待商家叫号。

预约后不一定需要取号；没有预约也可以直接参加现场排队。

### 三类角色

| 角色 | 主要职责 |
| --- | --- |
| `USER` | 浏览地点、创建和取消预约、查看钱包和消费码、现场取号 |
| `MERCHANT` | 管理地点和时段、叫号、完成服务、标记过号、核销消费码 |
| `ADMIN` | 审核商家入驻、执行平台级钱包和预约管理 |

简化记忆：

```text
USER 发起服务
MERCHANT 提供和处理服务
ADMIN 维护平台秩序
```

---

## 第 0.2 课：仓库结构与一次预约请求的完整技术链路

### 一、本课目标

1. 认识 QueueMate 的主要目录。
2. 理解浏览器、前端、后端和数据库的关系。
3. 从用户点击预约一直追踪到 MySQL。
4. 区分 View、Controller、Service、Mapper 和数据库的职责。
5. 初步理解 JWT、事务、原子 SQL 和常见 HTTP 状态码。

### 二、仓库结构

| 目录 | 职责 |
| --- | --- |
| `frontend` | Vue 页面、路由、用户交互、接口调用 |
| `backend` | Spring Boot 接口、权限和业务规则 |
| `sql` | MySQL 表结构和初始化数据 |
| `tests` | Postman、Playwright、JMeter 测试 |
| `.github/workflows` | GitHub Actions 自动构建和测试 |

本地运行关系：

```text
浏览器 → Vue/Vite 5173 → Spring Boot 8080 → MySQL 3306
```

- `5173`：Vite 前端开发服务器。
- `8080`：Spring Boot 后端服务器。
- `3306`：MySQL 数据库服务。
- Vite 会把以 `/api` 开头的请求转发到 `http://localhost:8080`。

相关文件：

- `frontend/queuemate-web/vite.config.js`
- `backend/queuemate-server/src/main/resources/application.yml`

### 三、什么是 Vue 页面

Vue 页面是使用 Vue 框架编写、最终由浏览器展示给用户的前端页面。

一个 `.vue` 文件通常包含：

```vue
<script setup>
// 页面逻辑：加载数据、调用接口、处理按钮点击
</script>

<template>
  <!-- 页面结构：文字、按钮、表格等 -->
</template>

<style>
/* 页面样式：颜色、大小、间距等 */
</style>
```

- `template`：页面长什么样。
- `script`：页面会做什么。
- `style`：页面如何显示。

QueueMate 创建预约的页面入口是：

```text
frontend/queuemate-web/src/views/VenueDetailView.vue
```

用户选择预约时段后，页面执行：

```js
await bookingApi.create(slot.id)
```

其中 `await` 表示等待后端返回结果后再继续执行。

### 四、前端如何生成 HTTP 请求

`frontend/queuemate-web/src/services/api.js` 中定义了：

```js
export const bookingApi = {
  create: (slotId) => http.post('/bookings', { slotId }),
}
```

最终请求大致为：

```http
POST /api/v1/bookings
Authorization: Bearer eyJhbGci...
Content-Type: application/json

{
  "slotId": "123456"
}
```

各部分含义：

- `POST`：创建新资源。
- `/api/v1/bookings`：后端预约接口地址。
- `Content-Type`：请求体使用 JSON。
- `Authorization`：携带身份凭证。
- Body：告诉后端要预约哪个时段。

两个容易混淆的地址：

- `/bookings`：前端页面路由，用于显示“我的预约”页面。
- `/api/v1/bookings`：后端 API，用于创建预约数据。

### 五、Authorization、Bearer 和 JWT

`Authorization` 是 HTTP 请求头字段，用于携带身份凭证。

```text
Authorization : Bearer  JWT内容
请求头名称        凭证类型  真正的凭证
```

`Bearer` 的原意是“持有者”，表示当前请求者持有后面的 token。`Bearer` 本身不是 token，真正的凭证是后面的 JWT。

前端登录成功后保存 JWT。之后发送请求时，Axios 请求拦截器读取已经保存的 JWT，并自动添加请求头；它不是每次请求时重新登录或重新获取 JWT。

后端 `JwtAuthenticationFilter` 会：

1. 读取 `Authorization`。
2. 确认内容以 `Bearer ` 开头。
3. 取出并验证 JWT。
4. 查询当前用户和角色。
5. 把用户身份放进 Spring Security 上下文。

没有有效身份通常返回 `401`；已经登录但角色不允许通常返回 `403`。

### 六、Controller 如何接收请求

`BookingController` 的类级地址是：

```java
@RequestMapping("/api/v1/bookings")
```

创建方法使用：

```java
@PostMapping
@ResponseStatus(HttpStatus.CREATED)
@PreAuthorize("hasRole('USER')")
```

组合结果是：

```http
POST /api/v1/bookings
```

`@RequestMapping` 是 Java 注解。它不是普通业务类；使用它是在声明请求地址与 Controller 的映射规则。Spring 启动时读取这些注解并注册接口。

重要注解：

| 注解 | 作用 |
| --- | --- |
| `@RequestMapping` | 声明类或方法的基础请求地址 |
| `@PostMapping` | 接收 POST 请求 |
| `@RequestBody` | 把 JSON 请求体转换成 Java 对象 |
| `@Valid` | 执行参数校验 |
| `@AuthenticationPrincipal` | 获取当前登录用户 |
| `@PreAuthorize` | 检查角色权限 |

`BookingCreateRequest` 要求 `slotId` 不能为空并且必须为正数。`slotId` 为 `-3` 时，会在进入 Service 之前返回 `400 PARAM_INVALID`。

### 七、slotId 是什么

`slot` 是预约时段，`slotId` 是某条预约时段数据的唯一编号。

示例：

| id | 地点 | 日期 | 时间 | 容量 |
| --- | --- | --- | --- | --- |
| `123456` | 某羽毛球馆 | 8 月 23 日 | 14:00–15:00 | 10 |

三个 ID 的区别：

- `venueId`：地点编号。
- `slotId`：地点下某个具体预约时段的编号。
- `bookingId`：预约成功后生成的预约记录编号。

关系：

```text
地点 venue
  └─ 包含多个时段 slot
       └─ 用户选择时段后产生预约 booking
```

### 八、Controller、Service、Mapper 的职责

| 层 | 主要职责 | 不应该承担的职责 |
| --- | --- | --- |
| View | 展示页面、收集操作、显示结果 | 决定最终业务规则 |
| Controller | 接收 HTTP 请求、校验参数和权限、调用 Service | 堆放复杂预约逻辑 |
| Service | 判断业务能否执行、组织事务和调用顺序 | 处理页面样式 |
| Mapper | 执行数据库查询和更新 | 决定完整业务流程 |
| MySQL | 保存数据、执行 SQL、提供约束 | 处理浏览器交互 |

需要特别注意：

- Controller 不是负责判断全部预约条件。
- Mapper 不是“更新请求”，而是执行 SQL。
- MySQL 更新的是表中的数据，SQL 是发送给数据库的命令。

### 九、BookingService 的预约顺序

`BookingService.create()` 按以下顺序执行：

```text
确认当前用户
  ↓
查询预约时段
  ↓
查询所属地点
  ↓
检查地点和时段能否预约
  ↓
检查用户是否重复预约
  ↓
原子占用一个名额
  ↓
收费时扣除钱包余额
  ↓
保存预约记录
  ↓
收费预约生成消费码
  ↓
返回预约结果
```

Service 会检查地点状态、预约开关、时段状态、是否过期、是否重复、容量和钱包余额等业务规则。

### 十、事务与回滚

创建预约的方法使用 `@Transactional`。事务表示一组数据库操作要么全部成功，要么全部撤销。

收费预约可能包含：

```text
占用名额 → 扣款 → 保存预约 → 生成消费码
```

如果保存预约时失败，事务会回滚：

- `reserved_count` 恢复，不再占用名额。
- 钱包余额恢复。
- 不会留下预约记录或不完整的消费码。

这里的准确术语是“回滚”，不是业务代码稍后再发起一次退款。

### 十一、原子 SQL 防止超卖

`BookingSlotMapper.reserveCapacity()` 使用条件更新：

```sql
update booking_slots
set reserved_count = reserved_count + 1
where id = ?
  and status = 'OPEN'
  and reserved_count < capacity
```

如果先查询剩余名额，再单独执行加一，多个并发请求可能同时看到“剩余 1”，随后全部加一，造成超卖。

当前 SQL 把“检查仍有容量”和“预约数加一”合并为一个原子操作。

返回值：

- `1`：成功更新一行，占位成功。
- `0`：没有数据同时满足全部更新条件。

返回 `0` 不一定表示时段不存在，也可能表示：

- 时段关闭；
- 时段已满；
- 地点停用；
- 地点没有启用预约。

所以 Service 会重新查询相关数据，判断并返回更准确的业务错误。

### 十二、数据库的最终保护

`booking_slots` 表使用检查约束保证：

```text
reserved_count >= 0
reserved_count <= capacity
```

`bookings` 表使用唯一约束防止同一用户同时拥有两条相同时段的有效预约。

项目采用两层保护：

```text
Service 预检查：尽早返回友好的业务错误
数据库约束：在并发情况下提供最终保护
```

### 十三、响应与状态码

成功响应使用统一结构：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "id": "预约ID",
    "bookingNo": "BK...",
    "status": "BOOKED"
  }
}
```

常见 HTTP 状态码：

| 状态码 | 含义 | QueueMate 示例 |
| --- | --- | --- |
| `201` | 创建成功 | 预约创建成功 |
| `400` | 参数错误 | `slotId` 为空、0 或负数 |
| `401` | 没有有效身份 | JWT 缺失或无效 |
| `403` | 已登录但权限不足 | 当前角色不允许预约 |
| `404` | 资源不存在 | 预约时段不存在 |
| `409` | 业务状态冲突 | 已约满、重复预约、时段关闭 |
| `500` | 未处理的服务器错误 | 程序发生未知异常 |

### 十四、完整调用链

```text
用户在地点详情页选择时段
→ VenueDetailView 调用 bookingApi
→ Axios 给请求添加已保存的 JWT
→ Vite 把 /api 请求转发到后端 8080
→ JwtAuthenticationFilter 验证 JWT
→ Spring Security 检查角色
→ BookingController 接收并校验请求
→ BookingService 判断预约规则并开启事务
→ BookingSlotMapper 原子占用名额
→ BookingMapper 保存预约
→ MySQL 持久化数据
→ BookingResponse 和 ApiResponse 返回结果
→ Axios 处理响应
→ Vue 页面显示成功或错误
```

### 十五、排障顺序

| 现象 | 优先检查 |
| --- | --- |
| 按钮没有反应 | Vue 页面事件 |
| 没有网络请求 | 页面逻辑和 `api.js` |
| 请求地址错误 | `http.js`、Vite 代理、后端端口 |
| 返回 401 | JWT 是否存在、过期或格式错误 |
| 返回 403 | 当前用户角色 |
| 返回 409 | Service 业务规则和当前数据状态 |
| SQL 报错 | Mapper、SQL 和数据库表结构 |
| 后端成功但页面报错 | API 响应结构和 Axios 响应拦截器 |

---

## 第 0.2 课习题与批改

### 第 1 题：调用链排序（2 分）

题目：将 `BookingService`、`MySQL`、`VenueDetailView`、`BookingController`、`bookingApi`、`JwtAuthenticationFilter`、`BookingSlotMapper` 按顺序排列。

个人答案：

```text
VenueDetailView
→ bookingApi
→ JwtAuthenticationFilter
→ BookingController
→ BookingService
→ BookingSlotMapper
→ MySQL
```

批改：正确，2 / 2。

### 第 2 题：页面路由与 API（1 分）

个人答案：`/bookings` 跳转到“我的预约”界面；`/api/v1/bookings` 是前端请求后端创建预约。

批改：正确，1 / 1。

### 第 3 题：状态码判断（2 分）

个人答案：

```text
无 JWT → 401
slotId=-3 → 400
时段已满 → 409
创建成功 → 201
```

批改：全部正确，2 / 2。

### 第 4 题：reserveCapacity 返回 0（1.5 分）

个人答案：可能约满或者时段关闭，不一定是时段不存在。

批改：核心理解正确，但原因不完整。还可能是地点停用或地点未启用预约。得分 1 / 1.5。

### 第 5 题：并发超卖（1.5 分）

个人答案：多个用户并发时可能都读取到剩余人数为 1，然后都加一；条件判断和更新放在同一条 SQL 中，会作为原子操作执行。

批改：正确，1.5 / 1.5。

### 第 6 题：事务回滚（1 分）

个人答案：名额取消占用并且退款。

批改：结果正确。更准确的表达是名额占用和钱包扣款随事务一起回滚，而不是额外执行一笔退款。得分 1 / 1。

### 第 7 题：完整调用链（1 分）

个人答案中已经包含页面、JWT、Controller、Service、Mapper 和数据库，但存在以下混淆：

1. 创建预约发生在地点详情页，不是“我的预约”页面。
2. JWT 是登录后保存并由 Axios 注入，不是每次请求重新获取。
3. Controller 接收请求、校验参数和权限；Service 判断预约业务规则。
4. Mapper 执行 SQL，MySQL 更新数据。

批改：0.5 / 1。

### 本课结果

```text
总分：9.0 / 10
结果：通过
已掌握：前后端请求链、常见状态码、原子占位、事务回滚
还不稳：Controller/Service/Mapper 职责边界，JWT 并非每次重新获取
下一课：1.1 进程、端口和 localhost
```

---

## 第 1.1 课：进程、端口和 localhost

### 本课目标

1. 区分程序、进程、PID 和端口。
2. 理解 TCP 监听、客户端临时端口、localhost、IPv4 和 `0.0.0.0`。
3. 准确说明 QueueMate 中 5173、8080、3306 的监听者和连接方向。
4. 使用 PowerShell 检查端口、进程、TCP 连通性和健康接口。

### 知识笔记

- 程序是磁盘上的代码和可执行文件；进程是程序正在运行的实例。PID 是操作系统当前分配给进程的编号，进程退出后可被复用。
- 端口是网络服务的逻辑编号。配置文件写了端口不代表端口已开启，只有进程成功启动并监听后才可连接。
- TCP 是传输控制协议，在本地链路中提供可靠、有序的连接。服务端固定监听端口，客户端连接时使用操作系统分配的临时源端口，因此多个客户端能同时连接同一服务端端口。
- localhost 表示“发起连接的程序自己所在的机器”，通常解析到 `127.0.0.1` 或 `::1`。手机里的 localhost 是手机，不是开发电脑。
- IPv4 地址用于定位主机或网络接口，端口再定位该机器上的网络服务。
- 服务端绑定 `0.0.0.0` 表示接受发往本机所有 IPv4 接口的连接；它是监听范围，通常不是客户端填写的目标地址。
- Vue 用于编写页面和交互；Vite 是由 Node.js 运行的前端开发/构建工具，开发时监听 5173、提供前端资源并代理 `/api` 请求。

### QueueMate 真实代码链路

```text
浏览器临时端口
→ Node/Vite 监听 0.0.0.0:5173
→ Vite 连接 localhost:8080
→ Java/Spring Boot/Tomcat 监听 8080
→ Java 通过 JDBC 驱动使用 MySQL 协议连接 localhost:3306/queuemate
→ mysqld 监听 3306
```

真实配置：

- `package.json`：`vite --host 0.0.0.0`。
- `vite.config.js`：前端优先使用 5173，`/api` 代理目标是 `http://localhost:8080`。
- `http.js`：Axios 默认 `baseURL` 是 `/api/v1`。
- `application.yml`：Spring Boot 使用 8080，默认数据库地址为 `jdbc:mysql://localhost:3306/queuemate`。
- `DB_URL` 存在时覆盖 `application.yml` 冒号后的默认数据库地址。

### 常见错误与排障

- `ERR_CONNECTION_REFUSED`：目标端口通常没有进程接收连接，不等同于 HTTP 401/403/404/500。
- 页面能打开但 API 失败：5173 可能正常，应继续检查 Vite 代理、8080 和后端日志。
- 8080 被占用：Spring Boot 通常启动失败，先根据 `OwningProcess` 查询 PID 和进程名，不直接结束未知进程。
- 5173 被占用：当前没有 `strictPort: true`，Vite 可能自动改用 5174，应以终端实际 URL 为准。
- MySQL `Connection refused` 优先检查 mysqld 和 3306；`Access denied` 通常表示已到达 MySQL，但账号、密码或权限不通过。
- `Test-NetConnection` 只证明 TCP 连通；健康接口成功也不等于全部业务健康。

### 本课习题、个人答案与批改

#### 概念题 1：进程与端口（1.5 分）

个人答案：正确区分程序与进程、PID 与端口；将 5173、8080、3306 表述为前端、后端和 SQL；指出浏览器是连接 Vite 5173 的客户端。

批改：核心区分正确。准确说法是 5173 由 Node/Vite 监听，8080 由 Java/Spring Boot/Tomcat 监听，3306 由 mysqld/MySQL 监听；SQL 是数据库命令，不是监听进程。得分：1.3 / 1.5。

#### 概念题 2：localhost（1.5 分）

个人答案：正确说明浏览器、Java 和手机中 localhost 的不同视角；指出手机应使用电脑局域网 IP，并处于同一局域网。

批改：视角正确。还应说明 Vite 需监听 `0.0.0.0`、防火墙允许端口、网络没有客户端隔离。答案的 Markdown 显示文字为电脑局域网 IP，但链接实际指向 localhost，技术文档中应保持一致。得分：1.3 / 1.5。

#### 概念题 3：地址拆解（1 分）

个人答案：正确拆解 HTTP 地址、localhost、5173 和 `/venues`；将 `jdbc:mysql` 表述为 JDBC 协议。

批改：地址结构基本正确。JDBC 是 Java 访问数据库的 API/驱动规范，`jdbc:mysql:` 是 JDBC 连接 URL；底层数据库通信是 MySQL 协议经 TCP 传输。得分：0.7 / 1。

#### 新代码题 4：手机经 Vite 访问 API（2 分）

个人答案：给出手机请求 `http://192.168.1.20:5173/api/v1/venues`、Vite 接收、localhost 取决于连接发起者，并列出浏览器→Vite、Vite→Spring Boot、Spring Boot→MySQL；未单独写出 Axios 路径，并把代理目标写为局域网 IP 的 8080。

批改：Axios 路径应明确为 `/api/v1/venues`。手机首段请求正确，但真实配置的代理目标是 `http://localhost:8080`，追代码必须按配置回答，不能用可能也能连通的地址替换。得分：1.5 / 2。

#### 代码题 5：配置与故障预测（2 分）

个人答案：认为 5173 被占用时 Vite 会失败；正确识别 8080 占用和 MySQL 未启动；能找出环境变量中的新数据库主机和端口，但对默认值是否生效表述相反，并把 `192.168.1.50` 误写为 `193.168.1.50`。

批改：5173 占用时 Vite 可能自动改用 5174；8080 占用时 Spring Boot 通常失败；MySQL 未启动时静态前端可能打开，但数据库业务失败；设置 `DB_URL` 后默认 `localhost:3306` 不生效，后端连接 `192.168.1.50:3307/queuemate`。得分：1.4 / 2。

#### 实践题（2 分）

实践过程：首次检查只有 3306 监听，能定位为 Vite 和 Spring Boot 未启动；随后确认后端 8080 和 HikariPool 数据库连接，配置 Node.js 22.23.2 与 pnpm 11.19.0 并启动 Vite。

最终验证：

```text
5173 -> PID 31304 -> node.exe -> D:\DevTools\NodeJS\node.exe
8080 -> PID 32140 -> java.exe -> D:\JAVA\jdk-21\bin\java.exe
3306 -> PID 6868  -> mysqld.exe
5173/8080/3306 TCP -> 全部 True
GET /api/v1/health -> code=0, status=UP, service=queuemate-server
```

批改：能够把“没有监听”与“服务没有启动”关联，并在环境问题解决后完成三端口闭环验证。得分：2 / 2。

### 批改与订正

```text
概念题 1：1.3 / 1.5
概念题 2：1.3 / 1.5
概念题 3：0.7 / 1.0
新代码题 4：1.5 / 2.0
代码题 5：1.4 / 2.0
实践题：2.0 / 2.0
总分：8.2 / 10
结果：通过
```

需要补强的准确表达：

1. 3306 由 MySQL/mysqld 监听，不是“SQL 监听”。
2. Vite 是 Node.js 运行的前端开发工具/开发服务器，不是页面按钮本身。
3. `jdbc:mysql:` 是 JDBC 连接 URL，底层数据库通信使用 MySQL 协议经 TCP 传输。
4. Vite 代理目标是 `localhost:8080`；`DB_URL` 存在时默认 `localhost:3306` 被覆盖。

### 本课结果

```text
本课：1.1 进程、端口和 localhost
已掌握：程序/进程/PID/端口的基本区分；localhost 的视角；5173、8080、3306 的主要链路；端口和健康检查
还不稳：TCP/HTTP/JDBC/MySQL 的协议层次，服务名称的精确性，Vite 端口回退和环境变量覆盖的准确表达
实践结果：Node/Vite 5173、Java/Spring Boot 8080、MySQL 3306 全部监听且 TCP 成功，健康接口 UP
技术表达评分：8 / 10
下一课：1.2 HTTP 请求、响应、JSON 和状态码
课后任务：用自己的话复述“浏览器→Vite 5173→Spring Boot 8080→MySQL 3306”，并明确谁监听、谁主动连接
```

---

## 第 1.2 课：HTTP 请求、响应、JSON 和状态码

### 本课目标

1. 说明 HTTP 和 JSON 分别解决什么问题。
2. 拆解请求方法、地址、请求头、路径参数、查询参数和请求体。
3. 区分 GET、POST、PATCH，并理解幂等性。
4. 结合 QueueMate 解释 200、201、400、401、403、405、409、500。
5. 区分 HTTP 状态码与 `ApiResponse.code`，说明 Axios 如何处理成功和失败响应。
6. 判断请求在哪一层结束：认证、授权、参数校验、业务逻辑或路由层。

### 知识笔记

#### HTTP 与 JSON

HTTP（HyperText Transfer Protocol，超文本传输协议）规定客户端和服务器如何发送请求、返回响应。一次 HTTP 请求通常包含：

- 请求方法，例如 GET、POST、PATCH；
- 请求地址，例如 `/api/v1/bookings`；
- 请求头，例如 `Content-Type`、`Accept`、`Authorization`；
- 可选请求体，通常承载创建或修改资源需要的数据。

JSON（JavaScript Object Notation）是一种文本形式的数据交换格式，不是网络协议，也不是编程语言。QueueMate 前后端经常用 JSON 传递结构化数据：

```json
{
  "slotId": 6001
}
```

`Content-Type: application/json` 表示实际发送的请求体是 JSON；`Accept: application/json` 表示客户端希望或能够接收 JSON 响应。

#### 请求方法、参数位置和幂等性

- GET：读取资源，参数常位于查询字符串中，一般不应修改服务器业务数据。
- POST：创建资源或发起操作，业务数据通常放在请求体中。
- PATCH：局部修改资源或改变状态；资源编号常位于路径中，修改内容位于请求体中。
- 路径参数：地址结构的一部分，例如 `/bookings/6001` 中的 `6001`。
- 查询参数：位于 `?` 后，例如 `?status=BOOKED`。
- 请求体：随请求发送的数据，例如新建预约数据或取消原因。

幂等是指：对同一个请求执行一次和执行多次，服务器资源的最终状态或业务效果相同。它不表示“数据库完全不发生改变”。例如第一次取消预约把状态从 `BOOKED` 改为 `CANCELLED`，以后重复取消若最终仍保持 `CANCELLED`，该操作仍可能是幂等的。GET 通常安全且幂等；POST 通常不幂等；PATCH 是否幂等取决于接口语义和实现。

#### HTTP 状态码与业务码

HTTP 状态码描述协议层面的处理结果；QueueMate 响应体中的 `code` 描述应用层面的业务结果，两者不能互相替代。

| HTTP 状态码 | 含义 | QueueMate 示例 |
| --- | --- | --- |
| 200 | 请求成功 | 查询场馆成功 |
| 201 | 成功创建资源 | 创建预约成功 |
| 400 | 请求格式或参数不合法 | `slotId=0`，`PARAM_INVALID` |
| 401 | 未通过身份认证 | JWT 缺失、无效或过期，`AUTH_UNAUTHORIZED` |
| 403 | 已识别身份但没有权限 | MERCHANT 创建用户预约，`AUTH_FORBIDDEN` |
| 405 | HTTP 方法不受接口支持 | 对只支持 POST 的地址发送 PUT，`METHOD_NOT_ALLOWED` |
| 409 | 请求与当前业务状态冲突 | 预约时段已满，`BOOKING_SLOT_FULL` |
| 500 | 服务器出现未预期处理的内部异常 | 未被正确转换的后端异常 |

创建预约成功时，`201` 与业务码 `"0"` 不矛盾：201 表示 HTTP 层成功创建资源，`"0"` 表示 QueueMate 业务执行成功。

### QueueMate 真实代码链路

```text
VenueDetailView.vue
  → api.js 发送 POST /api/v1/bookings，请求体包含 slotId
  → http.js 请求拦截器添加 Authorization: Bearer <JWT>
  → Vite 将 /api 请求代理到 Spring Boot 8080
  → Spring Security 检查身份与权限
  → BookingController 接收并校验 BookingCreateRequest
  → BookingService 执行业务规则和写库
  → ApiResponse 封装 code、message、data
  → http.js 成功时返回 response.data.data，失败时整理 err.status、err.code、err.message
```

关键代码入口：

- `frontend/queuemate-web/src/views/VenueDetailView.vue`
- `frontend/queuemate-web/src/services/api.js`
- `frontend/queuemate-web/src/services/http.js`
- `backend/queuemate-server/src/main/java/com/queuemate/booking/BookingController.java`
- `BookingCreateRequest.java`
- `BookingService.java`
- `common/api/ApiResponse.java`
- `common/exception/GlobalExceptionHandler.java`
- `config/RestAuthenticationEntryPoint.java`
- `config/RestAccessDeniedHandler.java`

#### 请求在哪一层结束

1. 没有 JWT：Spring Security 认证层返回 `401 / AUTH_UNAUTHORIZED`，不会进入 Controller 参数校验。
2. MERCHANT 创建预约：授权检查返回 `403 / AUTH_FORBIDDEN`，不是业务层失败。
3. USER 携带 `slotId=0`：通过认证授权后，在 Bean Validation 参数校验阶段返回 `400 / PARAM_INVALID`。
4. USER 预约已满时段：进入 Service 后发生业务冲突，返回 `409 / BOOKING_SLOT_FULL`。
5. 使用不支持的 PUT：在路由/请求方法匹配处返回 `405 / METHOD_NOT_ALLOWED`。

### 重要术语

- 客户端：主动发起请求的一方，本课是浏览器中的 Vue 应用或 curl。
- 服务器：接收请求并返回响应的一方，本课是 Spring Boot。
- 请求拦截器：Axios 发送请求前统一处理配置的函数，QueueMate 用它注入 JWT。
- 响应拦截器：收到响应后统一处理结果的函数，QueueMate 用它解包成功数据并整理错误。
- 认证：确认“你是谁”；未认证通常返回 401。
- 授权：确认“你能做什么”；身份明确但权限不足通常返回 403。
- 参数校验：检查输入格式、必填项和取值范围是否合法。
- 业务冲突：输入和身份合法，但当前业务状态不允许操作，通常返回 409。

### 常见错误与排障

1. 把 201 当成失败：Axios 默认将 200～299 都视为成功，201 不会自动进入 `catch`。
2. 只检查 HTTP 状态：还要理解响应体中的业务 `code`。
3. 混淆 401 和 403：401 是尚未通过认证；403 是身份已确认但权限不足。
4. 把所有失败归入业务层：安全过滤、参数校验、路由匹配都可能在 Service 前结束请求。
5. 把不支持的请求方法写成 500：接口存在但不接受该方法时应判断 405。
6. 混淆 Network 与页面变量：Network 显示原始 `{code, message, data}`；`http.js` 解包后，页面代码通常只得到 `data`。

---

### 本课习题

1. 解释 HTTP 与 JSON 的职责、HTTP 请求组成，以及 `Content-Type` 和 `Accept` 的区别。
2. 解释 GET、POST、PATCH，区分查询参数、路径参数和请求体，并说明哪些请求会修改业务数据以及什么是幂等性。
3. 解释 400、401、403、409、500，并说明 HTTP 201 与业务码 `"0"` 为什么不矛盾。
4. 判断五种请求的 HTTP 状态码、业务码和结束层次：无 JWT 创建预约；MERCHANT 创建预约；USER 使用 `slotId=0`；预约已满；使用不支持的 PUT。
5. 沿 Axios 代码回答创建预约的完整路径、JWT 注入点、201 的成功分支、成功响应解包结果和 409 错误对象。
6. 在浏览器 Network 面板记录一个真实 GET 请求的 URL、方法、状态、查询参数、响应 Content-Type 和响应 JSON 顶层字段。
7. 使用 curl 验证无 JWT 的非法预约参数和公开登录接口的空请求体，并解释为什么前者先返回 401、后者能进入校验返回 400。

### 个人答案

1. HTTP 规定 Web 客户端和服务器的请求与响应；JSON 是前后端交互使用的结构化数据格式。请求包括方法、地址、请求头和可选请求体。`Content-Type` 表示发送的数据格式，`Accept` 表示客户端能够接收的数据格式。
2. GET 用于读取资源，POST 用于创建资源或发起操作，PATCH 用于修改资源的一部分或状态。能够正确识别 `status=BOOKED` 查询参数、`6001` 路径参数以及创建、取消请求体；指出 POST 和 PATCH 会修改业务数据。幂等性回答为“重复执行后业务数据和数据库不发生改变”。
3. 400 是请求不合法，401 是身份认证失败，403 是权限不足，409 是业务冲突，500 是后端内部异常。201 是 HTTP 协议层成功，业务码 `"0"` 是业务逻辑成功，二者不矛盾。
4. 回答为：401/非 0/认证层；403/非 0/业务层；400/非 0/业务层；409/非 0/业务层；500/非 0/路由层。
5. 回答为：`POST /api/v1/bookings`，`slot.id` 放入请求体；`http.js` 请求拦截器把 JWT 添加到 `Authorization`；201 属于 2xx，默认不进入 `catch`；成功解包为 `{id:"6001", status:"BOOKED"}`；409 被整理成 `err.status=409`、`err.code="BOOKING_SLOT_FULL"` 和后端业务提示。
6. 实测 `GET http://localhost:5173/api/v1/venues/page?status=ACTIVE&page=1&pageSize=9` 返回 200；查询参数为 `status=ACTIVE`、`page=1`、`pageSize=9`；响应 `Content-Type` 为 `application/json`；响应 JSON 顶层字段为 `code`、`message`、`data`，属于完整 `ApiResponse`。
7. 第一次响应为 `401 / AUTH_UNAUTHORIZED`，提示先登录或提供有效 token；第二次为 `400 / PARAM_INVALID`，`data` 中包含用户名和密码不能为空。受保护的预约接口先经过 Spring Security，没有有效认证信息时在 Controller 前结束；登录接口公开，因此能进入 Controller 并触发参数校验。

### 批改与订正

| 题目 | 得分 | 批改 |
| --- | ---: | --- |
| 第 1 题 | 1.0 / 1.0 | 概念和请求组成正确；JSON 更准确的名称是“数据交换格式”。 |
| 第 2 题 | 1.25 / 1.5 | 方法与参数位置正确；幂等性不是数据库完全不变化，而是重复执行与执行一次的最终效果相同。 |
| 第 3 题 | 1.5 / 1.5 | 状态码及 201 与业务码 `"0"` 的分层解释正确；401 还包括 JWT 缺失、无效或过期。 |
| 第 4 题 | 0.75 / 1.5 | 403 应结束于授权层，400 应结束于参数校验层，不支持的 PUT 应为 `405 / METHOD_NOT_ALLOWED`；业务码应写精确值。 |
| 第 5 题 | 2.0 / 2.0 | 请求路径、JWT 注入、Axios 成功判断、响应解包和错误整理全部正确。 |
| 第 6 题 | 1.0 / 1.0 | Network 实践记录完整，能区分原始 `ApiResponse` 与 Axios 解包后的 `data`。 |
| 第 7 题 | 1.0 / 1.0 | 两次响应和“安全认证先于 Controller 参数校验”的原因解释正确。 |

订正重点：

1. 幂等：重复相同请求与只执行一次的最终业务效果相同，不等于完全不写数据库。
2. 401 是认证层，403 是授权层，400 可以来自参数绑定/校验，409 来自业务冲突，405 来自路由或请求方法匹配，500 才是服务器内部异常。
3. 回答统一错误响应时，应写出 HTTP 状态、精确业务码、错误消息和结束层次。

### 本课结果

```text
本课：1.2 HTTP 请求、响应、JSON 和状态码
已掌握：HTTP 与 JSON 的职责；请求组成；GET/POST/PATCH；常见状态码；HTTP 状态与业务码分层；Axios JWT 注入、成功解包与错误整理；Security 先于 Controller 参数校验
还不稳：幂等性的精确定义；安全认证/授权层、参数校验层、业务层和路由层的准确区分；失败业务码的精确表达
实践结果：完成浏览器 Network 请求检查和两次 curl 分层验证
技术知识评分：8.5 / 10
结果：通过
下一课：1.3 从“登录”按钮追一次完整请求
课后任务：复习 401、403、400、405、409 的结束层次，并用“重复执行与执行一次的最终效果相同”复述幂等性
```

---

## 第 1.3 课：从“登录”按钮追一次完整请求

### 本课目标

1. 从 `LoginView.vue` 的表单提交追到 `POST /api/v1/auth/login`。
2. 说明 Vite 5173、Spring Boot 8080 和 MySQL 3306 在登录链路中的连接方向。
3. 区分 Security 公开接口放行、Controller 参数校验、Service 凭据与用户状态检查。
4. 说明 BCrypt 密码匹配、JWT 签发、统一响应、Axios 解包、本地会话保存和登录后路由跳转。

### 知识笔记

- 登录分为两个阶段：用户名和密码验证成功后，后端才签发 JWT；后续受保护请求再携带该 JWT。
- `SecurityConfig` 对 `/api/v1/auth/login` 配置 `permitAll()`。没有 `Authorization` 时，`JwtAuthenticationFilter` 继续放行，因此登录请求不需要预先持有 JWT。
- `LoginView.submit()` 先执行 Element Plus 前端校验；校验失败时不会产生 Network 请求。前端校验改善体验，后端 `@Valid` 才是不能绕过的接口校验。
- `authApi.login()` 调用 Axios 的 `http.post('/auth/login', payload)`；`baseURL=/api/v1`，最终路径为 `POST /api/v1/auth/login`。
- Axios 请求拦截器在发送前注入已有 JWT；Axios 响应拦截器在后端响应后返回 `response.data.data`。两者的执行方向不能混淆。
- `AuthController` 接收 JSON、转换为 `LoginRequest`、执行 `@Valid` 并调用 `AuthService`。
- `AuthService` 使用 MyBatis-Plus 按用户名查询 `users`，再用 `PasswordEncoder.matches()` 验证输入密码与数据库 BCrypt 哈希是否匹配。它不解密数据库密码。
- 用户不存在或密码错误统一返回 `401 / AUTH_CREDENTIALS_INVALID`，避免直接暴露用户名是否存在；密码正确但账号禁用返回 `403 / USER_DISABLED`。
- `JwtTokenService` 在凭据和用户状态验证通过后签发 JWT，默认有效期为 7200 秒；登录成功的 HTTP 状态为 200，业务码为 `"0"`。
- `UserRoleService` 查询 `user_roles` 并与用户主角色合并，随后组成 `LoginResponse(token, tokenType, expiresIn, user)`。
- `http.js` 解包成功响应后，`LoginView` 得到的 `session` 就是 `LoginResponse`，不是完整的 `{code,message,data}`。
- `authState.setSession()` 写入 `queuemate.token`、`queuemate.user`、`queuemate.activeRole`；随后 Vue Router 按 `redirect` 或角色首页跳转。
- 后续受保护请求若返回 `401 / AUTH_UNAUTHORIZED`，响应拦截器会清除会话并跳转登录页；登录接口自身的 401 被特意排除，只在当前页面显示凭据错误。

### QueueMate 真实代码链路

```text
LoginView.submit
→ 前端表单校验
→ authApi.login
→ Axios 请求拦截器
→ POST /api/v1/auth/login
→ Vite 5173 代理到 Spring Boot 8080
→ JwtAuthenticationFilter
→ SecurityConfig permitAll
→ AuthController + @RequestBody + @Valid
→ AuthService.login
→ UserMapper 查询 users / MySQL 3306
→ PasswordEncoder.matches
→ 用户状态检查
→ JwtTokenService.generateToken
→ UserRoleMapper 查询 user_roles
→ LoginResponse
→ ApiResponse(code, message, data)
→ HTTP 200 JSON
→ Axios 响应拦截器解包 data
→ authState.setSession
→ Vue Router 按 redirect 或角色跳转
```

### 重要术语

- 请求拦截器：Axios 发送请求前统一修改请求配置，本项目用于添加 `Authorization`。
- 响应拦截器：Axios 收到响应后统一解包成功数据或整理失败对象。
- 密码哈希：密码的单向验证结果；QueueMate 使用带 `{bcrypt}` 标识的 BCrypt 哈希，而不是可逆密文。
- `permitAll`：该接口允许未认证请求访问，不代表接口跳过参数校验和业务凭据校验。
- `SecurityContext`：保存当前一次请求中已认证用户身份和权限的容器。

### 常见错误与排障

1. 点击登录但没有 Network 请求：先检查表单校验和 `submit()`，不要直接排查后端。
2. 页面可打开但登录无法连接：5173 正常不代表 8080 或 3306 正常。
3. 空字段返回 400：结束于 Controller 前后的参数绑定/校验；不是密码验证失败。
4. 密码错误返回 401：结束于 `AuthService` 凭据检查；不是参数校验层。
5. JWT 过期返回 401：结束于 Spring Security 认证层；不是登录业务层。
6. 页面读取 `session.data.token` 失败：响应拦截器已经解包，正确路径是 `session.token`。
7. 把响应拦截器放在发请求之前：应改为“请求拦截器发送前，响应拦截器返回后”。

### 本课习题

1. 解释“用户名和密码换取 JWT”与“JWT 访问受保护接口”的区别。
2. 判断空用户名、密码错误、账号禁用和 JWT 过期的 HTTP 状态、精确业务码及结束层次。
3. 解释密码哈希、`PasswordEncoder.matches()` 和统一凭据错误提示。
4. 排列从 `LoginView.submit` 到 Vue Router 的真实调用顺序并说明各节点职责。
5. 预测 Axios 解包后的 `session`、`session.data`、localStorage 键和 USER 默认跳转地址。
6. 比较登录凭据错误与受保护接口认证失败两种 401 的后端来源和前端行为。
7. 在浏览器 Network 与 Application 面板追踪一次 `alice / User123456` 的成功登录。
8. 追踪一次错误密码登录，记录 HTTP 状态、业务码、消息、页面和会话变化。

### 个人答案

- 第 1 题：能够说明用户名和密码验证成功后后端才返回 JWT，并指出 JWT 由 `JwtTokenService` 签发；未明确把 `permitAll()` 作为登录无需 JWT 的直接配置依据。
- 第 2 题：四种情况的 HTTP 状态和业务码正确；把密码错误、账号禁用和 JWT 过期分别误归为参数校验阶段或“登录层”。
- 第 3 题：能够说明 `matches()` 不解密密码，并理解统一错误提示可减少账号枚举；“数据库不存明文”的风险说明过于笼统。
- 第 4 题：能够列出主干节点和多数职责；误把 Axios 响应拦截器放在请求发送阶段，且遗漏后端响应返回后再解包的顺序。
- 第 5～8 题：未独立作答；学生要求直接查看参考答案，因此不计为独立完成。

### 批改与订正

| 题目 | 得分 | 批改 |
| --- | ---: | --- |
| 第 1 题 | 0.8 / 1.0 | 主线正确；补充 `SecurityConfig.permitAll()` 是登录无需预先携带 JWT 的直接依据。 |
| 第 2 题 | 0.6 / 1.0 | 状态和业务码正确；密码错误属于 Service 凭据检查，账号禁用属于 Service 用户状态检查，JWT 过期属于 Security 认证层。 |
| 第 3 题 | 0.8 / 1.0 | `matches()` 和账号枚举解释正确；需补充明文泄露风险、加盐和 BCrypt 计算成本。 |
| 第 4 题 | 1.0 / 1.5 | 主干正确；发送前应是请求拦截器，响应拦截器必须位于 HTTP 响应返回之后。 |
| 第 5 题 | 0 / 1.5 | 未独立作答，已查看参考答案。 |
| 第 6 题 | 0 / 1.0 | 未独立作答，已查看参考答案。 |
| 第 7 题 | 0 / 2.0 | 未完成浏览器成功登录实践，已查看预期结果。 |
| 第 8 题 | 0 / 1.0 | 未完成错误密码实践，已查看预期结果。 |

订正重点：

1. 登录接口能够匿名访问是因为 `permitAll()`；过滤器链仍然存在。
2. 请求拦截器位于请求发出前，响应拦截器位于后端返回后。
3. 参数校验、Service 凭据检查、Service 用户状态检查和 Security JWT 认证是不同结束层次。
4. 已公布答案的原题不再用于补考；下次使用等价的新场景验证是否真正掌握。

### 本课结果

```text
本课：1.3 从“登录”按钮追一次完整请求
已掌握：登录后签发 JWT 的基本顺序；主干代码节点；密码哈希不需要解密；统一凭据错误可减少账号枚举
还不稳：permitAll 与过滤器链的关系；请求/响应拦截器时序；参数校验、Service 检查与 Security 认证的准确分层；从后端响应返回前端的完整顺序
实践结果：未完成 Network、localStorage、成功登录和错误密码分支的独立验证
技术表达评分：5 / 10
验收成绩：3.2 / 10
结果：未通过，需使用未公布答案的新题补强验收
下一课：1.3 补强验收，通过后进入 2.1 表、行、列、主键和外键
课后任务：重新手画一次登录请求与响应双向链路，并分别标出请求拦截器、响应拦截器、参数校验、Service 凭据检查和 Security JWT 认证层
```

---

## 后续课程追加模板

```text
## 第 X.X 课：课程名称

### 本课目标

### 知识笔记

### QueueMate 真实代码链路

### 重要术语

### 常见错误与排障

### 本课习题

### 个人答案

### 批改与订正

### 本课结果
本课：
已掌握：
还不稳：
实践结果：
技术表达评分：
下一课：
课后任务：
```
