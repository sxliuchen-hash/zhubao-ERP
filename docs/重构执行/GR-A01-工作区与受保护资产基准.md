# GR-A01 工作区与受保护资产基准

**日期：2026-10-30。** 本页只记录本节点已经核对的事实，以及明确还没核对的事项。它是后续开发的保护基准，不是服务器盘点结果，也不是方案里尚未落地的能力。

规则来源：[实施边界](../大版本整体重构方案.md#implementation-strategy)、[存量接续](../大版本整体重构方案.md#stock-adoption)、[保护范围](../大版本整体重构方案.md#preservation)。进度只维护在[项目核心](../00_项目核心.md)。

## 1. 工作区基准

| 项 | 已核对事实 |
|---|---|
| 开发分支 | `refactor/grok`，从当时的 `main` 分出 |
| 分出点 | `7ba60e39038cb9a9583d309e2ffd8a2fc0329437`（`7ba60e3`，2026-10-30 16:18:43 +0800，`docs: 纳入资产打样金工并拆分Grok执行节点`） |
| 与远程 | 分出时 `main` 比 `origin/main` 超前 46 个提交。本节点不推送、不改远程 |
| 线上发布 | 核心记载的最近发布仍是 `cloud-20261029-printarea-16f4b2f`，代码 `16f4b2f`。本机分出点不是该发布提交。本节点没有连接服务器复核 |

本节点提交只包含本基准和核心里的节点进度。`main` 停在分出点，重构提交留在 `refactor/grok`。

### 未提交改动的归属

分出时工作区只有下面三个文件有差异。它们留在工作区，不进入本分支提交，也没有被还原：

| 文件 | `git diff` 所见 | 归属 |
|---|---|---|
| `frontend/src/business-workspaces.css` | 多行选择器收成一行，声明块未改 | 本机既有样式整理 |
| `frontend/src/register-import.css` | 同上 | 同上 |
| `frontend/src/styles.css` | 同上，行数变化来自换行合并 | 同上 |

核心记载：发布 `cloud-20261029-printarea-16f4b2f` 按提交快照构建前端，没有纳入这三个文件。该发布事实本节点没有重新上服务器核对。这三个文件也没有做浏览器版式检查。

## 2. 已从现行代码核实的保护对象

下面是当前代码里的身份和存放位置。方案中的袋号、部分质检、独立发货贴纸、打样、金工和拍照计数算法尚未实现，不记成已经保护好的运行能力。

### 母单原号

- 权威编号是 `biz_mother_order.code`，唯一键 `uk_mother_order_code`。
- 新号由 `OrderCodes.next("MD", …)` 生成，形式为 `MD-yyyyMMdd-` 加四位当日流水。同一天已经用过的三位旧流水也占用序号，避免改成四位后撞号。
- 收货入口在 `MobileIntakeService.nextMotherOrderCode`。
- 子单 `biz_work_order.code` 由 `MotherOrderSplitService.nextChildCode` 用前缀 `ZD` 生成，外键指向母单号。迁移 `V33` 写明：不拆仍用母单号。
- 后续接续保留原母单号和已印工单号，不通过重建母单换号。

### 已印工单标签与旧码

- 现行二维码由 `LabelService.qrContent` 生成：`JG|<scanType>|<编码>|<版本>|<实例>`。实例以 `LI` 加哈希组成。`LabelType` 的注释只写到编码段，实现还带版本和实例。
- 扫码类型有 `mother`、`work_order`、`outbound`、`rework`、`stock_batch`。工单标签按 `biz_work_order` 生成（`LabelService.generateWorkOrderLabel`）。
- 冻结内容在两处：`biz_print_job.content_snapshot`（`VARCHAR(1000)`，旧任务），以及 `biz_print_batch.content_snapshot`（`LONGTEXT`，含 `previewImage` 的 PNG Base64）和 `payload_hash`。补打记录在 `biz_label_reprint_log`。
- 内置模板代号包括 `MOTHER-DEFAULT`、`WORK_ORDER-DEFAULT`。`V56`、`V77` 只把当时仍为 60×40 的模板和打印队列改成 40×30，已经是其他尺寸的行不动。
- 已冻结批次原图和厂内贴纸外观保持不动。`SPOOLED` 只表示送入系统队列。本节点没有修改模板、样式或打印代码。

### 母单照片原件与引用

- 元数据在 `doc_evidence_attachment`：附件编号、`business_code`、`storage_location`、`checksum`、`object_type`。
- 业务引用在 `doc_business_evidence_link`，用 `attachment_code` 指向目标类型和目标号。
- 原件目录由 `DocumentManagementService.storageRoot()` 决定：配置了本地根目录时用配置，未配置时代码默认 `target/zhubao-evidence`。生产环境实际根目录本节点没有读取。
- 原件、附件编号和母单引用保留。不删除、不搬迁、不批量改挂。收料照片继续表示收料。下线不删除原件。
- 核心已记载早期测试服有 3 张母单的证据编号误用运单号，其中 1 张缺引用，历史字节未改。本节点没有再打开服务器核对。

### 原收货来源

- 母单保存数量、重量、`ledger_code`，以及 `source_register_type`、`source_line_no`、`source_sequence_no`。`ledger_code` 有唯一键。
- 逐行来源在 `biz_receipt_line`，主键是母单号加行号，外键指向母单，`ledger_code` 唯一。
- 子单分配在 `biz_child_line_allocation`，指向子单和对应收货行。
- 库存流水在 `inv_stock_ledger`，列包含 `mother_order_code`、`work_order_code`、`source_ledger_code`。正式写入走 `StockLedgerService`。
- 已经形成的收货和库存是接续依据。以后新增目标或袋只建立对应分配，不按同一母单再收一次货、再记一笔期初。

## 3. 本节点没有核对的事项

这些事项保持待核。缺少快照或没有打开服务器，不能写成空库，也不能写成已经盘点完成。

- 测试服上的母单、子单、收货行、打印批次、附件文件是否齐全，条数和可扫性都未统计。
- “其他模块暂未深度使用”没有被核对成报工、质检、发货、财务、外协、公司料或固定资产没有记录。
- P0 样本 `MD-20261029-0003` 的 120 颗成品漏查和重量差异，仍按核心已有记载待核，本节点没有改业务数据。
- 生产附件根目录、成套备份，以及厂内程序本机已出纸文件，都没有在本机之外核对。
- 线上库里的标签模板行是否全部已是 40×30，没有逐行读取；迁移只改写当时仍为 60×40 的行。
- 本机 `7ba60e3` 与线上 `16f4b2f` 之间的业务数据差异，没有做对比。

## 4. 后续节点如何使用

- 重构提交继续放在 `refactor/grok`。上述三个样式文件保持未提交，不混入重构提交。
- 下一节点是 GR-A02：只读列出母单、必要子单、收货来源、标签实例和照片引用。没有实际快照时标待核，不宣称线上盘点已完成。
- 在 GR-Z14 获得明确发布授权之前，不向业务服务器写入，不改业务库，不删除原件，不修改现有标签样式。
