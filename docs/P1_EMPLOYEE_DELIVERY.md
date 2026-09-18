# P1 员工考核交付记录 · 2026-09-18

## 可用入口

- 管理员/HR：考试管理 → 员工考核（`/#/admin/exam/employee`）。
- 员工：考试中心 → 我的考核（`/#/client/exam/list`）。
- 新菜单需退出再登录刷新角色菜单缓存。员工必须已审核、启用，且归属模板所在部门。
- 交互按已批准预览实施；实际截图：`work/codex-previews/employee-assessment/implemented/`。截图中均为已清理的虚构验收数据。

## 本轮修改

后端新增 `assignment/controller/EmployeeAssignmentController.java`、`assignment/service/EmployeeAssignmentService.java`、`EmployeeAssignmentCreateDTO` 和 `EmployeeAssignmentQueryDTO`；既有 `ExamAssignmentServiceImpl` 仅将过期时整账号失效限制到候选人，员工其他任务与会话不受影响。新增 `EmployeeAssignmentIntegrationTest` 9 项集成测试。

前端新增 `api/modules/exam/assignment/employee.ts`、`views/Exam/Assignment/Employee.vue`、`components/EmployeeIssueDialog.vue`；替换 `views/Exam/Exam/Client/List.vue` 为个人任务门户。`CandidateResultDialog.vue` 支持独立授权的员工管理结果，`Enter.vue` 和 `ExamTimer.vue` 区分员工/候选人结果路由，`Result.vue` 规范路由参数类型，`ResultOnlyScore.vue` 返回“我的考核”。其他已有 P0 和 Word 导入修改保留，未提交 Git。

V018 只新增菜单、独立权限和个人列表索引，复用原分配唯一约束及单卷约束。七个新接口和字段见 `04-api-spec.md` 的 P1 员工任务接口；业务约定见 D-030。

## 验证结果

- 后端测试：71 项，失败/错误/跳过均 0；日志 `work/codex-logs/20260918-112832-p0-tests.log`（复用原有 bounded runner，文件名仍含 p0）。
- 后端打包成功：`work/codex-logs/20260918-113002-p0-package.log`。
- 正式前端最终构建成功：`work/codex-logs/20260918-114025-p1-build.log`；改动文件 ESLint 成功：`work/codex-logs/20260918-114027-p1-lint.log`；P0 保存队列与交卷等待逻辑回归成功。
- 全量 TypeScript 未全绿，剩 32 项既有错误；`work/codex-logs/20260918-114025-p1-types.log`。此次页面/API 无新增类型错误。
- 真实 MySQL/HTTP：四个并发批量请求总计仅新增两个任务；批次含未审核成员整批拒绝；本人查询忽略注入 userId；跨用户详情/结果/开考拒绝；员工无管理发放、名单和模板读取权限；HR 正常发放并可查看完整结果；停用拦截作答但保留员工会话；恢复相同试卷；交卷只展示通过并禁止重考；未来/过期任务不能开始。
- 浏览器：部门发放 3 人成功；手动重发 2 人显示 0 新增、保留原日期；单次作答提示、开始、保存答案、回到列表查看试卷截止时间、继续原卷、交卷及通过结果弹窗均通过。修正部门模式勾选一致性与弹窗间距，保留固定确认按钮。
- 测试脚本 `work/p1-live-smoke.py` 用唯一前缀和显式记录清理测试数据；本次清理成功，日志 `work/codex-logs/p1-live-cleanup.log`。临时密码只保存在已删除的 Git 忽略文件中。
- 清理后对比备份，原七张关键业务表计数完全一致（用户 5、分配 1、试卷 2、试卷题目 18、试卷选项 61、题库题目 64、成绩记录 2），V018 成功。`git diff --check` 通过。

## 本地运行与边界

更新前检查无进行中的未截止试卷，完成 V017 数据库备份；在内存中继承用户原启动进程的两项密钥，启动新版 JAR 于 8080，自动应用 V018。后台 PID/日志见 `work/p1-backend-process.json`，未关闭用户原 PowerShell，也未重设密钥。新运行日志级别为 INFO。

本次为本地功能和小规模并发验收，不是负载测试。500 人发放上限已服务端约束，尚未进行满额压力测试。保留原全量类型债务；依赖安装缺少 vue-eslint-parser 顶层链接，验证通过 NODE_PATH 使用本地已有依赖，未改变包版本。dev 内联 sourcemap 打包有 Vite 栈溢出，pro 构建成功。账号验证码仍有原先 API 可选策略，需认证专项处理。

下一任务建议：简答题人工阅卷队列、权限/审计、最终完成后自动发布是否通过；完成之前继续禁止包含有效简答题规则的新考核发放。密钥持久化和启动脚本需在正式部署前单独落实。
