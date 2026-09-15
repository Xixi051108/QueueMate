from __future__ import annotations

import html
import re
import textwrap
from pathlib import Path

from reportlab.lib import colors
from reportlab.lib.enums import TA_CENTER, TA_LEFT
from reportlab.lib.pagesizes import A4
from reportlab.lib.styles import ParagraphStyle, getSampleStyleSheet
from reportlab.lib.units import mm
from reportlab.pdfbase import pdfmetrics
from reportlab.pdfbase.ttfonts import TTFont
from reportlab.platypus import (
    BaseDocTemplate,
    Flowable,
    Frame,
    KeepTogether,
    PageBreak,
    PageTemplate,
    Paragraph,
    Spacer,
    Table,
    TableStyle,
    XPreformatted,
)
from reportlab.platypus.tableofcontents import TableOfContents


ROOT = Path(__file__).resolve().parents[1]
OUTPUT = ROOT / "output" / "pdf" / "QueueMate-44-lessons-complete-guide.pdf"
KNOWLEDGE = ROOT / "knowledge"

PAGE_W, PAGE_H = A4
MARGIN_X = 18 * mm
MARGIN_TOP = 18 * mm
MARGIN_BOTTOM = 17 * mm
CONTENT_W = PAGE_W - 2 * MARGIN_X

BLUE = colors.HexColor("#1769AA")
BLUE_DARK = colors.HexColor("#123B5D")
BLUE_PALE = colors.HexColor("#EAF4FB")
INK = colors.HexColor("#243746")
MUTED = colors.HexColor("#617483")
LINE = colors.HexColor("#CBDDE8")
PAPER = colors.HexColor("#F8FBFD")
ORANGE = colors.HexColor("#F28C45")
GREEN = colors.HexColor("#2E8B67")
RED = colors.HexColor("#C64B4B")


def register_fonts() -> None:
    candidates = [
        ("QM", Path("C:/Windows/Fonts/msyh.ttc")),
        ("QM-Bold", Path("C:/Windows/Fonts/msyhbd.ttc")),
    ]
    for name, path in candidates:
        if path.exists():
            pdfmetrics.registerFont(TTFont(name, str(path)))
    if "QM" not in pdfmetrics.getRegisteredFontNames():
        fallback = Path("C:/Windows/Fonts/simhei.ttf")
        pdfmetrics.registerFont(TTFont("QM", str(fallback)))
        pdfmetrics.registerFont(TTFont("QM-Bold", str(fallback)))


register_fonts()


STYLES = getSampleStyleSheet()
STYLES.add(ParagraphStyle(
    name="CoverTitle", fontName="QM-Bold", fontSize=28, leading=38,
    textColor=BLUE_DARK, alignment=TA_CENTER, spaceAfter=10 * mm,
))
STYLES.add(ParagraphStyle(
    name="CoverSub", fontName="QM", fontSize=12, leading=20,
    textColor=MUTED, alignment=TA_CENTER,
))
STYLES.add(ParagraphStyle(
    name="H1", fontName="QM-Bold", fontSize=20, leading=28,
    textColor=BLUE_DARK, spaceBefore=4 * mm, spaceAfter=4 * mm,
))
STYLES.add(ParagraphStyle(
    name="H2", fontName="QM-Bold", fontSize=15, leading=22,
    textColor=BLUE, spaceBefore=4 * mm, spaceAfter=3 * mm,
))
STYLES.add(ParagraphStyle(
    name="H3", fontName="QM-Bold", fontSize=11.5, leading=17,
    textColor=INK, spaceBefore=3 * mm, spaceAfter=1.5 * mm,
))
STYLES.add(ParagraphStyle(
    name="BodyCN", fontName="QM", fontSize=9.2, leading=15.2,
    textColor=INK, spaceAfter=2.2 * mm, allowWidows=0, allowOrphans=0,
))
STYLES.add(ParagraphStyle(
    name="SmallCN", fontName="QM", fontSize=7.8, leading=12,
    textColor=MUTED, spaceAfter=1.5 * mm,
))
STYLES.add(ParagraphStyle(
    name="BulletCN", parent=STYLES["BodyCN"], leftIndent=5 * mm,
    firstLineIndent=-3.5 * mm, bulletIndent=1.2 * mm, spaceAfter=1.2 * mm,
))
STYLES.add(ParagraphStyle(
    name="NumberCN", parent=STYLES["BodyCN"], leftIndent=6 * mm,
    firstLineIndent=-4 * mm, spaceAfter=1.2 * mm,
))
STYLES.add(ParagraphStyle(
    name="BoxTitle", fontName="QM-Bold", fontSize=10, leading=15,
    textColor=BLUE_DARK, spaceAfter=1.5 * mm,
))
STYLES.add(ParagraphStyle(
    name="CodeCN", fontName="QM", fontSize=7.4, leading=11,
    textColor=colors.HexColor("#163247"), leftIndent=3 * mm,
    rightIndent=3 * mm, borderColor=LINE, borderWidth=0.6,
    borderPadding=3 * mm, backColor=colors.HexColor("#F1F6F9"),
    spaceBefore=1.5 * mm, spaceAfter=2.5 * mm,
))
STYLES.add(ParagraphStyle(
    name="TableCell", fontName="QM", fontSize=7.2, leading=10.5,
    textColor=INK,
))
STYLES.add(ParagraphStyle(
    name="TableHead", fontName="QM-Bold", fontSize=7.4, leading=10.5,
    textColor=colors.white, alignment=TA_CENTER,
))
STYLES.add(ParagraphStyle(
    name="TOC0", fontName="QM-Bold", fontSize=10.5, leading=16,
    textColor=BLUE_DARK, leftIndent=0,
))
STYLES.add(ParagraphStyle(
    name="TOC1", fontName="QM", fontSize=8.6, leading=13.2,
    textColor=INK, leftIndent=7 * mm,
))


MODULES = [
    ("模块 0", "项目全景与学习方法", "先知道系统解决什么问题、由哪些部分组成，避免一开始就陷入单个类和单条 SQL。"),
    ("模块 1", "本地运行、网络与一次请求", "把浏览器、Vite、Spring Boot 与 MySQL 串成可观察的运行链路。"),
    ("模块 2", "MySQL 数据模型与 SQL 基础", "理解数据怎样落表，以及数据库约束为什么是并发情况下的最后防线。"),
    ("模块 3", "Java 与 Spring Boot 分层", "掌握对象、依赖注入和 Controller-Service-Mapper 的职责边界。"),
    ("模块 4", "登录、JWT、Spring Security 与 RBAC", "弄清身份、角色和资源归属三道安全检查。"),
    ("模块 5", "核心业务模块与状态机", "沿着商家、地点、预约、钱包、消费码与排队理解真实业务规则。"),
    ("模块 6", "事务、并发与数据一致性", "解释为什么并发请求不会超卖、重复扣款或生成重复号码。"),
    ("模块 7", "Vue3 前端与响应式页面", "理解页面状态、路由、Axios 与响应式布局如何共同工作。"),
    ("模块 8", "自动化测试体系", "区分单元、接口、UI 和性能测试的职责，并理解测试隔离。"),
    ("模块 9", "Git、GitHub Actions 与持续集成", "把本地修改变成可追踪提交，并让云端自动重复验证。"),
    ("模块 10", "性能工程、排障与工程质量", "用数据和分层证据定位问题，诚实识别当前系统边界。"),
    ("模块 11", "简历表达与模拟面试", "把真实实现、验证证据、取舍和边界组织成可信的技术表达。"),
]


