# 密钥恢复与认证安全交付（2026-09-18）

## 本次结果

原密钥已加密持久化，根目录提供初始化、启动、停止和重启脚本。普通账号不能省略验证码；验证码原子消费，登录和候选人验证具备独立 IP 与身份限流。新版已启动，本轮无数据库迁移，仍为 V020。

## 修改文件

- 根目录 `initialize-backend-secrets.ps1`、`start-backend.ps1`、`stop-backend.ps1`，共享 `scripts/ops/Backend.Common.ps1`，以及 `.gitignore`：Windows DPAPI、严格目录权限、身份核对、有限启动等待及运行记录。
- 后端 `ability/auth/`、`config/AuthWebConfig.java`：Redis 原子窗口、入口 IP 拦截、429/503 异常。
- `ability/captcha/controller/CaptchaController.java`、`ability/captcha/service/impl/CaptchaServiceImpl.java`：先存储后输出、5 分钟有效、SETNX、GETDEL。
- `SysUserController`、`SysUserLoginReqDTO`、`SysUserServiceImpl`：强制验证码、请求校验、账号身份限流、密码错误统一提示；`CandidateVerificationThrottle` 改为独立考核码维度。
- `ServiceExceptionHandler`、`application.yml`：429/Retry-After、503、不信任转发头、独立必需 pepper。
- 前端 `InputCaptcha.vue`、`LoginForm.vue`、`RegisterForm.vue`、`config/axios/service.ts`：失败刷新、必填校验、重复提交保护、限流与不可用提示。
- 新增 `AuthSecurityTest`，调整 `SysUserServiceImplTokenTest` 构造依赖。同步 PRD、API、开发计划、部署说明、决策及交接文档；具体路径可按上述类名检索。

## 接口与存储

接口细节见 `04-api-spec.md` 的 D-032。普通账号登录四字段必填；验证码 key 必须为 UUID。Redis 需 6.2+（本地为 7）。限流固定 10 分钟，默认账号/码各 10 次，登录/候选人 IP 300 次，注册 IP 60 次，取图 IP 600 次。正常登录和候选人 JWT 响应保持原契约，无新权限或迁移。

`.local/backend-secrets.clixml` 保存原两项密钥的 Windows 用户加密内容，已排除 Git；原密钥未轮换。`.local/backend-process.json` 为最新进程记录，替代之前 work 内的人工阅卷进程记录。当前日志 `work/codex-logs/20260918-145643-031-backend.log`，PID 8240（以后以运行记录和实际进程为准）。

## 验证结果

- 后端 94 项测试全部通过，新 JAR 打包成功：`work/codex-logs/20260918-144733-auth-package.log`。
- 正式前端构建和修改文件 ESLint 通过：`20260918-144731-auth-build.log`、`20260918-144729-auth-lint.log`。
- 真实 Redis 24 并发仅 10 次获准，计数和 TTL 正确，剩余不足 1 秒仍阻断；脚本 `work/auth-live.py atomic`。
- 真实 HTTP 验证缺失/错误/过期/重放验证码拒绝；同一验证码 4 并发只有一次登录；账号第 11 次返回 429 和 Retry-After，窗口过期恢复；四类 IP 限流不能通过伪造转发头绕过；独立考核码限流生效。日志 `20260918-145533-auth-verify.log`。
- 重启前签发的候选人 JWT、原姓名和考核码在无密钥环境变量的新进程启动后继续有效。原密钥相等性仅内存比较，不打印。停止/重启及加密文件不变校验通过，重启耗时 14 秒：`20260918-145640-auth-restart.log`。重复启动不创建第二个后端。
- 缺失、损坏、明文格式的密钥文件均被拒绝，真实文件未改；目录 ACL 仅当前用户和 SYSTEM，Git 忽略有效。
- 浏览器通过：空表单三字段必填；错误密码提交后清空验证码并换图；正确密码与新验证码进入管理工作台，随后退出。两次验证码处理经用户当次授权。
- 临时数据、会话和测试凭据已清理，七张原业务表计数与验收前一致（5/1/2/18/61/64/2）：`work/codex-logs/20260918-150227-auth-cleanup.log`。

## 部署中发现并修复的问题

Windows PowerShell 重定向子进程可能拿不到退出码；停止脚本改为复核目标进程已退出，避免成功停止却报失败。初版测试部署包装器使用管道捕获后台服务输出，等待超时（`20260918-145316-auth-deploy.log`），应用已成功运行；改用日志文件捕获后，独立重启验收通过。超时不是后端业务启动失败，当前服务已确认健康。

## 未完成与下一步

- DPAPI 文件不能跨机器/Windows 用户解密；未做物理重启、开机自启或跨机器灾备。正式迁移前需要受控原密钥备份及恢复演练。
- 当前仍为本地开发拓扑。生产 profile、HTTPS、MySQL/Redis 网络隔离、备份轮转、可信反向代理和共享出口限流调优尚待部署专项；普通截图验证码不等同于高级机器人防护。
- 之前完整类型检查的 32 项旧错误本轮未整理；本轮验证生产构建和修改文件 lint，没有声称完整类型检查通过。
- 下一项建议实现带权限和筛选条件的成绩导出，再完善候选人 Excel 批量导入；若准备正式上线，应先完成生产部署专项。
