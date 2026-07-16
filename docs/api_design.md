# QueueMate API 设计

## 1. 基本约定

- Base URL：`/api/v1`
- 数据格式：`application/json`
- 鉴权方式：`Authorization: Bearer <jwt>`
- 字符集：UTF-8

统一响应结构建议：

```json
{
  "code": "0",
  "message": "success",
  "data": {}
}
```

统一错误结构建议：

```json
{
  "code": "BOOKING_SLOT_FULL",
  "message": "当前时段已约满",
  "data": null
}
```

## 2. 认证接口

### 2.1 `POST /auth/register`

说明：注册普通用户账号。

成功状态码：`201 Created`。

请求体：

```json
{
  "username": "alice",
  "password": "Password123",
  "displayName": "Alice",
  "phone": "13800000001"
}
```

参数规则：

- `username`：3 到 50 个字符
- `password`：8 到 64 个字符
- `displayName`：必填，最多 100 个字符
- `phone`：可选；填写时必须为 11 位手机号格式

失败场景：

- 参数不合法：`400 / PARAM_INVALID`
- 用户名已存在：`409 / USERNAME_EXISTS`

响应体：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "userId": 1,
    "username": "alice",
    "role": "USER"
  }
}
```

### 2.2 `POST /auth/login`

说明：账号密码登录，返回 JWT。

请求体：

```json
{
  "username": "alice",
  "password": "Password123"
}
```

失败场景：

- 用户名或密码错误：`401 / AUTH_CREDENTIALS_INVALID`
- 用户已禁用：`403 / USER_DISABLED`

响应体：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "token": "jwt-token",
    "tokenType": "Bearer",
    "expiresIn": 7200,
    "user": {
      "id": 1,
      "username": "alice",
      "role": "USER"
    }
  }
}
```

### 2.3 `GET /auth/me`

说明：获取当前登录用户信息。

权限：已登录用户。

token 缺失、伪造、过期或对应用户不可用时返回：`401 / AUTH_UNAUTHORIZED`。

## 3. 地点接口

### 3.1 `GET /venues`

说明：首版使用列表方式查询地点，结果按地点 ID 升序返回。

查询参数：

- `category`：可选，`TEA_SHOP` / `STUDY_ROOM` / `BADMINTON_COURT`
- `status`：可选，`ACTIVE` / `INACTIVE`
- `keyword`：可选，同时模糊匹配名称、描述和模拟地址

权限：公开接口。

### 3.2 `GET /venues/{id}`

说明：查看地点详情。

权限：公开接口。

地点不存在时返回：`404 / VENUE_NOT_FOUND`。

### 3.3 `POST /venues`

说明：创建地点。

权限：`MERCHANT`、`ADMIN`。

请求体：

```json
{
  "name": "QueueMate 自习室 A",
  "category": "STUDY_ROOM",
  "description": "安静、自习卡座",
  "addressText": "模拟地址 1 号",
  "queueEnabled": false,
  "bookingEnabled": true,
  "defaultPrice": 10.00,
  "merchantId": 2001
}
```

规则：

- `MERCHANT` 创建时，归属商家固定为当前登录用户，客户端传入的 `merchantId` 不生效。
- `ADMIN` 创建时必须传入 `merchantId`，目标账号必须是可用的 `MERCHANT`。
- 新建地点状态固定为 `ACTIVE`。
- 同一商家下地点名称不可重复。
- `defaultPrice` 不得小于 `0`，最多保留两位小数。
- 响应中的 `id` 和 `merchantId` 使用字符串表示，避免 JavaScript 丢失 64 位整数精度；路径和请求中的 ID 仍传十进制数字文本。

失败场景：

- 参数不合法：`400 / PARAM_INVALID`
- 管理员未指定有效商家：`400 / MERCHANT_INVALID`
- 同一商家已有同名地点：`409 / VENUE_NAME_EXISTS`
- 角色无权创建：`403 / AUTH_FORBIDDEN`

### 3.4 `PUT /venues/{id}`

说明：更新地点基本信息。

权限：地点所属 `MERCHANT` 或 `ADMIN`。

可修改名称、类别、描述、模拟地址、排队开关、预约开关和默认价格；不可通过该接口修改归属商家或状态。

失败场景：

- 地点不存在：`404 / VENUE_NOT_FOUND`
- 商家操作其他商家的地点：`403 / RESOURCE_NOT_OWNED`
- 修改后名称与同一商家的其他地点重复：`409 / VENUE_NAME_EXISTS`

### 3.5 `PATCH /venues/{id}/status`

说明：启用或停用地点。

