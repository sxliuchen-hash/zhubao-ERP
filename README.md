# 来料加工进销存与生产管理系统

本仓库已经从文档评审进入 M1 实施基线与业务原型阶段。M0 已完成工程基础验证；M1 在此基础上增加可评审的业务原型页面、测试补强、开发原则、版本管理和进度文档。

- Spring Boot 后端基础工程。
- Vue 3 PWA 前端基础工程。
- 统一 API 响应和异常处理。
- RBAC / 数据范围枚举基线。
- 扫码解析。
- 转移式库存台账领域服务。
- 视频截取任务状态模型。
- SQL 基线脚本。
- 后端单元测试和前端组件/函数测试。
- M1 首页工作台、来料母单、生产工单、质检返工、库存追溯、PWA 作业原型。
- M2 核心业务工作台 API 基线，覆盖来料、生产、质检、库存、PWA。
- M2.1 前端产品化重构第一轮，升级生产追溯指挥台、全局状态、风险信号、业务筛选条和高密度 ERP 表格样式。
- M2.2 配置基线管理，覆盖系统参数、编码规则、角色权限、工序模板、仓库状态、视频参数、组件版本和备份策略。
- M2.3 配置变更控制基线，增加变更审批队列、审计日志和 MySQL 变更控制草案。
- M2.4 操作型 UI 重构第一轮，将首页升级为生产运营主控台，补充真实业务表格、单据详情、证据链、审批兜底队列和紧凑科技风格外壳。
- M2.5 核心业务页操作化，将来料、生产、质检、库存追溯页统一升级为主表、详情侧栏、扫码/查询入口和动作按钮的 ERP 操作界面。
- M3.1 库存台账 JDBC 持久化切入，新增 MySQL profile、JDBC 仓储、库存维度键迁移脚本和 H2 仓储测试，默认仍使用内存仓储便于评审。
- M3.2 软硬件现场 POC 记录基线，新增硬件 POC API、前端记录区和 MySQL 合同脚本，用于管理验证窗口、证据、阻断项和下一步动作。
- M3.3 业务工作台 JDBC 读取基线，新增 MySQL 工作台仓储、追溯时间线合同表和评审 seed 数据，可通过环境变量切换工作台 API 数据源。
- M3.5 来料确认入库业务操作基线，新增 `POST /api/business/operations/inbound-confirmations`，前端来料页可调用真实业务命令写入库存台账。
- M3.6 生产报工业务操作基线，新增 `POST /api/business/operations/production-reports`，将同一子工单维度下的在制库存转为半成品库存。
- M3.7 前端评审台信息架构整改，首页显式展示当前真实完成范围、未完成缺口、一期业务闭环状态图和模块完成度台账，避免把样例页面误判为完整交付系统。
- M3.8 质检冻结业务操作基线，新增 `POST /api/business/operations/quality-freezes`，将同一子工单维度下的半成品异常库存转为冻结库存。
- M3.9 返工派发业务操作基线，新增 `POST /api/business/operations/rework-dispatches`，将同一子工单维度下的冻结库存转为返工在制库存。
- M3.10 返工报工业务操作基线，新增 `POST /api/business/operations/rework-reports`，将同一子工单维度下的返工在制库存转回半成品库存，并在质检页增加真实动作核验区。
- M3.11 文档与附件管理基线，新增 `/api/documents/*` 元数据 API、前端文档附件工作台和 MySQL 合同脚本，用于管理文档包、证据附件、存储策略和归档审计。
- M3.12 附件上传与预览基线，新增 multipart 上传、checksum、上传审计、预览 URL 和内容读取接口，前端文档附件页提供上传/预览测试台。
- M3.13 前端评审台视觉整改，补强工业 ERP 工作台样式、模块代码导航、桌面/移动响应式约束和状态标签显示，避免宽表撑破移动端页面。
- M3.14 复检放行入库业务操作基线，新增 `POST /api/business/operations/quality-releases`，将同一子工单维度下的半成品库存转为成品库存。
- M3.15 成品出库业务操作基线，新增 `POST /api/business/operations/outbound-shipments`，将同一子工单维度下的成品库存转为已发货库存。
- M3.17 客户退货入库业务操作基线，新增 `POST /api/business/operations/customer-returns`，将同一子工单维度下的已发货库存转回退货复核库存。
- M3.18 首页产品级信息架构整改，参考 ERPNext/Odoo/InvenTree 的制造、库存和质量作业结构，将首页首屏重做为生产运营指挥台，补充工位运行、执行质量、风险热区、损耗审批快照和 M3.17 状态同步。
- M3.19 损耗审批与工资试算基线，新增 `POST /api/business/operations/loss-approvals`，按投入、产出、合理转移、标准损耗率、计件单价和扣损单价生成审批/工资试算快照；同时将首页首屏修正为表格优先的业务调度台。
- M3.20 真实业务链路测试基线，新增 `backend/src/test/resources/acceptance/real-flow-scenario-20260706.json` 脱敏验收 fixture，驱动同一客户/母单/工单/物料维度下的生产、质检、返工、复检、出库、退货和损耗试算回归测试，并补强损耗审批样例数据可用性测试。
- M3.21 前端评审控制台整改，首页首屏补强“我目前实际做到了什么”“还不具备交付条件”“业务调度台”“已接业务命令”“证据链健康度”和“模块完成度台账”，明确区分已验证能力、样例数据、POC 和待开发缺口。
- M3.22 轻量审批闭环基线，新增 `ApprovalService`、`/api/approvals/tasks`、`/api/approvals/tasks/pending`、`/api/approvals/tasks/{approvalCode}/decisions` 和 `V11__m3_lightweight_approval_contract.sql`；`loss-approvals` 会生成待审批或自动通过任务，但仍不代表复杂流程引擎、真实权限或正式工资锁账。
- M3.23 前端作业台视觉整改，首页从“项目评审说明页”改为“白班生产作业台”，首屏集中呈现今日 KPI、阻断优先级、业务调度队列、焦点工单、称重交接、证据链、硬件兜底、开源参考和模块 API 契约。
- M3.24 轻量审批 JDBC 持久化基线，新增 `JdbcApprovalTaskRepository`、`ZHUBAO_APPROVAL_REPOSITORY` 配置开关和 H2 仓储回归测试，审批任务与审计事件可写入 `approval_task` / `approval_audit_event` 合同表。
- M3.25 前端现场作业台重构，首页从“白班看板”进一步收敛为“来料加工现场总控”，首屏改为业务指标、一期链路、阻断队列、今日单据池、焦点工单、称重交接、证据链、硬件兜底、开源样例依据和模块 API 契约。
- M3.26 核心业务页现场化重构，将来料母单页改为“来料收货拆包作业台”，生产工单页改为“生产派工报工作业台”，保留确认入库和提交报工真实 API 动作，并补齐证据槽、称重交接、工位负载和报工规则。
- M3.27 UI 密度整改，针对“页面内容少、UI 太像汇报页”的反馈，压缩首页、来料母单、生产工单的页头、标题、统计卡片、链路条和操作区高度，让阻断队列、母单池、工单池更早进入首屏，并消除桌面视口页面级横向滚动。
- M3.28 质检库存作业台重构，将质检返工页改为“质检返工损耗作业台”，库存追溯页改为“库存台账追溯作业台”，首屏补齐质检阻断队列、今日质检池、客户库存池、追溯阻断队列、台账明细和证据槽。
- M3.29 轻量审批工作台，新增前端 `AP 审批工作台`，接入 `/api/approvals/tasks`、`/pending` 和 `/decisions`，展示待审批、已锁定、审批快照、工资边界和通过/驳回动作；仍不替代完整流程引擎、真实权限或正式工资锁账。
- M3.30 软硬件联动作业台，前端 `软硬件联动` 页面接入 `/api/hardware/devices`、`/integration-tasks`、`/poc-records` 和 `/summary`，形成设备兼容池、联动阻断队列、POC 记录、任务健康度和部署拓扑；接口失败时保留本地样例兜底，不把一期业务卡死在设备直连上。
- M3.31 售后复核闭环，新增 `POST /api/business/operations/after-sales-reviews`，将 `RETURN-CHECK` 退货复核库存按复核结论分流到成品、返工、报废或冻结状态；库存追溯页新增“售后复核”真实动作入口。
- M3.32 作业台视觉重整，针对“页面内容少、UI 不够好看”的反馈，重整全局外壳、顶栏、侧栏、表格、状态色和移动端密度，让 1280 桌面首屏能同时看到总控指标和今日单据池；本轮不新增业务接口。
- M3.33 权限守卫基线，新增 `X-Zhubao-Role` / `X-Zhubao-Operator` / `X-Zhubao-Data-Scope` 请求头解析和敏感 POST 权限拦截，覆盖库存台账直接转移、来料、报工、质检、出库、退货、售后复核、损耗审批、审批决策、附件上传和视频任务；当前仍不等于完整登录、菜单权限和数据范围过滤。
- M3.34 评审界面产品化整改，针对“页面仍像内部调试台、UI 丑”的反馈，重做全局视觉基线、页头、生产总控区、表格、按钮和状态色；本轮只改善前端呈现，不新增后端业务接口。
- M3.35 附件存储边界，新增 `AttachmentObjectStorage`、本地文件存储适配和 `zhubao.documents.storage.*` 配置，上传附件从内存缓存推进到 `local://evidence/...`，后续可切 NAS/S3 签名访问。
- M3.36 现场指挥台产品化整改，针对“只看到简单页面、内容不够、UI 太丑”的反馈，重构首页为主从式 ERP/MES 调度台：现场态势、一期闭环、今日单据池、焦点工单、称重交接、质检门禁、证据链、硬件兜底和开源依据集中呈现；本轮仍不新增后端业务接口。
- M3.37 附件元数据持久化边界，新增 `DocumentManagementRepository`、内存仓储、JDBC 仓储和 `ZHUBAO_DOCUMENTS_REPOSITORY` 配置；文档包、附件元数据、审计事件可切换到 `doc_*` 合同表，上传内容仍默认走本地对象存储适配。
- M3.38 作业界面整改，针对“当前页面简单、内容少、UI 丑”的反馈，压缩 760px 窄视口全局导航和页头，文档附件页新增当前文档包、待补证据、仓储边界运营板，避免首屏只停留在摘要卡片。
- M3.39 运维中心基线，新增 `/api/operations/center` 和 `OC 运维中心` 页面，覆盖日志、服务器运行指标、报错汇总、数据库备份、数据库/前端/后端运行状态和运维接入清单。
- M3.40 评审 UI 纠偏，针对“只看到简单页面、没有内容、UI 太丑”的反馈，先承认当前前端仍是评审/POC 可视化而非完整生产系统；本轮把全局外壳从暗色演示板改为浅色 ERP/MES 操作台，压缩侧栏、页头、卡片和表格高度，让首页首屏优先呈现今日单据池、焦点工单和异常处理。
- M3.41 Docker 离线交付基线，新增后端/前端运行镜像、Docker Compose、离线镜像 tar 打包脚本、客户启动脚本和 [19_Docker一键部署与离线交付.md](./docs/19_Docker一键部署与离线交付.md)，运行包只包含构建产物、镜像、SQL 初始化和部署脚本，不包含源码。
- M3.42 生产操作台整改，针对“页面简单、内容少、UI 丑”的反馈，把首页首屏重构为流程链路、今日单据池、当前处理单、操作员待办和焦点任务的三栏 ERP/MES 工作台，减少说明性卡片，优先呈现可操作对象和下一动作。
- M3.43 运维中心备份证据增强，配置 `zhubao.operations.backup-root` 后会扫描最新备份文件并展示文件大小、SHA-256 checksum 和 RPO 状态；未配置目录时继续明确显示“未计算”，不伪装成真实备份。
- M3.44 Docker 离线包完整性校验，运行包新增 `MANIFEST.sha256`，启动脚本会校验 Compose、SQL、README、启动脚本和镜像 tar；zip 旁路生成 `.sha256`，便于构建后上传到客户服务器前后核对。
- M3.45 运维中心前端 URL 探活，区分展示用 `ZHUBAO_FRONTEND_URL`/`ZHUBAO_PUBLIC_URL` 和后端实际探测用 `ZHUBAO_FRONTEND_PROBE_URL`；Docker 默认探测 `http://frontend/`，本地评审默认未配置以避免误报。
- M3.46 业务操作台视觉整改，针对“只看到简单页面、UI 太丑”的反馈，将全局外壳和首页覆盖为更克制的企业 ERP/MES 操作台视觉：紧凑侧栏、浅色工作区、清晰表格、状态色收敛、首屏继续突出单据池、阻断和当前处理单。
- M3.47 运维中心真实日志读取与报错聚合，配置 `zhubao.operations.log-file` / `ZHUBAO_LOG_FILE` 后可读取日志文件尾部记录，并把 WARN/ERROR 按视频截取、数据库备份、登录权限、数据库连接、附件存储等来源汇总到报错表。
- M3.48 运维中心 PWA 静态资源探活，基于 `ZHUBAO_FRONTEND_PROBE_URL` 检查 `manifest.webmanifest` 和 `sw.js`，让前端站点可访问、Manifest 可访问、Service Worker 可访问三类问题分开定位。
- M3.49 现场控制台视觉重构，针对“页面内容少、UI 太丑”的反馈，在不新增后端业务接口的前提下统一全局外壳、首页三栏作业台、单据池、当前处理单和运维中心的控制台视觉层，让浏览器首屏更接近生产现场 ERP/MES 工作台。

