# QueueMate 钱包、付费预约与消费码实现

> 本文记录钱包与消费码模块当时的实现基线。自动过期、管理员钱包接口、排队和统计已在 `knowledge/08-backend-completion.md` 中完成。

## 1. 本轮完成内容

- 钱包余额查询、模拟充值和本人流水查询。
- 收费预约的预付消费扣款。
- 支付成功后生成一对一消费凭证。
- 商家核销消费码并完成预约。
- 时段开始前取消收费预约时全额退款并作废消费码。
- 并发付费预约、并发核销和退款/核销互斥保护。

## 2. 钱包一致性

同一用户的钱包余额变更前先执行：

```sql
select *
from wallets
where user_id = ?
for update;
```

行锁保证同一钱包的充值、支付和退款串行执行，因此流水中的 `balance_before` 与 `balance_after` 可以准确对应实际余额。

支付仍使用余额条件更新：

```sql
update wallets
set balance = balance - ?
where id = ?
  and status = 'ACTIVE'
  and balance >= ?;
```

受影响行数不是 1 时，返回余额不足或钱包不可用，事务中不会留下预约、流水或消费码。

## 3. 收费预约事务

收费预约的一个事务包含：

1. 校验地点、时段、重复预约和容量。
2. 原子增加 `reserved_count`。
3. 锁定钱包并扣减预付金额。
4. 写入 `PAYMENT` 钱包流水。
5. 写入 `BOOKED/PAID` 预约。
6. 写入 `AVAILABLE` 消费凭证。

`wallet_transactions(biz_type, biz_no, type)` 防止同一预约重复扣款，`booking_vouchers.booking_id` 防止生成多张消费凭证。

## 4. 消费码

消费码格式为：

```text
QM + 10 位安全随机字符
```

字符集去除了容易混淆的 `I/O/0/1`。数据库唯一键处理随机碰撞，最多尝试生成 5 次。

用户通过“我的预约”获得完整消费码。核销接口和业务异常不输出完整码，代码中也没有记录完整消费码的日志。

## 5. 核销与退款竞态

核销和退款都会先对同一条 `booking_vouchers` 记录执行 `for update`。

核销路径：

```text
AVAILABLE -> REDEEMED
BOOKED -> FULFILLED
```

退款路径：

```text
AVAILABLE -> VOID
BOOKED/PAID -> CANCELLED/REFUNDED
钱包退款 + REFUND 流水 + 名额回补
```

两条事务谁先锁定并改变凭证状态，另一条就会在拿到锁后发现状态不再是 `AVAILABLE`，从而失败并回滚。这避免了“已经消费又退款”的双重成功。

## 6. 权限边界

- 钱包接口仅允许 `USER`。
- 创建预约仅允许 `USER`。
- 用户只能查看自己的完整消费码。
- 核销仅允许地点所属 `MERCHANT` 或 `ADMIN`。
- 商家提交其他地点的消费码返回 `RESOURCE_NOT_OWNED`。
- 用户取消本人预约，管理员可按相同退款窗口取消预约。

## 7. 自动化结果

本轮新增：

- `WalletServiceTest`：5 个。
- `WalletControllerSecurityTest`：4 个。
- `BookingVoucherServiceTest`：7 个。
- `BookingVoucherControllerSecurityTest`：4 个。

后端全量测试：

```text
Tests run: 94, Failures: 0, Errors: 0, Skipped: 0
```

## 8. 真实并发验证

测试时段容量为 3，6 个用户各充值 50，并发预约价格 20 的同一时段：

- 3 个请求成功，3 个请求返回 409。
- 成功用户余额为 30，失败用户余额仍为 50。
- `reserved_count=3`。
- 支付流水数量为 3。
- 消费凭证数量为 3。

对同一消费码并发提交两次核销：

- 1 个请求返回 200。
- 1 个请求返回 409。
- 数据库只有一次核销结果。

临时用户、钱包、流水、预约、消费码和时段均已清理。

## 9. 当前边界

- 当时尚缺的消费码自动过期、`NO_SHOW` 和管理员钱包接口已在后续后端收官迭代完成。
- Postman 集合已经更新，但尚未通过 Newman 正式运行。
- 后续模块的现场排队状态机和繁忙统计已经完成。
