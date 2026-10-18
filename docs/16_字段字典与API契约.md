# 来料加工进销存与生产管理系统 字段字典与 API 契约

**文档版本**：V2.1
**更新日期**：2026-10-13
**文档定位**：现行 API 调用、字段校验、权限和存储边界的统一入口

## 1. 使用范围

本文件记录已在源码注册的接口及有效字段。当前启用范围和架构以[项目核心](./00_项目核心.md)为准；仅在修改具体接口时读取对应章节。已退出的配置基线、样例场景和视频截取原型见[历史 API 片段](./archive/contracts/2026-10-13_弃用原型API片段.md)，不再注册或作为现行契约。

业务工作台默认只读正式单据与台账；评审快照仅在显式演示模式读取，不与正式数据合并，数据来源见“持久化与数据来源”。接口存在、测试通过或仓储可切换均不代表生产实际使用。现场采集、拆单、工序交接、质检、网关、财务等当前入口统一列于末章“现行端点索引”，完整请求与响应类型可由对应控制器追溯。

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

登记表导入分为预览、结构化批次确认、逐行复核、入账计划和显式入账。预览、确认与复核不写库存；仅独立入账命令调用业务服务，阻断行与重复入账拒绝。设备产量独立保存，机器绑定由人工复核后执行。

业务操作接口前缀：

```http
/api/business/operations
```

| 接口 | 页面 | 用途 |
|---|---|---|
| `POST /inbound-confirmations` | 来料母单 | 确认客户来料入库，写入库存台账和客户所有原料库存余额 |
| `POST /process-stations` | 生产 | 现场工序交付。旧 `POST /production-reports` 已退出 |
| `POST /quality-checks` 及 `/rework`、`/rework-reports`、`/rechecks`、`/releases` | 质检 | 条款判定、返工、复检和放行。旧 `/quality-freezes`、`/rework-dispatches`、`/rework-reports`、`/quality-releases` 已退出 |
| `POST /outbound-shipments` | 库存追溯 | 确认成品出库。必须带 `requestId`，同一次提交并可回放 |
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
| `GET /api/machine-outputs?limit=20` | 生产统计 / 样例库 | 查询最近设备产量记录，默认最多 20 条 |
| `GET /api/machine-outputs/summary?limit=100` | 生产派工 / 生产统计 | 汇总最近设备产量记录，返回总记录数、总产量、按机器/日期分组、机器档案待匹配数和签名附件待补数 |
| `GET /api/machine-outputs/machine-match-suggestions?limit=100` | 生产派工 / 机器档案复核 | 按现场机器编号聚合未绑定 `machineId` 的设备产量记录，返回候选机器档案 ID、未匹配数量和人工复核动作；只读建议，不自动绑定 |
| `GET /api/machine-outputs/import-batches/{importBatchNo}` | 样例库/登记表入账结果 | 查询某个登记表导入批次生成的设备产量记录 |
| `POST /api/machine-outputs` | 生产统计 / 设备产量补录 | 手工或低成本设备适配提交设备产量记录，受 `MACHINE_OUTPUT_SUBMIT` 权限控制 |

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

`MachineOutputRecord` 返回上述字段并补充 `createdBy`、`createdAt`。雕刻登记表显式入账先调用生产报工写库存台账，再保存同批次设备产量记录；统计基于最近记录聚合。

`MachineOutputSummary`：

| 字段 | 说明 |
|---|---|
| `totalRecords` | 本次汇总纳入的设备产量记录数 |
| `totalQuantity` / `quantityUnit` | 汇总产量和单位；单位不一致时返回 `混合` |
| `byMachine[]` | 按 `machineNoSnapshot` 分组的产量和记录数，供生产页查看机器产能 |
| `byWorkDate[]` | 按 `workDate` 分组的产量和记录数，供日报入口使用 |
| `unmatchedMachineRecords` | `machineId` 为空的记录数，表示还未匹配机器档案 |
| `signatureMissingRecords` | `signatureMediaId` 为空的记录数，表示仍需补手写签名或附件 |

`MachineOutputMachineMatchSuggestion`：
| 字段 | 说明 |
|---|---|
| `machineNoSnapshot` | 现场机器编号快照，来自登记表或手工补录 |
| `productCategory` | 同组记录中优先取到的品类快照 |
| `candidateMachineIds[]` | 同一机器编号已有已绑定记录时返回的候选机器档案 ID；为空表示需要先建档或人工确认 |
| `matchedRecords` / `unmatchedRecords` / `totalRecords` | 同一机器编号下已绑定、未绑定和总记录数 |
| `latestWorkDate` | 同组记录中最新产量日期，用于人工复核排序 |
| `sourceRegisterType` / `sourceMachineLineNo` | 原始登记表类型和机器行号快照，方便回看低成本硬件/纸质登记来源 |
| `readiness` | `READY_TO_BIND`、`NEEDS_MASTER_DATA` 或 `CONFLICT`；当前只作为复核建议状态 |
| `suggestedAction` | 下一步人工处理建议，不代表后端已经执行绑定 |

机器匹配建议只读，不自动绑定。当前已有机器档案与显式绑定命令；签名附件、批量多编号绑定和厂商协议接入仍需后续完善。

### 2.1 登记表导入模板字段

`RegisterImportTemplate` 约束当前 Excel 导入器的字段解析与校验，不直接写业务数据。

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

前端复核台约束：

- `GET /api/register-import/batches?limit=10` 用于显示最近确认批次，后端返回批次头和行级快照；`limit` 在服务端限制为 1-50。
- `GET /api/register-import/batches/{importBatchNo}` 用于点击批次后读取单批次详情，展示 `rows[].normalizedFields` 和 `rows[].messages`。
- 后端 API 不可用时显示读取失败，不回填历史评审批次，也不将未保存数据写成已入账。
- `status=CONFIRMED` 在页面显示为“待入账映射”，不得被解释为已经生成母单、报工、设备产量或出库单。
- `status=POSTED` 在页面显示为“已入业务单据”，重复调用显式入账命令必须被后端拒绝。
- 入账映射必须走 `POST /posting-commands` 独立命令，不能在批次查询或 posting plan 接口中隐式写库存台账。
- 的复核补录只允许更新未入账批次行的 `normalizedFields`；`importBatchNo`、`sourceRegisterLineNo`、`sourceRegisterSequence` 等来源审计字段禁止通过补录接口覆盖。

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
| `GET /tasks` | 首页/审批工作台/质检返工 | 查询全部轻量审批任务，保留历史账号审批任务 |
| `GET /tasks/pending` | 首页/审批工作台/质检返工 | 查询待审批任务，保留历史账号审批任务 |
| `GET /tasks/{approvalCode}` | 审批工作台/质检返工 | 查询单个审批任务 |
| `POST /tasks/{approvalCode}/decisions` | 审批工作台/质检返工 | 审批通过或驳回，并锁定审批快照 |

硬件联动接口前缀：

```http
/api/hardware
```

| 接口 | 页面 | 用途 |
|---|---|---|
| `GET /devices` | 软硬件联动 | 返回设备兼容矩阵 |
| `GET /integration-tasks` | 软硬件联动 | 返回硬件调用任务和健康度 |
| `GET /poc-records` | 软硬件联动 | 返回现场 POC 验证记录 |
| `GET /poc-evidence` | 软硬件联动 | 返回 POC 记录与附件证据的只读关联摘要 |
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
| `GET /storage/integrity` | 文档附件 | 仅公司范围管理员／总经理可读取原件与元数据的只读对账结果，列出缺原件、SHA-256 不符、无元数据原件和上传中断临时文件；不清理文件 |
| `POST /attachments/{code}/reconciliations` | 文档附件 | 仅公司范围管理员／总经理可核对「待核对」附件的原件 SHA-256，补记核对审计并启用；原件缺失或校验不符时拒绝 |
| `POST /attachments/uploads` | 文档附件 | multipart 上传附件，生成附件元数据、checksum、审计事件和预览 URL |
| `GET /attachments/{code}/preview-url?purpose=<purpose>` | 文档附件 | 登录后按附件单据校验业务数据范围，返回短期签名地址；默认 `preview`，下载须另行申请 `download`，响应禁止缓存 |
| `GET /attachments/{code}/content?expiresAt=<epoch>&purpose=<purpose>&resolution=original&token=<hmac>` | 文档附件 | 签名绑定附件、期限、用途与清晰度；仅文件头与元数据相符的 JPEG、PNG、MP4、WebM 可内联，其余按原文件名下载；响应 `no-store`、`nosniff`。当前仍整文件读取，Range 与派生图留待 A2 后续增量 |

附件上传若在原件发布后遇到元数据或审计写入结果不确定，错误会包含附件编号并要求核对；原件保留，不能把错误解释为“文件不存在，可以直接重传”。本地原件先写同目录临时文件并原子发布。下线先保存「已下线」并立即拒绝访问，原件保留供受控恢复。每日默认 03:00 执行只读完整性检查，异常数量写入服务日志；管理员可随时读取上述明细接口。时间可用 `zhubao.documents.integrity.cron` 配置。

手机采集仅接受 JPG、PNG、MP4、WebM；电脑通用上传另接受 PDF。两条入口均校验声明类型与文件头，按 `zhubao.documents.capture.photo-max-bytes`（默认 8 MiB，含 PDF）和 `video-max-bytes`（默认 50 MiB）限制实际写入字节数；电脑入口使用流式读取。文件头检查不代替完整解码或病毒扫描。

原件发布后先登记「待核对」元数据，审计写入成功后改为「已上传」。待核对期间预览、下载、业务绑定及手机采集列表均不开放；重启后仍保持该状态。管理员可用核对接口恢复校验和一致的附件。元数据首次写入结果不确定时，原件可能没有元数据；完整性报告会列为 `UNREGISTERED_ORIGINAL`，需人工判定归属，不能自动删除或直接重传。

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
| `weight` | 评审工作台重量展示值；正式数量计算使用业务命令中的数值字段与单位 |
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

`POST /api/business/operations/inbound-confirmations` 使用该命令模型。该接口执行真实业务动作并保存正式单据与库存台账。

| 字段 | 说明 |
|---|---|
| `motherOrderCode` | 母单号，作为库存台账来源单据 |
| `customerId` | 业务客户 ID |
| `ownerCustomerId` | 库存所有客户 ID，未传时默认等于 `customerId` |
| `materialCode` | 物料编码 |
| `inboundDate` | 现场入库日期，建议保存为 `yyyy-MM-dd`；纸质表只有月日时由业务日期补全年份 |
| `expressCompany` | 入库快递名称/物流方式 |
| `courierNo` | 可空、可重复的来源运单号；业务身份始终是母单号 |
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
| `processingRequirement` | 加工要求，不能为空 |
| `dueDate` | 完成期限，`yyyy-MM-dd` |
| `customerName` | 客户名称快照 |
| `requestId` | 可选。同一次确认保持不变，8 到 64 位，只含字母、数字、点、下划线、冒号和减号。不传时仍走旧的各自提交，不回放 |

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
- 带 `requestId` 时，动作记为 `INBOUND_CONFIRM`，目标类型 `MOTHER_ORDER`，目标编号是母单号。操作人取当前会话，不接受客户端另报账号。同账号、同请求、同规范化内容回放原 `InboundConfirmationResult`；换母单、换客户、换数量重量或其他内容会拒绝，必须换新 `requestId`。母单、来料事实、入库流水和成功结果在同一条数据库连接里提交。单据保存失败或结果保存失败时整单回滚。运单可关联多张母单，查询时须选择具体母单。
- 不带 `requestId` 的旧调用，以及登记导入调用的确认入库，仍由库存仓储和单据仓储各自打开连接并提交。这不是回放，也不能当成已经同事务。带 `requestId` 的工序交付、初检、开返工、返工报工、复检和放行已另行接入各自的工作单元，不并进这条入库事务。未带 `requestId` 的岗位交付仍各自提交。`production-reports` 和旧质检直写 HTTP 已退出。确认发货必须带 `requestId` 并走工作单元。

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

### 5.2.1 岗位工序交付 `ProcessStationReportCommand`

`POST /api/business/operations/process-stations` 使用该命令。手机岗位报工和桌面工序交付面板都调用它。没有新的 HTTP 路径。

| 字段 | 说明 |
|---|---|
| `code` | 子单号，或不拆母单号。已拆母单不能把母单号当目标 |
| `process` | 要交付的工序。操作人取当前登录账号 |
| `requestId` | 可选。同一次确认保持不变，8 到 64 位，只含字母、数字、点、下划线、冒号和减号。不传时仍走旧的各自提交，不回放 |

带 `requestId` 时，动作记为 `PROCESS_HANDOFF`。目标类型是 `WORK_ORDER` 或 `MOTHER_ORDER`，目标编号是解析后的真实单号。规范化内容包含客户、货权客户、母单号、工序、整单数量和重量。同账号、同请求、同规范化内容回放原 `ProcessStationReportResult`，不第二次交付，也不第二次改库存。换工序、换单据、换客户或换账号都会拒绝，必须换新 `requestId`。不拆母单首次转在制、本工序库存流水、交付事实和成功结果在同一条数据库连接里提交。交接保存失败或结果保存失败时整单回滚，不能留下「库存已转但没有交付事实」。已有「这道工序已经交过」只拒绝另一次交付，不能代替结果回放。前序未交、岗位不符、已拆母单未选真实子单仍按原规则拒绝。

证据只能在交付成功并拿到台账号之后绑定。中间工序的操作类型是 `process_station_report`，末道是 `production_report`，操作编号是返回的 `ledgerCode`。上传或绑定失败只补文件或引用，不重新交付。过程说明仍走 `POST /api/scan/progress-records`，不产生库存、工资或工序完成。

不带 `requestId` 的岗位交付仍由库存仓储和交接单各自打开连接并提交。这不是回放，也不能当成已经同事务。通用 `production-reports` HTTP 已退出。工序交付没有新迁移：交付唯一键沿用 `V34__m3_process_station.sql`，请求表沿用 `V44__m3_command_request.sql`。

### 5.2.2 质检轮次与复检

桌面质检面板调用下面这些命令。质检员取当前登录账号。请求里另传的姓名不采用。

| 方法 | 命令 | 动作 |
|---|---|---|
| `POST /api/business/operations/quality-checks` | `QualityCheckCommand`，增加可选 `requestId` | `QUALITY_CHECK` |
| `POST /api/business/operations/quality-checks/rework` | `QualityReworkCommand`，增加可选 `requestId` | `QUALITY_REWORK` |
| `POST /api/business/operations/quality-checks/rework-reports` | `QualityFollowCommand`：`code`、可选 `requestId` | `REWORK_REPORT` |
| `POST /api/business/operations/quality-checks/rechecks` | `QualityRecheckCommand`：`code`、`lines`、可选 `requestId` | `QUALITY_RECHECK` |
| `POST /api/business/operations/quality-checks/releases` | `QualityFollowCommand` | `QUALITY_RELEASE` |

`requestId` 规则与工序交付相同。有值时，同账号、同请求、同规范化内容回放原结果。换条款或原因、换单据、换客户、换账号都拒绝。规范化内容含客户、货权客户、母单号；初检和复检还含逐条判定。目标类型是 `WORK_ORDER` 或 `MOTHER_ORDER`。

初检和复检的结果都是 `QualityCheckResult`。除原有质检单号外，增加 `roundCode`、`roundNo`、`roundType`。`roundType` 为 `INITIAL` 或 `RECHECK`。初检轮次号是 1。复检使用下一个轮次号，并记下返工单号和返工报工台账。原质检对照上的条款、原因、`frozen` 和返工号不因复检而改写。

带 `requestId` 时，本次库存流水、本次轮次或返工事实、成功结果在同一条连接提交。保存失败整单回滚。不带 `requestId` 时质检面板仍各自提交，不回放。桌面质检池不再调用 `quality-freezes`、`rework-dispatches`、旧 `rework-reports` 和 `quality-releases`，这些 HTTP 已退出。

复检前必须已经开返工，并且 `biz_quality_rework_report` 里有这条返工单的报工事实，库存回到半成品，没有未处理冻结。复检不合格会再记一笔冻结。只有最新轮次为合格或让步、半成品余额足够、且没有冻结余额时才能放行。放行不把初检 `frozen` 改成 0。

