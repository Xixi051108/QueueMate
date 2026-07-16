# QueueMate 学习记录 04：固定预约时段

> 当前阶段：固定预约时段的查询、创建和状态管理已实现，尚未进入用户预约与并发容量扣减

## 1. 本阶段完成目标

本阶段实现三个接口：

```http
GET   /api/v1/venues/{venueId}/slots
POST  /api/v1/venues/{venueId}/slots
PATCH /api/v1/venues/{venueId}/slots/{slotId}/status
```

公开用户可以查询时段。地点所属商家和管理员可以创建、打开或关闭时段。

## 2. 为什么先做固定时段，再做预约

预约防超卖依赖一个稳定的容量载体。`booking_slots` 先定义：

- 哪一天可以预约。
- 开始和结束时间。
- 总容量 `capacity`。
- 已预约数量 `reservedCount`。
- 免费或收费价格。
- 当前是否开放。

下一阶段提交预约时，只需围绕某个确定的 `slotId` 做原子容量更新，而不是临时计算商家营业时间。

## 3. 查询规则

时段查询允许匿名访问，支持：

- `dateFrom`：起始日期，包含边界。
- `dateTo`：结束日期，包含边界。
- `status`：`OPEN` 或 `CLOSED`。

`dateFrom` 晚于 `dateTo` 时返回 `400 / PARAM_INVALID`。结果按日期、开始时间和时段 ID 排序，便于页面展示和接口断言。

响应额外计算：

```text
availableCapacity = capacity - reservedCount
```

这个字段只是当前余量展示。真正预约时仍必须使用数据库条件更新，不能先读余量再普通写入。

## 4. 创建规则

创建时段前依次校验：

```text
地点存在
  -> 当前用户是地点所属商家或管理员
  -> 地点状态为 ACTIVE
  -> bookingEnabled = true
  -> 日期不早于今天
  -> 开始时间早于结束时间
  -> 容量和价格合法
  -> 精确重复时段不存在
  -> 插入 OPEN / reservedCount=0 时段
```

商家资源归属复用 `VenueService.requireOwnerOrAdmin`，避免预约、排队等后续模块各写一套不同规则。

## 5. 重复时段保护

数据库唯一索引为：

```text
uk_booking_slots_unique (venue_id, slot_date, start_time, end_time)
```

Service 先查询，用于返回友好错误；插入时继续捕获唯一键冲突，处理两个并发请求同时通过查询的竞态。两种路径都统一返回：

```text
409 / BOOKING_SLOT_EXISTS
```

首版只禁止完全相同的时间范围，不额外禁止相互重叠的时段。这与当前数据库约束一致，避免在场地可能拥有多个并行资源时误判。

## 6. 时段状态

- 新建时段固定为 `OPEN`。
- `OPEN` 可以切换为 `CLOSED`，阻止后续新预约。
- 关闭时不清空或减少已有 `reservedCount`。
- `CLOSED` 可以重新打开，但地点仍必须启用且支持预约。
- 路径中的时段不属于指定地点时，返回 `404 / BOOKING_SLOT_NOT_FOUND`，不暴露其他地点的时段细节。

## 7. ID 精度

MyBatis-Plus 默认生成的雪花 ID 可能超出 JavaScript 安全整数范围。因此时段响应中的以下字段序列化为字符串：

- `id`
- `venueId`
- `createdBy`

日期和时间继续使用 ISO 字符串，价格使用两位小数语义。

## 8. 已完成测试

`BookingSlotServiceTest` 共 13 个测试，覆盖：

- 公开查询和可用余量。
- 反向日期范围。
- 正常创建。
- 停用地点和预约开关。
- 非法时间和过去日期。
- 业务查询重复与数据库并发重复。
- 关闭和重新打开。
- 时段与地点不匹配。
- 跨商家资源归属失败。

`BookingSlotControllerSecurityTest` 共 7 个测试，覆盖公开查询、非法状态参数、匿名 401、USER 403、商家创建、参数校验和关闭时段。

加上认证与地点模块，当前 Maven 全量共 47 个测试。

真实 HTTP + MySQL 回归覆盖了公开查询、反向日期范围、USER 禁止创建、商家创建、重复时段、跨店越权、管理员关闭、商家重开、路径地点不匹配、非法时间和预约开关。回归产生的临时时段已按精确 ID 删除，并确认残留数量为 0。

## 9. 当前边界

本阶段没有实现：

- 用户提交预约。
- `reservedCount` 增减。
- 并发防超卖。
- 重复预约限制。
- 预约扣款或退款。
- 已有时段的容量、时间和价格编辑。

这些能力将在后续预约事务中实现，避免把时段配置与用户预约状态混在同一轮。

## 10. 下一阶段建议

下一阶段实现用户预约最小闭环：

```text
POST /bookings
  -> USER 身份限制
  -> 校验地点和时段可用
  -> 原子 reservedCount + 1
  -> 唯一约束阻止重复预约
  -> 创建免费预约记录
  -> 并发测试验证不超卖
```

收费预约和钱包扣款建议紧接其后，并在同一事务内扩展。
