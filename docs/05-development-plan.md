# 开发计划与工作规范

> 版本：V0.1  
> 当前阶段：Phase 1 已完成；Phase 2 已扩展完成部门岗位职级结构、模板、候选人分配和候选人入口，迁移已接入 Flyway，待完整浏览器联调验收；Phase 0 环境基线仍有待整理

## 1. 总体策略

- 先保证鉴权、数据归属和历史快照正确，再扩展业务页面。
- 按可独立验收的纵向功能开发，避免先一次性改完所有数据库再长期不可运行。
- 保留现有 `Repo/Exam/Paper` 内部命名，首期不做大规模重命名。
- 每个阶段完成后，后端启动、前端构建和关键业务回归必须通过。
- 用户当前为单人开发，可以在 `main` 上开发，但每个功能保持小提交；达到可发布基线后再采用功能分支。

## 2. Phase 0：冻结可运行基线

### 目标

把当前“本机能够运行”的状态变成可重复搭建、可回滚的工程基线。

### 工作项

- [ ] 检查并提交 `compose.yaml`。
- [ ] 将 `work/`、后端 `logs/`、前端依赖和构建产物加入 `.gitignore`。
- [ ] 复核 `application-dev.yml` 的改动，只保留必要配置。
- [ ] 提供不含生产密钥的 `.env.example` 或配置说明。
- [ ] 编写本地启动、停止和健康检查命令。
- [ ] 创建新的可运行基线标签，例如 `baseline-runnable-v1`。

### 完成定义

- 新环境按照文档可以启动 MySQL、Redis 和后端。
- 数据库中 Quartz 表名与 `tablePrefix` 一致。
- 登录、题库列表、考试列表接口可以访问。
- `git status` 不包含日志和临时目录。

## 3. Phase 1：鉴权安全和角色基线

### 目标

先修复正式上线会影响身份安全和试卷隔离的问题。

### 工作项

- [x] JWT 密钥外部化。
- [x] Shiro Realm 调用 JWT 签名验证。
- [x] 修复旧 token 与 Redis 会话绑定策略。
- [x] 所有考生试卷接口增加 owner 校验。
- [x] 初始化 `HR`、`EMPLOYEE`、`CANDIDATE` 角色和菜单权限。
- [x] 员工自由注册默认待审核，由系统管理员或 HR 审核通过后分配员工角色。
- [x] 候选人角色只拥有必要考试接口权限。

### 完成定义

- 篡改 JWT 后无法访问任何受保护接口。
- 用户 A 无法查看、答题或提交用户 B 的试卷。
- 候选人 token 无法访问后台管理接口。
- 待审核员工无法登录考试。

### 实际结果（2026-09-02）

- 代码和迁移已完成，新增 JWT/Realm/Redis 会话绑定、试卷 owner 与权限注解测试。
- 修复账号密码为空时绕过密码校验的底座漏洞。
- 角色或账号状态改变时撤销 Redis 登录会话，并关闭 Realm 长期授权缓存，避免旧权限继续生效。
- 底座中遗漏权限注解的配置、插件、上传、菜单、字典和部门管理接口已补齐后端校验。
- `V001`、`V002` 已放入后端标准 Flyway 目录，由应用启动时按版本自动执行；启动前仍需提供至少 32 字符的 `JWT_SECRET`。
- 后端注册契约和前端注册页均已补齐工号、部门、选填手机和选填邮箱；注册成功不自动登录，明确提示等待审核。

## 4. Phase 2：岗位、模板和考核分配

### 目标

打通“部门 -> 岗位 -> `INTERVIEW` 模板 -> HR 录入候选人 -> 6 位考核码进入”的招聘面试闭环，并为转正、晋升目标职级预留完整结构。

### 工作项

- [x] 新建 `el_position` 和岗位后端 CRUD。
- [x] 新增岗位管理页面。
- [x] 新建 `el_depart_position`、`el_position_grade`，支持岗位跨部门复用和职级维护。
- [x] 题库分类收敛为入职招聘、员工晋升；应用场景改为面试、转正、晋升。
- [x] 扩展题库和考核模板的岗位、场景、启用状态字段。
- [x] 新建 `el_exam_assignment`。
- [x] 开发 HR 候选人手工录入和列表页面，字段包含姓名、候选人编号、手机号、邮箱、岗位、批次、开始时间和截止时间。
- [x] 自动匹配部门与岗位的 `INTERVIEW` 面试考核模板。
- [x] 创建候选人内部用户和角色。
- [x] 生成、哈希保存、重置和停用 6 位考核码。
- [x] 候选人表单默认生成 14 天有效期，并允许 HR 在发放前调整。
- [x] 新增 `/exam-entry` 候选人身份验证页面。
- [x] 开发 `create-by-assignment` 并支持恢复原试卷。
- [x] 在分配与试卷之间增加唯一约束，交卷或过期后禁止重考。
- [x] 为管理员和 HR 增加候选人已完成结果的只读详情入口，并使用候选人分配范围的独立权限。

