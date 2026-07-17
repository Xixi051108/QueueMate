# QueueMate 免费预约与并发防超卖

> 本文记录免费预约阶段的历史实现。收费预约已在后续钱包与消费码模块中上线，当前行为以 `knowledge/07-wallet-voucher-implementation.md` 为准。

## 1. 本轮目标

本轮完成用户预约的第一个可运行闭环：

- `USER` 创建免费预约。
- 查询当前用户自己的预约。
- 用户取消本人预约，`ADMIN` 可取消任意预约。
- 阻止重复预约和容量超卖。
- 取消时只回补一次名额。
- 收费时段暂不创建预约，等待钱包支付模块。

## 2. 为什么先做免费预约

收费预约还需要钱包余额条件扣减、支付流水、退款流水和幂等保护。如果在钱包模块之前直接把收费预约标为成功，会形成错误业务状态。

因此本轮采用明确的阶段边界：

- `price = 0`：创建 `BOOKED` 预约，`payStatus=NOT_REQUIRED`，`paidAmount=0`。
- `price > 0`：返回 `409/BOOKING_PAYMENT_REQUIRED`，不占名额、不写预约。

## 3. 创建预约的事务顺序

创建预约在一个事务中完成：

1. 校验调用者是 `USER`。
2. 查询时段和地点。
3. 校验地点启用、允许预约、时段开放且未开始。
4. 校验当前为免费时段。
5. 预检查同一用户是否已预约该时段。
6. 用数据库条件更新原子增加 `reserved_count`。
7. 插入 `bookings` 记录。

核心容量更新同时校验时段和地点状态：

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

受影响行数为 `1` 才代表抢到名额。并发请求会在 MySQL 中竞争同一行，达到容量后其余请求无法再满足 `reserved_count < capacity`。

容量更新和预约插入处于同一事务。如果插入失败，前面的容量增加也会回滚。

## 4. 重复预约的双重保护

重复预约不能只依赖“先查再插”，因为两个并发请求可能同时查询到不存在。

本轮使用两层保护：

- Service 预检查，尽早返回可读的 `BOOKING_DUPLICATE`。
- 数据库生成列 `active_slot_id` 只在状态为 `BOOKED` 时取 `slot_id`，唯一键 `uk_bookings_user_active_slot (user_id, active_slot_id)` 处理并发竞态。

取消后生成列变为 `NULL`。MySQL 唯一索引允许多条 `NULL`，因此历史取消记录可以保留，用户也能重新预约同一时段；新的有效预约仍会再次占用唯一键。

数据库抛出的重复键异常也映射为 `409/BOOKING_DUPLICATE`，并由事务回滚已经增加的名额。

## 5. 取消预约的一致性

取消只允许从 `BOOKED` 进入 `CANCELLED`：

```sql
update bookings
set status = 'CANCELLED',
    cancel_reason = ?,
    cancelled_at = ?
where id = ?
  and status = 'BOOKED';
```

只有受影响行数为 `1` 时才继续回补容量：

```sql
update booking_slots
set reserved_count = reserved_count - 1
where id = ?
  and reserved_count > 0;
```

这两个更新位于同一事务。两个请求并发取消同一预约时，只有一个请求能把 `BOOKED` 改成 `CANCELLED`，因此名额只会回补一次。

## 6. 权限和接口边界

- `POST /api/v1/bookings`：仅 `USER`。
- `GET /api/v1/bookings/my`：仅 `USER`，只查询本人。
- `PATCH /api/v1/bookings/{id}/cancel`：预约本人或 `ADMIN`。
- `MERCHANT` 不能代表用户创建或取消预约。
- 所有 64 位 ID 在 JSON 响应中序列化为字符串。

## 7. 测试结果

自动化回归：

- `BookingServiceTest`：15 个。
- `BookingControllerSecurityTest`：10 个。
- 后端全量：72 个测试全部通过。

真实 HTTP + MySQL 并发验证：

- 临时时段容量：3。
- 并发用户数：12。
- 成功预约：3。
- `BOOKING_SLOT_FULL`：9。
- 最终 `reserved_count`：3。
- 最终有效预约数：3。
- 重复用户时段组合：0。
- 同一预约并发取消：1 成功、1 个 `BOOKING_STATUS_INVALID`。
- 取消后重新预约成功，容量和有效预约数继续一致。

验证完成后，临时预约、时段、钱包和用户数据均已清理。

## 8. 下一步

下一模块接入站内钱包：

1. 钱包查询和模拟充值。
2. 收费预约的余额条件扣减。
3. 支付流水与预约创建同事务。
4. 已支付预约取消退款。
5. 防重复扣款、防重复退款和并发余额一致性。
