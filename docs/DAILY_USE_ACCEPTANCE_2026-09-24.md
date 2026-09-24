# 公司日常答题使用验收报告

> 本文保留第一次验收的历史状态；同日后续修复、最新验证和发布情况见 [修复回归报告](./DAILY_USE_FIXES_2026-09-24.md)。

验收日期：2026-09-24（Asia/Shanghai）
源码基线：e6398eb，加本次尚未提交的默认开始时间修复及验收脚本。
结论：**核心业务具备公司日常考核试运行条件；本次覆盖的主链未发现尚未修复的阻断缺陷。** 修复、用例和文档已落在本地，本轮未提交、推送或部署测试服务器。

## 1. 已实际运行的范围

使用本地 Windows、Java 17、MySQL 8、独立 Redis、正式前端及 release JAR，在 127.0.0.1:18090 验证。自动化浏览器为已安装的 Chrome，1440×1000 桌面视口。依据最新确认的 D-010/D-033，场景为面试 INTERVIEW、转正 REGULARIZATION、晋升 PROMOTION；同步修正了文档入口中仍残留的旧场景说明。

| 业务环节 | 实际操作及检查 | 结果 |
| --- | --- | --- |
| 管理配置 | 管理员经真实 API 创建岗位、三场景题库、四种题型及组卷模板 | 通过 |
| 员工身份 | 手机/邮箱留空注册，待审核时拒绝登录，HR 审批后正常登录 | 通过 |
| 候选人身份 | HR 发放姓名+6 位大写字母数字考核码、默认 14 天、错误姓名拒绝、重置旧码失效 | 通过 |
| 发放与归属 | 员工批量发放，重复自然键复用；跨部门整批失败并回滚；伪造 userId 无效 | 通过 |
| 建卷与恢复 | 四请求并发开考只有一张试卷，刷新继续原卷、已保存答案恢复 | 通过 |
| 题目快照 | 建卷后改源题及答案键，已生成的内容和评分依据不变 | 通过 |
| 客观题作答 | 单选/多选/判断、伪造或重复选项拒绝、少选不得分、自动判分 | 通过 |
| 保存边界 | 快速连续选择按顺序保存；简答切题/交卷前提交待保存文字；失败保留并阻断，重试/清空正常 | 通过 |
| 交卷与结果 | 四请求并发交卷只结算一次；提交后禁止改答和重考；考生仅能看到通过状态 | 通过 |
| 简答与阅卷 | 中文/换行/特殊字符，超过 5000 字符拒绝，待阅卷不提前公布结果，零分/范围/版本冲突/修改审计/幂等终审 | 通过 |
| 过期与停用 | 未到开始时间拒绝、到期拒绝答题且服务端自动结算、恢复任务沿用原卷，员工账号不受任务停用影响 | 通过 |
| 题目导入 | Excel/Word 四题型，预校验不落库、部分成功、重复跳过、原始错误行号报告 | 通过 |
| 候选人导入 | 有效/错误/重复行分类、他人导入会话拒绝、并发确认只发放一次、码可登录、任务关闭后明文下载失效 | 通过 |
| HR 查询与导出 | 组合筛选、零分筛选、跨全部页导出、18 列文件内容、空导出拒绝、部门范围约束 | 通过 |
| 真实页面 | 候选人答题，员工简答，管理员评分，员工再登录看结果，HR 筛选/下载/上传/确认/未保存提醒 | 12 个场景通过 |

“完整链路”指上述 V1 公司考核主链；题库和模板配置、注册审批经真实接口执行，浏览器交互集中验证考生、阅卷和 HR 日常操作，不表示每个通用后台设置页面都逐项点击过。

## 2. 已修复的问题

**默认立即生效的考核，刚发放后偶尔显示“考核尚未开始”。** Java Date 含毫秒，落到 MySQL 秒精度 DATETIME 时可能向上舍入，使开始时间短暂落在未来。修复前探测 10 次出现 4 次即时认证失败。

候选人及员工服务的默认开始时间现在都向下取整到秒；HR 明确提交的开始/截止时间保持原值，默认截止仍为开始后 14 天。新增 2 个后端回归用例：默认时间秒精度且可以立即开始、显式未来时间保持不变。真实 MySQL 上另连续执行 20 次“发放后马上认证”，全数成功；最终一键回归再次通过。

