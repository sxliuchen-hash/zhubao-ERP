# 来料加工进销存与生产管理系统

面向客户来料加工。主链路是收货、报工、质检、库存、出库和追溯。当前界面是一套浅色作业台，日常页用表格和当前单据。

基础版本是 Git 标签 `baseline-m3.138`。之后的整理在分支 `dev/after-m3.138`。

文档索引见 [docs/README.md](docs/README.md)。历史调研、样张和进度长记录在 [docs/archive](docs/archive/)。

## 目录结构

```text
backend/   Spring Boot 3 / Java 17 后端
frontend/  Vue 3 / Vite / PWA 前端
docs/      现行设计文档；过程材料在 docs/archive
scripts/   本地开发和验证脚本
```

## 环境要求

- Java 17。
- Node.js 18+。
- npm 10+。
- pnpm 9+。
- Maven 不要求全局安装，`scripts/maven.ps1` 会下载项目本地 Maven 到 `.tools/`。

## 常用命令

一键启动前后端本地开发环境（推荐）：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/dev-up.ps1
```

或直接双击项目根目录的 `dev-up.cmd`。该脚本会：

- 启动后端 Spring Boot（端口 8080，首次自动下载内置 Maven）。
- 启动前端 Vite（端口 5173，首次自动 `pnpm install`）。
- 前端已配置 `/api` 代理到后端 8080，启动后自动打开浏览器 `http://localhost:5173`。

默认在两个独立 PowerShell 窗口分别启动前后端，便于分别看日志、关窗口即停。常用开关：

```powershell
# 仅启动后端 / 仅启动前端
powershell -ExecutionPolicy Bypass -File scripts/dev-up.ps1 -BackendOnly
powershell -ExecutionPolicy Bypass -File scripts/dev-up.ps1 -FrontendOnly
# 在当前窗口以后台作业方式启动（日志混合，Ctrl+C 一并停止）
powershell -ExecutionPolicy Bypass -File scripts/dev-up.ps1 -SameWindow
# 启动后不自动打开浏览器
powershell -ExecutionPolicy Bypass -File scripts/dev-up.ps1 -NoBrowser
```

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

前端 UI 冒烟检查会读取首页、来料母单和配置页，生成 `output/playwright/m352-*.png` 截图和 `m352-ui-check.json` 指标。首次运行如本地没有 Playwright 包，可先执行一次 `npx --yes --package playwright playwright --version` 让 npm 缓存包落地：

```powershell
$env:npm_config_cache=".\.npm-cache"
$env:LOCALAPPDATA="$PWD\.cache\localappdata"
node scripts\check-frontend-ui.cjs http://localhost:5173/
```

前端连接后端 API：

```powershell
$env:VITE_API_BASE_URL="http://localhost:8080"
pnpm --filter zhubao-erp-frontend dev
```

开发服务器把 `/api` 代理到 `localhost:8080`。后端没启动或接口没有单据时，页面显示空列表和失败原因，不再使用本地样例。需要直连后端地址时，再设置 `VITE_API_BASE_URL`。

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

启用 MySQL 硬件 POC 记录仓储：

```powershell
$env:SPRING_PROFILES_ACTIVE="mysql"
$env:ZHUBAO_HARDWARE_POC_REPOSITORY="jdbc"
powershell -ExecutionPolicy Bypass -File scripts/maven.ps1 -f backend/pom.xml spring-boot:run
```

硬件 POC JDBC 当前覆盖 `hw_poc_record`，用于持久化现场验证窗口、结果、证据摘要、阻断项、责任人和下一步动作；`V19__m3_hardware_poc_record_seed.sql` 会写入 NVR 视频、标签打印、PWA 弱光扫码和电子秤人工读数 4 条基线记录。该边界不改变 `/api/hardware/poc-records` 契约，也不代表 NVR、打印机、扫码设备或电子秤已经完成厂商 SDK/协议直连。

启用 MySQL 轻量审批任务持久化：

```powershell
$env:SPRING_PROFILES_ACTIVE="mysql"
$env:ZHUBAO_APPROVAL_REPOSITORY="jdbc"
powershell -ExecutionPolicy Bypass -File scripts/maven.ps1 -f backend/pom.xml spring-boot:run
```

