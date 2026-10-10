# 来料加工进销存与生产管理系统

面向客户来料加工，围绕母单、工单和库存台账管理收货、生产、质检、出库、退货与证据追溯。财务提供加工费、收付款、工资和成本的经营核算，不提供会计总账或银行代发。

当前仍处于内部开发与验证，尚未形成正式客户交付版本。功能状态见 [开发进度](docs/14_开发进度跟踪.md)，上线条件见 [上线待确认清单](docs/22_真实数据与上线待确认清单.md)。

## 文档入口

| 用途 | 文档 |
|---|---|
| 产品范围与业务规则 | [产品需求](docs/01_PRD_产品需求文档.md)、[业务流程](docs/02_业务流程与规则设计.md) |
| 当前技术、持久化与工程约定 | [系统架构](docs/05_系统架构设计.md)、[AGENTS](AGENTS.md) |
| 当前接口与字段 | [API 契约](docs/16_字段字典与API契约.md) |
| 安装、手机 HTTPS、备份与恢复 | [部署手册](docs/19_Docker一键部署与离线交付.md) |
| UI 设计与下一任务 | [UI 设计](docs/26_ERP整体UI与多端设计规划.md)、[UI 任务入口](docs/27_UI开发执行提示词.md) |
| 客户版本与历史来源 | [交付记录](docs/23_客户交付版本记录.md)、[归档索引](docs/archive/README.md) |

完整分类见 [文档索引](docs/README.md)。现行文档维护当前事实；历史需求、方案、验证与已完成任务归档后停止逐轮同步。

## 目录与环境

```text
backend/   Spring Boot 3 / Java 17 后端
frontend/  Vue 3 / Vite / PWA 前端
deploy/    Docker 配置、启动、备份与恢复
scripts/   开发、构建和验证入口
docs/      现行文档与分类归档
```

需要 Java 17 或兼容 JDK、Node.js 18+、pnpm 9+；npx 工具需要 npm。scripts/maven.ps1 会准备项目本地 Maven。

## 本地开发

```powershell
powershell -ExecutionPolicy Bypass -File scripts/dev-up.ps1
```

也可双击 dev-up.cmd。默认后端 http://localhost:8080、前端 http://localhost:5173，Vite 将 /api 代理到后端。启动脚本支持 -BackendOnly、-FrontendOnly、-SameWindow、-NoBrowser。

分别启动或首次安装依赖：

```powershell
pnpm install --store-dir .\.pnpm-store
powershell -ExecutionPolicy Bypass -File scripts/maven.ps1 -f backend/pom.xml spring-boot:run
pnpm --filter zhubao-erp-frontend dev
```

本地默认内存仓储。启用 MySQL 时设置 SPRING_PROFILES_ACTIVE=mysql 和 ZHUBAO_DB_URL、ZHUBAO_DB_USERNAME、ZHUBAO_DB_PASSWORD。各模块默认值与 Docker 覆盖见 [运行配置](docs/05_系统架构设计.md#运行配置与持久化)；仅开启 MySQL profile 不代表所有模块都已持久化。

前端默认同源访问；直连其他后端时设置 VITE_API_BASE_URL。工作台默认读取正式单据与台账，空库显示空列表；演示快照须显式开启，详见[数据来源](docs/05_系统架构设计.md#数据流与一致性)。附件与财务使用真实输入，账号操作通过申请、批准、执行完成。

## 验证

```powershell
powershell -ExecutionPolicy Bypass -File scripts/maven.ps1 -f backend/pom.xml test
pnpm --filter zhubao-erp-frontend test
pnpm --filter zhubao-erp-frontend build
```

统一检查可运行 `powershell -ExecutionPolicy Bypass -File scripts/check.ps1`，旧 check-m0/check-m1 命令转发到同一入口，任一步失败即停止。脚本维护回归见 `scripts/test-maintenance.ps1`。提交门槛以 AGENTS 为准。页面检查可使用 node scripts/check-frontend-ui.cjs http://localhost:5173/，需本地服务与 Playwright；output 截图不替代业务验收。

## Docker 交付

```powershell
powershell -ExecutionPolicy Bypass -File scripts/docker-build-package.ps1 -Version <版本号>
powershell -ExecutionPolicy Bypass -File scripts/verify-runtime-package.ps1 -PackagePath <运行包目录或zip>
```

运行包采用 MySQL、后端和 Nginx 前端。安装、配置、升级限制、备份与非破坏性恢复演练统一按 [部署手册](docs/19_Docker一键部署与离线交付.md) 执行。

手机摄像头需访问 HTTPS。试运行的 ZHUBAO_TLS_SAN 填实际访问 IP 或域名；网页请求上限 55MB，单个视频仍不超过 50MB。生产需核验账号、证书信任、附件备份和现场设备。
