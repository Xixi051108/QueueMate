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
- 一个收费预约成功后对应一张消费凭证，免费预约不生成消费凭证
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
| default_price | decimal(10,2) | 默认预付消费金额，`0` 表示免费 |
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
| price | decimal(10,2) | 时段预付消费金额，`0` 表示免费 |
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
| pay_status | varchar(20) | `NOT_REQUIRED` / `UNPAID` / `PAID` / `REFUNDED` / `FAILED` |
| paid_amount | decimal(10,2) | 实付金额 |
| cancel_reason | varchar(255) | 取消原因，可为空 |
| booked_at | datetime | 预约时间 |
| paid_at | datetime | 支付时间，可为空 |
| cancelled_at | datetime | 取消时间，可为空 |
| refunded_at | datetime | 退款时间，可为空 |
| updated_at | datetime | 更新时间 |

约束与索引：

- 生成列：`active_slot_id = case when status = 'BOOKED' then slot_id else null end`
- 唯一索引：`uk_bookings_user_active_slot (user_id, active_slot_id)`
- 唯一索引：`uk_bookings_no (booking_no)`
- 普通索引：`idx_bookings_venue_id (venue_id)`
- 普通索引：`idx_bookings_status (status)`

设计说明：

- `venue_id` 作为冗余字段，方便按地点维度统计和查询
- 有效重复预约通过唯一约束和业务校验双重控制；`CANCELLED` 历史记录不占用有效唯一键
- 免费预约成功后 `pay_status` 为 `NOT_REQUIRED`、`paid_amount` 为 `0`
- 收费预约成功后 `pay_status` 应为 `PAID`，`paid_amount` 表示消费码可抵扣或兑换的金额

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
- 目标唯一索引：`uk_wallet_transactions_biz_type (biz_type, biz_no, type)`，保证同一预约只成功扣款或退款一次
- 普通索引：`idx_wallet_transactions_user_id (user_id)`

设计说明：

- 充值、预约扣款、取消退款都必须写入流水
- 同一业务单号的扣款和退款需要具备幂等保护

### 3.7 `booking_vouchers`

说明：该表已由钱包与消费码模块创建。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| id | bigint | 主键 |
| booking_id | bigint | 预约 ID，一对一 |
| user_id | bigint | 用户 ID，冗余便于本人查询 |
| venue_id | bigint | 地点 ID，冗余便于核销归属校验 |
| consumption_code | varchar(32) | 用户出示的消费码，全局唯一 |
| amount | decimal(10,2) | 可抵扣或兑换的预付金额 |
| status | varchar(20) | `AVAILABLE` / `REDEEMED` / `VOID` / `EXPIRED` |
| valid_from | datetime | 最早核销时间 |
| valid_until | datetime | 最晚核销时间 |
| redeemed_by | bigint | 核销商家或管理员，可为空 |
| redeemed_at | datetime | 核销时间，可为空 |
| voided_at | datetime | 退款作废时间，可为空 |
| expired_at | datetime | 过期时间，可为空 |
| created_at | datetime | 创建时间 |
| updated_at | datetime | 更新时间 |

约束与索引：

- 唯一索引：`uk_booking_vouchers_booking_id (booking_id)`
- 唯一索引：`uk_booking_vouchers_consumption_code (consumption_code)`
- 普通索引：`idx_booking_vouchers_venue_status (venue_id, status)`
- 逻辑外键：`booking_id -> bookings.id`
- 逻辑外键：`redeemed_by -> users.id`

设计说明：

- 消费码使用密码学安全随机数生成，不使用预约 ID、手机号或连续序列推导
- 消费凭证金额必须等于预约的 `paid_amount`
- 消费码只通过用户本人预约详情返回，商家列表不批量返回完整码
- 应用日志只记录消费码后四位，避免完整码进入日志
- 核销、退款作废和过期处理均使用带状态条件的更新保证幂等

### 3.8 `queue_daily_sequences`

说明：按地点和日期保存最后一个已分配排队序号。取号事务使用 `insert ... on duplicate key update` 与连接级 `last_insert_id()` 原子递增，避免并发执行 `max(queue_no) + 1` 产生重复号码。