LESSONS = [
    dict(no="0.1", module=0, title="用最直白的话讲清 QueueMate 是什么",
         why="如果连业务对象和角色都没有建立，后面的 JWT、事务和并发只会变成孤立术语。本课先建立‘谁在什么场景下做什么’的最小业务地图。",
         concepts=["业务链：预约是提前占用未来时段；排队是到店前后领取当天号码，两者互不强制绑定。", "角色：USER 发起服务，MERCHANT 提供并处理服务，ADMIN 维护平台秩序。", "CRUD：Create、Read、Update、Delete 的缩写；QueueMate 不只是 CRUD，因为它还有权限、状态机、并发和自动化验证。"],
         files=["README.md - 项目目标、MVP 功能与非目标", "docs/requirement.md - 角色、范围和验收边界", "frontend/queuemate-web/src/router/index.js - 不同角色可进入的页面"],
         flow=["访客浏览地点", "普通用户登录后预约或取号", "商家维护地点、时段并处理号码/消费码", "管理员审核商家并执行平台级操作"],
         pitfalls=["把预约和排队说成同一流程", "把模拟钱包说成真实支付", "只报技术栈，不说明业务规则"],
         checks=["用 90 秒说清角色、两条业务链和技术价值", "指出至少两个非目标"]),
    dict(no="0.2", module=0, title="认识仓库目录和完整架构",
         why="真实排障必须知道问题可能在页面、接口、业务、SQL、测试还是 CI。目录结构就是系统职责的地图。",
         concepts=["Monorepo：前端、后端、SQL、测试和文档位于同一 Git 仓库。", "前后端分离：浏览器页面通过 HTTP API 调用后端，而不是由后端直接拼 HTML。", "调用链：一次操作按时间依次经过的组件集合。"],
         files=["frontend/queuemate-web - Vue 页面与接口调用", "backend/queuemate-server - Spring Boot API 与业务", "sql - 表结构、模拟数据和迁移", "tests 与 .github/workflows - 本地回归和云端持续集成"],
         flow=["VenueDetailView 收集预约动作", "api.js/http.js 生成 HTTP 请求", "Security 与 Controller 接收请求", "Service 编排规则和事务", "Mapper 执行 SQL，MySQL 持久化", "响应返回页面并显示结果"],
         pitfalls=["把 Controller 当成所有业务逻辑的容器", "把 Mapper 说成网络请求", "把页面路由 /bookings 与 API /api/v1/bookings 混淆"],
         checks=["不看资料画出前端到数据库链路", "为每个顶层目录说出职责"]),
    dict(no="1.1", module=1, title="进程、端口和 localhost",
         why="页面打不开时，先判断服务有没有运行和监听，能避免把网络连接失败误判成代码业务错误。",
         concepts=["进程：程序正在运行的实例；PID 是操作系统分配的进程编号。", "端口：主机上网络服务的逻辑入口；配置了端口不等于已经监听。", "localhost：发起连接的程序所在机器；手机里的 localhost 指手机自己。"],
         files=["frontend/queuemate-web/vite.config.js - Vite 5173 与 /api 代理", "backend/queuemate-server/src/main/resources/application.yml - 后端 8080 与数据库连接", "frontend/queuemate-web/package.json - Vite 启动命令"],
         flow=["浏览器临时端口连接 Vite 5173", "Vite 代理连接 Spring Boot 8080", "Java 通过 JDBC 驱动连接 mysqld 3306"],
         pitfalls=["3306 的监听者是 mysqld，不是 SQL", "5173 被占用时 Vite 可能回退到其他端口", "0.0.0.0 是监听范围，不是通常的访问目标"],
         checks=["解释 5173、8080、3306 分别由谁监听", "区分 ERR_CONNECTION_REFUSED 与 HTTP 500"]),
    dict(no="1.2", module=1, title="HTTP 请求、响应、JSON 和状态码",
         why="前后端联调的证据都在请求方法、地址、头、请求体、状态码和响应体中。",
         concepts=["HTTP 请求/响应：客户端发出方法、地址、头和可选正文；服务器返回状态、头和正文。", "JSON：使用对象、数组、字符串、数字、布尔值和 null 表达结构化数据。", "状态码与业务码：HTTP 码描述通用结果类别，ApiResponse.code 描述 QueueMate 的具体业务原因。"],
         files=["frontend/queuemate-web/src/services/http.js - baseURL、JWT 与错误归一化", "frontend/queuemate-web/src/services/api.js - GET/POST/PATCH 方法", "common/api/ApiResponse.java 与 common/exception/GlobalExceptionHandler.java"],
         flow=["POST /api/v1/bookings 携带 JSON 与 Bearer token", "Security 验证身份和角色", "Controller 反序列化并校验", "Service 成功或抛出业务异常", "成功返回 201/0；冲突返回 409/具体业务码", "Axios 成功提取 data，失败生成统一 Error"],
         pitfalls=["把 HTTP 201 与业务码 0 当成同一个值", "没有 JWT 时先得到 401，参数校验尚未运行", "网络失败没有 HTTP 响应，前端 status 为 0"],
         checks=["准确解释 200/201/400/401/403/404/409/500", "用 Network 面板拆解一次请求"]),
    dict(no="1.3", module=1, title="从登录按钮追一次完整请求",
         why="登录横跨表单、HTTP、密码校验、JWT、数据库与前端会话，是理解全栈链路的最佳样本。",
         concepts=["序列化/反序列化：Java 对象与 JSON 文本之间的转换。", "密码哈希校验：对输入密码执行同类算法，与数据库哈希比较，不解密原密码。", "会话状态：当前前端把 token 和用户信息放入 localStorage，并通过响应式状态使用。"],
         files=["views/LoginView.vue", "services/api.js 与 services/http.js", "AuthController.java -> AuthService.java -> UserMapper.java", "JwtTokenService.java 与 state/auth.js"],
         flow=["提交用户名密码", "POST /auth/login", "AuthService 查询用户并校验状态/密码", "JwtTokenService 签发 token", "LoginResponse 返回 token 与 user", "authState.setSession 保存并跳转角色首页"],
         pitfalls=["认为 JWT 是每次请求重新登录取得", "只检查前端登录成功提示，不看 Network", "登录失败时向外暴露‘用户名不存在’或‘密码错误’的差异"],
         checks=["从 LoginView 逐文件追到 UserMapper", "说明登录 401 与受保护接口 401 的共同点和差异"]),
    dict(no="2.1", module=2, title="表、行、列、主键、外键",
         why="Service 最终操作的不是抽象业务，而是一行行有关系的数据；先懂关系模型才能追 SQL。",
         concepts=["表/行/列：表描述一类实体，行是一条记录，列是记录的属性。", "主键：唯一标识一行；QueueMate 多数表使用 BIGINT id。", "外键：保证引用的父记录存在，保护表之间的引用完整性。"],
         files=["sql/schema.sql - 11 张表", "docs/db_design.md - 关系说明", "各模块 Entity 类 - Java 字段到表列的映射"],
         flow=["users 与 user_roles 描述账号及多角色", "venues 属于商家", "booking_slots 属于地点", "bookings 关联用户、地点和时段", "wallets/wallet_transactions 记录余额", "booking_vouchers、queue_tickets 与 queue_daily_sequences 支撑履约和排队"],
         pitfalls=["把主键等同于业务编号 booking_no", "删除父记录前忽略外键顺序", "把 Entity 当成可以直接接收所有外部请求的 DTO"],
         checks=["说出 11 张表的职责", "从 booking.id 关联到用户、地点、时段和消费码"]),
    dict(no="2.2", module=2, title="索引、唯一约束、检查约束",
         why="代码预检查在并发下可能同时通过，数据库约束负责最后一刻拒绝非法数据。",
         concepts=["普通索引：用额外存储换取查询速度，不自动阻止重复。", "唯一约束：禁止一组列出现重复组合。", "CHECK 约束：要求每行数据满足布尔条件，例如 reserved_count 不得超出 capacity。"],
         files=["schema.sql 中 users.username、消费码、业务流水唯一键", "bookings 的 active_slot_id 生成列与唯一键", "booking_slots 的 reserved_count CHECK"],
         flow=["Service 先检查并提供友好消息", "并发请求可能在检查后同时写入", "唯一键/CHECK 在数据库写入点拒绝非法结果", "后端捕获 DuplicateKeyException 转成稳定业务码"],
         pitfalls=["认为有索引就不会重复", "只依赖前端校验", "把所有唯一键冲突原样返回数据库异常"],
         checks=["用重复预约解释双层保护", "区分性能索引与一致性约束"]),
    dict(no="2.3", module=2, title="InnoDB、事务和 DECIMAL",
         why="付费预约同时影响容量、余额、预约、流水和消费码，任何一步失败都不能留下半套数据。",
         concepts=["InnoDB：MySQL 存储引擎，支持事务、行锁、崩溃恢复和外键。", "事务：一组操作一起提交或一起回滚；原子性只保证同一事务边界内的操作。", "DECIMAL：十进制定点数，适合金额；float/double 的二进制表示可能产生精度误差。"],
         files=["schema.sql 的 engine=InnoDB 与 DECIMAL 字段", "BookingService.create/cancel 的 @Transactional", "WalletService 的余额和流水更新"],
         flow=["开始事务", "占用名额并可能扣款", "写预约、流水和消费码", "全部成功提交", "任一步抛出运行时异常则整体回滚"],
         pitfalls=["把回滚说成额外退款", "用 double 保存金额", "误以为跨网络系统的所有操作自动加入本地数据库事务"],
         checks=["解释扣款成功但预约写入失败时的结果", "说明 wallets 与流水为何必须同事务"]),
    dict(no="3.1", module=3, title="Java 类、对象、接口、枚举和 record",
         why="QueueMate 源码大量使用 Entity、Request、Response、Mapper、Enum 和 record；分不清类型就无法阅读方法签名。",
         concepts=["类与对象：类是结构和行为的定义，对象是运行时实例。", "接口：声明能力和方法契约；MyBatis Mapper 接口由框架生成实现。", "枚举：有限状态集合；record：适合不可变数据载体，自动生成构造器和访问方法。"],
         files=["booking/Booking.java - Entity 类", "BookingStatus.java - 枚举", "BookingCreateRequest.java 与 BookingResponse.java - record DTO", "BookingMapper.java - 接口"],
         flow=["JSON 转 Request record", "Service 创建/读取 Entity 对象", "Mapper 接口持久化 Entity", "Entity 转 Response record", "Jackson 把 Response 转 JSON"],
         pitfalls=["把类名和对象变量名混为一谈", "认为接口不能运行所以 Mapper 没实现", "直接用 Entity 接外部请求导致可写字段失控"],
         checks=["任选一个包标注 Entity/Request/Response/Mapper/Enum", "解释 record 适合 DTO 的原因"]),
    dict(no="3.2", module=3, title="Spring Bean 与依赖注入",
         why="对象如果各自 new 依赖，配置、测试替换、事务代理和生命周期会难以统一。",
         concepts=["Bean：由 Spring 容器创建和管理的对象。", "依赖注入：对象声明自己需要什么，由容器提供实例。", "构造器注入：依赖通过构造器传入，便于保持必需依赖和单元测试替换。"],
         files=["@Service BookingService", "@RestController BookingController", "@Mapper BookingMapper", "BookingController(BookingService) 与 BookingService(...) 构造器"],
         flow=["启动时组件扫描发现注解", "Spring 创建 Mapper 代理", "创建 Service 并注入 Mapper", "创建 Controller 并注入 Service", "请求到达时复用这些 Bean"],
         pitfalls=["在 Service 内 new Mapper", "字段注入导致依赖隐藏", "忘记组件扫描范围或注解，启动时报找不到 Bean"],
         checks=["解释 Service 为什么不自己 new Mapper", "从构造器列出 BookingService 的五个依赖"]),
    dict(no="3.3", module=3, title="Controller、Service、Mapper 分层",
         why="分层让 HTTP、业务规则和 SQL 各自可读、可测、可替换，避免一个方法承担全部责任。",
         concepts=["Controller：处理 Web 契约，例如路径、方法、参数和响应。", "Service：组织业务规则、状态变化、事务和跨模块调用。", "Mapper：把 Java 调用转换为数据库查询与更新。"],
         files=["VenueController.java", "VenueService.java", "VenueMapper.java", "VenueCreateRequest.java 与 VenueResponse.java"],
         flow=["GET /venues/{id} 匹配 Controller", "Controller 调 Service.get", "Service 校验资源存在/规则", "Mapper 读取 venues", "Service 转换 Response", "Controller 用 ApiResponse 包装"],
         pitfalls=["Controller 堆积复杂业务", "Mapper 决定完整流程", "前端隐藏按钮代替后端权限"],
         checks=["追踪地点查询并标注每层输入输出", "给新需求判断应修改哪一层"]),
    dict(no="3.4", module=3, title="参数校验、统一响应和异常处理",
         why="客户端需要稳定的成功结构和可预测错误，而不是每个接口返回不同形状或暴露堆栈。",
         concepts=["Jakarta Validation：用 @NotNull、@Positive、@Size 等注解描述输入约束。", "统一响应：ApiResponse 固定 code、message、data。", "全局异常处理：@RestControllerAdvice 将异常集中转换为 HTTP 状态与 JSON。"],
         files=["BookingCreateRequest.java", "ApiResponse.java", "BusinessException.java", "GlobalExceptionHandler.java"],
         flow=["请求体反序列化", "@Valid 执行字段校验", "参数异常转 400/PARAM_INVALID", "业务异常携带 HttpStatus 和 code", "未知异常记录日志并返回 500/SYSTEM_ERROR"],
         pitfalls=["把 400 当成 Service 返回", "把所有异常吞掉并返回 200", "把数据库异常或堆栈直接暴露给前端"],
         checks=["预测 slotId=-3 的结束位置", "解释 HTTP 状态码和业务码的分工"]),
    dict(no="4.1", module=4, title="注册、密码哈希与登录",
         why="账号系统必须既保护密码，又保证用户和钱包初始化一致，并避免通过错误消息泄露账号是否存在。",
         concepts=["哈希：单向摘要；密码验证是重新计算并比较，不是解密。", "盐：让相同密码产生不同哈希，BCrypt 会管理盐和成本。", "账户枚举：攻击者通过差异化错误判断用户名是否存在。"],
         files=["AuthController.java", "AuthService.java", "RegisterRequest/LoginRequest", "users、wallets 与 username 唯一键"],
         flow=["注册参数校验", "BCrypt 编码密码", "同事务插入 USER/ACTIVE 用户与零余额钱包", "登录查询用户并检查状态", "matches 校验密码", "签发 JWT"],
         pitfalls=["保存或记录明文密码", "注册只写用户不写钱包", "登录分别返回‘用户不存在’和‘密码错误’"],
         checks=["解释数据库为何无法恢复原密码", "说明注册事务失败时钱包和用户的结果"]),
    dict(no="4.2", module=4, title="JWT 的结构、签名、过期与局限",
         why="JWT 让后端无需服务器 Session 也能识别请求，但签名不等于加密，过期和撤销也有明确边界。",
         concepts=["JWT：由 header、payload、signature 三段组成的令牌。", "签名：防止内容被篡改，不负责隐藏 payload。", "无状态：服务端不为每个登录保存 Session；每次请求携带 token。"],
         files=["JwtTokenService.java", "JwtProperties.java", "application.yml 的 JWT 配置", "LoginResponse.java"],
         flow=["登录时用密钥签发", "payload 保存用户标识和角色等声明", "设置过期时间", "请求时解析并验证签名/过期", "当前项目再查数据库刷新实际用户状态"],
         pitfalls=["把 JWT 说成加密用户数据", "把敏感密码放进 payload", "忽略当前项目没有 Refresh Token、黑名单和密钥轮换"],
         checks=["解释三段各做什么", "说明 token 泄露后的风险和当前边界"]),
    dict(no="4.3", module=4, title="Security 过滤器链和登录态",
         why="Controller 之前已经发生 JWT 解析和身份建立；不了解过滤器顺序就会误判为什么代码没有进入断点。",
         concepts=["过滤器链：请求进入 Controller 前后依次执行的一组 Web 过滤器。", "SecurityContext：保存本次请求已认证身份的上下文。", "AuthenticationPrincipal：Controller 从安全上下文取得当前用户。"],
         files=["SecurityConfig.java", "JwtAuthenticationFilter.java", "RestAuthenticationEntryPoint.java", "AuthenticatedUser.java"],
         flow=["读取 Authorization", "确认 Bearer 前缀", "解析 JWT", "查询实际用户和角色", "构造 Authentication 放入 SecurityContext", "权限通过后进入 Controller"],
         pitfalls=["Bearer 本身被当成 token", "token 无效仍继续当匿名用户执行写接口", "401 时不清理前端过期会话"],
         checks=["画出 Authorization 到 principal 的链路", "说明公开接口为何可跳过登录要求"]),
    dict(no="4.4", module=4, title="RBAC、401/403 与资源归属",
         why="有 MERCHANT 角色只说明是某个商家，不能自动获得修改所有商家地点的权力。",
         concepts=["RBAC：Role-Based Access Control，按角色授予能力。", "资源归属：进一步检查目标数据是否属于当前账号。", "最小权限：只授予完成职责所需的权限范围。"],
         files=["@PreAuthorize 注解", "VenueService.requireOwnerOrAdmin", "SecurityConfig 的公开/受保护路径", "前端 router meta.roles"],
         flow=["无身份 -> 401", "身份有效但角色不符 -> 403", "角色允许后加载目标地点", "MERCHANT 校验 merchant_id 是否等于当前用户", "ADMIN 按规则可越过归属检查"],
         pitfalls=["只在前端隐藏按钮", "把角色权限与数据归属混成一次判断", "为防信息泄露随意把 403 改成 404 而不统一策略"],
         checks=["解释商家修改别人地点为何是 403", "区分角色检查和所有者检查"]),
    dict(no="5.1", module=5, title="商家入驻与多角色账号",
         why="同一账号可能既是顾客又是商家，角色不是只能保存在 users.role 的单值标签。",
         concepts=["多对多关系：一个用户有多个角色，一个角色属于多个用户。", "审核状态机：PENDING 只能转 APPROVED 或 REJECTED。", "悲观锁：审核时锁住申请行，阻止并发重复处理。"],
         files=["user_roles 与 merchant_applications 表", "MerchantApplicationService/Mapper", "MerchantApplicationView.vue 与 AdminMerchantApplicationsView.vue"],
         flow=["USER 提交申请", "唯一生成列阻止同一人多个待审申请", "ADMIN 锁定并审核", "通过后 insert ignore 授予 MERCHANT", "用户刷新后可切换工作区"],
         pitfalls=["审核通过后删除 USER 角色", "重复审批同一申请", "误以为通过审核就自动创建并发布门店"],
         checks=["画出 PENDING 状态转换", "说明 user_roles 解决什么问题"]),
    dict(no="5.2", module=5, title="地点与预约时段",
         why="预约不是直接对地点计数，而是对地点下某一天、某段时间、某个容量的 slot 占位。",
         concepts=["Venue：提供服务的地点，保存归属、启停和是否支持预约/排队。", "BookingSlot：具体日期、开始/结束时间、容量、价格和开放状态。", "聚合关系：一个地点包含多个时段，时段不能脱离地点规则。"],
         files=["VenueService/Controller", "BookingSlotService/Controller", "booking_slots 表", "OperatorVenueView.vue"],
         flow=["商家创建地点", "启用 bookingEnabled", "创建未来时段并校验时间/容量/价格", "公开查询只返回符合日期和状态条件的时段", "商家可 OPEN/CLOSED"],
         pitfalls=["只校验 slot 状态不校验 venue 状态", "首版只防完全相同时段，不防部分重叠", "创建后当前只支持开关，不支持编辑核心字段"],
         checks=["区分 venueId、slotId、bookingId", "说明地点停用对预约的影响"]),
    dict(no="5.3", module=5, title="免费预约与取消",
         why="免费预约已经包含重复保护、容量竞争、状态更新和取消回补，是理解核心一致性的最小闭环。",
         concepts=["有效预约：BOOKED 状态占用名额；取消历史保留但不继续占位。", "条件更新：把前置状态写进 WHERE，只有状态仍符合时才更新。", "取消回补：预约成功占用一个名额，合法取消必须释放一个。"],
         files=["BookingService.create/cancel", "BookingSlotMapper.reserveCapacity/releaseCapacity", "BookingMapper 条件更新", "BookingsView.vue"],
         flow=["检查用户/时段/地点/重复", "原子占用容量", "保存 BOOKED/NOT_REQUIRED 预约", "取消时条件改为 CANCELLED", "reserved_count 原子减一", "取消后允许重新预约"],
         pitfalls=["先插预约再占容量而不设事务", "重复取消导致多次释放", "删除预约历史来允许重新预约"],
         checks=["画出 BOOKED -> CANCELLED", "解释取消两次为何一次成功一次 409"]),
    dict(no="5.4", module=5, title="付费预约、钱包和消费码",
         why="付费预约把容量、资金记录和履约凭证绑在一个事务中，是系统最复杂的业务链。",
         concepts=["钱包快照与流水：balance 方便快速读取，transaction 解释每次变化。", "消费凭证：把‘已付款预约’与‘到店履约’独立建模。", "业务唯一键：biz_type + biz_no + type 阻止同一业务重复扣款或退款。"],
         files=["WalletService/Mapper", "BookingVoucherService/Mapper", "BookingService.create/cancel", "wallet_transactions 与 booking_vouchers 表"],
         flow=["占名额", "锁钱包并检查余额", "条件扣款并写 PAY 流水", "保存 PAID 预约", "生成 AVAILABLE 消费码", "取消则退款并将凭证置 VOID；核销则预约 FULFILLED"],
         pitfalls=["把钱包当真实支付平台", "只更新余额不写流水", "核销和退款不加锁导致同一凭证走向两个终态"],
         checks=["追踪余额 50 -> 30 -> 50", "解释为什么消费码独立建表"]),
    dict(no="5.5", module=5, title="排队与繁忙统计",
         why="排队是按地点和日期生成号码并推进状态；统计则把预约与排队事件聚合成可读趋势。",
         concepts=["排队状态机：WAITING -> CALLED -> COMPLETED/MISSED。", "每日序列：每个地点每天独立维护 last_no。", "聚合查询：按小时分组并计算预约数、取号数或热度分数。"],
         files=["QueueTicketService/Mapper", "QueueSequenceMapper", "BusyHoursService/Mapper", "QueueView.vue 与 OperatorVenueView.vue"],
         flow=["用户取号获得 WAITING", "商家叫号变 CALLED", "服务完成或过号进入终态", "统计 SQL 按地点/日期/小时合并预约与排队数量"],
         pitfalls=["允许 WAITING 直接 COMPLETED", "全平台共用一个号码序列", "把聚合统计当作实时排队真相"],
         checks=["画出合法/非法状态转换", "解释号码为何按地点和日期隔离"]),
    dict(no="6.1", module=6, title="@Transactional 与回滚边界",
         why="多个数据库写操作只有在同一事务中，才能避免名额已占、钱已扣但预约不存在。",
         concepts=["事务边界：从带 @Transactional 的公开方法进入，到正常返回提交或异常退出回滚。", "传播与代理：Spring 通常通过代理开启事务；同类内部自调用可能绕过代理。", "回滚规则：默认 RuntimeException/Error 触发回滚，受检异常需按配置处理。"],
         files=["BookingService.create/cancel", "WalletService 的写方法", "MerchantApplicationService 审核", "测试中的异常回滚场景"],
         flow=["代理开启事务", "多个 Mapper 共用当前连接/事务", "正常返回 commit", "业务或运行时异常 rollback", "调用者收到错误响应"],
         pitfalls=["捕获异常后不再抛出导致事务提交", "私有方法加 @Transactional 期待生效", "把外部 HTTP 调用误认为自动参与 MySQL 事务"],
         checks=["指出付费预约事务内的所有写操作", "解释回滚与补偿的区别"]),
    dict(no="6.2", module=6, title="原子 SQL 防预约超卖",
         why="多个请求可能同时读到最后一个名额；必须让数据库在一次更新中同时检查和加一。",
         concepts=["竞态条件：结果依赖多个线程不可预测的执行时序。", "原子操作：对外表现为不可分割的检查与更新。", "受影响行数：条件 UPDATE 返回 1 表示成功，0 表示条件已不满足。"],
         files=["BookingSlotMapper.reserveCapacity", "BookingService.throwCapacityFailure", "tests/jmeter/concurrent-booking.jmx"],
         flow=["12 个用户同时提交", "每条 UPDATE 要求 reserved_count < capacity", "MySQL 串行处理冲突写入", "只有前三条更新 1 行", "其余返回 0 并被解释为 BOOKING_SLOT_FULL"],
         pitfalls=["先 SELECT 剩余名额再普通 +1", "把前端按钮禁用当并发保护", "只看 JMeter 进程码不核对业务成功数"],
         checks=["手画两个线程抢最后一位的错误时序", "解释 3 成功/9 冲突为何是正确结果"]),
    dict(no="6.3", module=6, title="行锁、余额原子扣减与幂等",
         why="同一钱包或消费码被并发操作时，需要锁、条件更新和唯一键共同保证只生效一次。",
         concepts=["SELECT ... FOR UPDATE：事务内读取并锁定目标行，其他写事务等待。", "条件扣减：UPDATE 同时要求 balance >= amount，受影响 0 行表示余额不足或状态不符。", "幂等：同一个业务请求重复到达时，不产生重复资金效果。"],
         files=["WalletMapper.selectByUserIdForUpdate/deductBalance", "WalletService", "wallet_transactions 唯一键", "BookingVoucherMapper 的 FOR UPDATE"],
         flow=["锁钱包得到稳定 balanceBefore", "条件扣减", "写带业务键的流水", "提交后释放锁", "重复请求被状态或唯一约束拒绝"],
         pitfalls=["只有 Java synchronized，无法覆盖多进程", "只有行锁，没有业务唯一键", "把接口返回相同理解为幂等的唯一定义"],
         checks=["解释锁与唯一键各防什么", "说明余额不足为何用条件 SQL 再兜底"]),
    dict(no="6.4", module=6, title="并发取号与状态条件更新",
         why="并发取号既要号码连续不重复，后续叫号也要防止两个操作者同时推进同一号码。",
         concepts=["Upsert：不存在则插入、存在则更新。", "LAST_INSERT_ID 技巧：在当前数据库连接中取回本次生成或更新的号码。", "Expected-to-target 更新：WHERE status=expected，SET status=target。"],
         files=["QueueSequenceMapper.next/currentConnectionValue", "queue_daily_sequences 唯一键", "QueueTicketMapper 状态更新", "QueueTicketService"],
         flow=["按 venue_id + date upsert 序列", "原子 last_no+1 并从同一连接读取", "插入 WAITING ticket", "叫号用 WAITING -> CALLED 条件更新", "并发第二次更新因状态变化返回 0"],
         pitfalls=["先查最大号码再 +1", "next 与 currentConnectionValue 不在同一连接", "无 expected status 的无条件覆盖"],
         checks=["解释 6 个并发用户为何得到连续唯一号码", "预测同一票并发叫号结果"]),
    dict(no="7.1", module=7, title="Vue 单文件组件、模板、响应式状态",
         why="页面不是静态 HTML；数据变化后 Vue 要自动重新渲染 loading、列表、错误和按钮状态。",
         concepts=["单文件组件：.vue 文件把 script、template、style 组织在一起。", "ref/reactive：让值或对象成为可追踪的响应式状态。", "computed：根据其他响应式值派生结果并缓存依赖关系。"],
         files=["VenueDetailView.vue", "WalletView.vue", "components/StatePanel.vue", "components/VenueCard.vue"],
         flow=["组件创建响应式状态", "onMounted 请求数据", "赋值触发依赖更新", "template 根据 v-if/v-for 渲染", "点击 @click 执行异步函数", "finally 恢复 loading/action 状态"],
         pitfalls=["忘记 ref 在 script 中使用 .value", "直接修改非响应式普通变量期待页面刷新", "异步失败不在 finally 恢复 loading"],
         checks=["在一个 View 中找出 ref/computed/event", "解释四态 loading/empty/error/success"]),
    dict(no="7.2", module=7, title="Router、路由守卫和多角色导航",
         why="单页应用需要把 URL 映射到组件，并在进入页面前处理登录要求和角色工作区。",
         concepts=["SPA：单页应用在不整页刷新时切换组件视图。", "路由守卫：导航发生前执行的检查，可重定向但不是后端安全边界。", "懒加载：访问路由时再加载对应页面代码。"],
         files=["router/index.js", "state/auth.js", "components/AppShell.vue", "LoginView.vue"],
         flow=["匹配 path 到 component", "读取 meta.requiresAuth/roles", "未登录跳 /login?redirect=原地址", "角色不符跳对应首页", "登录成功回到 redirect", "多角色账号切换 activeRole"],
         pitfalls=["把路由守卫当作后端鉴权", "重定向丢失原目标", "只保存单一 role 导致多身份失真"],
         checks=["追踪未登录访问 /wallet", "解释 ADMIN/MERCHANT 的默认首页"]),
    dict(no="7.3", module=7, title="Axios、统一错误处理和前后端联调",
         why="每个页面都手写 token、baseURL 和错误判断会重复且不一致，Axios 实例负责集中处理。",
         concepts=["Axios 实例：保存 baseURL、超时和默认头的 HTTP 客户端。", "拦截器：请求发出前或响应交给调用者前统一加工。", "同源代理：开发时浏览器请求 5173 的 /api，由 Vite 转到 8080。"],
         files=["services/http.js", "services/api.js", "vite.config.js", "各 View 的 try/catch/finally"],
         flow=["api.js 描述资源方法", "请求拦截器注入 Bearer token", "Vite 代理", "成功拦截器取 response.data.data", "失败拦截器生成 code/status/message", "401 清会话并带 redirect 登录"],
         pitfalls=["页面再次读取 result.data", "把 409 当网络错误", "VITE_API_BASE_URL 覆盖后遗漏 /api/v1"],
         checks=["解释组件最终拿到哪一层 data", "按 Network -> 后端日志 -> SQL 顺序排一次 401/409"]),
    dict(no="7.4", module=7, title="组件复用、设计 token 与移动端适配",
         why="统一组件和 token 能让视觉、状态和可访问性规则集中维护，而不是复制大量略有不同的 CSS。",
         concepts=["组件复用：通过 props、slots 和 events 组合通用 UI。", "设计 token：把颜色、间距、字号等设计决策命名为变量。", "响应式布局：根据可用宽度调整网格、按钮和信息层级。"],
         files=["components/AppShell/StatePanel/VenueCard/StickerBadge.vue", "styles/tokens.css 与 global.css", "design-system/MASTER.md"],
         flow=["token 定义语义颜色和尺寸", "共享组件引用 token", "页面组合组件", "媒体查询在 760px 等断点重排", "真实浏览器检查 1440/375/横屏/reduced-motion"],
         pitfalls=["每页硬编码不同颜色", "只缩小字体不重排布局", "装饰元素承载唯一状态信息", "触摸目标低于 44px"],
         checks=["从 token 追到一个组件样式", "解释移动端横向溢出的排查方法"]),
    dict(no="8.1", module=8, title="测试金字塔、断言和测试隔离",
         why="一种测试无法覆盖所有风险；快速的小测试与少量真实端到端测试要形成互补。",
         concepts=["测试金字塔：底层单元测试多而快，上层端到端测试少而真实。", "断言：把预期写成可自动判断的条件。", "测试隔离：每次运行拥有独立数据，不依赖上次残留或修改真实数据。"],
         files=["backend/src/test - 144 个后端测试", "tests/postman、tests/playwright、tests/jmeter", "docs/test_plan.md"],
         flow=["单元测试验证局部规则", "Controller 测试验证 HTTP/权限", "Newman 验证完整 API", "Playwright 验证浏览器流程", "JMeter 验证并发不变量", "CI 统一运行并保存报告"],
         pitfalls=["只追求测试数量", "用同一固定用户名导致重复运行失败", "断言只看 HTTP 200 不看业务数据"],
         checks=["为一个缺陷选择最合适测试层", "解释 runId 与清理的价值"]),
    dict(no="8.2", module=8, title="JUnit、Mockito 与 Security Test",
         why="后端规则需要快速、可重复验证，同时区分‘业务代码错’和‘HTTP/权限映射错’。",
         concepts=["JUnit：组织测试用例、生命周期和断言。", "Mockito：用可控替身代替依赖，验证调用与异常分支。", "MockMvc/Security Test：不启动真实浏览器也能测试 Controller、状态码和角色。"],
         files=["BookingServiceTest.java", "BookingControllerSecurityTest.java", "VenueControllerSecurityTest.java", "pom.xml 测试依赖"],
         flow=["Arrange 准备对象和 mock", "Act 调用方法或 MockMvc 请求", "Assert 检查返回/异常/调用", "Security Test 构造 USER/MERCHANT/ADMIN 身份", "Surefire 汇总结果"],
         pitfalls=["mock 掉被测对象本身", "只验证没有异常", "Service 单测误以为已经验证真实 SQL"],
         checks=["区分 ServiceTest 与 ControllerSecurityTest", "为余额不足设计 Arrange/Act/Assert"]),
    dict(no="8.3", module=8, title="Postman/Newman 接口回归",
         why="完整 API 回归需要真实 Spring Boot 和 MySQL，并可在命令行及 CI 中重复运行。",
         concepts=["Collection：按顺序组织请求、脚本和断言。", "Environment：保存 baseUrl、token、动态 ID 等运行变量。", "Newman：Postman 集合的命令行执行器。"],
         files=["tests/postman/QueueMate.postman_collection.json", "QueueMate.local.postman_environment.json", "package.json", "11-postman-newman-repeatable-regression.md"],
         flow=["生成唯一 runId", "注册/登录并保存 token", "创建地点、时段、预约等动态数据", "逐接口断言", "最后 ADMIN 调清理端点", "检查 remainingArtifacts=0 和 JSON 报告"],
         pitfalls=["使用 --bail 导致清理不执行", "普通 profile 下调用清理端点", "集合 JSON 可解析就声称回归通过"],
         checks=["解释 45 请求/99 断言的证据含义", "说明中断后如何补偿清理"]),
    dict(no="8.4", module=8, title="Playwright 真实浏览器 E2E",
         why="接口正确不代表表单、路由、提示和多角色浏览器操作能够组成可用流程。",
         concepts=["E2E：从用户界面入口到真实后端和数据库的端到端验证。", "Browser Context：彼此隔离的浏览器会话，可同时模拟用户和商家。", "Trace/截图/视频：失败时保留页面、网络和操作证据。"],
         files=["tests/playwright/playwright.config.js", "support/api-fixture.js", "support/ui-actions.js", "specs 下 6 条核心链路"],
         flow=["API fixture 创建独立数据", "浏览器执行真实登录和点击", "多 context 模拟角色协作", "断言 UI 与后端状态", "finally 调清理端点", "失败保留 trace/screenshots/video"],
         pitfalls=["固定 sleep 代替等待可观察状态", "多个角色共用同一 context", "只测 happy path 不验最终业务状态"],
         checks=["说明为何 E2E 数量少于单测", "从失败 trace 判断页面、网络还是接口问题"]),
    dict(no="8.5", module=8, title="JMeter 并发性能测试",
         why="防超卖必须通过真正同时发生的请求验证，串行调用无法暴露竞态。",
         concepts=["线程组：配置并发虚拟用户与执行步骤。", "同步定时器：让多个线程在同一屏障后同时发送关键请求。", "JTL 与百分位：JTL 保存每个采样；P90/P95 描述 90%/95% 请求不超过的时间。"],
         files=["tests/jmeter/concurrent-booking.jmx", "run.ps1", "write-summary-zh.ps1", "README.md"],
         flow=["创建容量 3 的时段和 12 用户", "各用户独立登录", "同步提交预约", "断言 3 个 201 与 9 个 409/FULL", "查询 reservedCount=3", "扫描 JTL 失败采样并清理"],
         pitfalls=["JMeter 进程返回 0 就认为业务通过", "只看平均值忽略尾延迟", "测试后残留 12 个用户和预约"],
         checks=["解释 56 个采样与 12 个预约请求的关系", "区分平均值、P90、P95、错误率"]),
    dict(no="9.1", module=9, title="Git 工作区、暂存区、提交和远程",
         why="版本控制既是回退保险，也是多人协作边界；错误的回退命令可能覆盖用户已有修改。",
         concepts=["工作区：当前磁盘文件；暂存区：下一次提交的候选快照；提交：带父关系和说明的不可变快照。", "分支：指向提交的可移动引用。", "远程：GitHub 等共享仓库；push 上传本地提交，fetch 获取远程引用。"],
         files=[".git 仓库元数据", ".gitignore", "HANDOFF.md 的安全流程", "README 中验证证据"],
         flow=["git status 看范围", "git diff 审查未暂存修改", "git add 精确暂存", "git diff --cached 复核", "git commit", "git push 并核对远程"],
         pitfalls=["未经确认 reset --hard", "把构建产物和密钥提交", "混入无关修改或覆盖他人工作"],
         checks=["解释三棵树模型", "给出安全提交前检查清单"]),
    dict(no="9.2", module=9, title="GitHub Actions 工作流",
         why="CI 把‘我本机能跑’升级为每次代码变化都在干净环境自动验证。",
         concepts=["Workflow：一个 YAML 自动化流程；trigger 决定何时执行。", "Job：在 Runner 上执行的一组 step；不同 job 可并行。", "Artifact：运行结束后保存的报告或构建产物，不等于 Git 提交。"],
         files=[".github/workflows/ci.yml", ".github/workflows/full-regression.yml"],
         flow=["push/PR 触发基础 CI", "checkout", "安装 Java/Node/pnpm 并利用缓存", "Maven test 与 Vite build", "always 上传报告", "全量流程另起 MySQL、后端、Newman、Playwright、JMeter"],
         pitfalls=["把 uses 与 run 混淆", "服务容器健康前就启动测试", "失败时不上传日志", "把 artifact 当部署"],
         checks=["指出 trigger/job/step/runner/service/artifact", "比较两条工作流的触发范围和成本"]),
    dict(no="9.3", module=9, title="用真实失败学习 CI 排障",
         why="CI 故障往往来自干净环境、版本和路径差异；要用日志和最小重现定位，而不是反复盲改。",
         concepts=["可复现性：锁文件、工具版本和命令在不同环境产生一致结果。", "工作目录：step 的相对路径起点。", "失败证据：退出码、日志、报告和上传产物共同描述故障。"],
         files=["ci.yml 的 pnpm 11.19.0", "full-regression.yml 的 JMeter 报告路径", "HANDOFF 3.14 与 4"],
         flow=["定位失败 job/step", "读取第一处根因而非最后一行", "对比本地与 Runner 版本/路径/环境变量", "本地模拟干净运行", "最小修复并再次触发", "保留报告证明恢复"],
         pitfalls=["pnpm 9/11 与锁文件不匹配", "JMeter 在父目录不存在时生成报告失败", "只看红叉不展开日志"],
         checks=["复盘两个真实故障的现象、根因、修复和证据", "解释为什么本地通过仍可能远程失败"]),
    dict(no="10.1", module=10, title="性能指标和瓶颈定位",
         why="单个‘快’或‘慢’没有意义；性能结论必须说明负载、分布、错误率和系统资源。",
         concepts=["吞吐量：单位时间完成的请求数。", "延迟分布：平均值可能掩盖慢请求，P90/P95 关注尾部。", "资源指标：CPU、内存、线程、连接池、锁等待和数据库负载用于解释为什么慢。"],
         files=["tests/jmeter 报告与 JTL", "write-summary-zh.ps1", "HikariPool/后端日志", "MySQL 慢查询与执行计划（扩展方向）"],
         flow=["固定测试环境与数据", "逐级增加并发", "同时观察延迟/吞吐/错误率", "关联后端和数据库资源", "定位瓶颈层", "修改后用同一基线复测"],
         pitfalls=["只报平均响应时间", "错误率高时仍讨论延迟优秀", "用一次本机结果外推生产容量"],
         checks=["解释 12 并发结果能证明什么、不能证明什么", "为 50/100/300 阶梯并发设计观察指标"]),
    dict(no="10.2", module=10, title="系统化排障",
         why="复杂系统故障不能靠猜；需要从用户可见现象逐层寻找第一处异常证据。",
         concepts=["分层定位：页面 -> Network -> 代理/端口 -> Security/Controller -> Service -> SQL -> 环境。", "最小复现：减少变量，只保留能稳定触发问题的步骤。", "相关性与因果：时间上同时发生的日志不一定是根因。"],
         files=["浏览器 DevTools", "http.js 错误归一化", "后端日志与 GlobalExceptionHandler", "Mapper SQL、CI 日志和测试报告"],
         flow=["记录准确现象和时间", "确认请求是否发出", "检查 URL/方法/status/code/body", "按 trace 进入后端层", "核对 SQL 数据和环境配置", "形成根因、修复和回归证据"],
         pitfalls=["页面报错就直接改 CSS", "看到 401 先查数据库 SQL", "同时改很多地方导致无法确认根因"],
         checks=["为登录后跳回登录画排障树", "为预约偶发失败区分 409、500 和网络失败"]),
    dict(no="10.3", module=10, title="安全、可维护性和当前边界",
         why="工程能力也包括知道系统尚未解决什么；诚实边界比堆砌高级术语更可信。",
         concepts=["威胁模型：明确资产、攻击者、入口和可能损失。", "可维护性：清晰分层、稳定契约、测试和迁移共同降低修改风险。", "技术债：当前可接受但未来需要偿还的设计限制。"],
         files=["HANDOFF 当前卡点与已知边界", "README 非目标", "SecurityConfig、schema.sql、前端 localStorage"],
         flow=["列出当前资产和入口", "对照实现识别已覆盖控制", "记录未覆盖风险", "按影响与成本排序", "用测试保护渐进改进"],
         pitfalls=["虚构 Redis、微服务或真实支付", "把 localStorage JWT 说成绝对安全", "忽略限流、Refresh Token、时段重叠、分页等边界"],
         checks=["说出至少六项当前边界", "为其中一项提出可验证的下一步"]),
    dict(no="11.1", module=11, title="项目 30 秒、90 秒、3 分钟介绍",
         why="不同场景要求不同信息密度；介绍应从问题和职责出发，再给方案与证据。",
         concepts=["电梯陈述：在有限时间内传递背景、职责、方案和结果。", "信息分层：30 秒给全景，90 秒给两条主链，3 分钟再展开并发和测试。", "证据化表达：数量、状态和运行结果必须能指向真实代码或报告。"],
         files=["README 项目目标与测开亮点", "HANDOFF 验证结果", "学习总纲的简历就绪检查表"],
         flow=["一句业务背景", "说明三角色与预约/排队", "给出 Spring Boot/Vue/MySQL 架构", "突出事务并发与自动化", "引用 144/45/99/6/12 等真实结果", "主动说明模拟支付等边界"],
         pitfalls=["从技术栈清单开始背诵", "使用无法解释的数字", "把团队成果和个人理解混淆"],
         checks=["分别录制三个时长版本", "删去所有无法用仓库证据支持的句子"]),
    dict(no="11.2", module=11, title="简历项目描述与量化结果",
         why="简历条目需要短，但不能牺牲真实性；每条都应能被追问到代码、测试或运行证据。",
         concepts=["STAR：情境、任务、行动、结果的组织方式。", "量化：用真实规模、覆盖范围和结果增强可验证性。", "边界声明：区分模拟能力、当前实现和生产化方向。"],
         files=["README 的最终结果", "两条 GitHub Actions 工作流", "测试目录与报告", "核心 Service/Mapper"],
         flow=["选 3-5 个最有技术含量的贡献", "每条写问题和行动", "补可核实结果", "准备代码入口", "准备取舍和局限追问"],
         pitfalls=["把‘参与’写成‘主导’", "把生成测试资产写成测试通过", "只写‘熟悉 Spring Boot’而没有动作和结果"],
         checks=["为防超卖写一条可核实条目", "为测试体系写一条不堆工具名的条目"]),
    dict(no="11.3", module=11, title="高频技术追问与压力追问",
         why="追问检验的不是背诵，而是能否从设计目标、实现、失败模式和边界持续推理。",
         concepts=["Why-How-Proof-Boundary：为什么做、如何做、证据是什么、尚有什么边界。", "反事实：如果去掉事务、唯一键或后端鉴权，会发生什么。", "权衡：说明方案解决的问题、成本和适用规模。"],
         files=["总纲 30 个高频问题", "Auth/Booking/Wallet/Queue 核心代码", "schema.sql 与测试资产"],
         flow=["先复述问题边界", "给结论", "用真实链路展开", "给失败案例或并发时序", "给测试证据", "说明替代方案与当前边界"],
         pitfalls=["只说术语不落代码", "遇到不会的问题虚构实现", "忽略面试官追问的条件变化"],
         checks=["准备 30 题口头回答", "对事务、JWT、401/403、防超卖各做两轮反事实追问"]),
    dict(no="11.4", module=11, title="完整模拟面试与查漏补缺",
         why="一次连续模拟能暴露知识之间的断点，例如能背 JWT 定义却追不出过滤器链。",
         concepts=["能力矩阵：概念、代码追踪、实践、表达四个维度分别评分。", "追问树：从项目介绍沿安全、数据库、并发、前端、测试和 CI 逐层深入。", "复盘：记录具体卡住的推理步骤，而不是只记‘答得不好’。"],
         files=["学习总纲模块验收表", "student-learning-notes-and-exercises.md", "本 PDF 的逐课自测清单"],
         flow=["3 分钟项目介绍", "随机选择一条真实链路", "连续深挖设计和异常分支", "现场读一段代码或设计测试", "按四维评分", "针对最低维度补课并复测"],
         pitfalls=["只练固定问题顺序", "老师替学生说完整答案", "未达到 80% 就继续跳课"],
         checks=["完成 30-45 分钟录音模拟", "列出三个薄弱点及对应代码和补练任务"]),
]


