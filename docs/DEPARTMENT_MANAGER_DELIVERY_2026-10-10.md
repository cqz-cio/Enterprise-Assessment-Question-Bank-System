# 部门主管与部门隔离交付（2026-10-10）

## 业务结果

以聊天最后确认的“保留员工入口，新增主管”为准。管理员可按部门配置主管账号，主管仅能发放本部门已有考核、审核本部门员工、查询及导出本部门结果、人工阅卷。HR 预先准备任务，员工继续以本人身份开始答题。

主管范围精确匹配一个启用的业务部门，不包含子部门，也不以创建人代替部门归属。HR 发放给本部门的历史任务可由主管处理。列表、数量、选项、详情、导出、评分及终审均校验当前身份，旧会话不能保留已失效的部门权限。

## 配置与操作

1. 在目标环境部署新版后端，由 Flyway 自动应用 V026；保留原密钥和业务数据，不手工重复执行 SQL。
2. 管理员打开“组织架构 → 人员管理 → 添加”，填写账号、姓名、初始密码，选择一个启用的业务部门及唯一的“部门主管”角色。日常按每部门一个账号配置。
3. 主管从原账号登录页进入。员工列表仅显示本部门员工；选中待审核员工后，在“批量操作”中通过或驳回。
4. 发放、查看成绩和阅卷使用现有页面。主管默认不获得用户编辑删除、角色授权、题库/模板配置或候选人 Excel 导入权限。

迁移不创建通用账号、不提供默认密码、不转换原员工账号。实际用户名、密码和部门由管理员配置。

## 迁移与接口

- 新增 V026__department_manager_role.sql：插入 DEPT_MANAGER（data_scope=2、role_level=90）和明确列举的菜单权限；复用既有用户、部门和角色表，无新增业务表或字段。
- 新增 POST /api/sys/user/registration/audit-batch：1～100 个员工 ID、APPROVE/REJECT、可选审核意见；去重、稳定锁顺序、行锁和整批事务回滚，重复审核不覆盖原记录。
- 原用户查询/审核、部门树/岗位选项、员工/候选人发放及操作、成绩和阅卷接口增加部门边界，保留请求响应结构。完整契约见 [API 文档](./04-api-spec.md)。
- 主管创建、修改和角色分配禁止混合角色或无效部门；鉴权刷新当前角色及部门。考生本人任务、唯一试卷及只展示是否通过的接口不变。

## 首轮交付验证

| 检查 | 结果 |
| --- | --- |
| 后端完整测试 | 131 项通过，0 失败；含新增 8 项部门范围测试 |
| 真实 MySQL/Redis/HTTP | 155 项通过，其中部门主管专项 45 项 |
| Chrome 实际页面 | 16 个场景累计通过、未处理异常 0，包含主管审核/阅卷/成绩 |
| 答题保存队列 | 顺序、文本刷新、失败阻断/重试和空答案通过 |
| 前端 | 全量类型检查、两组件 ESLint（0 警告）及正式构建通过 |
| 发布包 | 140 个前端文件逐字节一致，V026 与源码一致 |
| 清理 | 专用数据库、Redis 和后端全部清理；源库 40 表行数与校验值不变 |

验证由完整业务回归及最后 2 个主管浏览器场景的定向复核组成。完整编排的原始报告保留浏览器断言失败状态；修正测试读取方式和等待条件后，定向复核通过。业务代码在这两次浏览器复核之间未改变。

本地证据（工作日志不提交 Git）：

- work/codex-logs/qa-run-20261010-093157.json：155 项接口业务检查所在的完整编排及清理记录，保留当时的浏览器断言失败。
- work/codex-logs/qa-manager-run-20261010-093513.json：主管专项与实际页面最终通过、资源清理成功。
- work/codex-logs/qa-api-results.json、work/qa-browser/results.json：最终用例状态，分别为 155 和 16 项通过。
- work/codex-logs/20261010-092621-f01ff0a3-ops.output：131 项后端测试及 release 构建。
- work/codex-logs/20261010-092448-01678142-ops.log：正式前端构建。
- work/codex-logs/department-manager-package.json：前端/迁移文件一致性及包哈希。
- work/codex-logs/20261010-093619-9928f4aa-ops.output：最后一轮源库 40 表不变及资源清理证据。
- work/qa-browser/department-manager-employee-audit.png、department-manager-grading.png、department-manager-result.png：虚构数据的页面截图。

