# QueueMate 项目交接文档

> 最后更新：2026-07-16
> 当前分支：`main`  
> 认证初版基线提交：`2bc876e feat: add initial authentication module`  
> GitHub：`git@github.com:Xixi051108/QueueMate.git`

## 1. 我们在做什么

QueueMate 是一个测开作品集项目，使用奶茶店、自习室、羽毛球场等模拟场景，实现生活排队、预约和站内余额支付。目标不只是完成 CRUD，而是形成一条可展示的测开生态链：

```text
需求与数据库设计
  -> Spring Boot API
  -> Vue3 Web UI
  -> Postman/Newman 接口测试
  -> JMeter 并发与性能测试
  -> Playwright UI 自动化
  -> GitHub Actions CI
```

技术栈：

- 后端：Java 21、Spring Boot 3.3.5、MyBatis-Plus 3.5.9、MySQL 8.0
- 前端规划：Vue3、Element Plus、Axios
- 测试规划：JUnit 5、Mockito、Postman/Newman、JMeter、Playwright
- CI 规划：GitHub Actions

项目明确不接真实地图、短信、微信登录、真实支付平台。地点、账号、钱包、预约和队列均使用本地模拟数据。

## 2. 当前仓库与本机环境

工作目录：

```text
D:\QueueMate
```

本机已配置：

- JDK：`D:\JAVA\jdk-21`
- Maven：`D:\Maven\apache-maven-3.9.16\bin\mvn.cmd`
- MySQL 客户端：`D:\MySQL\MySQL Server 8.0\bin\mysql.exe`
- MySQL 服务名：`MySQL80`
- 数据库：`queuemate`
- VS Code：已配置 Java 启动任务和密码输入框

严禁把本机 MySQL 密码写入代码、文档、Git 或命令脚本。启动时使用 `DB_PASSWORD` 环境变量，或者使用 VS Code task/launch 的密码输入框。

会话结束后后台进程可能停止。新会话首先执行：

```powershell
Invoke-RestMethod http://localhost:8080/api/v1/health
```

如果未运行：

```powershell
cd D:\QueueMate\backend\queuemate-server
$env:DB_PASSWORD = '在本机临时输入，不要提交'
& 'D:\Maven\apache-maven-3.9.16\bin\mvn.cmd' spring-boot:run
```

## 3. 已经完成的内容

### 3.1 项目设计与骨架

- 前后端同仓目录已经建立。
- README、需求、数据库、API、测试计划和生态链文档已建立。
- `knowledge/` 用于记录每个开发板块的学习结果。
- `sql/schema.sql` 和 `sql/data.sql` 已建立，数据库包含用户、地点、时段、预约、钱包、钱包流水、队列号码等 MVP 表。
- 本地 MySQL 已初始化，模拟数据可用。
- VS Code 的 Maven 启动和调试配置已建立。

### 3.2 认证模块初版

当前只保留认证模块最初版本，公开接口为：

```text
POST /api/v1/auth/register
POST /api/v1/auth/login
GET  /api/v1/auth/me
GET  /api/v1/health
```

已实现：

- 注册固定创建 `USER/ACTIVE` 用户。
- 注册用户和零余额钱包在同一事务内创建。
- `users.username` 重复时返回 `409/USERNAME_EXISTS`。
- 密码使用 Spring Security BCrypt，不保存明文。
- 登录检查账号状态并签发 JWT Bearer token。
- JWT 包含用户 ID、用户名和角色，默认有效期 7200 秒。
- 每次鉴权重新查询数据库，因此角色或账号状态变更可立即生效。
- 未登录、伪造 token 和越权使用统一 JSON 401/403 响应。
- 数据库连接和 JWT 配置支持环境变量覆盖。

当前参数规则是认证初版规则：

- 用户名：3 到 50 个字符，目前只有长度校验，没有字符白名单。
- 密码：8 到 64 个字符，目前只有长度校验，没有强度分级和复杂字符规则。
- 昵称：必填，最多 100 个字符。
- 手机号：可选，填写时必须是模拟的 11 位格式。

当前刻意不存在以下内容：