## 目录结构

```text
backend/   Spring Boot 3 / Java 17 后端
frontend/  Vue 3 / Vite / PWA 前端
docs/      需求、架构、硬件、接口、评估和 M0 文档
scripts/   本地开发和验证脚本
```

## 环境要求

- Java 17。
- Node.js 18+。
- npm 10+。
- pnpm 9+。
- Maven 不要求全局安装，`scripts/maven.ps1` 会下载项目本地 Maven 到 `.tools/`。

## 常用命令

后端测试：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/maven.ps1 -f backend/pom.xml test
```

前端依赖和测试：

```powershell
$env:npm_config_cache=".\.npm-cache"
pnpm install --store-dir .\.pnpm-store
pnpm --filter zhubao-erp-frontend test
pnpm --filter zhubao-erp-frontend build
```

前端连接后端 API：

```powershell
$env:VITE_API_BASE_URL="http://localhost:8080"
pnpm --filter zhubao-erp-frontend dev
```

未设置或后端未启动时，M1/M2 工作台页面会使用本地评审样例兜底。

M0 一键检查：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/check-m0.ps1
```

M1 一键检查：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/check-m1.ps1
```

启动后端：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/maven.ps1 -f backend/pom.xml spring-boot:run
```

启用 MySQL 库存台账持久化：