审批 JDBC 当前只覆盖固定场景轻量审批任务和审计事件；M3.33 已对敏感 POST 接入轻量角色守卫，M3.119 已补账号仓储、密码哈希、登录失败锁定、账号启停、管理员解锁、生产密码策略、密码过期检测、管理员新建/重置非 demo 密码后强制首次登录改密、临时密码一次性消费、登录态自助改密/续期、认证审计摘要链、业务工作台 GET 首版数据范围过滤、账号管理审批摘要、请求头身份兜底开关、账号客户范围边界、账号前置审批就绪度只读契约、前置审批草稿评估契约、前置审批申请单 JDBC 持久化、账号动作申请单号校验边界、生产配置强制申请单号开关、申请单审批任务交接、审批通过态强制校验、账号停用会话吊销和前置审批证据上传入口，但持久化菜单、完整组织客户授权表、复杂审批流、审批 UI、正式电子签字和工资锁账仍需继续开发。

启用 MySQL 登录账号仓储：

```powershell
$env:SPRING_PROFILES_ACTIVE="mysql"
$env:ZHUBAO_AUTH_USERS_REPOSITORY="jdbc"
powershell -ExecutionPolicy Bypass -File scripts/maven.ps1 -f backend/pom.xml spring-boot:run
```

账号 JDBC 当前覆盖 `auth_user_account`，保存 `username`、`password_hash`、操作人快照、角色、数据范围、客户范围、启停状态、`demo_account`、`password_changed_at`、`password_change_required`、`temporary_password_consumed`、失败登录次数、锁定到期时间、最近登录和最近失败时间；M3.119 已提供账号创建/更新、启停、停用账号吊销现有 Bearer 会话、管理员解锁、密码策略校验、90 天密码过期检测、管理员新建/重置非 demo 密码后标记 `passwordChangeRequired` 并重置临时密码消费状态、首次临时密码登录成功后一次性消费、重复临时密码登录拒绝、临时密码会话业务上下文拒绝、登录态自助改密清除强制改密标记和消费标记并续期、认证审计摘要链、业务工作台 GET 首版数据范围过滤、账号管理审批摘要、MySQL profile 请求头兜底默认关闭、`CUSTOMER_RANGE` 账号客户范围、账号前置审批就绪度只读契约、账号前置审批草稿评估契约、前置审批申请单 JDBC 持久化、账号动作申请单号校验边界、生产配置强制申请单号开关、申请单审批任务交接、审批通过态强制校验和前置审批证据上传入口，并写入 `ACCOUNT_MANAGEMENT` / `ACCOUNT_PRE_APPROVAL` / `LOGIN DENIED` / `PASSWORD_CHANGE` / `SESSION_REVOKED` 审计事件。默认初始化账号仍是评审用账号，生产上线前必须替换密码、停用或删除演示账号，并继续接入 SSO/JWT、正式电子签字、审计防篡改、完整组织客户授权表和正式账号生命周期策略。

启用 MySQL 登录审计持久化：

```powershell
$env:SPRING_PROFILES_ACTIVE="mysql"
$env:ZHUBAO_AUTH_AUDIT_REPOSITORY="jdbc"
powershell -ExecutionPolicy Bypass -File scripts/maven.ps1 -f backend/pom.xml spring-boot:run
```

登录审计 JDBC 当前覆盖 `auth_audit_event`，记录登录成功、失败、锁定拒绝、密码过期拒绝、会话读取、退出、无效 token 解析、M3.89 账号管理动作和 M3.92 自助改密动作，只保存 token 尾号；M3.93 起新增 `previous_event_hash/event_hash` 摘要链字段和 `GET /api/auth/audit-integrity` 校验摘要。前端登录会话已按后端菜单编码过滤侧栏模块，M3.96 业务工作台 GET 已按当前 `dataScope` 做首版过滤，M3.97 账号管理动作会生成 `ACCOUNT_MANAGEMENT` 审批摘要任务，M3.98 MySQL profile 默认关闭请求头身份兜底，M3.99 Bearer `CUSTOMER_RANGE` 会话可从账号 `customerScope` 带出客户范围。但 SSO/JWT、生产菜单持久化、完整组织客户授权表、审计不可篡改策略和安全日志平台仍需继续接入。

启用 MySQL 文档附件元数据持久化：

