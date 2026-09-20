# 候选人 Excel 批量导入交付（2026-09-20）

## 功能

候选人考核页新增“下载模板”和“Excel 批量导入”。上传标准 XLSX 后展示原始行号、匹配测评、有效期、逐行状态与原因；确认后逐行独立发放，有效行成功，错误和重复行跳过。支持错误/重复筛选、问题行报告、本次考核码清单下载和离开未保存提醒。

模板包含九个必填字段、填写说明及当前数据范围内的部门岗位编码参考。单次最大 500 行、5 MB，展开后最大 32 MB；拒绝公式单元格、异常日期、过期窗口、无权限/停用的部门岗位组合及缺失或歧义模板。日期以 Asia/Shanghai 解释。

## 修改文件

- `yf-bev2-vue/src/views/Exam/Assignment/Candidate.vue`：增加工具栏入口，保留原有发放及查询操作。
- `yf-bev2-vue/src/views/Exam/Assignment/components/CandidateImportDialog.vue`：上传、校验、结果及下载交互。
- `yf-bev2-vue/src/api/modules/exam/assignment/candidateImport.ts`：导入接口与 Blob 下载。
- `yf-bev2-api/src/main/java/com/yf/modules/exam/assignment/importing/`：控制器、校验/任务服务、工作簿读写、并发防重及启动菜单缓存刷新。
- `ExamAssignmentServiceImpl.java`：手工与批量发放共用数据库防重；拒绝过期窗口和歧义模板。
- `CandidateImportTest.java`：解析、部分成功、错误报告、任务归属/过期/权限、并发防重与事务回滚测试；另两处既有测试适配新增依赖。
- API、数据模型、决策、开发计划及交接文档已同步。本次未合并此前未提交的品牌清理和其他改动。

## 迁移与接口

V024 已由本机 Flyway 应用。新增 `el_candidate_issue_key`，以候选人编号和批次联合主键保护手工、批量发放，并回填历史键；不合并、不删除、不修改历史分配及试卷。新增 `exam:assignment:candidate:import`，默认 admin/HR 获得，员工/候选人不获得。

接口均为 POST，前缀 `/api/exam/assignment/candidate`：`import-template`、`import-validate`、`import`、`import-error`、`import-codes`、`import-close`。完整契约见 `04-api-spec.md` 4.2。

## 验证

- 后端完整 114 项测试通过，其中候选人导入新增 9 项；覆盖正常、错误、重复、并发、回滚、过期、身份与数据范围变化及工作簿边界。
- 前端 pro 构建和新增文件 ESLint 通过；完整类型检查仍有 32 项既有问题，新增文件没有新增类型错误。
- 实际 MySQL/HTTP：6 行输入得到 4 成功/1 错误/1 重复；4 个相同任务并发提交返回同一批结果；2 个任务同时发放同一编号只创建一次。新考核码可完成候选人认证，重新上传跳过原有分配。
- 未登录、员工、候选人、其他 HR 读取/提交/关闭他人任务均被拒绝。真实返回的两份 XLSX 经 ZIP/XML 校验：发放清单 4 行、问题报告 2 行，无公式节点。
- 浏览器实际 HR 会话验证入口、虚构 Excel 上传、校验、错误筛选、提交、成功统计和未保存提醒。下载按钮完成请求并触发保存；工具的浏览器下载事件确认超时，下载管理页访问被工具策略禁止，因此未声称验证浏览器最终落盘文件。文件内容已经由真实下载接口验证。
- 页面截图：`work/codex-previews/candidate-import/implemented-review.png`，与批准预览在相同桌面窗口比较后调整了步骤条；弹窗底部操作固定可见。
- 临时测试账号、角色关联、考核分配、防重键及会话已清理。八张相关表恢复原计数，原有试卷、作答、题库、结果未变。

主要日志位于 `work/codex-logs/`：`20260920-171919-candidate-import-test.log`、`20260920-171727-candidate-import-build.log`、`20260920-171742-candidate-import-lint.log`、`20260920-170956-candidate-import-types.log`、`20260920-171313-candidate-import-verify.log`、`20260920-171553-candidate-import-cleanup.log`。

## 运行与限制

更新前备份：`work/backups/20260920-171018-before-candidate-import.sql`（V023）。后端使用原 `start-backend.ps1` / `stop-backend.ps1` 管理，当前 PID 和日志见 `.local/backend-process.json`；JWT_SECRET 与 ASSIGNMENT_CODE_PEPPER 未变。最终 JAR 同时打包本次前端构建，原有未提交静态资源源文件保持原样。

导入任务与明文考核码只保留在后端内存：预览 15 分钟，完成后重新计时 15 分钟；每人最多 10 个、全局最多 100 个。关闭或服务重启失效；多实例需粘性路由，当前没有分布式任务恢复。断网可对同一 taskId 重试；如服务重启，应重新上传，已有记录跳过，缺失口令通过现有重置入口处理，不创建第二次作答机会。

下一项建议是生产部署与备份恢复专项，随后处理既有前端类型债务。
