# QueueMate 学习记录 01：项目初始化与本地运行链路

> 记录日期：2026-07-15  
> 当前阶段：项目骨架、设计文档、Maven、MySQL、Spring Boot 健康接口已完成

## 1. 本阶段学习目标

这一阶段不是为了快速堆业务代码，而是先建立一条可重复、可验证的本地开发链路：

```text
需求与边界
  -> 仓库和模块划分
  -> Java/Maven 环境
  -> Spring Boot 工程骨架
  -> MySQL 表结构与模拟数据
  -> 后端启动
  -> 健康接口验证
  -> 自动化测试与 CI 的扩展入口
```

完成这条链路后，后续开发注册、登录、预约、排队、钱包等功能时，每个模块都可以在统一工程结构中实现和测试。

## 2. 当前完成情况

| 项目 | 当前结果 | 验证方式 |
| --- | --- | --- |
| JDK | Java 21.0.8 | `java -version` |
| Maven | Apache Maven 3.9.16 | `mvn -version` |
| 后端框架 | Spring Boot 3.3.5 | 应用启动日志 |
| ORM | MyBatis-Plus 3.5.9 | Maven 依赖已加载 |
| 数据库 | MySQL 8.0.44 | MySQL 客户端连接成功 |
| 数据库初始化 | 7 张表和模拟数据 | `SHOW TABLES` 与聚合计数 |
| 后端测试 | 1 个测试通过 | `mvn test` 返回 `BUILD SUCCESS` |
| Web 服务 | 8080 端口启动成功 | 请求 `/api/v1/health` |
| IDE | VS Code | Java、Maven、Spring Boot 配置已建立 |

数据库初始化后的数据量：

| 表 | 行数 |
| --- | ---: |
| `users` | 6 |
| `venues` | 3 |
| `booking_slots` | 4 |
| `bookings` | 2 |
| `queue_tickets` | 2 |
| `wallets` | 3 |
| `wallet_transactions` | 4 |

## 3. 我的设计过程

### 3.1 先确定项目价值，再选择技术

QueueMate 的目标不是只展示 CRUD，而是展示测开工程能力。因此在设计时优先考虑以下问题：

1. 哪些业务规则适合做接口测试？例如重复预约、余额不足、非法状态流转。
2. 哪些数据竞争适合做性能测试？例如多人同时抢最后几个预约名额。
3. 哪些角色边界适合做权限测试？例如商家不能管理其他商家的地点。
4. 哪些用户流程适合做 UI 自动化？例如登录、选择时段、支付、取消预约。
5. 如何让这些测试能进入 CI，而不是只能在开发者电脑上手工运行？

因此项目从一开始就预留了 Postman、JMeter、Playwright 和 GitHub Actions 目录，而不是等业务完成后再补测试。

### 3.2 选择单仓库、前后端分离结构

当前采用 monorepo，也就是前端、后端、SQL、测试和文档放在同一个 Git 仓库中：

```text
QueueMate/
├── backend/queuemate-server/   Spring Boot 后端
├── frontend/queuemate-web/     Vue 3 前端
├── sql/                        建表与模拟数据
├── tests/                      Postman、JMeter、Playwright
├── docs/                       需求、数据库、API、测试设计
├── knowledge/                  分阶段学习记录
├── scripts/                    本地和 CI 辅助脚本
└── .github/workflows/          GitHub Actions
```

这个结构适合个人作品集和中小型项目，因为：

- 一次提交可以同时包含接口、页面和测试。
- CI 可以从同一仓库构建完整系统。
- 文档、SQL 和代码版本保持一致。
- 不需要过早承担微服务的部署、链路追踪和分布式事务成本。

真实业务是否拆成多个仓库或微服务，应由团队规模、发布边界和系统负载决定，而不是因为“微服务更高级”。

### 3.3 先设计数据和接口，再写业务实现