权限：地点所属 `MERCHANT` 或 `ADMIN`。

请求体建议：

```json
{
  "status": "INACTIVE"
}
```

状态仅允许 `ACTIVE` 或 `INACTIVE`。地点不存在返回 `404 / VENUE_NOT_FOUND`，商家越权返回 `403 / RESOURCE_NOT_OWNED`。

## 4. 时段预约接口

### 4.1 `GET /venues/{id}/slots`

说明：查看某地点的预约时段。

查询参数：

- `dateFrom`：可选，ISO 日期，筛选该日期及之后的时段
- `dateTo`：可选，ISO 日期，筛选该日期及之前的时段
- `status`：可选，`OPEN` / `CLOSED`

权限：公开接口。

规则：

- `dateFrom` 不得晚于 `dateTo`。
- 地点不存在返回 `404 / VENUE_NOT_FOUND`。
- 结果按日期、开始时间、时段 ID 升序返回。
- 响应提供 `reservedCount` 和派生字段 `availableCapacity`。
- `id`、`venueId`、`createdBy` 使用字符串表示，避免 JavaScript 丢失 64 位整数精度。

### 4.2 `POST /venues/{id}/slots`

说明：创建预约时段。

权限：地点所属 `MERCHANT` 或 `ADMIN`。

请求体：

```json
{
  "slotDate": "2026-07-10",
  "startTime": "19:00:00",
  "endTime": "20:00:00",
  "capacity": 12,
  "price": 20.00
}
```

规则：

- 权限为地点所属 `MERCHANT` 或 `ADMIN`。
- 地点必须为 `ACTIVE` 且 `bookingEnabled=true`。
- `slotDate` 不得早于今天。
- `capacity` 必须大于 `0`。
- `startTime` 必须早于 `endTime`。
- `price` 不得小于 `0`，最多保留两位小数。
- 新建时段的 `reservedCount` 固定为 `0`，状态固定为 `OPEN`。
- 同一地点、日期、开始时间和结束时间组合不可重复。

失败场景：

- 参数不合法：`400 / PARAM_INVALID`
- 时间范围不合法：`400 / BOOKING_SLOT_TIME_INVALID`
- 地点已停用：`409 / VENUE_INACTIVE`
- 地点未启用预约：`409 / VENUE_BOOKING_DISABLED`
- 重复时段：`409 / BOOKING_SLOT_EXISTS`
- 商家操作其他商家的地点：`403 / RESOURCE_NOT_OWNED`

### 4.3 `PATCH /venues/{venueId}/slots/{slotId}/status`

说明：打开或关闭预约时段。

权限：地点所属 `MERCHANT` 或 `ADMIN`。

请求体：

```json
{
  "status": "CLOSED"
}
```

规则：

- 状态仅允许 `OPEN` 或 `CLOSED`。
- 关闭时段不会修改已有预约数。
- 重新打开时，地点仍必须为 `ACTIVE` 且启用预约。
- 路径中的时段必须属于对应地点，否则统一返回 `404 / BOOKING_SLOT_NOT_FOUND`。

### 4.4 `POST /bookings`

说明：普通用户预约某个免费或收费时段。

权限：`USER`。

成功状态码：`201 Created`。

请求体：

```json
{
  "slotId": 101
}
```

首版规则：

- `slotId` 必须是正整数。
- 地点必须为 `ACTIVE` 且 `bookingEnabled=true`。
- 时段必须为 `OPEN`，且开始时间尚未到达。
- 价格为 `0` 的时段直接创建免费预约；收费时段价格表示预付消费金额，不是额外预约手续费。
- 收费预约在同一事务内完成名额占用、钱包扣款、支付流水、预约记录和消费凭证创建。
- 容量通过数据库条件更新原子增加，容量增加和预约记录插入处于同一事务。
- 同一用户与时段只能存在一条预约记录，业务校验与数据库唯一键共同防重。

成功返回：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "id": "5001",
    "bookingNo": "BK8F28C3D893C34D13B77C7E73D75D6C09",
    "userId": "3001",
    "venueId": "4002",
    "slotId": "101",
    "status": "BOOKED",
    "payStatus": "NOT_REQUIRED",
    "paidAmount": 0.00,
    "cancelReason": null,
    "bookedAt": "2026-07-16T15:30:00",
    "cancelledAt": null
  }
}
```

收费预约响应会增加消费凭证：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "id": "5002",
    "bookingNo": "BK7E4F27C981D5488AB33D9C9B608E40B2",
    "status": "BOOKED",
    "payStatus": "PAID",
    "paidAmount": 20.00,
    "voucher": {
      "status": "AVAILABLE",
      "consumptionCode": "QM7K9P2X5A8",
      "amount": 20.00,
      "validFrom": "2026-07-20T18:30:00",
      "validUntil": "2026-07-20T20:00:00"
    }
  }
}
```

