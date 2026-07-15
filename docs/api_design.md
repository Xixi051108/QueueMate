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

说明：分页或列表查询地点。

查询参数建议：

- `category`
- `status`
- `keyword`

权限：公开接口。

### 3.2 `GET /venues/{id}`

说明：查看地点详情。

权限：公开接口。

### 3.3 `POST /venues`

说明：创建地点。

权限：`MERCHANT`、`ADMIN`。

请求体建议：

```json
{
  "name": "QueueMate 自习室 A",
  "category": "STUDY_ROOM",
  "description": "安静、自习卡座",
  "addressText": "模拟地址 1 号",
  "queueEnabled": false,
  "bookingEnabled": true,
  "defaultPrice": 10.00
}
```

### 3.4 `PUT /venues/{id}`

说明：更新地点基本信息。

权限：地点所属 `MERCHANT` 或 `ADMIN`。

### 3.5 `PATCH /venues/{id}/status`

说明：启用或停用地点。

权限：地点所属 `MERCHANT` 或 `ADMIN`。

请求体建议：

```json
{
  "status": "INACTIVE"
}
```

## 4. 时段预约接口

### 4.1 `GET /venues/{id}/slots`

说明：查看某地点的预约时段。

查询参数建议：

- `dateFrom`
- `dateTo`
- `status`

权限：公开接口。

### 4.2 `POST /venues/{id}/slots`

说明：创建预约时段。

权限：地点所属 `MERCHANT` 或 `ADMIN`。

请求体建议：

```json
{
  "slotDate": "2026-07-10",
  "startTime": "19:00:00",
  "endTime": "20:00:00",
  "capacity": 12,
  "price": 20.00
}
```

### 4.3 `POST /bookings`

说明：用户预约某个时段。

权限：`USER`。

请求体建议：

```json
{
  "slotId": 101
}
```

成功返回：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "bookingId": 5001,
    "bookingNo": "BK202607100001",
    "status": "BOOKED",
    "payStatus": "PAID",
    "paidAmount": 20.00
  }
}
```

失败场景：

- 时段不存在
- 时段已关闭
- 时段容量已满
- 重复预约
- 余额不足

### 4.4 `GET /bookings/my`

说明：查看当前用户的预约记录。

权限：`USER`。

查询参数建议：

- `status`
- `pageNum`
- `pageSize`

### 4.5 `PATCH /bookings/{id}/cancel`

说明：取消预约。

权限：预约所属 `USER`，或 `ADMIN`。

请求体建议：

```json
{
  "reason": "plan changed"
}
```

## 5. 钱包与模拟支付接口

### 5.1 `GET /wallets/my`

说明：查看当前用户的钱包余额。

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
    "ticketId": 9001,
    "ticketNo": "QT202607100001",
    "queueNo": 12,
    "status": "WAITING"
  }
}
```

### 6.2 `GET /venues/{id}/queue/tickets/current`

说明：查看当前排队进度和最新叫号。

权限：公开接口。

### 6.3 `PATCH /queue/tickets/{id}/call`

说明：叫号。

权限：地点所属 `MERCHANT` 或 `ADMIN`。

### 6.4 `PATCH /queue/tickets/{id}/complete`

说明：完成服务。

权限：地点所属 `MERCHANT` 或 `ADMIN`。

### 6.5 `PATCH /queue/tickets/{id}/miss`

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
| `POST /bookings` | 拒绝 | 允许 | 拒绝 | 视实现而定 |
| `GET /bookings/my` | 拒绝 | 允许 | 拒绝 | 可额外提供后台接口 |
| `PATCH /bookings/{id}/cancel` | 拒绝 | 仅本人 | 拒绝 | 允许 |
| `GET /wallets/my` | 拒绝 | 允许 | 可选 | 可选 |
| `POST /wallets/my/recharge` | 拒绝 | 允许 | 可选 | 拒绝 |
| `GET /wallets/my/transactions` | 拒绝 | 允许 | 可选 | 可选 |
| `GET /admin/wallet-transactions` | 拒绝 | 拒绝 | 拒绝 | 允许 |
| `POST /admin/wallets/{userId}/adjust` | 拒绝 | 拒绝 | 拒绝 | 允许 |
| `POST /venues/{id}/queue/tickets` | 拒绝 | 允许 | 允许 | 允许 |
| `GET /venues/{id}/queue/tickets/current` | 允许 | 允许 | 允许 | 允许 |
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
- `BOOKING_SLOT_NOT_FOUND`
- `BOOKING_SLOT_CLOSED`
- `BOOKING_SLOT_FULL`
- `BOOKING_DUPLICATE`
- `BOOKING_STATUS_INVALID`
- `WALLET_NOT_FOUND`
- `WALLET_FROZEN`
- `WALLET_BALANCE_NOT_ENOUGH`
- `PAYMENT_DUPLICATE`
- `PAYMENT_STATUS_INVALID`
- `REFUND_DUPLICATE`
- `QUEUE_TICKET_NOT_FOUND`
- `QUEUE_STATUS_INVALID`
- `RESOURCE_NOT_OWNED`

## 10. 接口测试重点

- 注册和登录成功、失败场景
- token 缺失、非法、过期场景
- 商家越权访问其他商家地点
- 高并发预约重复提交与容量上限控制
- 余额不足、重复扣款、取消退款、并发支付一致性
- 号码状态非法流转拦截
