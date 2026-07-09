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
- M3.50 样例映射与真实验收数据基线，将 ERPNext、Odoo、InvenTree 的公开业务对象参考映射到前端样例库，并把 `REAL-FLOW-20260706-001` fixture、自动化测试、证据编码和当前缺口集中展示；新增后端 fixture 合同测试，避免真实链路样例和自动化测试脱节。
- M3.51 样例库管理台重构，针对“样例库页面内容薄、像简单卡片”的反馈，将 `SA 样例库` 升级为验收数据指挥台，集中展示样例矩阵、当前主样例、开源调研命中、fixture/自动化测试门禁、真实数据集和现场数据缺口队列。
- M3.52 权限上下文与业务操作台整改，新增 `GET /api/security/current-context` 展示请求头身份、角色、数据范围、敏感动作权限和当前限制；配置基线页展示“当前权限 / 数据范围边界”。前端外壳继续收紧为高密度 ERP/MES 操作台，来料母单主表支持选中联动详情和处理链路，减少评审大屏感。
- M3.53 核心作业联动整改，生产、质检、库存追溯页主表支持选中联动右侧焦点对象，补齐“扫码进站/工序报工/称重交接/送检入账”“冻结/返工/放行/损耗审批”“客户权属/批次余额/台账明细/证据归档/售后复核”动作链，并把旧评审展示区从浏览器渲染中收起。
- M3.54 UI 视觉纠偏，针对“页面还是像简单样例、UI 太丑”的反馈，在不新增后端接口的前提下将全局外壳、侧栏、页头、首页、主表、详情侧栏和状态色统一为浅色高密度 ERP/MES 作业台，并保留当前真实边界提示。
- M3.55 腾讯云 COS 备份可见性，运维中心新增 `COS_BACKUP` 服务状态、`COS-BACKUP-*` 备份状态和 `OPS-CHK-007` 运维清单项，通过 `ZHUBAO_COS_BACKUP_*` 展示 Bucket、Region、Prefix、凭证模式和生命周期/保留策略 readiness，不保存密钥、不从页面执行上传。
- M3.56 现场登记表字段补齐，基于入库、出货、抛光、雕刻 4 张登记表新增差异分析文档，补齐入库签收、出货登记、抛光日记录、雕刻机器产量字段，并把 `inboundDate`、`workDate`、`operatorSignatureName` 写入真实业务命令，把 `雕刻`、`抛光日报与返工` 写入配置基线和评审 API 样例。
- M3.57 无源码运行包验收门禁，新增 `scripts/verify-runtime-package.ps1`，打包后自动验证目录和 zip：必需文件、`MANIFEST.sha256` 覆盖、zip `.sha256`、禁止源码/开发缓存、禁止 Compose build 上下文；`.deploy-artifacts/` 作为构建输出不进入 Git。
- M3.58 数据库备份脚本基线，新增 `deploy/backup-mysql.ps1` 和 `deploy/backup-mysql.sh`，运行包内可直接生成 MySQL dump、SHA-256 校验文件、导出到 `runtime-backups/`，并可显式调用 `coscmd upload` 上传到腾讯云 COS。
- M3.59 非破坏性恢复演练脚本基线，新增 `deploy/verify-restore.ps1` 和 `deploy/verify-restore.sh`，可把备份导入临时数据库、检查恢复表数量并默认删除临时库，不覆盖生产数据库。
- M3.60 运维中心恢复演练可见性，`/api/operations/center` 新增 `RESTORE-DRILL-*` 备份状态和 `OPS-CHK-008` 清单项，只读扫描备份目录中的 restore/verify-restore 标记文件，展示最近演练时间、文件大小、SHA-256 和过期状态。
- M3.61 恢复演练结构化证据，`verify-restore.ps1/sh` 成功后同时生成 `verify-restore-<timestamp>.json`，记录备份文件、备份 SHA-256、恢复库、恢复表数量、操作者、耗时和检查项；运维中心新增 `restoreDrillRecords` 只读展示结构化演练记录。
- M3.62 运维告警汇总，`/api/operations/center` 新增 `alerts` 和 summary 告警数量，按服务状态、备份/恢复演练、COS 配置和日志 WARN/ERROR 派生只读告警；前端运维中心新增告警汇总面板。
- M3.63 轻量登录会话基线，新增 `/api/auth/login`、`/session`、`/logout` 和 `/demo-users`，前端 API client 自动携带 Bearer token；配置基线页可登录演示账号并刷新权限上下文，请求头兜底仍保留用于评审和自动化测试。
- M3.64 现场登记来源审计补齐，入库、生产报工和出货命令新增 `sourceRegisterType`、`sourceRegisterLineNo`、`sourceRegisterSequence`；出货命令新增 `paperExpressText` 保留纸质快递栏原文，台账备注和字段文档同步更新。
- M3.65 登记表导入模板基线，新增 `/api/register-import/templates` 和 `/summary`，把入库、出货、抛光、雕刻 4 类现场登记表固化为字段映射、命令映射和导入校验规则；样例库新增“现场登记表导入准备度”视图。
- M3.66 登记表 Excel 上传预览，新增 `/api/register-import/previews`，通过 Apache POI 读取入库、出货、抛光、雕刻 4 类 `.xlsx`，标准化现场字段、继承抛光表头/雕刻合并单元格上下文、拆分纸质快递栏，并输出 READY/WARNING/BLOCKED 预览行；本阶段仍不直接写业务台账。
- M3.67 登记表预检操作台，样例库新增模板选择、业务日期、Excel 文件选择、上传预检、预览批次统计、阻断消息和标准化字段表；前端已接 `/api/register-import/previews`，继续保持“预检不入账”的安全边界。
- M3.68 真实登记表解析规则补齐，按 2026-07-07 的 4 张现场 Excel 复核空白占位行、抛光表头日期、雕刻 1-8 号机器组内继承和工作表来源，后端预检输出 `sourceSheetName/machineLineNo`，前端可下载阻断/警告行 CSV 供现场修表；确认导入仍未开放。
- M3.69 真实数据与上线待确认清单，新增上线前 P0 阻断项、真实主数据/业务单据/附件采集、损耗和返工规则、部署运维、软硬件 POC、验收门禁和会议输出要求，避免生产规则继续停留在口头假设。
- M3.70 真实登记表结构复核，重新解析 4 张 Excel 空白模板：入库/出货的 `月 日` 占位行继续跳过，抛光 6 个重复日报块支持无纸质序号但有明细内容的行进入预检，雕刻 6 组 x 8 台机器按本序号组上下文继承，前后端模板规则同步到 IN/OUT/POL/ENG 003。
- M3.71 参考登记表字段二次核对，对照入库、出货、抛光、雕刻 4 张参考表确认一期工序已覆盖这些现场动作，并补齐 `入库登记表.xlsx` 的 `备注 -> remark` 字段模板契约，登记表字段总数调整为 40。
- M3.72 上线待确认门禁可视化，将 `docs/22_真实数据与上线待确认清单.md` 的 P0/P1 确认项落到 `SA 样例库`，集中展示损耗计算口径、客户所有权、真实主数据、备份恢复、账号权限和部署形态的负责人、默认处理和验收证据。
- M3.73 登记表覆盖审计，重新对照 `入库登记表.xlsx`、`出货登记表.xlsx`、`抛光登记表.xlsx`、`雕刻登记表.xlsx`，确认没有新增生产工序缺口；补齐 4 类模板的 `sourceRegisterType/sourceRegisterLineNo` 审计字段，字段总数 40 -> 48，并在 `SA 样例库` 增加工序与字段覆盖矩阵。
- M3.74 登记表确认批次，新增 `/api/register-import/batches/confirm`、批次查询接口、内存/JDBC 双仓储和 `register_import_batch/register_import_row` MySQL 迁移；前端 `SA 样例库` 在预检通过后可确认结构化导入批次，仍不直接写库存台账或业务单据。
- M3.75 参考登记表字段命名复核，重新读取微信来源的入库、出货、抛光、雕刻 4 张 Excel，确认没有新增工序和新增业务字段；校准入库表 `寄货人/姓名`、`收货人/姓名` 与雕刻 `机器编号行号` 模板契约，并补覆盖测试防止后续漏列。
- M3.76 登记表确认批次复核台，`SA 样例库` 新增最近确认批次、批次详情、行级标准化字段、警告消息和“待入账映射”状态展示；前端可读取 `/api/register-import/batches` 和 `/batches/{importBatchNo}`，后端不可用时保留本地评审样例兜底，仍不直接写库存台账或业务单据。
- M3.77 登记表入账映射计划，新增 `/api/register-import/batches/{importBatchNo}/posting-plan`，对已确认批次只读生成目标命令草稿、已映射字段、缺失字段和警告；前端 `SA 样例库` 展示后端映射计划，仍不生成业务单据或写库存台账。
- M3.78 登记表显式入账命令，新增 `POST /api/register-import/batches/{importBatchNo}/posting-commands`，将通过映射计划的批次行转换为 `ConfirmInboundCommand`、`ReportProductionCommand` 或 `ShipFinishedStockCommand` 并复用后端业务服务写库存台账；阻断行拒绝、警告行需 `allowWarnings`、已入账批次拒绝重复执行。
- M3.79 雕刻设备产量独立落库，新增 `biz_machine_output_record` 迁移、内存/JDBC 仓储、`/api/machine-outputs` 提交与查询接口；`REG-ENGRAVING` 显式入账在写库存台账后同步生成设备产量记录，保留机器编号、机器行号、操机签字、批次号和台账号。
- M3.80 设备产量生产统计入口，新增 `GET /api/machine-outputs/summary`，按最近设备产量记录汇总总产量、按机器和按日期分组，并在生产派工报工作业台展示机器档案待匹配和签名附件待补数量。
- M3.81 真实母单/出库单结构化持久化切片，新增 `biz_mother_order`、`biz_mother_order_item`、`biz_outbound_order`、`biz_outbound_order_item` 迁移、内存/JDBC 仓储和只读查询接口；来料确认入库和成品出库仍先经 `StockLedgerService` 写库存台账，成功后保存结构化单据快照并拒绝重复单号；前端 `SA 样例库` 已在显式入账后拉取并展示结构化母单/出库单快照。
- M3.82 登记表复核补录入口，新增 `POST /api/register-import/batches/{importBatchNo}/rows/{sourceLineNo}/corrections` 和 `REGISTER_IMPORT_REVIEW` 权限，允许在确认批次行上补齐母单、工单、客户、物料、重量等入账映射字段；该动作只更新行级标准化字段并刷新 posting plan，不写库存台账或业务单据。
- M3.83 权限菜单树基线，新增后端 `NavigationMenuService`、`NavigationMenuItem` 和 `GET /api/security/menu-tree`，登录会话与当前权限上下文返回角色可见菜单树、菜单编码和按钮权限编码；前端配置基线页展示“授权菜单树”，用于对齐后续生产菜单表、按钮权限和导航过滤。
- M3.84 登录会话审计基线，新增 `AuthAuditEvent` 和 `GET /api/auth/audit-events`，记录登录成功、登录失败、会话读取、退出和无效 token 解析；审计事件只保留 token 尾号，不暴露完整 token 或密码，前端配置基线页展示最近登录审计。
- M3.85 登录审计持久化边界，新增 `AuthAuditRepository`、内存/JDBC 仓储、`ZHUBAO_AUTH_AUDIT_REPOSITORY` 配置开关和 `V15__m3_auth_audit_contract.sql`；MySQL profile 默认写入 `auth_audit_event`，前端接口不变。
- M3.86 授权导航过滤基线，前端 `App` 会恢复当前登录会话并按后端返回的 `menuCodes/menuTree` 过滤侧栏模块；`authApi` 登录/退出会广播会话变化，配置页登录后导航即时收敛，未登录或 API 不可用时仍保留全量评审导航兜底。
- M3.87 生产账号表与密码哈希边界，新增 `AuthUserAccountRepository`、内存/JDBC 账号仓储、`PasswordHasher`、`ZHUBAO_AUTH_USERS_REPOSITORY` 配置开关和 `V16__m3_auth_user_account_contract.sql`；登录流程改为从账号仓储读取 PBKDF2 密码哈希，Docker/MySQL 默认启用 JDBC 账号表。
- M3.88 登录失败锁定边界，账号仓储新增失败次数、锁定到期、最近登录和最近失败字段；登录失败 5 次后锁定 15 分钟，成功登录会清零失败计数，并通过 `V17__m3_auth_user_login_lockout_contract.sql` 固化 MySQL 合同。
- M3.89 账号管理与管理员解锁边界，新增 `GET/POST /api/auth/accounts`、`POST /api/auth/accounts/{username}/status` 和 `/unlock`；`AUTH_ACCOUNT_MANAGE` 仅允许 `ADMIN`、`GENERAL_MANAGER`，账号管理接口要求 Bearer 会话或显式评审角色头，不走匿名默认管理员兜底；支持生产账号创建/更新、保留或重置密码、启停、清锁和 `ACCOUNT_MANAGEMENT` 审计事件，前端配置基线页展示只读账号管理边界。
- M3.90 生产账号密码策略边界，新增 `PasswordPolicy`，账号管理创建/重置密码时要求至少 12 位、同时包含字母和数字、无首尾空白、不得等于账号名且不得使用常见弱口令；默认评审 seed 账号保留为评审用途，不作为生产密码策略豁免依据。
- M3.91 密码过期/续期边界，新增 `PasswordExpiryPolicy`，生产账号密码 90 天过期，登录过期账号会拒绝并写入 `LOGIN DENIED` 审计；管理员创建或重置密码会刷新 `passwordChangedAt`，登录会话和账号摘要返回改密时间、到期时间和过期状态；评审 seed 账号仅作为评审用途保留豁免，不作为生产上线豁免。
- M3.92 登录态自助改密/续期边界，新增 `POST /api/auth/password`，只接受 `Authorization: Bearer <token>` 登录会话，不使用请求头角色兜底；校验当前密码、新密码策略和新旧密码不同，成功后刷新 `passwordChangedAt/passwordExpiresAt/passwordExpired`、清零失败锁定、更新当前内存会话账号摘要并写入 `PASSWORD_CHANGE` 审计。该边界不代表生产账号前置审批、SSO/JWT、生产菜单持久化、GET 数据范围过滤、审计防篡改或安全日志平台完成。
- M3.93 认证审计完整性摘要边界，认证审计事件新增 `previousEventHash/eventHash`，内存/JDBC 审计仓储写入时生成 SHA-256 摘要链，新增 `GET /api/auth/audit-integrity` 返回最近事件链校验摘要，前端配置基线页展示“完整性摘要”和哈希尾号；该边界可用于发现审计链断裂或事件字段被改动，但仍不代表生产级不可篡改审计、安全日志平台或归档保全完成。
- M3.94 硬件 POC 记录仓储可切换边界，新增 `HardwarePocRecordRepository`、内存/JDBC 仓储、`zhubao.hardware.poc.repository` / `ZHUBAO_HARDWARE_POC_REPOSITORY` 配置和 `V19__m3_hardware_poc_record_seed.sql`；`GET /api/hardware/poc-records` 接口保持不变，本地默认内存，MySQL profile 默认 JDBC。该边界只推进 POC 记录持久化，不代表硬件 SDK 强集成完成。
- M3.95 硬件 POC 证据关联边界，新增 `HardwarePocEvidenceSummary` 和 `GET /api/hardware/poc-evidence`，按 `EvidenceAttachment.businessType=硬件 POC`、`businessCode=pocCode` 将 POC 记录与附件证据做只读聚合；未匹配附件的 POC 返回待补证据状态。该边界不代表附件上传闭环、现场复测留痕或厂商 SDK/协议直连完成。
- M3.96 业务工作台数据范围过滤边界，新增 `BusinessWorkspaceDataScopeFilter`，`/api/business/workspaces/*` GET 会按 Bearer 会话或 `X-Zhubao-*` 请求头解析出的 `dataScope` 做首版过滤；`SELF` 按操作人文本、`TEAM/DEPARTMENT` 按角色职责关键词、`CUSTOMER_RANGE` 按 `X-Zhubao-Customer-Scope` 收敛返回数据。该边界不代表完整组织树、客户授权表或全系统行级权限完成。
- M3.97 生产账号审批摘要边界，账号创建/更新、启停和管理员解锁成功后会生成 `ACCOUNT_MANAGEMENT` 轻量审批任务，可通过 `/api/approvals/tasks` 进入后续复核；该边界是事后审批摘要，不阻断账号动作，也不代表完整 IAM、前置账号审批、SSO/JWT、组织客户授权表或复杂审批流完成。
- M3.98 请求头身份兜底开关，新增 `zhubao.security.allow-header-context` / `ZHUBAO_ALLOW_HEADER_CONTEXT`；本地默认保留 `X-Zhubao-*` 评审兜底，MySQL profile 默认关闭，无 Bearer 登录会话时拒绝解析请求头身份，避免生产误用匿名 `ADMIN/ALL`。
- M3.99 账号客户范围边界，`auth_user_account` 新增 `customer_scope`，账号管理可为 `CUSTOMER_RANGE` 账号保存客户范围；Bearer 登录会话、当前权限上下文和业务工作台 GET 过滤优先使用账号内的 `customerScope`，请求头客户范围仅作为显式评审兜底。该边界不代表完整组织客户授权表、跨对象关系推导或全系统行级权限完成。
- M3.100 权限上下文客户范围可视化，前端全局基线、配置页当前权限面板、样例库上线门禁和运维中心登录权限提示统一展示 M3.99 账号客户范围边界；后端 `current-context` 限制说明同步区分 Bearer 账号 `customerScope` 与请求头评审兜底。该边界仍不代表完整组织客户授权表或生产 IAM 完成。
- M3.101 文档权限口径收敛，配置构型、字段/API 契约和上线待确认清单同步到 M3.100 客户范围可视化口径，继续保留生产账号前置审批、完整 IAM、生产菜单持久化、组织客户授权表、SSO/JWT 和审计保全缺口。
- M3.102 硬件低成本适配计划，新增 `HardwareAdapterPlan` 和 `GET /api/hardware/adapter-plans`，把拆包摄像头、标签打印机、PC 扫码枪、PWA 摄像头和电子秤的一期主适配、失败兜底、证据要求、下一步复测动作和风险等级固化为只读契约；前端软硬件联动作业台新增 `ADAPTER PLANS / 低成本优先` 区域。该边界仍不代表厂商 SDK/协议直连、现场复测留痕或附件归档闭环完成。
- M3.103 硬件协议样本清单，新增 `HardwareProtocolSample` 和 `GET /api/hardware/protocol-samples`，把 NVR/RTSP、标签打印、USB HID、PWA 扫码和电子秤 RS232/USB/TCP 预留的参数、样本载荷、采集方式、证据编码和复测动作固化为只读契约；前端软硬件联动作业台新增 `PROTOCOL SAMPLES / 复测留痕` 区域。该边界仍不代表厂商 SDK/协议直连或现场复测完成。
- M3.104 硬件协议参数复核，新增 `HardwareProtocolReviewItem` 和 `GET /api/hardware/protocol-reviews`，把 NVR、标签打印、PC/PWA 扫码和电子秤的已确认参数、待复核参数、型号冻结状态、责任人、复核窗口、阻断项和下一步动作固化为只读契约；前端软硬件联动作业台新增 `PROTOCOL REVIEW / 参数复核` 区域。该边界仍不执行真实硬件采集或复测结论确认。
- M3.105 硬件复测执行结果，新增 `HardwareProtocolExecutionResult` 和 `GET /api/hardware/protocol-executions`，把 NVR、标签打印、PC/PWA 扫码和电子秤的复测执行状态、执行人、型号冻结状态、通过/未通过参数、证据状态、风险等级和下一步动作固化为只读契约；前端软硬件联动作业台新增 `PROTOCOL EXECUTION / 复测结果` 区域。该边界仍不代表真实硬件采集、现场附件上传闭环、正式签字或厂商协议直连完成。
- M3.106 硬件签字闭环就绪度，新增 `HardwareProtocolSignoffReadiness` 和 `GET /api/hardware/protocol-signoffs`，把 NVR、标签打印、PC/PWA 扫码和电子秤的必备附件、缺失附件、签字角色、签字状态、型号冻结决策和上线门禁固化为只读契约；前端软硬件联动作业台新增 `SIGNOFF READINESS / 签字闭环` 区域。该边界仍不代表真实附件上传、正式电子签字、型号最终冻结或厂商协议直连完成。
- M3.107 账号前置审批就绪度，新增 `AuthAccountPreApprovalReadiness` 和 `GET /api/auth/accounts/pre-approval-readiness`，在 `AUTH_ACCOUNT_MANAGE` 权限下返回创建生产账号、变更角色/数据范围、管理员重置密码、账号启停/解锁的必备材料、缺失能力、审批角色、上线门禁和下一步动作；前端配置基线页新增“前置审批就绪度”区域。该边界仍不改变现有账号动作，不代表阻断式 IAM、正式电子签字、完整组织客户授权表或生产菜单持久化完成。
- M3.108 账号前置审批草稿评估，新增 `AuthAccountPreApprovalDraftCommand`、`AuthAccountPreApprovalDraft` 和 `POST /api/auth/accounts/pre-approval-drafts`，在 `AUTH_ACCOUNT_MANAGE` 权限下对账号动作申请草稿进行已收材料、待补材料、系统缺失能力、阻断状态和下一步动作评估；前端配置基线页新增“前置审批草稿评估”区域。该边界仍不落库、不放行账号动作，不代表真正阻断式审批流或正式电子签字完成。
- M3.109 账号前置审批申请单，新增 `AuthAccountPreApprovalApplication`、内存仓储、`GET/POST /api/auth/accounts/pre-approval-applications`，在 `AUTH_ACCOUNT_MANAGE` 权限下把草稿评估结果保存为可查询申请单，返回申请单状态、待补材料、系统缺失能力和提交时间；前端配置基线页新增“前置审批申请单”区域。该边界仍不阻断账号动作，不代表正式审批流、电子签字或完整 IAM 完成。
- M3.110 账号前置审批申请单 JDBC 持久化，新增 `JdbcAuthAccountPreApprovalApplicationRepository`、`AuthPreApprovalJdbcDataSourceConfiguration` 和 `V21__m3_auth_pre_approval_application_contract.sql`，MySQL profile 默认通过 `ZHUBAO_AUTH_PRE_APPROVAL_REPOSITORY=jdbc` 将申请单写入 `auth_account_pre_approval_application` 合同表；该边界解决申请单持久化和重载，不代表阻断式审批、正式电子签字或完整 IAM 完成。
- M3.111 账号动作申请单校验边界，`ManageAuthUserAccountCommand`、`AuthUserAccountStatusCommand` 和 `AuthUserAccountUnlockCommand` 新增可选 `preApprovalApplicationCode`；显式传入时后端会校验申请单存在、目标账号匹配、动作类型匹配且 `applicationStatus=READY_FOR_BLOCKING_APPROVAL`，并把申请单号写入账号管理审计和事后复核摘要。该边界仍不默认强制所有账号动作携带申请单，不代表正式电子签字、审批流审批通过态或完整 IAM 完成。
- M3.112 账号动作申请单号强制开关，新增 `zhubao.auth.pre-approval.enforce-account-actions` / `ZHUBAO_AUTH_PRE_APPROVAL_ENFORCE_ACCOUNT_ACTIONS`；本地评审默认关闭以兼容自动化用例，MySQL profile 默认开启，账号创建/更新、启停和管理员解锁在开启后必须携带 READY 申请单号。该边界仍不代表审批通过态、正式电子签字、完整 IAM 或 SSO/JWT 完成。
- M3.113 账号前置审批任务交接，新增 `AccountPreApprovalTaskCommand`、`ApprovalService.createAccountPreApprovalTask` 和 `V22__m3_auth_pre_approval_task_handoff.sql`，申请单提交后自动生成 `ACCOUNT_PRE_APPROVAL` 轻量审批任务，并把 `approvalTaskCode` 回写到申请单；前端配置基线页展示申请单绑定的审批任务号。该边界打通审批台交接，不代表账号动作已等待审批通过态、正式电子签字或完整 IAM 完成。
- M3.114 账号动作审批通过态校验，`zhubao.auth.pre-approval.enforce-account-actions=true` 时，账号创建/更新、启停和管理员解锁必须携带 READY 申请单，且申请单绑定的 `ACCOUNT_PRE_APPROVAL` 任务必须为 `APPROVED` 才会放行；本地评审默认关闭时仍兼容旧脚本。该边界补齐生产强制模式下的审批通过态门禁，不代表正式电子签字、完整 IAM、SSO/JWT 或审批后自动放行完成。
- M3.115 账号停用会话吊销，`AuthSessionService.revokeSessionsForUsername` 支持按账号吊销现有内存 Bearer 会话；账号停用成功后会写 `SESSION_REVOKED` 认证审计，并在 `ACCOUNT_MANAGEMENT` 审计和复核摘要中记录 `revokedSessions=N`。该边界补齐轻量登录会话的停用联动，不代表完整 IAM、SSO/JWT、分布式会话或正式电子签字完成。
- M3.116 管理员新建/重置密码强制首次改密，非 demo 账号由管理员提供新密码后会标记 `passwordChangeRequired=true`；临时密码可登录取得 Bearer 会话并调用 `POST /api/auth/password` 自助改密，但 `operatorContext()` 会拒绝该会话执行业务动作；自助改密成功后清除标记并刷新会话/账号摘要。该边界不代表一次性临时密码通道、完整 IAM、SSO/JWT 或正式电子签字完成。
- M3.117 临时密码一次性通道，`auth_user_account` 新增 `temporary_password_consumed` 内部字段；管理员新建/重置非 demo 密码后字段重置为 `false`，首次临时密码登录成功前原子消费为 `true` 并返回 Bearer 会话，后续再用同一临时密码登录会被拒绝且不累计失败锁定，自助改密成功后清除强制改密和消费标记。该边界仍不代表完整 IAM、SSO/JWT、正式电子签字或分布式会话完成。
- M3.118 账号前置审批证据关联，新增 `AuthAccountPreApprovalEvidenceSummary` 和 `GET /api/auth/accounts/pre-approval-evidence`，在 `AUTH_ACCOUNT_MANAGE` 权限下按 `EvidenceAttachment.businessType=账号前置审批`、`businessCode=applicationCode` 将前置审批申请单与本人核验/岗位交接附件做只读聚合；前端配置基线页新增“前置审批证据”区域。该边界只复用已有附件元数据，不代表附件上传闭环、正式电子签字、审批 UI 或完整 IAM 完成。
- M3.119 账号前置审批证据上传入口，新增 `POST /api/auth/accounts/pre-approval-evidence/uploads` multipart 接口，管理员在 `AUTH_ACCOUNT_MANAGE` 权限下上传申请单证据附件时会自动创建/更新 `DOC-<applicationCode>` 证据包，并写入 `businessType=账号前置审批`、`businessCode=<applicationCode>`、`documentCode=DOC-<applicationCode>` 的附件元数据；前端配置基线页“前置审批证据”区域新增申请单选择和附件上传表单。该边界是账号前置审批附件上传闭环第一步，不代表正式电子签字、审批 UI、生产归档策略/签名访问或完整 IAM 完成。
- M3.120 附件预览短期签名令牌，新增 `zhubao.documents.preview.secret` / `ttl-minutes` 配置，`GET /api/documents/attachments/{code}/preview-url` 生成包含 `expiresAt` 与 HMAC token 的 15 分钟预览 URL，`GET /api/documents/attachments/{code}/content` 必须校验 token、附件编码和过期时间后才读取本地对象内容；移除固定 `preview-demo`。该边界补齐本地附件预览权限第一步，不代表 NAS/S3/COS 对象存储切换、版本锁定或正式审计保全完成。
- M3.121 设备产量机器档案匹配建议，新增 `GET /api/machine-outputs/machine-match-suggestions`，按最近设备产量记录的现场机器编号聚合未绑定 `machineId` 的记录，并在已有同编号已绑定记录时返回候选机器档案 ID；生产派工报工作业台新增“机器档案匹配建议”只读清单。该边界只提供人工复核前的匹配建议，不自动改写历史产量记录，不代表完整机器主数据、批量绑定命令、签名附件或设备 SDK 直连完成。
- M3.122 硬件设备网关与读数接入基线，新增 `gateway` 模块、设备密钥鉴权、`DeviceReadingNormalizer` 归一化器、内存/JDBC 双仓储、`V25__m3_hardware_gateway_contract.sql` 和 `POST /api/hardware/gateway/readings`、`GET /api/hardware/gateway/readings`、`GET /api/hardware/gateway/devices`、`GET /api/hardware/gateway/devices/{deviceCode}/readings`、`GET /api/hardware/gateway/summary`。低成本硬件（串口转 HTTP 桥、ESP32、浏览器桥、扫码枪中继）无需厂商 SDK，凭 PBKDF2 哈希设备密钥即可推送称重、扫码、打印回执、视频截取标记和心跳读数；读数经归一化后按 READY/WARNING/BLOCKED 分级并保留原始报文供人工兜底，软硬件联动作业台新增“设备网关读数接入”面板。该边界打通设备到系统的标准化接入通道，不代表厂商 SDK/协议直连、真实设备型号冻结或读数自动写业务台账完成。
- M3.123 设备读数消费闭环，新增 `GATEWAY_READING_CONSUME` 权限、`ReadingConsumption` 可追责桥接记录、内存/JDBC 双仓储、`V26__m3_hardware_gateway_consumption_contract.sql` 和 `POST /api/hardware/gateway/consumptions`、`GET /api/hardware/gateway/consumptions`。操作工/车间组长/质检员/库管员/收货员凭登录会话或评审角色头把 READY/WARNING 读数消费为称重交接、扫码报工、打印确认、视频关联意图，BLOCKED 读数只能忽略(DISMISS)，同一读数拒绝重复消费；消费记录操作人、角色、业务对象和时间，软硬件联动作业台读数表新增消费动作和消费记录表。该边界把设备遥测桥接到可追责的人工业务动作，消费本身不写库存台账，仍需继续把称重/扫码消费联动到真实业务命令。
- M3.124 设备网关限流防滥用，新增 `GatewayRateLimiter` 滑动窗口限流器和 `GatewayRateLimitException`（映射 HTTP 429）。设备读数接入端点凭设备密钥鉴权而非人工 RBAC，因此单独按设备做滚动窗口限流（默认 60 秒内 120 条，可经 `ZHUBAO_GATEWAY_RATE_LIMIT_MAX` / `ZHUBAO_GATEWAY_RATE_LIMIT_WINDOW_SECONDS` 配置），避免桥接程序配置异常或密钥泄漏导致读数洪泛，各设备配额相互独立。该边界补齐设备侧接入的滥用防护，不代表签名防重放、mTLS 或完整设备身份体系完成。
- M3.125 设备网关设备管理，新增 `GATEWAY_DEVICE_MANAGE` 权限（仅 ADMIN/总经理）、`RegisterGatewayDeviceCommand` 和 `POST /api/hardware/gateway/devices`、`POST /api/hardware/gateway/devices/{deviceCode}/status`、`POST /api/hardware/gateway/devices/{deviceCode}/rotate-key`。管理员可注册/更新设备、启停设备、轮换设备密钥（PBKDF2 重新哈希，旧密钥立即失效），设备从只读 seed 推进到可运营；停用设备立即拒绝读数接入，更新时空密钥保留原哈希。前端软硬件联动作业台新增设备注册表单和启停/轮换密钥动作。该边界补齐设备生命周期运营，不代表签名防重放、mTLS 或完整设备身份体系完成。
- M3.126 设备网关 HTTP 集成测试，`M2ManagementApiSmokeTest` 端到端锁定设备密钥推送读数、授权角色消费、操作工被拒设备管理(403)、管理员注册设备的 HTTP 契约。
- M3.135 机器档案主数据表，新增 `MachineProfile`、`MachineProfileRepository`（内存/JDBC 可切换）、`MachineProfileService`、`SaveMachineProfileCommand`、`V30__m3_machine_profile_contract.sql`（`sys_machine`，seed 雕刻 1-8 号机）和 `GET/POST /api/machine-profiles`（新增/更新受 `MACHINE_OUTPUT_SUBMIT` 权限守卫，机器编号唯一）。为 M3.134 设备产量绑定提供真实可选的机器档案来源：`machine_no` 对应设备产量记录 `machineNoSnapshot`，`id` 即绑定命令 `machineId`。前端 `machineOutputApi` 新增 `fetchMachineProfiles`/`saveMachineProfile`。该边界补齐机器主数据维护，不代表设备台账、点检保养或设备 SDK 直连完成。
- M3.134 设备产量机器档案绑定命令，`MachineOutputRepository` 新增 `bindMachineId`（内存/JDBC，仅更新同编号且未绑定的记录），新增 `BindMachineOutputCommand`、`MachineOutputBindingResult`、`MachineOutputService.bindMachine` 和 `POST /api/machine-outputs/machine-bindings`（受 `MACHINE_OUTPUT_SUBMIT` 权限守卫）。承接 M3.121 匹配建议：复核候选机器档案 ID 后可把同编号未绑定设备产量记录批量绑定，不覆盖历史绑定；无未绑定记录时拒绝。前端 `machineOutputApi` 新增 `bindMachineOutput`，生产派工报工作业台机器档案匹配建议区对 `READY_TO_BIND` 项新增"绑定到机器档案"动作。该边界补齐设备产量到机器主数据的人工绑定闭环，不代表自动建档、批量多编号绑定或签名附件闭环完成。
- M3.133 客户授权前端管理界面，配置基线页 `当前权限 / 数据范围边界` 区新增"客户授权管理（CUSTOMER_RANGE）"面板，接入 `customerAuthorizationApi` 的 `GET/POST /api/security/customer-authorizations` 和 `/revocations`，总经理/管理员可在界面手工授权/撤销客户、查看授权清单；后端不可用时保持空列表兜底。至此客户授权形成"账号管理自动同步 + 界面手工维护 → 授权表 → 会话有效范围 → 业务工作台 GET 过滤"端到端可用闭环。该边界不代表完整组织树、跨对象关系推导或全系统行级权限完成。
- M3.132 客户授权表接入账号管理，`AuthUserAccountManagementService` 新增可选 `CustomerAuthorizationService` 依赖（重载构造器向后兼容 + 全参 `@Autowired` 构造器）；管理员创建/更新 `CUSTOMER_RANGE` 账号时，账号客户范围同步写入客户授权表 `sys_customer_authorization`（新增客户授权、撤销不再授权的客户），当数据范围切换为非 `CUSTOMER_RANGE` 时清空该账号授权。授权表已是登录会话客户范围的权威来源（M3.130），本轮打通"账号管理 → 授权表 → 会话有效范围 → 业务工作台 GET 过滤"闭环。该边界不代表完整组织树、跨对象关系推导或全系统行级权限完成。
- M3.130 客户授权关系表持久化，新增 `CustomerAuthorization`、`CustomerAuthorizationRepository`（内存/JDBC 可切换）、`CustomerAuthorizationService`、`CUSTOMER_AUTHORIZATION_MANAGE` 权限（仅 ADMIN/总经理）、`V29__m3_customer_authorization_contract.sql`（`sys_customer_authorization`）和 `GET/POST /api/security/customer-authorizations`、`POST /api/security/customer-authorizations/revocations`。`CUSTOMER_RANGE` 账号的可见客户范围改为来自可维护的授权表（授权表有记录时为准，否则回退账号 `customerScope` 文本字段）；`AuthSessionService` Bearer 会话解析时按授权表计算有效客户范围，业务工作台 GET 过滤据此收敛。前端新增 `customerAuthorizationApi` 服务。该边界把 `CUSTOMER_RANGE` 从账号文本字段升级为可维护授权关系表，不代表完整组织树、跨对象关系推导或全系统行级权限完成。
- M3.129 权限菜单树持久化，将原先硬编码在 `NavigationMenuService` 的菜单树和角色可见性抽取为 `NavigationMenuRepository`（内存默认 + JDBC 可选），新增 `V28__m3_navigation_menu_contract.sql`（`sys_navigation_menu` / `sys_role_menu` 两表并 seed 评审默认）。`zhubao.security.menu.repository` 本地默认 `memory`、MySQL profile 默认 `jdbc`（环境变量 `ZHUBAO_MENU_REPOSITORY`），菜单标签、路由、按钮权限编码和角色可见性可按部署自定义而无需改代码。`GET /api/security/menu-tree`、登录会话和权限上下文的菜单契约保持不变。该边界解决生产菜单持久化，是走向客户交付准入的关键项；仍不代表菜单在线可视化编辑 UI 或完整组织客户授权表完成。
- M3.128 设备网关读数防重放，新增 `GatewayReplayGuard`（nonce + 时间戳窗口，按设备隔离）和 `GatewayReplayException`（映射 HTTP 409）。`SubmitDeviceReadingCommand` 新增可选 `nonce`；开启 `zhubao.gateway.replay.enabled`（本地默认关闭，MySQL profile 默认开启，环境变量 `ZHUBAO_GATEWAY_REPLAY_ENABLED`）后，读数必须携带 nonce 和可解析时间戳（epoch 毫秒/ISO-8601/本地时间），时间戳超出 ±300 秒窗口或 nonce 在窗口内重复即拒绝，nonce 仅按窗口保留以限制内存。前端推送读数时自动带 ISO 时间戳和随机 nonce。该边界防止被抓包的有效请求被重放，不代表 mTLS、可验证签名或完整设备身份体系完成（设备密钥以 PBKDF2 哈希存储，无法在服务端做经典 HMAC 验签）。
- M3.127 设备读数联动业务台账闭环，`ConsumeReadingCommand` 新增可选 `posting*` 字段（客户、母单、工单、物料、源/目标仓、数量、工序），`ReadingConsumption` 新增 `ledgerCode` 和 `V27__m3_hardware_gateway_consumption_ledger.sql`。当称重交接/扫码报工消费带齐业务字段时，`DeviceGatewayService` 调用 `BusinessOperationService.reportProduction` 真正写库存台账（WIP → 半成品）并回写台账号：`SCALE_WEIGHT` 用归一化后的真实重量，`SCAN_EVENT` 用扫码工单号；字段不全时退化为纯留痕（M3.123 行为），保持半自动、可回退。前端软硬件联动作业台读数消费区新增“联动报工”开关和业务字段表单，消费记录表展示台账号。该边界打通设备到库存台账的最后一段，硬件称重/扫码可自动生成报工；不代表全自动主数据匹配、称重误差校验或工资锁账完成。

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

