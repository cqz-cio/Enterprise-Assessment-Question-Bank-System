# 侧栏品牌图标补齐（2026-09-24）

## 改动

用户指出登录后左上角仍使用带白底的小 Logo，要求继续优化。原实现把整条横向 Logo 塞进 30×30px 的图标位，导致标志与字样都过小。

`yf-bev2-vue/src/components/Logo/src/Logo.vue` 复用上一轮批准的透明素材，通过 SVG 视口（110 68 565 580）只显示彩色品牌标志。保留原 30px 图标位、50px 栏高、系统名称与字色；侧栏收起为 64px 时图标居中。内置 `/brand-logo.png`、`/logo.png` 和未返回配置时使用该标志；自定义上传仍显示原图。首页链接补充可访问名称。

没有改动源 PNG、登录页、加载页或业务流程，无接口、数据库迁移、依赖或配置变更。原有未跟踪 `AGENTS.md`、`patches/` 保留。

## 验证

- 修改组件 ESLint 零警告、完整 TypeScript 检查通过；正式前端构建通过（65.1 秒）。日志：`work/codex-logs/20260924-170351-0dbfefc3-ops.log`。
- 实际 `Logo.vue` 使用项目 Vue、Pinia、路由与样式，在隔离 Chrome 中完成 10 项检查：展开、收起、再次展开、深色、顶部布局、顶部加侧栏布局、顶部深色、自定义图片、旧内置路径、无配置。
- 各状态图标均为 30×30px，无横向溢出；收起时居中、标题隐藏，展开恢复标题。点击 Logo 返回首页通过；未处理异常为 0。
- 已实际查看展开及收起截图：图形完整、边缘透明、无白框、没有微小英文残片。截图为放大局部，未改变实际显示尺寸。
- 证据：`work/sidebar-logo/browser-report.json`、`expanded.png`、`collapsed.png`。验证仅使用隔离组件和虚构状态，无管理账号登录或业务数据变动。

## 发布

- 应用提交 `c98fe7db567ecd439f883c133cbed447c8ba1722` 已推送 main；[运行 35979337448](https://github.com/cqz-cio/Enterprise-Assessment-Question-Bank-System/actions/runs/35979337448) 的 CI 与 CD 均成功，2026-09-24 17:11（北京时间）完成发布。
- CI 完整类型检查、正式构建与 JAR 静态资源校验通过；123 项后端测试、23 项部署测试通过。运维套件 13 项中 4 项 Windows 专用检查在 Linux runner 跳过，其余通过。
- 测试站：<https://124.220.2.69:18443>。发布目录 `/opt/enterprise-exam-test/releases/35979337448-1-c98fe7db567e`，发布前备份 `/opt/enterprise-exam-test/backups/ops/20260924-171045-b140d538`。
- 服务器 `current.jar` 指向本次发布，应用提交及 JAR SHA256 与 CI 产物一致；服务 active/running，自动重启计数为 0，无恢复标记，迁移集合未改变。JAR SHA256：`3a7b1221658766ad56ff4fc48245b53dfafbe22a7a295a2d208bf13fa0ade677`。
- 公网 HTTPS 首页实际加载 `/assets/index-d86deeb8.js` 与 `/assets/Layout-774fe8fa.js`；布局代码包含本次 SVG 视口和收起居中逻辑。线上 `/assets/tripeer-logo-light-b44722e0.png` 与源码 SHA256 完全一致：`b44722e07b653db0f4742eb43f92c4e22ec80266480cfc25a032dd86807ed7dc`。
- 只读配置查询确认当前 `backLogo=/brand-logo.png`，会命中新内置图标逻辑。线上复核为公开资源与服务器发布校验；本轮没有登录线上管理账号，实际组件的 10 项显示检查在本地隔离页面完成。
- 证据：`work/sidebar-logo/latest-workflow.json`、`ci-summary.json`、`deployed-release.json`、`deployed-resources.json`。最终交付文档以 `[skip ci]` 提交，避免重复发布。

本次无已知未解决问题；已打开的页面可能仍使用旧代码，下一步用 Ctrl+F5 刷新测试站，确认侧栏图标。