查询 `QualityCheckView` 增加 `roundCode`、`roundNo`、`roundType`、`reworkReported`、`canRecheck`、`recheckBlock`、`canRelease`、`releaseBlock` 和 `rounds`。没有轮次行的旧初检在 `rounds` 里作为历史首轮读出，`roundCode` 为空，不补插入复检。

证据在拿到轮次号之后绑定。操作类型是 `quality_round`，操作编号是 `roundCode`，场景是 `CHECK`。上传或绑定失败不重新质检。过程说明仍不是正式结论。

迁移 `V46__m3_quality_round.sql`，新增 `biz_quality_round`、`biz_quality_round_line`、`biz_quality_rework_report`。已有库需备份后受控执行，不修改已发布迁移。H2 测试不是 MySQL 升级验收。

### 5.2.3 中途改单

`POST /api/business/order-changes` 创建草稿。未填写的新要求、新期限和新路线继承当前已生效版本；没有生效版本时才用原母单。`expectedVersion` 不传时取当前已生效版本，传入且不一致则拒绝。`newRoute` 里的每一道工序和 `reworkProcess` 都要落在账号工序授权内。

同一 `requestId` 重复提交返回已经入库的那一张，编号以库里的真实 `id` 为准。内容变化或换账号按「同一请求的内容不一致」「该请求不能跨账号重放」拒绝。

`POST /api/business/order-changes/{id}/applications` 生效时只认草稿在财务确认前固定的 `expectedVersion`。调用方另传一个更新的版本号会被拒绝，不能绕过重新确认。财务确认、拒绝分别是 `finance-confirmations`、`finance-rejections`。金额不变时 `amountUnchanged=true`，不写差额。

已完成工序的重做会生成 `RW-` 轮次号。岗位交付和质检按这个轮次号识别；交接和台账单据号用轮次，库存维度仍是原母单或子单。原交付保留，同一道工序可以在新轮次再报一次。

改单证据阶段是 `ORDER_CHANGE`，操作类型同名，操作编号是改单号，目标必须是该改单的母单。

### 5.3 `ReportProductionCommand`

页面和导入不再调用 `POST /api/business/operations/production-reports`，该 HTTP 已退出。现场工序交付走 `process-stations`。登记导入和设备读数仍把数量、重量交给岗位服务 `reportProduction`，但必须登录，不能再绕过工序校验。下面的字段仍是这条内部命令的形状。

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

`POST /api/business/operations/quality-freezes` 使用该命令模型。该接口是 质检冻结写台账基线。

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

`POST /api/business/operations/rework-dispatches` 使用该命令模型。该接口是 返工派发写台账基线。

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

`POST /api/business/operations/rework-reports` 使用该命令模型。该接口是 返工报工写台账基线。

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

`POST /api/business/operations/quality-releases` 使用该命令模型。该接口是 复检放行写台账基线。

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

`POST /api/business/operations/loss-approvals` 使用该命令模型。该接口是 损耗审批与工资试算基线，目标是先冻结损耗归因和工资试算口径，不接入正式审批流，不写库存台账。

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
- 该接口会在 生成轻量审批任务，但只证明固定场景审批、审批责任和工资试算口径，不代表 Flowable/正式审批流已经接入。
- 工序单价、扣损单价、标准损耗率必须作为快照字段保存，后续配置调整不得反向影响历史试算。
- 正式工资发放、工资锁账、财务凭证和员工薪资明细仍在一期边界外；后续若接入工资模块，必须从该试算快照转入独立工资单状态机。

---

### 6.13 `ApprovalTask`

轻量审批保存固定场景任务与审计事件，支持内存/JDBC 仓储。账号创建/更新、启停、解锁和删除由管理员直接执行，不再生成新的 `ACCOUNT_MANAGEMENT` 事后复核摘要任务；历史任务保留。兼容显式申请接口仍可生成独立 `ACCOUNT_PRE_APPROVAL` 场景任务。审批页保留业务审批与历史任务；轻量审批不处理并行会签、复杂条件路由或正式工资锁账。

| 字段 | 说明 |
|---|---|
| `approvalCode` | 审批任务编码；损耗审批复用损耗审批单号，历史账号管理摘要使用 `AUTH-ACC-APP-<yyyyMMddHHmmssSSS>-0001` 序列 |
| `scene` | 审批场景，包含 `LOSS_APPROVAL`、兼容显式申请的 `ACCOUNT_PRE_APPROVAL` 和历史 `ACCOUNT_MANAGEMENT` |
| `businessCode` | 业务对象编码；损耗审批为工单号，账号管理为 `username` |
| `title` | 审批标题 |
| `status` | `WAITING_APPROVAL`、`APPROVED`、`REJECTED`、`AUTO_APPROVED` |
| `applicant` | 提交人快照 |
| `approver` | 审批责任人快照 |
| `ownerRole` | 责任角色快照 |
| `severity` | 前端风险色，不能替代业务状态 |
| `sourceStatus` | 来源命令返回的审批状态 |
| `sourceSummary` | 来源摘要；损耗审批保留投入、产出、允许损耗、超标损耗、试算工资等关键口径，账号管理保留 action、applicantRole、targetOperator、targetRole、targetDataScope、enabled、demoAccount 和 boundary |
| `evidenceCode` | 称重、缺陷照片、交接记录或审批附件编码；账号管理摘要当前为空 |
| `formalPayroll` | 固定为 `false`，表示该审批任务不等于正式工资发放 |
| `createdAt` | 创建时间快照 |
| `decidedAt` | 审批处理时间快照 |
| `decisionOperator` | 审批操作人 |
| `decisionComment` | 审批意见 |
| `locked` | 审批处理后锁定，后续变更必须产生新任务或新审计事件 |

历史 `ACCOUNT_MANAGEMENT` 任务场景约束：

| 字段 | 账号管理摘要取值 |
|---|---|
| `scene` | `ACCOUNT_MANAGEMENT` |
| `businessCode` | 目标账号 `username` |
| `sourceStatus` | `WAITING_ACCOUNT_REVIEW` |
| `sourceSummary` | `action=...; applicantRole=...; targetOperator=...; targetRole=...; targetDataScope=...; enabled=...; demoAccount=...; boundary=post-action approval summary, not a blocking IAM workflow` |

该历史任务只记录旧账号动作成功后的事后复核，不阻断当前账号动作；账号管理不再新增此类任务。历史数据和原迁移保留，不替代完整 IAM、SSO/JWT 或复杂审批流。

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
- `V11__m3_lightweight_approval_contract.sql` 冻结 `approval_task` 和 `approval_audit_event` 合同表；已补 H2 仓储测试验证任务持久化、审批决策和审计事件写入。
- 当前已有角色权限和账号前置审批任务；完整审批操作流程、复杂编排与工资锁账审计仍需完善。
- 审批通过不自动写库存台账、不自动发放工资、不自动生成财务凭证。
- 账号管理摘要任务不自动回滚或拦截账号动作，只提供后续复核入口。

### 6.15 轻量审批持久化契约

| 项目 | 契约 |
|---|---|
| 默认模式 | `zhubao.approval.repository=memory`，继续使用内存仓储，便于无数据库评审 |
| JDBC 模式 | `zhubao.approval.repository=jdbc`，写入 `approval_task` 和 `approval_audit_event` |
| MySQL profile | `ZHUBAO_APPROVAL_REPOSITORY` 默认 `memory`；Docker 交付配置默认覆盖为 `jdbc` |
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

### 7.4 库存持久化契约

`JdbcStockLedgerRepository` 负责持久化库存台账与余额。

| 项目 | 契约 |
|---|---|
| 默认模式 | `zhubao.inventory.repository=memory`，不要求数据库，便于评审和演示 |
| MySQL 模式 | `zhubao.inventory.repository=jdbc`，由 `application-mysql.yml` 和 `ZHUBAO_DB_*` 环境变量提供连接 |
| 台账表 | `inv_stock_ledger`，只追加库存业务事件 |
| 余额表 | `inv_stock_balance`，由台账写入同步维护 |
| 维度键 | `dimension_key`，格式为 `ownerType|ownerCustomerId|customerId|motherOrderCode|workOrderCode|materialCode|warehouseCode|stockStatus` |
| 迁移脚本 | `V6__m3_inventory_jdbc_repository_contract.sql`、`V39__m3_inventory_source_work_order.sql` |

持久化约束：

- `from` 和 `to` 不允许改变所有权、客户、母单或物料。工单通常保持不变；仅 `work_order_split` 允许 `RAW_WAITING` 的空工单转为 `WIP` 的非空工单，覆盖拆成子单与不拆母单首次报工。
- 来源工单独立存入 `from_work_order_code`；空值表示真实空工单，重读时不能回退目标工单。其他不可变业务维度与目标共用，来源仓库、状态继续分别保存。
- `StockLedgerRepository.save` 是单笔库存原子边界：足额检查、来源扣减、目标增量和台账追加一起成功或一起失败。内存同步校验，JDBC 使用条件扣减、原子增量和单连接事务；同维度岗位交付仍检查足额，净余额不变。
- 同一台账号重复保存拒绝且余额不变；业务单号可能包含多次合法部分交付，不能只凭单号去重。该契约不承诺库存与业务单据仓储之间的跨仓储事务。
- `dimension_key` 用于规避 MySQL 可空字段参与唯一索引时的重复余额问题。
- 库存台账号使用日期时间戳 + 序列，避免服务重启后简单日序列重复。

### 7.5 `ShipFinishedStockCommand`

`POST /api/business/operations/outbound-shipments` 使用该命令模型。该接口是 成品出库写台账基线。

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
| `requestId` | 可选。同一次确认保持不变，8 到 64 位，只含字母、数字、点、下划线、冒号和减号。不传时仍走旧的各自提交，不回放 |

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
- 带 `requestId` 时，动作记为 `OUTBOUND_SHIPMENT`，目标类型 `OUTBOUND_ORDER`，目标编号是出货单号。操作人取当前会话，不接受客户端另报账号。同账号、同请求、同规范化内容回放原 `OutboundShipmentResult`。规范化内容包含客户、货权客户、母单、工单、物料、来源仓、目标仓、数量、重量、物流公司和物流单号。换出货单号、换客户、换数量、换重量、换物流或换账号都拒绝，必须换新 `requestId`。库存流水、结构化出库单和成功结果在同一条数据库连接里提交。出库单保存失败或结果保存失败时整单回滚。出货单号唯一键和库存条件扣减在这条写入路径里生效。
- 不带 `requestId` 的旧调用仍由库存仓储和单据仓储各自打开连接并提交。这不是回放，也不能当成已经同事务；这时出库单保存失败可能只留下已扣库存。没有新迁移：出货单唯一键沿用 `V14__m3_business_document_contract.sql`，请求表沿用 `V44__m3_command_request.sql`。

### 7.6 `CustomerReturnCommand`

`POST /api/business/operations/customer-returns` 使用该命令模型。该接口是 客户退货入库写台账基线。

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
- 只完成退货入库台账基线；由 `POST /after-sales-reviews` 决定复核后的放行、返工、报废或冻结分流。

### 7.7 `AfterSalesReviewCommand`

`POST /api/business/operations/after-sales-reviews` 使用该命令模型。该接口是 售后复核分流写台账基线。

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
| `objectType` | 服务端确认后的来源单据类型：`MOTHER_ORDER`、`WORK_ORDER`、`OUTBOUND_ORDER`、`REWORK`。空值表示旧附件来源不确定，只能回看 |

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
| `storageLocation` | 当前存储位置，默认对象内容仍可为 `local://evidence/{documentCode}/{attachmentCode}/{fileName}`，附件元数据可写入内存或 JDBC 仓储，后续对象内容迁移为 NAS/S3 签名访问 |
| `checksum` | `sha256` 校验和 |
| `uploadedAt` | 上传时间 |
| `previewUrl` | 为短期签名预览内容地址，包含 `expiresAt`、`purpose=preview`、`resolution=original` 与 HMAC `token` |

### 9.5 `AttachmentPreviewUrl`

| 字段 | 说明 |
|---|---|
| `code` | 附件编码 |
| `fileName` | 文件名 |
| `mediaType` | MIME 类型 |
| `url` | 返回含 `expiresAt`、`purpose`、`resolution=original`、`token` 的短期签名 URL；`purpose` 与取址请求一致 |
| `expiresAt` | 过期时间展示值，默认 15 分钟后过期，可由 `zhubao.documents.preview.ttl-minutes` 调整 |

附件预览使用短期 HMAC 令牌。`zhubao.documents.preview.secret` 签名 `attachmentCode:expiresAt:purpose:resolution`，`zhubao.documents.preview.ttl-minutes` 控制默认 15 分钟有效期。旧的仅绑定附件与期限的令牌不再适用；更改用途或清晰度会使签名失效。页面每次预览、重试或下载都重新请求，下载不通过追加 `download=true` 切换。内容地址仍是有效期内可复用的短期持有者地址，内容请求不重新校验登录账号的客户范围；申请地址时按单据校验范围。媒体加载失败时自动重新取址一次。该机制不替代按登录账号授权的细粒度预览权限或对象存储归档策略。

文档附件约束：

- 当前 已支持 multipart 上传到后端本地存储适配、checksum、审计事件、预览 URL 和内容读取；文档包、附件元数据和审计事件通过 `DocumentManagementRepository` 支持内存/JDBC 切换。
- `zhubao.documents.repository=jdbc` 时读取/写入 V10 合同表：`doc_package`、`doc_evidence_attachment`、`doc_audit_event`。V42 为附件增加可空 `object_type`，并新增 `doc_business_evidence_link`。过程说明使用 V43 的 `scan_operation_progress`，由 `zhubao.scan-progress.repository` 切换。已有库需备份后受控执行 V42 和 V43，不修改 V10 或 V36。
- 后续生产环境必须按部署条件接 NAS/S3 兼容存储，并通过签名 URL 控制预览和下载。
- 质检、称重、视频、配置审批证据必须能绑定业务对象、校验和、保留策略和审计事件。
- 审批完成后的配置和质检证据应锁定 checksum，后续修改必须产生新版本或新审计事件。

### 9.6 `BusinessEvidenceLink`

文件和引用分开。同一校验和在同一张单据上不重复落盘；跨场景、跨工序、跨条款各保留一条引用。

| 字段 | 说明 |
|---|---|
| `linkCode` | 引用编号 |
| `attachmentCode` | 已存在的附件编号 |
| `targetType` | 真实单据类型，与附件 `objectType` 相同 |
| `targetCode` | 真实单据编号，必须等于附件 `businessCode` |
| `stage` | `INTAKE`、`PRODUCTION`、`QUALITY`、`SHIPPING`、`REWORK` |
| `scene` | `UNPACK`、`WEIGH`、`DEFECT`、`FINISH`、`PROCESS`、`CHECK`、`PACK`、`WAYBILL` |
| `operationType` | 已存在的服务端操作：`inbound_confirm`、`process_station_report`、`production_report`、`quality_check`、`quality_freeze`、`quality_release`、`rework_dispatch`、`rework_report`、`outbound_shipment`、`progress_note` |
| `operationCode` | 对应台账号、质检案件号、返工号、已落库出货单号或过程记录编号。不接受交接编号或前端自编编号 |
| `processName` | 工序。生产证据必填；其他阶段可空，空值保存为空字符串 |
| `clauseNo` | 条款号。可空，空值保存为空字符串 |
| `operatorName` | 当前登录操作人 |
| `linkedAt` | 绑定时间 |
| `linkKeyHash` | 上述业务键的 SHA-256。命中后仍核对原始维度 |

查询 `EvidenceBoard` 分两列：`links` 是已经关联的引用，`historical` 是同一单据上尚未关联或来源不确定的附件。来源不确定的旧附件 `label` 为「历史单据证据」，`fileState` 为 `HISTORICAL`，`sceneCode` 为空，不带工序或操作编号。已上传但还没绑定的文件 `fileState` 为 `UPLOADED_PENDING_LINK`；新增 `sceneCode` 由已保存的采集场景标签反查，无法确认时为空。页面只有在单据类型、单号、场景与当前作业一致且已有正式操作编号时才提供补关联入口，服务端仍校验操作事实与授权。已绑定为 `LINKED`。

绑定约束：

