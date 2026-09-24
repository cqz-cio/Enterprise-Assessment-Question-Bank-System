# 日常使用问题修复与回归

日期：2026-09-24；对应 D-039。按用户要求暂缓备份工作和服务器容量测试。本次完成前端类型、密码成功流程、导入清单持久恢复及此前默认开始时间修复；本地全链路回归及测试站上线验收全部通过，已通过既有 CI/CD 发布到现有测试站。

## 修复结果

1. 既有 32 项 TypeScript 错误全部修复：清理不存在且未使用的组件引用，修正 Element Plus 类型、表格引用、路由参数及 API 响应类型。未变更页面布局或主题。CI 的全量类型检查从非阻断提示改为发布门禁。
2. 密码接口成功实际返回 code=0；修正原页面判断不存在的 success 字段，现正常提示成功、退出并重新登录。已使用虚构员工通过真实页面修改并验证新密码。
3. 导入清单 AES-256-GCM 加密保存 30 天；关闭、刷新及重启后可恢复。重开窗口显示本人最近任务，上传字节相同的原文件可恢复对应任务。预览仍保留 15 分钟，已发放/部分发放的任务关闭不删除。无新账号或额外服务器密钥。
4. 每行创建候选人、防重键和加密结果同事务提交；归档失败回滚该行，部分成功中断后只处理未完成行。同一任务跨实例以数据库行锁保证幂等。
5. 候选人、员工默认开始时间向下取整到秒，解决 MySQL DATETIME 舍入导致刚发放“尚未开始”；显式时间和默认 14 天规则不变。连续 20 次即时认证通过。
6. 首轮真实 MySQL 回归发现归档校验误把 DATETIME 的 LocalDateTime 返回值当作失效日期，已改为明确 getTimestamp 读取。最终新环境完整重跑通过，不以失败轮次作为通过证据。

权限和数据范围仍按当前配置校验；未登录、员工、其他 HR、被撤销权限均不能恢复别人的清单。响应 no-store。重置、停用、过期的码不再返回；过期归档自动清理。原认证表仍仅保存安全摘要，密钥不落库，需继续保留原 ASSIGNMENT_CODE_PEPPER。旧版本已经丢失的清单不能追溯恢复。

## 验证证据

| 检查 | 结果 | 证据 |
| --- | --- | --- |
| 全量 TypeScript | 0 错误 | [类型日志](../work/codex-logs/20260924-110024-2234ed60-ops.log) |
| 修改文件 ESLint | 通过 | [检查日志](../work/codex-logs/20260924-110131-87a5e40f-ops.log) |
| 正式前端构建 | 通过，137 个资源文件 | [构建日志](../work/codex-logs/20260924-110224-c57c497e-ops.log) |
| 全量后端 release verify | 123/123，0 失败、0 跳过 | [Maven 日志](../work/codex-logs/20260924-110607-5458228c-ops.output) |
| JAR 内容 | 137 个前端文件、364 个 class 与构建输出一致，包含 V025 | 本地逐文件校验；CI 再构建并校验同一发布包 |
| 接口/业务 | 110/110 PASS | [用例 JSON](../work/codex-logs/qa-api-results.json) |
| Chrome 实际操作 | 14/14 PASS，未处理异常 0 | [浏览器 JSON](../work/qa-browser/results.json) |
| 作答保存顺序/失败重试 | 全部通过 | [专项输出](../work/codex-logs/20260924-110850-a49e45ae-ops.output) |
| 一键全链路（跳过负载） | 131.5 秒，全阶段 PASS，自动清理完成 | [运行清单](../work/codex-logs/qa-run-20260924-110722.json)、[详细输出](../work/codex-logs/20260924-110722-836a9e90-ops.output) |
| GitHub 工作流检查 | actionlint 通过 | [检查日志](../work/codex-logs/20260924-110621-a9d2ed9a-ops.log) |

用例与复现命令见 [scripts/qa/README.md](../scripts/qa/README.md)。本轮在真实 MySQL/Redis/Java/Chrome 上创建虚构账号、岗位、三场景模板和答卷；实际停止并重启后端后恢复原 JWT 和考核码。源库全部 40 张表行数与校验值不变，专用数据库、Redis、进程、JAR 副本和测试凭证已删除。

## 重点回归用例

