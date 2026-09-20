# API 接口规范（目标版）

> 版本：V0.1  
> 状态：Phase 1 已实现；Phase 2 岗位、模板、候选人分配、候选人入口和唯一试卷链路已实现；Phase 3 及以后仍为目标契约  
> 基础路径：`/api`

## 1. 通用约定

### 1.1 协议与鉴权

- 管理端、员工端和候选人验证后的接口使用现有 JWT。
- Token 请求头沿用现有项目：`token: <jwt>`。
- 登录、员工注册、验证码和候选人考核码验证接口允许匿名访问。
- 管理接口必须同时校验角色权限和数据范围。

### 1.2 返回结构

沿用项目现有 `ApiRest<T>` 结构；文档示例只展示核心字段：

```json
{
  "code": 0,
  "message": "success",
  "data": {}
}
```

实际 `code/message` 字段名称和成功码以当前 `ApiRest` 实现为准，开发时不得创建第二套不兼容返回结构。

### 1.3 分页请求

沿用现有 `PagingReqDTO<T>`：

```json
{
  "current": 1,
  "size": 20,
  "params": {}
}
```

### 1.4 时间和枚举

- 时间使用后端当前统一格式，API 和数据库均按 `Asia/Shanghai` 解释。
- 状态在接口中使用英文稳定代码，不使用中文作为数据库值。
- ID 一律作为字符串返回，避免前端 JavaScript 精度丢失。

## 2. 候选人入口

### 2.1 验证姓名和考核码

```http
POST /api/exam/assignment/candidate/verify
Auth: anonymous
```

请求：

```json
{
  "candidateName": "张三",
  "accessCode": "GZ7K92"
}
```

成功响应：

```json
{
  "token": "jwt-token",
  "assignmentId": "1980000000000000001",
  "examId": "1980000000000000002",
  "paperId": null,
  "candidateName": "张三",
  "positionName": "外贸业务员",
  "examTitle": "外贸业务员入职/面试考核",
  "validFrom": "2026-09-02 09:00:00",
  "expireAt": "2026-09-16 09:00:00",
  "assignmentStatus": "ASSIGNED"
}
```

规则：

- 姓名在去除首尾空格后精确匹配。
- 考核码必须为 6 位，比较时统一转换为大写。
- 错误提示不要区分“姓名不存在”和“考核码错误”，避免泄露人员信息。
- `STARTED` 且未交卷时返回已有 `paperId`，允许恢复。
- `COMPLETED/EXPIRED/DISABLED` 返回不可进入错误。
- 验证成功后签发候选人内部用户的普通 JWT，token 过期时间不得超过 `expireAt`。

### 2.2 查询当前分配信息

```http
POST /api/exam/assignment/current
Auth: employee/candidate
```

请求：

```json
{"id": "assignmentId"}
```

只允许查询属于当前用户的分配。

### 2.3 员工注册

```http
POST /api/sys/user/reg
Auth: anonymous
```

```json
{
  "userName": "employee001",
  "password": "client-encrypted-password",
  "realName": "李四",
  "employeeNo": "E20260001",
  "deptCode": "SALES",
  "mobile": "13900000000",
  "email": "lisi@example.com",
  "captchaKey": "captcha-key",
  "captchaValue": "ABCD"
}
```

注册接口在现有 `UserRegReqDTO` 上扩展 `employeeNo/mobile/email`。注册成功后账号沿用现有 `UserState.AUDIT=2`，不签发可用登录 token。V1 必填姓名、账号、密码、工号、部门和验证码；手机号、邮箱均为选填。

实现状态：已完成。注册不再受通用审核开关影响而绕过审核，固定写入 `AUDIT` 并分配 `EMPLOYEE` 角色。

### 2.4 员工注册申请分页

```http
POST /api/sys/user/paging
Permission: sys:user:paging
Auth: admin/HR
```

复用现有用户分页，增加工号筛选，并以 `state=2` 查询待审核员工。为 HR 分配该接口权限和仅员工范围的数据权限。

### 2.5 审核员工注册

```http
POST /api/sys/user/registration/audit
Permission: sys:user:registration:audit
Auth: admin/HR
```

```json
{
  "userId": "employee-user-id",
  "action": "APPROVE",
  "remark": "信息核验通过"
}
```