class AccentRule(Flowable):
    def __init__(self, width=CONTENT_W, color=BLUE, thickness=1.2):
        super().__init__()
        self.width = width
        self.height = 2 * mm
        self.color = color
        self.thickness = thickness

    def draw(self):
        self.canv.setStrokeColor(self.color)
        self.canv.setLineWidth(self.thickness)
        self.canv.line(0, self.height / 2, self.width, self.height / 2)


class GuideDocTemplate(BaseDocTemplate):
    def __init__(self, filename: str):
        super().__init__(
            filename,
            pagesize=A4,
            leftMargin=MARGIN_X,
            rightMargin=MARGIN_X,
            topMargin=MARGIN_TOP,
            bottomMargin=MARGIN_BOTTOM,
            title="QueueMate 44 课零基础完整学习手册",
            author="QueueMate learning project",
            subject="QueueMate 项目技术学习教材",
        )
        frame = Frame(
            MARGIN_X,
            MARGIN_BOTTOM,
            CONTENT_W,
            PAGE_H - MARGIN_TOP - MARGIN_BOTTOM,
            leftPadding=0,
            rightPadding=0,
            topPadding=4 * mm,
            bottomPadding=3 * mm,
            id="main",
        )
        self.addPageTemplates(PageTemplate(id="main", frames=[frame], onPage=draw_page))
        self._bookmark_seq = 0

    def beforeDocument(self):
        self._bookmark_seq = 0

    def afterFlowable(self, flowable):
        if not isinstance(flowable, Paragraph):
            return
        if flowable.style.name not in {"H1", "H2"}:
            return
        level = 0 if flowable.style.name == "H1" else 1
        text = flowable.getPlainText()
        self._bookmark_seq += 1
        key = f"heading-{self._bookmark_seq}"
        self.canv.bookmarkPage(key)
        self.canv.addOutlineEntry(text, key, level=level, closed=False)
        self.notify("TOCEntry", (level, text, self.page, key))