- 同时校验单据存在、附件属于该单据、操作已经落库，以及当前用户能读取该单并具备对应操作权限。
- 母单与子单、不同客户的单据不能因为客户端声称同客户而复用附件。
- 已下线附件不能预览，也不能新增绑定；已有引用保留。
- 绑定不报工、不判定质检、不扣库存。`progress_note` 只能绑到已经保存的过程记录，记录不存在或单据、阶段不一致时拒绝。旧的正式操作绑定仍然可用。
- 旧 `POST /api/documents/captures`、`POST /api/documents/attachments/uploads` 和命令里的单个 `evidenceCode` 保持原样。

文档附件存储配置：

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `zhubao.documents.repository` | `memory` | 文档包、附件元数据和审计事件仓储；可设为 `jdbc` 读取/写入 `doc_*` 合同表 |
| `zhubao.documents.storage.provider` | `local` | 当前只落地本地文件适配；后续可扩展为 `nas`、`s3` 或其他实现 |
| `zhubao.documents.storage.local-root` | `target/zhubao-evidence` | 本地附件根目录，测试环境可覆盖为临时目录 |

---

## 10. 持久化与数据来源

正式交易与评审快照分别维护。`/api/business/workspaces/*` 及 `summary` 返回 `dataSource`：默认 `LIVE`，只从正式单据、交接、质检、审批与库存台账生成投影；设置 `zhubao.business.workspace.demo-enabled=true` 时返回 `DEMO`，只读取独立评审快照。两种来源不合并，真实数据为空不触发样例回退。

来料、工单、质检、损耗与库存行新增可空 `operationContext`，包含 `customerId`、`ownerCustomerId`、`motherOrderCode`、`workOrderCode`、`materialCode`、`warehouseCode`、`ownerType`、`stockStatus`、`quantity`、`weight`。仅能唯一定位正式库存维度时提供；评审行、多仓或多状态歧义时为空，前端拒绝猜测客户、物料和重量。业务写入使用当前登录人，物流、返工单号、复核结论及损耗计价由使用者输入，不再按样例码生成。

当前启用范围以项目核心和实际页面消费链为准；缺少真实记录的指标、离线草稿和证据不制造占位事实，远期模型只保留归档。

### 10.1 正式工作台与显式演示来源

`BusinessWorkspaceService` 默认通过 `FormalBusinessWorkspaceQuery` 聚合正式单据、工序、质检、余额和台账，返回 `dataSource=LIVE`；空库返回空列表，不补评审记录，也不把正式数据与快照合并。

仅在显式 `zhubao.business.workspace.demo-enabled=true` 时使用 `BusinessWorkspaceRepository` 的独立评审快照，返回 `DEMO`。`zhubao.business.repository` 和 `snapshot-code` 只控制这个演示来源，不控制正式母单持久化；历史 `biz_workspace_*` 表与迁移保留兼容。

本地正式单据默认内存；MySQL profile 下 `business-document`、库存等默认 JDBC，审批和附件元数据仍受独立开关控制，默认可能是 memory。Compose 显式覆盖相关 JDBC 开关。最终以实际环境变量为准，不能仅凭开启 profile 推断全模块持久化。

正式余额与流水另可调用 `/api/inventory/balances`、`/api/inventory/ledgers`；正式进度是 `/api/business/operations/progress`。页面展示的聚合也必须服从登录角色与客户范围。

### 10.2 业务操作写台账基线

业务命令统一通过库存服务产生台账，页面不得直接修改库存余额。

| 项目 | 契约 |
|---|---|
| 来料确认入库 | `POST /api/business/operations/inbound-confirmations`，`RAW_WAITING` 入账 |
| 工序交付 | `POST /api/business/operations/process-stations`，末道 `WIP -> SEMI_FINISHED` |
| 质检对照 | `POST /api/business/operations/quality-checks`，不合格才 `SEMI_FINISHED -> FROZEN` |
| 成品出库 | `POST /api/business/operations/outbound-shipments`，必须带 `requestId`，`FINISHED -> SHIPPED` |
| 客户退货 | `POST /api/business/operations/customer-returns`，`SHIPPED -> FINISHED` |
| 售后复核 | `POST /api/business/operations/after-sales-reviews`，`FINISHED@RETURN-CHECK -> FINISHED/REWORK/SCRAP/FROZEN` |
| 损耗审批 | `POST /api/business/operations/loss-approvals`，返回损耗审批与工资试算快照，并创建轻量审批任务；不写库存台账 |
| 结构化母单 | `POST /api/business/operations/inbound-confirmations` 在库存台账成功后写 `biz_mother_order/biz_mother_order_item`；重复母单号先拒绝 |
| 结构化出库单 | `POST /api/business/operations/outbound-shipments`。带 `requestId` 时库存流水、出库单和成功结果在同一条连接提交；不带 `requestId` 时仍在库存台账各自提交后再写 `biz_outbound_order/biz_outbound_order_item`。重复出库单号由唯一键拒绝 |
| 单据查询 | `GET /api/business/documents/mother-orders/{code}`、`GET /api/business/documents/outbound-orders/{code}` |
| 轻量审批任务 | `GET /api/approvals/tasks`、`GET /api/approvals/tasks/pending`、`POST /api/approvals/tasks/{approvalCode}/decisions`，用于固定场景待审批、审批通过/驳回和锁定 |
| 后端入口 | `BusinessOperationController` |
| 权属边界 | `OwnerType.CUSTOMER`，保持客户来料和企业自有库存隔离 |
| 当前限制 | 母单/出库单已具备基础结构化快照和明细行，但尚未完成主数据匹配、完整状态流转、补录 UI、追溯聚合和单据更正；轻量审批可切换 JDBC 持久化，但正式权限、复杂审批流、审批 UI、工资锁账/发放、售后结算和对象存储仍需继续接入 |

库存台账必须只追加；损耗审批、工资计算和证据采集按职责调用相应服务。

### 10.3 正式业务单据持久化

`BusinessDocumentRepository` 保存正式业务单据及其关联记录，供操作命令、进度查询与标签使用：

| 项目 | 契约 |
|---|---|
| 默认模式 | `zhubao.business-document.repository=memory`，无数据库评审时使用内存仓储 |
| JDBC 模式 | `zhubao.business-document.repository=jdbc`，读取/写入 `biz_mother_order`、`biz_mother_order_item`、`biz_outbound_order`、`biz_outbound_order_item` |
| MySQL profile | `ZHUBAO_BUSINESS_DOCUMENT_REPOSITORY=jdbc`，默认随 `mysql` profile 启用 |
| 入库落库时机 | `BusinessOperationService.confirmInbound` 完成 `StockLedgerService.transfer` 后保存母单快照和一条物料明细 |
| 出库落库时机 | `BusinessOperationService.shipFinishedStock` 完成 `StockLedgerService.transfer` 后保存出库单快照和一条出库明细 |
| 防重复 | 调用库存台账前先检查母单号/出库单号是否已存在，存在则拒绝，避免重复库存入账 |
| 查询接口 | `GET /api/business/documents/mother-orders/{code}`、`GET /api/business/documents/outbound-orders/{code}` |

正式单据已包含拆单、工序交接与按要求质检记录；完整状态流转、单据更正、主数据自动匹配及业务验收仍需按产品范围推进。

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
| `evidence` | 现场证据摘要；可通过 `/api/hardware/poc-evidence` 查看附件元数据关联摘要 |
| `blocker` | 阻断项或剩余风险 |
| `owner` | 当前责任人快照 |
| `nextAction` | 下一步动作 |

### 11.2 `HardwarePocEvidenceSummary`

| 字段 | 说明 |
|---|---|
| `pocCode` | POC 记录号，对应 `HardwarePocRecord.code` |
| `scene` | POC 验证场景快照 |
| `result` | POC 验证结果快照 |
| `documentCode` | 最近关联的证据包编码，未关联时为空字符串 |
| `attachmentCount` | 匹配到的附件数量 |
| `missingEvidence` | 待补证据数量；没有附件时返回 1 |
| `attachmentCodes` | 关联附件编码列表 |
| `latestUploadedAt` | 最近附件上传时间，未关联时为空字符串 |
| `status` | `已关联证据`、`证据待补` 或 `待补证据` |
| `nextAction` | 证据侧下一步动作，未关联时提示补传现场 POC 原始照片、视频或日志 |
| `tone` | 前端状态色：`green`、`amber`、`red` 等 |

`GET /api/hardware/poc-evidence` 只读聚合 `HardwarePocRecord` 和 `EvidenceAttachment`，关联条件为 `EvidenceAttachment.businessType = 硬件 POC` 且 `EvidenceAttachment.businessCode = HardwarePocRecord.code`；该接口不执行上传、归档、复测确认或设备直连动作。

已移除的适配计划、协议样本、参数复核、复测结果和签字就绪度五组说明接口不属于现行 API；原字段保存在[整理前契约](./archive/contracts/16_字段字典与API契约_整理前.md)。硬件型号确认、现场复测与签字要求继续作为需求和上线决策保留。

硬件 POC 约束：

- 没有 POC 记录的硬件能力不能承诺为一期强集成。
- 结果为阻断时必须有人工兜底或明确排期。
- POC 记录必须能关联设备、业务场景和证据附件。
- 电子秤、NVR、PWA 扫码、标签打印至少各保留一条现场验证记录。
- `GET /api/hardware/poc-records` 读取 `HardwarePocRecordRepository`，接口响应字段不变；本地默认 `memory`，MySQL profile 默认 `jdbc`，seed 脚本为 `V19__m3_hardware_poc_record_seed.sql`。
- `GET /api/hardware/poc-evidence` 读取附件元数据做只读证据关联，未关联附件的 POC 明确返回待补证据。
- `zhubao.hardware.poc.repository` / `ZHUBAO_HARDWARE_POC_REPOSITORY` 只控制 POC 记录仓储来源，不代表硬件 SDK、NVR 协议、打印驱动、扫码识别或电子秤采集已完成强集成。

---

## 13. 权限守卫

敏感命令由角色权限守卫保护，工作台与财务查询分别校验数据范围。客户授权表和菜单仓储已落地；完整组织树与 SSO/JWT 仍未接入。详细读取规则见相应能力章节。

### 13.1 请求头

| Header | 说明 | 默认值 | 示例 |
|---|---|---|---|
| `X-Zhubao-Operator` | 操作人快照，用于后续审计上下文 | `system-review` | `车间组长 马杰` |
| `X-Zhubao-Role` | 当前操作角色 | `ADMIN` | `WORKSHOP_LEADER` |
| `X-Zhubao-Data-Scope` | 数据范围编码，业务工作台 GET 会按该值做首版过滤 | `ALL` | `TEAM` |
| `X-Zhubao-Customer-Scope` | 客户范围或业务对象编码范围，`CUSTOMER_RANGE` 时用于收敛工作台数据 | 空 | `ZD-20260706-001;ZD-20260706-002` |

说明：
- 默认 `ADMIN` 只在 `zhubao.security.allow-header-context=true` 时用于兼容现有评审脚本和自动化测试，不代表生产环境允许匿名管理员。
- 正式环境使用登录态解析操作人、角色和范围；请求头仅用于显式允许的评审环境。
- 角色或数据范围无法识别时返回 `403`。

### 13.2 受保护接口

| 接口 | 权限码 | 允许角色 |
|---|---|---|
| `POST /api/inventory/ledger/transfers` | `INVENTORY_LEDGER_TRANSFER` | `ADMIN`、`GENERAL_MANAGER` |
| `POST /api/business/operations/inbound-confirmations` | `BUSINESS_INBOUND_CONFIRM` | `ADMIN`、`GENERAL_MANAGER`、`RECEIVER`、`WAREHOUSE_KEEPER` |
| `POST /api/business/operations/process-stations` | `BUSINESS_PRODUCTION_REPORT` | `ADMIN`、`GENERAL_MANAGER`、`OPERATOR`、`WORKSHOP_LEADER` |
| `POST /api/business/operations/quality-checks` | `BUSINESS_QUALITY_FREEZE` | `ADMIN`、`GENERAL_MANAGER`、`QUALITY_INSPECTOR`、`WORKSHOP_LEADER` |
| `POST /api/business/operations/quality-checks/rework` | `BUSINESS_REWORK_DISPATCH` | `ADMIN`、`GENERAL_MANAGER`、`QUALITY_INSPECTOR`、`WORKSHOP_LEADER` |
| `POST /api/business/operations/quality-checks/rework-reports` | `BUSINESS_REWORK_REPORT` | `ADMIN`、`GENERAL_MANAGER`、`OPERATOR`、`WORKSHOP_LEADER` |
| `POST /api/business/operations/quality-checks/releases` | `BUSINESS_QUALITY_RELEASE` | `ADMIN`、`GENERAL_MANAGER`、`QUALITY_INSPECTOR`、`WORKSHOP_LEADER` |
| `POST /api/business/operations/outbound-shipments` | `BUSINESS_OUTBOUND_SHIPMENT` | `ADMIN`、`GENERAL_MANAGER`、`WAREHOUSE_KEEPER`、`SALES` |
| `POST /api/business/operations/customer-returns` | `BUSINESS_CUSTOMER_RETURN` | `ADMIN`、`GENERAL_MANAGER`、`WAREHOUSE_KEEPER`、`SALES`、`QUALITY_INSPECTOR` |
| `POST /api/business/operations/after-sales-reviews` | `BUSINESS_AFTER_SALES_REVIEW` | `ADMIN`、`GENERAL_MANAGER`、`QUALITY_INSPECTOR`、`WORKSHOP_LEADER` |
| `POST /api/business/operations/loss-approvals` | `BUSINESS_LOSS_APPROVAL` | `ADMIN`、`GENERAL_MANAGER`、`WORKSHOP_LEADER` |
| `POST /api/approvals/tasks/{approvalCode}/decisions` | `APPROVAL_DECISION` | `ADMIN`、`GENERAL_MANAGER`、`WORKSHOP_LEADER`、`FINANCE_STATISTICIAN` |
| `POST /api/documents/attachments/uploads` | `DOCUMENT_ATTACHMENT_UPLOAD` | `ADMIN`、`GENERAL_MANAGER`、`RECEIVER`、`WAREHOUSE_KEEPER`、`QUALITY_INSPECTOR`、`WORKSHOP_LEADER` |
| `GET /api/auth/account-options`、`GET /api/auth/accounts`、`GET /api/auth/accounts/pre-approval-readiness`、`POST /api/auth/accounts/pre-approval-drafts`、`GET/POST /api/auth/accounts/pre-approval-applications`、`GET /api/auth/accounts/pre-approval-evidence`、`POST /api/auth/accounts/pre-approval-evidence/uploads`、`POST /api/auth/accounts`、`POST /api/auth/accounts/{username}/status`、`POST /api/auth/accounts/{username}/unlock`、`POST /api/auth/accounts/{username}/delete`、`POST /api/auth/accounts/{username}/session-revocations` | `AUTH_ACCOUNT_MANAGE` | 仅 `ADMIN` |
| `GET/POST /api/security/customer-authorizations`、`POST /api/security/customer-authorizations/revocations` | `CUSTOMER_AUTHORIZATION_MANAGE` | 仅 `ADMIN` |

### 13.3 错误响应

角色越权时返回：

```json
{
  "success": false,
  "code": 40301,
  "message": "角色 OPERATOR 无权执行 损耗审批试算",
  "data": null
}
```

权限拒绝的 HTTP 状态码为 `403`，业务错误码为 `40301`。

登录会话不存在、已退出、已过期、已吊销，或账号密码／权限已变更时，HTTP 状态码同样是 `403`，业务码同样是 `40301`，但 `message` 固定为「登录已失效，请重新登录」。前端只在这句文案，或 HTTP `401` 时，清除旧令牌并回到登录页。其他 `403`（例如「无权执行」）保留当前登录。响应体不是 JSON 时，不能判断为身份失效，也不清除令牌。审计里仍保留「登录会话已过期」「账号已停用」等具体原因。

请求体 Bean Validation 和参数约束校验失败时，HTTP 状态码为 `400`，业务错误码为 `422`。`message` 仅汇总非空、去重、排序后的约束提示，以中文分号分隔；没有提示时返回“请求参数不正确”。不返回校验异常的方法签名、被拒绝的输入值等内部详情。

### 13.4 当前限制

- 认证优先使用 Bearer 会话；账号、审计、菜单和客户授权均有内存/JDBC 仓储。正式电子签字、SSO/JWT 与不可篡改审计不属于当前完成能力。
- 工作台 GET、进度和财务读取按各自查询规则过滤；不能据此推断所有接口已具备全系统行级授权。完整组织树与跨对象范围推导仍待完善。
- 尚未把权限矩阵持久化到数据库或配置基线变更流程。
- 尚未把审批人和权限守卫结果统一写入完整审计事件。

