# QueueMate 本地开发环境

## 1. Java

本机已使用 JDK 21：

```powershell
java -version
```

VS Code 工作区已在 `.vscode/settings.json` 中配置：

```json
"java.configuration.runtimes": [
  {
    "name": "JavaSE-21",
    "path": "D:\\JAVA\\jdk-21",
    "default": true
  }
]
```

## 2. VS Code 插件

打开 `D:\QueueMate` 后，VS Code 会根据 `.vscode/extensions.json` 推荐安装：

- Extension Pack for Java
- Maven for Java
- Spring Boot Extension Pack
- YAML
- REST Client

## 3. Maven

本机已安装 Apache Maven 3.9.16：

```text
MAVEN_HOME=D:\Maven\apache-maven-3.9.16
Path=%MAVEN_HOME%\bin
```

新开一个 PowerShell 后验证：

```powershell
mvn -version
```

VS Code 已安装 Maven for Java 插件，可识别 `backend/queuemate-server/pom.xml`。

## 4. 后端启动

已有 QueueMate 本地数据库在启动新版后端前，需要先导入商家入驻迁移：

```powershell
& 'D:\MySQL\MySQL Server 8.0\bin\mysql.exe' --default-character-set=utf8mb4 -u root -p queuemate
```

进入 MySQL 后执行：

```sql
source D:/QueueMate/sql/migrations/20260720_merchant_onboarding.sql;
```

该迁移会创建 `user_roles` 和 `merchant_applications`，并把现有账号角色安全回填到多身份表；脚本可重复执行。

进入后端目录：

```powershell
cd D:\QueueMate\backend\queuemate-server
```

运行测试：

```powershell
mvn test
```

启动服务：

```powershell
$env:DB_PASSWORD = '本机 MySQL 密码'
mvn spring-boot:run
```

`DB_PASSWORD` 只在当前 PowerShell 进程中生效，不会写入仓库。还可以通过 `DB_USERNAME`、`DB_URL` 和 `JWT_SECRET` 覆盖对应配置。

健康检查：

```text
GET http://localhost:8080/api/v1/health
```

## 5. VS Code 任务

项目已配置 `.vscode/tasks.json`：

- `backend: test`
- `backend: run`

可在 VS Code 中使用 `Terminal -> Run Task...` 执行。

运行 `backend: run` 时会弹窗要求输入本机 MySQL 密码，输入内容不会保存到项目文件。

## 6. VS Code 调试

项目已配置 `.vscode/launch.json`：

- `Debug QueueMate Server`

可在 VS Code 的 Run and Debug 面板直接启动后端。

启动调试时同样会弹窗要求输入本机 MySQL 密码。

## 7. 前端启动

前端使用 Vue3、Vite、Element Plus、Axios 和 pnpm：

```powershell
cd D:\QueueMate\frontend\queuemate-web
pnpm install
pnpm dev
```

默认访问地址：

```text
http://localhost:5173
```

开发服务器把 `/api` 代理到 `http://localhost:8080`。如需使用其他 API 地址，可复制 `.env.example` 并设置 `VITE_API_BASE_URL`。

生产构建：

```powershell
pnpm build
```

前端启动前应先检查后端健康接口。禁止把 `DB_PASSWORD`、JWT 或真实账号密码写入前端环境文件。