本项目先完成 requirement、db_design、api_design 和 test_plan，再创建代码骨架。这样做的目的，是先统一以下契约：

- 角色有哪些，分别能操作什么资源。
- 预约和排队分别有哪些状态。
- 容量、余额和状态流转由谁维护。
- API 如何命名、如何鉴权、如何返回错误。
- 测试需要哪些可构造、可查询、可清理的数据。

设计文档不是一次写完后不再变化。开发中发现冲突时，应同时更新文档、代码和测试，使三者保持一致。

### 3.4 使用最短反馈闭环验证初始化

本阶段采用了由小到大的验证顺序：

```text
mvn -version
  -> mvn test
  -> MySQL 登录
  -> 执行 schema.sql
  -> 执行 data.sql
  -> SHOW TABLES / COUNT(*)
  -> mvn spring-boot:run
  -> GET /api/v1/health
```

这种顺序有利于快速定位故障。例如 `mvn -version` 失败时没有必要先排查 Spring Boot；MySQL 客户端都无法登录时，也没有必要先排查 MyBatis。

## 4. Maven 技术点

### 4.1 Maven 解决什么问题

Maven 主要负责：

- 根据 `pom.xml` 下载和管理 Java 依赖。
- 统一编译、测试、打包和运行命令。
- 通过插件扩展 Spring Boot 启动、测试报告等能力。
- 在本机和 CI 中使用相同构建过程。

本项目的 Maven 坐标是：

```xml
<groupId>com.queuemate</groupId>
<artifactId>queuemate-server</artifactId>
<version>0.0.1-SNAPSHOT</version>
```

三者共同标识一个构件。`SNAPSHOT` 表示仍处于开发阶段。

### 4.2 常用生命周期命令

```powershell
mvn clean       # 删除 target 构建产物
mvn compile     # 编译主代码
mvn test        # 编译并执行测试
mvn package     # 测试后生成 jar
mvn spring-boot:run  # 通过插件启动 Spring Boot
```

常见原则：CI 至少运行 `mvn test` 或 `mvn verify`，不能只验证能否编译。

### 4.3 当前主要依赖的职责

| 依赖 | 职责 |
| --- | --- |
| `spring-boot-starter-web` | REST API、JSON、Tomcat |
| `spring-boot-starter-validation` | 请求参数校验 |
| `spring-boot-starter-security` | 认证与授权框架 |
| `mybatis-plus-spring-boot3-starter` | 数据访问和常用 CRUD 能力 |
| `mysql-connector-j` | Java 连接 MySQL 的 JDBC 驱动 |
| `jjwt-*` | JWT 创建、解析和校验 |
| `spring-boot-starter-test` | JUnit、Mockito、Spring 测试支持 |
| `spring-security-test` | 模拟用户、角色和鉴权测试 |

依赖应按实际需要加入。依赖越多，版本冲突、安全漏洞和升级成本越高。

## 5. Spring Boot 技术点

### 5.1 启动类

`QueueMateApplication` 是应用入口。`@SpringBootApplication` 组合了自动配置、组件扫描和配置类能力。

启动过程可以理解为：

```text
JVM 启动
  -> 创建 Spring ApplicationContext
  -> 扫描 Bean
  -> 应用自动配置
  -> 启动内嵌 Tomcat
  -> 注册 Controller 路由
  -> 监听 8080 端口
```

### 5.2 配置文件与环境变量

基础配置位于：

```text
backend/queuemate-server/src/main/resources/application.yml
```

Spring Boot 支持多种配置来源，常见优先级中，命令行参数和环境变量可以覆盖配置文件。例如不修改仓库文件也能临时覆盖数据库密码：

```powershell
$env:SPRING_DATASOURCE_PASSWORD = '本机数据库密码'
mvn spring-boot:run
```

重要安全原则：

