# 全链路验收（Windows 本地隔离环境）

用途：验证公司日常考核从配置、发放到答题、阅卷、结果与导出的完整业务链。使用真实 HTTP、MySQL、Redis 和 Chrome；不连接线上测试站，不使用真实人员记录。

## 前置条件

- 在仓库根目录执行；Java 17、Maven、Python 3.10+、Node.js、Docker 可用。
- 本地 yf-exam-mysql、yf-exam-redis 已运行，源库 yf_boot_exam 至少已有基础迁移（本轮源库 V024）；新版本 V025 只由隔离后端在测试库自动迁移。源库只读取，须含基础角色、菜单、字典及至少两个启用部门。
- 前端依赖已安装，yf-bev2-vue/dist-pro 是当前源码的正式构建；yf-bev2-api/target/release-build/yf-bev2-api.jar 是当前源码的 release 构建。
- Node 能 require('playwright')，已安装 Chrome。默认使用 channel: chrome，可通过 QA_BROWSER_CHANNEL 改为另一个已安装的兼容浏览器通道。
- 127.0.0.1:18090 空闲。脚本不会停掉占用端口的已有服务。

## 构建与执行

先以有时限的命令构建前端：

~~~powershell
@'
import sys
sys.path.insert(0, 'scripts/ops')
from ops_common import ROOT, run
print(run(['node', 'node_modules/vite/bin/vite.js', 'build', '--mode', 'pro'],
          cwd=ROOT / 'yf-bev2-vue', timeout=300, record_output=True).decode('utf-8', 'replace')[-3000:])
'@ | python -
python scripts/ci/run.py 300 mvn.cmd -B -f yf-bev2-api/pom.xml -Prelease verify
~~~

Maven 离线缓存已准备好的机器可以附加 -o；本次使用本机已有的 -s work/p0-maven-settings.xml。不要复制其他环境的密钥或密码。

在本次 Codex 机器上，Playwright 位于已提供的工具运行时，可这样加入 Node 模块路径；其他机器指向自己的 Playwright 安装目录即可：

~~~powershell
$env:NODE_PATH = Join-Path $env:USERPROFILE '.cache\codex-runtimes\codex-primary-runtime\dependencies\node\node_modules'
python scripts/ci/run.py 300 python scripts/qa/run_acceptance.py
~~~

完整入口依次执行基础配置、接口主链、简答阅卷、过期/停用、Excel/Word 导入、立即生效回归、100 人负载、成绩/数据范围、答题保存队列和真实浏览器操作。最终自动停止专用后端、删除专用 Redis/数据库，并比较源库全部表的行数和校验值。任一步失败都会返回非零退出码。--skip-load 只用于快速诊断，不可作为完整容量验证结论。

每一步设有 15–120 秒上限，外层总上限 300 秒；子进程连续 60 秒无实际日志进展会终止进程树。执行器定期报告阶段仍在运行，详细 stdout/stderr 写入 work/codex-logs/。若被操作系统或外层强制终止，finally 可能无法运行，使用下面的清理命令：

~~~powershell
python scripts/ci/run.py 120 python scripts/qa/environment.py cleanup
~~~

清理只接受经过校验的 qa_exam_<12位随机十六进制> 库名和对应 Redis 名；停止进程前核对 PID 的完整命令行。存在未清理环境时，入口拒绝创建第二套环境。不要并行启动两份验收。

## 用例文件与断言

| 文件 | 测试内容 |
| --- | --- |
| environment.py | 隔离 MySQL/Redis/应用生命周期、同一随机测试密钥重启，源数据指纹校验，进程清理 |
| acceptance.py | 员工注册审批、候选人认证、三场景配置、权限/归属、幂等建卷与交卷、快照、评分、阅卷、过期、停用与恢复 |
| extended.py | Excel/Word 部分成功/重复导入、加密清单重启恢复/权限/失效码/到期、报告与下载内容、部门范围、默认时间回归、可选 100 人负载、浏览器夹具 |
| answer-queue.cjs | 从实际 Enter.vue 提取逻辑，验证连续保存顺序、切题/交卷前刷新待保存文本、失败保留/阻断、重试与清空 |
| browser.cjs | 实际页面登录、点击、选题、刷新、文字输入、评分、筛选、上传与下载；14 个浏览器验收场景，包含关闭/刷新恢复清单和修改密码重新登录 |
| run_acceptance.py | 新环境全流程编排、分步日志、最终自动清理 |

业务用例名称、断言与实现放在同一脚本中；失败记录包含用例 ID、原因和耗时。负面用例要求可识别的认证/权限/业务拒绝，不接受 HTTP 5xx、404 或重定向作为“拒绝成功”。

管理账号和负载员工是虚构 SQL 夹具；常规员工另外走真实注册、待审核和审批接口。岗位、题库、题目、模板、发放、作答、阅卷均走实际 API。只有快照对照、过期时钟加速和结果核验使用隔离库 SQL。验证码图片由真实接口生成，自动化从专用测试 Redis 取值后提交正常登录接口，并验证一次性消费；不评价人工辨认验证码的体验。

## 输出及保密

- work/codex-logs/qa-run-<时间>.json：各阶段状态和自动清理结果。
- work/codex-logs/qa-api-results.json：最新一轮接口/业务用例及可选负载结果（数量以 JSON 为准）。
- work/codex-logs/qa-load-results.json：100 人负载耗时、数量和错误。
- work/qa-browser/results.json：浏览器用例及未处理的页面异常。
- work/qa-browser/*.png：虚构数据的业务状态截图；results.xlsx 为虚构成绩导出。
- work/codex-logs/*qa-cleanup.json：源库未改变及资源清理证据。
- 上次机器可读报告保存到 work/qa-archive/，详细日志保留各自时间戳。

随机测试密码、token 和考核码只暂存在忽略目录 .local/，清理后删除。不输出或提交这些值；考核码下载不长期保存、不截图。脚本不复制真实账号、候选人、题库、作答或评分记录；仅复制表结构、迁移历史和基础配置。不要把工作日志目录提交到仓库。

本地 Chrome 与短时 100 人负载不能替代实际服务器、网络、长时负载、移动端或跨浏览器验收；详见本次 [验收报告](../../docs/DAILY_USE_ACCEPTANCE_2026-09-24.md)。

D-039 修复回归按用户要求使用 --skip-load，不再次执行容量测试，也不做备份工作。运行器在导入成功后实际停止并重启 Java 进程，再以原 JWT 恢复原码；随机 QA 密钥只保存在被忽略的临时状态文件，最终清理。
