# 来料加工进销存与生产管理系统 字段字典与 API 契约

**文档版本**：V1.1
**更新日期**：2026-07-08
**适用阶段**：M2 API 基线，M3 数据库和前后端联调依据  

---

## 1. 目标

本文件先冻结“评审工作台 API”的字段契约，解决前端样例、后端 API、后续 seed 数据和数据库设计之间的字段不一致问题。

当前接口仍返回确定性样例数据，不代表最终生产持久化已完成。M3 需要将这些字段迁移到：

- MySQL 业务表和样例 seed 数据。
- 后端 repository/service。
- 前端 API client。
- 验收脚本和演示环境初始化数据。

当前代码和草案位置：

- [BusinessWorkspaceService.java](../backend/src/main/java/com/zhubao/erp/business/app/BusinessWorkspaceService.java)
- [BusinessReviewData.java](../backend/src/main/java/com/zhubao/erp/business/domain/BusinessReviewData.java)
- [BusinessWorkspaceRepository.java](../backend/src/main/java/com/zhubao/erp/business/domain/BusinessWorkspaceRepository.java)
- [BusinessWorkspaceSeedData.java](../backend/src/main/java/com/zhubao/erp/business/infra/BusinessWorkspaceSeedData.java)
- [businessWorkspaceApi.ts](../frontend/src/services/businessWorkspaceApi.ts)
- [V3__m2_business_workspace_contract.sql](../backend/src/main/resources/db/mysql/V3__m2_business_workspace_contract.sql)
- [ConfigurationBaselineService.java](../backend/src/main/java/com/zhubao/erp/configuration/app/ConfigurationBaselineService.java)
- [ConfigurationBaselineData.java](../backend/src/main/java/com/zhubao/erp/configuration/domain/ConfigurationBaselineData.java)
- [configurationBaselineApi.ts](../frontend/src/services/configurationBaselineApi.ts)
- [V4__m2_configuration_baseline_contract.sql](../backend/src/main/resources/db/mysql/V4__m2_configuration_baseline_contract.sql)
- [V5__m2_configuration_change_control_contract.sql](../backend/src/main/resources/db/mysql/V5__m2_configuration_change_control_contract.sql)
- [V7__m3_hardware_poc_record_contract.sql](../backend/src/main/resources/db/mysql/V7__m3_hardware_poc_record_contract.sql)
- [V8__m3_business_workspace_jdbc_repository_contract.sql](../backend/src/main/resources/db/mysql/V8__m3_business_workspace_jdbc_repository_contract.sql)
- [V9__m3_business_workspace_review_seed.sql](../backend/src/main/resources/db/mysql/V9__m3_business_workspace_review_seed.sql)
- [DocumentManagementService.java](../backend/src/main/java/com/zhubao/erp/document/app/DocumentManagementService.java)
- [documentManagementApi.ts](../frontend/src/services/documentManagementApi.ts)
- [V10__m3_document_management_contract.sql](../backend/src/main/resources/db/mysql/V10__m3_document_management_contract.sql)
- [ApprovalService.java](../backend/src/main/java/com/zhubao/erp/approval/app/ApprovalService.java)
- [JdbcApprovalTaskRepository.java](../backend/src/main/java/com/zhubao/erp/approval/infra/JdbcApprovalTaskRepository.java)
- [V11__m3_lightweight_approval_contract.sql](../backend/src/main/resources/db/mysql/V11__m3_lightweight_approval_contract.sql)
- [OperationsCenterService.java](../backend/src/main/java/com/zhubao/erp/operations/app/OperationsCenterService.java)
- [operationsCenterApi.ts](../frontend/src/services/operationsCenterApi.ts)
- [RegisterImportTemplateService.java](../backend/src/main/java/com/zhubao/erp/register/app/RegisterImportTemplateService.java)
- [RegisterImportConfirmationService.java](../backend/src/main/java/com/zhubao/erp/register/app/RegisterImportConfirmationService.java)
- [RegisterImportPostingPlanService.java](../backend/src/main/java/com/zhubao/erp/register/app/RegisterImportPostingPlanService.java)
- [RegisterImportController.java](../backend/src/main/java/com/zhubao/erp/register/web/RegisterImportController.java)
- [registerImportApi.ts](../frontend/src/services/registerImportApi.ts)
- [V12__m3_register_import_batch_contract.sql](../backend/src/main/resources/db/mysql/V12__m3_register_import_batch_contract.sql)
- [BusinessDocumentService.java](../backend/src/main/java/com/zhubao/erp/business/app/BusinessDocumentService.java)
- [BusinessDocumentController.java](../backend/src/main/java/com/zhubao/erp/business/web/BusinessDocumentController.java)
- [V14__m3_business_document_contract.sql](../backend/src/main/resources/db/mysql/V14__m3_business_document_contract.sql)
- [NavigationMenuService.java](../backend/src/main/java/com/zhubao/erp/security/app/NavigationMenuService.java)
- [SecurityContextController.java](../backend/src/main/java/com/zhubao/erp/security/web/SecurityContextController.java)
- [authApi.ts](../frontend/src/services/authApi.ts)

---

## 2. 接口分组

统一前缀：

```http
/api/business/workspaces
```

| 接口 | 页面 | 用途 |
|---|---|---|
| `GET /summary` | 首页/总览 | 返回核心工作台数量汇总 |
| `GET /inbound` | 来料母单 | 返回入库检查点、来料队列、入库异常 |
| `GET /production` | 生产工单 | 返回工位负载、工单列表、称重交接 |
| `GET /quality` | 质检返工 | 返回质检指标、质检单、损耗审批 |
| `GET /inventory` | 库存追溯 | 返回库存余额、台账明细、追溯时间线、证据 |
| `GET /mobile` | PWA 作业 | 返回移动任务、PWA 能力边界、离线草稿 |

登记表导入模板接口前缀：

```http
/api/register-import
```

| 接口 | 页面 | 用途 |
|---|---|---|
| `GET /templates` | 样例库/预检操作台 | 返回入库、出货、抛光、雕刻登记表的字段映射、目标命令和校验规则 |
| `GET /summary` | 样例库/预检操作台 | 返回模板数量、字段数量、必填字段数量和解析器接入状态 |
| `POST /previews` | 样例库/预检操作台 | multipart 上传 `.xlsx`，按模板解析并返回标准化字段、阻断/警告消息和可确认状态；不写业务台账 |
| `POST /batches/confirm` | 样例库/预检操作台 | 接收已通过预检的 `RegisterImportPreview`，保存结构化导入批次、行级标准化字段和校验消息；不写库存台账 |
| `GET /batches` | 样例库/导入复核 | 查询最近结构化导入批次 |
| `GET /batches/{importBatchNo}` | 样例库/导入复核 | 按批次号查询批次头、行和消息 |
| `GET /batches/{importBatchNo}/posting-plan` | 样例库/入账映射计划 | 只读生成目标命令草稿、已映射字段、缺失字段和警告；不写库存台账或业务单据 |
| `POST /batches/{importBatchNo}/rows/{sourceLineNo}/corrections` | 样例库/导入复核 | 对确认批次行补录入账映射字段，更新行级标准化字段后重新生成 posting plan；不写库存台账或业务单据 |
| `POST /batches/{importBatchNo}/posting-commands` | 样例库/登记表入账 | 显式执行已通过映射计划的批次行，调用后端业务服务生成库存台账；阻断行和重复入账拒绝 |

当前 M3.88 已完成 Excel 上传预览解析、前端问题 CSV 下载、真实空白模板结构复核、结构化导入批次确认、参考表字段命名复核、确认批次复核台、只读入账映射计划、行级复核补录入口、显式入账命令、雕刻设备产量独立落库、生产页设备产量汇总入口、基础业务单据结构化持久化切片，以及账号仓储、PBKDF2 密码哈希和登录失败锁定边界：支持入库、出货、抛光、雕刻 4 类登记表，能处理空白模板占位行、抛光 6 个重复日报块、抛光无纸质序号明细行、雕刻 6 组 x 8 号机器组内继承、数量单位和纸质快递栏拆分；4 类模板已纳入 `sourceRegisterType/sourceRegisterLineNo` 字段映射，字段总数仍为 48。M3.75 将入库表真实表头 `寄货人/姓名`、`收货人/姓名` 和雕刻派生定位字段 `机器编号行号` 写入模板契约。M3.82 前端可通过 `GET /batches`、`GET /batches/{importBatchNo}`、`GET /batches/{importBatchNo}/posting-plan`、`POST /batches/{importBatchNo}/rows/{sourceLineNo}/corrections`、`POST /batches/{importBatchNo}/posting-commands`、`GET /machine-outputs/summary`、`GET /business/documents/mother-orders/{code}` 和 `GET /business/documents/outbound-orders/{code}` 查看最近确认批次、行级标准化字段、警告消息、命令草稿、缺失字段补录入口、入账按钮、后端返回的业务单号/台账号、设备产量统计以及入账后的母单/出库单结构化快照。确认导入、复核补录和 posting plan 仅用于结构化复核与入账准备；真正入账必须走独立 POST 命令，并继续复用业务服务与库存台账服务。

业务操作接口前缀：

```http
/api/business/operations
```

| 接口 | 页面 | 用途 |
|---|---|---|
| `POST /inbound-confirmations` | 来料母单 | 确认客户来料入库，写入库存台账和客户所有原料库存余额 |
| `POST /production-reports` | 生产工单 | 提交工序报工，将同一子工单的在制库存转为半成品库存 |
| `POST /quality-freezes` | 质检返工 | 冻结质检异常库存，将同一子工单的半成品库存转为冻结库存 |
| `POST /rework-dispatches` | 质检返工 | 派发返工库存，将同一子工单的冻结库存转为返工在制库存 |
| `POST /rework-reports` | 质检返工 | 提交返工报工，将同一子工单的返工在制库存转回半成品库存 |
| `POST /quality-releases` | 质检返工 | 复检放行，将同一子工单的半成品库存转为成品库存 |
| `POST /outbound-shipments` | 库存追溯 | 确认成品出库，将同一子工单的成品库存转为已发货库存 |
| `POST /customer-returns` | 库存追溯 | 客户退货入库，将同一子工单的已发货库存转回退货复核库存 |
| `POST /after-sales-reviews` | 库存追溯 | 售后复核分流，将退货复核库存转为成品、返工、报废或冻结库存 |
| `POST /loss-approvals` | 质检返工 | 生成损耗审批与工资试算快照，并创建轻量审批任务；不写库存台账，不代表正式工资发放 |

业务单据查询接口前缀：

```http
/api/business/documents
```

| 接口 | 页面 | 用途 |
|---|---|---|
| `GET /mother-orders/{code}` | 来料母单 / 登记表入账结果 / 追溯 | 查询入库业务动作保存的结构化母单快照，包含入库登记字段、库存台账号和来源登记表字段 |
| `GET /outbound-orders/{code}` | 出库追溯 / 登记表入账结果 | 查询成品出库业务动作保存的结构化出库单快照，包含出货登记字段、物流字段、库存台账号和来源登记表字段 |

设备产量接口前缀：

```http
/api/machine-outputs
```

| 接口 | 页面 | 用途 |
|---|---|---|
| `GET /machine-outputs?limit=20` | 生产统计 / 样例库 | 查询最近设备产量记录，默认最多 20 条 |
| `GET /machine-outputs/summary?limit=100` | 生产派工 / 生产统计 | 汇总最近设备产量记录，返回总记录数、总产量、按机器/日期分组、机器档案待匹配数和签名附件待补数 |
| `GET /machine-outputs/import-batches/{importBatchNo}` | 样例库/登记表入账结果 | 查询某个登记表导入批次生成的设备产量记录 |
| `POST /machine-outputs` | 生产统计 / 设备产量补录 | 手工或低成本设备适配提交设备产量记录，受 `MACHINE_OUTPUT_SUBMIT` 权限控制 |

`SubmitMachineOutputCommand`：

| 字段 | 说明 |
|---|---|
| `code` | 可选；为空时后端生成 `MOR-` 编号 |
| `workDate` | 必填，设备产量日期 |
| `customerId` / `customerNameSnapshot` | 可选客户 ID 和客户快照 |
| `productCategory` | 必填，品类 |
| `machineId` / `machineNoSnapshot` | 设备档案 ID 可空，机器编号快照必填 |
| `workOrderCode` / `workReportCode` | 可选工单/报工快照；登记表显式入账会写工单号 |
| `outputQuantity` / `quantityUnit` | 必填产量和单位，单位为空默认 `件` |
| `operatorEmployeeId` / `operatorSignatureName` | 操机员工 ID 可空，操机签字快照必填 |
| `signatureMediaId` | 可选，后续接手写签名附件 |
| `sourceRegisterType` / `sourceLineNo` / `machineLineNo` / `sourceSequenceNo` | 原始登记来源、Excel 行号、1-8 号机器行和纸质序号 |
| `importBatchNo` / `ledgerCode` | 可选导入批次号和库存台账号；`REG-ENGRAVING` 入账时自动写入 |
| `status` | 可选，默认 `SUBMITTED` |
| `remark` / `operator` | 备注和录入人 |

