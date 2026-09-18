# P1 简答题人工阅卷交付记录

日期：2026-09-18。依据 D-031 及用户批准的 `work/codex-previews/manual-grading/` 预览实施。

## 使用入口与业务规则

- 管理员：考核管理 → 人工阅卷。待阅卷列表支持筛选，详情包含题目导航、作答、快照参考答案/评分要点及评分栏。
- 简答题最多 5000 字符，自动保存；切题、交卷等待保存完成，保存失败保留文本并可重试。刷新恢复原卷与答案。
- 每题可打 0 至满分、最多两位小数；评语选填。未评分不能完成，零分需明确保存。完成前修改评分留痕，最终完成后只读并自动公布结果。
- HR 默认没有评分权限；候选人和员工不能访问阅卷接口，最终只查看是否通过。
- 候选人可在原有效期内使用原姓名和考核码查询待阅卷状态/是否通过，不延长有效期、不允许重考。过期由 HR 告知结果。

## 本轮文件

后端（路径相对于 `yf-bev2-api/src`）：

- 新增 `main/java/com/yf/modules/exam/grading/` 下控制器、事务服务及 3 个请求 DTO。
- 新增 `main/java/com/yf/modules/exam/paper/dto/request/PaperTextAnswerDTO.java`。
- 扩展 PaperQu 实体、作答/管理详情 DTO、PaperQuController/Service/ServiceImpl 及 PaperQuMapper/PaperMapper XML：文本作答、快照查询和管理端评分信息。
- 更新 ObjectivePaperPolicy、EmployeeAssignmentService：允许已支持的 short 组卷/发放，未知题型仍拒绝。
- 更新 ExamAssignmentServiceImpl：终态候选人原有效期内只查结果；ExamRecordServiceImpl：成绩汇总锁和历史同卷重复计数处理。
- 新增 `main/java/com/yf/config/MenuCacheStartup.java`：Flyway 后清理派生菜单缓存，使迁移新增菜单可见。
- ServiceExceptionHandler 不再记录带原始表单内容的校验异常，避免答案/凭据进入日志。
- 新增 ManualGradingIntegrationTest（10 项），更新 ObjectivePaperPolicyTest、PaperSafetyIntegrationTest、EmployeeAssignmentIntegrationTest 和 H2 schema。

前端（路径相对于 `yf-bev2-vue/src`）：

- 新增 `api/modules/exam/grading/index.ts`、`views/Exam/Grading/Index.vue`、`views/Exam/Grading/components/GradingDetail.vue`。
- 更新 `api/modules/exam/paper/index.ts`、`views/Exam/Exam/Client/Enter.vue` 与 `components/ExamTimer.vue`：文本保存队列、失败重试、交卷/离开保护和到期处理。
- 更新 `views/Exam/Candidate/Entry.vue`：终态直接查结果、返回入口同步最新状态，防止缓存仍显示开始按钮。
- 更新 `views/Exam/Assignment/components/CandidateResultDialog.vue`：管理端显示简答内容、参考答案、评分要点及评语。
- `permission.ts` 刷新时重新获取服务端菜单；`config/axios/service.ts` 去除完整响应日志。

现有 P0、员工门户、Word 导入等未提交修改均保留，没有提交或推送 Git。

## 数据库与接口

- V019：文本答案、评分状态、试卷版本、审计表、阅卷菜单与管理员权限。
- V020：阅卷日志表统一 `utf8mb4_general_ci`，修复实际 MySQL 联表排序规则冲突。已应用 V019 不回改，两项迁移均由 Flyway 成功执行。
- 备份：`work/backups/20260918-133637-before-grading.sql`（迁移前 V018）；SHA256 `0c6632ad62cd01e2a32a14559d2dbd5712fdb9b71bc49701c7622b9fa25413a1`。备份和临时凭据位于 Git 忽略目录。
- 新增 POST：`/api/exam/paper/qu/fill-text-answer`；`/api/exam/grading/positions`、`paging`、`detail`、`question/save`、`finalize`。
- 变更候选人 verify 的已交卷查询语义，管理结果详情增加简答字段。请求、响应、权限及版本重试规则见 `04-api-spec.md` 第 6、7 节。

## 验证

- 后端 81 项测试通过，包含 10 项人工阅卷测试；前端生产构建、改动文件 ESLint 通过。
- 答案队列 VM 回归：立即切题/交卷前保存、失败保留文本、失败阻止切题/交卷、重试和清空答案。
- 实际 HTTP/MySQL：未登录、普通员工/HR 越权、跨用户保存、5000 字上限、提交后修改拒绝、零分/超分、修改审计、旧版本冲突、三次并发完成只生成一次 FINALIZE 和一次成绩汇总。日志 `work/codex-logs/20260918-134256-grading-live.log`。
- 原有效期查询：候选人待阅卷/已完成均可重新认证，只返回本人最小结果；过期认证和再次开考被拒绝。
- 候选人浏览器：客观题+两道简答题输入、立即切题保存、刷新同卷恢复、交卷待阅卷提示、原考核码重新进入结果、返回入口状态同步。截图位于 `work/codex-previews/manual-grading/implemented/`。
- 管理端浏览器已验证：新增菜单可打开、待阅/已完成切换、姓名筛选、未保存切题保护、逐题评分和评语、显式零分、修改审计、最终确认、完成后及刷新后只读。候选人刷新后只显示“测评通过”，没有分数/答案。截图见 implemented 下 audit/completed/list/result.png。
- 最后一次后端测试及打包：`work/codex-logs/20260918-135621-grading-package.log`（81 项通过）；前端构建：`20260918-135603-grading-build.log`；lint：`20260918-135602-grading-lint.log`。
- 当前进程信息为 `work/grading-backend-process.json`，后端日志 `work/codex-logs/20260918-135725-grading-backend.log`。本次保留原密钥，并验证重启后仍可通过原会话验收。
- 临时账号、会话、试卷、评分日志和 Quartz 任务已清理，7 张原业务表记录数与备份一致（5/1/2/18/61/64/2），临时凭据文件已删除。清理日志 `work/codex-logs/20260918-140032-grading-cleanup.log`。`git diff --check` 通过。

## 当前限制与下一项

- 全量 TypeScript 检查仍有 32 项既有错误，本轮改动页面没有新增类型错误；不能称全库类型检查通过。
- 原 JWT_SECRET / ASSIGNMENT_CODE_PEPPER 在重启时只于内存继承，未轮换、未输出或落盘。可靠的关机后密钥恢复方案仍待建设。
- 历史缺失题面/答案无法重建，LEGACY_INCOMPLETE 阻止评分完成；不支持最终完成后的重新评分审批流程。
- 本轮不含 AI 评分、成绩导出、候选人 Excel 导入、压力测试及全面安全审计。优先补齐密钥持久化与登录限流/验证码强制策略，之后完善批量录入和成绩导出。
