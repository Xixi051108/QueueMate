# QueueMate 数据库设计

## 1. 设计原则

- 先满足 MVP 业务闭环，不做过度抽象
- 优先保证可测性、可读性和可扩展性
- 用最少核心表支撑认证、预约、排队和统计
- 并发关键点优先依赖数据库约束与原子更新

## 2. 核心实体关系

- 一个商家可以管理多个地点
- 一个地点只归属一个商家
- 一个地点可以配置多个预约时段
- 一个用户可以产生多个预约记录
- 一个用户拥有一个钱包账户
- 一个钱包账户可以产生多条钱包流水
- 一个地点每天可以生成多条排队号码

## 3. 表设计

### 3.1 `users`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 主键 |
| username | varchar(50) | 用户名，唯一 |
| password_hash | varchar(255) | 密码哈希 |
| role | varchar(20) | `USER` / `MERCHANT` / `ADMIN` |
| display_name | varchar(100) | 展示名称 |
| phone | varchar(20) | 模拟手机号，可为空 |
| status | varchar(20) | `ACTIVE` / `DISABLED` |
| created_at | datetime | 创建时间 |
| updated_at | datetime | 更新时间 |

约束与索引：

- 唯一索引：`uk_users_username (username)`
- 普通索引：`idx_users_role (role)`

### 3.2 `venues`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 主键 |
| name | varchar(100) | 地点名称 |
| category | varchar(30) | `TEA_SHOP` / `STUDY_ROOM` / `BADMINTON_COURT` |
| description | varchar(500) | 地点描述 |
| merchant_id | bigint | 所属商家用户 ID |
| address_text | varchar(255) | 模拟地址描述 |
| queue_enabled | tinyint | 是否支持取号 |
| booking_enabled | tinyint | 是否支持预约 |
| default_price | decimal(10,2) | 默认预约价格，`0` 表示免费 |
| status | varchar(20) | `ACTIVE` / `INACTIVE` |
| created_at | datetime | 创建时间 |
| updated_at | datetime | 更新时间 |

约束与索引：

- 外键逻辑关联：`merchant_id -> users.id`
- 可选唯一约束：`uk_venues_name_merchant (name, merchant_id)`
- 普通索引：`idx_venues_category (category)`
- 普通索引：`idx_venues_merchant_id (merchant_id)`

### 3.3 `booking_slots`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 主键 |
| venue_id | bigint | 地点 ID |
| slot_date | date | 预约日期 |
| start_time | time | 开始时间 |
| end_time | time | 结束时间 |
| capacity | int | 容量上限 |
| reserved_count | int | 当前已预约数 |
| price | decimal(10,2) | 时段价格，`0` 表示免费 |
| status | varchar(20) | `OPEN` / `CLOSED` |
| created_by | bigint | 创建人 |
| created_at | datetime | 创建时间 |
| updated_at | datetime | 更新时间 |

约束与索引：

- 唯一索引：`uk_booking_slots_unique (venue_id, slot_date, start_time, end_time)`
- 普通索引：`idx_booking_slots_venue_date (venue_id, slot_date)`

设计说明：

- `reserved_count` 用于加速余量查询
- 预约提交时依赖数据库原子更新控制容量

### 3.4 `bookings`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 主键 |
| booking_no | varchar(50) | 预约单号 |
| user_id | bigint | 用户 ID |
| venue_id | bigint | 地点 ID，冗余便于查询 |
| slot_id | bigint | 时段 ID |
| status | varchar(20) | `BOOKED` / `CANCELLED` / `FULFILLED` / `NO_SHOW` |
| pay_status | varchar(20) | `UNPAID` / `PAID` / `REFUNDED` / `FAILED` |
| paid_amount | decimal(10,2) | 实付金额 |
| cancel_reason | varchar(255) | 取消原因，可为空 |
| booked_at | datetime | 预约时间 |
| paid_at | datetime | 支付时间，可为空 |
| cancelled_at | datetime | 取消时间，可为空 |
| refunded_at | datetime | 退款时间，可为空 |
| updated_at | datetime | 更新时间 |

约束与索引：

- 唯一索引：`uk_bookings_user_slot (user_id, slot_id)`
- 唯一索引：`uk_bookings_no (booking_no)`
- 普通索引：`idx_bookings_venue_id (venue_id)`
- 普通索引：`idx_bookings_status (status)`

设计说明：

- `venue_id` 作为冗余字段，方便按地点维度统计和查询
- 重复预约通过唯一约束和业务校验双重控制
- 收费预约成功后 `pay_status` 应为 `PAID`

### 3.5 `wallets`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 主键 |
| user_id | bigint | 用户 ID |
| balance | decimal(10,2) | 当前余额 |
| status | varchar(20) | `ACTIVE` / `FROZEN` |
| created_at | datetime | 创建时间 |
| updated_at | datetime | 更新时间 |

约束与索引：

- 唯一索引：`uk_wallets_user_id (user_id)`

设计说明：

- 钱包只用于站内模拟支付
- 不保存任何真实银行卡、微信、支付宝信息