---

## 14. 认证与账号

账号来自 `AuthUserAccountRepository`，使用 PBKDF2 密码哈希；支持失败锁定、密码策略、过期检查、自助改密、临时密码一次性消费及认证审计。账号前置审批与事后复核是不同任务类型，放行规则见本章账号管理部分。

### 14.1 `POST /api/auth/login`

请求：

| 字段 | 说明 |
|---|---|
| `username` | 登录账号用户名，后端会归一化为小写查找 |
| `password` | 明文登录密码，仅用于本次校验，不进入响应或审计日志 |

两个字段均为必填，缺失、空字符串或纯空白分别返回“请输入用户名”／“请输入密码”。登录页先检查空白输入，显示“请输入账号”／“请输入密码”，不发送登录请求。

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

密码过期规则：

- 生产账号以 `passwordChangedAt` 为起点，90 天后过期。
- `passwordChangedAt` 为空的生产账号视为已过期。
- 密码已过期时，密码校验通过后仍返回 `403`，消息为“密码已过期，请联系管理员重置密码”。
- 密码过期拒绝登录会写入 `LOGIN` / `DENIED` 审计，原因包含“密码已超过 90 天有效期”，且不增加失败登录次数。
- 默认评审 seed 账号在该边界中不因 90 天规则过期；该豁免只用于评审，不作为生产上线豁免。

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
| `passwordChangedAt` | 密码最近变更时间展示值；未知为空字符串 |
| `passwordExpiresAt` | 密码到期时间展示值；`passwordChangedAt` 为空时返回 epoch 对应展示值 |
| `passwordExpired` | 当前会话账号密码是否已过期；过期生产账号通常无法新建会话，仅用于边界展示和评审 seed 判定 |
| `passwordChangeRequired` | 当前会话是否必须先完成自助改密；为 `true` 时 token 可用于 `POST /api/auth/password`，但不能解析为业务操作上下文；临时密码是否已消费不暴露给前端 |
| `customerScope` | 账号客户范围；`dataScope=CUSTOMER_RANGE` 时由账号仓储带出并供业务工作台 GET 过滤使用 |
| `menuCodes` | 当前角色可见菜单编码，使用前端模块 tab key，如 `dashboard`、`inbound`、`approvals`、`sample` |
| `menuTree` | 当前角色可见菜单树，元素结构见 13.5；包含菜单编码、名称、模块说明、路由和按钮权限编码 |

### 14.2 其他认证接口

| API | 说明 |
|---|---|
| `GET /api/auth/session` | 使用 `Authorization: Bearer <token>` 读取当前会话 |
| `POST /api/auth/logout` | 注销当前 token；前端无论后端是否可达都清理本地 token |
| `POST /api/auth/password` | 自助改密/续期；成功后清除强制首次改密标记和临时密码消费标记；只接受 Bearer 登录会话，不使用请求头角色兜底 |
| `GET /api/auth/demo-users` | 仅返回启用且 `demoAccount=true` 的演示账号，按账号名排序；不返回真实账号。无启用演示账号时返回空列表，登录页隐藏演示密码提示和候选列表 |
| `GET /api/auth/audit-events` | 返回最近登录会话审计事件，默认返回最近审计事件；仓储可切换内存/JDBC |
| `GET /api/auth/audit-integrity` | 返回最近认证审计事件摘要链校验结果 |

`POST /api/auth/password` 请求：

| 字段 | 说明 |
|---|---|
| `currentPassword` | 当前登录账号的原密码，必须校验通过 |
| `newPassword` | 新密码，必须通过 生产密码策略，且不得与当前密码相同 |

自助改密成功后：

- 返回刷新后的 `AuthSession`，token 保持不变。
- 更新账号 `passwordChangedAt`，账号摘要和会话中的 `passwordExpiresAt/passwordExpired` 随之刷新。
- 清除账号 `passwordChangeRequired` 和 `temporary_password_consumed` 标记，临时密码会话改密后可重新解析为业务操作上下文。
- 清零该账号失败登录次数和锁定到期时间。
- 写入 `PASSWORD_CHANGE` / `SUCCESS` 审计事件，审计中只保留 token 尾号。
- 该接口不纳入 `PermissionGuardInterceptor` 请求头兜底，由 `AuthSessionService` 内部 Bearer session 校验；无 Bearer token、当前密码错误、新密码与旧密码相同或不符合策略时返回拒绝或业务错误。

### 14.3 登录会话审计

`AuthAuditEvent` 记录登录、会话解析、退出、账号管理与改密事件，并保存 `previousEventHash/eventHash` 摘要链。本地内存模式重启丢失，JDBC 模式写入 `auth_audit_event`；摘要链不等于不可篡改归档。

`AuthAuditEvent`：

| 字段 | 说明 |
|---|---|
| `eventId` | 审计事件号，当前格式为 `AUTH-AUD-...` |
| `eventType` | 事件类型：`LOGIN`、`SESSION_READ`、`LOGOUT`、`SESSION_RESOLVE`、`ACCOUNT_MANAGEMENT`、`PASSWORD_CHANGE`、`SESSION_REVOKED` |
| `username` | 登录用户名；未知 token 可为空 |
| `operator` | 操作人快照；失败登录或未知 token 可为空 |
| `role` | 角色编码；失败登录或未知 token 可为空 |
| `result` | `SUCCESS` 或 `DENIED` |
| `reason` | 审计原因，例如登录成功、用户名或密码错误、会话不存在 |
| `occurredAt` | 审计发生时间展示值 |
| `sessionTokenSuffix` | token 尾号，只用于排查；不得返回完整 token 或密码 |
| `previousEventHash` | 前一条认证审计事件摘要；最近事件链校验使用 |
| `eventHash` | 当前事件 SHA-256 摘要，基于前序摘要和事件业务字段计算 |

`AuthAuditIntegritySummary`：

| 字段 | 说明 |
|---|---|
| `checkedEvents` | 本次校验的最近事件数量 |
| `verified` | 最近事件摘要链是否通过校验 |
| `latestEventId` | 最近一条事件号 |
| `latestEventHash` | 最近一条事件摘要 |
| `brokenEventId` | 校验失败时的断点事件号；通过时为空 |
| `message` | 校验说明 |

新增 `AuthAuditRepository` 仓储边界：

| 配置项 | 默认值 | 说明 |
|---|---|---|
| `zhubao.auth.audit.repository` | `memory` | 登录审计仓储；可设为 `jdbc` 写入 `auth_audit_event` |
| `ZHUBAO_AUTH_AUDIT_REPOSITORY` | `jdbc` in mysql profile | MySQL profile 下的环境变量开关 |

`V15__m3_auth_audit_contract.sql` 冻结 `auth_audit_event` 合同表，字段包括 `event_id`、`event_type`、`username`、`operator`、`role`、`result`、`reason`、`occurred_at` 和 `session_token_suffix`。该表不保存密码、完整 token 或客户端密钥。

新增 `AuthUserAccountRepository` 仓储边界：

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

`password_changed_at` 用于密码过期检测；管理员创建/重置密码和用户自助改密会刷新该字段。账号前置审批申请单与菜单分别使用独立仓储。

`V20__m3_auth_user_customer_scope_contract.sql` 在 `auth_user_account` 上新增：

| 字段 | 说明 |
|---|---|
| `customer_scope` | 账号客户范围，当前以 `|` 序列化保存；读取时兼容 `|`、`,`、`，`、`;`、`；` 分隔符，仅在 `data_scope=CUSTOMER_RANGE` 时作为 Bearer 数据范围过滤依据 |

`V23__m3_auth_force_password_change.sql` 在 `auth_user_account` 上新增：

| 字段 | 说明 |
|---|---|
| `password_change_required` | 强制首次登录改密状态；管理员新建/重置非 demo 账号密码后写 `true`，登录会话和账号摘要返回 `passwordChangeRequired=true`，自助改密成功后清除 |

`V24__m3_auth_temporary_password_once.sql` 在 `auth_user_account` 上新增：

| 字段 | 说明 |
|---|---|
| `temporary_password_consumed` | 临时密码一次性消费状态；仅当 `password_change_required=true` 时有意义，首次临时密码登录成功前从 `false` 原子更新为 `true`，重复使用同一临时密码登录会被拒绝，自助改密成功后清除为 `false`；该字段不进入前端公开响应 |

`V18__m3_auth_audit_integrity_contract.sql` 在 `auth_audit_event` 上新增：

| 字段 | 说明 |
|---|---|
| `previous_event_hash` | 前一条认证审计事件摘要 |
| `event_hash` | 当前事件摘要，保存时由内存/JDBC 审计仓储生成 |

### 14.4 账号管理接口

独立用户管理页编码为 `users`，仅向具有 `AUTH_ACCOUNT_MANAGE` 的管理员（`ADMIN`）开放；总经理没有账号管理或客户授权管理权限。接口要求 Bearer 会话或允许的显式评审角色头，不使用匿名管理员兜底。管理员在同页直接维护账号、重置密码、分配内置角色、数据范围、授权客户及可操作工序；角色权限由 `PermissionGuard` 定义，不支持自定义 ACL。配置页仅保留登录会话、自助改密和存储管理。

创建/重置密码遵循密码策略；成功管理动作写认证审计，不再生成新的事后账号审批。未传 `preApprovalApplicationCode` 时直接保存，即使旧 `zhubao.auth.pre-approval.enforce-account-actions=true` 也不阻断；显式传入申请单号时保留兼容校验和申请消费。禁止删除当前登录账号，禁止停用、降权或删除最后一个启用管理员。

| API | 权限 | 说明 |
|---|---|---|
| `GET /api/auth/account-options` | `AUTH_ACCOUNT_MANAGE` | 返回可分配的内置角色及各角色权限，权限列表来自 `PermissionGuard`；结构见 `AuthAccountOptions` |
| `GET /api/auth/accounts` | `AUTH_ACCOUNT_MANAGE` | 返回账号状态摘要列表；GET 在控制器内显式调用权限守卫 |
| `GET /api/auth/accounts/pre-approval-readiness` | `AUTH_ACCOUNT_MANAGE` | 返回账号前置审批就绪度只读清单；用于暴露必备材料和缺失能力，不阻断现有账号动作 |
| `POST /api/auth/accounts/pre-approval-drafts` | `AUTH_ACCOUNT_MANAGE` | 评估账号前置审批申请草稿；返回已收材料、待补材料、系统缺失能力和阻断状态，不写账号、不创建审批任务 |
| `GET /api/auth/accounts/pre-approval-applications` | `AUTH_ACCOUNT_MANAGE` | 返回已提交的账号前置审批申请单；可切换 JDBC 仓储，按提交时间倒序 |
| `POST /api/auth/accounts/pre-approval-applications` | `AUTH_ACCOUNT_MANAGE` | 将草稿评估结果保存为申请单；可切换 JDBC 仓储持久化；生成 `ACCOUNT_PRE_APPROVAL` 轻量审批任务并回写 `approvalTaskCode`；不写账号、不阻断账号动作 |
| `GET /api/auth/accounts/pre-approval-evidence` | `AUTH_ACCOUNT_MANAGE` | 返回申请单证据摘要，按 `businessType=账号前置审批`、`businessCode=applicationCode` 关联附件元数据 |
| `POST /api/auth/accounts/pre-approval-evidence/uploads` | `AUTH_ACCOUNT_MANAGE` | multipart 上传申请单证据附件；请求字段 `applicationCode`、`file`，自动创建/更新 `DOC-<applicationCode>` 证据包并绑定 `documentCode=DOC-<applicationCode>`、`businessType=账号前置审批`、`businessCode=<applicationCode>` |
| `POST /api/auth/accounts` | `AUTH_ACCOUNT_MANAGE` | 新建或更新账号；新建必须提供密码，更新不传密码时保留原密码哈希，传密码时需通过密码策略并重置失败次数和锁定状态；可不传 `preApprovalApplicationCode` 直接保存，显式传入时校验申请单放行状态；提供密码且 `demoAccount=false` 会要求目标账号首次登录先自助改密，并重置临时密码消费状态 |
| `POST /api/auth/accounts/{username}/status` | `AUTH_ACCOUNT_MANAGE` | 直接启用或停用账号，停用后登录仍返回通用“用户名或密码错误”；显式传入 `preApprovalApplicationCode` 时校验 `AUTH-PRE-STATUS-001` 申请单。账号变更、管理审计和显式申请消费在同一事务提交后再吊销会话；吊销失败时账号保持停用、申请保持已执行，返回“只重试会话吊销”，不要再次提交原申请 |
| `POST /api/auth/accounts/{username}/unlock` | `AUTH_ACCOUNT_MANAGE` | 管理员直接清零失败次数并解除临时锁定；显式传入 `preApprovalApplicationCode` 时校验 `AUTH-PRE-STATUS-001` 申请单。解锁与显式申请消费、管理审计同一事务 |
| `POST /api/auth/accounts/{username}/delete` | `AUTH_ACCOUNT_MANAGE` | 事务删除账号及该账号客户授权关系，返回 `null`；保留业务记录、认证审计、历史审批及申请数据。禁止删除自己及最后一个启用管理员；删除后旧会话失效 |
| `POST /api/auth/accounts/{username}/session-revocations` | `AUTH_ACCOUNT_MANAGE` | 只重试已停用账号的会话吊销，不修改账号、不消费申请。账号仍启用时拒绝。返回 `SessionRevocationResult`：`username`、`revokedSessions` |

返回 `AuthAccountOptions`：

| 字段 | 说明 |
|---|---|
| `roles` | 内置角色选项列表；每项包含 `value`（`RoleCode`）、`label`（角色中文名）、`permissions`（`PermissionGuard` 返回的该角色权限） |
| `roles[].permissions[]` | 权限摘要，包含 `code`（权限码）和 `label`（权限中文名）；用于展示角色能力，不提供自定义权限写入 |

`ManageAuthUserAccountCommand`：

| 字段 | 说明 |
|---|---|
| `username` | 登录用户名，后端归一化为小写 |
| `password` | 新建账号必填；更新账号可不传，不返回给前端；提供时必须通过 密码策略；提供时 会刷新 `passwordChangedAt` 并清除失败锁定，非 demo 账号还会设置 `passwordChangeRequired=true` 且 `temporary_password_consumed=false` |
| `operator` | 操作人姓名或岗位快照 |
| `role` | `RoleCode` 编码 |
| `dataScope` | `DataScope` 编码 |
| `customerScope` | 可选客户范围列表；仅 `dataScope=CUSTOMER_RANGE` 时保存并去重，非客户范围账号会清空；更新账号不传该字段时保留既有范围 |
| `enabled` | 是否启用 |
| `demoAccount` | 是否评审 seed 账号；生产账号应为 `false` |
| `preApprovalApplicationCode` | 可选兼容前置审批申请单号；未传时直接保存，旧强制开关不使其必填。显式传入时后端必须能查到申请单，且目标账号、动作 readiness 和 `READY_FOR_BLOCKING_APPROVAL` 状态匹配，否则拒绝账号动作 |
| `allowedProcesses` | 可操作工序列表，限定雕刻、镶嵌、抛光；更新不传时保留既有工序绑定 |

密码策略：

| 规则 | 说明 |
|---|---|
| 最小长度 | 至少 12 位 |
| 字符类型 | 必须同时包含字母和数字 |
| 空白字符 | 不允许首尾空白 |
| 账号相同 | 密码不得与账号名相同，比较时忽略大小写和账号首尾空白 |
| 常见弱口令 | 拒绝 `123456`、`12345678`、`admin123`、`password`、`password123`、`qwerty123`、`welcome123` 等内置弱口令 |
| seed 账号 | 默认评审 seed 账号仅用于评审，不作为生产密码策略豁免依据；生产上线前必须替换、停用或删除 |

密码过期边界：

| 规则 | 说明 |
|---|---|
| 有效期 | 生产账号密码有效期 90 天 |
| 改密时间来源 | `auth_user_account.password_changed_at` / `AuthUserAccount.passwordChangedAt` |
| 过期处理 | 登录密码校验通过后仍拒绝创建会话，返回 `403` 和“密码已过期，请联系管理员重置密码” |
| 续期边界 | 管理员创建账号或重置密码刷新 `passwordChangedAt`；登录态自助改密成功也刷新 `passwordChangedAt` |
| seed 账号 | `demoAccount=true` 的评审 seed 账号不因 90 天规则过期；生产上线前仍必须停用、删除或替换 |

