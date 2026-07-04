# QueueMate 测试计划

## 1. 测试目标

QueueMate 的测试目标不是验证简单页面展示，而是围绕业务规则、权限控制、状态流转和并发一致性来体现测开价值。

重点验证：

- JWT 鉴权是否可靠
- RBAC 权限边界是否清晰
- 预约是否存在超卖、重复预约、一致性异常
- 排队状态流转是否符合预期
- Web UI 主流程是否可自动化回归
- CI 是否能承接基础质量门禁

## 2. 测试范围

### 2.1 范围内

- 后端接口功能测试
- 角色权限测试
- 状态流转测试
- 并发预约测试
- Web UI 自动化测试
- 基础 CI 集成

### 2.2 范围外

- 第三方地图联调测试
- 短信验证码测试
- 微信登录测试
- 支付链路测试
- 大规模分布式压测

## 3. 测试分层策略

### 3.1 后端单元与集成测试

目标：

- 验证 service 层业务规则
- 验证 repository/mapper 层数据库交互
- 验证事务、唯一约束、状态校验

重点模块：

- AuthService
- VenueService
- BookingService
- QueueService
- StatsService

### 3.2 Postman 接口测试

目标：

- 验证接口契约和关键场景
- 支撑快速回归和环境冒烟

建议内容：

- 环境变量管理
- 登录后 token 传递
- 正常流程与异常流程断言
- Newman 预留，后续接入 CI

### 3.3 JMeter 性能测试

目标：

- 验证预约并发场景下是否超卖
- 观察接口响应时间和错误率

首版重点：

- 同一时段高并发预约
- 预约成功数不超过容量
- 失败响应码清晰

### 3.4 Playwright Web UI 自动化

目标：

- 覆盖用户端和商家端的最短主流程
- 支撑页面交互回归

首版重点：

- 用户注册/登录
- 浏览地点与预约
- 查看我的预约并取消
- 商家登录并处理排队号码

### 3.5 GitHub Actions CI

目标：

- 自动执行构建和基础测试
- 为后续自动化测试接入预留位置

首版规划：

- 后端构建与测试
- 前端构建
- Postman/Newman 预留
- Playwright 预留
- JMeter 手动触发或独立 workflow 预留

## 4. 测试环境与数据

### 4.1 测试环境

- 本地开发环境
- 后续可扩展为 GitHub Actions CI 环境

### 4.2 测试数据建议

- 管理员账号：1 个
- 商家账号：2 个
- 普通用户账号：3 到 5 个
- 模拟地点：奶茶店、自习室、羽毛球场
- 每个地点配置若干未来时段
- 部分地点支持现场取号

## 5. 核心测试主题

## 5.1 鉴权测试

- 未登录访问受保护接口，应返回未授权
- 携带伪造 token，应返回未授权
- 使用已禁用账号登录，应返回失败
- 使用错误密码登录，应返回失败

## 5.2 权限测试

- `USER` 不可创建地点
- `USER` 不可叫号
- `MERCHANT` 不可操作其他商家的地点
- `MERCHANT` 不可查看其他商家受限统计
- `ADMIN` 可全局操作

## 5.3 预约测试

- 用户成功预约开放且未满的时段
- 同一用户重复预约同一时段被拒绝
- 已满时段预约失败
- 已关闭时段预约失败
- 取消预约成功后名额回补
- 重复取消同一预约被拒绝

## 5.4 排队状态流转测试

- 用户成功取号
- 商家可对 `WAITING` 执行叫号
- 商家可对 `CALLED` 执行完成
- 商家可对 `CALLED` 执行过号
- `WAITING -> COMPLETED` 必须失败
- `COMPLETED -> CALLED` 必须失败

## 5.5 并发一致性测试

- 给某时段设置容量 `N`
- 以远大于 `N` 的并发数提交预约
- 最终成功预约数不得超过 `N`
- `booking_slots.reserved_count` 必须与有效预约数一致
- 不允许出现重复预约脏数据

## 5.6 统计测试

- 指定地点统计接口返回小时维度数据
- 统计结果字段完整
- 无数据时返回空列表而不是异常

## 5.7 UI 自动化测试

- 用户登录后可浏览地点列表
- 用户可进入地点详情并提交预约
- 用户可在我的预约中取消预约
- 商家登录后可查看本店排队并执行叫号
- 页面应对异常提示有可断言反馈

## 6. 典型测试用例清单

| 编号 | 类型 | 场景 | 预期 |
| --- | --- | --- | --- |
| API-001 | 鉴权 | 未登录访问 `/auth/me` | 返回 401/未授权 |
| API-002 | 权限 | `USER` 调用 `POST /venues` | 返回 403/禁止访问 |
| API-003 | 预约 | 成功预约空闲时段 | 返回成功，状态为 `BOOKED` |
| API-004 | 预约 | 重复预约同一时段 | 返回 `BOOKING_DUPLICATE` |
| API-005 | 预约 | 超容量预约 | 返回 `BOOKING_SLOT_FULL` |
| API-006 | 状态流转 | 对 `WAITING` 执行 `complete` | 返回 `QUEUE_STATUS_INVALID` |
| PERF-001 | 并发 | 100 并发抢 20 个名额 | 成功数 <= 20 |
| UI-001 | UI | 用户登录并预约 | 页面成功提示，列表可见新预约 |
| UI-002 | UI | 商家叫号 | 页面显示号码状态变化 |

## 7. 自动化资产规划

### 7.1 Postman

- `tests/postman/QueueMate.postman_collection.json`
- `tests/postman/QueueMate.local.postman_environment.json`

### 7.2 JMeter

- `tests/jmeter/booking-concurrency.jmx`
- `tests/jmeter/queue-basic-flow.jmx`

### 7.3 Playwright

- `tests/playwright/specs/auth.spec.ts`
- `tests/playwright/specs/booking.spec.ts`
- `tests/playwright/specs/merchant-queue.spec.ts`

## 8. CI 规划

建议后续设置以下工作流：

- `backend-ci.yml`：Java 构建、单元测试、集成测试
- `frontend-ci.yml`：前端安装、构建
- `api-tests.yml`：Newman 执行 Postman 集合
- `ui-tests.yml`：Playwright 自动化测试

JMeter 建议：

- 先作为手动测试资产维护
- 后续再评估是否作为手动触发 workflow 接入

## 9. 通过标准

- 核心接口 happy path 可用
- 权限边界用例通过
- 状态流转非法路径可被拦截
- 并发预约不存在超卖
- UI 主流程可稳定执行
- CI 能自动完成基础校验