### 3.6 `wallet_transactions`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 主键 |
| transaction_no | varchar(50) | 交易流水号 |
| wallet_id | bigint | 钱包 ID |
| user_id | bigint | 用户 ID，冗余便于查询 |
| type | varchar(20) | `RECHARGE` / `PAYMENT` / `REFUND` / `ADJUSTMENT` |
| amount | decimal(10,2) | 交易金额，正数记录 |
| balance_before | decimal(10,2) | 变动前余额 |
| balance_after | decimal(10,2) | 变动后余额 |
| biz_type | varchar(30) | 业务类型，例如 `BOOKING` |
| biz_no | varchar(50) | 业务单号，例如预约单号 |
| status | varchar(20) | `SUCCESS` / `FAILED` |
| remark | varchar(255) | 备注 |
| created_at | datetime | 创建时间 |

约束与索引：

- 唯一索引：`uk_wallet_transactions_no (transaction_no)`
- 普通索引：`idx_wallet_transactions_user_id (user_id)`
- 普通索引：`idx_wallet_transactions_biz (biz_type, biz_no)`

设计说明：

- 充值、预约扣款、取消退款都必须写入流水
- 同一业务单号的扣款和退款需要具备幂等保护

### 3.7 `queue_tickets`

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 主键 |
| ticket_no | varchar(50) | 业务单号 |
| venue_id | bigint | 地点 ID |
| user_id | bigint | 用户 ID，可为空，支持游客模式扩展 |
| queue_date | date | 取号日期 |
| queue_no | int | 当日排队序号 |
| status | varchar(20) | `WAITING` / `CALLED` / `COMPLETED` / `MISSED` |
| taken_at | datetime | 取号时间 |
| called_at | datetime | 叫号时间 |
| completed_at | datetime | 完成时间 |
| missed_at | datetime | 过号时间 |
| updated_at | datetime | 更新时间 |

约束与索引：

- 唯一索引：`uk_queue_tickets_no (ticket_no)`
- 唯一索引：`uk_queue_tickets_daily_no (venue_id, queue_date, queue_no)`
- 普通索引：`idx_queue_tickets_venue_status (venue_id, status)`

## 4. 枚举约定

### 4.1 用户角色

- `USER`
- `MERCHANT`
- `ADMIN`

### 4.2 用户状态

- `ACTIVE`
- `DISABLED`

### 4.3 地点类别

- `TEA_SHOP`
- `STUDY_ROOM`
- `BADMINTON_COURT`

### 4.4 地点状态

- `ACTIVE`
- `INACTIVE`

### 4.5 时段状态

- `OPEN`
- `CLOSED`

### 4.6 预约状态

- `BOOKED`
- `CANCELLED`
- `FULFILLED`
- `NO_SHOW`

### 4.7 支付状态

- `UNPAID`
- `PAID`
- `REFUNDED`
- `FAILED`

### 4.8 钱包状态

- `ACTIVE`
- `FROZEN`

### 4.9 钱包流水类型

- `RECHARGE`
- `PAYMENT`
- `REFUND`
- `ADJUSTMENT`

### 4.10 钱包流水状态

- `SUCCESS`
- `FAILED`

### 4.11 排队状态

- `WAITING`
- `CALLED`
- `COMPLETED`
- `MISSED`

## 5. 并发控制设计

### 5.1 预约防超卖

核心思路：

- 先校验 `bookings(user_id, slot_id)` 是否已存在
- 对收费预约，先校验钱包余额是否足够
- 再执行带条件的时段容量原子更新，例如：

```sql
update booking_slots
set reserved_count = reserved_count + 1
where id = ?
  and status = 'OPEN'
  and reserved_count < capacity;
```

- 若受影响行数为 `0`，说明时段已满或不可预约
- 对收费预约，在同一事务内执行钱包余额扣减、写入钱包流水、写入 `bookings`
- 任一步失败时需要回滚事务，避免扣款成功但预约失败

### 5.2 模拟支付防重复扣款

核心思路：

- `bookings(user_id, slot_id)` 控制重复预约
- `wallet_transactions(biz_type, biz_no, type)` 可作为幂等设计参考
- 扣款时使用条件更新保证余额不为负，例如：

```sql
update wallets
set balance = balance - ?
where user_id = ?
  and status = 'ACTIVE'
  and balance >= ?;
```

- 若受影响行数为 `0`，说明余额不足或钱包不可用
- 写入交易流水时记录 `balance_before` 和 `balance_after`

### 5.3 取消预约回补名额和退款

- 仅 `BOOKED` 状态可取消
- 取消成功后事务内执行 `reserved_count = reserved_count - 1`
- 若预约已支付且未退款，则事务内执行余额退款并写入退款流水
- 防止重复取消导致计数异常
- 防止重复退款导致余额异常

## 6. 统计设计

首版不单独建立统计表，直接基于业务表聚合：

- 预约繁忙度：按 `booking_slots` 和 `bookings` 聚合
- 排队繁忙度：按 `queue_tickets` 的 `taken_at` 或 `queue_no` 聚合

示例统计维度：

- 指定地点
- 指定日期范围
- 指定小时段

## 7. 初始化数据建议

- 1 个管理员账号
- 2 个商家账号
- 3 到 5 个普通用户账号
- 每个普通用户准备一个钱包和初始模拟余额
- 6 到 10 个模拟地点
- 每个地点准备未来 3 到 7 天的预约时段
- 准备少量历史预约和排队记录，便于统计展示
- 准备少量充值、支付、退款流水，便于支付页面和测试展示

## 8. 后续扩展点

- 增加操作日志表
- 增加评价或反馈表
- 增加更复杂的商家营业时间模板
- 增加黑名单、爽约次数等信用字段