def draw_page(canvas, doc):
    canvas.saveState()
    canvas.setStrokeColor(LINE)
    canvas.setLineWidth(0.6)
    canvas.line(MARGIN_X, PAGE_H - 12 * mm, PAGE_W - MARGIN_X, PAGE_H - 12 * mm)
    canvas.setFont("QM", 7.5)
    canvas.setFillColor(MUTED)
    canvas.drawString(MARGIN_X, PAGE_H - 9 * mm, "QueueMate 44 课零基础完整学习手册")
    canvas.drawRightString(PAGE_W - MARGIN_X, 9 * mm, f"第 {doc.page} 页")
    canvas.restoreState()


def inline_markup(text: str) -> str:
    escaped = html.escape(text.strip())
    escaped = re.sub(r"`([^`]+)`", r'<font name="QM" color="#1769AA">\1</font>', escaped)
    escaped = re.sub(r"\*\*([^*]+)\*\*", r"<b>\1</b>", escaped)
    return escaped


def P(text: str, style="BodyCN") -> Paragraph:
    return Paragraph(inline_markup(text), STYLES[style])


def bullet(text: str) -> Paragraph:
    return Paragraph("• " + inline_markup(text), STYLES["BulletCN"])


def numbered(idx: int, text: str) -> Paragraph:
    return Paragraph(f"{idx}. " + inline_markup(text), STYLES["NumberCN"])


