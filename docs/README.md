# 企业人才考核系统工程文档

> 文档版本：V0.1  
> 更新日期：2026-09-02  
> 当前状态：需求基线已建立，业务二开尚未开始

## 1. 文档用途

本目录是本项目后续开发的主要依据，用于统一产品范围、业务规则、数据结构、接口契约、实施顺序和验收标准。

新的开发会话应优先读取本目录，而不是仅根据聊天记录或原始开源项目 README 推断需求。

## 2. 推荐阅读顺序

1. [AI_HANDOFF.md](./AI_HANDOFF.md)：新会话入口、当前状态和开发约束。
2. [01-product-requirements.md](./01-product-requirements.md)：产品范围、角色、流程和验收要求。
3. [02-system-design.md](./02-system-design.md)：现有系统复用方式和目标架构。
4. [03-data-model.md](./03-data-model.md)：数据库新增表、字段和状态机。
5. [04-api-spec.md](./04-api-spec.md)：后续开发接口契约。
6. [05-development-plan.md](./05-development-plan.md)：开发阶段、任务顺序和完成定义。
7. [06-testing-acceptance.md](./06-testing-acceptance.md)：测试清单和业务验收用例。
8. [07-intranet-deployment.md](./07-intranet-deployment.md)：开发环境与后续内网部署方案。
9. [08-decision-log.md](./08-decision-log.md)：已确认决策、建议默认值和待确认问题。

## 3. 需求权威级别

发生冲突时，按以下优先级处理：

1. 用户后续明确确认并更新到 `08-decision-log.md` 的决策。
2. 本目录中的产品需求和接口契约。
3. 原始产品方案文档。
4. yf-boot-exam 原项目的现有行为。

不能因为原项目已经这样实现，就默认该行为符合本项目需求。

## 4. 已确认的产品方向

- 产品定位：企业内部招聘、入职、晋升和日常考核系统。
- 岗位由系统管理员预先维护，不由 HR 每次创建。
- 每个岗位固定包含入职/面试、晋升、通用日常三种考核场景，对应固定代码 `ENTRY`、`PROMOTION`、`GENERAL`。
- HR 录入候选人并选择固定岗位，不负责设计题库规则。
- 候选人不注册，通过固定入口的“姓名 + 考核码”参加指定考核。
- 员工保留正式账号和注册功能，注册后应经过审核。
- 每条考核分配只允许一次作答；未交卷可恢复同一份试卷，交卷或过期后不得重考。
- 考生端最终结果只展示“是否通过”，分数、答案和解析仅供有权限的管理人员查看。
- 核心身份：系统管理员、HR、员工、候选人；出题/阅卷权限可作为可选独立角色。
- 客观题自动评分；简答题由人工评分并填写评语。
- 系统最终需要支持公司内网部署，业务数据保存在公司控制的环境中。

## 5. 当前技术基线

- 后端：Java 17、Spring Boot 3.2.1、Shiro、MyBatis-Plus、MySQL、Redis、Quartz。
- 前端：Vue 3、TypeScript、Element Plus、Vite。
- 基础项目：yf-team/yf-boot-exam。
- 本地依赖：`compose.yaml` 管理 MySQL 8 和 Redis 7。
- 后端默认端口：`8080`。
- Swagger/Knife4j：`http://localhost:8080/doc.html`。

## 6. 文档维护规则

- 需求改变时先修改 `08-decision-log.md`，再同步受影响文档。
- 接口实现发生变化时必须同步修改 `04-api-spec.md`。
- 数据库字段变化时必须同步修改 `03-data-model.md` 并提供迁移脚本。
- 每完成一个开发阶段，在 `05-development-plan.md` 中更新状态和实际结果。
- 不在文档中提交生产密码、JWT 密钥、真实候选人数据或其他敏感信息。
