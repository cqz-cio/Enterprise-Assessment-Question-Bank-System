# GitHub CI/CD：测试服务器

工作流：`.github/workflows/ci-cd.yml`，名称 **CI and Test Deployment**。

## 更新项目时怎么用

- PR 指向 `main`：安装冻结的 pnpm 依赖，运行运维/部署测试、正式前端构建和后端全量测试，校验 JAR 内前端文件，再保存发布包。
- 推送或合并到 `main`：上述检查通过后，使用**同一次 CI 的发布包**部署 `124.220.2.69`。
- 手动：GitHub → Actions → CI and Test Deployment → Run workflow → 选择 `main`，勾选 `deploy`。取消勾选只做 CI。
- 仓库变量 `TEST_DEPLOY_ENABLED` 未设为 `true` 时，只运行 CI。首次配置完成后再开启；暂时关闭自动部署也改这个变量。
- 手动运行旧提交或其他分支不能部署；脚本还会检查运行提交是否仍是远程 `main` 最新提交。队列中的旧运行可能因此报错，使用最新一次即可。

目前前端存在已记录的类型债务，全量 `ts:check` 为非阻断检查并上传日志；正式 Vite 构建和后端测试失败仍阻止发布。清理类型债务后应移除该步骤的 `continue-on-error`，不能把类型检查警告当作全量通过。

**这是单实例维护发布，会短暂停机。** 不要在有人正式答题、导入清单尚未下载时发布；重启会清掉内存中的候选人导入任务。流水线不重新导入数据库、不改现有密钥、不重新创建容器、不修改 ERP/Nginx/证书。

## 一次性安装服务器部署账号

先在可信管理员电脑创建专用 Ed25519 密钥（不要复用管理员/root 密钥；私钥仅放 GitHub Secret）：

```bash
ssh-keygen -t ed25519 -f exam-test-github -C github-enterprise-exam-test
```

自动化使用的私钥需要无口令；此命令交互只在人工准备密钥时使用。不要把私钥放进仓库或聊天。将公钥 `exam-test-github.pub` 和已审核的 `deploy/linux/install-cd.sh`、`deploy_test.py` 上传服务器同一目录，使用管理员身份运行：

```bash
sudo timeout --kill-after=5s 60s bash ./install-cd.sh /absolute/path/exam-test-github.pub
```

安装器要求原测试环境标记 `.exam-test-owned` 和原备份工具已存在。它只安装部署账号、受限 sudo 入口与 root 所有的脚本，不重启服务。部署账号固定为 `exam-deploy`；独立的 `exam-test` 继续运行应用。重新安装会替换这个专用账号的公钥，可用于轮换 CI 密钥。

- `/var/lib/enterprise-exam-deploy/incoming`：部署账号上传 JAR 与清单。
- `/usr/local/sbin/enterprise-exam-test-deploy`：root 所有，部署账号仅能 sudo 调用此入口。参数经过校验，不能指定其他服务或根目录；上传脚本不会以 root 执行。
- `authorized_keys` 使用 `restrict`，关闭端口转发、PTY 等能力；账号不加入 docker、sudo 或 exam-test 组。该账号仍有部署本应用代码的权限，必须保护私钥。
- 若云防火墙限制 SSH 来源，需要让所选 GitHub runner 能连接 SSH 端口。不要为此开放 MySQL/Redis/18082。
- 部署脚本将来更新后，需管理员重新运行安装器安装已审核版本；日常发布不会自动替换提权脚本。

## GitHub 配置

仓库 Settings → Environments 创建 **test**，部署分支只允许 `main`。需要人工发布确认时可在 GitHub 环境设置审批规则。

Settings → Secrets and variables → Actions 配置：

| 类型 | 名称 | 值 |
| --- | --- | --- |
| Repository variable | `TEST_DEPLOY_ENABLED` | `true`；必须是仓库级，job 条件求值时还未加载环境变量 |
| Variable（仓库或 test 环境） | `TEST_SSH_HOST` | 可不填，默认 `124.220.2.69` |
| Variable（仓库或 test 环境） | `TEST_SSH_PORT` | 可不填，默认 `22`；以实际 SSH 端口为准 |
| Secret（优先 test 环境） | `TEST_SSH_PRIVATE_KEY` | 专用私钥完整内容，包括 BEGIN/END 行 |
| Secret（优先 test 环境） | `TEST_SSH_KNOWN_HOSTS` | 已核实的服务器 OpenSSH known_hosts 记录 |