def callout(title: str, text: str, color=BLUE_PALE):
    data = [[Paragraph(inline_markup(title), STYLES["BoxTitle"])], [P(text)]]
    table = Table(data, colWidths=[CONTENT_W], hAlign="LEFT")
    table.setStyle(TableStyle([
        ("BACKGROUND", (0, 0), (-1, -1), color),
        ("BOX", (0, 0), (-1, -1), 0.7, LINE),
        ("LEFTPADDING", (0, 0), (-1, -1), 4 * mm),
        ("RIGHTPADDING", (0, 0), (-1, -1), 4 * mm),
        ("TOPPADDING", (0, 0), (-1, -1), 2.2 * mm),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 2.2 * mm),
    ]))
    return table


def code_block(text: str):
    wrapped = []
    for line in text.rstrip().splitlines() or [""]:
        if len(line) <= 92:
            wrapped.append(line)
        else:
            wrapped.extend(textwrap.wrap(line, width=92, subsequent_indent="    ", replace_whitespace=False))
    return XPreformatted(html.escape("\n".join(wrapped)), STYLES["CodeCN"])


def make_table(rows: list[list[str]], header=True):
    if not rows:
        return Spacer(1, 1)
    ncols = max(len(r) for r in rows)
    normalized = [r + [""] * (ncols - len(r)) for r in rows]
    pdata = []
    for ridx, row in enumerate(normalized):
        style = STYLES["TableHead"] if header and ridx == 0 else STYLES["TableCell"]
        pdata.append([Paragraph(inline_markup(cell), style) for cell in row])
    widths = [CONTENT_W / ncols] * ncols
    table = Table(pdata, colWidths=widths, repeatRows=1 if header else 0, hAlign="LEFT")
    commands = [
        ("GRID", (0, 0), (-1, -1), 0.45, LINE),
        ("VALIGN", (0, 0), (-1, -1), "TOP"),
        ("LEFTPADDING", (0, 0), (-1, -1), 2.2 * mm),
        ("RIGHTPADDING", (0, 0), (-1, -1), 2.2 * mm),
        ("TOPPADDING", (0, 0), (-1, -1), 1.7 * mm),
        ("BOTTOMPADDING", (0, 0), (-1, -1), 1.7 * mm),
        ("ROWBACKGROUNDS", (0, 1 if header else 0), (-1, -1), [colors.white, PAPER]),
    ]
    if header:
        commands.append(("BACKGROUND", (0, 0), (-1, 0), BLUE_DARK))
    table.setStyle(TableStyle(commands))
    return table