### 实际结果（2026-09-03）

- 新增 `V003`～`V007`，实现岗位、题库/模板扩展、考核分配、部门岗位职级结构、题库分类及 `paper.assignment_id` 唯一约束。
- 候选人考核码只在创建/重置时返回一次；库内保存带服务端 pepper 的查询摘要和 PBKDF2 盐化哈希。
- 候选人 JWT 有效期不超过分配截止时间，分配校验、试卷 owner 校验和唯一试卷恢复已接入答题链路。
- 候选人列表已增加已完成结果详情；HR 通过独立权限读取分配绑定的唯一试卷，不获得管理员全量考试记录权限。
- 后端完整测试通过；前端新增页面相关类型错误已清零。全量前端类型检查仍受项目原有组件缺失/类型债务影响，Vite 构建在本机无输出 60 秒后按防阻塞规则终止。
- 迁移已接入 Flyway，关键页面仍需在应用启动并自动迁移目标数据库后做浏览器联调。

### 完成定义

- HR 不创建岗位或组卷规则，只按部门选择固定岗位即可发放考核。
- 候选人无需注册，通过姓名和考核码进入正确考核。
- 考核码不能进入其他岗位或其他人的考核。
- 刷新或重新打开页面可以恢复未完成试卷。
- 已交卷或已过期的分配不能创建第二张试卷；再次考核必须由管理员或 HR 新建分配。

## 5. Phase 3：题库企业化和不可变试卷

### 目标

使随机组卷在真实企业使用中具备可追溯性。

### 工作项

- [x] 题目启用/停用字段、编辑和列表展示。
- [x] 随机抽题只选择启用题目。
- [x] 试卷题干、选项、标准答案和解析快照（V017；历史回填局限见 D-029）。
- [x] 答题和结果接口改为读取快照。
- [x] 选项随机排序并保存最终顺序。
- [ ] 检查题目数量不足时的错误提示。
- [x] 按分配创建试卷事务、唯一约束和重复请求幂等；旧模板建卷入口关闭。

### 完成定义

- 修改或停用题目不改变历史试卷。
- 同一模板的两名答题人能够获得不同题目或顺序。
- 选项乱序后仍能正确判分。
- 并发点击开始只生成一份试卷，并在该分配整个生命周期内保持唯一。

## 6. Phase 4：简答题和人工阅卷

### 目标

完成主观题从出题、作答、交卷、阅卷到最终成绩的闭环。

### 工作项

- [x] 增加 `short` 题型字典。
- [x] 题目编辑和 Excel 导入支持参考答案、评分标准及无选项保存。
- [x] 答题页面增加多行文本输入和自动保存。
- [x] 交卷后进入 `PENDING_REVIEW`。
- [x] 建立待阅卷列表和阅卷详情页。
- [x] 支持单题评分、题目评语和评分范围校验。
- [x] 建立阅卷变更记录。
- [x] 全部评分后计算最终成绩和通过状态。

### 完成定义

- 未阅卷的简答题不能错误计为零分并直接完成考核。
- 阅卷分数不能超过题目满分。
- 最终成绩等于客观题分数与主观题分数之和。
- 二次修改评分能够留下记录。

## 7. Phase 5：员工考核、Excel 和报表

### 工作项

- [ ] 员工注册字段和审核流程优化，手机号和邮箱保持选填。
- [ ] 由系统管理员或 HR 按员工或部门分配 `INTERVIEW`、`REGULARIZATION`、`PROMOTION` 考核。
- [ ] 员工“我的考核”和历史记录。
- [x] 题目 Excel 模板下载、预校验和批量导入；正确行导入、重复题跳过、错误行返回报告。
- [ ] 候选人 Excel 导入，正确行导入、错误行返回报告。
- [ ] 考生端结果仅展示是否通过，不返回分数、答案和解析。
- [ ] 客观题结算或主观题最终阅卷后自动公布是否通过，不增加人工发布步骤。
- [x] 按筛选条件导出成绩。
- [x] 增加岗位、场景、批次和阅卷状态筛选。