- `POST /api/v1/auth/password-strength`
- `PasswordPolicy`
- `WEAK/MEDIUM/STRONG` 密码强度
- 中文用户名字符白名单或无效符号限制
- 大写、小写、数字、符号组合要求

不要在没有用户明确确认的情况下重新加入这些能力。

### 3.3 地点管理与 RBAC

已实现接口：

```text
GET   /api/v1/venues
GET   /api/v1/venues/{id}
POST  /api/v1/venues
PUT   /api/v1/venues/{id}
PATCH /api/v1/venues/{id}/status
```

已实现：

- 地点列表和详情允许匿名访问。
- 列表支持 `category`、`status`、`keyword` 筛选。
- `MERCHANT/ADMIN` 可创建地点，`USER` 返回统一 `403/AUTH_FORBIDDEN`。
- 商家创建地点时归属强制绑定当前登录商家；请求中的其他 `merchantId` 不生效。
- 管理员创建地点时必须指定可用的 `MERCHANT`，否则返回 `400/MERCHANT_INVALID`。
- 商家只能修改、启停自己名下地点，越权返回 `403/RESOURCE_NOT_OWNED`。
- 管理员可以修改、启停任意地点。
- 同一商家地点名称重复返回 `409/VENUE_NAME_EXISTS`。
- 地点不存在返回 `404/VENUE_NOT_FOUND`。
- 地点 ID 和商家 ID 在响应中序列化为字符串，避免 JavaScript 丢失雪花 ID 精度。
- 方法级权限异常已补充统一 403 映射，不再被通用异常处理成 500。

### 3.4 固定预约时段

已实现接口：

```text
GET   /api/v1/venues/{venueId}/slots
POST  /api/v1/venues/{venueId}/slots
PATCH /api/v1/venues/{venueId}/slots/{slotId}/status
```

已实现：

- 时段列表允许匿名访问，支持 `dateFrom`、`dateTo` 和 `status` 筛选。
- 查询结果按日期、开始时间和 ID 稳定排序，并返回派生余量 `availableCapacity`。
- 地点所属 `MERCHANT/ADMIN` 可创建和切换时段状态，`USER` 返回统一 403。
- 商家资源归属复用地点模块规则，跨店操作返回 `403/RESOURCE_NOT_OWNED`。
- 只有 `ACTIVE` 且 `bookingEnabled=true` 的地点可以创建或重新打开时段。
- 日期不得早于今天，容量必须大于 0，开始时间必须早于结束时间，价格不得为负。
- 新时段固定创建为 `OPEN`、`reservedCount=0`。
- 精确重复时段返回 `409/BOOKING_SLOT_EXISTS`，数据库唯一键竞态也映射为同一错误。
- 时段与路径地点不匹配时返回 `404/BOOKING_SLOT_NOT_FOUND`。
- 时段响应中的 `id`、`venueId`、`createdBy` 使用字符串，避免 JavaScript 精度丢失。

当前刻意未实现用户预约、容量扣减、并发防超卖、重复预约、支付和退款。

### 3.5 测试与验证

干净构建命令：

```powershell
cd D:\QueueMate\backend\queuemate-server
& 'D:\Maven\apache-maven-3.9.16\bin\mvn.cmd' clean package
```

最后验证结果：

