# Windows 内网运行与备份

适用于当前 Windows Java 应用 + Docker Desktop MySQL/Redis，单后端实例。
`prod` 是新增的可选配置，本次不会自动切换现有服务或修改现有数据库密码。

## 1. 构建发布包

安装 Java 17、Maven、Node.js、pnpm 和 Python 3.10+。按锁文件安装前端依赖：

```powershell
cd yf-bev2-vue
pnpm install --frozen-lockfile
cd ..
python scripts/ops/build_release.py
```

构建工具依次执行正式前端构建、Maven `clean verify`，逐文件比较 JAR 中前端与
`dist-pro`，将 JAR 和 SHA256 清单保存到 `yf-bev2-api/target/releases/<时间>/`。任何一步失败都不会发布。
默认拒绝脏工作区；`--allow-dirty` 仅用于本机测试，清单会标记包含未提交代码。
可通过 `--offline --maven-settings <路径> --maven-repository <路径>` 使用预装离线缓存。
每阶段最多 300 秒，连续 60 秒无输出则停止进程树，日志在 `work/codex-logs/`。
已有 32 项前端类型债务尚未在本专项处理；Vite 构建通过不等于全量类型检查通过。

## 2. 准备生产配置

1. 保留 `.local/backend-secrets.clixml`。旧系统必须继续使用原 JWT 和考核码密钥。
2. 将本目录 `application-production.properties.example` 复制到
   `.local/application-production.properties`，填入数据库专用账号及 Redis 密码。
   该目录已排除 Git；沿用密钥初始化脚本设置的 Windows 用户权限。不要把凭据填到模板中。
3. 数据库账号只授权本项目 schema；迁移仍需该 schema 的建表/改表等 DDL 权限。
   Redis 启用认证后再填写密码。当前开发容器无 Redis 密码，不能直接照抄配置切换。
4. 将 MySQL/Redis 端口限制为本机地址或内部专网，并设置 Windows 防火墙。
   当前根目录 `compose.yaml` 仍是开发依赖；它的默认密码和端口映射不能用于正式上线。
5. 上传文件保存在管理端“本地上传”插件配置的 `localDir`，属于持久数据；不能随发布目录删除。

`prod` 默认仅监听 `127.0.0.1:8080`、关闭 Swagger/Knife4j、连接池最多 20、INFO 日志按
20MB/文件滚动，历史 30 天且总量 1GB。启动器的 stdout/stderr 日志另在 `work/codex-logs/`，
需在服务停止后定期归档清理；它们不由 Spring 日志轮转管理。

## 3. 发布与回退

在无进行中考试、无未保存导入考核码的维护窗口操作：

```powershell
# 先备份并恢复演练，见下一节。保留原 JAR 和它的校验值。
# 用发布副本启动；不要使用下一次构建会清空的 target/release-build 中间产物。
powershell.exe -NoProfile -ExecutionPolicy Bypass -File .\start-backend.ps1 -Restart -Profile prod -JarPath ".\yf-bev2-api\target\releases\<本次时间目录>\yf-bev2-api.jar"
```

启动健康检查失败会停止本次新进程并给出日志，不会自动回退数据库。
成功启动后，模式、JAR 和配置路径保存在 `.local/backend-launch.json`，停止服务仍保留。
已有 prod 启动记录时，未指定参数的日常启动/重启沿用它；主动切回开发模式时明确传入 `-Profile dev`。
检查 `POST /api/sys/config/detail`、登录页及本人业务操作；`/doc.html`、`/v3/api-docs` 应为 404。
发布包仍需在目标机器验证 HTTPS、权限和局域网访问。端口冲突时不要直接结束所有 Java 进程。

应用回退：只有旧 JAR 与当前数据库结构兼容时，才用同样脚本启动保存的旧 JAR。
不兼容时应在新数据库实例恢复备份、核对结果后人工切换连接配置，禁止把旧备份直接导入在用库。
数据库恢复工具只提供隔离演练，不提供覆盖生产库的快捷命令。

## 4. 备份与隔离恢复演练

Docker Desktop 需运行，使用现有容器内部的数据库凭据，不在命令行传密码：

```powershell
python scripts/ops/backup.py create
python scripts/ops/backup.py verify "work/backups/ops/<上一步输出的目录名>"
```

可选 `--container`、`--database`、`--output`；上传路径跨容器/操作系统时以
`--uploads-dir "D:/实际上传目录"` 指定主机上的真实目录。上传插件启用但路径不存在时会失败，
不会把漏备份附件当作成功。备份目录在 Windows 上限制为当前用户和 SYSTEM。

备份内容：MySQL 结构/记录/触发器/存储过程/事件、启用的本地上传目录 ZIP、原 Windows
加密密钥文件（若存在）、SHA256 与快照内表行数清单。失败的目录标记 `incomplete`，不能演练。
不备份 Redis 会话/验证码/限流缓存；恢复后重新登录，导入中的临时明文考核码不可恢复。
MySQL 用户权限、外部对象存储、本机外部配置、TLS 证书和发布 JAR 需另行保管。

数据库使用 InnoDB 一致性快照，备份时不能执行 DDL/迁移；非 InnoDB 表会拒绝。
附件在线复制与数据库不是同一事务快照，严格灾备应在维护窗口停止写入后备份。
行数从同一 SQL 快照统计，不拿备份期间变化的业务库行数作为比较基准。

演练先核对 SHA256，再创建同一 MySQL 镜像 ID 的全新临时容器：无外部网络、无端口发布、
无主机目录挂载。验证全部表/行数、Flyway 版本和附件 ZIP 校验，随后删除临时容器及卷。
镜像需已在本机；工具不会自动联网拉取。报告保存在备份目录 `restore-verification.json`。
演练失败时报告 `passed=false`。如果 Docker 自身故障导致清理失败，按日志中的
`exam-restore-check-*` 容器名处理，不能删除正在使用的 `yf-exam-mysql`。

建议每天备份、升级前额外备份、至少每季度演练；保留期按公司要求确定。
本工具不会注册计划任务或自动删旧备份。SQL/附件含业务数据，异机副本必须放入公司控制的
加密存储；仅在同一块磁盘备份不能抵御磁盘损坏。

## 5. 上线前仍需落实

- 实际服务器、内网域名、HTTPS 证书及入口代理配置。
- 原密钥的跨机器安全托管。DPAPI 文件只能在原 Windows 用户/机器使用，单独复制它无法灾备迁机。
- 专用数据库账号、Redis 认证、端口隔离，以及定时备份与异机加密副本。
- 代理后的登录限流：后端默认忽略转发 IP 头。代理会令用户共享一个来源地址，必须另行验证容量和可信代理规则，不能直接信任任意 `X-Forwarded-For`。
- 单实例运行；候选人导入任务只在该进程内存保留。当前演练是恢复完整性验证，不是 100 人并发验收。

配置依据：[Spring Boot 3.2](https://docs.spring.io/spring-boot/docs/3.2.x/reference/htmlsingle/)；
[MySQL 8.0 mysqldump](https://dev.mysql.com/doc/refman/8.0/en/mysqldump.html)。
