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

启动前端：

```powershell
pnpm --filter zhubao-erp-frontend dev
```

默认地址：

- 后端 API：`http://localhost:8080`
- 前端：`http://localhost:5173`

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

当前已进入 M3 一期开发推进中，前端已按 M3.16 调整为更紧凑的工业 ERP 工作台样式。下一步重点不再是继续堆演示页，而是把真实业务闭环、持久化、权限审批和软硬件证据补齐：

- MySQL DDL 完整版和迁移策略。
- API 字段字典。
- RuoYi-Vue 具体分支和许可证确认。
- NVR、标签打印、PWA 扫码、备份恢复的现场附件关联和真实设备复测。
- 将 M2 工作台样例 API 迁移为 MySQL seed 数据和真实业务聚合。
- 将配置基线、配置变更单和审计日志迁移为 MySQL seed 数据，并接入真实权限。
- 补齐退货、损耗审批、工资试算和对象存储，避免当前原型停留在局部台账动作。

## 开发管理文档

- [M1 开发验证报告](docs/12_M1开发验证报告.md)
- [开发原则与版本管理规范](docs/13_开发原则与版本管理规范.md)
- [开发进度跟踪](docs/14_开发进度跟踪.md)
- [样例与软硬件联动管理](docs/15_样例与软硬件联动管理.md)
- [字段字典与 API 契约](docs/16_字段字典与API契约.md)
- [样例数据来源与真实数据测试](docs/17_样例数据来源与真实数据测试.md)
