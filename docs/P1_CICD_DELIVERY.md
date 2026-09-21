# GitHub CI/CD 真实联调交付（2026-09-21）

## 结果与授权

用户授权推送并验证真实流水线，随后明确授权在测试服务器安装专用部署账号/受限 sudo，将新专用 SSH 私钥保存到本仓库 test 环境的加密 Secrets，并开启自动部署。

- **CI 与 CD 均成功**：[运行 35571422437](https://github.com/cqz-cio/Enterprise-Assessment-Question-Bank-System/actions/runs/35571422437)。
- 已部署应用提交：`b849c99a4b5eb02b9e44a0243287a14e78e32f75`。
- 测试入口：`https://124.220.2.69:18443/#/login`。
- JAR：`/opt/enterprise-exam-test/releases/35571422437-1-b849c99a4b5e/yf-bev2-api.jar`。
- SHA256：`8085c0a277e3f853de1ece3f045e346e00e23b4dbe1f449864eb00f713c47cad`。
- 发布前备份：`/opt/enterprise-exam-test/backups/ops/20260921-151022-360c158c`。
- 原包 `releases/logo-cf9d0b673af0120c.jar` 保留；`cd-current.json` 记录本次新旧版本、提交和备份路径。

本交付记录使用仅文档的 `[skip ci]` 提交，避免为记录验收结果再次停服；服务器应用版本以上述成功部署提交为准。

## 实际配置

- GitHub 仓库环境 `test`，只允许 `main` 分支部署；仓库变量 `TEST_DEPLOY_ENABLED=true`。
- 环境 Secrets：`TEST_SSH_PRIVATE_KEY`、`TEST_SSH_KNOWN_HOSTS`；SSH 主机/端口变量为 `124.220.2.69` / `22`。私钥仅在生成进程内存及 GitHub 加密 Secret 中存在，没有写入 Git、日志或本地明文文件。
- 主机公钥来自已通过既有 known_hosts 校验的管理员 SSH 连接；不采用未核实的即时扫描值。
- 专用账号 `exam-deploy` 已实际验证密钥登录、上传目录写入和受限 sudo。应用仍由 `exam-test` 运行；部署账号不加入 docker 或应用数据组，不能读取 `secrets/app.env`。
- 已安装 root 所有的 `/usr/local/sbin/enterprise-exam-test-deploy`，不自动执行上传的 root 脚本。`prepare` 子命令只提供当前 JAR 副本用于增量传输，不复制运行密钥。

## 首次真实运行发现并修复

1. 现有 workspace 使用 pnpm 11 的 `allowBuilds` 配置；最初 CI 的 pnpm 9 安装失败。固定 pnpm 11.3.0，同时在前端 `packageManager` 中声明版本。
2. GitHub runner 的 Maven 依赖解析在默认镜像阶段停滞，被 60 秒无进展保护终止。增加 CI 专用 Maven Central 设置，后续构建成功；不更改本机 Maven 配置。
3. 旧 Windows 包的一份 SQL 使用 CRLF，Linux checkout 使用 LF。比较迁移时仅归一化行尾，继续拒绝真实 SQL 修改/删除，并补充回归测试。
4. 约 126 MiB 完整 JAR 的跨境 SCP 传输在 60 秒内只传约 1.5 MiB，且非交互无进度输出。改为基于服务器旧 JAR 的 rsync 变化块传输，保留真实字节进度、60 秒停滞保护、300 秒上传上限和完整 SHA256 校验；后续成功上传并发布。

失败的前三次运行均未切换应用；完整通过后才执行停服备份和发布。

## 验证

- GitHub 真实运行完成前端冻结依赖安装、运维/部署测试、正式前端构建、后端 115 项测试及 JAR 前端一致性校验。
- 新增部署相关测试现为 20 项：包含备份失败、健康失败、迁移保护、SSH 凭据/主机校验、过期提交拒绝、跨平台行尾和增量传输预置目录防护。Windows 运维 13 项此前已通过；Linux CI 自动跳过 4 项 Windows 启动专用测试。
- 前端全量类型检查实际仍报 32 项既有错误，作为非阻断报告；不视作类型检查通过。
- 服务器 `ActiveState=active`、`SubState=running`、`NRestarts=0`；`.cd-needs-recovery` 不存在。
- 部署前后，原配置/密钥文件的 SHA256、全部附件 SHA256、八张业务表记录数一致，Flyway 仍为 `024 / success=1`。没有新增迁移或业务 API。
- 公网 TLS 正常，首页、构建 JS/CSS、Logo、验证码 PNG 返回成功，API 文档入口 404；原 ERP HTTP 200。已上线的 Logo 比例保留。
- 本次自动发布备份由部署器验证完整状态和逐项校验值；没有将备份上传 GitHub，也没有对这份新备份另做数据库恢复演练。

## 日常使用与后续

推送/合并到 main 后自动部署；手动发布用 Actions → CI and Test Deployment → Run workflow → main。停用自动发布将仓库变量 `TEST_DEPLOY_ENABLED` 改为 `false`。完整配置及故障恢复见 [部署操作说明](../deploy/linux/GITHUB_ACTIONS.md)。

后续优先清理 32 项前端类型债务，使类型检查成为阻断项；继续落实定时备份、加密异机副本及测试站完整浏览器业务验收。单实例发布仍有维护停机窗口，避免在正式答题或导入清单未保存时发布。

本机服务未重启。原有未跟踪 `AGENTS.md` 和 `patches/` 保留；已上线 Logo 改动以单独提交 `63f5bdb` 保存，避免自动部署覆盖回旧版。