| 用例 | 操作与预期 |
| --- | --- |
| IMPORT-C-06 | 关闭已发放任务后仍能恢复并下载原考核码；PASS |
| IMPORT-C-07 | 重复上传同一文件恢复原任务和原考核码；PASS |
| IMPORT-C-08 | 未登录不能恢复清单；PASS |
| IMPORT-C-09 | 员工不能恢复清单；PASS |
| IMPORT-C-10 | 其他 HR 不能恢复或下载他人清单；PASS |
| IMPORT-C-11 | 恢复清单响应禁止浏览器缓存；PASS |
| IMPORT-C-12 | 真实进程重启后原 JWT 可恢复相同任务与全部原码；PASS |
| IMPORT-C-13 | 真实进程重启后原考核码仍可认证；PASS |
| IMPORT-C-14 | 重启后重复提交不新增分配；PASS |
| IMPORT-C-15 | 归档表只保存密文，不包含姓名、联系方式及原码；PASS |
| IMPORT-C-16 | 重置后恢复清单不会重新暴露旧码；PASS |
| IMPORT-C-17 | 已过期码不再通过恢复接口提供；PASS |
| IMPORT-C-18 | 到期清单拒绝恢复和下载；PASS |
| UI-HR-04 | 关闭、刷新、重新打开恢复原清单并下载；PASS |
| UI-PASS-01 | 修改密码成功提示、退出并以新密码重新登录；PASS |

后端归档新增 6 个用例覆盖关闭/重启、归档失败整行回滚、部分成功中断恢复、错误密钥/篡改/所有者调包、失效码过滤和跨实例并发。原有 9 个导入用例继续覆盖部分成功、权限变化、重复、错误日期、文件限制和到期清理。

## 修改文件、迁移与 API

- 归档及导入：CandidateImportArchive.java（新）、CandidateImportService.java、CandidateImportController.java、CandidateImportTest.java。
- 时间修复：EmployeeAssignmentService.java、ExamAssignmentServiceImpl.java、EmployeeAssignmentIntegrationTest.java。
- 前端：CandidateImportDialog.vue / candidateImport.ts；Passwd.vue；App、CountDown、Form、Setting、Table、Dashboard、考核详情/记录、部门/菜单及 global.d.ts 的类型修正。
- 新增脚本：scripts/qa/ 下的环境、接口、导入/恢复、浏览器、保存队列和编排器；更新 GitHub 工作流及相关文档。
- **迁移 V025**：新增 el_candidate_import_task，存任务所有者、文件摘要、状态、到期/创建时间及认证密文；不改已有分配/试卷数据。
- **新增 POST /api/exam/assignment/candidate/import-restore**；import-close 改为保留已发放结果，同文件 import-validate 返回可恢复的原任务。完整契约见 [API 文档](./04-api-spec.md)。
- 保留原有未跟踪 AGENTS.md、patches/；不把日志、测试凭据和下载码清单加入 Git。

## 范围与后续

备份调度/异机副本、服务器容量验证按用户要求暂缓。本轮桌面 Chrome 主业务链通过；手机和其他浏览器未开展专项。建议 HR 在现有测试站用一批虚构人员进行日常操作确认，再安排小范围使用。此前的 [首次全链路报告](./DAILY_USE_ACCEPTANCE_2026-09-24.md) 是修复前历史结果，本报告替代其中类型债务、清单丢失及待发布状态。


## 测试站发布与最终验收

- 应用提交：2cef942f8139a8f6ec214b167f8f2d1d0de718fd。[GitHub 运行 35950414592](https://github.com/cqz-cio/Enterprise-Assessment-Question-Bank-System/actions/runs/35950414592) 的构建和部署均 success，新类型门禁已在云端实际通过。
- 入口：[企业人才考核测试站](https://124.220.2.69:18443/)。服务器 systemd active，current.jar 的 SHA256 与发布清单一致，Flyway 成功应用 V025，首页与 JAR 内容一致，无恢复故障标记。
- 服务器真实功能验收：即时发放认证、并发请求幂等建卷/交卷、跨用户读写拒绝、快照评分、仅通过状态、交卷后禁止重考、超时自动结算、停用阻断、交卷后重新登录查结果、加密清单关闭恢复、原文件重复上传及 Excel 下载全部通过。此为功能/幂等验收，不是容量测试。
- 一次性服务器测试脚本的清理分支首次执行有误，已即时修正并清理，再从全新虚构数据完整运行成功。最终运行 13.5 秒，原九张核心表计数保持一致，未保留测试用户、试卷、会话、归档或 Quartz 任务。[最终业务验收日志](../work/codex-logs/20260924-111807-d039-live-acceptance.log)。
- 最终复核：原八张业务表的 CHECKSUM 摘要与发布前一致，新归档表测试记录为 0；公网 TLS、首页、验证码和原 ERP 入口通过。[最终核验](../work/codex-logs/20260924-111900-46267520-ops.output)。
- Chrome 公网登录及候选人入口渲染正常，未处理页面异常 0。[浏览器日志](../work/codex-logs/20260924-111659-d8856e58-ops.output)、[登录页截图](../work/qa-browser/server-login-after-fixes.png)。
- 发布沿用原部署流程的保护措施，未新增备份计划/异机副本，未改变业务密钥或原 ERP 配置。最终文档单独以 [skip ci] 提交，避免仅补报告再次停服。
