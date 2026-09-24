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

## 发布状态与下一步

本轮为本地代码、正式前端构建及浏览器验收；未提交、推送、部署或重启服务器。后端 release profile 会从 `dist-pro` 打包前端，发布时应使用现有发布流水线重建。已保留原有未跟踪的 `AGENTS.md`、`patches/`。

下一步：发布这批 Logo 修改到测试站后检查两个入口；无需数据库迁移。本轮未重跑业务全流程，视觉修改没有触及其逻辑。