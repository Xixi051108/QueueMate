# QueueMate 学习记录 02：用户注册、登录与 JWT 鉴权

> 当前阶段：后端认证最小闭环已实现并通过真实 MySQL 冒烟验证

## 1. 本阶段完成目标

本阶段实现了三个接口：

```http
POST /api/v1/auth/register
POST /api/v1/auth/login
GET  /api/v1/auth/me
```

形成的完整链路是：

```text
注册参数校验
  -> 用户名唯一性检查
  -> BCrypt 密码哈希
  -> 创建 USER
  -> 创建零余额钱包
  -> 事务提交

登录账号密码校验
  -> 检查账号状态
  -> 签发 JWT
  -> 客户端保存 token

Bearer token
  -> JWT 验签和过期检查
  -> 查询数据库中的最新用户状态和角色
  -> 建立 Spring Security 身份
  -> 访问 /auth/me
```

## 2. 代码分层

| 层次 | 主要文件 | 职责 |
| --- | --- | --- |
| Controller | `AuthController` | 接收 HTTP 请求并返回统一响应 |
| DTO | `RegisterRequest`、`LoginRequest` 等 | 定义接口契约和参数校验 |
| Service | `AuthService` | 注册、登录、用户钱包事务 |
| Mapper | `UserMapper`、`WalletMapper` | 访问 MySQL |
| Entity | `User`、`Wallet` | 映射数据库表 |
| Security | `JwtAuthenticationFilter` | 从 Bearer token 恢复身份 |
| Token | `JwtTokenService` | JWT 签发、验签和解析 |
| Exception | `GlobalExceptionHandler` | 统一业务和系统错误 |

分层的重点不是文件数量，而是让 HTTP、业务规则、数据访问和安全机制可以分别测试和演进。

## 3. 注册设计

### 3.1 为什么只能注册 USER

公开注册接口固定创建 `USER`。不能接受客户端传入 `role`，否则攻击者可以直接注册 `ADMIN` 或 `MERCHANT`。

商家和管理员账号应由后台审批、初始化脚本或管理员接口创建。

### 3.2 用户名和参数校验

注册请求使用 Jakarta Validation：

- 用户名 3 到 50 个字符。
- 密码 8 到 64 个字符。
- 昵称必填，最多 100 个字符。
- 手机号可选，填写时必须满足当前模拟格式。

参数校验失败统一返回：

```json
{
  "code": "PARAM_INVALID",
  "message": "请求参数不合法",
  "data": null
}
```

前端校验用于改善用户体验，后端校验用于保护接口，数据库唯一约束用于处理并发下的最终一致性。

### 3.3 为什么检查用户名后还要捕获唯一键冲突

只执行“先查询用户名是否存在”仍然存在并发竞态：

```text
请求 A：查询，不存在
请求 B：查询，不存在
请求 A：插入成功
请求 B：插入冲突
```

因此业务层先查询用于快速给出友好错误，数据库 `users.username` 唯一约束负责最终防线，代码还要把重复键异常转换为 `409 / USERNAME_EXISTS`。

### 3.4 用户和钱包为什么使用同一事务

注册成功后，每个普通用户都应该有钱包。因此创建用户和创建钱包必须一起成功或一起失败：

```java
@Transactional
public RegisterResponse register(RegisterRequest request) {
    // insert user
    // insert wallet
}
```

如果钱包插入失败，事务会回滚用户插入，避免出现“用户存在但钱包缺失”的半成品数据。

## 4. 密码安全

### 4.1 不能保存明文密码

数据库只保存 `password_hash`。新注册用户使用 BCrypt，保存值以 `{bcrypt}` 开头。BCrypt 的特点包括：

- 每次哈希自动加入随机盐。
- 相同密码多次哈希结果通常不同。
- 校验时使用 `matches`，不能解密原密码。
- 计算成本可调，降低暴力破解速度。

