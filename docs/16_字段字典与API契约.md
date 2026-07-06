# 来料加工进销存与生产管理系统 字段字典与 API 契约

**文档版本**：V1.0  
**更新日期**：2026-07-06  
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

## 9. M3 迁移要求

1. 将当前 `BusinessWorkspaceService` 内存样例迁移为 seed 数据。
2. 用 MySQL repository 替换当前 `InMemoryBusinessWorkspaceRepository`。
3. 将 `status`、`action`、`tone` 分离，避免前端展示状态污染业务状态。
4. 将重量字段拆为数值和单位，禁止长期使用字符串重量参与计算。
5. 将客户、物料、人员、工序单价、损耗率保存快照字段。
6. 将工作台 API 改为读取真实业务聚合，不再直接维护独立样例数据。

---

## 10. 硬件联动字段

### 10.1 `HardwarePocRecord`

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
