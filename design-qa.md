# Logo 透明背景视觉验收

final result: passed

## 对照范围与证据

- 视觉规范：用户批准的最后一张局部效果图 `work/logo-transparency/approved-preview.png`（1505×1045 像素）。图中为放大的 Logo 局部；实际页面尺寸按既有规范保留。
- 原始页面参照：用户提供的两个 1920×911 截图，以及 `docs/LOGO_PROPORTION_DELIVERY.md` 中的 154/300px 显示规范。
- 合并比较输入：`work/logo-transparency/comparison.png`（1600×1000），将批准预览与真实浏览器局部截图放在同一画面；已实际打开核对。
- 完整页面：`work/logo-transparency/login-desktop.png`、`loading-desktop.png`，1920×911 CSS px，deviceScaleFactor=1。
- 局部页面：`login-focus.png` 为 460×80 CSS px、2 倍捕获（920×160）；`loading-focus.png` 为约 420×252 CSS px、1.7 倍捕获（714×427）。对照页按容器显示并明确标注放大倍率不同，未把概念图外框尺寸当作生产布局要求。
- 窄屏：`login-mobile.png`、`login-dark.png`、`loading-mobile.png` 为 390×844；`login-narrow.png` 为 320×844，deviceScaleFactor=1。
- 状态：未登录、无业务数据；浅色和深色主题；首屏加载状态通过阻止脚本启动捕获。

## 检查结论

- 字体与排版：TRIPEER 浅色字样在深底上清楚，加载页为灰色字样；企业标题仍用原字体、字号和字重。320px 延续原有换行方式，没有裁切或横向溢出。
- 间距与布局：原登录组件、Logo 盒子、20px 间距、页面分栏及加载圈位置未改变。实测桌面 Logo 分别为 154×52.19 和 300×101.61px，窄屏宽 116/260px；生成素材与原图比例带来的高度差不足 0.1 CSS px。
- 颜色：两处沿用原页面背景，登录字样为浅色、加载字样为灰色；蓝橙灰图形可辨识。概念图里的轻微渐变没有加入页面，保持原背景规范。
- 图片质量：实际显示尺寸及放大的截图中均没有原先的矩形白底，没有可见的白边或色块。两张 PNG 均有真实 alpha；保留源素材和其他使用位置。生成文件不是原图逐像素编辑，已按最终显示尺寸验收。
- 文案与内容：企业名称、登录表单、加载状态和按钮文案均未变化。
- 功能保护：自定义 `/custom-logo.png` 保留原路径；旧内置 `/logo.png` 正确映射到深底版本；主题切换正常。浏览器 8 项检查均无未处理异常。

## 对照历史

1. 设计阶段首次预览画布出现透明破边；在实施前已生成不透明干净画布的修订版，用户批准的是修订版。
2. 实施检查：在正式构建中检查桌面、窄屏与深色模式，查看完整截图指标和放大局部截图，再在合并输入中对照批准图。未发现需要修复的 P0/P1/P2 视觉差异。
3. 代码格式检查中的 CRLF 告警已修复；该修复不改变视觉输出，不计为视觉迭代。

## 剩余范围与实施清单

- [x] 去除两个指定位置的白底并区分深浅背景字色。
- [x] 保留页面背景、尺寸、文案及自定义 Logo。
- [x] 正式前端构建、完整类型检查、组件 Lint、8 项浏览器检查。
- [x] 检查合并对照图及局部显示质量。
- [x] 测试站 CI/CD 发布后完成 6 项真实 Chrome 检查，查看两处局部截图并核对线上 PNG 与源码 SHA256。

无未解决的 P0/P1/P2 或需单列的 P3 视觉问题。原始高分辨率图形没有逐像素等价声明，验收对象为页面中的实际显示效果。

## 线上复核（2026-09-24）

[CI/CD 成功运行](https://github.com/cqz-cio/Enterprise-Assessment-Question-Bank-System/actions/runs/35967669039) 发布 `50d43bd` 后，在相同桌面和窄屏视口复测；`work/logo-transparency/deployed/browser-report.json` 的 6 项检查全部通过。已实际查看 `deployed/login-focus.png` 和 `deployed/loading-focus.png`，两处透明背景、字色、尺寸和边缘与本地验收一致，无新增视觉差异。线上 PNG 哈希与源码文件完全一致。

## 侧栏补齐（2026-09-24）

按用户追加的侧栏位置，沿用此前批准的透明品牌图形，保留既有 30px 图标盒与系统标题；SVG 视口仅显示彩色标志。已查看 `work/sidebar-logo/expanded.png`、`collapsed.png` 的实际组件截图：无白底、无裁断、无英文残片，收起时中心对齐。10 项布局/主题/配置状态及首页导航检查通过，无未处理异常或视觉问题。该检查使用真实项目组件的隔离页面，未登录管理账号。CI/CD 运行 35979337448 已成功部署 c98fe7d；公网实际加载的 Layout 代码包含本次视口与居中处理，PNG 与源码哈希一致，当前内置 Logo 配置命中新逻辑。线上校验范围为公开资源及发布状态，未声称完成线上管理页截图验收。证据见 `work/sidebar-logo/deployed-resources.json` 与 `docs/SIDEBAR_LOGO_DELIVERY.md`。