无新增数据库迁移，仍为 V024。无新增/删除接口、请求字段或响应字段；发放接口的默认时间行为与文档同步。

验收工具也修正了真实弹窗/异步切题的等待，并修正内层日志缓冲造成外层空闲超时的问题。最终完整运行从全新数据开始，170.9 秒结束，全部阶段通过且自动清理成功；不把前期调试失败计为产品成功。

## 3. 验证证据

| 检查 | 最终结果 | 证据 |
| --- | --- | --- |
| 后端完整 release verify | 117 tests，0 failure / 0 error / 0 skipped；BUILD SUCCESS | [Maven 日志](../work/codex-logs/20260924-102758-6051ef66-ops.output) |
| 前端正式构建 | 通过，137 个产物文件 | [构建日志](../work/codex-logs/20260924-095310-c2de129f-ops.output) |
| 运行包一致性 | 137 个前端文件及 360 个 class 与构建输出一致 | [校验日志](../work/codex-logs/20260924-102713-40bf4cd2-ops.output) |
| 真实 API/业务/负载用例 | 100/100 PASS | [用例 JSON](../work/qa-archive/20260924-110423/qa-api-results.json) |
| 浏览器业务场景 | 12/12 PASS，未处理的页面异常 0 | [浏览器 JSON](../work/qa-archive/20260924-110423/results.json) |
| 保存队列专项 | 连续保存、失败保留、切题/交卷、重试与清空全部通过 | [专项日志](../work/codex-logs/20260924-102659-1dde35a2-ops.output) |
| 新环境一键运行 | 全阶段 PASS，自动清理成功 | [运行清单](../work/codex-logs/qa-run-20260924-102435.json)、[详细输出](../work/codex-logs/20260924-102434-1c690e56-ops.output) |
| 前端全量类型检查 | **未通过：32 项既有 TypeScript 错误** | [类型日志](../work/codex-logs/20260924-095152-e4908dd3-ops.output) |

截图：[恢复后的简答](../work/qa-browser/employee-short-answer.png)、[待阅卷](../work/qa-browser/employee-pending.png)、[管理员阅卷完成](../work/qa-browser/admin-grading-complete.png)、[员工最终结果](../work/qa-browser/employee-final-result.png)。截图均为虚构人员与题目。

构建过程曾遇到运行中的 JAR 被 Windows 占用；验收后端改为运行独立副本，后续完整 release verify 已成功。浏览器用例未使用模拟 API；管理/负载账号由隔离 SQL 夹具创建，验证码从专用测试 Redis 取值再走正常认证。过期测试在隔离库加速截止时间，真实定时结算正常执行。测试方法和复现命令见 [scripts/qa/README.md](../scripts/qa/README.md)。

## 4. 100 人短时并发结果

100 个独立员工分别经真实接口登录后，同时开考；每人 3 轮、每轮 3 次保存，两轮之间间隔 5 秒，随后交卷并查询自身结果。不是 100 个请求共用一个账号。

| 操作 | 请求数 | P50 | P95 | 最大值 |
| --- | ---: | ---: | ---: | ---: |
| 同时开考 | 100 | 2923.3 ms | 5477.4 ms | 5687.4 ms |
| 答案保存 | 900 | 223.9 ms | 382.8 ms | 516.8 ms |
| 交卷 | 100 | 339.5 ms | 482.3 ms | 551.7 ms |

请求错误 0；数据库核对 100 条分配分别只有一张卷，全部交卷。并发工作段 17.32 秒，含创建账号/登录的整个负载阶段 76.42 秒。原始数据见 [负载 JSON](../work/qa-archive/20260924-110423/qa-load-results.json)。同时开考存在约 5.7 秒尾部等待，应在实际服务器上结合公司可接受等待时间复核。

此结果仅证明本机、本次小题量、短时间的负载表现；没有测真实服务器硬件、网络延迟、100 人同时登录、长时间运行、大题库/大导入文件、断电或主从切换，不能据此保证线上固定并发容量。

## 5. 数据与运行环境保护