`action` 只允许 `APPROVE` 或 `REJECT`。通过映射为现有 `UserState.NORMAL=0` 并确认 `EMPLOYEE` 角色；驳回映射为 `UserState.DISABLED=1`。两种操作都记录审核人、审核时间和原因，不得静默删除申请。

实现状态：已完成。HR 分页在服务端强制限定 `EMPLOYEE` 角色，不能通过请求参数扩大到管理员或候选人。

### 2.6 当前账号资料修改

```http
POST /api/sys/user/update
Auth: admin/HR/employee
```

请求只接受当前账号可自行维护的 `avatar`、`realName`、`idCard`、`mobile`、`email` 和可选 `password`；服务端始终以登录身份确定用户，不接受客户端指定用户 ID。成功后返回更新后的完整登录会话（含新 token），前端必须立即替换本地会话，避免资料保存后继续使用已失效 token。候选人资料由 HR 管理，不开放该入口。

### 2.7 员工端考试记录

```http
POST /api/exam/exam/record/client-paging
Permission: exam:client:record
Auth: employee
```

服务端强制使用当前登录用户 ID，只返回考试 ID、考试名称和是否通过。响应不得包含试卷 ID、考试次数、最高分、最近分、答案或解析；完整成绩和试卷明细继续使用管理端权限保护的接口。

## 3. 岗位管理

### 3.1 新增或修改岗位

```http
POST /api/exam/position/save
Permission: exam:position:add 或 exam:position:edit
```

```json
{
  "id": null,
  "code": "FOREIGN_SALES",
  "name": "外贸业务员",
  "departmentIds": ["department-id-1", "department-id-2"],
  "grades": [
    {"id": null, "code": "P1", "name": "初级业务员", "levelNo": 1, "sort": 1, "status": 1}
  ],
  "status": 1,
  "sort": 10,
  "remark": ""
}
```

### 3.2 岗位分页

```http
POST /api/exam/position/paging
Permission: exam:position:view
```

筛选字段：`departmentId`、`code`、`name`、`status`。响应包含 `departmentIds`、`departmentNames` 和 `grades`。

### 3.3 按部门查询岗位及职级

```http
POST /api/exam/position/list-by-department
Auth: HR/admin
```

请求 `{"id":"department-id"}`。仅返回该部门关联的启用岗位，并附带岗位职级，用于候选人录入和题库/模板配置。

### 3.4 启用或停用

```http
POST /api/exam/position/change-status
Permission: exam:position:edit
```

停用岗位不影响历史试卷，但不能创建新的考核分配。

## 4. 考核分配与候选人管理

### 4.1 HR 创建候选人考核

```http
POST /api/exam/assignment/candidate/create
Permission: exam:assignment:candidate:add
```

请求：

```json
{
  "candidateName": "张三",
  "candidateNo": "CAND-2026-0001",
  "mobile": "13800000000",
  "email": "zhangsan@example.com",
  "departId": "department-id",
  "positionId": "position-id",
  "batchNo": "2026-AUTUMN-01",
  "validFrom": "2026-09-02 09:00:00",
  "expireAt": "2026-09-16 09:00:00"
}
```

后端行为：

1. 校验部门、岗位均存在关联且岗位启用。
2. 自动匹配同一部门和岗位启用的 `INTERVIEW` 面试考核模板。
3. 创建候选人内部 `el_sys_user` 和候选人角色关系。
4. 创建 `el_exam_assignment`。
5. 生成 6 位考核码并只在本次响应中返回明文。

时间规则：`validFrom` 默认当前时间；`expireAt` 默认等于 `validFrom + 14 天`。前端创建表单自动带出这两个值，HR 可以在提交前调整。

响应：

```json
{
  "assignmentId": "assignment-id",
  "candidateName": "张三",
  "positionName": "外贸业务员",
  "examTitle": "外贸业务员入职/面试考核",
  "accessCode": "GZ7K92",
  "entryUrl": "http://intranet-host/#/exam-entry"
}
```

### 4.2 候选人 Excel 导入

```http
POST /api/exam/assignment/candidate/import
Content-Type: multipart/form-data
Permission: exam:assignment:candidate:import
```

表格建议列：