前端 UI 冒烟检查会读取首页、来料母单和配置基线页，生成 `output/playwright/m352-*.png` 截图和 `m352-ui-check.json` 指标。首次运行如本地没有 Playwright 包，可先执行一次 `npx --yes --package playwright playwright --version` 让 npm 缓存包落地：

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

未设置 `VITE_API_BASE_URL` 且未设置 `VITE_ENABLE_API=true` 时，前端会直接使用本地评审样例兜底，不再主动请求 `/api/*`，避免前端单独运行时产生大量 404 控制台噪音。需要走 Vite 同源代理到 `localhost:8080` 时，可设置：

```powershell
$env:VITE_ENABLE_API="true"
pnpm --filter zhubao-erp-frontend dev
```

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
- `GET /api/hardware/adapter-plans`
- `GET /api/hardware/protocol-samples`
- `GET /api/hardware/protocol-reviews`
- `GET /api/hardware/protocol-executions`
- `GET /api/hardware/protocol-signoffs`
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

## 当前阶段与下一步

当前已进入 M3 一期开发推进中，前端继续向生产现场作业台和高密度 ERP/MES 操作界面收敛；样例库已升级为验收数据指挥台，把开源参考、前端样例、后端 fixture、自动化测试、证据编码、上线门禁、真实数据缺口和登记表导入准备度收敛到同一张管理矩阵。来料、生产、质检、库存追溯主表已具备选中联动详情和处理链路；来料母单已补入现场签收入库字段，生产工单已补入抛光日报和雕刻设备统计字段，配置基线已纳入雕刻与抛光日报模板。