### 4.2 当前密码策略

认证模块初版只实现 8 到 64 个字符的基础长度校验和 BCrypt 哈希，没有实现密码强度分级、泄露密码阻断或复杂字符规则。这是适合第一阶段学习的最小实现，后续再单独设计完整密码策略。

### 4.3 登录错误信息为什么不区分账号和密码

无论用户不存在还是密码错误，都返回：

```json
{
  "code": "AUTH_CREDENTIALS_INVALID",
  "message": "用户名或密码错误",
  "data": null
}
```

这样可以减少攻击者通过接口枚举有效用户名。真实业务还应增加失败次数限制、审计日志、IP/账号维度限流和必要的人机验证。

## 5. JWT 设计

### 5.1 token 中包含什么

当前 JWT 包含：

| Claim | 含义 |
| --- | --- |
| `iss` | 签发者 `QueueMate` |
| `sub` | 用户 ID |
| `iat` | 签发时间 |
| `exp` | 过期时间 |
| `username` | 用户名 |
| `role` | `USER`、`MERCHANT` 或 `ADMIN` |

JWT 默认有效期是 7200 秒。

JWT 只是签名，不是加密。payload 可以被客户端解码，因此不能放密码、手机号、身份证号等敏感信息。

### 5.2 签名解决什么问题

服务器使用密钥对 header 和 payload 签名。攻击者可以读取 JWT 内容，但没有密钥就无法合法修改用户 ID、角色或过期时间。

服务端必须验证：

- 签名是否正确。
- `iss` 是否为预期签发者。
- token 是否过期。
- Claim 类型和值是否可解析。

### 5.3 为什么每次请求还要查询用户

只相信 token 中的角色会产生状态延迟。例如管理员已经禁用某账号，但旧 token 在两小时内仍可能有效。

当前过滤器根据 token 的用户 ID 查询数据库，并使用数据库中的最新状态和角色建立身份。因此：

- 用户被禁用后，旧 token 会失效。
- 用户角色修改后，权限立即使用新角色。
- token 内仍保留角色 Claim，便于测试和展示，但授权以数据库为准。

代价是每个认证请求增加一次数据库查询。真实高流量系统可以使用短期缓存、token version 或集中式会话方案，在性能和实时失效之间权衡。

### 5.4 为什么使用无状态 Session

安全配置使用：

```text
SessionCreationPolicy.STATELESS
```

服务端不创建传统登录 Session，每次请求都携带 Bearer token。这便于 API、前后端分离和水平扩容，但 token 的存储、刷新、撤销和泄露处理需要单独设计。

## 6. Spring Security 请求过程

```text
HTTP 请求
  -> JwtAuthenticationFilter
  -> 读取 Authorization: Bearer ...
  -> JwtTokenService 验签
  -> UserMapper 查询用户
  -> 写入 SecurityContext
  -> URL 权限规则
  -> Controller
```

公开接口：

- `/api/v1/health`
- `/api/v1/auth/register`
- `/api/v1/auth/login`

其他接口默认要求认证。后续地点、钱包、预约模块会继续增加 `hasRole` 或方法级资源权限校验。

## 7. 401 与 403

| 状态 | 含义 | 示例 |
| --- | --- | --- |
| `401 Unauthorized` | 没有有效身份 | token 缺失、伪造、过期 |
| `403 Forbidden` | 身份有效但没有权限 | USER 调用管理员接口 |

当前统一响应：

```json
{
  "code": "AUTH_UNAUTHORIZED",
  "message": "请先登录或提供有效 token",
  "data": null
}
```

```json
{
  "code": "AUTH_FORBIDDEN",
  "message": "无权访问当前资源",
  "data": null
}
```

## 8. 配置与密钥

数据库和 JWT 配置支持环境变量覆盖：

```powershell
$env:DB_USERNAME = '本地数据库账号'
$env:DB_PASSWORD = '本地数据库密码'
$env:JWT_SECRET = '至少32字节的随机密钥'
mvn spring-boot:run
```

