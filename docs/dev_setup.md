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

当前系统 PATH 中没有检测到 `mvn` 命令。推荐安装 Apache Maven 后配置：

```text
MAVEN_HOME=D:\apache-maven
Path=%MAVEN_HOME%\bin
```

配置完成后新开一个 PowerShell，验证：

```powershell
mvn -version
```

如果只在 VS Code 中开发，也可以先安装 Maven for Java 插件，让 VS Code 识别 `backend/queuemate-server/pom.xml`。

## 4. 后端启动

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
mvn spring-boot:run
```

健康检查：

```text
GET http://localhost:8080/api/v1/health
```

## 5. VS Code 任务

项目已配置 `.vscode/tasks.json`：

- `backend: test`
- `backend: run`

可在 VS Code 中使用 `Terminal -> Run Task...` 执行。

## 6. VS Code 调试

项目已配置 `.vscode/launch.json`：

- `Debug QueueMate Server`

可在 VS Code 的 Run and Debug 面板直接启动后端。