## 修复过程与环境

真实 MySQL 迁移检查发现新授权关系 ID 超过既有字段长度，已修正并在新临时库验证。浏览器断言改为读取评语文本框值，并等待成绩筛选请求完成。历史静态构建文件已从发布输出中清理，重新打包后逐字节核对当前资源。

Docker 因失效的临时通信端点无法启动，保留旧运行目录后恢复；未重置容器或数据卷。记录位于 work/codex-logs/department-manager-docker-final-start.json。

## 用户流程复验与修复（2026-10-10 下午）

按用户要求在第二套本地隔离环境完成“主管审核 → 发放 → 员工答题和刷新恢复 → 交卷 → 主管阅卷 → 结果/导出 → 另一部门隔离 → 员工查看通过状态”。使用餐具部和家具部的虚构主管、员工及真实记录，未操作真实人员数据。

本轮覆盖的部门权限路径未发现跨部门越权：两个部门互相不能读取成绩及答卷详情；篡改部门筛选、批次关键字、用户 ID、旧接口或个人资料字段均不能扩大范围；共享批次关键字的导出只含本部门行；混合部门发放和审核整批拒绝且无部分写入。主管不包含下级部门，错误配置全量数据范围仍不能扩大主管权限；部门停用后旧会话失效。HR 发放的本部门任务正常可见和可阅卷。

发现并直接修复两处问题：

1. 主管的批量菜单误显示“启用/禁用”。后端原已拒绝无权限操作；共享 DataTable 改为根据当前权限过滤后渲染，实际页面复查只显示审核通过/驳回。
2. 本人通过旧答题链接，在交卷、停用或过期后仍可能读取题目和已保存答案；交卷后的页面还会短暂出现题目及异常倒计时。后端答题卡和题目读取统一校验可作答状态；前端先确认试卷状态，已交卷直接进入结果页，计时未加载时显示校时状态。有效试卷仍可保存并刷新恢复同一份答案，网络校时失败保留本地答案并重试。

第 2 项收紧 `POST /api/exam/paper/qu/list-card` 和 `POST /api/exam/paper/qu/detail-for-answer` 的允许状态，请求/响应字段不变。结果只展示是否通过、管理端授权阅卷和结果查询保持可用。本轮修复无新增迁移，沿用 V026。

| 本轮复验 | 结果 |
| --- | --- |
| 后端完整 release verify | 135 项通过，0 失败、0 跳过，含 4 项新增答题读取边界测试 |
| 真实 MySQL/Redis/HTTP | 158 项累计通过，0 失败；修复后定向重跑部门隔离 28 项、答题读取 9 项、过期/停用 11 项 |
| 实际浏览器流程 | 完成上述业务闭环及导出文件检查；修复后再查菜单、旧链接跳转，以及新任务开考/保存/刷新/交卷 |
| 阅卷及结果 | 管理端客观 30 分、主观 8 分、总分 38/40，通过；员工端只见“考核通过”，不显示分数、答案或评语 |
| 导出 | 浏览器真实下载 XLSX，18 列、1 条餐具部记录，和筛选结果一致，无其他部门数据 |
| 前端门禁 | 全量类型检查、3 个改动组件 ESLint（0 警告）、正式构建和答题保存队列回归通过 |
| 新发布包 | 140 个前端文件与构建逐字节一致，V026 与源码一致 |
| 环境 | 18091 专用数据库、Redis、后端及临时凭据已清理；源库 40 表行数和校验值不变 |
| 用户本地演示站 | 18090 已更新为新包，12 个演示账号、原有密码、数据和签名密钥保留，HTTP 入口资源与构建一致 |