截至 M3.121，现场登记表链路已完成入库、出货、抛光、雕刻 4 类 Excel 上传预检、问题 CSV、真实模板结构复核、结构化导入批次确认、参考表字段命名复核、确认批次复核台、只读入账映射计划、复核补录入口、显式入账命令、雕刻设备产量独立落库、生产页设备产量汇总入口、机器档案匹配建议入口，以及来料母单/出库单的基础结构化持久化切片。系统已能把通过预检的登记表保存为 `register_import_batch/register_import_row` 批次和行级 JSON，前端可展示最近确认批次、行级标准化字段、警告消息、posting plan 的命令草稿、缺失字段和警告，并可对后端 posting plan 中的阻断字段进行行级补录；补录只更新标准化字段，不写库存台账或业务单据。非阻断行必须通过独立 POST 命令交给后端业务服务入账；`REG-ENGRAVING` 会额外生成 `biz_machine_output_record`，生产页可通过 `GET /api/machine-outputs/summary` 查看最近设备产量的总量、机器分组、日期分组、机器档案待匹配和签名附件待补数量，并可通过 `GET /api/machine-outputs/machine-match-suggestions` 查看按现场机器编号聚合的未绑定记录、候选机器档案 ID 和人工复核动作；`REG-INBOUND` / `REG-OUTBOUND` 显式入账复用入库/出库业务动作，在库存台账成功后保存 `biz_mother_order` / `biz_outbound_order` 快照，`SA 样例库` 会继续调用业务单据查询接口展示快照字段、状态、来源登记表行和台账号。库存变化仍统一经过 `StockLedgerService`，重复入账会被 `POSTED` 状态拒绝。权限侧已补后端授权菜单树、按钮权限编码、登录会话审计、登录审计 JDBC 持久化边界、前端登录会话导航过滤、`auth_user_account` 账号表、PBKDF2 密码哈希、登录失败锁定、账号启停、管理员解锁、密码策略、密码过期检测、管理员新建/重置非 demo 密码后强制首次登录改密、临时密码一次性消费、登录态自助改密/续期、认证审计摘要链、业务工作台 GET 首版数据范围过滤、账号管理审批摘要、请求头身份兜底开关、账号客户范围边界、客户范围可视化、账号前置审批就绪度只读契约、账号前置审批草稿评估契约、前置审批申请单 JDBC 持久化、账号动作申请单号校验边界、生产配置强制申请单号开关、申请单审批任务交接、生产强制模式下审批通过态校验、账号停用会话吊销和前置审批证据上传入口；文档附件短期预览签名令牌；登录会话、当前权限上下文、`GET /api/security/menu-tree`、`GET /api/auth/audit-events`、`GET /api/auth/audit-integrity`、`GET /api/auth/accounts`、`GET /api/auth/accounts/pre-approval-readiness`、`POST /api/auth/accounts/pre-approval-drafts`、`GET/POST /api/auth/accounts/pre-approval-applications`、`GET /api/auth/accounts/pre-approval-evidence`、`POST /api/auth/accounts/pre-approval-evidence/uploads` 和 `GET /api/approvals/tasks` 可返回角色可见菜单、最近登录审计、审计链完整性摘要、账号状态摘要、账号客户范围、改密时间、到期时间、过期状态、强制改密状态、账号前置审批缺口、申请草稿待补材料、前置审批申请单、申请单审批任务号、前置审批证据关联状态、上传结果和账号变更复核任务，MySQL profile 下申请单可写入 `auth_account_pre_approval_application` 并回写 `approval_task_code`，且默认要求账号管理、启停和解锁请求携带 `preApprovalApplicationCode` 并校验申请单目标账号、动作类型、READY 状态和绑定审批任务 APPROVED 状态，停用账号会吊销现有 Bearer 会话并写 `SESSION_REVOKED`，首次临时密码登录会原子消费 `temporary_password_consumed` 并保留该 Bearer 会话用于自助改密，重复使用同一临时密码登录会被拒绝且不累计失败锁定，`POST /api/auth/password` 可在已登录 Bearer 会话内校验当前密码、刷新改密时间并清除强制改密与消费标记。软硬件侧已将硬件 POC 记录从服务内样例推进到内存/JDBC 仓储可切换，MySQL profile 默认读取 `hw_poc_record`，新增 `GET /api/hardware/poc-evidence` 只读聚合 POC 记录与附件证据，M3.102 新增 `GET /api/hardware/adapter-plans` 返回五类低成本适配计划，M3.103 新增 `GET /api/hardware/protocol-samples` 返回协议参数和样本载荷，M3.104 新增 `GET /api/hardware/protocol-reviews` 返回已确认参数、待复核参数、型号冻结状态、责任人、复核窗口和阻断项，M3.105 新增 `GET /api/hardware/protocol-executions` 返回复测执行状态、执行人、证据状态、通过/未通过参数、风险等级和下一步动作，M3.106 新增 `GET /api/hardware/protocol-signoffs` 返回必备附件、缺失附件、签字角色、签字状态、型号冻结决策和上线门禁；硬件边界仍不代表真实附件上传、正式电子签字、真实设备型号最终冻结、硬件 SDK 强集成或厂商协议直连完成。账号侧 M3.119 申请单已可持久化、重载、被账号动作校验、通过生产配置默认强制携带申请单号，并在提交后生成 `ACCOUNT_PRE_APPROVAL` 轻量审批任务；生产强制模式会要求绑定审批任务为 `APPROVED` 后才放行账号动作，停用账号会吊销现有 Bearer 会话并写 `SESSION_REVOKED`，管理员新建/重置非 demo 密码后会要求首次登录先自助改密，临时密码本身只允许首次成功登录一次，前置审批申请单可按 `businessType=账号前置审批` 与 `businessCode=申请单号` 关联附件元数据，并可通过专用上传入口自动创建/更新 `DOC-申请单号` 证据包。正式机器主数据自动绑定、签名附件、电子签字、生产菜单持久化、真正不可篡改审计、SSO/JWT、安全日志平台、完整组织客户授权表和现场附件归档闭环仍未完成。下一步重点不再是继续堆演示页，而是补齐机器档案绑定命令、签名附件、完整母单/出库生命周期、完整组织客户授权表、NAS/S3 签名访问、软硬件原始证据补传、硬件复测真实附件和电子签字闭环、备份计划任务、恢复演练记录结构化入库/告警和生产运维接入：