```text
Tests run: 47, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

测试分布：

- `AuthControllerTest`：1 个
- `AuthServiceTest`：6 个
- `JwtTokenServiceTest`：2 个
- `QueueMateApplicationTests`：1 个
- `VenueServiceTest`：11 个
- `VenueControllerSecurityTest`：6 个
- `BookingSlotServiceTest`：13 个
- `BookingSlotControllerSecurityTest`：7 个

真实 HTTP + MySQL 回归已验证：

- 健康检查返回 `UP`。
- 注册成功并创建 `USER`。
- 密码以 `{bcrypt}` 开头。
- 自动创建余额 `0.00`、状态 `ACTIVE` 的钱包。
- 重复用户名返回 409。
- 正确密码登录并返回 Bearer JWT。
- `/auth/me` 返回当前用户。
- 错误密码、缺失 token、伪造 token 返回 401。
- 非法 JSON 返回 `400/PARAM_INVALID`。
- 回归测试用户和钱包已清理，没有污染数据库。
- 打包后的可执行 JAR 已在临时端口 8081 独立启动验证。
- 地点公开列表和详情查询成功。
- 非法地点筛选返回 `400/PARAM_INVALID`。
- USER 创建地点返回 `403/AUTH_FORBIDDEN`。
- 商家创建地点时即使提交其他 `merchantId`，归属仍为当前商家。
- 同一商家重复地点名称返回 `409/VENUE_NAME_EXISTS`。
- 商家跨店修改返回 `403/RESOURCE_NOT_OWNED`。
- 管理员可停用任意地点。
- 地点响应中的 64 位 ID 保持字符串精度。
- 地点回归产生的临时数据已删除，数据库残留数量为 0。
- 时段公开列表和状态筛选成功，初始化地点 4002 返回 2 个开放时段。
- 反向日期范围返回 `400/PARAM_INVALID`。
- USER 创建时段返回 `403/AUTH_FORBIDDEN`。
- 新时段创建为 `OPEN`、预约数 0、可用余量等于容量，64 位 ID 保持字符串精度。
- 精确重复时段返回 `409/BOOKING_SLOT_EXISTS`。
- 商家跨店管理时段返回 `403/RESOURCE_NOT_OWNED`。
- 管理员关闭和所属商家重新打开时段成功。
- 时段与路径地点不匹配返回 `404/BOOKING_SLOT_NOT_FOUND`。
- 非法时间范围和未启用预约地点分别返回稳定业务错误。
- 时段回归产生的临时数据已删除，数据库残留数量为 0。

Postman 资产：

- `tests/postman/QueueMate.postman_collection.json`
- `tests/postman/QueueMate.local.postman_environment.json`
- 当前共 19 个请求，JSON 可正常解析。
- 已加入地点公开查询、USER 禁止创建、商家创建/更新/停用和不存在地点断言。
- 已加入时段公开查询、USER 禁止创建、商家创建、重复时段和关闭时段断言。
- 尚未使用 Postman Runner 或 Newman 正式执行，所以不能把 Newman 标记为已通过。

### 3.6 模拟账号

仅用于本地开发：

| 角色 | 用户名 | 密码 |
| --- | --- | --- |
| ADMIN | `admin` | `Admin123456` |
| MERCHANT | `merchant_tea`、`merchant_sport` | `Merchant123456` |
| USER | `alice`、`bob`、`carol` | `User123456` |

SQL 文件和本地数据库保存的是 BCrypt 哈希。以上明文仅为公开的本地模拟凭据，不能用于真实环境。

## 4. 当前卡点与已知边界

目前没有阻塞开发的故障，认证初版可以正常构建、启动和调用。

已知边界：

- 使用有效 token 访问不存在的受保护路径时，`NoResourceFoundException` 会进入通用异常处理并返回 500，而不是 404。这是初版现存问题，不影响已有接口；后续可增加 404 异常映射和回归测试。
- 密码策略目前只有 8 到 64 字符的长度限制，不代表生产级密码安全。
- JWT 只有 Access Token，没有 Refresh Token、主动登出、黑名单和密钥轮换。
- 登录尚无限流、失败次数锁定、审计日志和验证码。
- Postman 尚未通过 Newman 执行。
- 前端还没有进入实际功能开发。
- 地点列表首版返回完整列表，尚未分页；数据量扩大后需要升级为分页响应。
- 时段首版只禁止完全相同的时间范围，没有禁止重叠时段。
- 时段创建后暂不支持修改日期、时间、容量和价格，只支持打开或关闭。

## 5. 下一步计划

下一模块建议实现“用户预约 + 并发防超卖”，不要马上扩展密码策略或进入前端。

建议顺序：

1. 明确免费预约首版接口、状态和取消规则。
2. 实现 `Booking` 实体、Mapper、Service、Controller 和 DTO。
3. `POST /bookings` 仅允许 `USER`，校验地点和时段仍可预约。
4. 使用带条件的数据库原子更新执行 `reserved_count + 1`，受影响行为 0 时返回已满或不可用。
5. 使用 `uk_bookings_user_slot` 和业务校验双重阻止重复预约。
6. 将容量更新和预约记录插入放在同一事务内，任一步失败都回滚。
7. 实现 `GET /bookings/my` 和取消预约，取消时原子回补名额。
8. 增加并发集成测试，确认成功数不超过容量且 `reserved_count` 与有效预约数一致。
9. 增加 Postman 预约请求和 `knowledge/05-booking-concurrency.md`。
10. 完整回归后提交并推送 GitHub。

并发预约模块完成后再依次推进：

```text
站内钱包支付与退款
  -> 现场排队状态机
  -> 繁忙时段统计
  -> Vue3 前端
  -> Newman/JMeter/Playwright
  -> GitHub Actions