```powershell
$env:SPRING_PROFILES_ACTIVE="mysql"
$env:ZHUBAO_DB_URL="jdbc:mysql://localhost:3306/zhubao_erp?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai"
$env:ZHUBAO_DB_USERNAME="zhubao"
$env:ZHUBAO_DB_PASSWORD="your-password"
powershell -ExecutionPolicy Bypass -File scripts/maven.ps1 -f backend/pom.xml spring-boot:run
```

启用 MySQL 业务工作台评审数据：

```powershell
$env:SPRING_PROFILES_ACTIVE="mysql"
$env:ZHUBAO_BUSINESS_REPOSITORY="jdbc"
$env:ZHUBAO_BUSINESS_SNAPSHOT_CODE="SNAP-20260706-DAY"
powershell -ExecutionPolicy Bypass -File scripts/maven.ps1 -f backend/pom.xml spring-boot:run
```

业务工作台 JDBC 当前读取的是 `biz_workspace_*` 评审快照和 seed 数据，用于验收与演示；正式交易闭环仍需继续接入来料、生产、质检、出库等真实业务表。

启用 MySQL 轻量审批任务持久化：

```powershell
$env:SPRING_PROFILES_ACTIVE="mysql"
$env:ZHUBAO_APPROVAL_REPOSITORY="jdbc"
powershell -ExecutionPolicy Bypass -File scripts/maven.ps1 -f backend/pom.xml spring-boot:run
```

