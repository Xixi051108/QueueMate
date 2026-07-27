# Postman/Newman 可重复全量回归

## 1. 为什么不能直接运行旧集合

旧集合包含注册、充值、创建地点和时段、预约、退款、取号、叫号与管理员余额调整。Postman 脚本只能调用 HTTP API，而业务 API 刻意没有提供删除用户、地点、时段和钱包流水的能力，因此直接全量运行会持续污染本地开发库。

## 2. 本轮方案

- 每次注册请求生成唯一 `runId`。
- 动态用户名使用 `qm_<runId>`。
- 测试地点使用 `Postman Venue <runId>`。
- 管理员调整备注包含同一 `runId`。
- 集合保存后端返回的地点、时段、预约和排队 ID，避免把 64 位 ID 转为 JavaScript Number。
- 集合最后调用专用清理端点，按外键顺序删除本轮数据。
- 清理响应返回 `remainingArtifacts`，Postman 断言必须为 `0`。

## 3. 清理端点的安全边界

清理端点需要同时满足：

1. Spring profile 为 `e2e`。
2. `queuemate.test-support.enabled=true`。
3. 请求身份为 `ADMIN`。

清理服务不会按模糊前缀批量删除。用户还必须匹配固定测试昵称和手机号；地点必须匹配运行标记与本地商家夹具；时段必须属于固定测试地点、由测试商家近期创建；调整流水备注必须包含本轮 `runId`。任一现存资源不满足约束时返回 `409/TEST_DATA_UNSAFE` 并回滚事务。

## 4. 中断与补偿

Runner 和 Newman 均不使用 `bail`，保证普通断言失败后仍会走到 Teardown。如果运行在管理员余额增加后中断，清理服务会计算本轮标记调整流水的净变化，先反向恢复余额，再删除流水。排队号码删除后，会根据剩余号码重新计算每日序列，不会盲目删除其他用户的序列状态。

如果进程或网络在 Teardown 前完全中断，保留当前 Postman 变量，恢复 E2E 服务后单独执行管理员登录与 `Cleanup current Postman run`。

## 5. Newman 命令

```powershell
cd D:\QueueMate\tests\postman
pnpm install
pnpm test:report
```

`package.json` 固定 Newman 6.2.2，`pnpm-lock.yaml` 固定传递依赖。JSON 报告是运行产物，不提交 Git。

## 6. 首次正式结果

```text
Requests: 45, failed: 0
Assertions: 99, failed: 0
Average response time: 39 ms
Run-scoped remaining artifacts: 0
```

这说明“集合文件存在”“JSON 可解析”和“全量回归已通过”是三个不同层次的结论；只有保留真实执行命令、断言汇总和清理证据，才能标记 Newman 通过。