```text
姓名* | 候选人编号* | 手机号* | 邮箱* | 部门编码* | 岗位编码* | 批次* | 可进入时间* | 截止时间*
```

采用部分成功策略：逐行校验，正确行在事务中正常导入，错误行不落库。响应必须包含总行数、成功数、失败数和错误报告下载地址；错误报告保留原始行号、候选人编号及逐行失败原因。

```json
{
  "totalCount": 100,
  "successCount": 96,
  "failureCount": 4,
  "errorReportUrl": "/api/exam/assignment/candidate/import-error/download?taskId=import-task-id"
}
```

### 4.3 候选人列表

```http
POST /api/exam/assignment/candidate/paging
Permission: exam:assignment:candidate:view
```

筛选字段：姓名、部门、岗位、批次、分配状态、考试时间范围、是否通过、阅卷状态。

### 4.3A 查看候选人结果详情

```http
POST /api/exam/assignment/candidate/result-detail
Permission: exam:assignment:candidate:result
Auth: admin/HR
```

请求：

```json
{"id": "assignment-id"}
```

只允许读取候选人类型且状态为 `COMPLETED` 的考核分配。服务端通过分配记录取得唯一试卷，校验试卷的 `assignment_id`、`user_id` 和交卷状态，不接受客户端直接指定候选人或试卷所有者。响应包含试卷总分、及格分、候选人得分、是否通过、考试用时、交卷时间以及逐题作答和评分详情。该接口为只读权限，不授予 HR 题库、模板、全量考试记录或评分修改权限。

### 4.3B 题库与模板结构字段

题库和考核模板均提交 `departId`、`positionId`、`sceneType`、`targetGradeId`。`sceneType` 仅允许 `INTERVIEW`、`REGULARIZATION`、`PROMOTION`；非晋升场景由服务端清空 `targetGradeId`，晋升场景允许该字段为空。模板所选题库必须与四个结构字段完全一致。

### 4.4 重置考核码

```http
POST /api/exam/assignment/candidate/reset-code
Permission: exam:assignment:candidate:code
```

```json
{"id": "assignment-id"}
```

仅 `ASSIGNED/STARTED` 且未完成的分配允许重置。重置只改变进入凭证，不会创建第二份试卷；响应只返回一次新明文考核码。

### 4.5 停用/恢复考核

```http
POST /api/exam/assignment/change-status
Permission: exam:assignment:edit
```

```json
{
  "id": "assignment-id",
  "action": "DISABLE",
  "reason": "候选人取消面试"
}
```

### 4.6 给员工分配考核

```http
POST /api/exam/assignment/employee/create
Permission: exam:assignment:employee:add
Auth: admin/HR
```

```json
{
  "examId": "promotion-template-id",
  "userIds": ["employee-user-id-1", "employee-user-id-2"],
  "batchNo": "2026-Q3-PROMOTION",
  "validFrom": "2026-09-10 09:00:00",
  "expireAt": "2026-09-12 18:00:00"
}
```

员工不生成考核码，通过正式账号在“我的考核”查看任务。

## 5. 题库和考核模板扩展

沿用现有接口并增加字段：

```text
/api/exam/repo/repo/save
  + positionId
  + sceneType
  + status

/api/exam/repo/qu/save
  + externalCode
  + status
  + referenceAnswer
  + gradingCriteria
  + tags

/api/exam/exam/exam/save
  + positionId
  + sceneType
  + templateStatus
  + optionShuffle
```

V1 不开放考生端结果展示配置：候选人和员工只可查看“是否通过”，不能查看分数、标准答案或解析。

新增题目状态接口：

```http
POST /api/exam/repo/qu/change-status
Permission: exam:question:edit
```

题目 Excel 接口：

```http
GET  /api/exam/repo/qu/import-template
POST /api/exam/repo/qu/import/validate
POST /api/exam/repo/qu/import
POST /api/exam/repo/qu/import-error-report
Permission: repo:qu:import
Content-Type for POST: multipart/form-data
Parts: repoId, file
```

统一模板包含“填写说明、试题数据、填写示例、字段字典”四个工作表，支持单选题、多选题、判断题和简答题。限制为 `.xlsx`、10 MB、1000 道题；数据表表头必须保持一致，不允许合并单元格、公式或错误值。

