# 开发环境与内网部署工作文档

> 版本：V0.1  
> 当前状态：本地开发环境已使用 Docker Compose 管理 MySQL 和 Redis

## 1. 当前本地结构

```text
Windows 开发机
├── Spring Boot 后端（本机进程，8080）
├── Vue 前端（开发时可单独运行；当前也可使用后端 static 预编译页面）
└── Docker Desktop
    └── yf-exam
        ├── yf-exam-mysql（3306）
        └── yf-exam-redis（6379）
```

基础依赖启动：

```powershell
cd "D:\Enterprise Assessment Question Bank System"
docker compose up -d
docker compose ps
```

后端开发启动：

```powershell
cd "D:\Enterprise Assessment Question Bank System\yf-bev2-api"
mvn spring-boot:run
```

以上命令用于人工操作。自动化开发会话启动长驻服务时，应使用后台进程、日志文件和有限等待，不能无限占用前台命令。

## 2. 本地健康检查

```powershell
docker compose ps
docker exec yf-exam-redis redis-cli ping
docker exec yf-exam-mysql mysqladmin ping -h localhost -uroot -p
```

应用访问：

```text
http://localhost:8080/
http://localhost:8080/doc.html
```

数据库密码等敏感配置不得复制到公开文档或提交到公共仓库；当前开发配置在进入生产前必须改为外部注入。

## 3. 推荐生产内网结构

```text
公司终端浏览器
       |
       | HTTPS / 内网域名
       v
Nginx 或反向代理（80/443）
       |
       v
Spring Boot 应用（仅内部端口）
       |
       +------ MySQL（不对员工网段直接开放）
       |
       +------ Redis（只允许应用访问）
```

生产环境推荐用一个 Compose 项目管理应用组件，但数据库数据卷必须独立持久化并纳入备份。

## 4. 云服务器与公司内网的关系

- 如果部署在公司实体服务器，并只绑定公司局域网 IP，天然适合纯内网访问。
- 如果部署在腾讯云 CVM，CVM 不等于公司局域网；要实现公司内部访问，需要 VPN、专线、零信任网关或公网入口 + 公司固定出口 IP 白名单。
- 公司公网出口 IP 是否固定由公司的宽带和网络方案决定，不能只在 CVM 内自行决定。
- CVM 的安全组可以限制哪些公网 IP 能访问，但只能识别访问请求到达 CVM 时的源公网 IP。

## 5. 生产端口策略

建议：

| 端口 | 生产暴露范围 |
| --- | --- |
| 80/443 | 公司局域网或公司固定出口 IP |
| 8080 | 仅 Nginx/应用内部网络 |
| 3306 | 仅应用容器/管理跳板机 |
| 6379 | 仅应用容器 |
| 22 | 运维管理 IP，禁止全网开放 |

Docker Compose 生产配置中，MySQL 和 Redis 不应使用 `ports` 映射到公网网卡；应用通过 Compose 内部服务名访问它们。

## 6. 生产配置要求

- MySQL、Redis 和 JWT 密钥通过环境变量或独立配置文件传入。
- 使用独立的生产 profile，禁止复用 `application-dev.yml`。
- 日志级别从 debug 调整到 info/warn，并配置轮转和保留周期。
- 关闭公开注册以外的无关匿名接口；员工注册仍按审核策略开放。
- Swagger 默认关闭或限制源 IP。
- 设置反向代理请求体大小、连接超时和真实源 IP 转发。
- 如果使用 HTTPS，证书终止在 Nginx，并将内部后端端口保持不公开。

## 7. 数据备份

至少建立：

- 每日 MySQL 逻辑备份。
- 备份文件保留 7 到 30 天，按公司要求确定。
- Redis 只保存会话和缓存时可以重建；若承载关键数据则一并备份。
- 升级数据库前执行即时备份。
- 每季度至少进行一次恢复演练。

备份完成不等于可恢复，只有恢复演练通过才算有效。

## 8. 发布步骤

1. 确认目标 Git tag 和数据库迁移版本。
2. 备份当前生产数据库。
3. 在测试环境执行迁移并完成冒烟测试。
4. 构建前端和后端产物。
5. 更新生产容器或服务。
6. 执行健康检查和核心业务冒烟测试。
7. 检查 Nginx、防火墙和安全组访问范围。
8. 记录发布时间、版本、迁移和回滚方案。

## 9. 回滚原则

- 应用版本通过镜像 tag 或 JAR 版本回滚。
- 数据库迁移必须在发布前评估是否可逆。
- 不使用破坏性 Git 命令代替部署回滚。
- 涉及数据格式变化时，回滚方案必须同时覆盖代码和数据。

## 10. 当前 Windows 本地启动方式（2026-09-18）

项目根目录执行以下命令；Java 17、已有 JAR 和 Compose 中 MySQL/Redis 须先可用。脚本只管理本项目后端，不负责前端、Docker 或开机自启。

```powershell
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\start-backend.ps1
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\stop-backend.ps1
# 明确重启已运行的后端
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\start-backend.ps1 -Restart
```

普通启动发现本项目已运行会直接提示，不重复启动。停止前核对完整项目路径、PID 与创建时间，不停止无关 Java 或占用 8080 的其他程序。升级/重启应避开正在答题时段；脚本本身不判断是否有人答题。后台隐藏运行，日志位于 `work/codex-logs/`，最新 PID、JAR 和日志路径记录在 `.local/backend-process.json`。启动最多等待 90 秒，日志 60 秒无进展提前失败并清理本次失败后端；健康检查通过才报告 ready。脚本临时注入原密钥给 Java，随后还原调用进程环境；日志默认覆盖为 INFO。

原两项密钥已加密保存到 `.local/backend-secrets.clixml`，目录权限限当前 Windows 用户及 SYSTEM，且 `.local/` 已排除 Git。日常启动不再需要手工设置密钥环境变量。初始化命令 `initialize-backend-secrets.ps1` 仅用于已有原密钥的初次保存：要求原启动 PowerShell 已有 JWT_SECRET 与 ASSIGNMENT_CODE_PEPPER；已有有效文件直接校验，遇到不同原值拒绝覆盖。不得为解决启动错误临时生成新值，否则旧考核码会失效。

DPAPI 文件绑定同一 Windows 用户和计算机，不能作为跨机器灾备；换机器/重装前须通过受控密钥管理渠道另行备份原密钥，并演练恢复。本轮已验证清空环境变量的新 PowerShell 重启、原 JWT 与考核码兼容；未进行物理重启或跨机器恢复。加密机制见 [Microsoft Export-Clixml 文档](https://learn.microsoft.com/en-us/powershell/module/microsoft.powershell.utility/export-clixml)。

当前认证忽略转发 IP 头，Vite/反向代理后的用户可能共用一个限流来源。正式上线必须结合可信代理网络、入口防刷和峰值并发调整，不可简单打开任意 Forwarded 头信任。开发 profile、数据库端口隔离、HTTPS 和完整备份仍按前文生产要求另行落实。