- 真实密码、JWT 密钥和云服务密钥不能提交到 Git。
- 开发、测试、生产使用不同配置。
- 生产环境使用环境变量或专业密钥管理服务。
- 数据库不应长期使用 `root` 账号连接应用，应创建最小权限业务账号。

### 5.3 健康接口的意义和边界

当前接口：

```http
GET /api/v1/health
```

示例响应：

```json
{
  "code": "0",
  "message": "success",
  "data": {
    "status": "UP",
    "service": "queuemate-server"
  }
}
```

它证明：

- Java 进程成功启动。
- Tomcat 正在监听 8080。
- Spring MVC 已注册 Controller。
- HTTP 请求和 JSON 序列化正常。

它暂时不能证明：

- 应用已经成功执行 MySQL 查询。
- 所有业务依赖都健康。
- JWT、权限和业务功能已实现。

当前 MySQL 已通过客户端单独验证，但项目还没有 Mapper 查询。因此后续应增加数据库级健康检查或集成测试，真正执行一条 SQL。实际生产中通常使用 Spring Boot Actuator，并谨慎控制健康详情的暴露范围。

## 6. MySQL 数据库设计技术点

### 6.1 为什么使用 InnoDB 和 utf8mb4

所有表使用 InnoDB，主要为了事务、行级锁、崩溃恢复和外键支持。字符集使用 `utf8mb4`，可以完整保存中文和四字节 Unicode 字符。

### 6.2 七张核心表的职责

| 表 | 业务职责 |
| --- | --- |
| `users` | 用户身份、角色和状态 |
| `wallets` | 用户当前模拟余额 |
| `venues` | 商家拥有的地点 |
| `booking_slots` | 可预约日期、时间、容量和价格 |
| `bookings` | 用户预约单和支付状态 |
| `wallet_transactions` | 充值、支付、退款等余额流水 |
| `queue_tickets` | 现场取号及叫号状态 |

### 6.3 主键、唯一约束、外键和索引

这些概念职责不同：

- 主键用于唯一标识一行数据。
- 唯一约束负责阻止业务重复数据。
- 外键保护表之间的引用完整性。
- 普通索引用于提高查询速度，但会增加写入和存储成本。

项目中的典型约束：

| 约束 | 解决的问题 |
| --- | --- |
| `users.username` 唯一 | 防止用户名重复 |
| `bookings(user_id, slot_id)` 唯一 | 防止同一用户重复预约同一时段 |
| 时段四字段唯一 | 防止地点出现完全重复的时段 |
| 每日排队号唯一 | 防止同一地点同一天出现重复号码 |
| `wallets.user_id` 唯一 | 保证一个用户只有一个钱包 |
| 外键 | 防止预约引用不存在的用户、地点或时段 |

业务校验不能只写在前端。前端校验改善体验，后端校验保护接口，数据库约束负责最后一道数据一致性防线。

### 6.4 金额为什么使用 DECIMAL

金额字段使用 `DECIMAL(10,2)`，不能使用 `float` 或 `double`。二进制浮点数可能产生精度误差，而十进制定点数更适合金额计算。

真实支付业务还需要明确：

- 币种。
- 最小金额单位。
- 舍入规则。
- 退款是否允许部分退款。
- 支付和退款幂等号。
- 账务流水是否允许修改。

### 6.5 钱包余额和流水为什么分表

`wallets.balance` 用于快速读取当前余额，`wallet_transactions` 用于记录余额为什么变化。两者应在同一个数据库事务中更新。

支付时需要同时满足：

```text
校验余额充足
  -> 原子扣减余额
  -> 写入支付流水
  -> 更新预约支付状态
  -> 整个事务一起提交或一起回滚
```

只修改余额而不写流水，会导致无法审计；只写流水而没有正确更新余额，会导致账实不符。

### 6.6 预约并发与防超卖

`booking_slots` 同时保存 `capacity` 和 `reserved_count`。不能采用“先查询剩余量，再普通更新”的方式，因为两个并发请求可能同时看到最后一个名额。

