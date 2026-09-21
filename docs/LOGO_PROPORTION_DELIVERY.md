# 登录与加载 Logo 比例优化（2026-09-21）

## 修改与授权

用户确认独立预览后明确要求实施并部署到测试站。保留 TRIPEER 原素材，仅调整显示比例。

- `yf-bev2-vue/src/views/Login/Login.vue`：桌面 Logo 从 48×48 容器调整为宽 154px、高度自适应（原图实际约 52px），标题 24px、间距 20px；窄屏 Logo 宽 116px、标题 18px，允许换行。
- `yf-bev2-vue/index.html`：首屏 Logo 宽 300px（窄屏 260px），标题 26px（窄屏 23px），单加载圈 30px，修正全屏背景与居中；支持减少动态效果偏好。
- 无新增依赖、数据库迁移或业务 API；后台导航 Logo 不在本次批准预览范围内。

## 验证

- 修改文件 ESLint、`git diff --check` 通过。
- 正式前端构建与 Maven release 构建通过，115 项后端测试全部通过；137 个 JAR 前端文件逐项校验一致。
- 部署前逐项比较新旧 JAR 的后端 class、依赖库和迁移内容，全部相同。
- 公网浏览器在 1920×911 检查桌面登录页，Logo 实测 154×52.14px；390×844 实测 116×39.28px；320px、390px 无横向溢出。
- 服务器返回新版首屏 HTML，核对 300px Logo、30px 加载圈与动画样式；没有为截图人为延长真实加载时间，未单独截获短暂首屏动画。
- 公网 HTTPS 首页、关联 JS/CSS、验证码返回 200，TLS 证书验证通过；文档入口 404。只读检查，没有提交登录验证码或改动业务数据。
- 截图：`work/codex-previews/logo-desktop-deployed.png`、`logo-mobile-deployed.png`。
- 日志：`work/codex-logs/20260921-113212-1aa1f9db-ops.output`（构建）、`20260921-113320-d9513a7e-ops.output`（部署）、`20260921-113522-f06e7af7-ops.output`（公网）。

## 部署与恢复

- 已部署 `https://124.220.2.69:18443/#/login`，仅重启独立 `enterprise-exam-test` 服务；ActiveState=active、NRestarts=0，原 ERP 返回 200。
- 服务器备份：`/opt/enterprise-exam-test/backups/ops/20260921-113325-1befd725`。沿用原测试环境密钥与配置。
- 当前 JAR：`/opt/enterprise-exam-test/releases/logo-cf9d0b673af0120c.jar`；旧包保留，`deployment.json` 记录 previousJar。
- 本地发布：`yf-bev2-api/target/releases/20260921-113257-fa99fefa`，SHA256 `cf9d0b673af0120cc7751be9ccb4420ce563df3fa285aa999c0771c081fb76f6`；包括未提交修改，附 `frontend-source.patch` 用于追溯。本轮未提交或推送 Git。
- 保留已有未跟踪 AGENTS.md、patches/；未重启本机后端。

## 后续

前端既有类型债务未在本轮处理或重测。推荐继续完成测试站完整业务浏览器验收与备份调度；本轮公网可访问性已确认，替代此前“18443 待放行”的历史状态。