失败场景：

- 时段不存在：`404 / BOOKING_SLOT_NOT_FOUND`
- 地点已停用：`409 / VENUE_INACTIVE`
- 地点未启用预约：`409 / VENUE_BOOKING_DISABLED`
- 时段已关闭：`409 / BOOKING_SLOT_CLOSED`
- 时段已开始或已过期：`409 / BOOKING_SLOT_EXPIRED`
- 时段容量已满：`409 / BOOKING_SLOT_FULL`
- 重复预约：`409 / BOOKING_DUPLICATE`
- 钱包不存在或冻结：`404 / WALLET_NOT_FOUND`、`409 / WALLET_FROZEN`
- 钱包余额不足：`409 / WALLET_BALANCE_NOT_ENOUGH`
- 重复扣款：`409 / PAYMENT_DUPLICATE`

### 4.5 `GET /bookings/my`

说明：查看当前用户的预约记录。

权限：`USER`。

查询参数：

- `status`：可选，`BOOKED` / `CANCELLED` / `FULFILLED` / `NO_SHOW`

首版返回列表，不分页；结果按预约时间和预约 ID 倒序排列，只返回当前登录用户的数据。响应中的 `id`、`userId`、`venueId`、`slotId` 均使用字符串。

预约所属用户可在响应中看到完整消费码、预付金额和凭证状态。商家预约列表不得批量返回完整消费码，核销时由用户出示并由商家提交。

### 4.6 `PATCH /bookings/{id}/cancel`

说明：取消预约。

权限：预约所属 `USER`，或 `ADMIN`。

请求体：

```json
{
  "reason": "plan changed"
}
```

规则：

- `reason` 可选，去除首尾空白后最多 255 个字符。
- 只有 `BOOKED` 状态可以取消。
- 普通用户只能取消自己的预约；管理员可以取消任意预约。
- 时段开始前可取消凭证仍为 `AVAILABLE` 的收费预约并全额退款；退款成功时消费凭证同步变为 `VOID`。
- 时段开始后首版不允许用户自助取消收费预约。
- `FULFILLED`、`NO_SHOW` 或消费凭证已经 `REDEEMED` 的预约不可取消。
- 预约状态条件更新与时段名额回补处于同一事务，并发重复取消只允许一次成功。

失败场景：

- 预约不存在：`404 / BOOKING_NOT_FOUND`
- 普通用户取消他人预约：`403 / RESOURCE_NOT_OWNED`
- 当前状态不可取消：`409 / BOOKING_STATUS_INVALID`
- 收费预约退款窗口已关闭：`409 / BOOKING_REFUND_WINDOW_CLOSED`
- 消费凭证已核销、作废或过期：`409 / CONSUMPTION_CODE_STATUS_INVALID`
- 重复退款：`409 / REFUND_DUPLICATE`

### 4.7 `POST /venues/{venueId}/booking-vouchers/redeem`

说明：商家使用用户出示的消费码核销已支付预约。

权限：地点所属 `MERCHANT` 或 `ADMIN`。

请求体：

```json
{
  "consumptionCode": "QM7K9P2X5A8"
}
```

核销规则：

- 消费码必须存在并属于路径地点。
- 预约状态必须为 `BOOKED`，支付状态必须为 `PAID`。
- 消费凭证状态必须为 `AVAILABLE`。
- 首版默认核销窗口为预约开始前 30 分钟至时段结束时间。
- 核销成功时，在同一事务内把凭证改为 `REDEEMED`、预约改为 `FULFILLED`，并记录核销人和核销时间。
- 重复请求不会重复核销；第二次返回明确的状态冲突。
- 日志中只允许记录消费码后四位，不记录完整消费码。

成功响应：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "bookingId": "5002",
    "bookingNo": "BK7E4F27C981D5488AB33D9C9B608E40B2",
    "status": "FULFILLED",
    "payStatus": "PAID",
    "paidAmount": 20.00,
    "voucherStatus": "REDEEMED",
    "redeemedBy": "2001",
    "redeemedAt": "2026-07-20T18:45:00"
  }
}
```

失败场景：

- 消费码不存在：`404 / CONSUMPTION_CODE_NOT_FOUND`
- 商家跨店核销：`403 / RESOURCE_NOT_OWNED`
- 消费码已核销、作废或过期：`409 / CONSUMPTION_CODE_STATUS_INVALID`
- 不在核销时间窗口：`409 / CONSUMPTION_CODE_OUT_OF_WINDOW`
- 预约或支付状态不允许核销：`409 / BOOKING_STATUS_INVALID`

## 5. 钱包、预付消费与退款接口

### 5.1 `GET /wallets/my`

说明：查看当前用户的钱包余额。钱包余额用于预付收费时段对应的消费金额。

权限：`USER`。

响应体建议：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "walletId": 1001,
    "balance": 88.00,
    "status": "ACTIVE"
  }
}
```