这是基础回归与修复后定向复测的累计结果，不是一次运行完整编排得到的 158 项。最初答题读取专项真实复现 4 项失败，修复后 9 项全部通过。导出测试补充了“同一关键字匹配两个部门”的断言，避免把本部门合法记录误判为泄漏。浏览器日志仅记录一次预期的跨部门拒绝信息，未观察到页面崩溃。

本轮证据均位于被 Git 忽略的 `work/codex-logs/`：

- `department-reaudit-api.json`：158 项最终接口结果。
- `department-reaudit-ui.json`、`department-reaudit-browser-errors.json`：实际浏览器步骤记录及预期权限拒绝日志。
- `department-reaudit-export.json`、`department-reaudit-export.xlsx`：实际下载内容核验。
- `20261010-151455-456e6075-ops.output`：修复前答题读取问题；`20261010-152110-7e4532c3-ops.output`：修复后读取 9 项通过。
- `20261010-151843-716fbe8e-ops.output`：135 项后端测试及 release 构建。
- `20261010-151604-4a37afc7-ops.output`、`20261010-151859-e3a57f88-ops.output`、`20261010-151621-1b2428e3-ops.output`：类型、lint、前端构建。
- `20261010-151941-f3b85793-ops.output`：保存队列和进入答题页状态门禁回归。
- `department-reaudit-package.json`、`department-review-updated.json`：新包及本地 18090 更新核验；包 SHA256 为 `05d99977d546b705589cd905a214d6f49e103c0db365d30b2d6ebf6f635d1a7f`。
- `department-reaudit-cleanup.json`：临时环境清理和源库 40 表未变。
- `department-reaudit-menu-fixed.jpg`、`department-reaudit-reentry-fixed.jpg`、`department-reaudit-resume-fixed.jpg`、`department-reaudit-employee-pass.jpg`：修复及员工端最小化结果截图。

## 当前状态与下一步

2026-10-12 已按用户授权推送 main 并部署原服务器，线上版本和 V026 见下方发布记录。本地业务库未迁移，既有 AGENTS.md、patches/ 未修改。此前的 `http://127.0.0.1:18090/#/login` 演示环境及凭据仍保留在 `.local/`；本次发布代码不复制演示账号到服务器。推荐下一步由线上管理员按实际部门创建主管账号并绑定“部门主管”角色，再由 HR/主管使用虚构名单确认日常操作。

2026-10-10 首轮使用 --skip-load，当日本地复验也未重跑容量、备份、移动端或线上验收；本地通过不代替目标服务器验收。历史缺失题面/答卷和生产运维事项沿用原限制。

## 线上发布（2026-10-12）