`/import/validate` 只读取和校验，不落库，返回总数、可导入数、重复数、失败数及逐行问题。确认导入时前端把同一文件再次提交到 `/import`，服务端重新校验并在题库级 Redis 锁内导入有效行。重复规则为“同一题库 + 题型 + 去除 HTML/空白并转小写后的题干”，重复行跳过且不覆盖原题；格式错误行不落库，其余正确行正常导入。导入题目默认启用。

简答题的选项和“正确答案”列必须为空，“解析/参考答案”保存为 `referenceAnswer`，“评分要点”保存为 `gradingCriteria`。本接口只完成题库可用数据存储，不代表简答题作答和人工阅卷流程已经完成。

## 6. 试卷与答题接口改造

### 6.1 开始考核

建议新增基于分配的入口，替代客户端直接传任意 `examId`：

```http
POST /api/exam/paper/paper/create-by-assignment
Auth: employee/candidate
```

```json
{"id": "assignment-id"}
```

服务端必须根据 assignment 取得 `examId/userId`，不能相信客户端提供的用户 ID。

响应：

```json
{
  "paperId": "paper-id",
  "resumed": false
}
```

无论调用多少次，每条 assignment 都只能关联一份试卷。若已有进行中试卷，返回同一 `paperId` 且 `resumed=true`；若分配已完成、过期或停用，则返回相应错误，不能创建重考试卷。

### 6.2 保存客观题答案

现有接口保留：

```http
POST /api/exam/paper/qu/fill-answer
Auth: paper owner
```

增加规则：校验当前用户、分配状态、试卷状态和有效期；保存后不向客户端返回是否正确。

Phase 1 已先为旧接口增加当前用户 owner、交卷状态和截止时间校验；分配状态校验待 Phase 2 的 `el_exam_assignment` 落地后接入。

### 6.3 保存简答题

```http
POST /api/exam/paper/qu/fill-text-answer
Auth: paper owner
```

```json
{
  "paperId": "paper-id",
  "quId": "source-question-id",
  "answerText": "候选人的回答"
}
```

响应：

```json
{
  "filled": true,
  "savedAt": "2026-09-02 15:30:00"
}
```

已实现（D-031）：`paperId` 和 `quId` 为必填字符串，最长 64 字符；`answerText` 必填且最多 5000 字符，空字符串用于清空答案。返回 `filled` 和 `savedAt`。沿用试卷/来源题目组合定位，校验当前用户、唯一试卷、分配状态和截止时间；提交后拒绝修改。不会返回评分键或执行主观题自动判分。

### 6.4 交卷

现有接口 `/api/exam/paper/paper/hand` 保留，但必须：

- 校验试卷归属当前用户。
- 交卷幂等。
- 停止继续保存答案。
- 无简答题时自动完成。
- 有简答题时返回待阅卷状态。

Phase 1 已完成旧试卷详情、答题卡、答题、主动交卷和实时状态的统一 owner 校验；`full-detail` 与试卷管理分页已限制为 `exam:record:list` 管理权限。

考生端响应核心字段：

```json
{
  "paperId": "paper-id",
  "paperState": "PENDING_REVIEW",
  "resultAvailable": false
}
```

交卷接口不向考生返回客观分或最终分。管理端的完整成绩由成绩查询接口返回。

### 6.5 查询本人最终结果

```http
POST /api/exam/assignment/my-result
Auth: employee/candidate
```

```json
{"assignmentId": "assignment-id"}
```

最终结果形成后自动公布，无需 HR 手工发布。无主观题时在交卷结算后自动可查；有主观题时在 `finalize` 成功后自动可查，并只返回：

```json
{
  "assignmentId": "assignment-id",
  "resultAvailable": true,
  "passed": true
}
```

存在未完成的人工阅卷时返回 `resultAvailable=false`；完成后立即转为 `true`。任何情况下都不得向考生返回分数、标准答案或解析。

## 7. 人工阅卷

### 7.1 待阅卷列表

```http
POST /api/exam/grading/paging
Permission: exam:grading:view
```

