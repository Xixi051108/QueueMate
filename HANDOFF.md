# QueueMate 项目交接文档

> 最后更新：2026-07-21
> 当前分支：`main`  
> 当前功能代码基线：`484d984 feat: expand venue categories`
> 认证初版基线提交：`2bc876e feat: add initial authentication module`  
> GitHub：`git@github.com:Xixi051108/QueueMate.git`

## 1. 我们在做什么

QueueMate 是一个测开作品集项目，使用奶茶店、自习室、羽毛球场、饭店、酒店、妆造店、商场等模拟场景，实现生活排队、预约和站内余额支付。目标不只是完成 CRUD，而是形成一条可展示的测开生态链：

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

已有本地数据库在首次运行商家入驻版本前，还需要执行可重复迁移：

```sql
source D:/QueueMate/sql/migrations/20260720_merchant_onboarding.sql;
```

该迁移创建 `user_roles` 和 `merchant_applications`，并把现有账号角色回填到多身份表。

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
- 地点分类共 7 类：`TEA_SHOP`、`STUDY_ROOM`、`BADMINTON_COURT`、`RESTAURANT`、`HOTEL`、`MAKEUP_STUDIO`、`SHOPPING_MALL`，前端分别显示为奶茶店、自习室、羽毛球场、饭店、酒店、妆造店和商场。
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

### 3.5 免费预约与并发防超卖

已实现接口：

```text
POST  /api/v1/bookings
GET   /api/v1/bookings/my
PATCH /api/v1/bookings/{id}/cancel
```

已实现：

- `POST /bookings` 仅允许 `USER`，匿名返回 401，`MERCHANT/ADMIN` 返回 403。
- 免费和收费时段均可预约。
- 免费预约成功状态为 `BOOKED`，支付状态为 `NOT_REQUIRED`，实付金额为 `0`。
- 收费预约在同一事务内完成钱包扣款、支付流水、预约记录和 `AVAILABLE` 消费凭证创建。
- 校验地点启用、预约开关、时段开放和时段尚未开始。
- 使用数据库条件更新原子执行 `reserved_count + 1`，并同时校验地点和时段状态。
- 容量增加与预约记录插入处于同一事务，插入失败时容量自动回滚。
- 使用业务预检查和 `uk_bookings_user_active_slot` 唯一键双重阻止同一用户重复持有同一时段的 `BOOKED` 预约。
- `GET /bookings/my` 仅返回当前用户数据，支持预约状态筛选。
- 用户可取消本人预约，管理员可取消任意预约，商家不可取消。
- 取消使用 `BOOKED -> CANCELLED` 条件更新，成功后在同一事务内原子回补名额。
- 取消记录继续保留；只要时段仍开放、未开始且有余量，用户可以重新预约同一时段并创建一条新预约记录。
- 并发重复取消只有一个请求成功，不会重复回补名额。
- 预约响应中的 `id`、`userId`、`venueId`、`slotId` 使用字符串。
- 不存在路径和不支持的 HTTP 方法已统一返回 JSON `404/RESOURCE_NOT_FOUND` 与 `405/METHOD_NOT_ALLOWED`。

### 3.6 钱包、付费预约、消费码和退款

- 时段 `price` 表示预付消费金额，不是额外预约手续费。
- 已实现 `GET /api/v1/wallets/my`、`POST /api/v1/wallets/my/recharge` 和 `GET /api/v1/wallets/my/transactions`。
- 钱包操作仅允许 `USER`，新注册用户继续自动创建零余额钱包。
- 充值、支付和退款都写入余额变动前后值完整的钱包流水。
- 钱包使用 `select ... for update` 串行化同一用户余额变更，余额不足不会扣成负数。
- 收费预约成功后生成一张与预约一对一的 `BookingVoucher` 消费凭证。
- 用户在“我的预约”中查看消费码，商家到店核销后预约变为 `FULFILLED`。
- 消费凭证状态为 `AVAILABLE / REDEEMED / VOID / EXPIRED`。
- 未核销收费预约取消退款时，消费码同步变为 `VOID`。
- 首版退款规则为时段开始前且消费凭证仍为 `AVAILABLE` 时全额退款；开始后不允许用户自助取消。
- 已核销消费码不可退款、取消或再次核销。
- 首版核销窗口设计为预约开始前 30 分钟至时段结束时间。
- 商家只能核销自己地点的消费码，管理员可全局核销。
- 消费码使用不可预测随机值，日志不得记录完整消费码。
- 已实现 `POST /api/v1/venues/{venueId}/booking-vouchers/redeem`。
- 核销与退款共同锁定消费凭证行，防止同一消费码同时被退款和核销。
- 定时任务会把超过 `valid_until` 的 `AVAILABLE` 凭证转为 `EXPIRED`，并把对应预约转为 `NO_SHOW`。
- 已实现管理员钱包全局流水查询和正负余额调整，调整写入 `ADJUSTMENT` 流水。
- `sql/schema.sql`、`sql/data.sql` 和一次性迁移脚本已同步，本地 MySQL 已执行迁移。