```powershell
$env:SPRING_PROFILES_ACTIVE="mysql"
$env:ZHUBAO_DOCUMENTS_REPOSITORY="jdbc"
powershell -ExecutionPolicy Bypass -File scripts/maven.ps1 -f backend/pom.xml spring-boot:run
```

文档附件 JDBC 当前覆盖 `doc_package`、`doc_evidence_attachment`、`doc_audit_event` 三张合同表；附件二进制内容仍由 `zhubao.documents.storage.*` 指定的存储适配处理；M3.120 已为本地内容读取补短期 HMAC 预览令牌和过期时间校验，生产 NAS/S3/COS 对象存储切换、版本锁定和正式审计保全仍需继续开发。

启用 MySQL 权限菜单树持久化：

```powershell
$env:SPRING_PROFILES_ACTIVE="mysql"
$env:ZHUBAO_MENU_REPOSITORY="jdbc"
powershell -ExecutionPolicy Bypass -File scripts/maven.ps1 -f backend/pom.xml spring-boot:run
```

菜单树 JDBC 覆盖 `sys_navigation_menu` 和 `sys_role_menu`：`V28__m3_navigation_menu_contract.sql` seed 12 个一期菜单和各角色可见性，菜单标签、模块、路由、按钮权限编码、排序、启停和角色绑定均可在库中自定义，`GET /api/security/menu-tree` 契约不变。本地默认内存菜单，便于评审。该边界解决生产菜单持久化，不代表菜单在线编辑 UI 或完整组织客户授权表完成。

启用 MySQL 客户授权关系表：

```powershell
$env:SPRING_PROFILES_ACTIVE="mysql"
$env:ZHUBAO_CUSTOMER_AUTHORIZATION_REPOSITORY="jdbc"
powershell -ExecutionPolicy Bypass -File scripts/maven.ps1 -f backend/pom.xml spring-boot:run
```

客户授权 JDBC 覆盖 `sys_customer_authorization`：`CUSTOMER_RANGE` 账号可见客户范围由授权表维护（总经理/管理员通过 `POST /api/security/customer-authorizations` 授权、`/revocations` 撤销），登录会话解析时按授权表计算有效范围，授权表无记录时回退账号 `customerScope` 文本字段。本地默认内存仓储，便于评审。该边界不代表完整组织树、跨对象关系推导或全系统行级权限完成。

启用 MySQL 机器档案主数据：

```powershell
$env:SPRING_PROFILES_ACTIVE="mysql"
$env:ZHUBAO_MACHINE_PROFILE_REPOSITORY="jdbc"
powershell -ExecutionPolicy Bypass -File scripts/maven.ps1 -f backend/pom.xml spring-boot:run
```

机器档案 JDBC 覆盖 `sys_machine`：`V30__m3_machine_profile_contract.sql` seed 雕刻 1-8 号机，机器编号唯一，`GET/POST /api/machine-profiles` 维护机器档案，为设备产量绑定（M3.134）提供真实 `machineId` 来源。本地默认内存仓储，便于评审。

启用 MySQL 硬件设备网关持久化：

```powershell
$env:SPRING_PROFILES_ACTIVE="mysql"
$env:ZHUBAO_GATEWAY_REPOSITORY="jdbc"
powershell -ExecutionPolicy Bypass -File scripts/maven.ps1 -f backend/pom.xml spring-boot:run
```

设备网关 JDBC 覆盖 `hw_gateway_device` 和 `hw_gateway_reading`：注册设备保存设备名、可接受读数类型和 PBKDF2 哈希后的设备密钥，读数保存归一化值、单位、编码、READY/WARNING/BLOCKED 状态和原始报文。低成本硬件通过 `POST /api/hardware/gateway/readings` 凭设备密钥推送标准化读数，评审默认设备密钥由 `ZHUBAO_GATEWAY_DEMO_DEVICE_KEY` 提供（默认 `zhubao-device-demo`），生产上线前必须替换、停用或删除评审设备并轮换密钥。该边界打通设备到系统的接入通道，不代表厂商 SDK/协议直连、真实设备型号冻结或读数自动写业务台账完成。

启动前端：

```powershell
pnpm --filter zhubao-erp-frontend dev
```

默认地址：

- 后端 API：`http://localhost:8080`
- 前端：`http://localhost:5173`

Docker 离线运行包：
```powershell
powershell -ExecutionPolicy Bypass -File scripts\docker-build-package.ps1 -Version m3.62.0
```