已实现的平铺请求：`current`（默认 1）、`size`（默认 10，最大 100）、`keyword`（姓名/考核名称）、`positionId`、`departId`、`sceneType`、`batchNo`、`examId`、`graderId`、`submittedFrom`、`submittedTo`、`state`（PENDING/GRADED，默认 PENDING）。时间格式 `yyyy-MM-dd HH:mm:ss`，Asia/Shanghai。返回 `records/total/current/size`。前端提供姓名/考核名称、岗位、场景、批次、提交时间及状态筛选；其余为 API 筛选能力。

`POST /api/exam/grading/positions` 使用同一查看权限，返回已有待阅/已阅试卷关联岗位的 `id/name`，无需额外岗位管理权限。

### 7.2 获取阅卷详情

```http
POST /api/exam/grading/detail
Permission: exam:grading:view
```

请求 `{"id":"paper-id"}`。响应包含 `version`、考核与考生信息、客观分/主观分/通过线、`shortCount/gradedCount`、`questions`（题干/参考答案/评分要点快照、文本答案、满分、当前得分、评语、阅卷人和时间）及 `logs`（GRADE/REGRADE/FINALIZE 操作和评分/评语前后值）。只允许查看已提交的待阅/已阅试卷。

### 7.3 保存单题评分

```http
POST /api/exam/grading/question/save
Permission: exam:grading:view AND exam:grading:score
```

```json
{
  "paperQuId": "paper-question-id",
  "score": 12.5,
  "comment": "业务判断正确，但风险控制措施不够完整",
  "expectedVersion": 0
}
```

### 7.4 完成阅卷

```http
POST /api/exam/grading/finalize
Permission: exam:grading:view AND exam:grading:finalize
```

```json
{"paperId": "paper-id", "expectedVersion": 2}
```

只有全部简答题均已评分时允许完成；零分也必须明确保存。评分范围为 0 至题目满分，最多两位小数，评语选填且最长 2000 字符。保存及完成响应为最新完整阅卷详情。不同内容的过期版本写入被拒绝；相同评分重试不重复记日志；重复完成不重复结算。

完成后在同一事务内计算最终总分/通过状态，将试卷 `gradingState` 更新为 GRADED、分配更新为 COMPLETED，写考试记录和审计日志，并自动向考生开放“是否通过”。完成后只读；停用分配、未交卷和 LEGACY_INCOMPLETE 试卷不能评分或完成。默认只给管理员阅卷权限，HR 不自动获得评分权限。

候选人 `POST /api/exam/assignment/candidate/verify` 对 PENDING_REVIEW/COMPLETED 分配允许在原有效期内认证并返回结果入口，必须关联本人已提交试卷；不会恢复作答状态。仍禁止开新卷、重考或改答，停用/过期拒绝认证（D-031）。

## 8. 成绩查询和导出（2026-09-20 已实现）

独立报表按考核分配逐行展示；旧汇总按人员和模板聚合，不作为本报表数据源。

| POST 接口 | 权限 | 请求与返回 |
| --- | --- | --- |
| `/api/exam/results/paging` | `exam:results:view` | 筛选请求；返回 records,total,all,completed,pending |
| `/api/exam/results/detail` | `exam:results:view` | `{id}`（试卷 ID）；返回单行结果 |
| `/api/exam/results/options` | `exam:results:view` | 返回 departments、positions，元素 `{id,name}` |
| `/api/exam/results/export-preview` | view 和 `exam:results:export` | 同一筛选请求；返回 total,pending,limit |
| `/api/exam/results/export` | view 和 `exam:results:export` | 同一筛选请求；返回 XLSX 二进制 |

筛选：keyword（姓名/编号/工号）、subjectType、departId、positionId、sceneType、batchNo、title、state（ALL/COMPLETED/PENDING）、passed（boolean）、scoreMin、scoreMax、submittedFrom、submittedTo。current 默认 1，size 默认 10、最大 100；日期为上海时间 `yyyy-MM-dd HH:mm:ss`，含起止边界。分数支持零分和两位小数，反向范围拒绝。姓名、批次、考核名称包含匹配，通配符按字面处理。

返回行字段：id、assignmentId、subjectName、subjectType、subjectNo、departName、positionName、sceneType、batchNo、title、objectiveScore、subjectiveScore、userScore、totalScore、qualifyScore、passed、gradingState、gradedCount、handTime、graderName、gradedAt。待阅卷的 subjectiveScore/userScore/passed 为 null 或省略，不能作为零分/未通过；分数和通过状态过滤只匹配最终结果。all/completed/pending 按其他筛选条件计算，不受 state 影响。