### 3.7 现场排队与繁忙统计

- 已实现取号、本人号码查询、公开当前进度、叫号、完成和过号接口。
- 排队状态固定为 `WAITING -> CALLED -> COMPLETED/MISSED`，非法或并发重复流转返回 `409/QUEUE_STATUS_INVALID`。
- 商家只能操作自己地点的号码，管理员可全局操作。
- `queue_daily_sequences` 使用地点和日期联合主键，通过数据库原子递增分配每日号码。
- `queue_tickets` 同时约束每日号码唯一，以及同一账号同一地点同一天只能持有一个有效号码。
- 已实现 `GET /api/v1/stats/venues/{venueId}/busy-hours`，合并预约时段小时和取号小时并计算热度。
- 需求文档范围内的后端模块已经全部实现。

### 3.8 测试与验证

干净构建命令：

```powershell
cd D:\QueueMate\backend\queuemate-server
& 'D:\Maven\apache-maven-3.9.16\bin\mvn.cmd' clean package
```

最后验证结果：

```text
Tests run: 137, Failures: 0, Errors: 0, Skipped: 0
BUILD SUCCESS
```

测试分布：

- `AuthControllerTest`：1 个
- `AuthServiceTest`：6 个
- `JwtTokenServiceTest`：2 个
- `UserResponseTest`：1 个
- `QueueMateApplicationTests`：1 个
- `VenueServiceTest`：11 个
- `VenueControllerSecurityTest`：10 个
- `BookingSlotServiceTest`：13 个
- `BookingSlotControllerSecurityTest`：7 个
- `BookingServiceTest`：18 个
- `BookingControllerSecurityTest`：10 个
- `WalletServiceTest`：8 个
- `WalletControllerSecurityTest`：4 个
- `WalletAdminControllerSecurityTest`：4 个
- `BookingVoucherServiceTest`：8 个
- `BookingVoucherControllerSecurityTest`：4 个
- `QueueTicketServiceTest`：9 个
- `QueueTicketControllerSecurityTest`：7 个
- `BusyHoursServiceTest`：3 个
- `BusyHoursControllerSecurityTest`：4 个
- `MerchantApplicationServiceTest`：6 个

真实 HTTP + MySQL 回归已验证：

- 健康检查返回 `UP`。
- 注册成功并创建 `USER`。
- 密码以 `{bcrypt}` 开头。
- 自动创建余额 `0.00`、状态 `ACTIVE` 的钱包。
- 重复用户名返回 409。
- 正确密码登录并返回 Bearer JWT。
- `/auth/me` 返回当前用户。
- 登录和 `/auth/me` 中的 19 位用户 ID 以字符串返回，避免浏览器精度舍入导致商家地点归属筛选失败。
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
- 创建容量为 3 的临时免费时段，12 个不同用户并发预约，最终 3 个成功、9 个返回 `409/BOOKING_SLOT_FULL`。
- 并发结束后 `reserved_count=3`，有效 `BOOKED` 数量为 3，重复用户时段组合为 0。
- 获胜用户重复预约返回 `409/BOOKING_DUPLICATE`。
- 单元回归已验证取消预约后可重新预约同一时段；真实本地库需先执行 `sql/migrations/20260717_allow_booking_rebook.sql` 再联调。
- 同一预约并发取消两次时，1 次成功、1 次返回 `409/BOOKING_STATUS_INVALID`。
- 取消释放名额后，先前失败用户可成功预约，最终容量和有效预约数继续一致。
- 免费预约阶段曾验证收费时段拒绝；当前版本已升级为钱包付费预约。
- 预约并发回归产生的 4 条预约、1 个时段、12 个钱包和 12 个用户已全部清理。
- 有效 token 访问不存在路径返回 `404/RESOURCE_NOT_FOUND`，使用不支持方法返回 `405/METHOD_NOT_ALLOWED`。
- 新用户充值 100 后，收费预约 20 将余额扣至 80，生成支付流水和 `AVAILABLE` 消费码。
- 商家核销后预约变为 `FULFILLED`、消费码为 `REDEEMED`，重复核销与核销后取消均返回 409。
- 另一笔收费预约取消后变为 `CANCELLED/REFUNDED`，消费码变为 `VOID`，余额退回。
- 容量 3 的收费时段由 6 个用户并发预约，3 个成功、3 个冲突；成功者余额 30，失败者余额 50。
- 数据库中并发结果为 3 条支付流水、3 张消费凭证、`reserved_count=3`。
- 同一消费码并发核销结果为 200/409。
- 本轮临时用户、钱包、流水、预约、消费码和时段已全部清理。
- 同一用户重复持有有效号码返回 `409/QUEUE_TICKET_DUPLICATE`。
- 完整验证 `WAITING -> CALLED -> COMPLETED` 和 `WAITING -> CALLED -> MISSED`。
- 6 个用户并发取号全部成功，数据库得到连续且唯一的 `3-8` 号。
- 同一号码并发叫号结果为 200/409。
- 管理员余额增加 5 后扣减 5，余额恢复为 200，并产生 2 条调整流水。
- 定时任务将过期消费码转为 `EXPIRED`、预约转为 `NO_SHOW`。
- 繁忙统计正确合并预约和排队的小时数量。
- 本轮排队、统计和过期验证产生的临时数据与临时数据库账号已全部清理。