推荐使用数据库条件更新：

```sql
update booking_slots
set reserved_count = reserved_count + 1
where id = ?
  and status = 'OPEN'
  and reserved_count < capacity;
```

根据受影响行数判断是否抢位成功：

- 受影响 1 行：占位成功。
- 受影响 0 行：时段已满或不可预约。

预约记录创建、容量占用和钱包支付需要进一步设计事务边界。JMeter 后续应验证高并发下 `reserved_count <= capacity`，并检查成功预约数与计数是否一致。

## 7. SQL 初始化与模拟数据

建表和模拟数据被拆成两个文件：

```text
sql/schema.sql  # 数据库结构
sql/data.sql    # 本地演示数据
```

这样拆分的好处是结构和数据职责清晰，测试环境可以按需加载不同数据集。

MySQL 客户端中可以执行：

```sql
source D:/QueueMate/sql/schema.sql;
source D:/QueueMate/sql/data.sql;
```

注意：当前 `schema.sql` 包含 `DROP TABLE IF EXISTS`，适合本地首次初始化或可重建测试环境，不适合直接用于生产升级。真实业务应使用 Flyway 或 Liquibase 管理增量迁移，例如：

```text
V1__create_initial_tables.sql
V2__add_booking_payment_columns.sql
V3__add_query_indexes.sql
```

已经上线的表通常通过增量变更演进，不能每次删除重建。

## 8. VS Code 开发方式

项目已预留以下工作区配置：

- `.vscode/extensions.json`：推荐 Java、Maven、Spring Boot、REST Client 等插件。
- `.vscode/settings.json`：指定 Java 21。
- `.vscode/tasks.json`：提供 `backend: test` 和 `backend: run`。
- `.vscode/launch.json`：提供 Java 调试入口。

命令行和 IDE 并不是两套构建系统。VS Code 最终仍然调用 JDK、Maven 和 Spring Boot，因此要先理解命令行，再使用 IDE 提升效率。

调试相比普通启动的主要价值是：

- 设置断点。
- 查看变量和调用栈。
- 单步执行代码。
- 定位异常发生前的数据状态。

## 9. 本次遇到的问题与排查方法

### 9.1 `mvn` 命令不存在

可能原因：Maven 未安装，或 Maven 的 `bin` 目录没有进入 `PATH`。

排查：

```powershell
mvn -version
$env:MAVEN_HOME
$env:Path
```

修改系统环境变量后，需要重新打开 PowerShell 或 VS Code，旧进程通常不会自动获得新环境变量。

### 9.2 Maven 无法下载依赖

典型表现是 `Could not transfer artifact`、DNS、连接超时或权限错误。

排查顺序：

1. 检查是否能访问 Maven Central 或团队私服。
2. 检查代理、VPN、防火墙和 `settings.xml`。
3. 检查本地仓库中失败下载生成的 `.lastUpdated` 状态。
4. 在企业环境中确认私服地址和认证配置。

首次下载依赖较慢是正常的，之后会优先使用本地 Maven 仓库缓存。

### 9.3 MySQL `Access denied`

这通常表示账号、密码、主机授权或认证方式不正确，不代表 MySQL 服务没有启动。

应分别检查：

- Windows 的 MySQL 服务是否处于 Running。
- 用户名和密码是否正确。
- 账号是否允许从当前 host 登录。
- 账号是否有目标数据库权限。

### 9.4 浏览器显示 `ERR_CONNECTION_REFUSED`

这表示目标地址没有进程接受连接，常见原因：

- Spring Boot 尚未启动。
- 启动过程中报错退出。
- 服务已被手工停止。
- 端口不是 8080。
- 8080 被其他程序占用。

Windows 可以使用以下命令检查端口：

```powershell
Get-NetTCPConnection -LocalPort 8080 -ErrorAction SilentlyContinue
```