`MachineOutputRecord` 返回上述字段并补充 `createdBy`、`createdAt`。M3.79 中 `REG-ENGRAVING` 的 `POST /register-import/.../posting-commands` 会先经 `BusinessOperationService.reportProduction` 写库存台账，再保存同批次 `MachineOutputRecord`；这解决“雕刻产量只在台账备注里”的缺口。M3.80 新增的 `MachineOutputSummary` 面向生产统计页，先基于最近记录聚合，后续可替换为 SQL 聚合或独立报表表。

`MachineOutputSummary`：

| 字段 | 说明 |
|---|---|
| `totalRecords` | 本次汇总纳入的设备产量记录数 |
| `totalQuantity` / `quantityUnit` | 汇总产量和单位；单位不一致时返回 `混合` |
| `byMachine[]` | 按 `machineNoSnapshot` 分组的产量和记录数，供生产页查看机器产能 |
| `byWorkDate[]` | 按 `workDate` 分组的产量和记录数，供日报入口使用 |
| `unmatchedMachineRecords` | `machineId` 为空的记录数，表示还未匹配机器档案 |
| `signatureMissingRecords` | `signatureMediaId` 为空的记录数，表示仍需补手写签名或附件 |

### 2.1 登记表导入模板字段

`RegisterImportTemplate` 用于约束后续 Excel 导入器，不直接写业务数据。

| 字段 | 说明 |
|---|---|
| `code` | 模板编码，如 `REG-INBOUND`、`REG-OUTBOUND` |
| `name` | 登记表名称 |
| `sourceFile` | 当前参考文件名 |
| `businessFlow` | 该表对应的业务链路 |
| `targetCommand` | 导入预览通过后应生成或补齐的业务命令 |
| `targetLedgerType` | 最终影响的库存台账来源类型 |
| `parserStatus` | 当前状态为 `COMMAND_READY`，表示命令映射、预览解析和结构化批次确认已就绪；业务单据自动入账仍待映射 |
| `nextAction` | 后续开发动作 |
| `fields` | 字段映射列表 |
| `validationRules` | 导入校验规则列表 |

### 2.2 登记表导入预览字段

`POST /api/register-import/previews` 请求：

| 字段 | 说明 |
|---|---|
| `templateCode` | 必填，`REG-INBOUND`、`REG-OUTBOUND`、`REG-POLISHING`、`REG-ENGRAVING` |
| `businessDate` | 可选，纸质表只有月日时用于补全年份 |
| `file` | 必填，现场登记表 `.xlsx` 文件 |

返回对象：

| 字段 | 说明 |
|---|---|
| `importBatchNo` | 本次预览批次号；确认导入后作为 `register_import_batch.import_batch_no` 和 `register_import_row.import_batch_no` |
| `templateCode` / `templateName` | 模板编码和模板名称 |
| `sourceFileName` | 上传文件名 |
| `totalRows` | 实际解析出的业务行数量，不含空白模板行 |
| `readyRows` / `warningRows` / `blockedRows` | 可确认、警告、阻断行数量 |
| `executable` | 是否可进入人工复核或确认导入；只要存在阻断行即为 `false` |
| `nextAction` | 下一动作提示 |
| `rows` | 逐行预览结果 |

`rows[]`：

| 字段 | 说明 |
|---|---|
| `sourceLineNo` | Excel 原始行号 |
| `sourceRegisterSequence` | 纸质表内序号 |
| `status` | `READY`、`WARNING`、`BLOCKED` |
| `normalizedFields` | 标准化后的字段 Map，例如 `sourceSheetName`、`sourceRegisterType`、`sourceRegisterLineNo`、`inboundDate`、`paperExpressText`、`logisticsNo`、`operator`、`machineLineNo`、`machineNo`、`quantityUnit` |
| `messages` | 逐行校验消息 |

`messages[]`：

| 字段 | 说明 |
|---|---|
| `ruleCode` | 校验规则编码 |
| `level` | `BLOCK` 或 `WARN` |
| `field` | 命中的目标字段 |
| `message` | 校验说明 |
| `fallback` | 现场补救动作 |

前端问题 CSV 下载字段：

| 字段 | 说明 |
|---|---|
| `importBatchNo` | 本次预检批次号 |
| `templateCode` / `templateName` | 登记表模板 |
| `sourceFileName` / `sourceSheetName` | 上传文件名和 Excel 工作表名 |
| `sourceLineNo` / `sourceRegisterSequence` | Excel 原始行号和纸质序号 |
| `status` | 行状态 |
| `ruleCode` / `level` / `field` | 命中的校验规则 |
| `message` / `fallback` | 现场修表说明和兜底动作 |
| `normalizedFields` | 该行全部标准化字段 JSON，便于和原表回查 |

CSV 下载只发生在前端浏览器内，来自 `/previews` 返回结果，不是新的后端导出接口；确认导入后会保存结构化批次，但当前仍不写入库存台账或业务单据。

### 2.3 登记表结构化导入批次

`POST /api/register-import/batches/confirm` 请求：

| 字段 | 说明 |
|---|---|
| `preview` | 必填，来自 `/previews` 的完整 `RegisterImportPreview` |
| `operator` | 复核确认人；为空时后端使用 `登记表复核员` |
| `confirmRemark` | 复核备注；为空时后端写入“不直接写业务台账”的默认说明 |

确认规则：

- `preview.executable` 必须为 `true`。
- `blockedRows` 必须为 `0`。
- `rows` 不能为空。
- 同一个 `importBatchNo` 不允许重复确认。
- `WARNING` 行可以确认，但必须保留行级 `messages`，供导入复核页面二次审查。

返回对象 `RegisterImportBatch`：

| 字段 | 说明 |
|---|---|
| `importBatchNo` | 结构化导入批次号 |
| `templateCode` / `templateName` | 登记表模板 |
| `sourceFileName` | 上传文件名 |
| `targetCommand` | 后续业务入账映射目标，例如 `ShipFinishedStockCommand` |
| `totalRows` / `readyRows` / `warningRows` / `blockedRows` | 预检统计快照 |
| `status` | `CONFIRMED`、`POSTED` 或 `REJECTED`；`POSTED` 表示显式入账命令已执行 |
| `confirmedBy` / `confirmedAt` | 复核人和确认时间 |
| `confirmRemark` | 复核备注 |
| `nextAction` | 下一动作；确认后为“待业务单据入账映射”，入账后提示重复执行将被拒绝 |
| `rows` | 结构化导入行，包含 `sourceLineNo`、`sourceRegisterSequence`、`normalizedFields` 和 `messages` |

MySQL 持久化表：

| 表 | 用途 |
|---|---|
| `register_import_batch` | 批次头、模板、来源文件、统计、确认人和状态 |
| `register_import_row` | 行级原表行号、纸质序号、状态、标准化字段 JSON 和消息 JSON |

M3.76 前端复核台约束：

- `GET /api/register-import/batches?limit=10` 用于显示最近确认批次，后端返回批次头和行级快照；`limit` 在服务端限制为 1-50。
- `GET /api/register-import/batches/{importBatchNo}` 用于点击批次后读取单批次详情，展示 `rows[].normalizedFields` 和 `rows[].messages`。
- 前端在后端 API 不可用时保留本地评审批次兜底，并明确标注数据来源；该兜底只用于评审界面，不代表已写入 MySQL。
- `status=CONFIRMED` 在页面显示为“待入账映射”，不得被解释为已经生成母单、报工、设备产量或出库单。
- `status=POSTED` 在页面显示为“已入业务单据”，重复调用显式入账命令必须被后端拒绝。
- 入账映射必须走 `POST /posting-commands` 独立命令，不能在批次查询或 posting plan 接口中隐式写库存台账。
- M3.82 的复核补录只允许更新未入账批次行的 `normalizedFields`；`importBatchNo`、`sourceRegisterLineNo`、`sourceRegisterSequence` 等来源审计字段禁止通过补录接口覆盖。

### 2.4 登记表入账映射计划

`GET /api/register-import/batches/{importBatchNo}/posting-plan` 返回对象 `RegisterImportPostingPlan`：

| 字段 | 说明 |
|---|---|
| `importBatchNo` | 结构化导入批次号 |
| `templateCode` / `templateName` | 登记表模板 |
| `targetCommand` | 目标命令草稿类型；雕刻为 `MachineOutputRecordDraft / ReportProductionCommand` |
| `status` | `READY`、`WARNING`、`BLOCKED`、`POSTED` |
| `totalRows` / `readyDrafts` / `warningDrafts` / `blockedDrafts` | 批次行数和草稿状态统计 |
| `nextAction` | 下一步处理说明 |
| `drafts` | 行级入账草稿 |

`drafts[]`：

| 字段 | 说明 |
|---|---|
| `sourceLineNo` / `sourceRegisterSequence` | Excel 原始行号和纸质序号 |
| `targetCommand` | 该行建议生成的目标命令 |
| `status` | 行级 `READY`、`WARNING`、`BLOCKED`、`POSTED` |
| `mappedFields` | 已从登记表行映射出的命令字段草稿 |
| `missingFields` | 缺失字段列表，例如母单、工单、客户、物料、仓库、重量或物流单号 |
| `warningMessages` | 继承确认批次行级 `WARN` 消息 |

`missingFields[]`：

| 字段 | 说明 |
|---|---|
| `field` | 缺失字段名 |
| `level` | 当前为 `BLOCK` |
| `message` | 缺失原因 |
| `fallback` | 复核页补救动作 |

只读约束：

- posting plan 只读取已确认批次，不修改 `register_import_batch/register_import_row`。
- posting plan 不调用库存台账服务，不写 `inv_stock_ledger` 或 `inv_stock_balance`。
- posting plan 不生成 `biz_mother_order`、`biz_work_report`、`biz_machine_output_record`、`biz_outbound_order` 等业务单据。
- 正式入账只能通过 2.6 的显式业务命令执行，并继续复用后端库存台账服务和权限守卫。

### 2.5 登记表复核补录命令

`POST /api/register-import/batches/{importBatchNo}/rows/{sourceLineNo}/corrections` 请求对象 `CorrectRegisterImportRowCommand`：

| 字段 | 说明 |
|---|---|
| `fields` | 必填；待补录字段键值，例如 `shipmentCode`、`motherOrderCode`、`workOrderCode`、`customerId`、`materialCode`、`weight` |
| `operator` | 可选；复核补录人，为空时使用默认登记表复核员 |
| `correctionRemark` | 可选；补录说明，写入批次 `nextAction` 便于复核台展示 |

执行规则：

- 只允许对 `CONFIRMED` 且行状态非 `POSTED` 的批次行补录。
- 补录动作只合并更新 `register_import_row.normalized_fields_json`，并更新批次 `next_action`。
- 不调用 `StockLedgerService`，不写 `inv_stock_ledger/inv_stock_balance`，不生成母单、出库单、报工或设备产量记录。
- `importBatchNo`、`sourceRegisterLineNo`、`sourceRegisterSequence` 等来源审计字段禁止覆盖。
- 补录后应重新读取 `GET /posting-plan`，由 posting plan 判断该行是否从 `BLOCKED` 变为 `READY` 或 `WARNING`。
- 权限码为 `REGISTER_IMPORT_REVIEW`，当前允许 `RECEIVER`、`WAREHOUSE_KEEPER`、`WORKSHOP_LEADER` 以及默认管理角色执行。

返回对象为更新后的 `RegisterImportBatch`。

### 2.6 登记表显式入账命令

`POST /api/register-import/batches/{importBatchNo}/posting-commands` 请求对象 `PostRegisterImportBatchCommand`：

| 字段 | 说明 |
|---|---|
| `allowWarnings` | 可选；当 posting plan 为 `WARNING` 时必须显式传 `true`，否则后端拒绝入账 |
| `operator` | 可选；入账执行人，为空时使用批次确认人 |
| `postingRemark` | 可选；追加进入业务命令备注，并和登记表批次号、原始行号一起写入台账备注 |

执行规则：

- 先读取 `RegisterImportPostingPlan`，存在 `blockedDrafts > 0` 时拒绝。
- `warningDrafts > 0` 且 `allowWarnings` 不是 `true` 时拒绝。
- 批次或行状态为 `POSTED` 时拒绝重复执行。
- `REG-INBOUND` 转换为 `ConfirmInboundCommand`。
- `REG-OUTBOUND` 转换为 `ShipFinishedStockCommand`。
- `REG-POLISHING` 转换为 `ReportProductionCommand`；`REG-ENGRAVING` 会先执行 `ReportProductionCommand` 写库存台账，再同步生成 `biz_machine_output_record`。
- 所有库存变化必须经 `BusinessOperationService` 进入 `StockLedgerService`，不得直接改余额表。
- 成功后行状态更新为 `POSTED`；全部行完成后批次状态更新为 `POSTED`。
- 权限码为 `REGISTER_IMPORT_POSTING`，当前允许 `RECEIVER`、`WAREHOUSE_KEEPER`、`WORKSHOP_LEADER` 以及默认管理角色执行。

返回对象 `RegisterImportPostingExecutionResult`：

