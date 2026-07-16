# QueueMate 后端收官实现

## 1. 本轮范围

本轮把需求文档中的剩余后端能力一次性补齐：

- 现场取号、本人号码和公开排队进度。
- 商家叫号、完成、过号和管理员全局操作。
- 并发安全的地点每日递增号码。
- 管理员钱包流水和余额调整。
- 消费码自动过期与预约 `NO_SHOW`。
- 预约与排队合并的繁忙时段统计。

前端、Newman/JMeter/Playwright 正式执行和 GitHub Actions 不属于本轮后端实现范围。

## 2. 并发取号序列

直接执行下面的逻辑存在竞态：

```sql
select max(queue_no) + 1 from queue_tickets;
```

多个事务可能同时读到相同最大值。因此新增 `queue_daily_sequences`，主键为：

```text
(venue_id, queue_date)
```

每次取号在事务中执行：

```sql
insert into queue_daily_sequences (venue_id, queue_date, last_no)
values (?, ?, last_insert_id(1))
on duplicate key update
  last_no = last_insert_id(last_no + 1);
```

随后在同一数据库连接读取：

```sql
select last_insert_id();
```

MySQL 会对主键冲突记录加行锁，因此同一地点同一天的并发请求会串行获得不同序号。

## 3. 有效号码防重

`queue_tickets.active_flag` 是生成列：

```text
WAITING / CALLED -> 1
COMPLETED / MISSED -> null
```

唯一索引：

```text
(venue_id, queue_date, user_id, active_flag)
```

这允许用户在号码进入终态后重新取号，同时禁止同一用户并发获得两个有效号码。

## 4. 状态机

排队状态只允许：

```text
WAITING -> CALLED
CALLED -> COMPLETED
CALLED -> MISSED
```

状态修改使用带旧状态条件的更新：

```sql
update queue_tickets
set status = ?
where id = ?
  and status = ?;
```

并发叫号或重复完成时只有一个请求能更新一行，另一个请求返回 `QUEUE_STATUS_INVALID`。

## 5. 自动过期

定时任务按固定间隔执行两个事务内更新：

1. 把 `valid_until` 已经过期的 `AVAILABLE` 凭证更新为 `EXPIRED`。
2. 把这些凭证对应的 `BOOKED/PAID` 预约更新为 `NO_SHOW`。

核销、退款和过期都以 `AVAILABLE` 为条件，因此同一凭证只能有一条路径成功。

## 6. 管理员余额调整

管理员调整金额支持正负数：

- 正数：增加余额。
- 负数：扣减余额。
- `0`：拒绝。
- 扣减后小于 `0`：拒绝。

调整前先锁定钱包行，并写入 `ADJUSTMENT` 流水。流水金额保存绝对值，方向由 `balance_before` 和 `balance_after` 表达。

## 7. 繁忙时段统计

统计不维护冗余汇总表，而是实时聚合：

- 预约：按 `booking_slots.start_time` 的小时统计。
- 排队：按 `queue_tickets.taken_at` 的小时统计。
- 热度：`bookingCount + queueCount`。

商家只能查看自己地点，管理员可查看全部地点。查询日期范围最多 366 天。

## 8. 验证结果

自动化测试：

```text
Tests run: 125, Failures: 0, Errors: 0, Skipped: 0
```

真实 MySQL 并发取号：

- 6 个用户同时请求同一地点。
- 6 个请求全部成功。
- 获得连续且唯一的 `3-8` 号。
- 数据库序列表 `last_no=8`。

同一号码并发叫号：

```text
200, 409
```

自动过期验证：

```text
BOOKED / AVAILABLE
  -> NO_SHOW / EXPIRED
```

管理员余额增加后再扣减，最终余额恢复，调整流水完整。所有临时用户、号码、序列、流水、预约、消费码、时段和数据库账号均已清理。

## 9. 当前结论

需求文档范围内的后端功能已经完成。下一阶段可以进入 Vue3 前端、正式接口/性能/UI 自动化执行和 GitHub Actions。
