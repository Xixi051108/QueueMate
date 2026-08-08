# QueueMate Playwright E2E

首阶段覆盖核心收费链路：注册、充值、付费预约、取消、退款和消费凭证作废。

## 运行前提

1. 本地 MySQL `queuemate` 数据库可用，并包含初始化账号。
2. 后端必须使用 `e2e` profile 且启用测试清理端点。
3. 不需要手工启动前端；Playwright 会启动或复用 Vite 服务。

推荐用 VS Code 启动 `QueueMate Server (E2E - Postman cleanup enabled)`。也可以在后端目录执行：

```powershell
$env:DB_PASSWORD = '在本机临时输入，不要提交'
$env:SPRING_PROFILES_ACTIVE = 'e2e'
$env:TEST_SUPPORT_ENABLED = 'true'
& 'D:\Maven\apache-maven-3.9.16\bin\mvn.cmd' spring-boot:run
```

## 首次安装

```powershell
cd D:\QueueMate\tests\playwright
pnpm install
pnpm exec playwright install chromium
```

## 执行

```powershell
pnpm test
pnpm test:headed
pnpm report
```

测试通过和失败都会在 `finally` 中调用受保护的清理接口。成功标准包括业务断言通过，以及清理响应 `remainingArtifacts=0`。
