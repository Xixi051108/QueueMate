# QueueMate

QueueMate 是一个面向测开作品集的生活排队与预约平台项目，聚焦常见线下场景的 Web 化管理与测试实践，例如奶茶店取号、自习室时段预约、羽毛球场预约等。

当前阶段目标是完成项目初始化和设计文档，先把后续开发、测试、性能验证和 CI 需要的蓝图定清楚，再逐步实现后端、前端和自动化能力。

## 项目目标

- 练习 Spring Boot + Vue3 + MySQL 的完整业务开发流程
- 体现测开价值，而不是只做基础 CRUD
- 为 Postman、JMeter、Playwright、GitHub Actions 提供可落地的测试对象
- 沉淀一个适合面试展示和后续扩展的个人作品集项目

## 技术栈

- 后端：Java, Spring Boot, MyBatis-Plus, MySQL
- 前端：Vue3, Element Plus, Axios
- 测试：Postman, JMeter, Playwright
- CI：GitHub Actions

## MVP 功能

- 用户注册、登录、JWT 鉴权
- 角色权限：`USER`、`MERCHANT`、`ADMIN`
- 地点管理：奶茶店、自习室、羽毛球场等模拟地点
- 时间段预约：固定时段槽位、容量限制、取消预约、重复预约限制
- 现场取号：取号、叫号、完成、过号
- 繁忙时段统计

## 非目标

- 不接真实地图
- 不接短信验证码
- 不接微信登录
- 不接支付
- 不接真实商家或真实地理位置数据

## 仓库结构

```text
QueueMate/
├── README.md
├── docs/
│   ├── api_design.md
│   ├── db_design.md
│   ├── requirement.md
│   └── test_plan.md
├── backend/
│   └── queuemate-server/
│       └── src/
│           ├── main/
│           │   ├── java/
│           │   └── resources/
│           └── test/
│               └── java/
├── frontend/
│   └── queuemate-web/
│       ├── public/
│       └── src/
├── tests/
│   ├── jmeter/
│   ├── playwright/
│   │   └── specs/
│   └── postman/
├── .github/
│   └── workflows/
├── scripts/
└── sql/
```

## 文档说明

- 需求说明：[docs/requirement.md](/D:/QueueMate/docs/requirement.md)
- 数据库设计：[docs/db_design.md](/D:/QueueMate/docs/db_design.md)
- 接口设计：[docs/api_design.md](/D:/QueueMate/docs/api_design.md)
- 测试计划：[docs/test_plan.md](/D:/QueueMate/docs/test_plan.md)

## 开发规划

### Phase 1

- 完成项目初始化和文档设计
- 确定仓库结构、数据库模型、核心接口和测试策略

### Phase 2

- 实现 Spring Boot 后端基础框架
- 完成认证、权限、地点、预约、排队核心 API
- 预留集成测试和并发测试入口

### Phase 3

- 实现 Vue3 前端基础页面
- 打通登录、地点浏览、预约、我的预约、商家叫号等主流程

### Phase 4

- 接入 Postman/Newman 接口测试
- 接入 JMeter 并发预约性能测试
- 接入 Playwright Web UI 自动化测试

### Phase 5

- 接入 GitHub Actions 持续集成
- 自动执行构建、基础测试和关键回归

## 测开亮点

- JWT 鉴权与 RBAC 权限测试
- 预约并发场景下的防超卖验证
- 预约与取号状态流转校验
- 商家隔离与越权访问测试
- Web UI 自动化覆盖用户端和商家端主流程

## 后续实现建议

- 后端先落认证、地点、预约三条主线
- 数据库先落核心表和初始化模拟数据
- 前端先做最短业务闭环，不追求复杂设计
- 自动化测试优先覆盖接口、权限、并发和状态流转
