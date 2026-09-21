# Windows 部署与备份恢复交付（2026-09-21）

用户确认正式服务器尚未确定，先完善当前 Windows 部署。本轮交付运维工具和可选生产配置，
没有把当前业务环境切换到生产 profile，也没有改动原密钥或数据库凭据。

## 修改内容

- `scripts/ops/backup.py`：MySQL InnoDB 一致性备份、本地附件 ZIP、原 DPAPI 文件副本、SHA256 与快照表行数清单；独立容器恢复和完整性报告。
- `scripts/ops/ops_common.py`：命令总超时、无进展超时、日志、Windows 进程树终止及失败报告；敏感查询 stdout 使用自动关闭的临时文件。
- `scripts/ops/build_release.py`、后端 POM release profile：正式前端构建、后端全量测试、独立目录打包、逐文件检查 JAR 静态资源、版本化发布副本。
- `application-prod.yml`、`ProductionDocumentationFilter.java`：生产数据库/Redis 凭据外部注入、回环监听、连接池上限、INFO 日志及轮转、文档入口关闭。
- `start-backend.ps1` 与 `deploy/windows/`：可选 prod 启动、私有外部配置模板、构建/备份/发布/回退说明；成功启动后持久记忆模式、JAR 与配置路径，停止后仍保留。
- 前端补充 `vue-eslint-parser` 直接依赖，并开始跟踪现有 pnpm 锁文件；两个 `.gitignore` 同步维护。
- `test_backup.py`、`test_ops_common.py`、`test_start_backend.py`、`ProductionDocumentationFilterTest.java`：备份防误操作、损坏校验、失败清理、超时、启动参数及文档关闭回归。

操作命令及前置条件见 [Windows 运维说明](../deploy/windows/README.md)。

## 验证结果

- 后端全量 **115 项测试通过**，Maven `clean verify` 通过。
- 最终一键发布命令通过；JAR 内 **137 个前端文件**逐一匹配正式构建，生产配置已打入包。
- **13 项运维工具测试通过**：完整/损坏/未完成/非法路径备份、快照行数、隔离容器失败清理、stdout 不落日志、命令失败及超时终止、生产启动记忆与显式模式覆盖。
- PowerShell 启动脚本语法检查通过；pnpm 离线、冻结锁文件安装成功。
- 实际备份及恢复：**40 张表、685 条记录、V024、3 个附件**验证通过；临时容器及卷已删除。
- 使用备份副本和独立 Redis，在随机回环端口实际启动 prod：站点首页和配置接口可用；`/doc.html`、`/v3/api-docs`、`/swagger-ui/index.html`、`/webjars/knife4j/test.js` 均返回 404。临时 Java、MySQL、Redis、网络已清理。
- 发现并处理 Docker Desktop 本次启动异常；正常停止命令超时后终止崩溃进程并重启，引擎恢复。未重置 Docker 或删除业务卷。
- 一次前端冷启动构建无输出被超时保护终止；诊断构建及随后完整发布均通过。

本机证据：

- 备份/恢复报告：`work/backups/ops/20260921-090058-4c5ac728/`。
- 发布副本：`yf-bev2-api/target/releases/20260921-090313-11d6c0d6/`。
- 后端测试/打包日志：`work/codex-logs/20260921-090222-abe114e7-ops.output`。
- 运维回归：`work/codex-logs/20260921-090758-f22dddb0-ops.log`。
- prod 启动：`work/codex-logs/exam-prod-check-1a07157c93.log`。

## 迁移、接口与边界

**无新增数据库迁移，无业务 API 变更。** 恢复工具不支持把备份写入在用数据库。
线上数据没有被测试修改；测试使用备份的独立副本。现有数据库/Redis 仍沿用开发配置，
正式启用 prod 需要先配置专用账号、Redis 认证及入口网络。

备份含业务信息，Windows 目录 ACL 限当前用户及 SYSTEM，Git 不跟踪备份。
DPAPI 副本仅适用于原用户/机器，跨机器灾备还必须托管原始密钥；本次没有导出明文密钥。
在线附件复制与数据库不是同一个事务快照，严格恢复点需维护窗口停止写入。
SHA256 用于检测损坏，不构成防恶意篡改签名；备份目录/异机存储必须限制访问。

本地发布副本包含此前尚未提交的品牌清理代码，清单明确标记 `dirtyWorkingTree=true`。
本次提交只包含本专项变更，品牌清理及其 V022/V023 文件仍由原任务单独交付。
不能把这份本地测试包声称为某一 Git 提交的完全可复现包。
全量前端既有 32 项类型债务未在本专项修复，正式构建通过不等于类型检查全通过。

## 推荐下一步

确定实际服务器后，落实内网域名/HTTPS、数据库和 Redis 认证与端口隔离、可信代理和登录并发限额、
定时备份及公司控制的异机加密副本、跨机器密钥恢复演练。继续保持单后端实例；导入临时口令只在内存保留。
