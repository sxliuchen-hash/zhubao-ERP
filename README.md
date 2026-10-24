# 来料加工生产管理系统

当前保留已在前端展示的功能及其必要依赖，围绕收货、母单、生产、质检、发货和库存证据追溯收敛。尚未形成正式客户交付版本。

**开发默认只读 [AGENTS](AGENTS.md) 和 [项目核心](docs/00_项目核心.md)，再按任务查代码或专题，不通读历史文档。**

| 需要了解 | 入口 |
|---|---|
| 当前功能、架构、代码位置、阻断与下一步 | [项目核心](docs/00_项目核心.md) |
| 业务规则 | [业务专题](docs/02_业务流程与规则设计.md) |
| 接口、字段与权限 | [API 专题](docs/16_字段字典与API契约.md) |
| 安装、HTTPS、升级、备份与恢复 | [部署专题](docs/19_Docker一键部署与离线交付.md) |
| 腾讯云测试服务器更新与回滚 | [测试服务器更新操作手册](docs/腾讯云测试服务器更新操作手册.md) |
| 厂内标签打印：建工位、改配置、启动接收程序 | [操作教程](docs/厂内标签打印操作教程.md) |
| 安卓容器与集中标签打印设计及验收要求 | [开发文档](docs/安卓APP与集中打印开发文档.md)，完成状态以核心为准 |
| 按任务查现行专题 | [文档入口](docs/README.md) |
| 历史需求、方案和验证记录 | [文档归档](docs/archive/README.md) |
| 已退出运行源码的原型 | [代码归档](archive/README.md) |

## 本地启动

需要 Java 17 或兼容 JDK、Node.js 18+、pnpm 9+。

```powershell
powershell -ExecutionPolicy Bypass -File scripts/dev-up.ps1
```

也可双击 `dev-up.cmd`。默认后端 `http://localhost:8080`、前端 `http://localhost:5173`，前端将 `/api` 代理到后端。脚本支持 `-BackendOnly`、`-FrontendOnly`、`-SameWindow`、`-NoBrowser`。

首次安装依赖或分别启动：

```powershell
pnpm install --store-dir .\.pnpm-store
powershell -ExecutionPolicy Bypass -File scripts/maven.ps1 -f backend/pom.xml spring-boot:run
pnpm --filter zhubao-erp-frontend dev
```

本地默认内存仓储，重启不保证保留数据。MySQL 设置 `SPRING_PROFILES_ACTIVE=mysql` 和 `ZHUBAO_DB_URL`、`ZHUBAO_DB_USERNAME`、`ZHUBAO_DB_PASSWORD`；各模块实际开关及已知安装阻断见项目核心与部署专题。手机摄像头需要 HTTPS。

## 验证与交付

按 AGENTS 执行最小充分验证；普通开发不重复全量测试或制作运行包。实际交付才按部署专题构建、检查和安装。历史测试通过、页面截图及无镜像结构包均不代表已经上线。

默认 `rg` 搜索排除 `docs/archive/`、`archive/`；追查历史时显式使用 `rg --no-ignore <关键字> docs/archive` 或 `rg --no-ignore <关键字> archive/code`。归档内容仍由 Git 保存，但不属于当前开发上下文或运行源码。