### 完成定义

- 员工审核通过后能看到分配给自己的考核。
- 导入正确行落库，错误行不落库并精确定位到行和字段。
- 候选人和员工只能看到是否通过，管理端按权限查看完整成绩。
- 最终结果形成后自动对考生可见，无需 HR 再次发布。
- 导出数据与页面筛选条件一致且不越权。

## 8. Phase 6：产品化和内网部署

### 工作项

- [ ] 替换原项目名称、Logo、演示文案和菜单名称。
- [ ] 关闭或限制生产 Swagger。
- [ ] 前端生产构建。
- [ ] 后端、前端/Nginx、MySQL、Redis 的部署编排。
- [ ] 数据库备份和恢复演练。
- [ ] 局域网访问范围和防火墙策略。
- [ ] 生产配置、日志轮转和健康检查。

## 9. 推荐提交粒度

示例：

```text
docs: establish product and engineering baseline
chore: ignore local logs and work files
fix(auth): verify jwt signature and bind active session
feat(position): add fixed position management
feat(assignment): add candidate assessment assignment
feat(candidate): add access-code entry flow
fix(paper): enforce paper ownership
feat(paper): persist immutable question snapshots
feat(grading): add short-answer review workflow
feat(report): export filtered assessment results
```

不要把数据库迁移、后端、前端和无关格式化混入一个巨大提交。

## 10. 每次开发任务的工作记录

建议在开发会话结尾按以下格式记录：

```markdown
## 本次目标

## 已完成
- 文件/接口/迁移

## 验证结果
- 执行的测试和结果

## 尚未完成
- 明确剩余内容

## 风险或决策
- 新发现问题

## 下一步
- 最小可执行任务
```

## 11. 开发前检查清单

- [ ] 已阅读 `docs/AI_HANDOFF.md` 和相关专题文档。
- [ ] 已执行 `git status`，没有覆盖用户未提交修改。
- [ ] 已确认当前阶段和任务边界。
- [ ] 已检查数据库结构与实体、DTO、Mapper 的一致性。
- [ ] 涉及接口时已检查 `04-api-spec.md`。
- [ ] 涉及 UI 视觉变化时先确认设计方向，再修改页面。
- [ ] 测试和构建使用有限超时且不进入 watch 模式。

## 2026-09-07：试题 Excel 导入完善

- 判断题页面示例与模板、后端统一为“正确/错误”。
- 确认导入响应携带本次问题行报告，完成页可直接下载，避免导入后重新校验将成功行误报为重复；报告包含原始行号。
- 空模板返回明确提示；下载接口返回 JSON 错误时展示消息，不保存伪 XLSX；零成功结果显示警告。
- 无数据库迁移；`/api/exam/repo/qu/import` 增加可选 `errorReportBase64` 响应字段。
- 验证：导入服务 6 项测试通过；Vue SFC 脚本、模板编译及 TypeScript 转译通过；git diff --check 通过。
- 验证限制：ESLint 缺少 vue-eslint-parser，浏览器工具因 Windows 沙箱 ACL 初始化失败无法运行，尚未完成实际上传验收。没有重启正在运行的应用。
- 下一步：重启/部署更新后验收模板下载、混合有效/重复/错误行导入及完成页报告，再实施不可变试卷快照。

### 导入按钮不可见修复（2026-09-07）

定位到管理员数据库授权已存在，但 Redis 登录快照和浏览器缓存缺少 repo:qu:import。会话 info 接口改为读取当前权限，路由初始化前同步。无新增数据库迁移、无扩大角色权限。会话/令牌 6 项测试通过，运行中的 info 接口已验证返回导入权限。

浏览器验收通过：在用户原 Chrome 页刷新后已出现“Excel 批量导入”，选择原题库可打开含模板下载和上传校验的完整窗口。真实模板接口返回有效 XLSX。

## 2026-09-07：Word 题目直接导入完成

