# 全前端品牌显示检查与统一修复（2026-09-24）

## 范围与结论

用户在 D-040/D-041 后要求全面检查并直接统一优化同类 Logo 问题。本轮逐一搜索前端图片、品牌配置、Logo 组件、页面入口和 favicon 引用，再捕获当前页面和真实组件，按既有透明品牌方案修复。未改业务流程、题目图片、头像或验证码。

| 步骤 | 检查位置 | 发现与处理 | 证据（work/brand-audit/） |
| --- | --- | --- | --- |
| 1 | 登录与员工注册 | 桌面、390px、320px、深色显示正常；复用公共内置 Logo 识别规则，保留原尺寸与自定义上传 | before/ 与 after/ 的 login-*、register-mobile.png |
| 2 | 首屏加载 | 透明背景、桌面/手机比例正常，保留现有效果 | before/loading-desktop.png、after/loading-mobile.png |
| 3 | 候选人入口 | 仍引用旧不透明图片；全局深色下页面标题几乎不可见。改为按主题选择已批准的透明素材，并使用现有深色卡片/文字令牌 | before/candidate-dark.png、after/candidate-dark.png；桌面/320px截图 |
| 4 | 管理端/员工端共享导航 | 收起状态初始化顶部或顶部加侧栏布局时系统名称消失；只在经典侧栏收起时隐藏标题，消除延迟回调旧状态。30px 标志与 64px 收起居中保持 | before-components/top-left-collapsed.png、after-components/top-left-collapsed.png |
| 5 | 浏览器标签图标 | 原整条横向白底 Logo 缩成细小条状；统一用独立透明标志，16/32/48px 在深浅底均可识别 | before-components/previews.png、after-components/previews.png |
| 6 | 系统设置中的 Logo 预览 | 旧图片的白带与灰底不融合；默认图片改为透明，两个 Logo 预览使用浅灰底，缩略图继续完整等比显示 | before-components/previews.png、after-components/previews.png |
| 7 | 旧默认路径与配置兼容 | /brand-logo.png、/logo.png 统一为已批准的透明图片；同站默认路径的查询参数、片段、绝对 URL 正确识别，上传/外部 Logo 保持自定义 | asset-report.json、after-components/browser-report.json |

## 实现文件

- `yf-bev2-vue/src/utils/branding.ts`：统一全字标、浅色字标、独立标志及同站内置路径识别。
- `src/components/Logo/src/Logo.vue`：使用共享标志，派生标题显示状态，保留首页导航及可访问名称。
- `src/views/Login/Login.vue`、`src/views/Exam/Candidate/Entry.vue`：统一品牌来源；候选人入口按深浅主题适配，保持尺寸、内容与认证流程。
- `src/views/System/Config/components/BaseConfig.vue`、`src/plugins/uploader/src/FileUploader.vue`：只为两项 Logo 配置指定预览背景；其他上传控件沿用原默认背景与行为。
- `yf-bev2-vue/index.html`、`public/tripeer-mark.svg`、`public/brand-logo.png`、`public/logo.png`：favicon 和兼容资源。

以上省略前缀的 src/public 路径均位于 yf-bev2-vue。没有数据库迁移、接口、依赖或持久配置变更，原有 AGENTS.md、patches/ 未纳入提交。

SVG 只以 D-041 相同视口显示既有批准 PNG，内嵌图片字节与 `tripeer-logo-light.png` 完全一致，不重新绘制品牌。两个旧公共 PNG 路径与 `tripeer-logo-transparent.png` 字节一致，具有真实透明通道。未被前端引用的历史源图片保留；旧后端静态构建文件由 CI 正式构建覆盖并核验，不手工修改生成物。

## 验证

- 改动文件 ESLint 零警告；完整 TypeScript 检查通过。正式前端构建通过。
- 真实 Logo.vue/上传组件及项目 Vue、Pinia、Element Plus 样式：15 项布局/主题/配置场景通过，包含收起状态初次挂载顶部布局。
- 额外验证：快速展开后立即收起无旧标题、首页链接、同站绝对默认 URL、自定义/外部路径保留、缩略图完整比例、图标在 16/32/48px 的深浅底显示。
- 浏览器实际解码独立 SVG 并检查 32px 渲染：386 个明显不透明像素，角点透明，避免只凭文件成功响应判定图标存在。
- 12 项正式构建页面复测通过，包含 1920px 桌面、390px/320px 窄屏、深浅主题、注册及自定义/旧路径兼容；所有页面无横向溢出或未处理异常。已实际查看候选人深色与窄屏最终截图，标题与对应字标清晰。CI/CD 发布后补记线上证据。

证据为本轮新捕获，目录 `work/brand-audit/`，不使用旧截图作为本轮验收。候选人、登录、注册、加载检查来自完整正式前端；管理导航和上传预览来自真实组件隔离页面，未重新登录线上管理账号。标签图标证据是浏览器实际解码后的资源在图标尺寸的渲染，未声称捕获浏览器原生标签栏。不提交业务表单或改动业务数据；此检查不等同于全业务或完整可访问性认证。

## 发布

本地代码与资源完成修复，验证和 CI/CD 发布跟进中。