| 字段 | 说明 |
|---|---|
| `importBatchNo` | 入账批次号 |
| `templateCode` / `templateName` | 登记表模板 |
| `status` | 当前为 `POSTED`；预留 `PARTIAL`、`REJECTED` |
| `totalDrafts` / `postedDrafts` / `skippedDrafts` | 草稿总数、已入账行数和跳过行数 |
| `blockedDrafts` / `warningDrafts` | 执行时的阻断/警告统计快照 |
| `operator` / `postedAt` | 执行人和执行时间 |
| `nextAction` | 后续提示，成功时说明库存变化已进入库存台账 |
| `items` | 行级执行结果 |

`items[]`：

| 字段 | 说明 |
|---|---|
| `sourceLineNo` / `sourceRegisterSequence` | Excel 原始行号和纸质序号 |
| `targetCommand` | 实际调用的业务命令 |
| `status` | 当前为 `POSTED`；预留 `SKIPPED` |
| `businessCode` | 后端业务服务返回的母单号、工单号、出库单号等 |
| `ledgerCode` | `StockLedgerService` 返回的库存台账号 |
| `message` | 执行说明 |

`RegisterFieldMapping`：

| 字段 | 说明 |
|---|---|
| `sourceColumn` | 原始表头字段或系统生成的审计字段，例如 `登记表类型`、`原表行号` |
| `targetField` | 系统字段或命令字段 |
| `targetObject` | 目标命令或业务对象 |
| `required` | 导入确认前是否必填 |
| `normalizeRule` | 标准化规则，例如表头继承、日期补全年份、快递栏拆分 |
| `validationRule` | 校验规则说明 |

`RegisterValidationRule`：

| 字段 | 说明 |
|---|---|
| `code` | 校验规则编码 |
| `level` | `BLOCK` 或 `WARN` |
| `description` | 规则说明 |
| `fallback` | 失败兜底动作 |

审批任务接口前缀：
```http
/api/approvals
```

| 接口 | 页面 | 用途 |
|---|---|---|
| `GET /tasks` | 首页/质检返工 | 查询全部轻量审批任务 |
| `GET /tasks/pending` | 首页/质检返工 | 查询待审批任务 |
| `GET /tasks/{approvalCode}` | 质检返工 | 查询单个审批任务 |
| `POST /tasks/{approvalCode}/decisions` | 质检返工 | 审批通过或驳回，并锁定审批快照 |

配置基线接口前缀：

```http
/api/configuration/baseline
```

| 接口 | 页面 | 用途 |
|---|---|---|
| `GET /summary` | 配置基线 | 返回配置项数量和需审批项数量 |
| `GET /system-parameters` | 配置基线 | 返回系统参数、等级、作用范围、变更控制 |
| `GET /code-rules` | 配置基线 | 返回业务编码格式、示例和唯一性规则 |
| `GET /role-permissions` | 配置基线 | 返回角色、数据范围、权限和敏感动作 |
| `GET /process-templates` | 配置基线 | 返回工序模板、损耗、工资和质检规则 |
| `GET /warehouse-states` | 配置基线 | 返回仓库状态、客户所有权边界和状态流转 |
| `GET /video-parameters` | 配置基线 | 返回视频截取、人工上传和保存策略参数 |
| `GET /components` | 配置基线 | 返回开源组件版本、许可证、用途和风险 |
| `GET /backup-policies` | 配置基线 | 返回数据库、附件、配置模板的备份策略 |
| `GET /change-requests` | 配置基线 | 返回配置变更单、审批状态和风险等级 |
| `GET /audit-events` | 配置基线 | 返回配置审批、导出和后续修改的审计事件 |

硬件联动接口前缀：

```http
/api/hardware
```

| 接口 | 页面 | 用途 |
|---|---|---|
| `GET /devices` | 软硬件联动 | 返回设备兼容矩阵 |
| `GET /integration-tasks` | 软硬件联动 | 返回硬件调用任务和健康度 |
| `GET /poc-records` | 软硬件联动 | 返回现场 POC 验证记录 |
| `GET /summary` | 软硬件联动 | 返回设备、兜底、健康度和 POC 阻断汇总 |

文档附件接口前缀：

```http
/api/documents
```

| 接口 | 页面 | 用途 |
|---|---|---|
| `GET /summary` | 文档附件 | 返回文档包、附件、证据缺口、人工兜底和审计事件数量 |
| `GET /packages` | 文档附件 | 返回业务文档包和证据完整度 |
| `GET /attachments` | 文档附件 | 返回附件元数据、存储位置、校验和和兜底动作 |
| `GET /audit-events` | 文档附件 | 返回上传、补传、归档、审批锁定审计事件 |
| `POST /attachments/uploads` | 文档附件 | multipart 上传附件，生成附件元数据、checksum、审计事件和预览 URL |
| `GET /attachments/{code}/preview-url` | 文档附件 | 返回附件预览 URL 和过期时间 |
| `GET /attachments/{code}/content` | 文档附件 | 读取已上传到当前存储适配器的附件内容，后续生产环境替换为 NAS/S3 签名 URL |

运维中心接口前缀：

```http
/api/operations
```

| 接口 | 页面 | 用途 |
|---|---|---|
| `GET /center` | 运维中心 | 返回服务状态、服务器指标、报错汇总、近期日志、数据库备份状态和运维接入清单 |

---

## 3. 通用字段

| 字段 | 类型 | 说明 | 后续落库建议 |
|---|---|---|---|
| `code` | string | 业务编码或样例编码 | 所有主业务表保留唯一编码 |
| `label` | string | 展示名称 | 字典或配置表 |
| `value` | string | 展示值，含单位 | 生产表拆成数值和单位 |
| `detail` | string | 辅助说明 | 备注/异常说明 |
| `owner` | string | 当前责任人 | 用户 ID + 快照名称 |
| `action` | string | 下一步动作 | 状态机动作或待办动作 |
| `tone` | string | 前端状态色：`green/blue/amber/red/violet/gray` | 前端展示字段，不作为业务状态 |
| `status` | string | 业务状态 | 必须改为枚举或字典 |

---

## 4. 来料字段

### 4.1 `InboundOrder`

| 字段 | 说明 |
|---|---|
| `code` | 母单号，例如 `MD-20260706-001` |
| `customer` | 客户名称快照 |
| `material` | 物料名称快照 |
| `inboundDate` | 入库日期，来自现场签收入库登记 |
| `expressCompany` | 快递名称/物流方式 |
| `courierNo` | 快递单号 |
| `senderName` | 寄货人姓名快照 |
| `receiverName` | 收货人姓名快照 |
| `unpackSigner` | 拆箱签字/确认人 |
| `goodsName` | 现场货品名称快照，可与物料档案名称不同 |
| `quantity` | 件数 |
| `quantityUnit` | 数量单位，默认 `件`，可按物料配置为 `颗/条/批` 等 |
| `weight` | 重量展示值，M3 应拆成 `netWeight` + `weightUnit` |
| `status` | 入库状态 |
| `videoStatus` | 拆包视频状态 |
| `ownerType` | 库存所有权，目前固定为客户所有 |
| `remark` | 入库备注 |

必须保留的业务约束：

- 客户来料和企业自有库存不能混在同一余额维度里。
- 确认入库必须写库存台账。
- 拆包视频失败必须有人工上传兜底。
- 入库数量必须有单位口径；前端可以展示 `320 件`，后端字段必须拆成 `quantity` + `quantityUnit`。

### 4.2 `ConfirmInboundCommand`

`POST /api/business/operations/inbound-confirmations` 使用该命令模型。该接口是 M3.5 第一条真实业务动作基线，不再只返回工作台样例。

| 字段 | 说明 |
|---|---|
| `motherOrderCode` | 母单号，作为库存台账来源单据 |
| `customerId` | 业务客户 ID |
| `ownerCustomerId` | 库存所有客户 ID，未传时默认等于 `customerId` |
| `materialCode` | 物料编码 |
| `inboundDate` | 现场入库日期，建议保存为 `yyyy-MM-dd`；纸质表只有月日时由业务日期补全年份 |
| `expressCompany` | 入库快递名称/物流方式 |
| `courierNo` | 入库快递单号，必须唯一 |
| `senderName` | 寄货人姓名快照 |
| `receiverName` | 收货人姓名快照 |
| `unpackSigner` | 拆箱签字/确认人，一期保存姓名，二期可关联签名附件 |
| `goodsName` | 现场货品名称快照 |
| `sourceRegisterType` | 来源登记表名称或类型，如 `签收入库登记表` |
| `sourceRegisterLineNo` | 原始登记表行号，批量导入或纸质转录时使用；已补入命令并写入台账备注 |
| `sourceRegisterSequence` | 纸质表内序号，如入库登记表的 `序号` |
| `warehouseCode` | 入库仓库，来料默认 `RAW-CUSTOMER` |
| `quantity` | 入库数量，必须大于 0 |
| `quantityUnit` | 入库数量单位，已补入命令，默认 `件` |
| `weight` | 入库重量，必须大于 0 |
| `operator` | 操作人快照 |
| `evidenceCode` | 快递单、称重记录或视频证据编码 |
| `remark` | 业务备注 |

返回 `InboundConfirmationResult`：

| 字段 | 说明 |
|---|---|
| `motherOrderCode` | 母单号 |
| `ledgerCode` | 库存台账号，由库存服务生成 |
| `warehouseCode` | 写入仓库 |
| `stockStatus` | 写入库存状态，一期为 `RAW_WAITING` |
| `quantity` | 已入账数量 |
| `weight` | 已入账重量 |
| `operator` | 操作人快照 |
| `evidenceCode` | 证据编码 |

实现约束：

- 接口不得直接改库存余额，必须通过 `StockLedgerService.transfer` 追加台账。
- `OwnerType` 固定为 `CUSTOMER`，确保客户来料不会进入企业自有库存。
- `workOrderCode` 为空，表示仍处于母单原料库存，后续子工单拆分再进入在制库存。

---

## 5. 生产字段

### 5.1 `WorkOrder`

| 字段 | 说明 |
|---|---|
| `code` | 子工单或返工工单号 |
| `customer` | 客户名称快照 |
| `material` | 物料名称快照 |
| `process` | 当前工序 |
| `processCategory` | 工序类别：人工/设备/质检/包装 |
| `productCategory` | 品类快照，雕刻统计必须填写 |
| `machineNo` | 机器编号，设备类工序必须填写或标记待匹配 |
| `workItemName` | 工作项目，如粗抛、精抛、返工抛光、雕刻 |
| `startedAt` | 本次作业开始时间 |
| `finishedAt` | 本次作业完成时间 |
| `completionStatus` | 完成情况 |
| `handoffAuditStatus` | 交单审核状态 |
| `assignee` | 负责人快照 |
| `plannedQuantity` | 计划数量 |
| `quantityUnit` | 数量单位，默认 `件` |
| `finishedQuantity` | 已完成数量 |
| `status` | 工单状态 |
| `progress` | 前端进度百分比 |

### 5.2 `ProductionHandoff`

| 字段 | 说明 |
|---|---|
| `code` | 称重交接单号 |
| `fromNode` | 来源工序/人员 |
| `toNode` | 目标工序/人员 |
| `inputWeight` | 投入重量展示值 |
| `outputWeight` | 产出重量展示值 |
| `lossRate` | 损耗率展示值 |
| `status` | 交接状态 |

必须保留的业务约束：

- 关键工序缺少称重交接时不能进入下一步。
- 工序单价、损耗率必须按报工时快照保存。
- 返工仍走同一套工单状态机。

### 5.3 `ReportProductionCommand`

`POST /api/business/operations/production-reports` 使用该命令模型。该接口是 M3.6 生产报工写台账基线。

| 字段 | 说明 |
|---|---|
| `motherOrderCode` | 母单号 |
| `workOrderCode` | 子工单或返工工单号 |
| `customerId` | 业务客户 ID |
| `ownerCustomerId` | 库存所有客户 ID，未传时默认等于 `customerId` |
| `materialCode` | 物料编码 |
| `fromWarehouseCode` | 来源在制仓/工位 |
| `toWarehouseCode` | 目标半成品仓/暂存区 |
| `quantity` | 本次报工产出数量，必须大于 0 |
| `quantityUnit` | 本次报工数量单位，已补入命令，默认 `件` |
| `weight` | 本次报工产出重量，必须大于 0 |
| `workDate` | 工序日报日期，建议保存为 `yyyy-MM-dd`；抛光日报和雕刻日统计必须能按该字段过滤 |
| `process` | 工序名称快照 |
| `processCategory` | 工序类别快照 |
| `productCategory` | 品类快照 |
| `machineNo` | 机器编号快照 |
| `workItemName` | 工作项目 |
| `startedAt` | 本次作业开始时间 |
| `finishedAt` | 本次作业完成时间 |
| `completionStatus` | 完成情况 |
| `handoffAuditStatus` | 交单审核状态，提交后默认 `待审核` |
| `sourceRegisterType` | 来源登记表名称或类型，如 `工作项目记录单（日）`、`雕刻机统计单（日）` |
| `sourceRegisterLineNo` | 原始抛光日报/雕刻统计行号，批量导入时使用；已补入命令并写入台账备注 |
| `sourceRegisterSequence` | 纸质表内序号；抛光明细可为空但必须保留 `sourceRegisterLineNo`，雕刻表可表示同一日期/客户/品类分组序号 |
| `operator` | 操作人快照 |
| `operatorSignatureName` | 操机签字/确认人姓名快照；雕刻等设备工序必须填写，二期可关联签名附件 |
| `handoffCode` | 称重交接记录编码 |
| `remark` | 业务备注 |

