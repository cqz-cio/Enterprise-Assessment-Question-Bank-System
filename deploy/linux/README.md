# Linux 测试部署

本目录对应单实例测试环境。Java 17 运行 JAR，Nginx 提供 HTTPS，独立 Docker Compose 项目提供 MySQL 8 与 Redis 7。不是高并发容量承诺，也不要直接替换服务器上已有应用。

GitHub 自动测试、构建和测试站更新见 [GitHub CI/CD 配置说明](GITHUB_ACTIONS.md)。

## 本次部署约定

- 主目录：`/opt/enterprise-exam-test`，归属标记 `.exam-test-owned`。
- 公网入口：`https://124.220.2.69:18443`；管理登录 `/#/login`，候选人 `/#/exam-entry`。
- 后端仅监听 `127.0.0.1:18082`；专用 MySQL 为 `127.0.0.1:13307`，Redis 为 `127.0.0.1:16380`。
- 数据库名 `enterprise_exam_test`，业务账号 `exam_app`，Docker 项目 `enterprise-exam-test`。不能指向原服务器 ERP 或宿主机 MySQL/Redis。
- 用户选择完整复制本机数据库和附件，**测试环境生成独立 JWT 与考核码密钥**。原账号密码、题库、历史答卷/成绩保留；旧考核码不能在测试环境认证，需要新建测试考核。原本机环境不受影响。
- 访问测试站需要重新登录；Redis 会话不从本机复制。密钥生成一次后持久保存，重启/升级不得重新生成。
- 仅腾讯云入站 TCP 18443 需要为测试访问开放。80 保留现有网站及证书验证路径；数据库、Redis、后端端口不能对公网开放。

## 文件与权限

`secrets/app.env` 和 `secrets/mysql.env` 为 0600，`secrets/` 为 0700。前者包含独立 JWT/pepper 及数据服务认证配置；不要打印到终端、聊天、Git 或日志。`app.env.example` 仅为无密钥模板，systemd 环境文件应使用双引号并正确转义反斜线。

Redis 官方镜像会降权运行，`secrets/redis.conf` 需 root 所有、镜像 redis 组可读（0640）；组 ID 应通过当前镜像的 `id -g redis` 核对，不要假定固定 ID。宿主机父目录仍为 0700。不要将文件改为所有用户可读。

独立系统用户 `exam-test` 运行应用，无交互登录。JAR root:exam-test 0640；主目录及 releases 0750、组 exam-test；uploads/logs 由 exam-test 所有、0750。systemd 仅开放这两个目录写入。`logback-spring.xml` 通过 `LOGGING_CONFIG` 指定，避免底座日志配置向只读工作目录写入；按 20 MB/文件、30 天、合计 1 GB 轮转。

## 首次部署步骤

1. 盘点现有服务、端口、磁盘、内存和 Java 17；保存当前 Nginx 配置。不要改系统 Java 默认版本。本次使用 `/usr/lib/jvm/java-17-openjdk-amd64/bin/java`。
2. Windows 使用 `scripts/ops/build_release.py` 打包并校验前端，使用 `scripts/ops/backup.py create` 备份。上传发布 JAR、数据库 SQL、附件 ZIP、清单到受限目录；逐项核对 SHA256。不要上传 Windows 原密钥或 DPAPI 文件。
3. 创建专用用户和上述目录权限，安装本目录的 compose、服务单元、日志配置。生成三套独立随机值：数据库 root/业务密码、Redis 密码、JWT/pepper；JWT 与 pepper 各至少 32 字符且彼此不同。填入私有环境文件，保持后端绑定回环地址。
4. `docker compose up -d --pull never mysql redis` 只启动本项目。先确认目标库为空，再导入 SQL；已有表时停止并人工核实，禁止直接覆盖。导入后逐表核对清单行数及 Flyway 版本。解压附件时拒绝绝对路径、路径穿越和符号链接。
5. 仅在测试库把 `pl_plugin_data` 中 `upload-local` 的 `localDir` 改为 `/opt/enterprise-exam-test/uploads/`，`visitUrl` 设为空。上传目录授予 exam-test 读写权限。
6. JAR 保存在 `releases/<commit>.jar`，`current.jar` 指向已校验文件；安装服务后 `systemctl daemon-reload`，`systemctl enable --now enterprise-exam-test`。确认本机 HTTP 200、Flyway 成功、验证码可生成、日志无启动错误。
7. 按下一节配置独立 IP 证书和 18443 HTTPS。Nginx 配置验证通过才 reload；公网验证前不要宣称已可外部访问。