- MySQL DDL 完整版和迁移策略。
- API 字段字典。
- RuoYi-Vue 具体分支和许可证确认。
- NVR、标签打印、PC/PWA 扫码、电子秤和备份恢复的现场附件上传/归档闭环、正式电子签字、协议样本复核和真实设备参数最终冻结。
- 将 M2 工作台样例 API 迁移为 MySQL seed 数据和真实业务聚合。
- 将 M3.81/M3.82 已落的真实母单、出库单和登记表行级补录继续推进到主数据自动匹配、状态流转、批量复核、追溯聚合；在 M3.121 机器档案匹配建议之上补齐机器档案绑定命令、签名附件和更完整的设备产能统计报表。
- 将配置基线、配置变更单和审计日志迁移为 MySQL seed 数据，并在 M3.119 账号前置审批证据上传入口、M3.117 临时密码一次性通道、M3.116 管理员新建/重置密码强制首次改密、M3.115 账号停用会话吊销、M3.114 账号动作审批通过态校验、M3.113 账号前置审批任务交接、M3.112 账号动作申请单号强制开关、M3.111 账号动作申请单号校验、M3.110 账号前置审批申请单 JDBC 持久化、M3.109 账号前置审批申请单、M3.108 账号前置审批草稿评估、M3.107 账号前置审批就绪度、M3.100 数据范围过滤、账号审批摘要、请求头兜底开关、账号客户范围和可视化边界之上接入生产菜单表、完整组织客户授权表、SSO/JWT、正式电子签字和审计保全。
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
- [真实数据与上线待确认清单](docs/22_真实数据与上线待确认清单.md)
- [客户交付版本记录](docs/23_客户交付版本记录.md)
