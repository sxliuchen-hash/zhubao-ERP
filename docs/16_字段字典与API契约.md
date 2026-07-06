# 来料加工进销存与生产管理系统 字段字典与 API 契约

**文档版本**：V1.0  
**更新日期**：2026-07-07
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
| `POST /loss-approvals` | 质检返工 | 生成损耗审批与工资试算快照，并创建轻量审批任务；不写库存台账，不代表正式工资发放 |

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
| `GET /attachments/{code}/content` | 文档附件 | 读取已上传到本地缓存的附件内容，后续替换为对象存储签名 URL |

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
| `courierNo` | 快递单号 |
| `quantity` | 件数 |
| `weight` | 重量展示值，M3 应拆成 `netWeight` + `weightUnit` |
| `status` | 入库状态 |
| `videoStatus` | 拆包视频状态 |
| `ownerType` | 库存所有权，目前固定为客户所有 |

必须保留的业务约束：

- 客户来料和企业自有库存不能混在同一余额维度里。
- 确认入库必须写库存台账。
- 拆包视频失败必须有人工上传兜底。

### 4.2 `ConfirmInboundCommand`

`POST /api/business/operations/inbound-confirmations` 使用该命令模型。该接口是 M3.5 第一条真实业务动作基线，不再只返回工作台样例。

| 字段 | 说明 |
|---|---|
| `motherOrderCode` | 母单号，作为库存台账来源单据 |
| `customerId` | 业务客户 ID |
| `ownerCustomerId` | 库存所有客户 ID，未传时默认等于 `customerId` |
| `materialCode` | 物料编码 |
| `warehouseCode` | 入库仓库，来料默认 `RAW-CUSTOMER` |
| `quantity` | 入库数量，必须大于 0 |
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
| `assignee` | 负责人快照 |
| `plannedQuantity` | 计划数量 |
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
| `weight` | 本次报工产出重量，必须大于 0 |
| `process` | 工序名称快照 |
| `operator` | 操作人快照 |
| `handoffCode` | 称重交接记录编码 |
| `remark` | 业务备注 |

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
| `quantity` | 本次出库数量，必须大于 0 |
| `weight` | 本次出库重量，必须大于 0 |
| `logisticsNo` | 物流单号或客户交接单号 |
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
| `logisticsNo` | 物流单号或客户交接单号 |
| `operator` | 出库操作人快照 |
| `evidenceCode` | 证据编码 |

实现约束：

- `from` 与 `to` 必须保持同一客户、母单、工单和物料维度。
- 成品出库只允许改变仓库和库存状态，当前基线为 `FINISHED -> SHIPPED`。
- 若来源成品库存不足，后端必须拒绝写台账。
- 出库不等同于退货；客户退货由 `POST /customer-returns` 独立命令反向写台账。

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
- M3.17 只完成退货入库台账基线；是否复检放行、返工或报废仍需后续售后/质检流程决定。

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
| `storageLocation` | 当前存储位置，M3.12 为 `memory://`，后续迁移为对象存储或 NAS |
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

- 当前 M3.12 已支持 multipart 上传到后端内存缓存、checksum、审计事件、预览 URL 和内容读取。
- 后续生产环境必须接 MinIO/S3 或 NAS，并通过签名 URL 控制预览和下载。
- 质检、称重、视频、配置审批证据必须能绑定业务对象、校验和、保留策略和审计事件。
- 审批完成后的配置和质检证据应锁定 checksum，后续修改必须产生新版本或新审计事件。

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
| 损耗审批 | `POST /api/business/operations/loss-approvals`，返回损耗审批与工资试算快照，并创建轻量审批任务；不写库存台账 |
| 轻量审批任务 | `GET /api/approvals/tasks`、`GET /api/approvals/tasks/pending`、`POST /api/approvals/tasks/{approvalCode}/decisions`，用于固定场景待审批、审批通过/驳回和锁定 |
| 后端入口 | `BusinessOperationController` |
| 权属边界 | `OwnerType.CUSTOMER`，保持客户来料和企业自有库存隔离 |
| 当前限制 | 尚未更新工作台快照状态；轻量审批可切换 JDBC 持久化，但正式权限、复杂审批流、审批 UI、工资锁账/发放和退货后的售后质检复核仍需继续接入 |

这些接口证明一期闭环开始从“展示样例”进入“业务命令”。库存变化必须通过库存服务产生只追加台账；损耗审批、工资试算、称重交接和退货复核这类非库存动作也必须先建独立业务命令，再按职责接入审批服务、工资服务或证据服务。

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