Postman 资产：

- `tests/postman/QueueMate.postman_collection.json`
- `tests/postman/QueueMate.local.postman_environment.json`
- 当前共 43 个请求，JSON 可正常解析。
- 已加入地点公开查询、USER 禁止创建、商家创建/更新/停用和不存在地点断言。
- 已加入时段公开查询、USER 禁止创建、商家创建、重复时段和关闭时段断言。
- 已加入钱包、预约、退款、消费码、排队状态机、管理员余额调整和繁忙统计断言。
- 尚未使用 Postman Runner 或 Newman 正式执行，所以不能把 Newman 标记为已通过。

### 3.9 Vue3 全角色前端与设计系统

前端目录 `frontend/queuemate-web` 已从占位骨架升级为可运行的 Vue3 + Vite 应用。

已实现页面：

```text
/login            登录
/register         普通用户注册
/venues           地点列表与筛选
/venues/{id}      地点详情、时段预约、公开排队进度与用户取号
/bookings         我的预约、消费码与取消预约
/wallet           钱包余额、模拟充值与余额流水
/queue            本人排队号码、日期筛选与状态
/merchant/application 普通用户提交入驻申请并查看审核结果
/manage/venues    商家/管理员地点管理
/manage/venues/{id} 时段、叫号、核销和繁忙统计运营工作台
/admin/merchant-applications 管理员审核商家入驻申请
/admin/wallets    管理员全局钱包流水与余额调整
/admin/bookings   管理员按预约 ID 取消预约
```

已实现：

- Element Plus 组件按需注册，Axios 统一封装。
- JWT 和用户信息使用本地会话保存，请求自动添加 Bearer Token。
- Vue Router 统一处理登录保护与 `USER/MERCHANT/ADMIN` 角色页面限制；同一账号可同时保留 `USER + MERCHANT` 身份并在顶部切换工作区。
- `401` 清理过期会话并带原目标返回登录页，业务错误显示后端消息。
- 地点和详情允许匿名浏览，用户写操作会要求登录并校验角色。
- 注册、预约确认、取消、充值、取号、时段开关、叫号、核销和余额调整均有明确校验、确认或反馈状态。
- 余额不足预约会提供“前往充值”按钮，充值成功后携带原目标返回地点预约页面。
- 地点分类由 `frontend/queuemate-web/src/constants/venue.js` 统一维护，申请、创建/编辑和公开筛选共用同一组选项。
- 钱包流水根据 `balanceBefore/balanceAfter` 判断收支方向，避免把支付金额误显示为入账。
- 64 位业务 ID 保持字符串传递，不在前端转为 JavaScript Number。
- 登录、加载、空数据、错误、禁用和移动端状态已建立共享样式。
- `design-system/MASTER.md` 已建立，固定颜色、字体、字号、间距、圆角、阴影、组件状态、响应式和无障碍规则。
- 视觉方向为“城市服务台”，使用服务蓝与票据式信息结构，不使用渐变、玻璃效果、过度圆角或装饰动画。

最后验证：

```text
Vite production build: SUCCESS
Browser QA: 15 个访客/用户/商家/管理员页面与尺寸组合
Horizontal overflow: 0
Browser runtime errors: 0
HTTP 4xx/5xx during QA: 0
```