`AuthUserAccountStatusCommand`：

| 字段 | 说明 |
|---|---|
| `enabled` | `true` 启用，`false` 停用 |
| `reason` | 管理原因，写入账号管理审计原因 |
| `preApprovalApplicationCode` | 可选兼容前置审批申请单号；显式传入时按 `AUTH-PRE-STATUS-001` 校验目标账号和放行状态，未传时直接执行 |

`AuthUserAccountUnlockCommand`：

| 字段 | 说明 |
|---|---|
| `reason` | 解锁原因，写入账号管理审计原因 |
| `preApprovalApplicationCode` | 可选兼容前置审批申请单号；显式传入时按 `AUTH-PRE-STATUS-001` 校验目标账号和放行状态，未传时直接执行 |

返回 `AuthUserAccountSummary`：

| 字段 | 说明 |
|---|---|
| `username` | 登录用户名 |
| `operator` | 操作人快照 |
| `role` / `roleLabel` | 角色编码和中文名 |
| `dataScope` / `dataScopeLabel` | 数据范围编码和中文名 |
| `customerScope` | 账号客户范围；`CUSTOMER_RANGE` 账号用于 Bearer 数据范围过滤，其他数据范围账号为空 |
| `enabled` | 是否启用 |
| `demoAccount` | 是否评审 seed 账号 |
| `passwordChangedAt` | 密码最近变更时间展示值；未知为空字符串 |
| `passwordExpiresAt` | 密码到期时间展示值 |
| `passwordExpired` | 当前账号密码是否已过期 |
| `passwordChangeRequired` | 是否必须先完成首次自助改密；管理员新建/重置非 demo 密码后为 `true`，自助改密成功后为 `false` |
| `failedLoginCount` | 当前连续失败次数 |
| `lockedUntil` | 锁定到期展示值；未锁定为空字符串 |
| `locked` | 当前是否仍在锁定时间内 |
| `allowedProcesses` | 当前可操作工序列表 |

返回 `AuthAccountPreApprovalReadiness`：

| 字段 | 说明 |
|---|---|
| `readinessCode` | 就绪度项编码，如 `AUTH-PRE-CREATE-001` |
| `actionType` | 账号动作类型，如创建生产账号、变更角色/数据范围、管理员重置密码、账号启停/解锁 |
| `scenario` | 触发该前置审批缺口的业务场景 |
| `enforcementStatus` | 兼容前置审批流程的状态说明；不作为用户管理页直接保存的阻断条件 |
| `requiredBeforeAction` | 生产前置审批应具备的申请、审批、复核或交付材料 |
| `missingCapabilities` | 当前缺失的能力，如审批通过态强制校验、客户授权表、正式电子签字、审批后自动放行 |
| `approverRoles` | 建议审批或复核角色 |
| `auditEvidence` | 当前系统已能提供的审计或事后复核证据 |
| `rolloutGate` | 上线门禁说明 |
| `nextAction` | 后续应补的工程动作 |
| `riskLevel` | 风险等级中文值 |
| `tone` | 前端展示色调，当前为 `red` 或 `amber` |

以下前置审批模型和接口仅供历史数据及显式申请兼容，不是用户管理页的默认操作步骤。管理员可不传申请单直接保存；显式使用申请时，必备材料齐全可提交就绪申请，账号、角色、客户及工序范围、具体启停/解锁动作须与放行快照一致，执行后申请为 `EXECUTED`，不得重复使用。旧强制开关不再要求直接账号动作必须带申请单；`missingCapabilities` 仍描述完整身份系统、正式电子签字等边界。V38 及其他历史迁移原文保留。

`AuthAccountPreApprovalDraftCommand`：

| 字段 | 说明 |
|---|---|
| `actionType` | 必填；账号动作类型，当前支持 `CREATE_ACCOUNT`、`CHANGE_ROLE_SCOPE`、`RESET_PASSWORD`、`CHANGE_STATUS`，也兼容对应中文动作名和 readiness code |
| `targetUsername` | 必填；目标账号，后端归一化为小写 |
| `targetOperator` | 目标账号操作人或岗位快照 |
| `targetRole` | 目标角色编码 |
| `targetDataScope` | 目标数据范围编码 |
| `targetCustomerScope` | 目标客户范围列表，执行时与申请快照核对 |
| `targetAllowedProcesses` | 目标账号可操作工序列表，执行时核对完整快照 |
| `statusAction` | 状态动作：`ENABLE`、`DISABLE`、`UNLOCK`；状态申请不得跨动作使用 |
| `targetEnabled` | 目标启停状态 |
| `demoAccount` | 是否评审 seed 账号 |
| `reason` | 必填；申请原因 |
| `providedEvidence` | 已收材料或附件摘要；后端按必备材料名称做包含匹配，计算待补材料 |

返回 `AuthAccountPreApprovalDraft`：

| 字段 | 说明 |
|---|---|
| `draftCode` | 草稿评估编码，当前格式为 `AUTH-PRE-DRAFT-<ACTION>-<USERNAME>` |
| `readinessCode` | 对应 就绪度项编码 |
| `actionType` | 账号动作中文名 |
| `targetUsername` | 目标账号 |
| `applicant` / `applicantRole` | 当前申请人和角色，来自登录会话或显式评审角色头 |
| `requiredEvidence` | 该动作进入阻断式审批前应具备的材料 |
| `providedEvidence` | 请求中已提供并归一化后的材料 |
| `missingEvidence` | 尚缺材料；非空时 `blockingStatus=缺少前置材料` |
| `missingCapabilities` | 当前系统缺失能力，如审批通过态强制校验、正式电子签字、审批后自动放行 |
| `approverRoles` | 建议审批角色 |
| `blockingStatus` | `缺少前置材料`、`系统缺阻断式审批能力` 或 `可进入阻断式审批` |
| `readyForBlockingApproval` | 当前草稿是否已具备进入阻断式审批的全部材料和系统能力；通常为 `false` |
| `sourceSummary` | 草稿评估摘要，包含目标账号、角色、客户范围、原因和 `pre-action draft only` 边界说明 |
| `nextAction` | 下一步补齐动作 |
| `riskLevel` / `tone` | 风险等级和前端展示色调 |

`AuthAccountPreApprovalApplicationCommand`：

字段与 `AuthAccountPreApprovalDraftCommand` 一致。兼容 POST 接口会先复用草稿评估逻辑，再将评估结果保存为申请单；会创建 `ACCOUNT_PRE_APPROVAL` 轻量审批任务并回写 `approvalTaskCode`。该显式接口不执行账号创建/更新/启停/解锁，不生成 `ACCOUNT_MANAGEMENT` 事后审批任务。

返回 `AuthAccountPreApprovalApplication`：

| 字段 | 说明 |
|---|---|
| `applicationCode` | 申请单编码，当前格式为 `AUTH-PRE-APP-<yyyyMMddHHmmss>-<seq>` |
| `draftCode` | 申请单来源草稿评估编码 |
| `readinessCode` / `actionType` | 对应前置审批就绪度项和账号动作中文名 |
| `targetUsername` | 目标账号，后端归一化为小写 |
| `applicant` / `applicantRole` | 申请人和角色，来自登录会话或显式评审角色头 |
| `targetOperator` / `targetRole` / `targetDataScope` / `targetCustomerScope` | 目标账号快照，用于后续组织客户授权和审批差异比对 |
| `targetEnabled` / `demoAccount` | 目标账号启停和是否评审账号快照 |
| `reason` | 申请原因 |
| `requiredEvidence` / `providedEvidence` / `missingEvidence` | 必备材料、已收材料和待补材料 |
| `missingCapabilities` | 当前系统仍缺的阻断式审批能力 |
| `approverRoles` | 建议审批角色 |
| `blockingStatus` | 复用草稿评估的阻断状态 |
| `applicationStatus` | `WAITING_EVIDENCE`、`WAITING_SYSTEM_CAPABILITY` 或 `READY_FOR_BLOCKING_APPROVAL` |
| `readyForBlockingApproval` | 是否已具备进入阻断式审批的材料和系统能力 |
| `sourceSummary` | 包含 `stored pre-approval application`、`approvalTaskCode` 与 `not yet an enforcing IAM workflow` 边界说明 |
| `nextAction` | 下一步补齐动作 |
| `riskLevel` / `tone` | 风险等级和前端展示色调 |
| `approvalTaskCode` | 生成的 `ACCOUNT_PRE_APPROVAL` 轻量审批任务号；为空表示未接入审批服务或历史数据 |
| `submittedAt` | 申请单提交时间，当前为展示用本地时间字符串 |

`auth_account_pre_approval_application` 合同表：

| 字段 | 说明 |
|---|---|
| `application_code` | 申请单唯一编码 |
| `draft_code` | 来源草稿评估编码 |
| `readiness_code` / `action_type` | 前置审批就绪度项和账号动作 |
| `target_username` | 目标账号 |
| `applicant` / `applicant_role` | 申请人快照 |
| `target_operator` / `target_role` / `target_data_scope` / `target_customer_scope` | 目标账号与客户范围快照；列表字段用 `|` 序列化 |
| `target_enabled` / `demo_account` | 目标启停和评审账号标记 |
| `reason` | 申请原因 |
| `required_evidence` / `provided_evidence` / `missing_evidence` / `missing_capabilities` | 前置材料与系统能力缺口；列表字段用 `|` 序列化 |
| `approver_roles` | 建议审批角色，`|` 序列化 |
| `blocking_status` / `application_status` / `approval_task_code` / `ready_for_blocking_approval` | 草稿阻断状态、申请单状态、轻量审批任务号和是否具备进入阻断式审批条件 |
| `source_summary` / `next_action` / `risk_level` / `tone` | 边界摘要、下一步、风险等级和展示色 |
| `submitted_at` | 提交时间字符串 |
| `created_time` / `updated_time` | 数据库写入与更新时间 |

索引：`uk_auth_pre_approval_application_code`、`idx_auth_pre_approval_target_username`、`idx_auth_pre_approval_status`、`idx_auth_pre_approval_readiness`，新增 `idx_auth_pre_approval_task_code`。

`ACCOUNT_PRE_APPROVAL` 轻量审批任务：

| 字段 | 说明 |
|---|---|
| `scene` | 固定为 `ACCOUNT_PRE_APPROVAL` |
| `businessCode` | 申请单 `applicationCode` |
| `sourceStatus` | 申请单 `applicationStatus` |
| `sourceSummary` | 包含申请单号、目标账号、目标角色、数据范围、客户范围、待补材料、系统缺口和 `not final IAM approval` 边界 |
| `evidenceCode` | 当前填申请单号，后续可替换为正式审批附件或电子签字证据 |

管理动作会写入 `AuthAuditEvent`，`eventType` 为 `ACCOUNT_MANAGEMENT`，`eventId` 前缀为 `AUTH-ACC-`，`sessionTokenSuffix` 只保留管理员 token 尾号。不再同时新增 `ApprovalTask` 事后复核摘要，既有 `ACCOUNT_MANAGEMENT` 审批任务保留。

显式传入申请单号时的兼容校验规则：

| 场景 | 校验动作 | 失败条件 |
|---|---|---|
| 创建账号 | `AUTH-PRE-CREATE-001` | 申请单不存在、目标账号不一致、readiness 不一致、`applicationStatus` 不是 `READY_FOR_BLOCKING_APPROVAL` 或 `readyForBlockingApproval=false` |
| 更新账号并重置密码 | `AUTH-PRE-PASSWORD-001` | 同上 |
| 变更角色 / 数据范围 / 客户范围 | `AUTH-PRE-ROLE-001` | 同上 |
| 启用 / 停用 / 管理解锁 | `AUTH-PRE-STATUS-001` | 同上 |

`zhubao.auth.pre-approval.enforce-account-actions` 属于旧兼容配置，不再把未带申请单号的账号管理操作拦截为待审批，即使配置为 `true` 也可直接保存。显式传入申请单号时继续执行匹配、放行及消费校验；旧强制配置为 `true` 时还核对该显式申请的绑定任务为 `APPROVED`。创建/重置非 demo 密码仍要求首次改密。上传证据不自动执行账号动作；正式电子签字、审批后自动放行与归档保全仍属后续范围。

账号创建、更新、启停和解锁在持久化事务内完成账号变更和 `ACCOUNT_MANAGEMENT` 审计，未提供申请时不消费申请。显式提供申请时，同一事务还消费申请：内存实现用单进程锁；JDBC 在一条连接里用条件更新锁住仍为 `READY_FOR_BLOCKING_APPROVAL` 且 `ready_for_blocking_approval=true` 的申请行，再写账号和审计，最后把申请标为 `EXECUTED`。任一步失败则整笔回滚，申请仍可重试；同一申请不能被并发重复使用。`zhubao.auth.account-action.repository` 本地默认 `memory`，MySQL profile 默认 `jdbc`，并与账号、审计、申请仓储一起切换。账号保存时的客户授权替换、删除账号及客户授权关系均使用同一账号事务，历史业务、审计和审批记录不随账号删除。JDBC 账号动作须搭配 JDBC 客户授权仓储，保证使用同一事务连接。

会话保存在进程内存，不能加入上述数据库事务。停用、删除、密码重置及角色/数据范围/客户范围/工序权限更新后吊销目标账号旧会话。停用吊销失败不回滚账号，也不把显式申请改回可执行；调用 `POST /api/auth/accounts/{username}/session-revocations` 只重试已停用账号吊销。解析 Bearer 会话时仍核对账号存在、启用状态及当前权限快照，已删除、停用或权限快照变化的旧令牌不能继续使用。保存或删除已提交后，会话清理异常只记录失败审计并返回已提交的成功结果；旧令牌仍受账号状态及密码、权限快照校验约束。客户授权范围保存同步与独立维护接口继续保留，不新增事后审批任务。

`AuthAccountPreApprovalEvidenceSummary`：

| 字段 | 说明 |
|---|---|
| `applicationCode` | 前置审批申请单号，对应 `auth_account_pre_approval_application.application_code` |
| `readinessCode` / `actionType` | 申请单对应的前置审批类型和账号动作 |
| `targetUsername` | 目标账号 |
| `requiredEvidence` | 当前动作必备附件，如 `本人核验附件`、`岗位交接或本人确认附件` |
| `documentCode` | 已关联附件所属文档编码；未关联时为空 |
| `attachmentCount` / `missingEvidence` | 已匹配附件数量和仍缺失/待上传附件数量 |
| `attachmentCodes` | 已关联的 `EvidenceAttachment.code` 列表 |
| `latestUploadedAt` | 最新附件上传时间快照 |
| `status` | `待补...`、`证据待补` 或 `已关联证据` |
| `nextAction` | 下一步处理说明，缺失时提示使用 `businessType=账号前置审批` 和 `businessCode=申请单号` 绑定附件 |
| `tone` | 前端状态色，沿用 `red`、`amber`、`green` 等枚举 |

### 14.5 菜单树接口

`GET /api/security/menu-tree` 返回当前角色可见菜单树。认证优先级与 `GET /api/security/current-context` 一致：优先读取 Bearer token；无 token 且 `zhubao.security.allow-header-context=true` 时使用 `X-Zhubao-*` 请求头兜底；关闭该开关时返回权限拒绝。

`NavigationMenuItem`：

| 字段 | 说明 |
|---|---|
| `code` | 菜单编码，当前与前端 `App.vue` tab key 对齐 |
| `label` | 菜单中文名 |
| `module` | 菜单业务分组说明 |
| `route` | 菜单路由标识，供前端导航使用 |
| `buttonCodes` | 该菜单关联的敏感按钮权限编码，如 `REGISTER_IMPORT_REVIEW`、`BUSINESS_INBOUND_CONFIRM` |
| `children` | 子菜单列表；当前 先返回一层菜单，保留树形结构 |