| 字段 | 类型 | 说明 |
| --- | --- | --- |
| venue_id | bigint | 地点 ID |
| queue_date | date | 排队日期 |
| last_no | int | 最后分配序号 |
| updated_at | datetime | 更新时间 |

主键：`(venue_id, queue_date)`。

### 3.9 `queue_tickets`

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
| active_flag | tinyint | 生成列；`WAITING/CALLED` 为 `1`，终态为 `null` |
| updated_at | datetime | 更新时间 |

约束与索引：

- 唯一索引：`uk_queue_tickets_no (ticket_no)`
- 唯一索引：`uk_queue_tickets_daily_no (venue_id, queue_date, queue_no)`
- 唯一索引：`uk_queue_tickets_user_active (venue_id, queue_date, user_id, active_flag)`
- 普通索引：`idx_queue_tickets_venue_status (venue_id, status)`

每日序列表保证号码递增，两个唯一索引分别兜住每日号码重复和同一用户在同一地点同一天持有多个有效号码的竞态。

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

- `NOT_REQUIRED`
- `UNPAID`
- `PAID`
- `REFUNDED`
- `FAILED`

### 4.8 消费凭证状态

- `AVAILABLE`
- `REDEEMED`
- `VOID`
- `EXPIRED`

### 4.9 钱包状态

- `ACTIVE`
- `FROZEN`

### 4.10 钱包流水类型

- `RECHARGE`
- `PAYMENT`
- `REFUND`
- `ADJUSTMENT`

### 4.11 钱包流水状态

- `SUCCESS`
- `FAILED`

### 4.12 排队状态

- `WAITING`
- `CALLED`
- `COMPLETED`
- `MISSED`

## 5. 并发控制设计

### 5.1 预约防超卖

核心思路：

- 先校验 `bookings(user_id, slot_id, status = 'BOOKED')` 是否已存在，并由有效预约唯一索引兜住并发竞态
- 免费和收费预约都使用同一套容量原子更新与重复预约约束
- 再执行带条件的时段容量原子更新；实际 SQL 同时校验时段开放、余量充足、地点启用且支持预约，例如：

```sql
update booking_slots bs
join venues v on v.id = bs.venue_id
set bs.reserved_count = bs.reserved_count + 1
where bs.id = ?
  and bs.status = 'OPEN'
  and bs.reserved_count < bs.capacity
  and v.status = 'ACTIVE'
  and v.booking_enabled = 1;
```

- 若受影响行数为 `0`，说明时段已满或不可预约
- 容量增加和写入 `bookings` 在同一事务内执行，插入失败时容量更新一并回滚
- 收费预约在同一事务内增加钱包余额扣减、钱包流水和消费凭证

### 5.2 模拟支付防重复扣款

核心思路：

- `bookings(user_id, active_slot_id)` 只约束 `BOOKED` 状态，取消后允许重新预约且保留历史记录
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
- 收费预约完整事务包含：名额占用、余额扣减、支付流水、预约记录和 `AVAILABLE` 消费凭证
- `booking_vouchers.booking_id` 唯一键和支付流水业务幂等键共同防止重复生成消费码或重复扣款

### 5.3 取消预约回补名额和退款

- 仅 `BOOKED` 状态可取消，使用条件更新保证并发取消只有一次成功
- 取消成功后事务内执行带 `reserved_count > 0` 条件的名额回补
- 若预约已支付且消费凭证仍为 `AVAILABLE`，则事务内执行余额退款、写入退款流水，并把凭证改为 `VOID`
- 消费凭证为 `REDEEMED` 时禁止取消和退款
- 防止重复取消导致计数异常
- 防止重复退款导致余额异常

### 5.4 消费码并发核销

- 核销使用 `status = 'AVAILABLE'`、地点归属和有效时间窗口作为条件更新
- 只有凭证更新成功后才把预约从 `BOOKED` 更新为 `FULFILLED`
- 凭证状态更新、预约状态更新、核销人和核销时间写入处于同一事务
- 两个请求同时核销同一消费码时，只允许一个请求成功
- 商家必须通过 `venue_id` 校验资源归属，不能跨店核销
- 过期任务把未使用凭证从 `AVAILABLE` 改为 `EXPIRED`，并将对应预约标记为 `NO_SHOW`

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
