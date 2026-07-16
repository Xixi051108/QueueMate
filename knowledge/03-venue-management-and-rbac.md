# QueueMate 学习记录 03：地点管理与 RBAC

> 当前阶段：地点管理最小闭环已实现，自动化测试已覆盖公开查询、角色权限和商家资源隔离

## 1. 本阶段完成目标

本阶段实现五个接口：

```http
GET   /api/v1/venues
GET   /api/v1/venues/{id}
POST  /api/v1/venues
PUT   /api/v1/venues/{id}
PATCH /api/v1/venues/{id}/status
```

地点列表和详情允许匿名访问。创建、修改和启停要求 `MERCHANT` 或 `ADMIN`，并在 Service 层继续校验资源归属。

## 2. 为什么角色判断和资源归属要分两层

RBAC 只能回答“这个角色能不能进入某类接口”：

```text
USER -> 不可管理地点
MERCHANT -> 可以管理地点
ADMIN -> 可以管理全部地点
```

但 `MERCHANT` 能进入更新接口，不代表可以更新任意地点。因此还需要对象级校验：

```text
venue.merchantId == currentUser.id
```

不满足时返回：

```json
{
  "code": "RESOURCE_NOT_OWNED",
  "message": "只能操作自己名下的地点",
  "data": null
}
```

Spring Security 的 `@PreAuthorize` 负责角色入口，`VenueService` 负责资源归属。即使 Service 被其他 Controller 或任务调用，也不会绕过核心权限规则。

## 3. 创建地点时如何确定所属商家

- `MERCHANT` 创建地点时始终使用当前登录用户 ID，不能靠请求体冒充其他商家。
- `ADMIN` 创建地点时必须指定 `merchantId`。
- 管理员指定的账号必须存在、角色为 `MERCHANT` 且状态为 `ACTIVE`。

这样既支持商家自助创建，又支持管理员代建，同时避免客户端直接控制资源归属。

## 4. 地点名称为什么有两层重复保护

数据库已有唯一索引：

```text
uk_venues_name_merchant (name, merchant_id)
```

Service 先查询同名地点，以便返回清晰的 `409 / VENUE_NAME_EXISTS`。并发请求仍可能同时通过查询，所以插入和更新时还要捕获数据库唯一键冲突。数据库约束是最终防线。

## 5. 地点查询设计

首版不做分页，使用轻量列表查询，支持：

- 按 `category` 精确筛选。
- 按 `status` 精确筛选。
- 使用 `keyword` 模糊匹配名称、描述和模拟地址。
- 按地点 ID 升序返回，保证结果顺序稳定，便于接口测试断言。
- 响应中的 `id` 和 `merchantId` 序列化为字符串，避免 MyBatis-Plus 雪花 ID 超出 JavaScript 安全整数范围后发生精度丢失。

数据量扩大后，再把响应升级为分页结构，避免首版提前引入不必要复杂度。

## 6. 参数与状态边界

- 地点名称必填，最多 100 个字符。
- 描述最多 500 个字符，模拟地址最多 255 个字符。
- 排队和预约开关必须明确传入布尔值。
- 默认价格不得小于 0，最多 8 位整数和 2 位小数。
- 新地点固定为 `ACTIVE`。
- 状态接口只接受 `ACTIVE` 和 `INACTIVE`。
- 更新基本信息时不能顺便修改归属商家或状态。

## 7. 401、403 和资源错误

| 场景 | HTTP | 错误码 |
| --- | --- | --- |
| 未登录调用地点管理接口 | 401 | `AUTH_UNAUTHORIZED` |
| USER 调用地点管理接口 | 403 | `AUTH_FORBIDDEN` |
| 商家操作其他商家的地点 | 403 | `RESOURCE_NOT_OWNED` |
| 地点不存在 | 404 | `VENUE_NOT_FOUND` |
| 同一商家地点名称重复 | 409 | `VENUE_NAME_EXISTS` |

引入方法级权限后发现，`AccessDeniedException` 原先会落入通用异常处理并返回 500。本阶段增加了明确的 403 映射，使 URL 级和方法级拒绝保持统一 JSON 响应。

## 8. 已完成的测试

`VenueServiceTest` 共 11 个测试，覆盖：

- 地点列表映射。
- 商家创建时强制绑定本人。
- 管理员为有效商家创建地点。
- 管理员缺少商家 ID 被拒绝。
- USER 不能创建地点。
- 商家修改自己的地点。
- 商家不能修改其他商家的地点。
- 管理员可停用任意地点。
- 地点不存在返回 404。
- 同一商家同名地点冲突。
- 可选文本标准化。

`VenueControllerSecurityTest` 共 5 个测试，覆盖匿名公开查询、匿名创建 401、USER 创建 403、MERCHANT 创建成功和请求参数错误。

当前全量 Maven 测试为 27 个，全部通过。

真实 HTTP + MySQL 回归还验证了公开列表和详情、非法筛选、USER 禁止创建、商家归属强制绑定、同名冲突、跨商家越权和管理员停用。回归生成的临时地点已按精确 ID 删除，并确认测试数据残留数量为 0。

## 9. Postman 资产

地点集合新增：

- 公开地点列表和详情。
- USER 创建地点被拒绝。
- 商家登录并保存 token。
- 商家创建、更新和停用自己名下的地点。

创建请求使用动态名称并保存地点 ID。执行真实集合后需要清理生成的地点数据。当前只验证了集合 JSON 结构，未执行 Newman，因此不能标记 Newman 已通过。

## 10. 下一阶段建议

下一阶段实现固定预约时段：

```text
BookingSlot Entity/Mapper
  -> 公开查询地点时段
  -> 商家/管理员创建时段
  -> 地点归属复用
  -> 容量、时间范围和重复时段校验
  -> 为并发预约防超卖准备原子更新
```