只验证无源码运行包结构、不构建镜像：
```powershell
powershell -ExecutionPolicy Bypass -File scripts\docker-build-package.ps1 -Version m3.62-layout -NoDockerBuild -SkipTests
```

单独验收已有运行包目录或 zip：
```powershell
powershell -ExecutionPolicy Bypass -File scripts\verify-runtime-package.ps1 -PackagePath .deploy-artifacts\zhubao-erp-runtime-m3.62.0.zip
```

运行包内生成 MySQL 备份：
```powershell
powershell -ExecutionPolicy Bypass -File .\backup-mysql.ps1
```

运行包内生成备份并上传腾讯云 COS：
```powershell
powershell -ExecutionPolicy Bypass -File .\backup-mysql.ps1 -UploadCos -CosPrefix prod/mysql/
```

运行包内验证备份可恢复：
```powershell
powershell -ExecutionPolicy Bypass -File .\verify-restore.ps1 -BackupFile .\runtime-backups\zhubao_erp_mysql_20260707-090000.sql -Operator ops-user
```

## 当前 API

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
- `GET /api/hardware/poc-evidence`
- `GET /api/hardware/summary`
- `POST /api/hardware/gateway/readings`
- `GET /api/hardware/gateway/readings`
- `GET /api/hardware/gateway/devices`
- `GET /api/hardware/gateway/devices/{deviceCode}/readings`
- `GET /api/hardware/gateway/summary`
- `POST /api/hardware/gateway/consumptions`
- `GET /api/hardware/gateway/consumptions`
- `POST /api/hardware/gateway/devices`
- `POST /api/hardware/gateway/devices/{deviceCode}/status`
- `POST /api/hardware/gateway/devices/{deviceCode}/rotate-key`
- `GET /api/documents/summary`
- `GET /api/documents/packages`
- `GET /api/documents/attachments`
- `GET /api/documents/audit-events`
- `POST /api/documents/attachments/uploads`
- `GET /api/documents/attachments/{code}/preview-url`
- `GET /api/documents/attachments/{code}/content?expiresAt=<epoch>&token=<hmac>`
- `GET /api/operations/center`
- `POST /api/auth/login`
- `GET /api/auth/session`
- `POST /api/auth/logout`
- `GET /api/auth/demo-users`
- `GET /api/auth/audit-events`
- `GET /api/security/current-context`
- `GET /api/security/menu-tree`
- `GET /api/security/customer-authorizations`
- `POST /api/security/customer-authorizations`
- `POST /api/security/customer-authorizations/revocations`
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
- `GET /api/business/documents/mother-orders/{code}`
- `GET /api/business/documents/outbound-orders/{code}`
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
- `GET /api/register-import/templates`
- `GET /api/register-import/summary`
- `POST /api/register-import/previews`
- `POST /api/register-import/batches/confirm`
- `GET /api/register-import/batches`
- `GET /api/register-import/batches/{importBatchNo}`
- `GET /api/register-import/batches/{importBatchNo}/posting-plan`
- `POST /api/register-import/batches/{importBatchNo}/rows/{sourceLineNo}/corrections`
- `POST /api/register-import/batches/{importBatchNo}/posting-commands`
- `GET /api/machine-outputs`
- `GET /api/machine-outputs/summary`
- `GET /api/machine-outputs/machine-match-suggestions`
- `POST /api/machine-outputs/machine-bindings`
- `GET /api/machine-outputs/import-batches/{importBatchNo}`
- `GET /api/machine-profiles`
- `POST /api/machine-profiles`
- `POST /api/machine-outputs`
- `POST /api/label/generate`
- `GET /api/label/mother-orders/{code}`
- `GET /api/label/templates`
- `POST /api/label/templates`

## 当前状态

一期业务命令已能写库存台账。客户交付版本尚未形成。下一步是收掉只读说明接口和页面里的第二套样例数据，而不是再加模块。

## 文档

- [文档索引](docs/README.md)
- [产品需求](docs/01_PRD_产品需求文档.md)
- [业务流程](docs/02_业务流程与规则设计.md)
- [领域模型](docs/03_领域模型与数据库设计.md)
- [Docker 部署](docs/19_Docker一键部署与离线交付.md)
- [上线待确认](docs/22_真实数据与上线待确认清单.md)