现场表单补充约束：

- 抛光报工必须能保存 `workItemName`、`startedAt`、`finishedAt`、`completionStatus` 和 `handoffAuditStatus`。
- 抛光日报必须能保存或推导 `workDate`；仅有开始/完成时间而无业务日期时，跨班次统计会失真。
- 雕刻报工必须能保存 `productCategory`、`machineNo` 和 `operatorSignatureName`；未绑定设备档案时允许先保存 `machineNo` 文本，但产能统计标记为待匹配设备。
- `completionStatus` 不直接等同于工序状态，工序是否流转仍以后端状态机和质检规则为准。
- 抛光登记表的 `姓名`、`日期` 是表头级字段，导入或录入时必须展开到每条工作项目明细。
- 雕刻登记表可按机器行继承本序号组内的日期、客户和品类，导入时不得把空白单元格直接视作缺失业务字段；进入新序号组后必须重置上下文。
- 报工数量必须有单位口径；计件工资和产能统计默认按 `件`，但应允许工序模板覆盖。

返回 `ProductionReportResult`：

| 字段 | 说明 |
|---|---|
| `workOrderCode` | 工单号 |
| `ledgerCode` | 库存台账号 |
| `fromWarehouseCode` | 来源仓/工位 |
| `toWarehouseCode` | 目标仓/暂存区 |
| `fromStockStatus` | 来源库存状态，当前为 `WIP` |
| `toStockStatus` | 目标库存状态，当前为 `SEMI_FINISHED` |
| `quantity` | 已入账数量 |
| `weight` | 已入账重量 |
| `process` | 工序名称快照 |
| `operator` | 操作人快照 |
| `handoffCode` | 称重交接记录编码 |

实现约束：

- `from` 与 `to` 必须保持同一客户、母单、工单和物料维度。
- 报工只允许改变仓库和库存状态，当前基线为 `WIP -> SEMI_FINISHED`。
- 若来源在制库存不足，后端必须拒绝写台账。

---

## 6. 质检字段

### 6.1 `QualityCase`

| 字段 | 说明 |
|---|---|
| `code` | 质检单号 |
| `workOrder` | 关联工单 |
| `process` | 关联工序 |
| `inspector` | 质检员快照 |
| `defect` | 缺陷描述 |
| `action` | 处理动作：返工、复检、放行等 |
| `status` | 质检状态 |

### 6.2 `LossApproval`

| 字段 | 说明 |
|---|---|
| `code` | 损耗审批单号 |
| `workOrder` | 关联工单 |
| `process` | 关联工序 |
| `standard` | 标准损耗率 |
| `actual` | 实际损耗率 |
| `evidence` | 称重和照片证据摘要 |
| `action` | 当前审批动作 |

### 6.3 `FreezeQualityStockCommand`

`POST /api/business/operations/quality-freezes` 使用该命令模型。该接口是 M3.8 质检冻结写台账基线。

| 字段 | 说明 |
|---|---|
| `qualityCaseCode` | 质检单号，作为库存台账来源单据 |
| `motherOrderCode` | 母单号 |
| `workOrderCode` | 子工单或返工工单号 |
| `customerId` | 业务客户 ID |
| `ownerCustomerId` | 库存所有客户 ID，未传时默认等于 `customerId` |
| `materialCode` | 物料编码 |
| `fromWarehouseCode` | 来源半成品仓/暂存区 |
| `toWarehouseCode` | 目标冻结仓/质检冻结区 |
| `quantity` | 本次冻结数量，必须大于 0 |
| `weight` | 本次冻结重量，必须大于 0 |
| `process` | 工序名称快照 |
| `inspector` | 质检员快照 |
| `defect` | 缺陷描述快照 |
| `evidenceCode` | 缺陷照片、称重或质检证据编码 |
| `remark` | 备注 |

### 6.4 `QualityFreezeResult`

| 字段 | 说明 |
|---|---|
| `qualityCaseCode` | 质检单号 |
| `workOrderCode` | 工单号 |
| `ledgerCode` | 库存台账号 |
| `fromWarehouseCode` | 来源仓/暂存区 |
| `toWarehouseCode` | 目标冻结仓/质检冻结区 |
| `fromStockStatus` | 来源库存状态，当前为 `SEMI_FINISHED` |
| `toStockStatus` | 目标库存状态，当前为 `FROZEN` |
| `quantity` | 已冻结数量 |
| `weight` | 已冻结重量 |
| `process` | 工序名称快照 |
| `inspector` | 质检员快照 |
| `evidenceCode` | 证据编码 |

实现约束：

- `from` 与 `to` 必须保持同一客户、母单、工单和物料维度。
- 质检冻结只允许改变仓库和库存状态，当前基线为 `SEMI_FINISHED -> FROZEN`。
- 若来源半成品库存不足，后端必须拒绝写台账。
- 冻结库存只是库存状态变更，不等同于生成返工工单；返工仍需后续业务命令接入同一工单状态机。

### 6.5 `DispatchReworkCommand`

`POST /api/business/operations/rework-dispatches` 使用该命令模型。该接口是 M3.9 返工派发写台账基线。

| 字段 | 说明 |
|---|---|
| `reworkOrderCode` | 返工单号，作为库存台账来源单据 |
| `qualityCaseCode` | 质检单号 |
| `motherOrderCode` | 母单号 |
| `workOrderCode` | 原子工单号，库存维度保持不变 |
| `customerId` | 业务客户 ID |
| `ownerCustomerId` | 库存所有客户 ID，未传时默认等于 `customerId` |
| `materialCode` | 物料编码 |
| `fromWarehouseCode` | 来源冻结仓/质检冻结区 |
| `toWarehouseCode` | 目标返工工位/返工区 |
| `quantity` | 本次返工派发数量，必须大于 0 |
| `weight` | 本次返工派发重量，必须大于 0 |
| `process` | 返工工序名称快照 |
| `operator` | 派发操作人快照 |
| `reason` | 返工原因快照 |
| `evidenceCode` | 缺陷照片、称重或质检证据编码 |
| `remark` | 备注 |

### 6.6 `ReworkDispatchResult`

| 字段 | 说明 |
|---|---|
| `reworkOrderCode` | 返工单号 |
| `qualityCaseCode` | 质检单号 |
| `workOrderCode` | 原子工单号 |
| `ledgerCode` | 库存台账号 |
| `fromWarehouseCode` | 来源冻结仓/质检冻结区 |
| `toWarehouseCode` | 目标返工工位/返工区 |
| `fromStockStatus` | 来源库存状态，当前为 `FROZEN` |
| `toStockStatus` | 目标库存状态，当前为 `REWORK` |
| `quantity` | 已派发数量 |
| `weight` | 已派发重量 |
| `process` | 返工工序名称快照 |
| `operator` | 派发操作人快照 |
| `evidenceCode` | 证据编码 |

实现约束：

- `from` 与 `to` 必须保持同一客户、母单、工单和物料维度。
- `reworkOrderCode` 是业务单据快照和台账来源，不直接改变库存维度里的 `workOrderCode`。
- 返工派发只允许改变仓库和库存状态，当前基线为 `FROZEN -> REWORK`。
- 若来源冻结库存不足，后端必须拒绝写台账。

### 6.7 `ReportReworkCommand`

`POST /api/business/operations/rework-reports` 使用该命令模型。该接口是 M3.10 返工报工写台账基线。

| 字段 | 说明 |
|---|---|
| `reworkOrderCode` | 返工单号，作为库存台账来源单据 |
| `qualityCaseCode` | 质检单号 |
| `motherOrderCode` | 母单号 |
| `workOrderCode` | 原子工单号，库存维度保持不变 |
| `customerId` | 业务客户 ID |
| `ownerCustomerId` | 库存所有客户 ID，未传时默认等于 `customerId` |
| `materialCode` | 物料编码 |
| `fromWarehouseCode` | 来源返工工位/返工区 |
| `toWarehouseCode` | 目标半成品仓/暂存区 |
| `quantity` | 本次返工报工产出数量，必须大于 0 |
| `weight` | 本次返工报工产出重量，必须大于 0 |
| `process` | 返工工序名称快照 |
| `operator` | 返工报工操作人快照 |
| `evidenceCode` | 返工照片、称重或质检证据编码 |
| `remark` | 备注 |

### 6.8 `ReworkReportResult`

| 字段 | 说明 |
|---|---|
| `reworkOrderCode` | 返工单号 |
| `qualityCaseCode` | 质检单号 |
| `workOrderCode` | 原子工单号 |
| `ledgerCode` | 库存台账号 |
| `fromWarehouseCode` | 来源返工工位/返工区 |
| `toWarehouseCode` | 目标半成品仓/暂存区 |
| `fromStockStatus` | 来源库存状态，当前为 `REWORK` |
| `toStockStatus` | 目标库存状态，当前为 `SEMI_FINISHED` |
| `quantity` | 已报工数量 |
| `weight` | 已报工重量 |
| `process` | 返工工序名称快照 |
| `operator` | 返工报工操作人快照 |
| `evidenceCode` | 证据编码 |

实现约束：

- `from` 与 `to` 必须保持同一客户、母单、工单和物料维度。
- `reworkOrderCode` 是业务单据快照和台账来源，不直接改变库存维度里的 `workOrderCode`。
- 返工报工只允许改变仓库和库存状态，当前基线为 `REWORK -> SEMI_FINISHED`。
- 若来源返工在制库存不足，后端必须拒绝写台账。
- 返工报工不等同于复检放行；复检放行由 `POST /quality-releases` 独立命令把合格半成品推进到成品库存。

### 6.9 `ReleaseQualityStockCommand`

`POST /api/business/operations/quality-releases` 使用该命令模型。该接口是 M3.14 复检放行写台账基线。

| 字段 | 说明 |
|---|---|
| `qualityCaseCode` | 质检单号，作为库存台账来源单据 |
| `motherOrderCode` | 母单号 |
| `workOrderCode` | 子工单或返工工单号 |
| `customerId` | 业务客户 ID |
| `ownerCustomerId` | 库存所有客户 ID，未传时默认等于 `customerId` |
| `materialCode` | 物料编码 |
| `fromWarehouseCode` | 来源半成品仓/暂存区 |
| `toWarehouseCode` | 目标成品仓/客户成品区 |
| `quantity` | 本次放行数量，必须大于 0 |
| `weight` | 本次放行重量，必须大于 0 |
| `process` | 复检工序名称快照 |
| `inspector` | 放行质检员快照 |
| `releaseResult` | 复检结论快照 |
| `evidenceCode` | 复检照片、抽检记录或放行证据编码 |
| `remark` | 备注 |

### 6.10 `QualityReleaseResult`

| 字段 | 说明 |
|---|---|
| `qualityCaseCode` | 质检单号 |
| `workOrderCode` | 工单号 |
| `ledgerCode` | 库存台账号 |
| `fromWarehouseCode` | 来源半成品仓/暂存区 |
| `toWarehouseCode` | 目标成品仓/客户成品区 |
| `fromStockStatus` | 来源库存状态，当前为 `SEMI_FINISHED` |
| `toStockStatus` | 目标库存状态，当前为 `FINISHED` |
| `quantity` | 已放行数量 |
| `weight` | 已放行重量 |
| `process` | 复检工序名称快照 |
| `inspector` | 放行质检员快照 |
| `releaseResult` | 复检结论快照 |
| `evidenceCode` | 证据编码 |

实现约束：

- `from` 与 `to` 必须保持同一客户、母单、工单和物料维度。
- 复检放行只允许改变仓库和库存状态，当前基线为 `SEMI_FINISHED -> FINISHED`。
- 若来源半成品库存不足，后端必须拒绝写台账。
- 复检放行只生成成品库存，不等同于发货或退货；出库仍需独立业务命令。

### 6.11 `ApproveLossCommand`

`POST /api/business/operations/loss-approvals` 使用该命令模型。该接口是 M3.19 损耗审批与工资试算基线，目标是先冻结损耗归因和工资试算口径，不接入正式审批流，不写库存台账。

| 字段 | 说明 |
|---|---|
| `approvalCode` | 损耗审批单号，作为审批/试算快照编码 |
| `motherOrderCode` | 母单号 |
| `workOrderCode` | 子工单或返工工单号 |
| `customerId` | 业务客户 ID |
| `ownerCustomerId` | 库存所有客户 ID，未传时默认仍按业务客户处理 |
| `materialCode` | 物料编码 |
| `process` | 工序名称快照 |
| `operator` | 提交人快照 |
| `approver` | 当前审批责任人快照 |
| `inputWeight` | 工序投入重量，必须大于 0，单位按业务配置默认为克 |
| `outputWeight` | 工序产出重量，必须大于等于 0 |
| `reasonableTransferWeight` | 合理转移/留样/工序间差异重量，必须大于等于 0 |
| `standardLossRatePercent` | 标准损耗率百分比，例如 `2.00` |
| `acceptedQuantity` | 合格计件数量，用于工资试算 |
| `pieceRate` | 工序计件单价快照 |
| `lossDeductionUnitPrice` | 超标损耗每克扣款单价快照 |
| `evidenceCode` | 称重、缺陷照片、交接记录或审批附件编码 |
| `remark` | 备注 |

