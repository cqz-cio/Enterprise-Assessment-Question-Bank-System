# 登录与加载页 Logo 透明背景（2026-09-24）

## 完成内容

按已批准的最后一张效果图实施两处 Logo：登录页采用透明底、浅色 TRIPEER 字样；加载页采用透明底、灰色字样。彩色图形保留蓝、橙、灰色系。页面背景、标题、布局、动画及 Logo 显示尺寸沿用原实现。

- 登录页：桌面宽 154px，窄屏宽 116px；原 `/brand-logo.png`、`/logo.png` 配置使用浅色字样版本。未返回配置时也显示内置版本；自定义上传路径继续使用原配置。
- 加载页：桌面宽 300px，窄屏宽 260px，保留浅灰底和原加载圈。
- 导航、favicon、候选人入口及原素材未改动。

## 修改文件

| 文件 | 用途 |
| --- | --- |
| `yf-bev2-vue/src/views/Login/Login.vue` | 内置 Logo 路径映射到浅色字样素材，保留自定义配置 |
| `yf-bev2-vue/src/assets/imgs/tripeer-logo-light.png` | 登录页透明 PNG（2154 × 730） |
| `yf-bev2-vue/index.html` | 加载页引用独立透明素材 |
| `yf-bev2-vue/public/tripeer-logo-transparent.png` | 加载页透明 PNG（2155 × 730） |
| `docs/08-decision-log.md` | D-040 视觉决策 |
| `docs/05-development-plan.md`、`docs/AI_HANDOFF.md` | 实施结果与交接 |
| `design-qa.md` | 视觉对照验收 |
| `scripts/ci/deploy.py`、`scripts/ci/test_deploy.py` | 限时、保留部分数据的上传与一次续传，失败保护回归 |
| `deploy/linux/GITHUB_ACTIONS.md` | 同步实际传输方式和时限 |
| 本文档 | 改动、验证和发布状态 |

## 接口、数据库与业务

无接口变更，无数据库迁移，无依赖变化。没有修改登录校验、考核逻辑或业务数据。

## 验证结果

- 正式前端构建通过（49.4 秒，上限 300 秒）。日志：`work/codex-logs/20260924-142134-045ba4c9-ops.log`。
- 完整 `vue-tsc --noEmit --skipLibCheck` 通过（18.6 秒，上限 120 秒）。日志：`work/codex-logs/20260924-142133-1cac20b4-ops.log`。
- 首次 ESLint 发现组件中已有混合换行及新增内容的 CRLF 告警，统一该组件换行后 `--max-warnings 0` 通过。最终日志：`work/codex-logs/20260924-142329-3d3d181c-ops.log`。
- `git diff --check` 通过。
- 两张 PNG 的四角均为 alpha=0，存在完整的 0～255 alpha 范围；不是用页面颜色填充白框。
- Chrome 8 项浏览器检查通过：1920×911 登录页与加载页；390×844 登录页、加载页及深色模式；320×844 登录页；自定义 Logo 保留；旧 `/logo.png` 内置配置映射。均无横向溢出，无未处理的页面异常。
- 浏览器使用正式构建页面，读取测试站公开品牌配置和验证码图片，仅放行这两个公开接口；未提交登录、注册或任何业务请求。首屏通过阻止 JavaScript 执行捕获，不改变源代码中的加载时长。
- 当前 CUA 浏览器工具受 Windows ACL 初始化故障影响，改用独立无头 Chrome 直接验证；独立浏览器进程树及本地验证服务均已停止。
- 对照图：`work/logo-transparency/comparison.png`；逐项指标：`work/logo-transparency/browser-report.json`；视觉结论见根目录 `design-qa.md`。

## 素材来源与提示词

使用内置 ImageGen，以现有 `yf-bev2-vue/public/logo.png` 为图形参考，生成两张透明 PNG。生成文件已复制到上面的工程路径，不依赖用户目录中的生成缓存。

加载页素材提示词：