审批 JDBC 当前只覆盖固定场景轻量审批任务和审计事件；M3.33 已对敏感 POST 接入轻量角色守卫，但真实登录、菜单权限、数据范围过滤、复杂审批流、审批 UI 和工资锁账仍需继续开发。

启用 MySQL 文档附件元数据持久化：

```powershell
$env:SPRING_PROFILES_ACTIVE="mysql"
$env:ZHUBAO_DOCUMENTS_REPOSITORY="jdbc"
powershell -ExecutionPolicy Bypass -File scripts/maven.ps1 -f backend/pom.xml spring-boot:run
```

文档附件 JDBC 当前覆盖 `doc_package`、`doc_evidence_attachment`、`doc_audit_event` 三张合同表；附件二进制内容仍由 `zhubao.documents.storage.*` 指定的存储适配处理，生产 NAS/S3 签名访问、预览权限和版本锁定仍需继续开发。

启动前端：

```powershell
pnpm --filter zhubao-erp-frontend dev
```

默认地址：

- 后端 API：`http://localhost:8080`
- 前端：`http://localhost:5173`

Docker 离线运行包：
```powershell
powershell -ExecutionPolicy Bypass -File scripts\docker-build-package.ps1 -Version m3.44.0
```

只验证无源码运行包结构、不构建镜像：
```powershell
powershell -ExecutionPolicy Bypass -File scripts\docker-build-package.ps1 -Version m3.44-layout -NoDockerBuild -SkipTests
```