角色菜单来自仓储配置，不能把某次 seed 的完整列表作为固定权限契约。默认菜单包含进度、财务、标签和用户管理。标签页编码 `label` 由 V47 写入，授予管理员、总经理、业务、收货和仓管，操作工不可见。用户管理页编码 `users` 由追加迁移 `V48__m3_users_menu.sql` 写入，仅授予 `ADMIN`，对应路由 `/users`。当前角色绑定见[内存菜单仓储](../backend/src/main/java/com/zhubao/erp/security/infra/InMemoryNavigationMenuRepository.java)，MySQL 部署读取 `sys_navigation_menu/sys_role_menu`。新增或调整角色可见性须与后端动作权限分开验证。已有库需备份后受控执行对应迁移，不修改已发布迁移。

菜单与角色可见性使用 `NavigationMenuRepository`，本地默认内存、MySQL profile 默认 JDBC；前端按会话 `menuCodes/menuTree` 过滤导航。导航隐藏不替代后端权限守卫。

### 14.6 当前限制

- token 保存在内存，服务重启即失效。
- 默认 seed 是评审账号，正式环境应初始化生产账号并替换评审凭据。
- 申请单仓储、显式申请单校验与 `ACCOUNT_PRE_APPROVAL` 任务保留兼容；账号管理默认直接保存，不执行电子签字。会话吊销仅覆盖本服务内存 Bearer token，仍需接入完整 IAM、正式刷新 token、SSO/JWT、完整组织授权体系和不可篡改审计策略。
- 菜单树与角色可见性已经支持 `sys_navigation_menu`、`sys_role_menu` 持久化；在线菜单编辑和完整组织树仍未完成。
- 登录审计可切换内存/JDBC 仓储并生成摘要链，但仍不等于生产安全日志平台、归档保全或真正不可篡改审计。
- 登录后导航按会话菜单过滤；用户管理页与账号、客户授权管理接口仅允许 `ADMIN`，具体动作仍须由后端授权。用户管理页直接保存，不依赖前置审批表单。
- `dataScope` 和账号 `customerScope` 已进入业务工作台 GET 首版过滤，客户授权表支持独立维护；完整组织树、跨对象关系推导和全系统行级权限仍待固化。

---

## 15. 当前权限上下文

`GET /api/security/current-context` 返回身份、角色、数据范围、客户范围、敏感动作和菜单树。优先读取 Bearer 会话；无 token 时由 `zhubao.security.allow-header-context` 决定是否接受评审请求头，本地默认开启、MySQL profile 默认关闭。客户范围由账号与客户授权关系计算；强制改密会话必须先自助改密，不能解析为业务上下文。

### 15.1 请求头

认证优先级：

1. `Authorization: Bearer <token>`。
2. `zhubao.security.allow-header-context=true` 时的 轻量权限守卫请求头兜底。

| Header | 说明 |
|---|---|
| `Authorization` | 登录会话 token，格式 `Bearer <token>` |
| `zhubao.security.allow-header-context` | 后端配置项；关闭后无 Bearer token 不再解析下面的请求头 |
| `X-Zhubao-Operator` | 操作人快照 |
| `X-Zhubao-Role` | 当前角色编码 |
| `X-Zhubao-Data-Scope` | 当前数据范围编码 |
| `X-Zhubao-Customer-Scope` | 请求头兜底客户范围；仅在允许请求头身份兜底且 `dataScope=CUSTOMER_RANGE` 时用于评审过滤 |

### 15.2 返回字段

| 字段 | 说明 |
|---|---|
| `operator` | 当前操作人快照 |
| `role` | 角色编码，例如 `ADMIN`、`WAREHOUSE_KEEPER` |
| `roleLabel` | 角色中文名 |
| `dataScope` | 数据范围编码，例如 `ALL`、`TEAM`、`CUSTOMER_RANGE` |
| `dataScopeLabel` | 数据范围中文名 |
| `dataBoundary` | 当前数据范围在真实登录接入后的业务边界说明 |
| `customerScope` | `CUSTOMER_RANGE` 使用的客户范围或业务对象编码范围；Bearer 会话来自账号 `customerScope`，请求头兜底来自 `X-Zhubao-Customer-Scope` |
| `headerBased` | 是否来自请求头；登录会话为 `false`，请求头兜底为 `true` |
| `allowedPermissions` | 当前角色可执行的敏感动作权限列表 |
| `sensitiveActionCodes` | 仅权限编码列表，便于前端做快速展示或按钮边界提示 |
| `menuCodes` | 当前角色可见菜单编码列表 |
| `menuTree` | 当前角色可见菜单树，元素结构同 13.5 `NavigationMenuItem` |
| `limitationNotes` | 当前认证、数据范围、菜单权限和审批锁账限制 |
| `headerContract` | 请求头契约，包含操作人、角色、数据范围和客户范围 Header 名称 |