### 6.12 `LossApprovalResult`

| 字段 | 说明 |
|---|---|
| `approvalCode` | 损耗审批单号 |
| `motherOrderCode` | 母单号 |
| `workOrderCode` | 工单号 |
| `process` | 工序名称快照 |
| `inputWeight` | 投入重量，按 3 位小数返回 |
| `outputWeight` | 产出重量，按 3 位小数返回 |
| `reasonableTransferWeight` | 合理转移重量，按 3 位小数返回 |
| `lossWeight` | 实际损耗重量，`max(0, inputWeight - outputWeight - reasonableTransferWeight)` |
| `allowedLossWeight` | 允许损耗重量，`inputWeight * standardLossRatePercent / 100` |
| `excessLossWeight` | 超标损耗重量，`max(0, lossWeight - allowedLossWeight)` |
| `standardLossRatePercent` | 标准损耗率百分比，按 2 位小数返回 |
| `actualLossRatePercent` | 实际损耗率百分比，`lossWeight * 100 / inputWeight` |
| `overLimit` | 是否存在超标损耗 |
| `approvalStatus` | 超标时为 `WAITING_APPROVAL`，未超标时为 `AUTO_APPROVED` |
| `acceptedQuantity` | 合格计件数量，按整数返回 |
| `pieceRate` | 计件单价快照，按 2 位小数返回 |
| `pieceWage` | 计件工资，`acceptedQuantity * pieceRate` |
| `lossDeduction` | 损耗扣款，`min(excessLossWeight * lossDeductionUnitPrice, pieceWage)` |
| `trialWage` | 工资试算，`max(0, pieceWage - lossDeduction)` |
| `formalPayroll` | 固定为 `false`，表示不是正式工资发放或锁账结果 |
| `operator` | 提交人快照 |
| `approver` | 审批责任人快照 |
| `evidenceCode` | 证据编码 |

实现约束：

- 该接口当前不调用 `StockLedgerService`，不改变库存余额，也不追加库存台账。
- 该接口会在 M3.22 生成轻量审批任务，但只证明固定场景审批、审批责任和工资试算口径，不代表 Flowable/正式审批流已经接入。
- 工序单价、扣损单价、标准损耗率必须作为快照字段保存，后续配置调整不得反向影响历史试算。
- 正式工资发放、工资锁账、财务凭证和员工薪资明细仍在一期边界外；后续若接入工资模块，必须从该试算快照转入独立工资单状态机。

---

### 6.13 `ApprovalTask`

M3.22 新增轻量审批任务基线，当前用于承载超标损耗审批。M3.24 已补 JDBC 仓储，可将任务与审计事件写入 MySQL 合同表。它仍不是完整流程引擎，不处理并行会签、复杂条件路由或正式工资锁账。

| 字段 | 说明 |
|---|---|
| `approvalCode` | 审批任务编码，当前复用损耗审批单号 |
| `scene` | 审批场景，当前为 `LOSS_APPROVAL` |
| `businessCode` | 业务对象编码，例如工单号 |
| `title` | 审批标题 |
| `status` | `WAITING_APPROVAL`、`APPROVED`、`REJECTED`、`AUTO_APPROVED` |
| `applicant` | 提交人快照 |
| `approver` | 审批责任人快照 |
| `ownerRole` | 责任角色快照 |
| `severity` | 前端风险色，不能替代业务状态 |
| `sourceStatus` | 来源命令返回的审批状态 |
| `sourceSummary` | 来源试算摘要，保留投入、产出、允许损耗、超标损耗、试算工资等关键口径 |
| `evidenceCode` | 称重、缺陷照片、交接记录或审批附件编码 |
| `formalPayroll` | 固定为 `false`，表示该审批任务不等于正式工资发放 |
| `createdAt` | 创建时间快照 |
| `decidedAt` | 审批处理时间快照 |
| `decisionOperator` | 审批操作人 |
| `decisionComment` | 审批意见 |
| `locked` | 审批处理后锁定，后续变更必须产生新任务或新审计事件 |

### 6.14 `ApprovalDecisionCommand`

`POST /api/approvals/tasks/{approvalCode}/decisions` 使用该命令模型。

| 字段 | 说明 |
|---|---|
| `decision` | `APPROVE` / `APPROVED` / `REJECT` / `REJECTED` |
| `operator` | 审批操作人快照 |
| `comment` | 审批意见 |
| `evidenceCode` | 审批附件或补充证据编码，可覆盖任务原证据编码 |

轻量审批约束：
- `WAITING_APPROVAL` 任务只能处理一次，处理后 `locked=true`。
- `AUTO_APPROVED` 用于未超标或在规则阈值内的自动通过快照。
- 默认仓储为内存实现；设置 `zhubao.approval.repository=jdbc` 或 `ZHUBAO_APPROVAL_REPOSITORY=jdbc` 后启用 `JdbcApprovalTaskRepository`。
- `V11__m3_lightweight_approval_contract.sql` 冻结 `approval_task` 和 `approval_audit_event` 合同表；M3.24 已补 H2 仓储测试验证任务持久化、审批决策和审计事件写入。
- 后续仍需接入真实用户、角色权限、审批 UI、复杂流程编排和工资锁账审计。
- 审批通过不自动写库存台账、不自动发放工资、不自动生成财务凭证。

### 6.15 轻量审批持久化契约

| 项目 | 契约 |
|---|---|
| 默认模式 | `zhubao.approval.repository=memory`，继续使用内存仓储，便于无数据库评审 |
| JDBC 模式 | `zhubao.approval.repository=jdbc`，写入 `approval_task` 和 `approval_audit_event` |
| MySQL profile | `ZHUBAO_APPROVAL_REPOSITORY=jdbc` |
| 任务主键 | `approval_code` 唯一，当前复用损耗审批单号 |
| 审计策略 | 每次 `save` 写入一条 `approval_audit_event`，用于保留创建、自动通过、人工通过或驳回痕迹 |
| 当前限制 | 只解决固定场景轻量审批持久化；不代表正式权限、复杂审批流、工资锁账或财务凭证完成 |

---

## 7. 库存与追溯字段

### 7.1 `InventoryBalance`

| 字段 | 说明 |
|---|---|
| `owner` | 库存所有者 |
| `batch` | 批次或来源单据 |
| `material` | 物料名称 |
| `location` | 仓库/库位/车间位置 |
| `status` | 库存状态 |
| `quantity` | 数量 |
| `weight` | 重量展示值 |
| `frozen` | 冻结数量或说明 |

### 7.2 `InventoryLedgerEntry`

| 字段 | 说明 |
|---|---|
| `time` | 发生时间 |
| `code` | 台账号 |
| `movement` | 台账动作 |
| `beforeBalance` | 变化前 |
| `afterBalance` | 变化后 |
| `sourceDocument` | 来源单据 |
| `operator` | 操作人 |

库存约束：

- 页面不提供直接修改余额入口。
- 所有余额必须能由台账明细汇总解释。
- 冻结、在制、返工、成品、发货必须是库存状态维度。

### 7.4 M3 库存持久化契约

当前已新增 `JdbcStockLedgerRepository` 作为库存台账第一个 M3 持久化切入点。

| 项目 | 契约 |
|---|---|
| 默认模式 | `zhubao.inventory.repository=memory`，不要求数据库，便于评审和演示 |
| MySQL 模式 | `zhubao.inventory.repository=jdbc`，由 `application-mysql.yml` 和 `ZHUBAO_DB_*` 环境变量提供连接 |
| 台账表 | `inv_stock_ledger`，只追加库存业务事件 |
| 余额表 | `inv_stock_balance`，由台账写入同步维护 |
| 维度键 | `dimension_key`，格式为 `ownerType|ownerCustomerId|customerId|motherOrderCode|workOrderCode|materialCode|warehouseCode|stockStatus` |
| 迁移脚本 | `V6__m3_inventory_jdbc_repository_contract.sql` |

持久化约束：

- `from` 和 `to` 维度只允许改变仓库和库存状态，不允许在一次库存转移中改变所有权、客户、母单、工单或物料。
- `dimension_key` 用于规避 MySQL 可空字段参与唯一索引时的重复余额问题。
- 库存台账号使用日期时间戳 + 序列，避免服务重启后简单日序列重复。

### 7.5 `ShipFinishedStockCommand`

`POST /api/business/operations/outbound-shipments` 使用该命令模型。该接口是 M3.15 成品出库写台账基线。

| 字段 | 说明 |
|---|---|
| `shipmentCode` | 出库单号，作为库存台账来源单据 |
| `motherOrderCode` | 母单号 |
| `workOrderCode` | 子工单或返工工单号 |
| `customerId` | 业务客户 ID |
| `ownerCustomerId` | 库存所有客户 ID，未传时默认等于 `customerId` |
| `materialCode` | 物料编码 |
| `fromWarehouseCode` | 来源成品仓/客户成品区 |
| `toWarehouseCode` | 目标已发货区或发货状态仓 |
| `deliveryDate` | 交货日期 |
| `deliveryCategory` | 交货类目，如成品、返修件、补发件、样品 |
| `quantity` | 本次出库数量，必须大于 0 |
| `quantityUnit` | 本次出库数量单位，已补入命令，必须与库存明细单位一致 |
| `weight` | 本次出库重量，必须大于 0 |
| `expressCompany` | 快递公司或物流方式 |
| `logisticsNo` | 物流单号或客户交接单号 |
| `paperExpressText` | 纸质出货表原始“快递”栏内容，批量导入时保留；已补入命令并写入台账备注 |
| `sourceRegisterType` | 来源登记表名称或类型，如 `交货情况登记表` |
| `sourceRegisterLineNo` | 原始出货登记行号，批量导入时使用；已补入命令并写入台账备注 |
| `sourceRegisterSequence` | 纸质表内序号 |
| `handler` | 经办人姓名快照 |
| `operator` | 出库操作人快照 |
| `evidenceCode` | 出库照片、物流单、交接单或附件编码 |
| `remark` | 备注 |

返回 `OutboundShipmentResult`：

| 字段 | 说明 |
|---|---|
| `shipmentCode` | 出库单号 |
| `workOrderCode` | 工单号 |
| `ledgerCode` | 库存台账号 |
| `fromWarehouseCode` | 来源成品仓/客户成品区 |
| `toWarehouseCode` | 目标已发货区或发货状态仓 |
| `fromStockStatus` | 来源库存状态，当前为 `FINISHED` |
| `toStockStatus` | 目标库存状态，当前为 `SHIPPED` |
| `quantity` | 已出库数量 |
| `weight` | 已出库重量 |
| `deliveryCategory` | 交货类目快照 |
| `expressCompany` | 快递公司或物流方式 |
| `logisticsNo` | 物流单号或客户交接单号 |
| `handler` | 经办人姓名快照 |
| `operator` | 出库操作人快照 |
| `evidenceCode` | 证据编码 |

实现约束：

- `from` 与 `to` 必须保持同一客户、母单、工单和物料维度。
- 成品出库只允许改变仓库和库存状态，当前基线为 `FINISHED -> SHIPPED`。
- 若来源成品库存不足，后端必须拒绝写台账。
- 经办人缺失不得确认出货；快递信息不完整时只能保存草稿，不能转为 `SHIPPED`。
- 出库不等同于退货；客户退货由 `POST /customer-returns` 独立命令反向写台账。
- 出货登记中的 `快递` 可能混写公司和单号，导入时先保留 `paperExpressText`，再拆分 `expressCompany/logisticsNo`。

### 7.6 `CustomerReturnCommand`

`POST /api/business/operations/customer-returns` 使用该命令模型。该接口是 M3.17 客户退货入库写台账基线。

| 字段 | 说明 |
|---|---|
| `returnCode` | 退货单号，作为库存台账来源单据 |
| `originalShipmentCode` | 原出库单号，用于追溯退货来源 |
| `motherOrderCode` | 母单号 |
| `workOrderCode` | 子工单或返工工单号 |
| `customerId` | 业务客户 ID |
| `ownerCustomerId` | 库存所有客户 ID，未传时默认等于 `customerId` |
| `materialCode` | 物料编码 |
| `fromWarehouseCode` | 来源已发货区或发货状态仓 |
| `toWarehouseCode` | 目标退货复核区，当前前端样例为 `RETURN-CHECK` |
| `quantity` | 本次退货数量，必须大于 0 |
| `weight` | 本次退货重量，必须大于 0 |
| `returnLogisticsNo` | 退回物流单号或客户交接单号 |
| `reason` | 退货原因快照 |
| `operator` | 入库操作人快照 |
| `evidenceCode` | 退回物流单、客户交接、照片或附件编码 |
| `remark` | 备注 |

返回 `CustomerReturnResult`：