### 5.2 `POST /wallets/my/recharge`

说明：模拟充值，只增加站内余额，不接入真实支付渠道。

权限：`USER`。

请求体建议：

```json
{
  "amount": 100.00,
  "remark": "mock recharge"
}
```

### 5.3 `GET /wallets/my/transactions`

说明：查看当前用户的钱包流水。

权限：`USER`。

查询参数建议：

- `type`
- `pageNum`
- `pageSize`

### 5.4 `GET /admin/wallet-transactions`

说明：管理员查看钱包流水。

权限：`ADMIN`。

可选查询参数：

- `userId`
- `type`

### 5.5 `POST /admin/wallets/{userId}/adjust`

说明：管理员进行模拟余额调整，用于测试数据维护。

权限：`ADMIN`。

请求体建议：

```json
{
  "amount": 50.00,
  "remark": "test data adjustment"
}
```

`amount` 为正数时增加余额，为负数时扣减余额；金额不能为 `0`，扣减后余额不得小于 `0`。每次调整都写入 `ADJUSTMENT` 流水。

## 6. 现场排队接口

### 6.1 `POST /venues/{id}/queue/tickets`

说明：为地点取号。

权限：已登录用户。

成功返回建议：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "id": "9001",
    "ticketNo": "QT20260710A1B2C3D4E5F6",
    "queueNo": 12,
    "queueDate": "2026-07-10",
    "status": "WAITING"
  }
}
```

### 6.2 `GET /venues/{id}/queue/tickets/current`

说明：查看当前排队进度和最新叫号。

权限：公开接口。

可选查询参数：`queueDate`，默认当天。

响应包含最新叫号、下一等待号、等待/已叫数量和当前 `WAITING/CALLED` 号码列表。

### 6.3 `GET /queue/tickets/my`

说明：查看当前登录账号取得的号码。

权限：已登录用户。

可选查询参数：

- `venueId`
- `status`
- `queueDate`

### 6.4 `PATCH /queue/tickets/{id}/call`

说明：叫号。

权限：地点所属 `MERCHANT` 或 `ADMIN`。

### 6.5 `PATCH /queue/tickets/{id}/complete`

说明：完成服务。

权限：地点所属 `MERCHANT` 或 `ADMIN`。

### 6.6 `PATCH /queue/tickets/{id}/miss`

说明：过号。

权限：地点所属 `MERCHANT` 或 `ADMIN`。

状态约束：

- `WAITING` 可流转到 `CALLED`
- `CALLED` 可流转到 `COMPLETED`
- `CALLED` 可流转到 `MISSED`
- 其他流转均返回业务错误

## 7. 统计接口

### 7.1 `GET /stats/venues/{id}/busy-hours`

说明：返回指定地点在某时间范围内的繁忙时段数据。

权限：地点所属 `MERCHANT` 或 `ADMIN`。

查询参数建议：

- `dateFrom`
- `dateTo`

日期范围必须正序且最多包含 366 天。预约数量按预约时段开始小时统计，排队数量按取号小时统计，`heatScore = bookingCount + queueCount`。

响应体建议：

```json
{
  "code": "0",
  "message": "success",
  "data": [
    {
      "hour": "10:00",
      "bookingCount": 14,
      "queueCount": 9,
      "heatScore": 23
    }
  ]
}
```

## 8. 权限矩阵

| 接口 | 匿名 | USER | MERCHANT | ADMIN |
| --- | --- | --- | --- | --- |
| `POST /auth/register` | 允许 | 允许 | 允许 | 允许 |
| `POST /auth/login` | 允许 | 允许 | 允许 | 允许 |
| `GET /auth/me` | 拒绝 | 允许 | 允许 | 允许 |
| `GET /venues` | 允许 | 允许 | 允许 | 允许 |
| `GET /venues/{id}` | 允许 | 允许 | 允许 | 允许 |
| `POST /venues` | 拒绝 | 拒绝 | 允许 | 允许 |
| `PUT /venues/{id}` | 拒绝 | 拒绝 | 仅自己 | 允许 |
| `PATCH /venues/{id}/status` | 拒绝 | 拒绝 | 仅自己 | 允许 |
| `GET /venues/{id}/slots` | 允许 | 允许 | 允许 | 允许 |
| `POST /venues/{id}/slots` | 拒绝 | 拒绝 | 仅自己 | 允许 |
| `PATCH /venues/{venueId}/slots/{slotId}/status` | 拒绝 | 拒绝 | 仅自己 | 允许 |
| `POST /bookings` | 拒绝 | 允许 | 拒绝 | 拒绝 |
| `GET /bookings/my` | 拒绝 | 允许 | 拒绝 | 可额外提供后台接口 |
| `PATCH /bookings/{id}/cancel` | 拒绝 | 仅本人 | 拒绝 | 允许 |
| `POST /venues/{venueId}/booking-vouchers/redeem` | 拒绝 | 拒绝 | 仅自己 | 允许 |
| `GET /wallets/my` | 拒绝 | 允许 | 拒绝 | 拒绝 |
| `POST /wallets/my/recharge` | 拒绝 | 允许 | 拒绝 | 拒绝 |
| `GET /wallets/my/transactions` | 拒绝 | 允许 | 拒绝 | 拒绝 |
| `GET /admin/wallet-transactions` | 拒绝 | 拒绝 | 拒绝 | 允许 |
| `POST /admin/wallets/{userId}/adjust` | 拒绝 | 拒绝 | 拒绝 | 允许 |
| `POST /venues/{id}/queue/tickets` | 拒绝 | 允许 | 允许 | 允许 |
| `GET /venues/{id}/queue/tickets/current` | 允许 | 允许 | 允许 | 允许 |
| `GET /queue/tickets/my` | 拒绝 | 允许 | 允许 | 允许 |
| `PATCH /queue/tickets/{id}/call` | 拒绝 | 拒绝 | 仅自己 | 允许 |
| `PATCH /queue/tickets/{id}/complete` | 拒绝 | 拒绝 | 仅自己 | 允许 |
| `PATCH /queue/tickets/{id}/miss` | 拒绝 | 拒绝 | 仅自己 | 允许 |
| `GET /stats/venues/{id}/busy-hours` | 拒绝 | 拒绝 | 仅自己 | 允许 |

## 9. 建议错误码

- `AUTH_UNAUTHORIZED`
- `AUTH_FORBIDDEN`
- `AUTH_CREDENTIALS_INVALID`
- `USERNAME_EXISTS`
- `USER_DISABLED`
- `VENUE_NOT_FOUND`
- `VENUE_INACTIVE`
- `VENUE_BOOKING_DISABLED`
- `VENUE_NAME_EXISTS`
- `MERCHANT_INVALID`
- `BOOKING_SLOT_NOT_FOUND`
- `BOOKING_SLOT_CLOSED`
- `BOOKING_SLOT_EXPIRED`
- `BOOKING_SLOT_FULL`
- `BOOKING_SLOT_EXISTS`
- `BOOKING_SLOT_TIME_INVALID`
- `BOOKING_NOT_FOUND`
- `BOOKING_DUPLICATE`
- `BOOKING_STATUS_INVALID`
- `BOOKING_REFUND_WINDOW_CLOSED`
- `CONSUMPTION_CODE_DUPLICATE`
- `CONSUMPTION_CODE_NOT_FOUND`
- `CONSUMPTION_CODE_STATUS_INVALID`
- `CONSUMPTION_CODE_OUT_OF_WINDOW`
- `WALLET_NOT_FOUND`
- `WALLET_FROZEN`
- `WALLET_BALANCE_NOT_ENOUGH`
- `PAYMENT_DUPLICATE`
- `PAYMENT_STATUS_INVALID`
- `REFUND_DUPLICATE`
- `WALLET_ADJUSTMENT_INVALID`
- `QUEUE_TICKET_NOT_FOUND`
- `QUEUE_TICKET_DUPLICATE`
- `QUEUE_VENUE_UNAVAILABLE`
- `QUEUE_STATUS_INVALID`
- `STATS_DATE_RANGE_INVALID`
- `RESOURCE_NOT_OWNED`
- `RESOURCE_NOT_FOUND`
- `METHOD_NOT_ALLOWED`

## 10. 接口测试重点

- 注册和登录成功、失败场景
- token 缺失、非法、过期场景
- 商家越权访问其他商家地点
- 高并发预约重复提交与容量上限控制
- 余额不足、重复扣款、取消退款、并发支付一致性
- 消费码生成、跨店核销、重复核销、核销时间窗口和退款作废
- 号码状态非法流转拦截
