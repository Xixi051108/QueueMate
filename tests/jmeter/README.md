# QueueMate JMeter

当前正式场景为“并发预约防超卖”：脚本创建容量为 3 的免费预约时段和 12 个独立用户，在同步定时器释放后同时预约。通过标准为 3 个预约成功、9 个返回 `BOOKING_SLOT_FULL`、时段 `reservedCount=3` 且 `availableCapacity=0`。

## 运行前提

1. MySQL 和 QueueMate 后端可用。
2. 后端使用 `e2e` profile 并启用测试清理端点。
3. 本机已安装 JMeter 5.6.3 和 Java 21。

## 非 GUI 正式执行

推荐使用一键脚本。它会生成带时间戳的 JTL 和 HTML 报告，并在任何采样器失败时返回失败状态：

```powershell
cd D:\QueueMate\tests\jmeter
.\run.ps1
```

也可以直接运行 JMeter：

```powershell
cd D:\QueueMate\tests\jmeter
New-Item -ItemType Directory -Force results | Out-Null
& 'D:\JMeter\apache-jmeter-5.6.3\apache-jmeter-5.6.3\bin\jmeter.bat' `
  -n `
  -t concurrent-booking.jmx `
  -l results\concurrent-booking.jtl `
  -e `
  -o report
```

一键脚本会在结束时输出具体报告路径。`results`、`report` 和 `jmeter.log` 都是忽略的本地运行产物。

## 可调参数

```powershell
.\run.ps1 -HostName 127.0.0.1 -Port 8080 -RunId jm_manual_001
```

不传 `runId` 时脚本会根据当前毫秒时间自动生成。无论业务断言成功还是失败，tearDown 线程组都会尝试清理 12 个动态用户、预约、测试时段和测试地点。

## 正式执行结果

2026-08-11 使用 JMeter 5.6.3 对真实 Spring Boot + MySQL 执行：

- 12 个并发预约请求中，3 个返回 201，9 个返回 `409/BOOKING_SLOT_FULL`。
- 时段最终 `reservedCount=3`、`availableCapacity=0`，没有超卖。
- 预约请求平均 191.1ms，P90 209ms，P95 215ms，最大 215ms。
- 全流程共 56 个采样，错误率 0.00%。
- 12 个动态用户、3 条预约、测试时段和测试地点均已清理，`remainingArtifacts=0`。