```

## 6. 绝对不要再踩的坑

### 6.1 不要在没有版本点时连续大改

用户已明确要求：以后每完成一轮修改都必须提交并推送，方便回退。

固定流程：

```text
确认需求边界
  -> 修改代码和文档
  -> 自动化测试
  -> 真实接口/数据库验证
  -> 清理测试数据
  -> 扫描敏感信息
  -> git commit
  -> git push
  -> 汇报 commit id
```

不要把多个用户决策混进同一个大提交。不要只修改不提交，也不要只提交不推送。

### 6.2 不要使用破坏性 Git 回退

- 当前认证基线已经推送，优先使用 commit 创建安全分支或 `git revert`。
- 禁止擅自使用 `git reset --hard`、`git checkout -- <file>` 或删除整个工作区。
- 工作区可能包含用户修改，回退前必须先看 `git status` 和 diff。

### 6.3 不要把“用户名允许中文”误解成“密码允许中文”

这个话题曾发生需求误解并引起大范围修改，之后又被要求恢复认证初版。当前基线没有最终采用任何中文用户名或密码复杂度方案。

如果未来重新设计，必须先和用户确认以下四项，再写代码：

1. 用户名允许哪些字符。
2. 密码是否允许中文、空格和 Unicode。
3. 最小/最大长度。
4. 是否强制大写、小写、数字、特殊符号，以及如何划分强中弱。

### 6.4 不要只更新代码而漏掉其他资产

接口变化必须同步检查：

- `README.md`
- `docs/requirement.md`
- `docs/api_design.md`
- `docs/test_plan.md`
- `knowledge/`
- `tests/postman/`
- `sql/data.sql`
- 本地 MySQL 模拟数据

曾经出现代码、Postman、示例密码和测试报告规则不一致的问题，后续必须用全仓搜索检查旧接口和旧枚举是否残留。

### 6.5 不要把“生成测试资产”写成“测试已通过”

- JUnit/Maven 只有实际执行成功才能标记通过。
- Postman 集合只完成 JSON 解析，不等于 Newman 已执行。
- JMeter 和 Playwright 同理，必须保留命令、结果摘要和报告证据。

### 6.6 不要污染本地数据库

真实注册冒烟会创建用户和钱包。测试完成后必须按外键顺序清理钱包再清理用户，并确认剩余数量为 0。不要删除初始化账号或用户真实创建的数据。

### 6.7 Windows 与工具注意事项

- 当前环境中 `rg.exe` 曾被系统拒绝执行，可回退到 PowerShell `Get-ChildItem | Select-String`。
- `Get-NetTCPConnection` 可能因权限不足失败，优先用实际 HTTP 健康检查判断服务是否运行。
- Maven 首次执行 `clean package` 可能需要联网下载插件，应耐心等待，不能把下载过程误判为卡死。
- PowerShell 双引号会展开 `$`，直接在命令中处理 BCrypt 哈希容易破坏内容。优先通过应用生成/校验，或使用安全的 MySQL 交互输入。
- Git 的 LF/CRLF 提示不是编译错误，不要为了消除提示批量重写所有文件。

## 7. 新会话接手检查清单

```powershell
cd D:\QueueMate
git status --short
git log -5 --oneline
git fetch origin
git rev-parse HEAD
git rev-parse origin/main
```

确认工作区和远程状态后：

```powershell
cd D:\QueueMate\backend\queuemate-server
& 'D:\Maven\apache-maven-3.9.16\bin\mvn.cmd' test
```

最后检查健康接口。当前基线应看到 47 个测试全部通过，认证、地点和时段接口均可用，并且项目中不存在 `password-strength`、`PasswordPolicy` 或 `PASSWORD_WEAK`。