只复制 40 张表的结构、迁移历史和 9 张基础配置表，不复制真实账号、候选人、试题、分配、答案或成绩。临时数据库和 Redis 使用随机名称，应用使用随机测试密钥与独立端口，真实数据库和原 Redis 保持运行。

最终运行删除临时数据库、专用 Redis、运行 JAR 副本和包含随机测试凭证的夹具，并停止专用后端。原数据库全部 40 张表的计数和校验值与测试前一致；清理证明见 `work/codex-logs/*qa-cleanup.json`。未操作线上服务器、GitHub 设置或原业务运行配置；已有未跟踪 AGENTS.md、patches/ 保留。

## 6. 剩余事项与使用建议

1. **可先进行小范围业务试运行。** 单实例的面试、转正、晋升核心流程已跑通；本次时间修复仍在本地，服务器需要发布此修复后再按同一用例做一次业务验收。
2. **清理 32 项前端类型债务。** 包括缺失组件声明、旧 Element Plus 类型、历史详情类型和未使用变量等。本次实际构建和主链通过，但全量类型检查未转绿，不能宣称所有工程检查均通过。
3. **长期使用前落实自动备份、异机副本及恢复演练。** 本轮只验证测试环境隔离和源库不变，没有重新执行灾备恢复/定时备份验证；此前备份工具交付不等于调度已落实。
4. **在真实服务器复测容量与浏览器。** 此次覆盖桌面 Chrome；手机、其他浏览器、慢网络、长时稳定性与服务器并发仍待专门验证。
5. 候选人导入明文清单保存在短时内存会话，按现有设计 15 分钟内下载，关闭/重启后不可恢复；维持单实例或粘性路由。浏览器已验证离开提醒及真实下载，不建议未经改造直接扩成多实例。

推荐下一项：先处理前端类型检查，再在实际测试服务器发布本次修复并按用例进行 HR 业务验收，同时落实备份调度与恢复安排。

## 7. 修改文件

- 业务修复：[EmployeeAssignmentService.java](../yf-bev2-api/src/main/java/com/yf/modules/exam/assignment/service/EmployeeAssignmentService.java)、[ExamAssignmentServiceImpl.java](../yf-bev2-api/src/main/java/com/yf/modules/exam/assignment/service/impl/ExamAssignmentServiceImpl.java)。
- 后端回归：[EmployeeAssignmentIntegrationTest.java](../yf-bev2-api/src/test/java/com/yf/modules/exam/assignment/service/EmployeeAssignmentIntegrationTest.java)。
- 新增测试：[environment.py](../scripts/qa/environment.py)、[acceptance.py](../scripts/qa/acceptance.py)、[extended.py](../scripts/qa/extended.py)、[browser.cjs](../scripts/qa/browser.cjs)、[answer-queue.cjs](../scripts/qa/answer-queue.cjs)、[run_acceptance.py](../scripts/qa/run_acceptance.py)、[README.md](../scripts/qa/README.md)。
- 文档：本报告、docs/README.md、04-api-spec.md、05-development-plan.md、06-testing-acceptance.md、AI_HANDOFF.md。
- 迁移：无。API：结构无变更，仅默认立即生效时间的秒精度修正。

## 附录 A：本次接口/业务用例明细

所有用例使用虚构测试数据，前置和操作由脚本固定，标题概括预期结果；详细断言与对应 ID 位于 acceptance.py、extended.py。负面测试拒绝将 HTTP 5xx/404 当作权限通过。