- 用户已明确授权按预览实施（D-028）。试题管理保留 Excel 入口，增加 Word 批量导入：模板下载、文件校验、逐题预览、部分成功入库、重复跳过、错误报告及缺失难度统一补充。
- 后端新增 WordQuestionParser、WordQuestionTemplateWriter、WordQuestionPreviewDTO；修改 RepoQuController、QuestionImportService/Impl、QuestionImportWorkbookService、导入预览/问题 DTO。Word 与 Excel 复用业务校验和入库流程，未增加依赖或数据库迁移。
- 前端修改 Qu.vue、QuImportDialog.vue、DataTable.vue 的 actions 插槽、题目 API 与 types；同步需求、决策、API 和交接文档。
- 新增四个接口：GET /api/exam/repo/qu/import-word-template；POST /api/exam/repo/qu/import-word/validate、/import-word、/import-word-error-report。统一要求 repo:qu:import，详见 04-api-spec.md。
- 验证：Maven 全量 41 测试通过（0 失败/错误）；Vue SFC 脚本、模板与 TypeScript 转译通过；git diff --check 通过。实际服务验证 DOCX 模板、未登录拦截、4 有效 + 1 重复 + 1 错误、正确持久化和重复导入不新增。测试题库及题目已清理，原有 64 道题保留。
- 浏览器已验证实际入口、题库选择、弹窗与底部操作可见；修正 Element Plus 弹窗样式作用域。自动文件上传工具返回 Not allowed，因此浏览器上传到完成页的整段流程未完成自动验收，不能等同于浏览器全流程通过。ESLint 仍受既有缺少 vue-eslint-parser 依赖限制。
- 后端已重启运行最新代码；日志 work/codex-logs/20260907-154708-backend-word-import.log；测试日志 work/codex-logs/20260907-154413-word-tests.log。本地访问 http://localhost:8000/#/admin/repo/qu。
- 当前边界：规范 DOCX 正文文字段落，最多 1000 题/10MB；不支持扫描图、公式、表格或自动编号；边界歧义阻止导入，可隔离的字段错误只阻止对应题目。
- 下一步：用甲方真实 Word 小样核对模板兼容性并补齐浏览器上传验收；后续继续不可变试卷快照。

## 2026-09-17：P0 考试正确性修复

### 已实现

- 简答题/未知题型不能进入新正式试卷，客观题保存接口拒绝主观题、空评分键和非法选项；历史含主观题试卷关闭作答后等待阅卷，不发布错误结果。
- 新卷不可变快照、管理/考生查询切换到快照、原评分键保留、选项顺序随机并固定。历史回填只冻结迁移时可用内容，缺失明确标记。
- 答题保存与交卷使用分配 → 试卷行锁；READ_COMMITTED 保证等待锁后读取最新答案；事务失败整体回滚，重复交卷只更新一次成绩。
- 到期交卷绕过最低作答时间；30 秒周期、每批最多 100 卷的补偿扫描覆盖任务丢失/服务停机。失败条目不会阻止后续试卷，停用分配不会被重新激活。
- 旧模板开考/详情入口拒绝访问，旧员工模板列表返回空页；本人 assignment 才能建卷或恢复，截止/交卷后不可重考。
- 前端答题请求排队，响应绑定原题，交卷等待所有保存完成；失败题未重存成功前阻止主动交卷；待阅卷结果不误显示为未通过。

### 本轮修改文件（不含原有 Word 导入改动）

- `yf-bev2-api/pom.xml`：增加 test scope 的 H2，仅用于事务回归。
- `assignment/service/impl/ExamAssignmentServiceImpl.java`：开始/恢复、验证锁和结果关联校验。
- `exam/controller/ExamController.java`、`exam/service/impl/ExamServiceImpl.java`：旧入口收口、客观题规则和服务端总分校验。
- `paper/service/ObjectivePaperPolicy.java`、`PaperAccessService.java`、`PaperQuService.java`、`impl/PaperQuServiceImpl.java`、`impl/PaperServiceImpl.java`：规则、快照、锁和结算。
- `paper/entity/Paper.java`、`PaperQu.java`、`PaperQuAnswer.java`；`paper/dto/PaperDTO.java`、`dto/response/PaperQuDetailDTO.java`、`PaperResultRespDTO.java`；`paper/mapper/PaperMapper.java`。
- `jobs/OverduePaperRecovery.java`。
- `resources/mapper/modules/exam/paper/PaperMapper.xml`、`PaperQuMapper.xml`；`resources/mapper/modules/exam/exam/ExamRecordMapper.xml`。
- `resources/db/migration/V017__paper_snapshots_and_settlement_safety.sql`。
- 新增 `PaperSafetyIntegrationTest.java`、`ObjectivePaperPolicyTest.java`、`OverduePaperRecoveryTest.java`、`src/test/resources/paper-safety-schema.sql`。
- 前端 `src/views/Exam/Exam/Client/Enter.vue`、`components/ResultOnlyScore.vue`；无布局/样式重设计。
- 文档 `03-data-model.md`、`04-api-spec.md`、`05-development-plan.md`、`08-decision-log.md`、`AI_HANDOFF.md`。

