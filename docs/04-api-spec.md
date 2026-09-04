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

建议后续改用 `paperQuId`，避免同一试卷出现重复来源题目时歧义。

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

筛选：姓名、岗位、场景、批次、考核名称、提交时间、阅卷人和阅卷状态。

### 7.2 获取阅卷详情

```http
POST /api/exam/grading/detail
Permission: exam:grading:view
```

响应包含题干快照、参考答案快照、评分标准快照、答题内容、题目满分、当前得分和评语。

### 7.3 保存单题评分

```http
POST /api/exam/grading/question/save
Permission: exam:grading:score
```

```json
{
  "paperQuId": "paper-question-id",
  "score": 12.5,
  "comment": "业务判断正确，但风险控制措施不够完整"
}
```

### 7.4 完成阅卷

```http
POST /api/exam/grading/finalize
Permission: exam:grading:finalize
```

```json
{"paperId": "paper-id"}
```

只有全部简答题均已评分时允许完成；完成后计算最终总分、通过状态，更新考核分配和考试记录，并自动向考生开放“是否通过”结果。

## 8. 成绩查询和导出

### 8.1 管理端结果分页

可以扩展现有 `/api/exam/exam/record/paging`，也可以建立清晰的新接口：

```http
POST /api/exam/report/result/paging
Permission: exam:report:view
```

筛选字段：

```text
subjectName, subjectType, positionId, sceneType, batchNo,
examId, assignmentStatus, gradingState, passed,
scoreMin, scoreMax, submittedFrom, submittedTo
```

### 8.2 成绩导出

```http
POST /api/exam/report/result/export
Permission: exam:report:export
```

导出当前筛选条件下的结果，不接受客户端上传任意结果行，防止越权导出。

建议列：姓名、人员类型、岗位、场景、批次、考核名称、客观分、主观分、总分、是否通过、交卷时间、阅卷状态、阅卷人。

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