浏览器 QA 使用当前运行中的真实 Spring Boot + MySQL 完成多角色登录与只读页面联调，覆盖 1440px、375px、横屏和 `prefers-reduced-motion`；未执行会污染数据的创建、充值、预约、核销和余额调整操作。临时检查脚本已清理。

完整全角色前端基线为 `aa295d3 feat: complete full-role Vue frontend`；后续商家入驻与分类扩展已继续提交并推送到远端 `main`。

### 3.10 商家入驻与多身份

已实现：

- 普通用户从顶部“商家入驻”进入三步申请，填写经营主体、拟入驻门店和地址资料。
- 管理员从“入驻审核”查看申请，可通过或填写原因驳回；被驳回后允许修改资料重新申请。
- 审核通过后给同一账号增加 `MERCHANT` 身份，不会移除原有 `USER` 身份。
- 申请人刷新或重新登录后，可在顶部切换顾客端和商家端。
- 审核通过只开通商家身份，不自动发布门店；商家进入“场所工作台”创建地点并设置预约、排队和默认价格。
- 管理员创建地点时可从已启用且拥有商家身份的账号列表中选择归属商家，不再手工输入用户 ID。
- 商家申请与管理员审核的真实浏览器流程已经验证，375px 页面横向溢出为 0。
- 19 位新用户 ID 的认证响应已统一字符串序列化，修复“地点已创建但我的场所为空”的归属筛选问题。

相关迁移：

```text
sql/migrations/20260720_merchant_onboarding.sql
```

近期关键提交：

```text
b353f5b feat: add merchant onboarding workflow
d409bb8 fix: preserve user id precision in auth responses
484d984 feat: expand venue categories
```

### 3.11 模拟账号

仅用于本地开发：

| 角色 | 用户名 | 密码 |
| --- | --- | --- |
| ADMIN | `admin` | `Admin123456` |
| MERCHANT | `merchant_tea`、`merchant_sport` | `Merchant123456` |
| USER | `alice`、`bob`、`carol` | `User123456` |

SQL 文件和本地数据库保存的是 BCrypt 哈希。以上明文仅为公开的本地模拟凭据，不能用于真实环境。

## 4. 当前卡点与已知边界

目前没有阻塞开发的故障，需求范围内的全部后端模块均可正常构建、启动和调用。

已知边界：

- 密码策略目前只有 8 到 64 字符的长度限制，不代表生产级密码安全。
- JWT 只有 Access Token，没有 Refresh Token、主动登出、黑名单和密钥轮换。
- 登录尚无限流、失败次数锁定、审计日志和验证码。
- Postman 尚未通过 Newman 执行。
- 管理员取消预约仍需手动输入预约 ID，因为后端没有全局预约列表接口。
- 已完成商家申请、管理员审核和商家创建地点的人工浏览器写流程；预约、充值、叫号、核销和余额调整仍需正式 Playwright 用例覆盖并自动清理测试数据。
- 地点列表首版返回完整列表，尚未分页；数据量扩大后需要升级为分页响应。
- 时段首版只禁止完全相同的时间范围，没有禁止重叠时段。
- 时段创建后暂不支持修改日期、时间、容量和价格，只支持打开或关闭。
- 我的预约首版返回完整列表，尚未分页。

## 5. 下一步计划

下一模块建议把当前完整前端转为可重复执行的自动化测试资产。

建议顺序：

1. 将多角色浏览器检查转为正式 Playwright 用例，并加入测试数据创建与清理。
2. 使用真实 Spring Boot + MySQL 回归注册、付费预约、取消退款、充值、用户取号、商家叫号与核销。
3. 接入 Postman/Newman 和 JMeter 正式执行结果。
4. 建立 GitHub Actions 后端、前端、接口和 UI 测试流水线。

```text
Playwright 多角色端到端写操作
  -> Newman/JMeter
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
- 浏览器能打开 GitHub 但 Git 报 `Could not resolve hostname github.com` 时，可能是浏览器安全 DNS 可用而 Windows 系统 DNS 缓存异常；本轮执行 `ipconfig /flushdns` 后恢复，随后 `git push origin main` 成功。

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

cd D:\QueueMate\frontend\queuemate-web
pnpm build
```

最后检查前后端健康状态。当前基线应看到后端 137 个测试全部通过、Vite 生产构建成功；认证、商家入驻、地点、时段、预约、钱包、消费码、排队和统计接口均可用，普通用户、商家和管理员前端页面均可访问，并且项目中不存在 `password-strength`、`PasswordPolicy` 或 `PASSWORD_WEAK`。