known_hosts 不是密码。先通过腾讯云控制台或既有可信管理员连接获取主机公钥指纹，例如 `ssh-keygen -lf /etc/ssh/ssh_host_ed25519_key.pub`。在管理员电脑执行 `ssh-keyscan -T 10 -p 22 -t ed25519 124.220.2.69`，核对所得密钥的指纹后保存整行到 Secret；非 22 端口必须使用 `[主机]:端口` 格式记录。流水线不会临时信任扫描结果，也不会关闭主机密钥校验。

JWT、考核码 pepper、数据库/Redis 密码继续只留在服务器现有 `secrets/`，**不需要上传 GitHub**。GitHub 环境和环境级 Secrets 的可用性受仓库可见性/套餐影响，见 [GitHub 环境说明](https://docs.github.com/en/actions/how-tos/deploy/configure-and-manage-deployments/manage-environments)；如不能使用环境 Secrets，可使用仓库 Secrets，同时严格限制 `main` 写入和工作流修改权限。

## 发布保护与失败处理

1. GitHub 串行发布；服务器另有文件锁。CI 记录 Git SHA、JAR SHA256，上传后再次校验；不允许 dirty 本地包进入 CD。
2. 新包放到 root 所有的独立 release 目录；旧 Flyway 文件被修改或移除时，停止发布，服务不受影响。仅统一 Windows CRLF/Linux LF 行尾后比较，避免跨平台打包被误判为 SQL 修改。
3. 停止 `enterprise-exam-test`，运行现有私有备份工具，逐项校验数据库、附件、运行配置及密钥备份清单；失败会恢复旧服务。
4. 原子更新 `current.jar`，启动新包。检查返回的首页与 JAR 内容一致、验证码返回 PNG（包含 Redis 依赖检查）、systemd 服务为 active；最后从 GitHub 检查公网 TLS/首页，不使用 `curl -k`。
5. 新包失败且迁移集合完全相同：自动恢复旧 JAR 并检查健康。新包带有新增迁移：停止应用，保留 `.cd-needs-recovery`，防止直接回退包或继续部署。

公网 HTTPS 检查失败只使工作流失败，不自动回退已通过内部检查的版本；应排查防火墙、Nginx、证书。健康检查不替代登录/作答/阅卷业务验收，也不是数据库恢复演练。

故障时管理员查看：

```bash
sudo cat /opt/enterprise-exam-test/.cd-needs-recovery
sudo cat /opt/enterprise-exam-test/cd-current.json
sudo journalctl -u enterprise-exam-test -n 100 --no-pager
sudo ls -lt /opt/enterprise-exam-test/cd-logs
```

`cd-current.json` 记录最近成功版本；失败/中断后以服务实际状态、`current.jar` 和 recovery 标记为准。`releases/<run-id>-<attempt>-<sha前12位>/deployment-result.json` 记录单次结果；标记记录原 JAR、目标 JAR、备份路径。发生迁移或恢复失败时，先核实 Flyway 状态与备份，在维护窗口按 Linux README 恢复兼容版本/整套备份，健康验证后才人工删除 `.cd-needs-recovery` 并重新运行工作流。不要删除标记后盲目重试，不要关闭 Flyway。

单命令默认不超过 120 秒，安装依赖/前后端各 300 秒；远程发布总等待 660 秒用于覆盖备份（180 秒）、服务停止/启动、健康检查（120 秒）和失败恢复，各内部命令仍独立限时。日志超过 60 秒无实际进展时会终止子进程；SSH 断开不会直接打断服务器关键部署步骤。中断状态保留标记，阻止下一次发布。

GitHub 发布包保留 14 天，构建诊断保留 7 天。服务器备份含密钥和业务数据，不上传为 Actions artifact；发布包、上传目录、私有备份和日志不自动删除，需定期检查磁盘并制定保留/异机备份策略。部署前最低空闲空间检查为 1 GiB，不代表足够容纳任意规模的备份。

本次只交付脚本及本地验证；提交到 GitHub、安装服务器入口、配置 Secrets、开启变量和首轮实际 Actions 联调完成后，才能称为自动部署已启用。
