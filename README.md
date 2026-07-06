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
- `GET /api/hardware/summary`
- `GET /api/business/workspaces/summary`
- `GET /api/business/workspaces/inbound`
- `GET /api/business/workspaces/production`
- `GET /api/business/workspaces/quality`
- `GET /api/business/workspaces/inventory`
- `GET /api/business/workspaces/mobile`

## 下一阶段

M1 通过后，进入 M2 文档基线冻结和 M3 一期正式开发。进入 M3 前需要补充：

- MySQL DDL 完整版和迁移策略。
- API 字段字典。
- RuoYi-Vue 具体分支和许可证确认。
- NVR、标签打印、PWA 扫码、备份恢复的现场 POC 记录。
- 将 M2 工作台样例 API 迁移为 MySQL seed 数据和真实业务聚合。

## 开发管理文档

- [M1 开发验证报告](docs/12_M1开发验证报告.md)
- [开发原则与版本管理规范](docs/13_开发原则与版本管理规范.md)
- [开发进度跟踪](docs/14_开发进度跟踪.md)
- [样例与软硬件联动管理](docs/15_样例与软硬件联动管理.md)
- [字段字典与 API 契约](docs/16_字段字典与API契约.md)