def lesson_story(lesson: dict) -> list:
    items = [PageBreak(), Paragraph(f"第 {lesson['no']} 课：{lesson['title']}", STYLES["H2"]), AccentRule()]
    items += [Paragraph("为什么要学", STYLES["H3"]), callout("本课问题", lesson["why"])]
    items += [Paragraph("核心概念（零基础解释）", STYLES["H3"])]
    for c in lesson["concepts"]:
        items.append(bullet(c))
    items += [Paragraph("QueueMate 真实代码入口", STYLES["H3"])]
    for f in lesson["files"]:
        items.append(bullet(f))
    items += [Paragraph("执行流程", STYLES["H3"])]
    for i, step in enumerate(lesson["flow"], 1):
        items.append(numbered(i, step))
    items += [Paragraph("常见错误与排障", STYLES["H3"])]
    for p in lesson["pitfalls"]:
        items.append(bullet(p))
    items += [Paragraph("本课验收", STYLES["H3"])]
    for c in lesson["checks"]:
        items.append(bullet(c))
    return items


def parse_markdown(path: Path) -> list:
    lines = path.read_text(encoding="utf-8").splitlines()
    story = []
    paragraph_lines: list[str] = []
    list_lines: list[tuple[str, str]] = []
    table_lines: list[list[str]] = []
    code_lines: list[str] = []
    in_code = False

    def flush_paragraph():
        nonlocal paragraph_lines
        if paragraph_lines:
            story.append(P(" ".join(x.strip() for x in paragraph_lines)))
            paragraph_lines = []

    def flush_list():
        nonlocal list_lines
        for kind, value in list_lines:
            story.append(bullet(value) if kind == "bullet" else P(value, "NumberCN"))
        list_lines = []

    def flush_table():
        nonlocal table_lines
        if table_lines:
            rows = [r for i, r in enumerate(table_lines) if not (i == 1 and all(re.fullmatch(r":?-{3,}:?", c.strip()) for c in r))]
            story.append(make_table(rows, header=True))
            story.append(Spacer(1, 2 * mm))
            table_lines = []

    for line in lines:
        stripped = line.strip()
        if stripped.startswith("```"):
            flush_paragraph(); flush_list(); flush_table()
            if in_code:
                story.append(code_block("\n".join(code_lines)))
                code_lines = []
                in_code = False
            else:
                in_code = True
            continue
        if in_code:
            code_lines.append(line)
            continue
        if not stripped:
            flush_paragraph(); flush_list(); flush_table()
            continue
        if stripped.startswith("### "):
            flush_paragraph(); flush_list(); flush_table()
            story.append(Paragraph(inline_markup(stripped[4:]), STYLES["H3"]))
        elif stripped.startswith("## "):
            flush_paragraph(); flush_list(); flush_table()
            story.append(Paragraph(inline_markup(stripped[3:]), STYLES["H2"]))
        elif stripped.startswith("# "):
            flush_paragraph(); flush_list(); flush_table()
            story.append(Paragraph(inline_markup(stripped[2:]), STYLES["H1"]))
        elif stripped.startswith("> "):
            flush_paragraph(); flush_list(); flush_table()
            story.append(callout("说明", stripped[2:], colors.HexColor("#FFF5E8")))
        elif re.match(r"^[-*] ", stripped):
            flush_paragraph(); flush_table()
            list_lines.append(("bullet", stripped[2:]))
        elif re.match(r"^\d+\. ", stripped):
            flush_paragraph(); flush_table()
            list_lines.append(("number", stripped))
        elif stripped.startswith("|") and stripped.endswith("|"):
            flush_paragraph(); flush_list()
            table_lines.append([c.strip() for c in stripped.strip("|").split("|")])
        elif re.fullmatch(r"---+", stripped):
            flush_paragraph(); flush_list(); flush_table(); story.append(AccentRule(color=LINE, thickness=0.7))
        else:
            flush_list(); flush_table(); paragraph_lines.append(stripped)
    flush_paragraph(); flush_list(); flush_table()
    if code_lines:
        story.append(code_block("\n".join(code_lines)))
    return story