所有操作共同验证当前登录身份在数据库中的最新部门及角色数据范围（不沿用会话中旧范围）：本人=分配创建者；本部门=分配部门；本部门及下级=组织编码前缀；全部=全部分配。身份/范围无效或需要部门但未配置时拒绝，不接受客户端身份覆盖。部门/岗位选项只来自权限范围内已交卷记录。试卷、分配、人员、模板四者必须一致；未交卷和无分配关联的旧卷不纳入新报表。已过期但交卷的记录继续提供管理查询。

导出与页面最后应用的筛选一致，包含全部页；下载时重新验证权限，在只读可重复读事务中查询最新数据。单次最多 10000 条，空数据和超限拒绝、不静默截断。响应 `Cache-Control: no-store`；失败为 JSON 业务错误，前端不保存为 Excel。

18 列：姓名、人员类型、编号/工号、部门、岗位、场景、批次、考核名称、客观分、主观分、最终总分、满分、及格分、是否通过、交卷时间、阅卷状态、终审人、完成阅卷时间。待阅卷主观分、最终总分、是否通过留空。编号为文本并保留前导零，文本不作为公式执行；冻结表头并附筛选。不含手机、邮箱、考核码、题目、答案。

## 9. 建议错误码

| 业务码 | 含义 |
| --- | --- |
| EXAM_ASSIGNMENT_NOT_FOUND | 考核信息不存在或凭证错误 |
| EXAM_ASSIGNMENT_NOT_STARTED | 尚未到可进入时间 |
| EXAM_ASSIGNMENT_EXPIRED | 考核已过期 |
| EXAM_ASSIGNMENT_DISABLED | 考核已停用 |
| EXAM_ASSIGNMENT_COMPLETED | 已完成，不能重复进入 |
| EXAM_RETAKE_NOT_ALLOWED | 当前分配不允许重考 |
| EXAM_TEMPLATE_NOT_CONFIGURED | 岗位未配置有效考核模板 |
| EXAM_PAPER_FORBIDDEN | 无权访问该试卷 |
| EXAM_PAPER_SUBMITTED | 试卷已交卷，不能修改 |
| EXAM_QUESTION_NOT_ENOUGH | 启用题目数量不足 |
| EXAM_GRADING_INCOMPLETE | 尚有简答题未评分 |
| EXAM_SCORE_OUT_OF_RANGE | 评分超出题目满分范围 |

错误响应对候选人应保持克制，不能通过不同提示枚举候选人姓名和考核码。

## 10. 接口实现完成定义

每个新增接口至少具备：

- Controller 参数校验。
- Service 事务和状态校验。
- 权限注解或明确的身份校验。
- 对象归属校验。
- Swagger/Knife4j 注释。
- 正常、越权、重复提交和过期场景测试。
- 本文档同步更新。

### 试题导入补充（2026-09-07）

确认导入响应新增可选 `errorReportBase64`，包含本次导入时生成的 XLSX 问题行报告；无问题行时为空。完成页直接下载该报告，不再次校验原文件，避免成功行被误报为重复。报告追加原始 Excel 行号。空数据表拒绝导入；判断题答案仅接受“正确”和“错误”。现有预校验报告接口保持不变。

### 会话权限同步（2026-09-07）

`POST /api/sys/user/info` 在验证 token、当前会话和账号状态后，按该会话用户 ID 读取最新权限列表，不再返回登录时的旧权限快照。前端刷新后在首次挂载业务页面前调用此接口同步按钮权限；接口不接受客户端指定权限所属用户，不签发新 token。

### Word 题目导入（2026-09-07）