上述后端 Java 简写路径均相对于 `yf-bev2-api/src/main/java/com/yf/modules/exam/`；resources 相对于 `yf-bev2-api/src/main/`。

### 验证

- Maven 全量 **62 项测试通过，0 失败/错误/跳过**；本轮新增 21 项，其中 15 项使用真实 H2 连接、Spring 事务及 MyBatis Mapper，覆盖保存/交卷双向竞态、重复交卷、回滚、跨用户、到期、停用、快照、主观题隔离和按分配建卷。
- 日志：`work/codex-logs/20260917-171423-p0-tests.log`。本机依赖缓存来源 ID 不一致，通过 work 下临时 settings 识别现有缓存，不改全局 Maven 配置；沙箱 javac 缓存访问失败后在正常本机权限下成功编译运行。
- 在 `yf-exam-mysql` 内创建随机 `codex_p0_test_` 临时库，只复制业务表结构并写虚拟数据，V017 **8 项检查通过**；包括评分键保持、内容回填、缺失标记、待阅卷隔离和源题修改/删除后快照不变。临时库已删除，未写业务库。
- 迁移日志：`work/codex-logs/20260917-170758-p0-mysql-migration.log`。
- 两个变更 Vue 组件的 SFC/TypeScript 转译通过；基于实际 Enter.vue 脚本验证有序保存、原题回调、交卷等待、失败阻止与重存恢复。日志：`work/codex-logs/p0-frontend-check.log`。
- 全量前端类型检查仍为原有 34 项错误，变更组件无新增错误；日志 `work/codex-logs/p0-types.log`。
- `git diff --check` 通过。
- 浏览器确认现有本地前端登录页可加载；当前没有登录会话，且业务后端未部署 V017，因此未声称完成浏览器答题全流程验收。

### 部署状态、限制和下一步

- 本轮完成源码、迁移和自动化验证，**未重启现有业务服务、未对业务库执行 V017、未提交 Git**；保留原有未提交改动。
- 部署时先备份并停止旧版后端，再启动新版由 Flyway 应用 V017，禁止旧新后端混写。部署后用虚拟账号完成候选人开始/恢复/交卷、停用和权限浏览器联调。
- H2 事务测试不等同于 MySQL 并发压测；MySQL 已验证迁移和快照数据行为，仍需部署环境并发验收。
- 历史已被修改/删除的原题面无法恢复；待阅卷的历史主观题要在 P1 人工阅卷完成后才能形成最终结果。
- 员工发放与“我的考核”门户仍待 P1，本轮旧模板入口关闭后不再提供任意开考能力。
- 建议下一项：部署并验收这批 P0，随后完成员工分配门户和人工阅卷。

## 2026-09-18：P0 本地运行验收