部门主管、部门隔离和两处验收修复已上线 [原测试站](https://124.220.2.69:18443/#/login)。功能提交 `6786b75aad1e69ffe6ca9997c522d65d0baf849f` 与部署传输调整提交 `58440945f05d80d52e17d841e151d04abbf64ba8` 已推送 main，实际运行应用版本为后者。[CI/CD 运行 38195403017](https://github.com/cqz-cio/Enterprise-Assessment-Question-Bank-System/actions/runs/38195403017) 最终第 2 次尝试的构建、部署均成功。

CI 核验 135 项后端测试全部通过，0 失败/错误/跳过；23 项部署安全测试通过。运维套件 13 项中 4 项 Windows 专用检查在 Linux runner 跳过，其余通过。全量类型检查、正式前端构建、release verify 和 140 个包内静态文件一致性门禁通过。

| 发布核验 | 结果 |
| --- | --- |
| 当前运行目录 | `/opt/enterprise-exam-test/releases/38195403017-2-58440945f05d` |
| CI/本机接续/线上 JAR | SHA256 均为 `e6734cffe5696d74a9441ec16df40b9babb2997a4377135f20e92cce4ee18030` |
| 数据库 | Flyway V026 成功；DEPT_MANAGER 为部门主管、data_scope=2、role_level=90，未创建默认账号 |
| 权限 | 审核、发放、成绩/导出、评分和终审权限存在；未授予用户状态/维护、角色授权或候选人导入权限 |
| 公网 | HTTPS、首页及 6 个入口/答题资源与 JAR 一致，验证码 PNG 正常；3 个受保护接口匿名访问被拒绝 |
| 服务 | active/running、NRestarts=0，无恢复标记，原 ERP 入口正常 |
| 原数据 | 原 8 张核心业务表校验值不变；其他已记录业务行数、3 个附件和原密钥/运行配置摘要一致；线上保留原 5 个账号 |
| V025 前置备份 | `/opt/enterprise-exam-test/backups/ops/20261012-103631-17907844`，实际为 V026 升级前的 V025 备份 |
| 最后接续发布备份 | `/opt/enterprise-exam-test/backups/ops/20261012-103938-ab1c2161`，已为 V026，不能当成升级前备份 |

首轮 [运行 38194803528](https://github.com/cqz-cio/Enterprise-Assessment-Question-Bank-System/actions/runs/38194803528) 的 CI 通过，上传在旧 32 KiB/s 限速及单次截止时间下失败，未切换服务。将限速提高为 128 KiB/s 并同步安全断言和部署说明后，新运行第一次尝试仍因跨境链路波动超时，末段实际速度约 40 KiB/s。两次失败均未改运行包或迁移数据库。

恢复时只使用最终运行已通过 CI 的原始 artifact：GitHub ZIP 摘要 `a23702f506636f42746bcf4cf5f77c3060f78fdd9e32aa201372e7e739969e47` 与下载一致，Git SHA、clean 状态和 JAR 摘要再次校验。确认没有残留 rsync 后，以现有部署账号从本机完整补齐私有暂存目录，服务器全文件摘要通过；调用原 root 所有受保护入口，保留备份、迁移比较、文件锁及启动健康检查。第一次受控发布后，仅重跑失败的 CD，复用同一 CI 包并以新运行包为差分基础，自动部署最终通过。没有上传本地 dirty 构建或跳过发布门禁。

本次线上验证为发布、只读权限配置和匿名保护检查，未重新执行登录后的完整员工/主管浏览器闭环；该业务闭环及双向部门隔离依据 2026-10-10 本地真实测试和最终相同源码的 CI。跨境大包传输仍可能超时，本轮保留现有有限重试和失败保护；后续可单独完善可靠的 artifact 获取/续传流程。未执行服务器容量、移动端或备份恢复演练。

本地发布证据（Git 忽略目录）：

- `work/codex-logs/department-deploy-ci-summary-final.json`：最终源码 CI 测试数量及跳过项。
- `work/codex-logs/department-deploy-workflow-38195403017.json`：最终工作流、任务及提交身份。
- `work/codex-logs/department-deploy-recovery-artifact.json`、`department-deploy-upload.json`：原 artifact 身份、完整上传与服务器摘要。
- `work/codex-logs/department-deploy-local-activation.json`：第一次受保护激活、升级前备份和退出状态。
- `work/codex-logs/department-deploy-before.json`、`department-deploy-after-first.json`、`department-deploy-after.json`：配置/密钥、业务行数、附件及服务前后对照。
- `work/codex-logs/department-deploy-live-verification.json`：最后公网资源、V026、权限及匿名拒绝核验。

最终发布记录以 `[skip ci]` 文档提交保存，避免相同应用重复停服。原有 AGENTS.md、patches/ 继续保持未跟踪。

## 修改文件

- `docs/01-product-requirements.md`
- `docs/02-system-design.md`
- `docs/03-data-model.md`
- `docs/04-api-spec.md`
- `docs/05-development-plan.md`
- `docs/06-testing-acceptance.md`
- `docs/08-decision-log.md`
- `docs/AI_HANDOFF.md`
- `docs/DEPARTMENT_MANAGER_DELIVERY_2026-10-10.md`
- `docs/README.md`
- `scripts/qa/README.md`
- `scripts/qa/browser.cjs`
- `scripts/qa/answer-queue.cjs`
- `scripts/qa/answer_read_access.py`
- `scripts/qa/department_manager.py`
- `scripts/qa/department_manager_flow.py`
- `scripts/qa/run_acceptance.py`
- `scripts/ci/deploy.py`
- `scripts/ci/test_deploy.py`
- `deploy/linux/GITHUB_ACTIONS.md`
- `yf-bev2-api/src/main/java/com/yf/modules/exam/assignment/importing/CandidateImportService.java`
- `yf-bev2-api/src/main/java/com/yf/modules/exam/assignment/mapper/ExamAssignmentMapper.java`
- `yf-bev2-api/src/main/java/com/yf/modules/exam/assignment/service/EmployeeAssignmentService.java`
- `yf-bev2-api/src/main/java/com/yf/modules/exam/assignment/service/impl/ExamAssignmentServiceImpl.java`
- `yf-bev2-api/src/main/java/com/yf/modules/exam/grading/GradingService.java`
- `yf-bev2-api/src/main/java/com/yf/modules/exam/paper/service/impl/PaperQuServiceImpl.java`
- `yf-bev2-api/src/main/java/com/yf/modules/exam/position/service/impl/PositionServiceImpl.java`
- `yf-bev2-api/src/main/java/com/yf/modules/exam/report/ResultReportService.java`
- `yf-bev2-api/src/main/java/com/yf/system/modules/depart/service/impl/SysDepartServiceImpl.java`
- `yf-bev2-api/src/main/java/com/yf/system/modules/role/service/impl/SysRoleServiceImpl.java`
- `yf-bev2-api/src/main/java/com/yf/system/modules/user/DepartmentScope.java`
- `yf-bev2-api/src/main/java/com/yf/system/modules/user/controller/SysUserController.java`
- `yf-bev2-api/src/main/java/com/yf/system/modules/user/dto/request/SysUserQueryReqDTO.java`
- `yf-bev2-api/src/main/java/com/yf/system/modules/user/dto/request/UserRegistrationBatchAuditReqDTO.java`
- `yf-bev2-api/src/main/java/com/yf/system/modules/user/enums/SysRoleId.java`
- `yf-bev2-api/src/main/java/com/yf/system/modules/user/mapper/SysUserMapper.java`
- `yf-bev2-api/src/main/java/com/yf/system/modules/user/service/SysUserService.java`
- `yf-bev2-api/src/main/java/com/yf/system/modules/user/service/impl/SysUserRoleServiceImpl.java`
- `yf-bev2-api/src/main/java/com/yf/system/modules/user/service/impl/SysUserServiceImpl.java`
- `yf-bev2-api/src/main/resources/db/migration/V026__department_manager_role.sql`
- `yf-bev2-api/src/main/resources/mapper/modules/exam/assignment/ExamAssignmentMapper.xml`
- `yf-bev2-api/src/main/resources/mapper/sys/user/SysUserMapper.xml`
- `yf-bev2-api/src/test/java/com/yf/ability/auth/AuthSecurityTest.java`
- `yf-bev2-api/src/test/java/com/yf/modules/exam/assignment/importing/CandidateImportTest.java`
- `yf-bev2-api/src/test/java/com/yf/modules/exam/assignment/service/EmployeeAssignmentIntegrationTest.java`
- `yf-bev2-api/src/test/java/com/yf/modules/exam/assignment/service/impl/ExamAssignmentServiceImplCandidateResultTest.java`
- `yf-bev2-api/src/test/java/com/yf/modules/exam/paper/service/ManualGradingIntegrationTest.java`
- `yf-bev2-api/src/test/java/com/yf/modules/exam/paper/service/PaperSafetyIntegrationTest.java`
- `yf-bev2-api/src/test/java/com/yf/modules/exam/report/ResultReportTest.java`
- `yf-bev2-api/src/test/java/com/yf/system/modules/user/DepartmentScopeTest.java`
- `yf-bev2-api/src/test/java/com/yf/system/modules/user/service/impl/SysUserServiceImplTokenTest.java`
- `yf-bev2-vue/src/views/Exam/components/DepartmentSelect.vue`
- `yf-bev2-vue/src/components/DataTable/src/DataTable.vue`
- `yf-bev2-vue/src/views/Exam/Exam/Client/Enter.vue`
- `yf-bev2-vue/src/views/Exam/Exam/Client/components/ExamTimer.vue`
- `yf-bev2-vue/src/views/System/User/User.vue`