- `GET /api/exam/repo/qu/import-word-template`：下载可直接解析的四题型 DOCX 示例模板，使用时需替换示例题。
- `POST /api/exam/repo/qu/import-word/validate`：预校验，返回通用计数和 issues，另含 `questions` 列表（paragraph、questionCode、questionType、content、options、answer、difficulty、explanation、gradingCriteria、status），供管理人员逐题核对。
- `POST /api/exam/repo/qu/import-word`：重新校验并复用题库锁和事务入库；返回成功/重复/失败数以及本次 XLSX 问题报告 `errorReportBase64`。
- `POST /api/exam/repo/qu/import-word-error-report`：下载预校验问题报告，XLSX 格式。
- 上述接口统一要求 `repo:qu:import`；POST 使用 multipart，字段 `repoId`、`file`，可选 `defaultDifficulty`（简单/一般/较难/极难，仅补充文档空难度）。
- `templateVersion=WORD_QUESTION_IMPORT_V1`；issues.rowNumber 表示题目起始段落号，解析问题的 message 另含具体问题段落及必要原文摘要。题目预览只向具备导入权限的管理端返回。
- `.docx`、10MB、1000 题；纯文字段落编号、选项和字段规则详见 D-028。文件损坏/加密、不支持内容或边界歧义返回业务错误，预校验不写库；可隔离的题目字段错误允许其余正确题入库。

### P0 安全修复契约（D-029，2026-09-17）

- `POST /api/exam/paper/paper/create`、`/pre-check`：保留原权限校验，统一返回旧入口已停用错误，不能再通过 examId 生成试卷。
- `POST /api/exam/exam/exam/detail-for-exam`：旧模板入口停用；`/client-paging` 返回空页，不再列出任意管理模板。员工分配门户仍待 P1，本次不新增员工发放接口。
- `POST /api/exam/paper/paper/create-by-assignment`：继续以当前用户 + 分配 ID 开始/恢复，已交卷或试卷时间已到期不能恢复或重考。
- `POST /api/exam/exam/exam/save`：只允许题量大于零的客观题组卷规则；零题量简答题规则可保留。拒绝空卷、负题量、非正分值、重复题型规则；服务端计算总分并校验及格分范围。
- `POST /api/exam/paper/qu/fill-answer`：事务内锁定本人分配与试卷；只接受该题的合法、无重复选项 ID，单选/判断最多一个，空数组表示清空答案；null 数组、主观题、无正确答案配置均拒绝。
- `/detail-for-answer` 读取快照，只返回作答所需内容，不映射解析、标准答案或得分字段；管理端 `/full-detail` 使用快照内容和原有评分键，包含管理用 analysis。
- `/hand` 幂等；主动交卷遵循快照最低作答时间，到期交卷不受其限制。历史主观题关闭作答后处于待阅卷，不返回最终通过状态。
- `/paper/detail` 增加 `resultAvailable`；仅已交卷且不待阅卷时返回 passed，否则 passed 为空/省略。员工结果组件处理处理中状态，避免将 null 误显示为未通过。
- `/assignment/my-result` 继续只返回 resultAvailable / passed，并复核试卷与当前分配、用户一致；历史主观题不发布结果。成绩汇总分页排除待阅卷试卷。
- 管理端试卷 DTO 增加 `gradingState`、`snapshotSource`；考生 DTO 不暴露这些管理字段。
- 无新增匿名端点或权限授予。数据库迁移 V017 由 Flyway 执行。

### P1 员工任务接口（D-030，2026-09-18，已实现）

以下路径均以 `/api/exam/assignment` 为前缀，均为 POST，使用既有 token 会话及 ApiRest 封装。旧 `/client-paging` 继续为空，不能用于新门户。

| 路径 | 权限 | 功能 |
| --- | --- | --- |
| `/employee/create` | `exam:assignment:employee:add` | 按显式名单批量发放 |
| `/employee/options` | 同上 | 本部门审核通过且启用的员工分页，只返回 id/name/employeeNo/departName |
| `/employee/templates` | 同上 | 本部门可选择的启用模板（D-031 后含简答题），不下放模板编辑权限 |
| `/employee/paging` | `exam:assignment:employee:view` | 管理任务分页 |
| `/employee/change-status` | `exam:assignment:employee:edit` | 停用或恢复员工任务 |
| `/employee/result-detail` | `exam:assignment:employee:result` | 已完成任务的管理试卷详情 |
| `/my-paging` | `exam:assignment:my:view` | 当前登录员工的任务及状态计数 |

