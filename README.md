# 来料加工进销存与生产管理系统

本仓库已经从文档评审进入 M0 开发验证阶段。M0 目标不是完成完整业务系统，而是建立可长期维护的前后端分离工程基础，并验证关键风险：

- Spring Boot 后端基础工程。
- Vue 3 PWA 前端基础工程。
- 统一 API 响应和异常处理。
- RBAC / 数据范围枚举基线。
- 扫码解析。
- 转移式库存台账领域服务。
- 视频截取任务状态模型。
- SQL 基线脚本。
- 后端单元测试和前端组件/函数测试。

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

M0 一键检查：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/check-m0.ps1
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

## M0 API

- `GET /api/m0/status`
- `POST /api/scan/resolve`
- `POST /api/inventory/ledger/transfers`
- `GET /api/inventory/ledgers`
- `GET /api/inventory/balances`
- `POST /api/video-clip-tasks`
- `GET /api/video-clip-tasks`

## 下一阶段

M0 通过后，再进入 M1 实施基线冻结和 M3 一期正式开发。进入 M3 前需要补充：

- MySQL DDL 完整版和迁移策略。
- API 字段字典。
- RuoYi-Vue 具体分支和许可证确认。
- NVR、标签打印、PWA 扫码、备份恢复的现场 POC 记录。