| 字段 | 说明 |
|---|---|
| `returnCode` | 退货单号 |
| `originalShipmentCode` | 原出库单号 |
| `workOrderCode` | 工单号 |
| `ledgerCode` | 库存台账号 |
| `fromWarehouseCode` | 来源已发货区或发货状态仓 |
| `toWarehouseCode` | 目标退货复核区 |
| `fromStockStatus` | 来源库存状态，当前为 `SHIPPED` |
| `toStockStatus` | 目标库存状态，当前为 `FINISHED` |
| `quantity` | 已退货数量 |
| `weight` | 已退货重量 |
| `returnLogisticsNo` | 退回物流单号或客户交接单号 |
| `reason` | 退货原因快照 |
| `operator` | 入库操作人快照 |
| `evidenceCode` | 证据编码 |

实现约束：

- `from` 与 `to` 必须保持同一客户、母单、工单和物料维度。
- 客户退货只允许改变仓库和库存状态，当前基线为 `SHIPPED -> FINISHED`，仓库从 `SHIPPED-CUSTOMER` 转入 `RETURN-CHECK`。
- 若来源已发货库存不足，后端必须拒绝写台账。
- M3.17 只完成退货入库台账基线；M3.31 起由 `POST /after-sales-reviews` 决定复核后的放行、返工、报废或冻结分流。

### 7.7 `AfterSalesReviewCommand`

`POST /api/business/operations/after-sales-reviews` 使用该命令模型。该接口是 M3.31 售后复核分流写台账基线。

| 字段 | 说明 |
|---|---|
| `reviewCode` | 售后复核单号，作为库存台账来源单据 |
| `returnCode` | 退货单号 |
| `originalShipmentCode` | 原出库单号，用于追溯退货来源 |
| `motherOrderCode` | 母单号 |
| `workOrderCode` | 子工单或返工工单号 |
| `customerId` | 业务客户 ID |
| `ownerCustomerId` | 库存所有客户 ID，未传时默认等于 `customerId` |
| `materialCode` | 物料编码 |
| `fromWarehouseCode` | 来源退货复核区，当前前端样例为 `RETURN-CHECK` |
| `toWarehouseCode` | 目标库位，例如 `FINISHED-CUSTOMER`、`REWORK-LINE-01`、`SCRAP-CUSTOMER`、`FROZEN-QC` |
| `quantity` | 本次复核分流数量，必须大于 0 |
| `weight` | 本次复核分流重量，必须大于 0 |
| `disposition` | 复核结论：`RELEASE`、`REWORK`、`SCRAP`、`FREEZE` |
| `inspector` | 售后复核/质检人员快照 |
| `finding` | 复核发现和处理依据 |
| `evidenceCode` | 复核照片、视频、客户沟通、质检附件编码 |
| `remark` | 备注 |

返回 `AfterSalesReviewResult`：

| 字段 | 说明 |
|---|---|
| `reviewCode` | 售后复核单号 |
| `returnCode` | 退货单号 |
| `originalShipmentCode` | 原出库单号 |
| `workOrderCode` | 工单号 |
| `ledgerCode` | 库存台账号 |
| `fromWarehouseCode` | 来源退货复核区 |
| `toWarehouseCode` | 目标库位 |
| `fromStockStatus` | 来源库存状态，当前为 `FINISHED`，仓库为 `RETURN-CHECK` |
| `toStockStatus` | 目标库存状态，由 `disposition` 映射为 `FINISHED`、`REWORK`、`SCRAP` 或 `FROZEN` |
| `quantity` | 已复核分流数量 |
| `weight` | 已复核分流重量 |
| `disposition` | 复核结论 |
| `inspector` | 售后复核/质检人员快照 |
| `finding` | 复核发现和处理依据 |
| `evidenceCode` | 证据编码 |

实现约束：

- `from` 与 `to` 必须保持同一客户、母单、工单和物料维度。
- 来源固定为 `RETURN-CHECK` 的退货复核库存，库存状态为 `FINISHED`；它不代表已重新放行，只代表退回实物已回到厂内待复核区。
- `disposition=RELEASE` 映射到 `FINISHED`，`REWORK` 映射到 `REWORK`，`SCRAP` 映射到 `SCRAP`，`FREEZE` 映射到 `FROZEN`。
- 售后复核只写库存台账和证据快照，不做售后结算、客户门户、财务红冲或正式赔付。

---

## 8. PWA 字段

### 8.1 `MobileTask`

| 字段 | 说明 |
|---|---|
| `code` | 工单、质检单或任务编码 |
| `scene` | 移动作业场景 |
| `nextAction` | 下一步动作 |
| `required` | 必填信息 |
| `status` | 移动作业状态 |

PWA 一期边界：

- 支持扫码、报工、称重、质检、库存查询。
- 不做完整后台管理。
- 扫码失败允许手工输入编码并二次确认。
- 弱网离线草稿恢复后必须二次校验业务状态。

---

## 9. 文档与附件字段

### 9.1 `DocumentPackage`

| 字段 | 说明 |
|---|---|
| `code` | 文档包编码，例如 `DOC-QC-20260706-021` |
| `title` | 文档包标题 |
| `category` | 分类：业务单据、质检证据、软硬件联动、配置/构型 |
| `businessCode` | 关联业务对象编码 |
| `owner` | 当前负责人快照 |
| `purpose` | 文档包用途 |
| `requiredEvidence` | 必需证据数量 |
| `linkedEvidence` | 已关联证据数量 |
| `status` | 文档包状态 |
| `retentionPolicy` | 保留策略 |
| `lastUpdated` | 最后更新时间 |
| `tone` | 前端状态色 |

### 9.2 `EvidenceAttachment`

| 字段 | 说明 |
|---|---|
| `code` | 附件编码 |
| `documentCode` | 所属文档包编码 |
| `businessType` | 业务类型，例如来料母单、质检记录、硬件 POC |
| `businessCode` | 业务对象编码 |
| `fileName` | 原始文件名或归档文件名 |
| `mediaType` | MIME 类型 |
| `storageLocation` | 对象存储、NAS 或待上传位置 |
| `storagePolicy` | 生命周期或归档策略 |
| `source` | 来源：PWA 上传、质检台上传、现场录屏、配置评审等 |
| `status` | 附件状态 |
| `owner` | 附件责任人快照 |
| `checksum` | 校验和，审批锁定后不得变化 |
| `uploadedAt` | 上传或待上传时间 |
| `fallbackAction` | 失败兜底动作 |
| `tone` | 前端状态色 |

### 9.3 `DocumentAuditEvent`

| 字段 | 说明 |
|---|---|
| `time` | 审计时间 |
| `eventCode` | 审计事件编码 |
| `documentCode` | 文档包编码 |
| `action` | 上传、补传、归档、审批锁定等动作 |
| `operator` | 操作人快照 |
| `detail` | 事件说明 |
| `tone` | 前端状态色 |

### 9.4 `EvidenceAttachmentUploadResult`

| 字段 | 说明 |
|---|---|
| `code` | 新附件编码 |
| `documentCode` | 所属文档包编码 |
| `fileName` | 上传文件名 |
| `mediaType` | MIME 类型 |
| `size` | 文件字节数 |
| `storageLocation` | 当前存储位置，M3.37 默认对象内容仍可为 `local://evidence/{documentCode}/{attachmentCode}/{fileName}`，附件元数据可写入内存或 JDBC 仓储，后续对象内容迁移为 NAS/S3 签名访问 |
| `checksum` | `sha256` 校验和 |
| `uploadedAt` | 上传时间 |
| `previewUrl` | 预览内容地址 |

### 9.5 `AttachmentPreviewUrl`

| 字段 | 说明 |
|---|---|
| `code` | 附件编码 |
| `fileName` | 文件名 |
| `mediaType` | MIME 类型 |
| `url` | 预览 URL |
| `expiresAt` | 过期时间 |

文档附件约束：

- 当前 M3.37 已支持 multipart 上传到后端本地存储适配、checksum、审计事件、预览 URL 和内容读取；文档包、附件元数据和审计事件通过 `DocumentManagementRepository` 支持内存/JDBC 切换。
- `zhubao.documents.repository=jdbc` 时读取/写入 V10 合同表：`doc_package`、`doc_evidence_attachment`、`doc_audit_event`。
- 后续生产环境必须按部署条件接 NAS/S3 兼容存储，并通过签名 URL 控制预览和下载。
- 质检、称重、视频、配置审批证据必须能绑定业务对象、校验和、保留策略和审计事件。
- 审批完成后的配置和质检证据应锁定 checksum，后续修改必须产生新版本或新审计事件。

文档附件存储配置：

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `zhubao.documents.repository` | `memory` | 文档包、附件元数据和审计事件仓储；可设为 `jdbc` 读取/写入 `doc_*` 合同表 |
| `zhubao.documents.storage.provider` | `local` | 当前只落地本地文件适配；后续可扩展为 `nas`、`s3` 或其他实现 |
| `zhubao.documents.storage.local-root` | `target/zhubao-evidence` | 本地附件根目录，测试环境可覆盖为临时目录 |

---

## 10. M3 迁移要求

1. 将当前 `BusinessWorkspaceService` 内存样例迁移为 seed 数据。
2. 用 MySQL repository 替换当前 `InMemoryBusinessWorkspaceRepository`。
3. 将 `status`、`action`、`tone` 分离，避免前端展示状态污染业务状态。
4. 将重量字段拆为数值和单位，禁止长期使用字符串重量参与计算。
5. 将客户、物料、人员、工序单价、损耗率保存快照字段。
6. 将工作台 API 改为读取真实业务聚合，不再直接维护独立样例数据。

### 10.1 业务工作台 JDBC 读取基线

当前已新增 `JdbcBusinessWorkspaceRepository` 作为业务工作台从内存样例迁移到 MySQL seed 数据的第一步。

| 项目 | 契约 |
|---|---|
| 默认模式 | `zhubao.business.repository=memory`，继续使用内存 seed，便于无数据库评审 |
| JDBC 模式 | `zhubao.business.repository=jdbc`，读取 `biz_workspace_*` 合同表 |
| 快照选择 | 优先使用 `zhubao.business.snapshot-code`，未配置时读取 `biz_workspace_snapshot` 中最新业务日期 |
| MySQL profile | `ZHUBAO_BUSINESS_REPOSITORY=jdbc`、`ZHUBAO_BUSINESS_SNAPSHOT_CODE=SNAP-20260706-DAY` |
| 追溯时间线 | `biz_workspace_trace_event`，由 `V8__m3_business_workspace_jdbc_repository_contract.sql` 提供 |
| 评审 seed | `V9__m3_business_workspace_review_seed.sql`，提供当前 M2/M3 页面评审样例 |

约束：

- `biz_workspace_*` 是评审快照表，不是最终交易表。
- 页面仍不得直接修改库存余额，正式动作必须继续落到业务命令和库存台账。
- 后续应将读取来源从评审快照升级为来料、工单、质检、库存和附件的真实聚合查询。

### 10.2 业务操作写台账基线

当前已新增 `BusinessOperationService`，用于把页面操作落到库存台账。

| 项目 | 契约 |
|---|---|
| 来料确认入库 | `POST /api/business/operations/inbound-confirmations`，`RAW_WAITING` 入账 |
| 生产报工 | `POST /api/business/operations/production-reports`，`WIP -> SEMI_FINISHED` |
| 质检冻结 | `POST /api/business/operations/quality-freezes`，`SEMI_FINISHED -> FROZEN` |
| 返工派发 | `POST /api/business/operations/rework-dispatches`，`FROZEN -> REWORK` |
| 返工报工 | `POST /api/business/operations/rework-reports`，`REWORK -> SEMI_FINISHED` |
| 复检放行 | `POST /api/business/operations/quality-releases`，`SEMI_FINISHED -> FINISHED` |
| 成品出库 | `POST /api/business/operations/outbound-shipments`，`FINISHED -> SHIPPED` |
| 客户退货 | `POST /api/business/operations/customer-returns`，`SHIPPED -> FINISHED` |
| 售后复核 | `POST /api/business/operations/after-sales-reviews`，`FINISHED@RETURN-CHECK -> FINISHED/REWORK/SCRAP/FROZEN` |
| 损耗审批 | `POST /api/business/operations/loss-approvals`，返回损耗审批与工资试算快照，并创建轻量审批任务；不写库存台账 |
| 结构化母单 | `POST /api/business/operations/inbound-confirmations` 在库存台账成功后写 `biz_mother_order/biz_mother_order_item`；重复母单号先拒绝 |
| 结构化出库单 | `POST /api/business/operations/outbound-shipments` 在库存台账成功后写 `biz_outbound_order/biz_outbound_order_item`；重复出库单号先拒绝 |
| 单据查询 | `GET /api/business/documents/mother-orders/{code}`、`GET /api/business/documents/outbound-orders/{code}` |
| 轻量审批任务 | `GET /api/approvals/tasks`、`GET /api/approvals/tasks/pending`、`POST /api/approvals/tasks/{approvalCode}/decisions`，用于固定场景待审批、审批通过/驳回和锁定 |
| 后端入口 | `BusinessOperationController` |
| 权属边界 | `OwnerType.CUSTOMER`，保持客户来料和企业自有库存隔离 |
| 当前限制 | 母单/出库单已具备基础结构化快照和明细行，但尚未完成主数据匹配、完整状态流转、补录 UI、追溯聚合和单据更正；轻量审批可切换 JDBC 持久化，但正式权限、复杂审批流、审批 UI、工资锁账/发放、售后结算和对象存储仍需继续接入 |