创建请求：`{examId,userIds,batchNo,validFrom?,expireAt?}`。userIds 为 1–500 个非空 ID，服务端去重；批次去除首尾空格后非空，最多 64 字符。日期格式 `yyyy-MM-dd HH:mm:ss`，Asia/Shanghai；省略则当前开始、14 天截止，截止必须晚于开始和当前时间。所有用户必须为模板部门的正常 EMPLOYEE 且非 CANDIDATE；校验模板、岗位部门关联、目标职级、题库、客观题规则、可用题量和及格分后事务写入。返回 `{created,existing,assignmentIds}`；重复自然键复用原任务，不更新日期、不恢复状态、不重建试卷。无效成员整批失败。

查询请求使用**平铺对象**：`{current:1,size:10,keyword?,departId?,positionId?,sceneType?,batchNo?,status?}`，不是原 DataTable 的 params 包装。current 最大 100000，size 为 1–500；所有 SQL 值使用绑定参数，不接收自定义排序。options 要求部门，支持姓名/工号 keyword；templates 要求部门，可按岗位/场景筛选，返回 id/title/positionId/positionName/sceneType/totalTime/questionCount。

分页返回 `{records,total,current,size,counts}`，counts 为 `{status,total}[]`，按当前其他筛选条件统计但不受状态筛选影响。管理 keyword 查姓名/工号，本人 keyword 查考核标题。本人范围由会话确定，忽略请求 userId。状态包括原状态及派生 UPCOMING、SETTLING；TODO 筛选 ASSIGNED/UPCOMING，CLOSED 筛选 EXPIRED/DISABLED。

本人行只返回 id、examTitle、batchNo、departName、positionName、sceneType、totalTime、validFrom、expireAt、paperDeadline、paperId、disabledReason、status、passed；仅已完成、交卷且非待阅卷时提供 passed。管理行额外提供 subjectName、employeeNo。列表均不返回分数、题目答案、解析或考核码；管理完整详情走独立权限接口。

状态请求 `{id,action:'DISABLE'|'ENABLE',reason?}`；停用原因必填且不超过 500 字，恢复要求原任务仍有效且原卷未交卷/未截止，不能重置倒计时。不会注销员工会话。详情请求 `{id}`，只允许 EMPLOYEE 分配且复核试卷绑定/所有者及最终完成状态。

开考、续考、交卷和本人最终结果复用现有 `/paper/create-by-assignment`、答题/交卷和 `/assignment/my-result` 接口；前端按员工/候选人路由分别返回各自结果入口。

### 认证安全契约（D-032，2026-09-18）

- `POST /api/sys/user/login`：`userName`、`password`、`captchaKey`、`captchaValue` 全部必填，最大长度分别为 255、1024、标准 UUID、16。省略验证码不再兼容；用户名/密码错误统一提示。正常返回沿用原 token 契约。
- `GET /api/common/captcha/gen?key=<UUID>`：返回 PNG，验证码存储 5 分钟，同一 key 不覆盖；先成功存储再输出图片。验证使用 Redis 原子 GETDEL，错误或成功均消费；重新尝试必须取新 key 和新图。大小写不敏感，验证码值去除首尾空格。
- 注册继续使用验证码和审核机制；登录、注册失败后前端清空旧验证码并重新取图。
- 所有上述入口及 `/api/exam/assignment/candidate/verify` 按实际连接源 IP 限制，校验请求体前计数；伪造 Forwarded/X-Forwarded-For 不改变来源。有效验证码之后，账号按数据库身份 ID 计数；候选人按考核码的 HMAC 查找值计数，不能通过换 IP 或姓名绕过码限制。
- 固定 600 秒窗口：登录 IP 300、候选人认证 IP 300、注册 IP 60、取验证码 IP 600；账号/考核码各 10 次。账号/考核码认证成功清除自身窗口，IP 请求仍累计。限额包含当前尝试，第 11 次账号/码尝试拦截；被拦截不延长窗口。
- 超限返回 **HTTP 429**，ApiRest 错误消息及 **Retry-After** 秒数（至少 1）；限流或验证码 Redis 操作失败返回 **HTTP 503** 和通用服务不可用消息。其余业务校验仍沿用 ApiRest 非零 code。
- Redis 最低需支持 GETDEL（6.2+，本地 Compose 为 7）。本轮没有新增匿名业务接口、授权权限或数据库迁移。