真实密钥不能提交到 Git。生产环境应使用部署平台 Secret、Vault、云密钥管理服务等集中管理，并建立轮换机制。

## 9. 已完成的测试

自动化测试覆盖：

- 注册创建 `USER`。
- 注册同时创建零余额钱包。
- 新密码使用 BCrypt。
- 重复用户名返回冲突。
- 正确密码登录并返回 JWT。
- 错误密码返回统一认证错误。
- 禁用用户无法登录。
- 当前用户信息从数据库读取。
- JWT 可解析用户 ID、用户名和角色。
- 被篡改 JWT 无法通过验签。
- 非法 JSON 返回 `400/PARAM_INVALID`。

真实 HTTP + MySQL 冒烟覆盖：

- 未登录访问 `/auth/me` 返回 401。
- 伪造 token 返回 401。
- 注册返回新用户 ID 和 `USER`。
- 登录返回 `Bearer` token 和 7200 秒有效期。
- 携带 token 能获取本人信息。
- 数据库密码字段前缀为 `{bcrypt}`。
- 注册后钱包余额为 `0.00`、状态为 `ACTIVE`。
- 冒烟测试账号已清理。

Postman 资产：

- `tests/postman/QueueMate.postman_collection.json`
- `tests/postman/QueueMate.local.postman_environment.json`

集合会按顺序执行动态用户注册、登录、token 保存、当前用户查询，并验证错误密码和缺失 token。动态注册会在本地数据库留下测试用户，后续可以增加测试数据清理接口或 Newman 执行后的数据库清理脚本。

## 10. 后续安全参考

- [NIST SP 800-63B](https://pages.nist.gov/800-63-4/sp800-63b.html)：后续设计密码长度、阻断列表和规范化策略时参考。
- [OWASP Authentication Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Authentication_Cheat_Sheet.html)：无 MFA 时的密码长度、强度计、常见/泄露密码阻断建议。
- [OWASP Password Storage Cheat Sheet](https://cheatsheetseries.owasp.org/cheatsheets/Password_Storage_Cheat_Sheet.html)：密码哈希算法和 BCrypt 72 字节输入限制。

## 11. 当前边界与后续增强

当前尚未实现：

- Refresh Token。
- 主动登出和 token 黑名单。
- 登录失败次数限制。
- 验证码和风控。
- 密码重置。
- 手机号真实性验证。
- 商家和管理员账号审批。
- token 密钥轮换。
- 权限注解和资源归属校验。

MVP 可以先使用短期 Access Token。等前端登录流程完成后，再根据实际需要决定是否加入 Refresh Token，避免一开始过度设计。

## 12. 自测问题

1. 为什么公开注册接口不能接收客户端传入的角色？
2. 为什么查询用户名不存在后，插入时仍可能发生唯一键冲突？
3. `@Transactional` 在注册流程中保护了哪些数据？
4. BCrypt 哈希为什么每次可能不同？
5. 为什么不能从 BCrypt 哈希还原密码？
6. JWT 是加密还是签名？payload 能否被用户看到？
7. `iss`、`sub`、`iat`、`exp` 分别代表什么？
8. 为什么账号禁用后不能只等待 token 自然过期？
9. 401 和 403 的区别是什么？
10. 为什么 JWT 密钥不能写入 Git？
11. 为什么前端参数校验不能代替后端校验？
12. 真实业务还需要怎样防止暴力登录和用户枚举？

## 13. 下一阶段建议

下一阶段可以实现地点管理：

```text
地点 Entity/Mapper
  -> 地点公开查询
  -> MERCHANT/ADMIN 创建地点
  -> 商家资源归属校验
  -> USER/MERCHANT/ADMIN 权限测试
```

对应学习记录建议为：

```text
knowledge/03-venue-management-and-rbac.md
```