这些接口证明一期闭环开始从“展示样例”进入“业务命令”。库存变化必须通过库存服务产生只追加台账；损耗审批、工资试算、称重交接和退货复核这类非库存动作也必须先建独立业务命令，再按职责接入审批服务、工资服务或证据服务。

### 10.3 业务单据结构化持久化切片

M3.81 新增 `BusinessDocumentRepository`，将来料确认入库和成品出库从“仅库存台账备注可追溯”推进到基础业务单据结构化快照：

| 项目 | 契约 |
|---|---|
| 默认模式 | `zhubao.business-document.repository=memory`，无数据库评审时使用内存仓储 |
| JDBC 模式 | `zhubao.business-document.repository=jdbc`，读取/写入 `biz_mother_order`、`biz_mother_order_item`、`biz_outbound_order`、`biz_outbound_order_item` |
| MySQL profile | `ZHUBAO_BUSINESS_DOCUMENT_REPOSITORY=jdbc`，默认随 `mysql` profile 启用 |
| 入库落库时机 | `BusinessOperationService.confirmInbound` 完成 `StockLedgerService.transfer` 后保存母单快照和一条物料明细 |
| 出库落库时机 | `BusinessOperationService.shipFinishedStock` 完成 `StockLedgerService.transfer` 后保存出库单快照和一条出库明细 |
| 防重复 | 调用库存台账前先检查母单号/出库单号是否已存在，存在则拒绝，避免重复库存入账 |
| 查询接口 | `GET /api/business/documents/mother-orders/{code}`、`GET /api/business/documents/outbound-orders/{code}` |

当前该切片优先解决登记表显式入账后的结构化追溯和测试证据；尚未覆盖拆单、工单生命周期、出库冻结、单据更正、主数据自动匹配和前端补录界面。

---

## 11. 硬件联动字段

### 11.1 `HardwarePocRecord`

| 字段 | 说明 |
|---|---|
| `code` | POC 记录号，例如 `POC-SCAN-20260706-001` |
| `deviceCode` | 设备编码，例如 `PWA-CAM-01` |
| `scene` | 验证场景，例如 PWA 弱光扫码 |
| `testWindow` | 验证日期和时间段 |
| `result` | 验证结果：通过、有条件通过、阻断 |
| `evidence` | 现场证据摘要，后续应关联附件或日志 |
| `blocker` | 阻断项或剩余风险 |
| `owner` | 当前责任人快照 |
| `nextAction` | 下一步动作 |

硬件 POC 约束：

- 没有 POC 记录的硬件能力不能承诺为一期强集成。
- 结果为阻断时必须有人工兜底或明确排期。
- POC 记录必须能关联设备、业务场景和证据附件。
- 电子秤、NVR、PWA 扫码、标签打印至少各保留一条现场验证记录。

---

## 11. 配置基线字段

### 11.1 `SystemParameter`

| 字段 | 说明 |
|---|---|
| `key` | 参数键，例如 `inventory.negative.allowed` |
| `value` | 当前基线值 |
| `category` | 参数分类：编码、称重、损耗、库存等 |
| `level` | 配置等级：`C1` 到 `C5` |
| `description` | 参数说明 |
| `changeControl` | 变更要求，例如需审批、记录日志 |
| `effectiveScope` | 影响范围 |

### 11.2 `CodeRule`

| 字段 | 说明 |
|---|---|
| `objectType` | 编码对象：母单、子工单、库存台账等 |
| `format` | 编码格式 |
| `example` | 示例编码 |
| `uniqueness` | 唯一性规则 |
| `changePolicy` | 冻结或变更策略 |

### 11.3 `RolePermissionBaseline`

| 字段 | 说明 |
|---|---|
| `role` | 角色名称 |
| `dataScope` | 数据范围 |
| `permissions` | 基础权限列表 |
| `sensitiveActions` | 必须审计的敏感动作 |

### 11.4 `ProcessTemplate`

| 字段 | 说明 |
|---|---|
| `code` | 工序模板编码 |
| `name` | 模板名称 |
| `materialScope` | 适用物料 |
| `steps` | 工序步骤 |
| `lossRule` | 损耗规则 |
| `wageRule` | 计件工资试算规则 |
| `qualityRule` | 质检规则 |
| `snapshotFields` | 工单创建时必须快照的字段 |

### 11.5 `WarehouseState`

| 字段 | 说明 |
|---|---|
| `code` | 仓库或状态编码 |
| `name` | 名称 |
| `ownerBoundary` | 所有权边界，当前重点为客户所有 |
| `stockStatus` | 库存状态 |
| `transitionRule` | 状态流转规则 |
| `negativeAllowed` | 是否允许负库存，一期默认 false |

### 11.6 `VideoParameter`

| 字段 | 说明 |
|---|---|
| `key` | 视频参数键 |
| `value` | 参数值 |
| `scene` | 应用场景 |
| `fallback` | 失败兜底方式 |
| `retention` | 保存周期或保留策略 |
| `changeControl` | 变更要求 |

### 11.7 `ConfigurationChangeRequest`

| 字段 | 说明 |
|---|---|
| `code` | 配置变更单号 |
| `targetKey` | 被变更的参数、编码规则或配置对象 |
| `targetName` | 业务名称 |
| `changeType` | 变更类型，例如参数调整、规则变更 |
| `beforeValue` | 变更前值 |
| `afterValue` | 变更后值 |
| `reason` | 变更原因 |
| `applicant` | 申请人快照 |
| `approver` | 审批人或审批角色快照 |
| `status` | 待审批、已通过、已驳回等 |
| `riskLevel` | 风险等级 |
| `submittedAt` | 提交时间 |

### 11.8 `ConfigurationAuditEvent`

| 字段 | 说明 |
|---|---|
| `eventId` | 审计事件号 |
| `targetKey` | 审计对象 |
| `operation` | 操作：审批通过、审批驳回、导出基线等 |
| `operator` | 操作人快照 |
| `occurredAt` | 发生时间 |
| `result` | 操作结果 |
| `evidence` | 关联证据 |
| `traceId` | 审计追踪号 |

配置基线迁移要求：

1. M3 使用 `cfg_*` 表承载配置基线 seed 数据。
2. 参数编辑必须在权限、审批、审计日志完成后开放。
3. C1 配置变更必须保留历史，不能直接覆盖旧值。
4. 工序模板变更只影响新工单，历史工单使用快照字段。
5. 视频和硬件参数必须支持失败兜底，不允许让硬件集成阻断业务闭环。
6. 配置变更必须先形成 `ConfigurationChangeRequest`，审批完成后才能写入配置表。
7. 每一次审批、驳回、导出、修改都必须写入 `ConfigurationAuditEvent` 并关联 `traceId`。

---

## 12. M3.33 轻量权限守卫契约

当前版本尚未接入生产登录和数据范围过滤，但敏感 POST 已接入后端轻量角色守卫，菜单树已返回到前端并在登录会话下驱动侧栏过滤，避免评审原型继续处于“所有人都能提交关键动作”的状态。

### 12.1 请求头

| Header | 说明 | 默认值 | 示例 |
|---|---|---|---|
| `X-Zhubao-Operator` | 操作人快照，用于后续审计上下文 | `system-review` | `车间组长 马杰` |
| `X-Zhubao-Role` | 当前操作角色 | `ADMIN` | `WORKSHOP_LEADER` |
| `X-Zhubao-Data-Scope` | 数据范围提示，当前只解析不做数据过滤 | `ALL` | `TEAM` |

说明：
- 默认 `ADMIN` 是为了兼容现有评审脚本和自动化测试，不代表生产环境允许匿名管理员。
- 后续接真实登录后，应由登录态、JWT 或网关注入操作人、角色和数据范围，前端不得自行伪造。
- 角色或数据范围无法识别时返回 `403`。

### 12.2 受保护接口

| 接口 | 权限码 | 允许角色 |
|---|---|---|
| `POST /api/inventory/ledger/transfers` | `INVENTORY_LEDGER_TRANSFER` | `ADMIN`、`GENERAL_MANAGER` |
| `POST /api/business/operations/inbound-confirmations` | `BUSINESS_INBOUND_CONFIRM` | `ADMIN`、`GENERAL_MANAGER`、`RECEIVER`、`WAREHOUSE_KEEPER` |
| `POST /api/business/operations/production-reports` | `BUSINESS_PRODUCTION_REPORT` | `ADMIN`、`GENERAL_MANAGER`、`OPERATOR`、`WORKSHOP_LEADER` |
| `POST /api/business/operations/quality-freezes` | `BUSINESS_QUALITY_FREEZE` | `ADMIN`、`GENERAL_MANAGER`、`QUALITY_INSPECTOR`、`WORKSHOP_LEADER` |
| `POST /api/business/operations/rework-dispatches` | `BUSINESS_REWORK_DISPATCH` | `ADMIN`、`GENERAL_MANAGER`、`QUALITY_INSPECTOR`、`WORKSHOP_LEADER` |
| `POST /api/business/operations/rework-reports` | `BUSINESS_REWORK_REPORT` | `ADMIN`、`GENERAL_MANAGER`、`OPERATOR`、`WORKSHOP_LEADER` |
| `POST /api/business/operations/quality-releases` | `BUSINESS_QUALITY_RELEASE` | `ADMIN`、`GENERAL_MANAGER`、`QUALITY_INSPECTOR`、`WORKSHOP_LEADER` |
| `POST /api/business/operations/outbound-shipments` | `BUSINESS_OUTBOUND_SHIPMENT` | `ADMIN`、`GENERAL_MANAGER`、`WAREHOUSE_KEEPER`、`SALES` |
| `POST /api/business/operations/customer-returns` | `BUSINESS_CUSTOMER_RETURN` | `ADMIN`、`GENERAL_MANAGER`、`WAREHOUSE_KEEPER`、`SALES`、`QUALITY_INSPECTOR` |
| `POST /api/business/operations/after-sales-reviews` | `BUSINESS_AFTER_SALES_REVIEW` | `ADMIN`、`GENERAL_MANAGER`、`QUALITY_INSPECTOR`、`WORKSHOP_LEADER` |
| `POST /api/business/operations/loss-approvals` | `BUSINESS_LOSS_APPROVAL` | `ADMIN`、`GENERAL_MANAGER`、`WORKSHOP_LEADER` |
| `POST /api/approvals/tasks/{approvalCode}/decisions` | `APPROVAL_DECISION` | `ADMIN`、`GENERAL_MANAGER`、`WORKSHOP_LEADER`、`FINANCE_STATISTICIAN` |
| `POST /api/documents/attachments/uploads` | `DOCUMENT_ATTACHMENT_UPLOAD` | `ADMIN`、`GENERAL_MANAGER`、`RECEIVER`、`WAREHOUSE_KEEPER`、`QUALITY_INSPECTOR`、`WORKSHOP_LEADER` |
| `POST /api/video-clip-tasks`、`POST /api/video-clip-tasks/{code}/retry`、`POST /api/video-clip-tasks/{code}/manual-upload` | `VIDEO_TASK_OPERATE` | `ADMIN`、`GENERAL_MANAGER`、`WAREHOUSE_KEEPER`、`WORKSHOP_LEADER` |

### 12.3 错误响应

角色越权时返回：

```json
{
  "success": false,
  "code": 40301,
  "message": "角色 OPERATOR 无权执行 损耗审批试算",
  "data": null
}
```

HTTP 状态码必须是 `403`。M3.33 已补 smoke 测试验证 `OPERATOR` 角色调用损耗审批会被拒绝。

### 12.4 当前限制

- M3.63 已接入登录会话 token，M3.84/M3.85 已接入登录审计事件和仓储边界，M3.87 已接入账号仓储和 PBKDF2 密码哈希边界，M3.88 已接入失败次数与临时锁定；生产账号初始化/停用/解锁、JWT/SSO、会话续期和审计防篡改仍未完成。
- 尚未按客户、部门、班组做数据范围过滤。
- 尚未把权限矩阵持久化到数据库或配置基线变更流程。
- 尚未把审批人和权限守卫结果统一写入完整审计事件。

---

## 13. M3.63/M3.87/M3.88 轻量登录会话 API 与账号仓储

M3.63 新增轻量登录会话基线，用于让前端配置基线页和敏感 POST 权限守卫从“请求头模拟”推进到“Bearer token 优先”。M3.87 将账号读取和密码校验从硬编码演示账号推进到 `AuthUserAccountRepository` 与 PBKDF2 密码哈希；M3.88 在同一仓储边界上记录失败次数和临时锁定。默认 seed 仍是评审账号，尚不等于完整生产用户中心。

### 13.1 `POST /api/auth/login`

请求：

| 字段 | 说明 |
|---|---|
| `username` | 登录账号用户名，后端会归一化为小写查找 |
| `password` | 明文登录密码，仅用于本次校验，不进入响应或审计日志 |

默认评审 seed 账号：

