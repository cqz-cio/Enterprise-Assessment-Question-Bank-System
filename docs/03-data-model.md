# 数据模型与迁移设计

> 版本：V0.1  
> 状态：Phase 1、Phase 2 及部门岗位职级结构已实现；后续阅卷和员工分配仍待实现  
> 命名约定：延续现有 `el_` 表前缀和字符串雪花 ID

## 1. 设计原则

- 尽量复用现有用户、题库、考试、试卷和记录表。
- 候选人与员工都通过内部 `user_id` 参与考试，避免重写现有鉴权。
- 使用统一“考核分配”描述某个人被分配某个固定考核模板。
- 历史试卷必须保存内容快照，不能依赖题库的当前数据。
- 状态字段使用稳定英文代码，中文名称通过数据字典展示。
- 表结构变更必须放入 `yf-bev2-api/src/main/resources/db/migration/` 下的 Flyway 版本化 SQL 文件。

## 2. 新增表

### 2.1 `el_position` 岗位表

| 字段 | 类型建议 | 约束 | 说明 |
| --- | --- | --- | --- |
| id | varchar(64) | PK | 岗位 ID |
| code | varchar(64) | UNIQUE, NOT NULL | 稳定岗位编码，例如 `FOREIGN_SALES` |
| name | varchar(128) | NOT NULL | 岗位名称 |
| status | tinyint | NOT NULL | 1 启用，0 停用 |
| sort | int | NOT NULL | 展示顺序 |
| remark | varchar(500) | NULL | 备注 |
| create_time | datetime | NOT NULL | 创建时间 |
| update_time | datetime | NULL | 更新时间 |
| create_by | varchar(64) | NULL | 创建人 |
| update_by | varchar(64) | NULL | 修改人 |

索引建议：

```sql
UNIQUE KEY uk_position_code (code),
KEY idx_position_status_sort (status, sort)
```

### 2.2 `el_exam_assignment` 考核分配表

该表同时承载候选人招聘考核和员工考核。

| 字段 | 类型建议 | 约束 | 说明 |
| --- | --- | --- | --- |
| id | varchar(64) | PK | 分配 ID |
| exam_id | varchar(64) | NOT NULL | 考核模板 ID |
| user_id | varchar(64) | NOT NULL | 内部用户 ID |
| subject_type | varchar(32) | NOT NULL | `CANDIDATE` 或 `EMPLOYEE` |
| subject_name | varchar(128) | NOT NULL | 姓名快照 |
| candidate_no | varchar(64) | NULL | 候选人编号；候选人分配必填，员工为空 |
| mobile | varchar(32) | NULL | 手机号快照；候选人分配必填 |
| email | varchar(255) | NULL | 邮箱快照；候选人分配必填 |
| depart_id | varchar(64) | NULL | 部门快照关联；新分配必填 |
| position_id | varchar(64) | NOT NULL | 岗位快照关联 |
| target_grade_id | varchar(64) | NULL | 晋升目标职级 |
| batch_no | varchar(64) | NULL | 招聘/考核批次 |
| access_code_hash | varchar(255) | NULL | 候选人考核码哈希；员工为空 |
| valid_from | datetime | NULL | 可进入时间 |
| expire_at | datetime | NOT NULL | 截止时间，默认 `valid_from + 14 天`，允许 HR 发放前调整 |
| status | varchar(32) | NOT NULL | 分配状态 |
| paper_id | varchar(64) | NULL | 当前/最终试卷 ID |
| activated_at | datetime | NULL | 首次进入时间 |
| submitted_at | datetime | NULL | 交卷时间 |
| completed_at | datetime | NULL | 最终评分完成时间 |
| disabled_reason | varchar(500) | NULL | 停用原因 |
| create_time/update_time | datetime |  | 审计字段 |
| create_by/update_by | varchar(64) |  | 审计字段 |

约束和索引建议：

```sql
UNIQUE KEY uk_assignment_user_exam_batch (user_id, exam_id, batch_no),
UNIQUE KEY uk_assignment_code_lookup (access_code_lookup),
KEY idx_assignment_exam_status (exam_id, status),
KEY idx_assignment_position_batch (position_id, batch_no),
KEY idx_assignment_expire (expire_at)
```

注意：BCrypt 哈希不能通过等值索引直接定位。实现时建议同时保存不可逆的 `access_code_lookup`（带服务器 pepper 的 HMAC-SHA256，用于定位）和 BCrypt `access_code_hash`（用于最终校验），或使用随机高熵 token 的 SHA-256 摘要作为唯一查询键。不得保存明文考核码。

### 2.3 `el_depart_position` 部门岗位关联表