### 15.3 示例

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
    "dataBoundary": "业务工作台 GET 已按当前角色职责关键词收敛到同班组、同工位或当前负责队列内的业务对象。",
    "customerScope": [],
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
      "dataScopeHeader": "X-Zhubao-Data-Scope",
      "customerScopeHeader": "X-Zhubao-Customer-Scope"
    }
  }
}
```

### 15.4 当前限制

- 业务工作台的范围过滤不等于全系统行级权限；完整组织树和跨对象范围推导仍需完善。
- 菜单和按钮权限由后端仓储返回；前端导航过滤不替代接口授权。
- 菜单表与角色菜单关联表已持久化；按钮权限仍使用权限编码与守卫规则，在线管理和完整组织授权需另行完善。
- 认证和账号操作规则见“认证与账号”；正式电子签字、SSO/JWT 和不可篡改审计仍属待决策或后续实现范围。
- 本接口不返回密码或鉴权密钥；`customerScope` 仅回显当前身份的有效客户范围，客户授权表已支持独立维护。
- 后续接入正式用户中心后，应保持生产关闭匿名管理员请求头兜底，只在测试环境显式启用。

## 16. 财务读取与经营汇总

财务 GET 不再把 `customerId` 当成授权。

| 读取 | 权限 | 范围 |
|---|---|---|
| 客户账单、协议、未计价、带客户的月度经营 | `FINANCE_CUSTOMER_READ` | `ALL` 可查任意客户；`CUSTOMER_RANGE` 只能查授权客户编号或客户名称。空的客户范围不是全公司 |
| 不带客户的月度经营、工资、工人单价、消耗品、固定资产、折旧、应付 | `FINANCE_COMPANY_READ` 且 `dataScope=ALL` | 这些数字不能按客户范围拆开看。客户范围账号即使有财务角色也不能靠省略 `customerId` 看到全公司 |

未带登录会话、也未显式给出 `X-Zhubao-Role` 的请求拒绝。操作工等只能看订单的角色没有上述两个读取权限。现有写入权限和请求头兜底开关不扩大。错误仍走 `40301`。

`GET /api/finance/dashboard?yearMonth=YYYY-MM&customerId=可选` 只读。`data` 含 `yearMonth`、`generatedAt`、`scope`、`operating`、`trend`、`currentBalances`、`receivableRanking`。`trend` 为选择月及向前五个月，当前自然月标记 `throughQueryTime=true`，不计算增幅。`currentBalances` 是截至本次查询的当前未收和未付，不随所选月份改成该月小计。`receivableRanking` 只含未收不为 0 的客户，按未收从高到低，并给出 `total`、`returned`、`truncated`。`capabilities` 写明各块是否可用及原因。公司范围含折旧和全部应付；客户范围不含公司折旧，也不能看到未归集到该客户的公司应付。查询不生成账单、工资或折旧。

`GET /api/finance/dashboard/details?kind=REVENUE|COST|RECEIVABLE|PAYABLE&yearMonth=收入和成本必填&customerId=可选&page=1&size=20` 只读。`totalAmount` 是筛选后的全量合计，不是当前页相加。收入和成本跟随月份与范围，合计应与同一范围的 `operating` 对应金额一致。未收和应付忽略月份，使用当前时点，合计应与 `currentBalances` 一致。分页 `page` 从 1 开始，`size` 最大 100。

财务页默认进入经营总览，原有登记和收付仍从同一页的二级入口进入。总览只调用上述两个 GET，打开页面不生成账单、工资、折旧或核销。已入账加工收入和经营结余跟随所选月份；当前未收和应付未付标明 `currentBalances.asOf`，不按月份改成该月小计。客户未收按 `receivableRanking.rows` 的返回顺序展示，前端不重排，也不把本页或分页明细相加当作总额。`gaps` 有内容时页面标暂估并显示原因。`currentBalances.limitation` 有内容时标明未归集范围；公司应付未进入该范围时不能显示成 0。客户范围的经营结余不是全公司净利润。明细合计使用返回的 `totalAmount`。

### 16.1 经营汇总请求与返回

| 字段 | 类型与说明 |
|---|---|
| `yearMonth` | 必填查询参数，格式 `YYYY-MM`；不合法月份拒绝 |
| `customerId` | 可选客户编号；提供时校验客户读取权限及有效客户范围，省略时要求全公司读取权限 |
| `generatedAt` | 汇总生成时间 |
| `scope.kind/customerIds/note` | 实际查询范围、已授权客户编号和范围说明，不回传未授权客户 |
| `operating` | `OperatingStatement`：`yearMonth/customerId/revenue/wageCost/consumableCost/depreciationCost/outsourceCost/result/gaps` |
| `trend[]` | 每项包含 `yearMonth/throughQueryTime/operating`；共六个月，当月仅截至查询时刻 |
| `currentBalances` | `asOf`、`outstandingAmount`、`unpaidAmount`、`limitation`。截至本次查询；`limitation` 说明未归集的公司应付没有算进客户范围 |
| `receivableRanking` | `total`、`returned`、`truncated`、`rows[]`。每行是 `customerId`、`customerName`、`outstandingAmount`。没有未收时 `rows` 为空且 `total=0`，这表示查过且没有余额 |
| `capabilities[]` | 每项包含 `part/available/reason`，不可用能力明确给出原因 |

经营收入按应收入账月份统计，收款不重复计收入；客户范围不分摊公司折旧。查询只读取，不生成账单、工资或折旧。完整类型见[FinanceDashboard](../backend/src/main/java/com/zhubao/erp/finance/domain/FinanceDashboard.java)与[OperatingStatement](../backend/src/main/java/com/zhubao/erp/finance/domain/OperatingStatement.java)。

## 17. 现行端点索引

本索引按当前控制器映射核对。相对路径与所属控制器前缀已合并；字段详表在前文，未展开的新类型以控制器和对应领域模型为准。后端权限以控制器及 `PermissionGuardInterceptor` 为准，不能因 HTTP GET 或导航可见而推断允许匿名读取。

### ApprovalController

来源：[ApprovalController](../backend/src/main/java/com/zhubao/erp/approval/web/ApprovalController.java)。

| 方法与完整路径 | 返回类型 |
|---|---|
| `GET /api/approvals/tasks` | `ApiResponse<List<ApprovalTask>>` |
| `GET /api/approvals/tasks/pending` | `ApiResponse<List<ApprovalTask>>` |
| `GET /api/approvals/tasks/{approvalCode}` | `ApiResponse<ApprovalTask>` |
| `POST /api/approvals/tasks/{approvalCode}/decisions` | `ApiResponse<ApprovalTask>` |

### BusinessDocumentController

来源：[BusinessDocumentController](../backend/src/main/java/com/zhubao/erp/business/web/BusinessDocumentController.java)。

| 方法与完整路径 | 返回类型 |
|---|---|
| `GET /api/business/documents/mother-orders/{code}` | `ApiResponse<MotherOrderRecord>` |
| `GET /api/business/documents/outbound-orders/{code}` | `ApiResponse<OutboundOrderRecord>` |

### BusinessOperationController

来源：[BusinessOperationController](../backend/src/main/java/com/zhubao/erp/business/web/BusinessOperationController.java)。

| 方法与完整路径 | 返回类型 |
|---|---|
| `GET /api/business/operations/mobile-intakes` | `ApiResponse<MobileIntakeLookup>` |
| `POST /api/business/operations/mobile-intakes` | `ApiResponse<MobileIntakeResult>`。`requestId` 必填；动作为 `INTAKE_CREATE`，目标类型 `REQUEST`。同账号同请求同内容回放原母单；新 `requestId` 可在同运单下新建另一张母单。`sourceType` 为 `COURIER`、`CUSTOMER_DELIVERY` 或 `INTERNAL_OTHER`；运单与快递公司均可空。新版传稳定 `customerId`、`customerName` 快照及 `lines[]`（`materialCode`、`goodsName`、`quantity`、`quantityUnit`、`weight`）；母单总数量和重量须等于明细合计。当前母单汇总和后续工序使用单一数量单位，明细单位不一致时拒绝建单。每行入对应材质的 `RAW_WAITING` 台账，整单在同一工作单元提交。旧客户端未带 `requestId` 时明确拒绝并提示升级，防止超时重复入账。 |
| `GET /api/business/operations/mobile-intakes?courierNo=...` | 返回全部可见母单的 `candidates[]`，含母单号、客户、收货时间、货品、要求和期限。兼容字段 `motherOrderCode` 仅在恰好一张候选时有值，多张时为空，调用方不得取第一张。 |
| `GET /api/business/master-data/customers` | 返回当前账号可见的客户档案，使用稳定客户 ID；改名不回写历史单据快照。全部数据范围，以及未填写客户名单的部门、班组或本人范围，返回全部档案；授权客户范围只返回名单内档案。 |
| `POST /api/business/master-data/customers` | `MASTER_DATA_MANAGE`（管理员、总经理、收货员、库管员、车间组长）创建或更新客户；名称归一化后不可重复。授权客户范围的账号新建客户后，该客户进入本人范围，便于当场建母单。 |
| `customerScope`（登录账号／客户授权） | 新增授权在用户管理按客户档案选择稳定数字 ID。服务端在会话解析时将历史名称或客户编码唯一映射为 ID；客户改名保留旧名别称。无法确认唯一归属的历史值仍留在原授权记录供人工核对，但不授予新单据访问权。 |
| `GET /api/business/master-data/materials` | 返回启用材质目录，支持前端搜索和多选。 |
| `POST /api/business/master-data/materials` | 同上 `MASTER_DATA_MANAGE` 角色可新增或更新材质；编码稳定，名称去重。新建母单页可直接新增并选中。 |
| `GET /api/business/operations/mother-orders/{code}/split` | 新版 `sourceLines[]` 显示收货来源行及逐行剩余数量、重量；无新明细的历史单据保留原汇总视图。 |
| `POST /api/business/operations/mother-orders/{code}/splits` | 新版每张子单的 `allocations[]` 指定 `sourceLineNo`、数量和重量；服务端锁定母单，核对每行剩余，并按真实材质转在制。现有下游工序按单一材质维度处理，一张子单只能分配同一材质的来源行。旧单继续接受原请求形态。 |
| `GET /api/business/operations/mother-orders/{code}` | `ApiResponse<MobileIntakeResult>` |
| `GET /api/business/operations/mother-orders/{code}/split` | `ApiResponse<MotherOrderSplitView>` |
| `POST /api/business/operations/mother-orders/{code}/keep` | `ApiResponse<MotherOrderSplitView>`。必须带 `requestId`。动作是 `MOTHER_KEEP`，拆分决定和成功结果同一次提交。同账号同请求回放后重新读取当前视图。缺编号直接拒绝 |
| `POST /api/business/operations/mother-orders/{code}/splits` | `ApiResponse<MotherOrderSplitView>`。必须带 `requestId`。动作是 `MOTHER_SPLIT`，库存转移、子单和母单拆分状态同一次提交。同一请求只生效一次；内容变化必须换新编号。缺编号直接拒绝 |
| `POST /api/business/operations/inbound-confirmations` | `ApiResponse<InboundConfirmationResult>` |
| `GET /api/business/operations/process-stations` | `ApiResponse<ProcessStationView>` |
| `POST /api/business/operations/process-stations` | `ApiResponse<ProcessStationReportResult>`。可选 `requestId`。有值时动作是 `PROCESS_HANDOFF`，同账号同请求同内容回放原结果。不拆母单首次转在制、工序流水、交付事实和成功结果同一次提交。无 `requestId` 时不回放、不保证同事务 |
| `GET /api/business/operations/quality-checks` | `ApiResponse<QualityCheckView>` |
| `POST /api/business/operations/quality-checks` | `ApiResponse<QualityCheckResult>`。可选 `requestId`。有值时动作是 `QUALITY_CHECK`，初检轮次、适用冻结和成功结果同一次提交 |
| `POST /api/business/operations/quality-checks/rework` | `ApiResponse<QualityReworkResult>`。可选 `requestId`。有值时动作是 `QUALITY_REWORK` |
| `POST /api/business/operations/quality-checks/rework-reports` | `ApiResponse<ReworkReportResult>`。可选 `requestId`。有值时动作是 `REWORK_REPORT` |
| `POST /api/business/operations/quality-checks/rechecks` | `ApiResponse<QualityCheckResult>`。可选 `requestId`。有值时动作是 `QUALITY_RECHECK`，结果用轮次号区分，不覆盖初检 |
| `POST /api/business/operations/quality-checks/releases` | `ApiResponse<QualityReleaseResult>`。可选 `requestId`。有值时动作是 `QUALITY_RELEASE`，按最新轮次放行 |
| `POST /api/business/operations/outbound-shipments` | `ApiResponse<OutboundShipmentResult>`。必须带 `requestId`。动作是 `OUTBOUND_SHIPMENT`，库存流水、出库单和成功结果同一次提交。缺编号直接拒绝 |
| `POST /api/business/operations/customer-returns` | `ApiResponse<CustomerReturnResult>` |
| `POST /api/business/operations/after-sales-reviews` | `ApiResponse<AfterSalesReviewResult>` |
| `POST /api/business/operations/loss-approvals` | `ApiResponse<LossApprovalResult>` |

### BusinessWorkspaceController

来源：[BusinessWorkspaceController](../backend/src/main/java/com/zhubao/erp/business/web/BusinessWorkspaceController.java)。

| 方法与完整路径 | 返回类型 |
|---|---|
| `GET /api/business/workspaces/inbound` | `ApiResponse<InboundWorkspaceData>` |
| `GET /api/business/workspaces/production` | `ApiResponse<ProductionWorkspaceData>` |
| `GET /api/business/workspaces/quality` | `ApiResponse<QualityWorkspaceData>` |
| `GET /api/business/workspaces/inventory` | `ApiResponse<InventoryWorkspaceData>` |
| `GET /api/business/workspaces/mobile` | `ApiResponse<MobileWorkspaceData>` |
| `GET /api/business/workspaces/summary` | `ApiResponse<BusinessWorkspaceSummary>` |

### ProgressBoardController

来源：[ProgressBoardController](../backend/src/main/java/com/zhubao/erp/business/web/ProgressBoardController.java)。

| 方法与完整路径 | 返回类型 |
|---|---|
| `GET /api/business/operations/progress` | `ApiResponse<List<ProgressRow>>` |
| `GET /api/business/operations/process-progress` | `ApiResponse<List<ProcessProgressRow>>`。按账号可见范围列出执行单当前工序百分比，不合成母单总进度 |
| `GET /api/business/operations/process-progress/target` | `ApiResponse<ProcessProgressTarget>`。已拆母单返回子单选项 |
| `GET /api/business/operations/process-progress/history` | `ApiResponse<List<ProcessProgressEvent>>` |
| `POST /api/business/operations/process-progress` | `ApiResponse<ProcessProgressHead>`。必须带 `requestId`。动作 `PROCESS_PROGRESS`。只允许 0/30/60/90/100，回退必须写原因。100% 不交付工序、不质检、不写库存或工资 |

### DocumentManagementController

来源：[DocumentManagementController](../backend/src/main/java/com/zhubao/erp/document/web/DocumentManagementController.java)。

| 方法与完整路径 | 返回类型 |
|---|---|
| `GET /api/documents/captures` | `ApiResponse<List<DocumentCaptureView>>` |
| `POST /api/documents/captures` | `ApiResponse<EvidenceAttachmentUploadResult>` |
| `POST /api/documents/evidence-files` | `ApiResponse<EvidenceAttachmentUploadResult>` |
| `POST /api/documents/evidence-links` | `ApiResponse<BusinessEvidenceLink>` |
| `GET /api/documents/evidence-links` | `ApiResponse<EvidenceBoard>` |
| `GET /api/documents/summary` | `ApiResponse<DocumentManagementSummary>` |
| `GET /api/documents/packages` | `ApiResponse<List<DocumentPackage>>` |
| `GET /api/documents/attachments` | `ApiResponse<List<EvidenceAttachment>>` |
| `GET /api/documents/storage` | `ApiResponse<StoredContentInventory>` |
| `GET /api/documents/audit-events` | `ApiResponse<List<DocumentAuditEvent>>` |
| `POST /api/documents/attachments/uploads` | `ApiResponse<EvidenceAttachmentUploadResult>` |
| `POST /api/documents/attachments/{code}/retirements` | `ApiResponse<StoredContentItem>` |
| `GET /api/documents/attachments/{code}/preview-url` | `ApiResponse<AttachmentPreviewUrl>` |
| `GET /api/documents/attachments/{code}/content` | `ResponseEntity<byte[]>` |

### FinanceController

来源：[FinanceController](../backend/src/main/java/com/zhubao/erp/finance/web/FinanceController.java)。

| 方法与完整路径 | 返回类型 |
|---|---|
| `GET /api/finance/price-agreements` | `ApiResponse<List<PriceAgreement>>` |
| `POST /api/finance/price-agreements` | `ApiResponse<PriceAgreement>` |
| `POST /api/finance/billing-runs` | `ApiResponse<BillingRunResult>` |
| `GET /api/finance/unpriced` | `ApiResponse<List<UnpricedItem>>` |
| `POST /api/finance/receivable-reversals` | `ApiResponse<ReceivableLine>` |
| `POST /api/finance/receipts` | `ApiResponse<CustomerReceipt>` |
| `GET /api/finance/statements` | `ApiResponse<CustomerStatement>` |
| `GET /api/finance/worker-rates` | `ApiResponse<List<WorkerRate>>` |
| `POST /api/finance/worker-rates` | `ApiResponse<WorkerRate>` |
| `GET /api/finance/payroll-lines` | `ApiResponse<List<PayrollLine>>` |
| `POST /api/finance/payroll-runs` | `ApiResponse<PayrollGenerateResult>` |
| `POST /api/finance/payroll-runs/confirmations` | `ApiResponse<PayrollRun>` |
| `GET /api/finance/consumable-items` | `ApiResponse<List<ConsumableStockView>>` |
| `GET /api/finance/consumable-movements` | `ApiResponse<List<ConsumableMovement>>` |
| `POST /api/finance/consumable-items` | `ApiResponse<com.zhubao.erp.finance.domain.ConsumableItem>` |
| `POST /api/finance/consumable-purchases` | `ApiResponse<ConsumableMovement>` |
| `POST /api/finance/consumable-issues` | `ApiResponse<ConsumableMovement>` |
| `GET /api/finance/fixed-assets` | `ApiResponse<List<FixedAsset>>` |
| `POST /api/finance/fixed-assets` | `ApiResponse<FixedAsset>` |
| `GET /api/finance/depreciation-entries` | `ApiResponse<List<DepreciationEntry>>` |
| `POST /api/finance/depreciation-runs` | `ApiResponse<List<DepreciationEntry>>` |
| `POST /api/finance/outsource-bills` | `ApiResponse<Payable>` |
| `GET /api/finance/payables` | `ApiResponse<List<PayableView>>` |
| `POST /api/finance/payable-payments` | `ApiResponse<PayablePayment>` |
| `GET /api/finance/operating-statement` | `ApiResponse<OperatingStatement>` |
| `GET /api/finance/dashboard` | `ApiResponse<FinanceDashboard>`。含当前余额和未收排行 |
| `GET /api/finance/dashboard/details` | `ApiResponse<FinanceDetailPage>`。`kind` 为 `REVENUE`、`COST`、`RECEIVABLE`、`PAYABLE` |

`POST /api/finance/payable-payments` 的请求体在应付单号、金额、付款日之外可以带 `idempotencyKey`，最长 80 字符。余额检查、付款流水和已付金额在同一事务中完成。JDBC 先锁应付行，再用 `paid_amount + 本次金额 <= amount` 条件更新；内存用同一把锁。流水合计与已付余额保持一致。同一幂等键且应付单、金额、付款日相同的重试返回原来的付款单，不新增流水；同一键但内容不同则拒绝。并发使用同一键只产生一笔付款。不传键时仍禁止超付，但不保证网络重试去重。已有库需在备份后受控执行 V40，为 `fin_payable_payment` 增加可空的 `idempotency_key`。该变更不修改 V36。

`POST /api/finance/receipts` 仍先按服务端会话核对客户写入范围，请求里的客户名称不能代替授权。请求体可以带 `idempotencyKey`，最长 80 字符。收款单和核销行在同一事务写入。同一键且客户、金额、收款日、客户名称和核销明细一致时返回原收款单；内容不同则拒绝；并发同键只产生一笔收款。不传键时不去重。登记成功后如果账单查询失败，页面保留并展示收款单号，只重试查询，不把这次失败当成收款失败。已有库另需受控执行 V41，为 `fin_customer_receipt` 增加可空的 `idempotency_key`。V36 的 `year_month` 仍未修改，真实 MySQL 8.4 空库升级仍会停在 V36，因此 V40 和 V41 还不能在那条失败路径之后自动执行。

### DeviceGatewayController

来源：[DeviceGatewayController](../backend/src/main/java/com/zhubao/erp/gateway/web/DeviceGatewayController.java)。

| 方法与完整路径 | 返回类型 |
|---|---|
| `POST /api/hardware/gateway/readings` | `ApiResponse<DeviceReading>` |
| `GET /api/hardware/gateway/readings` | `ApiResponse<List<DeviceReading>>` |
| `GET /api/hardware/gateway/devices/{deviceCode}/readings` | `ApiResponse<List<DeviceReading>>` |
| `GET /api/hardware/gateway/devices` | `ApiResponse<List<GatewayDeviceView>>` |
| `GET /api/hardware/gateway/summary` | `ApiResponse<GatewayIngestionSummary>` |
| `POST /api/hardware/gateway/consumptions` | `ApiResponse<ReadingConsumption>` |
| `GET /api/hardware/gateway/consumptions` | `ApiResponse<List<ReadingConsumption>>` |
| `POST /api/hardware/gateway/devices` | `ApiResponse<GatewayDeviceView>` |
| `POST /api/hardware/gateway/devices/{deviceCode}/status` | `ApiResponse<GatewayDeviceView>` |
| `POST /api/hardware/gateway/devices/{deviceCode}/rotate-key` | `ApiResponse<GatewayDeviceView>` |

### HardwareIntegrationController

来源：[HardwareIntegrationController](../backend/src/main/java/com/zhubao/erp/hardware/web/HardwareIntegrationController.java)。

| 方法与完整路径 | 返回类型 |
|---|---|
| `GET /api/hardware/devices` | `ApiResponse<List<HardwareDevice>>` |
| `GET /api/hardware/integration-tasks` | `ApiResponse<List<IntegrationTask>>` |
| `GET /api/hardware/poc-records` | `ApiResponse<List<HardwarePocRecord>>` |
| `GET /api/hardware/poc-evidence` | `ApiResponse<List<HardwarePocEvidenceSummary>>` |
| `GET /api/hardware/summary` | `ApiResponse<HardwareSummary>` |

### InventoryController

来源：[InventoryController](../backend/src/main/java/com/zhubao/erp/inventory/web/InventoryController.java)。

| 方法与完整路径 | 返回类型 |
|---|---|
| `POST /api/inventory/ledger/transfers` | `ApiResponse<StockLedgerEntry>` |
| `GET /api/inventory/prep-stock/customer` | `ApiResponse<List<StockBalance>>`。客户备料批次，母单为空 |
| `GET /api/inventory/prep-stock/factory` | `ApiResponse<List<StockBalance>>`。厂内未发货实物，客户范围账号看不到其他客户和公司料 |
| `GET /api/inventory/prep-stock/company` | `ApiResponse<List<StockBalance>>`。公司自有生产物料，仓库、财务、管理员或总经理可见 |
| `POST /api/inventory/prep-stock/customer-receipts` | `ApiResponse<String>`。必须带 `requestId`。不建加工母单 |
| `POST /api/inventory/prep-stock/issues` | `ApiResponse<String>`。必须带 `requestId`。同一客户备料批次领用到真实加工母单 |
| `POST /api/inventory/prep-stock/company-receipts` | `ApiResponse<String>`。必须带 `requestId`。权属始终为公司 |
| `POST /api/inventory/prep-stock/returns` | `ApiResponse<String>`。必须带 `requestId`。从加工母单待加工退回原备料批次 |
| `POST /api/inventory/prep-stock/company-issues` | `ApiResponse<String>`。必须带 `requestId`。公司料领用，权属保持公司，待加工转入在制 |
| `POST /api/inventory/prep-stock/company-returns` | `ApiResponse<String>`。必须带 `requestId`。公司料从在制退回待加工 |
| `GET /api/inventory/ledgers` | `ApiResponse<List<StockLedgerEntry>>` |
| `GET /api/inventory/balances` | `ApiResponse<List<StockBalance>>` |

### LabelController

来源：[LabelController](../backend/src/main/java/com/zhubao/erp/label/web/LabelController.java)。

| 方法与完整路径 | 返回类型 |
|---|---|
| `POST /api/label/generate` | `ApiResponse<LabelPayload>` |
| `GET /api/label/mother-orders/{code}` | `ApiResponse<LabelPayload>` |
| `GET /api/label/work-orders/{code}` | `ApiResponse<LabelPayload>` |
| `POST /api/label/reprints` | `ApiResponse<LabelPayload>` |
| `POST /api/label/print-jobs` | 旧版创建入口已停止接单，返回升级提示；改用打印批次接口并明确选择工位及队列。旧任务快照仍保留。 |
| `GET /api/label/print-jobs/{id}` | `ApiResponse<PrintJob>`。`SPOOLED` 只表示已送入系统队列 |
| `POST /api/label/print-jobs/{id}/retries` | 旧版补打入口已停止接单；核纸后以新批次明确选择目标。 |
| `POST /api/label/print-jobs/{id}/paper-checks` | `ApiResponse<PrintJob>`。人工核实出纸，不把任务改成已出纸 |
| `POST /api/label/print-jobs/{id}/relabels` | `ApiResponse<PrintJob>`。记录实物换贴，不表示打印队列已经出纸 |
| `POST /api/label/agents/jobs/claim` | 旧版工位领取入口已停用，返回升级提示。 |
| `POST /api/label/agents/jobs/{id}/report` | 旧版工位回报入口已停用，返回升级提示。 |
| `GET /api/label/print-stations`、`GET /api/label/print-queues` | 读取工位公开信息和当前启用队列；不返回工位凭据或 Windows 队列名。 |
| `POST /api/label/print-stations`、`POST /api/label/print-stations/{id}/rotate-token` | 仅管理员维护工位并轮换独立凭据；新凭据仅在创建／轮换响应中显示一次，服务端保存摘要。 |
| `GET /api/label/print-queues/all`、`POST /api/label/print-queues` | 仅管理员读取全部队列或维护逻辑队列；队列限定标签类型与纸张毫米尺寸。 |
| `POST /api/label/print-batches` | 请求含 `requestId`、`businessCode`、`templateCode`、`copiesPerTarget`、`targets[]`（`stationId`、`queueId`）。服务端去重目标、核对权限及队列能力、冻结标签快照，并在同一事务创建批次和每目标任务；同请求同内容回放原批次，内容变化拒绝。 |
| `GET /api/label/print-batches/{id}` | 返回批次和各目标任务的状态、失败原因、核纸结果；`SPOOLED` 仅表示进入系统队列。 |
| `POST /api/label/print-target-jobs/{id}/paper-checks` | 人工按目标记录一次 `result`；相同结果可重试，不允许直接覆盖已有核纸结论，不把队列状态伪装成已出纸。 |
| `POST /api/label/print-target-jobs/{id}/reroute` | 请求含新 `requestId`、`target`（`stationId`、`queueId`）和 `reason`。未领取的 `PENDING` 可直接取消；`FAILED`、`SPOOLED`、`UNCERTAIN` 须先核纸并记录精确结果 `确认未出纸`。原任务以条件更新变为 `CANCELED`，同一事务创建保留原快照及份数的新批次／关联目标任务；重复请求回放原改派批次，不允许同一原任务多次改派。`LEASED` 禁止改派。 |
| `POST /api/label/agents/v2/jobs/claim`、`POST /api/label/agents/v2/jobs/{id}/report` | 以工位 ID 和独立令牌鉴权，只领取目标工位的任务，返回目标队列 ID；厂内程序按 `queue.<队列 ID>` 映射本机 Windows 队列，缺失映射或队列不可用时回报 `FAILED`，不回退系统默认打印机。 |

V61 新增工位、队列、批次和目标任务表；原任务的 `PENDING`／`LEASED` 因无明确目标转为 `UNCERTAIN` 供人工核纸，原快照不改写。旧版打印任务创建、补打、工位领取和回报入口返回升级提示；旧任务仍可查询与人工核纸。新批次需管理员先建立工位、队列并把凭据及队列映射配置到厂内程序。
| `GET /api/label/templates` | `ApiResponse<List<LabelTemplate>>`。含字段顺序、打印机设置和 `layout` |
| `POST /api/label/templates` | `ApiResponse<LabelTemplate>`。`layout` 支持 `mode=AUTO/COMPACT/STANDARD`、`qrWidthMm`（5～100 mm）、`codeFontPt/fieldFontPt/emphasisFontPt/titleFontPt/footerFontPt`（4～36 pt）；数值留空表示沿用版式默认值，不接收任意 CSS |

`LabelPayload` 与 `LabelTemplate` 的 `layout` 结构相同；历史模板的 `layout_json` 为空时按 `AUTO` 和默认字号读取。模板字段列表为空表示不印额外字段，二维码与主单号仍保留。MySQL 通过追加迁移 V57 增加 `sys_label_template.layout_json`，不覆盖既有模板内容。

### MachineOutputController

来源：[MachineOutputController](../backend/src/main/java/com/zhubao/erp/machine/web/MachineOutputController.java)。

| 方法与完整路径 | 返回类型 |
|---|---|
| `GET /api/machine-outputs` | `ApiResponse<List<MachineOutputRecord>>` |
| `GET /api/machine-outputs/summary` | `ApiResponse<MachineOutputSummary>` |
| `GET /api/machine-outputs/machine-match-suggestions` | `ApiResponse<List<MachineOutputMachineMatchSuggestion>>` |
| `GET /api/machine-outputs/import-batches/{importBatchNo}` | `ApiResponse<List<MachineOutputRecord>>` |
| `POST /api/machine-outputs` | `ApiResponse<MachineOutputRecord>` |
| `POST /api/machine-outputs/machine-bindings` | `ApiResponse<MachineOutputBindingResult>` |

### MachineProfileController

来源：[MachineProfileController](../backend/src/main/java/com/zhubao/erp/machine/web/MachineProfileController.java)。

| 方法与完整路径 | 返回类型 |
|---|---|
| `GET /api/machine-profiles` | `ApiResponse<List<MachineProfile>>` |
| `POST /api/machine-profiles` | `ApiResponse<MachineProfile>` |

### OperationsCenterController

来源：[OperationsCenterController](../backend/src/main/java/com/zhubao/erp/operations/web/OperationsCenterController.java)。

| 方法与完整路径 | 返回类型 |
|---|---|
| `GET /api/operations/center` | `ApiResponse<OperationsCenterSnapshot>` |

### RegisterImportController

来源：[RegisterImportController](../backend/src/main/java/com/zhubao/erp/register/web/RegisterImportController.java)。

| 方法与完整路径 | 返回类型 |
|---|---|
| `GET /api/register-import/templates` | `ApiResponse<List<RegisterImportTemplate>>` |
| `GET /api/register-import/summary` | `ApiResponse<RegisterImportTemplateService.RegisterImportSummary>` |
| `POST /api/register-import/previews` | `ApiResponse<RegisterImportPreview>` |
| `POST /api/register-import/batches/confirm` | `ApiResponse<RegisterImportBatch>` |
| `GET /api/register-import/batches` | `ApiResponse<List<RegisterImportBatch>>` |
| `GET /api/register-import/batches/{importBatchNo}` | `ApiResponse<RegisterImportBatch>` |
| `POST /api/register-import/batches/{importBatchNo}/rows/{sourceLineNo}/corrections` | `ApiResponse<RegisterImportBatch>` |
| `GET /api/register-import/batches/{importBatchNo}/posting-plan` | `ApiResponse<RegisterImportPostingPlan>` |
| `POST /api/register-import/batches/{importBatchNo}/posting-commands` | `ApiResponse<RegisterImportPostingExecutionResult>` |

### ScanController

来源：[ScanController](../backend/src/main/java/com/zhubao/erp/scan/web/ScanController.java)。

| 方法与完整路径 | 返回类型 |
|---|---|
| `POST /api/scan/resolve` | `ApiResponse<ScanResolveResponse>` |
| `POST /api/scan/context` | `ApiResponse<ScanContextResponse>` |
| `POST /api/scan/progress-records` | `ApiResponse<ScanProgressRecord>` |

`POST /api/scan/resolve` 保持兼容：请求仍是 `content` 和可选 `scene`，响应 `objectType`、`code`、`nextAction`、`allowed`。`allowed` 固定为 `true`，只说明格式能识别，不是权限结论，当前不要求登录。

`POST /api/scan/context` 要求登录会话。请求字段：

| 字段 | 说明 |
|---|---|
| `content` | 必填。贴纸、运单或手输内容 |
| `objectType` | 可选。`MOTHER_ORDER`、`WORK_ORDER`、`OUTBOUND_ORDER`、`REWORK`、`EXPRESS_NO`。与 `JG\|` 贴纸类型冲突时拒绝 |
| `intent` | 可选。`LOOKUP`、`INTAKE`、`PRODUCTION`、`QUALITY`、`SHIPPING`。省略视为 `LOOKUP`。不能代替岗位权限 |

响应分组为 `target`、`order`、`requirements`、`progress`、`relatedTargets`、`requiresSelection`、`actions`、`operationContext`。未知数量、重量和客户用空值，不补 0。母单要求与子单快照分字段保留，来源分别为 `MOTHER_ORDER`、`WORK_ORDER`。岗位要求来源为 `PROCESS`。`progress.progressRecords` 返回该单据已保存的过程说明；没有记录时为 `[]`，不编造。`progress.reinspection.available` 只在已有合格或让步的复检轮次时为 `true`，原因仍说明原不合格记录保留。没有这种复检结论时为 `false`，不能把原不合格当成已经放行。

`progress.progressRecords[]` 字段：

| 字段 | 说明 |
|---|---|
| `code` | 服务端过程记录编号，形如 `PG-` 开头 |
| `targetType` | `MOTHER_ORDER` 或 `WORK_ORDER` |
| `targetCode` | 真实单据编号 |
| `motherOrderCode` | 该单据所属母单，由服务端读取 |
| `stage` | `PRODUCTION`、`QUALITY`、`SHIPPING`。中文只在界面映射为生产过程、质检过程、发货准备 |
| `process` | 工序。生产说明可填；其他阶段可空 |
| `clauseNo` | 条款号，可空 |
| `note` | 规范化后的说明 |
| `operatorId` | 当前登录账号 |
| `operatorName` | 保存时的操作人姓名快照 |
| `operatorRole` | 保存时的岗位代码快照 |
| `supplementary` | 正式业务已经完成时为 `true`，表示这是补充记录，不把单据改回进行中 |
| `recordedAt` | 首次写入时间。重试返回原时间 |
| `requestId` | 客户端本次说明的请求编号 |

`POST /api/scan/progress-records` 要求登录。请求只接受 `targetType`、`targetCode`、`motherOrderCode`、`stage`、`process`、`clauseNo`、`note`、`requestId`。客户端另传姓名、岗位、数量、重量或完成百分比都会被忽略，不能据此扩大权限。保存前同时核对真实单据、当前读取权限和阶段对应岗位权限：生产过程要有生产报工权限，质检过程要有质检权限，发货准备要有成品出库权限。已拆母单不能把过程记在母单上。不拆母单可以记在母单上。母单号必须与服务端单据一致，不能因为客户相同就跨母子单记录。相同 `requestId`、同一账号、同一目标和同一规范化说明重试时返回原记录；说明变了或目标变了不能合并。该请求编号不能跨客户或跨单重放。此接口不写库存、工资、工序交付、质检结论或出货单，也不是正式命令的幂等回放。

过程说明保存成功后，证据绑定使用 `operationType=progress_note`，`operationCode` 为上面的 `code`。记录不存在不能绑定。上传或绑定失败只保留记录编号，不重新调用本接口。

`actions[].code` 只使用 `SELECT_CHILD`、`INTAKE`、`REPORT_PROCESS`、`SUBMIT_QUALITY`、`OPEN_REWORK`、`REPORT_REWORK`、`RELEASE`、`SHIP`、`FILL_LOGISTICS`。`blockKind` 为 `NONE`、`PERMISSION` 或 `STATE`。这些代码不是可执行的接口地址。`operationContext` 给出余额候选和事实快照；多笔余额时 `selectedBalance` 为空。已拆母单 `requiresSelection=true`，不把第一张子单写入 `target.workOrderCode`。

发货意图下，纯运单只返回规范化物流号和 `FILL_LOGISTICS`，不带回入库母单。收货意图下，运单已有母单则打开原单且 `INTAKE` 不可再次建单；没有母单时订单字段为空，有收货权限才允许 `INTAKE`。有限数据范围下，无权和不存在都返回「单号不在当前账号的业务数据范围内」，响应不带客户、要求和关联单。全公司范围明确查无此单时返回「未找到对应单据」。上下文查询不写库存、工序、质检或出货单。

S2 起，收货、岗位报工、质检贴纸和标签现场贴纸改为调用上下文接口。S4 起，手机岗位、桌面岗位、质检和发货准备可以追加过程说明，保存逻辑相同。前端不再按编码长度把业务编码猜成运单，也不再用本地解析结果作为提交单号。`requiresSelection` 为真时必须由用户选择，查询失败不能回落到页面默认单据。扫码成功不等于已经留证或已经报工。过程说明保存在服务端，迁移为 `V43__m3_operation_progress.sql`。本地默认 `zhubao.scan-progress.repository=memory`，MySQL 配置默认 JDBC。已有库需备份后受控执行 V43，不修改 V36 或 V42。

S5p 增加内部工作单元 `BusinessWorkUnit`，没有新的 HTTP 接口。请求记在 `biz_command_request`，迁移为 `V44__m3_command_request.sql`。同账号、同 `requestId`、同内容回放 `resultText`；内容不一致返回「同一请求的内容不一致」；其他账号返回「该请求不能跨账号重放」。成功结果和本次通过工作单元连接写入的事实同一次提交。S5p 当时来料、报工、质检、发货都还没接入。S5a 已接入来料建单和带 `requestId` 的入库确认。S5b 已接入带 `requestId` 的工序交付。S5c 已接入带 `requestId` 的初检、开返工、返工报工、复检和放行，迁移 `V46__m3_quality_round.sql`。S5d 已接入带 `requestId` 的确认发货。未带 `requestId` 的旧质检调用、通用 `production-reports` 和确认发货仍各自提交。已有库需备份后受控执行 V44 与 V46，不修改已发布迁移。确认发货没有新迁移。

### AuthController

来源：[AuthController](../backend/src/main/java/com/zhubao/erp/security/web/AuthController.java)。

| 方法与完整路径 | 返回类型 |
|---|---|
| `POST /api/auth/login` | `ApiResponse<AuthSession>` |
| `GET /api/auth/session` | `ApiResponse<AuthSession>` |
| `POST /api/auth/logout` | `ApiResponse<Void>` |
| `POST /api/auth/password` | `ApiResponse<AuthSession>` |
| `GET /api/auth/demo-users` | `ApiResponse<List<String>>` |
| `GET /api/auth/audit-events` | `ApiResponse<List<AuthAuditEvent>>` |
| `GET /api/auth/audit-integrity` | `ApiResponse<AuthAuditIntegritySummary>` |
| `GET /api/auth/accounts` | `ApiResponse<List<AuthUserAccountSummary>>` |
| `GET /api/auth/account-options` | `ApiResponse<AuthAccountOptions>` |
| `GET /api/auth/accounts/pre-approval-readiness` | `ApiResponse<List<AuthAccountPreApprovalReadiness>>` |
| `POST /api/auth/accounts/pre-approval-drafts` | `ApiResponse<AuthAccountPreApprovalDraft>` |
| `GET /api/auth/accounts/pre-approval-applications` | `ApiResponse<List<AuthAccountPreApprovalApplication>>` |
| `GET /api/auth/accounts/pre-approval-evidence` | `ApiResponse<List<AuthAccountPreApprovalEvidenceSummary>>` |
| `POST /api/auth/accounts/pre-approval-evidence/uploads` | `ApiResponse<AuthAccountPreApprovalEvidenceSummary>` |
| `POST /api/auth/accounts/pre-approval-applications` | `ApiResponse<AuthAccountPreApprovalApplication>` |
| `POST /api/auth/accounts` | `ApiResponse<AuthUserAccountSummary>` |
| `POST /api/auth/accounts/{username}/status` | `ApiResponse<AuthUserAccountSummary>` |
| `POST /api/auth/accounts/{username}/unlock` | `ApiResponse<AuthUserAccountSummary>` |
| `POST /api/auth/accounts/{username}/delete` | `ApiResponse<Void>`，`data=null` |
| `POST /api/auth/accounts/{username}/session-revocations` | `ApiResponse<SessionRevocationResult>` |

### CustomerAuthorizationController

来源：[CustomerAuthorizationController](../backend/src/main/java/com/zhubao/erp/security/web/CustomerAuthorizationController.java)。

| 方法与完整路径 | 返回类型 |
|---|---|
| `GET /api/security/customer-authorizations` | `ApiResponse<List<CustomerAuthorization>>` |
| `POST /api/security/customer-authorizations` | `ApiResponse<CustomerAuthorization>` |
| `POST /api/security/customer-authorizations/revocations` | `ApiResponse<RevokeResult>` |

### SecurityContextController

来源：[SecurityContextController](../backend/src/main/java/com/zhubao/erp/security/web/SecurityContextController.java)。

| 方法与完整路径 | 返回类型 |
|---|---|
| `GET /api/security/current-context` | `ApiResponse<SecurityCurrentContext>` |
| `GET /api/security/menu-tree` | `ApiResponse<List<NavigationMenuItem>>` |

## 2026-10-10 复核第二批契约补充

- 附件列表、统计、采集列表、预览签发及库存读取要求真实 Bearer 会话；有限范围按实际单据客户及岗位关系过滤。存储清单与附件审计仅全公司范围管理员、总经理可读。附件下线后原签名链接亦不能继续读取。
- 业务写入中的 `operator`、`inspector`、`approver` 不作为身份凭据，可省略，服务端使用会话账号。登记确认、纠正、入账及设备读数消费也要求真实会话。
- 通用 `production-reports` 保留原登记字段，但必须匹配正式工单维度、整单数量重量、账号绑定工序和前道交付；只有末道转半成品。登记导入及设备生产消费共用该规则。
- 通用 `quality-releases` 以正式末道交付及质检事实决定是否放行；请求说明不能替代合格或让步判定。
- 财务公司写命令要求 `ALL` 范围；客户写命令使用客户编号及已保存的名称判断授权，冲销以原应收客户为准。MySQL 模式新增必填环境变量 `ZHUBAO_DOCUMENT_PREVIEW_SECRET`，至少 32 字符且禁止开发默认值。

## 2026-10-10 复核第三批契约补充

- 来料确认、报工、冻结、返工派发、返工报工、放行、出库、退货和售后复核的 `quantity` 与 `weight` 必须成对出现。两项都省略不再表示整批；调用方应提交输入框中可见的整批值，或提交重新确认后的本次值。重量保留调用方给出的十进制文本，不按数量比例换算。
- 通用 `production-reports` 的 `toWarehouseCode` 使用正式工单来源仓库。只有末道把库存状态改为半成品，中间工序保持在制；仓库编码本身不写成状态名。
- 质检对照提交和开返工的 `code` 使用最近一次成功识别返回的 `orderCode`。扫码文本变化后，该识别结果失效。
- 正式操作上下文按库存阶段匹配工单：收货待加工只匹配空工单；不拆母单进入在制及后续状态后，母单号是真实加工工单。仅有历史空工单时仍可返回该余额；它与母单工单或多仓库同时有余额时，上下文为空。
- 冻结命令的 `defect` 和返工派发的 `reason` 不接受“未判定”这一占位。没有真实缺陷或返工原因时，请求在提交前被拒绝。