## HTTPS 与续期

本机已有 Nginx 实际只加载 `/etc/nginx/conf.d/*.conf`。新增站点放 `enterprise-exam-test.conf`，从 `nginx-ip.conf.template` 替换 IP、证书目录。本次证书目录为 `/opt/enterprise-exam-test/tls/live/exam-test-ip`。

在**现有 IP 的 80 端口 server 块**仅增加 `nginx-acme-location.conf` 对应 location/include，webroot 为 `/var/lib/enterprise-exam-acme`，不要接管原网站根路径。添加后先用公网请求 `/.well-known/acme-challenge/<随机验证文件>` 验证返回指定内容，再申请证书。

使用独立 `/opt/enterprise-exam-test/certbot` venv（本次 Certbot 5.8.0）和独立 tls/work/logs 目录：

```bash
sudo timeout 120 /opt/enterprise-exam-test/certbot/bin/certbot certonly \
  --non-interactive --agree-tos --register-unsafely-without-email \
  --config-dir /opt/enterprise-exam-test/tls \
  --work-dir /opt/enterprise-exam-test/certbot-work \
  --logs-dir /opt/enterprise-exam-test/certbot-logs \
  --preferred-profile shortlived --webroot \
  --webroot-path /var/lib/enterprise-exam-acme \
  --ip-address 124.220.2.69 --cert-name exam-test-ip \
  --deploy-hook '/usr/sbin/nginx -t && /bin/systemctl reload nginx'
```

安装 `enterprise-exam-cert-renew.service/.timer` 到 `/etc/systemd/system/` 并启用 timer。每六小时检查并随机错峰，操作限制 180 秒；独立 config-dir 不会续期其他站点证书。IP 证书有效期很短，不能只依赖手工更新。原服务器的全局 Certbot 定时器不会自动使用这个独立目录。

Nginx 覆盖转发来源头，后端只信任回环反向代理。改为多级代理时必须重新验证，不能直接信任公网客户端传入的 `X-Forwarded-For`。

## 日常操作

```bash
sudo systemctl status enterprise-exam-test --no-pager
sudo journalctl -u enterprise-exam-test -n 80 --no-pager
sudo systemctl restart enterprise-exam-test
sudo systemctl stop enterprise-exam-test
sudo systemctl list-timers enterprise-exam-cert-renew.timer --no-pager
sudo systemctl status enterprise-exam-cert-renew.service --no-pager
```

仅重启这个服务；不能停止已有 ERP、全局 Redis/MySQL 或清空 Docker 卷。内存配置面向少量人员联调：JVM 堆 320 MB、服务上限 700 MB、MySQL 512 MB、Redis 96 MB。并发规模需另行压测。

## 备份、恢复与更新

将本目录 `backup.py` 安装为 `/opt/enterprise-exam-test/backup.py`，仓库 `scripts/ops/backup.py`、`ops_common.py` 安装到同名子目录。命令：

```bash
sudo timeout 180 python3 /opt/enterprise-exam-test/backup.py
```

输出到 `backups/ops/<时间戳>/`：数据库与附件清单，以及 `runtime/` 私有运行配置和独立测试密钥。`manifest.json` 与 `runtime-manifest.json` 均须 complete；后者文件也应逐项校验 SHA256。整个备份为敏感文件，异机副本应先加密。脚本不会删除历史备份；尚未配置业务数据定时备份或异机存储。

数据库备份是 InnoDB 一致性快照，附件在线另行复制；需要跨数据库/附件严格一致时先停用上传或在维护窗口停止应用。`scripts/ops/backup.py verify` 只恢复到新的临时容器，不覆盖现有库，但至少需要额外 1 GB 容器内存，当前小规格共享服务器不自动执行。

灾备恢复必须使用全新空库/隔离环境，恢复同一份 SQL、附件和 `runtime/secrets` 原值后再启动。不要拿本机原密钥替换测试环境已生成的密钥。迁移版本有差异时不能只换旧 JAR 回滚；需评估数据库兼容性，必要时从整套备份恢复。

升级先备份、验包，将新 JAR 放 releases；停止应用后原子替换 current.jar 链接，保留环境文件，启动并验证日志、登录、考核、原站点及证书；失败停止继续变更并按备份计划处理。不要覆盖已执行的 Flyway 脚本。

证书能力参考：[Let's Encrypt IP 证书与 Certbot 官方说明](https://letsencrypt.org/2026/03/11/shorter-certs-certbot)。
