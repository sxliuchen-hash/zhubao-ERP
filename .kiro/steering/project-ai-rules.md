---
inclusion: always
---

# 珠宝 ERP 项目 AI 开发规则

本文件是本项目所有 AI 助手（Kiro、Codex 等）必须遵守的开发规则，长期有效。

## 语言规范（强制）

- **所有代码注释必须使用中文。** 包括 Java Javadoc、行内注释、TypeScript/Vue 注释、SQL 注释。
- **所有文档必须使用中文。** 包括 `docs/` 下的设计与进度文档、README 的说明性文字、提交说明中的中文摘要。
- 允许保留英文的场景：代码标识符（类名、方法名、变量名、枚举值）、技术术语（如 RBAC、JDBC、HTTP、PBKDF2）、第三方 API 名称、日志中的英文关键字。
- 面向用户/评审的界面文案继续使用中文。
- Git 提交标题可用英文（遵循 conventional commits），但提交正文应包含中文说明要点。

## 多智能体并行协作（推荐）

- **鼓励派出多个子智能体（sub-agent）同时工作以提高效率。** 在遇到互不依赖的任务时优先并行，例如：多文件调研、跨模块代码分析、bug 定位、上下文收集。
- 典型并行场景：
  - 用 `context-gatherer` 在动手改动重度测试覆盖的服务前，先并行摸清调用链路和影响面。
  - 用 `general-task-execution` 并行处理独立的多文件读取或独立子任务。
- **必须串行执行的操作**（禁止并行）：Git 操作（commit / merge / branch）、同一文件的连续编辑、有前后依赖的构建与测试步骤。
- 派子智能体前先判断任务是否真正独立；简单的定向查找直接用搜索工具即可，不必派子智能体。

## 工程规范

- 严格遵循既有 DDD 分层：每个领域模块内保持 `domain / app / infra / web` 四层。
- 持久化遵循"内存默认 + JDBC 可切换"模式：`@ConditionalOnProperty` 控制，本地默认内存，MySQL profile 默认 JDBC。
- 新增可选依赖时采用"重载构造器向后兼容 + 单一 `@Autowired` 全参构造器"范式，避免破坏既有测试。
- 数据库迁移脚本 append-only，按 `V{n}__m3_xxx.sql` 递增命名，禁止修改已发布迁移。
- 每个增量都应：编写测试、通过后端与前端全量测试、同步更新 `README.md`、`docs/14_开发进度跟踪.md`、`docs/23_客户交付版本记录.md`，并以语义化提交独立提交。

## 验证基线

- 后端测试命令：`powershell -ExecutionPolicy Bypass -File scripts/maven.ps1 -f backend/pom.xml test`
- 前端测试与构建：`pnpm --filter zhubao-erp-frontend test` 与 `pnpm --filter zhubao-erp-frontend build`
- 交付包结构门禁：`scripts/verify-runtime-package.ps1`
- 改动后必须保证上述基线全绿再提交。