## 当前 API

- `GET /api/m0/status`
- `POST /api/scan/resolve`
- `POST /api/inventory/ledger/transfers`
- `GET /api/inventory/ledgers`
- `GET /api/inventory/balances`
- `POST /api/video-clip-tasks`
- `GET /api/video-clip-tasks`
- `GET /api/samples/scenarios`
- `GET /api/samples/summary`
- `GET /api/hardware/devices`
- `GET /api/hardware/integration-tasks`
- `GET /api/hardware/poc-records`
- `GET /api/hardware/summary`
- `GET /api/documents/summary`
- `GET /api/documents/packages`
- `GET /api/documents/attachments`
- `GET /api/documents/audit-events`
- `POST /api/documents/attachments/uploads`
- `GET /api/documents/attachments/{code}/preview-url`
- `GET /api/documents/attachments/{code}/content`
- `GET /api/operations/center`
- `GET /api/business/workspaces/summary`
- `GET /api/business/workspaces/inbound`
- `GET /api/business/workspaces/production`
- `GET /api/business/workspaces/quality`
- `GET /api/business/workspaces/inventory`
- `GET /api/business/workspaces/mobile`
- `POST /api/business/operations/inbound-confirmations`
- `POST /api/business/operations/production-reports`
- `POST /api/business/operations/quality-freezes`
- `POST /api/business/operations/rework-dispatches`
- `POST /api/business/operations/rework-reports`
- `POST /api/business/operations/quality-releases`
- `POST /api/business/operations/outbound-shipments`
- `POST /api/business/operations/customer-returns`
- `POST /api/business/operations/after-sales-reviews`
- `POST /api/business/operations/loss-approvals`
- `GET /api/approvals/tasks`
- `GET /api/approvals/tasks/pending`
- `GET /api/approvals/tasks/{approvalCode}`
- `POST /api/approvals/tasks/{approvalCode}/decisions`
- `GET /api/configuration/baseline/summary`
- `GET /api/configuration/baseline/system-parameters`
- `GET /api/configuration/baseline/code-rules`
- `GET /api/configuration/baseline/role-permissions`
- `GET /api/configuration/baseline/process-templates`
- `GET /api/configuration/baseline/warehouse-states`
- `GET /api/configuration/baseline/video-parameters`
- `GET /api/configuration/baseline/components`
- `GET /api/configuration/baseline/backup-policies`
- `GET /api/configuration/baseline/change-requests`
- `GET /api/configuration/baseline/audit-events`