| 用例 ID | 操作 / 预期结果 | 结果 |
| --- | --- | --- |
| SETUP-01 | 管理员通过真实 API 创建岗位、三场景题库/题目/模板 | PASS |
| AUTH-01 | 员工手机邮箱留空注册后待审核 | PASS |
| AUTH-02 | 未审核员工不可登录 | PASS |
| AUTH-03 | HR 审核后员工可正常登录 | PASS |
| AUTH-04 | 缺少验证码拒绝登录 | PASS |
| AUTH-05 | 验证码一次性消费不可重放 | PASS |
| AUTH-06 | 篡改 token 拒绝认证 | PASS |
| PERM-匿名-employee/create | 匿名不能访问管理分配接口 | PASS |
| PERM-匿名-candidate/paging | 匿名不能访问管理分配接口 | PASS |
| PERM-匿名-report | 匿名不能查询完整成绩 | PASS |
| PERM-员工-employee/create | 员工不能访问管理分配接口 | PASS |
| PERM-员工-candidate/paging | 员工不能访问管理分配接口 | PASS |
| PERM-员工-report | 员工不能查询完整成绩 | PASS |
| PERM-HR-bank | HR 不能创建题库 | PASS |
| PERM-HR-grading | HR 默认不能阅卷 | PASS |
| EMP-01 | 跨部门员工发放失败且整批回滚 | PASS |
| EMP-02 | 无效批次没有残留任务 | PASS |
| EMP-03 | 默认发放有效期为 14 天 | PASS |
| EMP-04 | 重复发放不创建新任务 | PASS |
| EMP-05 | 个人列表忽略伪造 userId | PASS |
| CAND-01 | 发放考核码为 6 位大写字母数字 | PASS |
| CAND-02 | 重复候选人编号与批次拒绝新增 | PASS |
| CAND-03 | 候选人不能查管理成绩 | PASS |
| CAND-04 | 错误姓名拒绝认证 | PASS |
| PAPER-01 | 4 个并发开考请求只生成一卷 | PASS |
| PAPER-02 | 试卷生成三类客观题 | PASS |
| CROSS-/api/exam/paper/paper/detail | 员工不可读取或操作他人考核 | PASS |
| CROSS-/api/exam/paper/qu/list-card | 员工不可读取或操作他人考核 | PASS |
| CROSS-/api/exam/paper/paper/hand | 员工不可读取或操作他人考核 | PASS |
| CROSS-/api/exam/assignment/current | 员工不可读取或操作他人考核 | PASS |
| CROSS-/api/exam/assignment/my-result | 员工不可读取或操作他人考核 | PASS |
| CROSS-/api/exam/paper/paper/create-by-assignment | 员工不可读取或操作他人考核 | PASS |
| LEAK-radio | 作答详情不泄漏评分键、解析及分数 | PASS |
| ANSWER-radio | 合法答案保存及刷新恢复 | PASS |
| CROSS-answer-radio | 跨用户保存答案被拒绝 | PASS |
| LEAK-multi | 作答详情不泄漏评分键、解析及分数 | PASS |
| ANSWER-multi | 合法答案保存及刷新恢复 | PASS |
| CROSS-answer-multi | 跨用户保存答案被拒绝 | PASS |
| ANSWER-invalid | 伪造选项及重复选项拒绝 | PASS |
| LEAK-judge | 作答详情不泄漏评分键、解析及分数 | PASS |
| ANSWER-judge | 合法答案保存及刷新恢复 | PASS |
| CROSS-answer-judge | 跨用户保存答案被拒绝 | PASS |
| SNAPSHOT-01 | 修改源题和评分键不改变已生成快照 | PASS |
| HAND-01 | 4 并发交卷一次结算且快照自动评分 30 分 | PASS |
| RESULT-01 | 候选人立即可查通过结果且无额外敏感字段 | PASS |
| HAND-02 | 交卷后禁止改答 | PASS |
| HAND-03 | 交卷后禁止重考 | PASS |
| RESULT-02 | 原有效期内原考核码可再次查询通过结果 | PASS |
| REPORT-01 | HR 可查询完整成绩及作答 | PASS |
| SCORE-01 | 多选少选得 0 分，单选和判断正常计分 | PASS |
| RESULT-03 | 员工交卷后立即自动公布是否通过 | PASS |
| SHORT-01 | 简答题超过 5000 字符拒绝 | PASS |
| SHORT-02 | 简答中文/换行/特殊字符保存和恢复 | PASS |
| GRADE-01 | 含主观题交卷后待阅卷不公布结果 | PASS |
| GRADE-02 | 尚有未评分题不允许完成阅卷 | PASS |
| GRADE-range--1 | 非法评分拒绝 | PASS |
| GRADE-range-11 | 非法评分拒绝 | PASS |
| GRADE-range-1.234 | 非法评分拒绝 | PASS |
| GRADE-03 | 零分正确保存且重试不重复留痕 | PASS |
| GRADE-04 | 旧版本评分覆盖被拒绝 | PASS |
| GRADE-05 | 评分修改保留前后值审计 | PASS |
| GRADE-06 | 并发完成阅卷只产生一次终审 | PASS |
| RESULT-04 | 终审后员工只看到未通过 | PASS |
| GRADE-07 | 终审后禁止修改评分 | PASS |
| REPORT-02 | HR 成绩包含终审后 8 分结果 | PASS |
| TIME-01 | 未来生效的考核码拒绝提前进入 | PASS |
| TIME-02 | 到期禁止保存答案 | PASS |
| TIME-03 | 定时补偿到期自动交卷并绕过最短作答时间 | PASS |
| TIME-04 | 到期任务不能重开试卷 | PASS |
| CODE-01 | 重置后旧码失效 | PASS |
| CODE-02 | 新码恢复原试卷 | PASS |
| CODE-03 | 停用后的候选人会话不可继续作答 | PASS |
| CODE-04 | 停用考核码不能认证 | PASS |
| EMP-06 | 员工任务停用不撤销员工账号 | PASS |
| EMP-07 | 停用员工任务不可继续作答 | PASS |
| EMP-08 | 员工任务恢复沿用同一试卷 | PASS |
| IMPORT-Q-01 | Excel 题目预校验不落库 | PASS |
| IMPORT-Q-02 | 四题型 Excel 部分成功，重复与错误隔离 | PASS |
| IMPORT-Q-03 | Excel 导入错误报告包含原始行号 | PASS |
| IMPORT-Q-04 | 相同题目重传不重复创建 | PASS |
| IMPORT-Q-05 | 员工无题目导入权限 | PASS |
| IMPORT-W-01 | Word 模板四题型可预览 | PASS |
| IMPORT-W-02 | Word 四题型正确入库 | PASS |
| IMPORT-W-03 | 重复 Word 导入不新增 | PASS |
| IMPORT-C-01 | 候选人预校验 2 有效/1 错误/1 重复 | PASS |
| IMPORT-C-02 | 其他 HR 不能提交别人的导入任务 | PASS |
| IMPORT-C-03 | 并发确认导入只发放一次 | PASS |
| IMPORT-C-04 | 考核码清单与错误行报告内容正确 | PASS |
| IMPORT-C-05 | 导入发放的考核码可真实认证 | PASS |
| IMPORT-C-06 | 关闭任务后不可下载明文考核码 | PASS |
| TIME-DEFAULT | 20 次默认立即生效发放后马上认证均成功 | PASS |
| LOAD-01 | 100 独立用户并发开考/持续保存/交卷无请求错误 | PASS |
| LOAD-02 | 100 人任务均唯一试卷且全部交卷 | PASS |
| REPORT-03 | 导出跨越全部页且具有 18 列 | PASS |
| REPORT-04 | 零分筛选不遗漏零分记录 | PASS |
| REPORT-05 | 倒置分数范围拒绝 | PASS |
| REPORT-06 | 不把空导出错误保存为 Excel | PASS |
| SCOPE-01 | 部门范围 HR 无法查询另一部门成绩 | PASS |
| SCOPE-02 | 部门范围 HR 不能直接读取他部门试卷成绩 | PASS |
| SCOPE-03 | 部门范围 HR 导出不能绕过数据范围 | PASS |

## 附录 B：真实浏览器用例

| 用例 | 结果 |
| --- | --- |
| UI-CAND-01 姓名和考核码认证并开始测评 | PASS |
| UI-CAND-02 客观题选择、刷新后原卷与答案恢复 | PASS |
| UI-CAND-03 逐题作答交卷，结果只显示是否通过 | PASS |
| UI-EMP-01 员工正式登录并进入我的考核 | PASS |
| UI-EMP-02 客观题与简答题逐题保存并刷新恢复 | PASS |
| UI-EMP-03 主观题交卷后显示等待阅卷 | PASS |
| UI-GRADE-01 管理员打开待阅卷试卷 | PASS |
| UI-GRADE-02 保存评分、审计记录并完成阅卷 | PASS |
| UI-RESULT-01 员工重新登录后看到已终审结果 | PASS |
| UI-HR-01 HR 登录并按批次筛选查看成绩 | PASS |
| UI-HR-02 成绩 Excel 从页面真实下载 | PASS |
| UI-HR-03 候选人 Excel 上传、预校验、部分成功发放 | PASS |