使用 `depart_id + position_id` 唯一约束表达多对多关系。同一“开发业务员”等岗位可以同时用于餐具部、家具部，部门层级继续复用 `el_sys_depart`。

### 2.4 `el_position_grade` 岗位职级表

| 字段 | 类型建议 | 说明 |
| --- | --- | --- |
| id | varchar(64) PK | 职级 ID |
| position_id | varchar(64) | 所属岗位 |
| code/name | varchar | 职级编码和名称 |
| level_no | int NULL | 可选的职级序号 |
| sort/status | int/tinyint | 排序及启停 |

### 2.5 `el_paper_grading_log` 阅卷记录表

| 字段 | 类型建议 | 说明 |
| --- | --- | --- |
| id | varchar(64) PK | 记录 ID |
| paper_id | varchar(64) | 试卷 ID |
| paper_qu_id | varchar(64) | 试卷题目 ID |
| grader_id | varchar(64) | 阅卷人 ID |
| score_before | decimal(10,2) | 修改前得分 |
| score_after | decimal(10,2) | 修改后得分 |
| comment_before | varchar(2000) | 修改前评语 |
| comment_after | varchar(2000) | 修改后评语 |
| action | varchar(32) | `GRADE`、`REGRADE`、`FINALIZE` |
| create_time | datetime | 操作时间 |

该表用于追踪评分变更；当前分数仍保存在 `el_paper_qu.actual_score` 中。

## 3. 现有表扩展

### 3.1 `el_repo` 题库

新增：

| 字段 | 类型建议 | 说明 |
| --- | --- | --- |
| depart_id | varchar(64) | 对应部门 |
| position_id | varchar(64) | 对应岗位 |
| scene_type | varchar(32) | 考核场景 |
| target_grade_id | varchar(64) | 晋升目标职级，可为空 |
| status | tinyint | 1 启用，0 停用 |

场景建议值：

```text
INTERVIEW       面试考核
REGULARIZATION  转正考核
PROMOTION       晋升考核
```

题库分类固定为 `RECRUITMENT`（入职招聘）和 `EMPLOYEE_PROMOTION`（员工晋升）。题库和模板保存时校验部门岗位关系；同一部门、岗位、场景、目标职级组合仅允许一个启用模板。

### 3.2 `el_repo_qu` 题目

新增：

| 字段 | 类型建议 | 说明 |
| --- | --- | --- |
| external_code | varchar(64) | 外部题目编号，用于题库维护和导入追溯，可为空 |
| status | tinyint | 启用/停用，默认 1 |
| reference_answer | text | 简答题参考答案，可为空 |
| grading_criteria | text | 简答题评分标准，可为空 |
| tags | varchar(500) | 题目标签，英文逗号分隔，可为空 |

题型增加：

```text
short  简答题
```

简答题不要求 `el_repo_qu_answer` 选项记录。上述字段及 `short` 字典值由
`V015__add_question_import_and_short_answer.sql` 落库；导入问题默认 `status=1`。

### 3.3 `el_exam` 考核模板

新增：

| 字段 | 类型建议 | 说明 |
| --- | --- | --- |
| depart_id | varchar(64) | 部门 |
| position_id | varchar(64) | 岗位 |
| scene_type | varchar(32) | 场景 |
| target_grade_id | varchar(64) | 晋升目标职级，可为空 |
| template_status | tinyint | 是否启用 |
| option_shuffle | tinyint | 是否随机选项，V1 默认 1 |

现有 `start_time/end_time` 作为模板默认值保留；个人实际可进入时间优先使用 `el_exam_assignment.valid_from/expire_at`。

### 3.4 `el_paper` 试卷

新增：

| 字段 | 类型建议 | 说明 |
| --- | --- | --- |
| assignment_id | varchar(64) | 对应考核分配 |
| paper_state | varchar(32) | 试卷状态 |
| objective_score | decimal(10,2) | 客观题得分 |
| subjective_score | decimal(10,2) | 主观题得分 |
| grading_state | varchar(32) | 阅卷状态 |

现有 `hand_state` 在过渡期保留，最终由 `paper_state` 表达更完整状态。

### 3.5 `el_paper_qu` 试卷题目

新增快照及主观题字段：

| 字段 | 类型建议 | 说明 |
| --- | --- | --- |
| content_snapshot | longtext | 题干快照 |
| analysis_snapshot | longtext | 解析快照 |
| reference_answer_snapshot | longtext | 参考答案快照 |
| grading_criteria_snapshot | longtext | 评分标准快照 |
| text_answer | longtext | 候选人/员工简答内容 |
| grading_state | varchar(32) | `NOT_REQUIRED/PENDING/GRADED` |
| grader_id | varchar(64) | 当前阅卷人 |
| grader_comment | varchar(2000) | 当前评语 |
| graded_at | datetime | 当前评分时间 |