def build_story() -> list:
    story = [Spacer(1, 30 * mm), Paragraph("QueueMate", STYLES["CoverTitle"])]
    story += [Paragraph("44 课零基础完整学习手册", STYLES["CoverTitle"])]
    story += [Paragraph("从项目全景、HTTP、MySQL、Spring Boot、安全与并发，\n到 Vue3、自动化测试、CI、性能排障和面试表达", STYLES["CoverSub"])]
    story += [Spacer(1, 20 * mm), callout(
        "使用说明",
        "第一篇按 44 课提供逐课导学：为什么、概念、真实代码、执行流程、常见错误和验收。第二篇完整收录项目现有 11 份专题知识记录，适合在某课后继续深挖。建议一次只学一课，先追代码，再完成验收。",
    )]
    story += [Spacer(1, 8 * mm), P("项目基线：Java 21、Spring Boot 3.3.5、MyBatis-Plus 3.5.9、MySQL 8、Vue 3、Vite、Axios、JUnit、Newman、Playwright、JMeter 与 GitHub Actions。", "SmallCN")]
    story += [P("生成日期：2026-09-05。内容以当前工作区代码、README、HANDOFF、学习总纲和 knowledge/01-11 为依据。", "SmallCN")]
    story += [PageBreak(), Paragraph("目录", STYLES["H1"])]
    toc = TableOfContents()
    toc.levelStyles = [STYLES["TOC0"], STYLES["TOC1"]]
    toc.dotsMinLevel = 0
    story += [toc, PageBreak()]

    story += [Paragraph("第一篇：44 课逐课学习地图", STYLES["H1"])]
    story += [callout("阅读方式", "每课的代码入口都使用仓库相对路径。先用编辑器打开对应文件，沿执行流程逐步找方法；遇到不懂的术语，再回到核心概念。不要背完整代码，重点理解输入、规则、状态、SQL 和输出。")]
    overview = [["课次", "主题", "模块"]]
    for lesson in LESSONS:
        overview.append([lesson["no"], lesson["title"], MODULES[lesson["module"]][1]])
    story += [Spacer(1, 3 * mm), make_table(overview)]

    for module_idx, (module_no, module_title, module_desc) in enumerate(MODULES):
        story += [PageBreak(), Paragraph(f"{module_no}：{module_title}", STYLES["H1"]), AccentRule(color=ORANGE)]
        story += [callout("本模块目标", module_desc, colors.HexColor("#FFF5E8"))]
        for lesson in [x for x in LESSONS if x["module"] == module_idx]:
            story += lesson_story(lesson)

    story += [PageBreak(), Paragraph("第二篇：QueueMate 项目深度知识记录", STYLES["H1"])]
    story += [callout(
        "为什么保留深度篇",
        "逐课篇负责建立学习顺序和概念地图；深度篇完整保留项目开发过程中形成的专题记录，其中包含设计原因、真实测试结果、边界和故障复盘。注意：深度篇按开发时间形成，早期记录中的表数量、测试数量等属于当时阶段；发生差异时，以第一篇的当前事实和当前代码为准。",
    )]
    deep_files = [
        "01-project-initialization-and-runtime.md",
        "02-authentication-and-jwt.md",
        "03-venue-management-and-rbac.md",
        "04-booking-slots.md",
        "05-booking-concurrency.md",
        "06-paid-booking-consumption-voucher-design.md",
        "07-wallet-voucher-implementation.md",
        "08-backend-completion.md",
        "09-vue3-frontend-design-system.md",
        "10-vue3-full-role-frontend.md",
        "11-postman-newman-repeatable-regression.md",
    ]
    for name in deep_files:
        story.append(PageBreak())
        story.extend(parse_markdown(KNOWLEDGE / name))

    story += [PageBreak(), Paragraph("附录：关键事实速查", STYLES["H1"])]
    facts = [
        ["类别", "当前项目事实"],
        ["运行端口", "Vite 5173；Spring Boot 8080；MySQL 3306"],
        ["核心数据表", "users、user_roles、merchant_applications、wallets、venues、booking_slots、bookings、booking_vouchers、wallet_transactions、queue_daily_sequences、queue_tickets"],
        ["成功响应", "ApiResponse: code='0', message='success', data=业务数据"],
        ["并发预约证据", "12 用户抢容量 3：3 个 201，9 个 409/BOOKING_SLOT_FULL；reservedCount=3"],
        ["后端测试", "144 个自动化测试"],
        ["接口回归", "Newman 45 个请求、99 个断言、0 失败，清理 remainingArtifacts=0"],
        ["UI 回归", "Playwright 6 条核心业务链路，使用独立浏览器上下文和 finally 清理"],
        ["CI", "基础构建与全量回归两层 GitHub Actions；报告 artifact 保留 14 天"],
        ["明确边界", "模拟钱包；无 Refresh Token/黑名单/登录限流；时段不防重叠且核心字段暂不可编辑；我的预约未分页"],
    ]
    story += [make_table(facts)]
    story += [Spacer(1, 5 * mm), callout("学习完成标准", "能够用自己的话解释概念，沿真实文件追完整链路，独立完成小范围验证，并诚实说明设计取舍和边界。任何一课建议达到 80% 再继续下一课。", colors.HexColor("#EAF7F1"))]
    return story


def main() -> None:
    OUTPUT.parent.mkdir(parents=True, exist_ok=True)
    doc = GuideDocTemplate(str(OUTPUT))
    doc.multiBuild(build_story(), maxPasses=20)
    print(OUTPUT)


if __name__ == "__main__":
    main()
