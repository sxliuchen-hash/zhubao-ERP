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
- [businessWorkspaceApi.ts](../frontend/src/services/businessWorkspaceApi.ts)
- [V3__m2_business_workspace_contract.sql](../backend/src/main/resources/db/mysql/V3__m2_business_workspace_contract.sql)

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
2. 将 `status`、`action`、`tone` 分离，避免前端展示状态污染业务状态。
3. 将重量字段拆为数值和单位，禁止长期使用字符串重量参与计算。
4. 将客户、物料、人员、工序单价、损耗率保存快照字段。
5. 将工作台 API 改为读取真实业务聚合，不再直接维护独立样例数据。