> Precise background extraction of the supplied original company logo. Produce the SAME TRIPEER logo asset with only its white background removed to genuine PNG alpha transparency. This is NOT logo redesign, NOT UI mockup. Preserve exact original blue/orange/gray symbol geometry, original gray custom TRIPEER wordmark geometry, all brand colors (gray approximately #7c7d81, muted periwinkle blue approximately #616daf, orange approximately #ff7d30), exact relative positioning and widths, crisp flat fills and antialiased sharp edges. Absolutely no gradient, shadow, lighting, texture, embossing, glow, recoloring, changed letter shapes, or new content. Remove ALL pure white backdrop pixels including internal white negative spaces in the emblem and letters to transparent, no white edge fringe. Output only this one horizontal company logo on genuine transparent background, no checkerboard pattern baked into image, no UI panels, no captions. Match original canvas aspect ratio 1190:403 as closely as possible and retain original margins: symbol content approx x95..377 y52..349 and lettering approx x409..1102 y156..245 in a 1190 by 403 source canvas. Keep original scale and placement relative to the canvas so replacing source file preserves layout. Be pixel-faithful to the supplied logo; just remove white.

登录页素材提示词：

> Make a second dark-surface version of this supplied transparent TRIPEER logo. Change ONLY the gray TRIPEER wordmark letters on the right to uniform solid off-white #F4F6FB. Keep every pixel of the blue/orange/gray symbol on the left, the exact same shapes, letter geometry, margins, positions, width, height and canvas aspect ratio of the supplied logo. Keep genuine PNG alpha transparency in all empty space. No white rectangle. No shadow, glow, outline, extrusion or texture. The lettering must have flat #F4F6FB fill with clean antialiased edges. Preserve the original source canvas 2155 x 730 ratio and padding. The output is only one logo asset, not a mockup or preview board.

## 发布结果（2026-09-24）

用户明确要求生效、推送代码，并在 CI 成功后 CD 发布。已推送 main，最终 [CI 与 CD 全部成功](https://github.com/cqz-cio/Enterprise-Assessment-Question-Bank-System/actions/runs/35967669039)，线上两处 Logo 已生效。

- Logo 实施提交：`a3e86f82879fe29d5497cdfa8ad9d96b7b84466f`；最终部署提交（含传输修复）：`50d43bd8d07641745fc0eba37191ebab03302c63`。
- 测试入口：[企业人才考核系统](https://124.220.2.69:18443/#/login)。
- 发布编号：`35967669039-1-50d43bd8d076`。
- JAR SHA256：`9162b6c545287fb8a65a47c609b3f8e992df46ca3e487b95622af527dfbab4b7`。
- 发布前已验证备份：`/opt/enterprise-exam-test/backups/ops/20260924-150834-f7fe4c70`；旧 JAR 保留，迁移集合一致。
- 服务 active/running、NRestarts=0，无 recovery 标记或本次遗留传输进程。
- CI：123 项后端测试、23 项部署测试全部通过；13 项运维测试中 4 项 Windows 专项按平台跳过，其余通过；全量前端类型、正式构建及 JAR 静态资源校验通过。
- 公网 TLS、首页和验证码依赖健康检查通过。Chrome 6 项线上检查通过：1920×911 登录/加载页，390×844 登录/加载/深色模式，320×844 登录；无横向溢出或未处理异常。两张线上 PNG 的 SHA256 与提交素材完全一致，已查看真实截图确认无白框及白边。
- 线上截图和报告：`work/logo-transparency/deployed/`；成功流水线、服务器版本及备份记录：`latest-workflow.json`、`deployed-release.json`、`cd-success.log`（均在 `work/logo-transparency/`）。

## 发布中发现的传输问题

最初的 CI 均通过，但跨境 SSH 上传多次停滞，进度显示 100% 时服务器仍未收齐；这些失败均发生在切换前，旧服务持续运行。单纯降低速率仍未解决，因此最终采用 32 KiB/s 限速、对本次私有上传副本使用 `--inplace --backup` 保留已收块，并在确认旧传输进程退出后最多续传一次。每次远端 110 秒、客户端 125 秒，清理 20 秒，两次上传及清理最多 270 秒，保留 60 秒无进展保护和完整 SHA256 校验。

新增上传失败不切换、清理失败不重连、停止旧传输后才能重连的回归测试；在服务器部署账号下用一次性随机文件实际验证了中断续传及移动块的完整 SHA256，测试目录已自动清理。最终成功运行的上传一次完成，未触发自动续传。早期遗留 rsync 已按账号和本次目录核对后清理，没有停止其他进程。

交付记录以仅文档 `[skip ci]` 提交推送，避免为记录结果再次重启应用。无接口、数据库迁移或依赖变化，保留原有未跟踪 `AGENTS.md`、`patches/`。

下一步：刷新测试站确认两处显示；本轮未重复完整业务流程或容量测试，未修改业务逻辑。
