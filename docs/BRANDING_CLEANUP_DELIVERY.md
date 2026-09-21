# 原项目品牌清理交付（2026-09-20）

## 修改范围

- `README.md`：改为当前项目介绍和工程文档入口，去除旧厂商官网、演示地址、宣传截图及联系方式；保留底座来源和 MIT LICENSE。
- `yf-bev2-vue/package.json`：当前项目描述和包标识，去除旧作者联系方式和项目推广链接；依赖及版本不变。
- 前端 `index.html`、`src/store/modules/app.ts`、`src/components/Footer/src/Footer.vue`、`src/components/Logo/src/Logo.vue`、`src/views/Login/Login.vue`、`src/locales/{zh-CN,en}.ts`：页面初始名称、缓存刷新、空页脚、Logo 比例及登录介绍清理。
- 前端 `public/`、`src/assets/imgs/logo.png`：复用已有 TRIPEER 图片、登录插图和本地头像，替换旧 Logo/favicon，所有默认图片均为本地资源。
- 后端 `BootExamApplication`、`SwaggerConfig`、`CacheKey`、`SysUser`、`SysUserServiceImpl`：启动和接口文档名称、配置/字典缓存版本、默认头像及有效会话展示数据刷新。
- 后端 `CNFilterFactoryBean`、`SysDepartServiceImpl` 的旧公司示例注释，以及 `SignUtils.main` 的外部演示地址已清理；业务方法不变。
- `yf_boot_exam.sql` 同步新环境默认展示；现有数据库通过 V022/V023 迁移。后端 `src/main/resources/static/` 由本次前端正式构建重新生成，避免 8080 入口继续运行原项目旧页面。
- `SysUserServiceImplTokenTest` 新增有效会话姓名/头像刷新且 token 不轮换的回归测试。

## 迁移和接口

V022 清理原公司配置和已知演示记录名称，改用本地默认头像。V023 清理已知旧演示考试下无考核分配的历史试卷标题。没有删除任何账号、试卷、题目或成绩，没有更改认证凭据、权限、题目/选项快照和作答。

无新增接口。`/api/sys/user/info` 返回最新姓名/头像，其他契约不变；API 文档见 `04-api-spec.md`，决策见 D-034。

## 验证

- 前端改动文件 ESLint、正式构建通过。构建日志：`work/codex-logs/20260920-112845-branding-build.log`。
- 后端 105 项测试全部通过；新回归验证旧会话展示刷新且 token 不变。日志：`work/codex-logs/20260920-112608-branding-package.log`。
- 两项迁移分别在临时 MySQL 数据库验证：重复执行结果一致，自定义配置/资料保留；V023 校验每个非标题字段保持一致。临时库已清理。
- 真实浏览器确认成绩页旧页脚/官网已移除、站点名称和头像更新，原 HR 会话正常；8080 登录页和候选人入口正常加载本地资源。
- 本机数据库备份：`work/backups/20260920-112502-before-branding.sql`；原 JAR 备份：`work/backups/backend-before-branding.jar`。备份均在 Git 忽略目录。
- V022/V023 已在本机运行服务通过 Flyway 应用。最终日志：`work/codex-logs/20260920-113158-162-backend.log`；当前进程记录 `.local/backend-process.json`。最后一次仅同步最终登录文案和已单独验证的 V023，后端代码未再修改，打包复用此前 105 项测试结果。
- 最终扫描数据库 197 个文字字段无旧公司/域名；运行源码和打包资源无匹配。7 张核心表行数一致，考核分配、题目/选项快照、作答、评分及除已知演示标题外的全部试卷字段保持不变。
- 最终浏览器读取确认成绩页标题为“企业人才考核系统 - 成绩查询”，无旧公司名称/官网，Logo 和头像图片加载成功；后端自带登录页显示当前企业考核介绍。

## 保留项和限制

- 旧名称只允许作为历史迁移的匹配条件、历史文档或底座来源记录；已执行迁移不能修改，否则会导致 Flyway 校验失败。
- LICENSE 及第三方依赖版权保留。内部 `com.yf`、`yf-bev2-*`、数据库/容器名称沿用兼容结构。
- 全量前端类型检查此前存在 32 项旧问题，本次未重跑；不声称已修复。
- 未提交或推送 Git；原有未跟踪 `AGENTS.md` 和 `patches/` 保留。

下一项建议：按原计划实施候选人 Excel 部分成功导入；生产上线前继续部署专项。

### 2026-09-21 发布整理

Linux 测试服务器部署前，将本节已完成的源码、静态资源和 V022/V023 归入单独提交，保留 AGENTS.md 与 patches/。
当前源码已随 Windows 运维专项通过 115 项后端测试及正式发布构建；Linux 发布使用 release profile，重新打包最新前端，不依赖这里保留的历史静态构建文件。
