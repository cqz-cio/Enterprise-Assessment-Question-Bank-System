# 企业人才考核系统

面向企业内部招聘面试、员工转正和晋升的题库与考核系统。

支持岗位题库、考核发放、候选人考核码入口、员工任务、随机组卷、自动评分、人工阅卷与成绩导出。

- 开发交接：[docs/AI_HANDOFF.md](docs/AI_HANDOFF.md)
- 工程文档：[docs/README.md](docs/README.md)
- 本地运行与部署：[docs/07-intranet-deployment.md](docs/07-intranet-deployment.md)
- 产品需求：[docs/01-product-requirements.md](docs/01-product-requirements.md)

技术栈：Java 17、Spring Boot 3、Vue 3、TypeScript、Element Plus、MySQL、Redis。
已有环境使用根目录 `start-backend.ps1` / `stop-backend.ps1` 管理后端；密钥保存和首次部署请遵循部署文档，勿重新导入已有数据库。

本项目基于 yf-boot-exam 二次开发。原始作者版权及 MIT 许可保留在 [LICENSE](LICENSE)。内部包名和表名沿用原结构以保持兼容。