- 已确认现有后端运行 P0，V017 于 08:58:05 成功应用，checksum 与源码一致。本轮保留现有进程与密钥，未重复迁移/重启；无业务代码、API 或新数据库版本变更。
- 构建通过：`work/codex-logs/20260918-090627-p0-package.log`；使用上轮已通过的 62 项测试结果，本轮重点补齐真实 HTTP/MySQL 与浏览器联调。
- 备份 `work/backups/20260918-090708-before-p0.sql` 已校验 SHA256 并在随机临时 MySQL 库完成恢复验证；7 张关键表记录数和 Flyway 版本匹配，临时库已删除。备份目录已排除 Git；注意备份时数据库已经是 V017。
- HTTP 验收通过：四并发开考同一张试卷；四并发交卷只结算一次且次数为 1；源题/选项/评分键修改不影响已建卷；未登录、跨用户读写交卷及候选人访问管理详情被拒绝；结果只发布通过状态；交卷后禁止再答/重考；过期保存被拒绝，30 秒后台扫描在 40 秒验证窗口内完成结算并忽略最短作答限制；停用后失效会话不能写入。
- 浏览器验收通过：候选人登录、开考、作答、刷新后答案恢复、交卷、结果页仅展示通过状态。测试数据为专门创建的临时岗位/题库/候选人，不使用真实候选人凭证。
- 临时业务数据、会话和对应 Quartz 任务已清理，7 张原业务表记录数保持不变；清理日志 `work/codex-logs/20260918-091347-p0-live-cleanup.log`。
- 新增本地辅助文件：`work/p0-backup.py`、`work/p0-backup-restore-check.py`、`work/p0-live-smoke.py`、`work/run-p0-package.ps1`、`work/run-p0-live.ps1`、`work/backups/.gitignore`。验收脚本使用唯一标识记录临时数据，认证信息只保存在内存或 Git 忽略目录；后续统一通过 `run-p0-live.ps1 -Action verify|cleanup|restore` 执行，120 秒总时限、60 秒无输出终止，写独立日志。verify 为后续浏览器步骤保留临时数据，必须在浏览器结束后执行 cleanup。
- 边界：这次是小规模真实并发验收，不是负载测试；不恢复无法追溯的历史原题。尚未确认原启动密钥的持久化来源，不能擅自替换；普通账号可选验证码及登录限流需后续认证专项处理。无 Git 提交。
- 下一项：优先实现员工考核发放（HR/管理员）与“我的考核”（员工），复用 assignment 单卷生命周期；随后开发简答题人工阅卷、最终发布及审计。

## 2026-09-18：P1 员工考核门户完成

- 已按用户批准的预览实现员工任务管理、双栏发放弹窗和个人任务门户，七个 API、独立权限及 V018 迁移已接入真实运行环境。
- 支持有效员工多选/部门当次名单、14 天默认有效期、自然键幂等发放、停用/恢复、未来/过期状态、继续原卷与只展示通过结果。题型/题量/题库/及格分和员工部门由后端校验。
- 71 项后端测试、正式前端构建、改动文件 ESLint、P0 保存队列回归、真实 HTTP/MySQL 及浏览器完整流程通过。完整 TypeScript 剩 32 项旧问题；新增页面没有新增错误。
- 修改文件、迁移/接口、日志、实际页面截图和运行注意事项详见 `P1_EMPLOYEE_DELIVERY.md`。测试临时数据已清理；无 Git 提交。
- 下一项：人工阅卷与最终结果发布闭环；同步安排部署密钥持久化、认证限流与前端类型/构建环境整理。

## 2026-09-18：P1 简答题与人工阅卷

- 已实现 Phase 4 业务闭环与 D-031；V019/V020 已运行，详见 `P1_GRADING_DELIVERY.md`。
- 后端 81 项测试通过；真实 HTTP/MySQL 验证权限、评分范围、显式零分、修改留痕、旧版本拒绝、三次并发完成只结算一次、本人结果最小化、原有效期重登录和禁止重考。
- 前端生产构建、修改文件 lint、答案保存队列回归通过；候选人浏览器验证切题/刷新保存、待阅卷提示、结果页返回入口的状态同步及原考核码再次进入结果页。管理端浏览器验收状态见交付文档。
- 下一项优先补齐密钥持久化与认证限流/验证码策略，之后候选人 Excel 和成绩导出；继续保留既有用户改动。

## 认证安全与本地启动交付（2026-09-18）

已完成 D-032：原密钥 Windows 用户加密保存、一键启动/停止/重启、强制一次性验证码、IP 与账号/考核码原子限流、429/503 处理及失败自动换图。94 项后端测试、前端正式构建和改动 lint、真实 Redis/HTTP、浏览器登录及无环境变量重启验收通过；测试数据已清理，无新迁移。具体文件、日志、限制和下一步见 [P1_AUTH_DELIVERY.md](./P1_AUTH_DELIVERY.md)。生产部署专项及原有类型债务仍未完成。


## 2026-09-20：成绩查询和 Excel 导出

已按批准预览实现数据范围受控的分配级成绩查询、详情和 18 列 XLSX 导出，V021 新增 admin/HR 菜单权限。待阅卷最终字段留空；零分筛选、跨页导出及 GRADED 旧汇总回归通过。全量后端 104 项、前端构建与 lint 通过，32 项旧类型问题未扩大。修改文件、迁移、API、真实 HTTP/MySQL、浏览器及清理结果见 `P1_RESULT_REPORT_DELIVERY.md`。下一项候选人 Excel 部分成功导入。