旧字段 `qu_id` 保留用于追溯来源，但试卷展示必须读取快照字段。

### 3.6 `el_paper_qu_answer` 试卷选项

新增：

| 字段 | 类型建议 | 说明 |
| --- | --- | --- |
| content_snapshot | text | 选项内容快照 |

`answer_id` 继续保留来源关系；显示内容和判分依据必须使用试卷快照记录。

### 3.7 `el_sys_user` 员工注册扩展

复用现有 `state`：`0=NORMAL`、`1=DISABLED`、`2=AUDIT`，不再新建一套员工审核状态。新增：

| 字段 | 类型建议 | 说明 |
| --- | --- | --- |
| employee_no | varchar(64) | 员工工号，正式员工唯一；候选人内部用户为空 |
| audit_by | varchar(64) | 最近审核人 |
| audit_time | datetime | 最近审核时间 |
| audit_remark | varchar(500) | 审核意见或驳回原因 |

手机号和邮箱复用 `el_sys_user.mobile/email` 且均允许为空，部门复用 `dept_code`。V1 不使用现有 `id_card` 作为员工注册必填字段。`avatar` 默认使用超级管理员头像；管理员、HR、员工和候选人新建时统一写入默认值，已有自定义头像不覆盖。

## 4. 状态机

### 4.1 考核分配状态

```text
ASSIGNED
   | 验证身份/开始
   v
STARTED
   | 交卷
   +----------------------+
   |                      |
   v                      v
PENDING_REVIEW         COMPLETED
   | 阅卷完成
   v
COMPLETED
```

任意未完成状态可因时间到达变为 `EXPIRED`，可被管理员设置为 `DISABLED`。

### 4.2 试卷状态

```text
CREATED -> IN_PROGRESS -> SUBMITTED -> PENDING_REVIEW -> COMPLETED
```

无主观题时，`SUBMITTED` 可以直接转为 `COMPLETED`。

### 4.3 题目阅卷状态

- `NOT_REQUIRED`：客观题。
- `PENDING`：简答题已交卷但未评分。
- `GRADED`：简答题已评分。

## 5. 数据一致性规则

- `el_exam_assignment.user_id` 必须对应存在且可用的内部用户。
- 候选人分配必须具有考核码摘要；员工分配不使用考核码。
- `paper.assignment_id` 与 `paper.user_id/exam_id` 必须一致。
- 一条分配在整个生命周期内最多关联一张试卷，`assignment_id` 应在 `el_paper` 上建立唯一约束。
- V1 不允许重考，不引入 attempt 次数字段；完成或过期后若需再次考核，必须创建新的分配记录。
- 交卷后禁止普通答题接口继续修改答案。
- 简答题得分不得小于 0 或大于该题满分。
- 全部简答题 `GRADED` 后才允许最终完成。
- 题库题目删除优先采用停用或逻辑删除，不能破坏历史试卷来源。

## 6. 迁移文件

已落地：

```text
yf-bev2-api/src/main/resources/db/migration/
├── V001__extend_sys_user_employee_audit.sql
└── V002__seed_auth_roles_permissions.sql
```

后续建议从 `V003` 继续递增：

```text
yf-bev2-api/src/main/resources/db/migration/
├── V003__create_position.sql
├── V004__extend_repo_and_exam.sql
├── V005__create_exam_assignment.sql
├── V006__extend_paper_snapshot.sql
├── V007__create_grading_log.sql
└── V008__seed_exam_dicts.sql
```

项目已接入 Flyway，默认扫描 `classpath:db/migration`，启用校验并对已有非空基线库以版本 `0` 建立 baseline。应用启动时按版本自动执行尚未应用的迁移；禁止再手工重复执行这些脚本。

## 7. 初始化数据

角色权限迁移已初始化 `HR`、`EMPLOYEE`、`CANDIDATE`，并将旧 `user` 用户关系迁移为 `EMPLOYEE`。后续业务迁移还需初始化：

- 场景字典。
- `short` 简答题类型。
- 分配、试卷和阅卷状态字典。
- 候选人固定入口所需菜单/路由权限。
- 旧题库和旧题目 `status=1`。

## 8. 数据隐私与保留

- 候选人姓名、候选人编号、手机号、邮箱、岗位、批次、开始时间和截止时间由 HR 录入并作为 V1 必填业务信息；考核入口仍只使用姓名和考核码。
- V1 不保存完整简历和身份证号等与考试无直接关系的敏感字段。
- 成绩导出必须受 HR 或管理员权限保护。
- 日志中不得输出明文考核码、完整 token、密码或标准答案。
- 候选人临时用户可以在保留成绩关联的前提下停用，不应直接物理删除。