它与 HTTP 401、403、404、500 不同：这些 HTTP 状态码说明连接已经到达服务器，只是认证、路由或业务处理出现问题。

### 9.5 `No MyBatis mapper was found`

当前还没有创建 Mapper，因此启动日志中的该警告符合现阶段状态。实现用户模块时创建 Mapper 后应再次观察该警告是否消失。

### 9.6 Spring Security 生成临时密码

因为已引入 Spring Security、但自定义 JWT 登录尚未完成，开发模式下可能看到自动生成的临时密码。后续实现自定义 `SecurityFilterChain`、用户加载和 JWT 过滤器后，应取消默认表单登录行为。

## 10. 面向真实业务需要继续加强的部分

当前骨架适合学习和本地演示，但距离生产系统还有明显差距：

| 当前做法 | 真实业务建议 |
| --- | --- |
| 使用 root 数据库账号 | 创建最小权限应用账号 |
| 本地传入数据库密码 | 使用环境变量或密钥管理服务 |
| SQL 脚本删除并重建表 | 使用 Flyway/Liquibase 增量迁移 |
| 模拟密码可能使用 `{noop}` | 使用 BCrypt/Argon2 哈希，绝不保存明文 |
| 自定义简单健康接口 | 接入 Actuator，并增加依赖健康检查 |
| 手工启动和验证 | 使用自动化集成测试和 CI |
| 单机 MySQL | 根据业务规模设计备份、恢复、高可用和监控 |
| 本地模拟钱包 | 保持账务事务、幂等、审计和对账能力 |
| 仅应用日志 | 增加结构化日志、trace ID、指标和告警 |

生产化不是一次性把所有技术都加入，而是在明确风险后按阶段增加能力。

## 11. 当前验证结论

已经可以确认：

- Java、Maven 和 Spring Boot 构建链路正常。
- MySQL 服务可登录，表结构和初始化数据已成功写入。
- Spring Boot 可以监听 8080 并返回健康接口 JSON。
- 项目具备继续开发后端业务模块的基础。

仍需后续验证：

- 通过 MyBatis Mapper 从 Spring Boot 中执行真实 SQL。
- 数据库异常时应用的错误处理和健康状态。
- JWT 登录、角色权限和资源归属校验。
- 预约与钱包事务的一致性。
- 并发预约是否真正不超卖。

## 12. 下一阶段学习任务

下一阶段建议实现“用户注册、登录与 JWT 鉴权”，学习顺序如下：

1. 用户实体、DTO、Mapper 和 Service 分层。
2. BCrypt 密码哈希与密码校验。
3. 注册参数校验和用户名唯一冲突处理。
4. 登录校验和 JWT 签发。
5. JWT 解析、过期校验和过滤器。
6. `USER`、`MERCHANT`、`ADMIN` 权限规则。
7. Postman 正常、异常、未登录和越权测试。
8. Spring Boot 集成测试连接 MySQL 测试库。

完成后，将学习结果继续记录为：

```text
knowledge/02-authentication-and-jwt.md
```

## 13. 自测问题

如果能独立回答下面的问题，说明已经掌握本阶段核心内容：

1. Maven 的 `compile`、`test`、`package` 分别做什么？
2. 为什么修改 `PATH` 后要重新打开终端？
3. `pom.xml` 中 `runtime` 和 `test` scope 有什么区别？
4. Spring Boot 从启动类到监听 8080 经历了什么？
5. 健康接口成功为什么不一定代表数据库查询成功？
6. 唯一约束、外键和普通索引分别解决什么问题？
7. 为什么金额使用 `DECIMAL` 而不是 `double`？
8. 为什么钱包余额和钱包流水要同时保留？
9. 为什么“先查库存再更新”可能导致超卖？
10. 为什么生产环境不能使用会删除表的初始化脚本？
11. `ERR_CONNECTION_REFUSED` 与 HTTP 500 有什么区别？
12. 为什么真实数据库连接不应该使用 root 账号？