## 当前阶段与下一步

当前已进入 M3 一期开发推进中，前端已按 M3.49 继续向生产现场作业台和高密度 ERP/MES 操作界面收敛，首页已从评审展示板重构为三栏生产操作台，并进一步统一为现场控制台视觉，首屏突出流程链路、今日单据池、当前处理单、操作员待办、焦点任务、证据链和硬件兜底；来料母单、生产工单、质检返工、库存追溯、轻量审批、文档附件、软硬件联动和运维中心已经从说明/列表页改为现场操作台，承载业务指标、一期链路、今日单据池、焦点对象、证据链、运行指标、日志、备份和真实 API 动作。后端已补入轻量审批任务、硬件联动 API、售后复核分流、轻量权限守卫、附件本地存储适配、附件元数据 JDBC 仓储、运维中心聚合 API、前端 URL 探活、PWA Manifest/Service Worker 探活、真实日志读取与 WARN/ERROR 聚合、备份文件大小与 SHA-256 证据、Docker 离线交付与运行包完整性校验基线。下一步重点不再是继续堆演示页，而是把真实登录权限、NAS/S3 签名访问、软硬件证据和生产运维接入补齐：

- MySQL DDL 完整版和迁移策略。
- API 字段字典。
- RuoYi-Vue 具体分支和许可证确认。
- NVR、标签打印、PWA 扫码、备份恢复的现场附件关联和真实设备复测。
- 将 M2 工作台样例 API 迁移为 MySQL seed 数据和真实业务聚合。
- 将配置基线、配置变更单和审计日志迁移为 MySQL seed 数据，并接入真实登录、菜单权限和数据范围过滤。
- 补齐审批 UI、复杂审批流、工资锁账/发放边界、NAS/S3 签名访问和售后结算边界，避免当前原型停留在局部台账动作、附件本地适配、附件元数据合同表与工资试算快照。
- 接入日志平台或高性能只读日志适配器、PWA 运行态细节、数据库备份目录、checksum 和恢复演练记录，避免运维中心停留在评审样例。

## 开发管理文档

- [M1 开发验证报告](docs/12_M1开发验证报告.md)
- [开发原则与版本管理规范](docs/13_开发原则与版本管理规范.md)
- [开发进度跟踪](docs/14_开发进度跟踪.md)
- [样例与软硬件联动管理](docs/15_样例与软硬件联动管理.md)
- [字段字典与 API 契约](docs/16_字段字典与API契约.md)
- [样例数据来源与真实数据测试](docs/17_样例数据来源与真实数据测试.md)
- [运维中心与运行监控设计](docs/18_运维中心与运行监控设计.md)
- [Docker 一键部署与离线交付](docs/19_Docker一键部署与离线交付.md)
- [腾讯云与本地部署运维方案](docs/20_腾讯云与本地部署运维方案.md)