| 用户名 | 密码 | 角色 | 数据范围 |
|---|---|---|---|
| `admin` | `admin123` | `ADMIN` | `ALL` |
| `leader` | `leader123` | `WORKSHOP_LEADER` | `TEAM` |
| `qc` | `qc123` | `QUALITY_INSPECTOR` | `DEPARTMENT` |
| `warehouse` | `warehouse123` | `WAREHOUSE_KEEPER` | `DEPARTMENT` |
| `operator` | `operator123` | `OPERATOR` | `SELF` |

本地默认使用内存账号仓储，启动时以 `PasswordHasher.hash()` 生成 PBKDF2 哈希；MySQL profile 默认使用 `auth_user_account` 表，`V16__m3_auth_user_account_contract.sql` 写入同一组评审 seed 账号及其 PBKDF2 哈希。上线前必须替换密码、停用或删除评审账号。

登录失败锁定规则：

- 已启用账号连续密码错误达到 5 次后，锁定 15 分钟。
- 锁定期间即使密码正确也返回 `403`，消息为“账号已锁定，请稍后再试”。
- 成功登录会清零失败次数和锁定到期时间。
- 未知账号和停用账号仍返回通用“用户名或密码错误”，避免通过错误提示枚举账号。

返回 `AuthSession`：

| 字段 | 说明 |
|---|---|
| `token` | Bearer token，前端保存到 `localStorage` 并自动附加到后续 API 请求 |
| `username` | 登录用户名 |
| `operator` | 操作人快照 |
| `role` / `roleLabel` | 角色编码和中文名 |
| `dataScope` / `dataScopeLabel` | 数据范围编码和中文名 |
| `expiresAt` | 会话过期时间展示值 |
| `demoAccount` | 是否评审/演示账号，来自账号仓储记录；生产账号应为 `false` |
| `menuCodes` | 当前角色可见菜单编码，使用前端模块 tab key，如 `dashboard`、`inbound`、`approvals`、`sample` |
| `menuTree` | 当前角色可见菜单树，元素结构见 13.4；包含菜单编码、名称、模块说明、路由和按钮权限编码 |

### 13.2 其他认证接口

| API | 说明 |
|---|---|
| `GET /api/auth/session` | 使用 `Authorization: Bearer <token>` 读取当前会话 |
| `POST /api/auth/logout` | 注销当前 token；前端无论后端是否可达都清理本地 token |
| `GET /api/auth/demo-users` | 返回可用于评审的演示账号列表 |
| `GET /api/auth/audit-events` | 返回最近登录会话审计事件，当前最多保留 50 条内存事件 |

### 13.3 登录会话审计

M3.84 新增 `AuthAuditEvent`，用于记录登录成功、登录失败、会话读取、退出和无效 token 解析。默认本地为内存队列，重启后丢失；M3.85 起可通过 JDBC 仓储写入 `auth_audit_event`，但仍不等于审计防篡改或安全日志平台。

`AuthAuditEvent`：

| 字段 | 说明 |
|---|---|
| `eventId` | 审计事件号，当前格式为 `AUTH-AUD-...` |
| `eventType` | 事件类型：`LOGIN`、`SESSION_READ`、`LOGOUT`、`SESSION_RESOLVE` |
| `username` | 登录用户名；未知 token 可为空 |
| `operator` | 操作人快照；失败登录或未知 token 可为空 |
| `role` | 角色编码；失败登录或未知 token 可为空 |
| `result` | `SUCCESS` 或 `DENIED` |
| `reason` | 审计原因，例如登录成功、用户名或密码错误、会话不存在 |
| `occurredAt` | 审计发生时间展示值 |
| `sessionTokenSuffix` | token 尾号，只用于排查；不得返回完整 token 或密码 |

M3.85 新增 `AuthAuditRepository` 仓储边界：

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `zhubao.auth.audit.repository` | `memory` | 登录审计仓储；可设为 `jdbc` 写入 `auth_audit_event` |
| `ZHUBAO_AUTH_AUDIT_REPOSITORY` | `jdbc` in mysql profile | MySQL profile 下的环境变量开关 |

`V15__m3_auth_audit_contract.sql` 冻结 `auth_audit_event` 合同表，字段包括 `event_id`、`event_type`、`username`、`operator`、`role`、`result`、`reason`、`occurred_at` 和 `session_token_suffix`。该表不保存密码、完整 token 或客户端密钥。

M3.87 新增 `AuthUserAccountRepository` 仓储边界：

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `zhubao.auth.users.repository` | `memory` | 登录账号仓储；本地默认内存 seed |
| `ZHUBAO_AUTH_USERS_REPOSITORY` | `jdbc` in mysql profile | MySQL profile 下的环境变量开关 |

`V16__m3_auth_user_account_contract.sql` 冻结 `auth_user_account` 基础合同表，字段包括 `username`、`password_hash`、`operator`、`role_code`、`data_scope`、`enabled`、`demo_account`、`password_changed_at`、`created_time` 和 `updated_time`。`password_hash` 格式为 `pbkdf2_sha256$iterations$salt$hash`，当前由 `PasswordHasher` 使用 `PBKDF2WithHmacSHA256` 校验。

`V17__m3_auth_user_login_lockout_contract.sql` 在 `auth_user_account` 上新增：

| 字段 | 说明 |
|---|---|
| `failed_login_count` | 连续失败登录次数，成功登录后清零 |
| `locked_until` | 临时锁定到期时间；为空表示未锁定 |
| `last_login_at` | 最近成功登录时间 |
| `last_failed_login_at` | 最近失败登录时间 |

当前表仍未覆盖密码轮换策略、生产账号初始化审批、管理员解锁、SSO/JWT 或菜单持久化。

### 13.4 菜单树接口

`GET /api/security/menu-tree` 返回当前角色可见菜单树。认证优先级与 `GET /api/security/current-context` 一致：优先读取 Bearer token，无 token 时使用 `X-Zhubao-*` 请求头兜底。

`NavigationMenuItem`：

| 字段 | 说明 |
|---|---|
| `code` | 菜单编码，当前与前端 `App.vue` tab key 对齐 |
| `label` | 菜单中文名 |
| `module` | 菜单业务分组说明 |
| `route` | 预留路由标识，当前仍用于契约展示 |
| `buttonCodes` | 该菜单关联的敏感按钮权限编码，如 `REGISTER_IMPORT_REVIEW`、`BUSINESS_INBOUND_CONFIRM` |
| `children` | 子菜单列表；当前 M3.83 先返回一层菜单，保留树形结构 |

M3.83 当前角色菜单编码基线：

| 角色 | 可见菜单 |
|---|---|
| `ADMIN` / `GENERAL_MANAGER` | `dashboard`、`inbound`、`production`、`quality`、`inventory`、`approvals`、`configuration`、`documents`、`sample`、`hardware`、`operations`、`mobile` |
| `SALES` | `dashboard`、`inbound`、`inventory`、`documents`、`sample` |
| `RECEIVER` | `dashboard`、`inbound`、`documents`、`hardware`、`mobile` |
| `WORKSHOP_LEADER` | `dashboard`、`production`、`quality`、`approvals`、`sample`、`hardware`、`operations`、`mobile` |
| `OPERATOR` | `dashboard`、`production`、`mobile` |
| `QUALITY_INSPECTOR` | `dashboard`、`quality`、`inventory`、`documents`、`mobile` |
| `WAREHOUSE_KEEPER` | `dashboard`、`inbound`、`inventory`、`documents`、`sample`、`hardware`、`operations`、`mobile` |
| `FINANCE_STATISTICIAN` | `dashboard`、`approvals`、`configuration` |

菜单树当前先作为后端可验证契约和配置基线页展示依据；M3.86 起前端登录会话会按 `menuCodes/menuTree` 过滤侧栏导航，未登录、请求头兜底或 API 不可用时仍保留全量评审导航。

### 13.5 当前限制

- token 保存在内存，服务重启即失效。
- M3.88 已接入账号仓储、PBKDF2 密码哈希和失败锁定边界，但默认 seed 仍是评审账号，不用于生产。
- 尚未接入生产账号初始化/停用/管理员解锁流程、刷新 token、SSO/JWT、菜单树持久化和不可篡改审计策略。
- M3.83 菜单树仍是后端内存基线服务，不等于生产角色表、菜单表或按钮表。
- M3.85 登录审计可切换内存/JDBC 仓储，但仍不等于生产安全日志平台、归档保全或审计防篡改。
- M3.86 前端侧栏会按登录会话 `menuCodes/menuTree` 做导航过滤；未登录、请求头兜底或 API 不可用时仍保留全量评审导航，不等于生产级菜单授权完成。
- `dataScope` 当前只进入上下文展示和后续设计约束，GET 工作台数据尚未按范围过滤。

---

## 14. M3.52/M3.63/M3.83/M3.86/M3.87/M3.88 当前权限上下文接口

`GET /api/security/current-context` 用于把当前身份、角色、数据范围、可执行敏感动作和授权菜单树显式暴露给前端配置基线页。M3.63 起优先读取 `Authorization: Bearer <token>` 登录会话；无 token 时继续使用请求头兜底，兼容评审脚本和自动化测试。M3.83 起该接口同步返回 `menuCodes` 和 `menuTree`。M3.86 起前端 `App` 会恢复登录会话并按 `menuCodes/menuTree` 过滤侧栏模块，登录/退出通过 `zhubao-auth-session-changed` 事件即时刷新导航。M3.87 起登录会话身份来自账号仓储和密码哈希校验结果；M3.88 起登录失败锁定也由账号仓储维护。

### 14.1 请求头

认证优先级：

1. `Authorization: Bearer <token>`。
2. M3.33 轻量权限守卫请求头兜底。

| Header | 说明 |
|---|---|
| `Authorization` | 登录会话 token，格式 `Bearer <token>` |
| `X-Zhubao-Operator` | 操作人快照 |
| `X-Zhubao-Role` | 当前角色编码 |
| `X-Zhubao-Data-Scope` | 当前数据范围编码 |

### 14.2 返回字段

| 字段 | 说明 |
|---|---|
| `operator` | 当前操作人快照 |
| `role` | 角色编码，例如 `ADMIN`、`WAREHOUSE_KEEPER` |
| `roleLabel` | 角色中文名 |
| `dataScope` | 数据范围编码，例如 `ALL`、`TEAM`、`CUSTOMER_RANGE` |
| `dataScopeLabel` | 数据范围中文名 |
| `dataBoundary` | 当前数据范围在真实登录接入后的业务边界说明 |
| `headerBased` | 是否来自请求头；登录会话为 `false`，请求头兜底为 `true` |
| `allowedPermissions` | 当前角色可执行的敏感动作权限列表 |
| `sensitiveActionCodes` | 仅权限编码列表，便于前端做快速展示或按钮边界提示 |
| `menuCodes` | 当前角色可见菜单编码列表 |
| `menuTree` | 当前角色可见菜单树，元素结构同 13.4 `NavigationMenuItem` |
| `limitationNotes` | 当前认证、数据范围、菜单权限和审批锁账限制 |
| `headerContract` | 请求头契约，包含操作人、角色、数据范围三个 Header 名称 |

### 14.3 示例

```json
{
  "success": true,
  "code": 0,
  "message": "ok",
  "data": {
    "operator": "库管员 何琳",
    "role": "WAREHOUSE_KEEPER",
    "roleLabel": "库管员",
    "dataScope": "TEAM",
    "dataScopeLabel": "本班组/工位",
    "dataBoundary": "后续真实登录接入后，只应读取同班组、同工位或当前负责队列内的业务对象。",
    "headerBased": true,
    "allowedPermissions": [
      { "code": "BUSINESS_INBOUND_CONFIRM", "label": "来料确认入库" }
    ],
    "sensitiveActionCodes": ["BUSINESS_INBOUND_CONFIRM"],
    "menuCodes": ["dashboard", "inbound", "inventory", "documents", "sample", "hardware", "operations", "mobile"],
    "menuTree": [
      {
        "code": "inbound",
        "label": "来料母单",
        "module": "收货 / 拆包",
        "route": "/inbound",
        "buttonCodes": ["BUSINESS_INBOUND_CONFIRM", "DOCUMENT_ATTACHMENT_UPLOAD"],
        "children": []
      }
    ],
    "limitationNotes": [
      "当前身份来自请求头，可用于评审和自动化测试兜底。"
    ],
    "headerContract": {
      "operatorHeader": "X-Zhubao-Operator",
      "roleHeader": "X-Zhubao-Role",
      "dataScopeHeader": "X-Zhubao-Data-Scope"
    }
  }
}
```

### 14.4 当前限制

- GET 工作台接口尚未按 `dataScope` 做真实过滤，配置基线页必须明确提示该限制。
- 菜单和按钮权限已由后端返回 M3.83 内存基线菜单树；M3.86 前端登录会话导航已按 `menuCodes/menuTree` 过滤，但未登录和请求头兜底仍展示全量评审导航。
- 尚未建立生产持久化菜单表、角色菜单关联表和按钮权限配置表。
- M3.88 已建立账号仓储、密码哈希和失败锁定边界，但生产账号初始化、停用、管理员解锁和 SSO/JWT 仍待接入。
- 当前接口不返回密码、客户范围清单或任何生产鉴权密钥。
- 后续接入正式用户中心后，应移除匿名管理员请求头兜底或仅在测试环境启用。
