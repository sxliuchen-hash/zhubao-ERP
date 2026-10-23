# 手机与电脑 UI 改造实施文档（Grok 可执行版）

版本：1.0。编写日期：2026-10-23。仓库：`F:\APPkaifa\zhubao-ERP`。

本文给出实施决策、文件范围、接口契约、操作顺序及验收用例。执行者应依次完成增量，而不是重新输出一份泛化方案。

## 0. 执行范围、输入与授权

### 0.1 当前状态

- 用户已认可手机 HTML 的整体方向，要求补充桌面稿，并要求写到 Grok 能执行的程度。
- 本轮交付是实施文档，未实施正式 UI 改造。桌面稿尚无单独确认记录。
- 后续用户明确说“按本文改造手机和电脑”时，可视为对本文两端开发范围的确认，按顺序实施，不逐增量重复请求许可；若只授权手机，则执行手机与必要共享部分，保留桌面布局。
- 开发授权不包括发布、切换服务器、数据库升级、生成正式 APK 或操作真实业务单据。上述行动按用户后续明确指令执行。

### 0.2 必须读取的输入

从仓库根目录开始，按以下顺序读取。其余专题只查当前增量所需小节。

1. [AGENTS.md](../../AGENTS.md)。
2. [项目核心](../00_项目核心.md)：当前范围、架构约束和最近阻断。
3. [手机 HTML](./mobile-ui-review/index.html)及[手机调研](./mobile-ui-review/调研与优化建议.md)。
4. [电脑 HTML](./desktop-ui-review/index.html)及[桌面说明](./desktop-ui-review/桌面调研与跨端设计说明.md)。
5. 本文。此前[交接说明](./跨端UI改造交接说明.md)作为快速入口，具体步骤以本文为准。

HTML 直接通过本地浏览器打开；不要依赖已经停止的临时 HTTP 服务。原型中的示例数据、角色对象、模拟保存、媒体占位和占位二维码均不进入正式代码。

### 0.3 开始工作时执行

在 PowerShell 的仓库根目录运行：

```powershell
Get-Location
git status --short
git log -5 --oneline
Get-Content frontend/package.json
```

检查本次相关文件的已有差异。当前工作区存在库存、权限、轻任务、打印和扫码等既存改动。不得 `reset`、`checkout --`、`clean` 或覆盖它们。某个已有改动被本任务依赖时，在增量说明中写清依赖，并做局部兼容；提交只纳入自己本轮的内容。

**首个交付：**完成任务 T00 的依赖表和首个可实施增量，然后直接执行已授权范围。只有缺少必要业务定义、用户限定范围之外的权限变更等真实阻断才需要询问。

## 1. 最终行为与明确取舍

### 1.1 用户最终看到什么

| 场景 | 手机 | 电脑 | 共同业务来源 |
|---|---|---|---|
| 日常入口 | 待办、订单、正中扫码、按权限显示的库存、更多 | 工作台、订单中心、生产作业、质量作业、库存、客户、财务、审批、工具和管理 | 同一菜单、会话与数据范围 |
| 查找单据 | 搜索与状态卡片 | 表格、组合筛选、右侧详情 | 同一只读列表契约 |
| 打开单据 | 独立详情，返回保留列表条件 | 当前列表旁的详情，可展开 | 同一规范身份及详情数据 |
| 收货 | 客户来料 → 要求证据，两段填写 | 分组字段和证据栏并排 | 原收货、模板、拆单与证据服务 |
| 工序 | 更新进度／正式交工切换 | 同一动作表单内切换 | 原进度及岗位交工服务 |
| 质检 | 逐条判定，按状态展开异常操作 | 要求、判定与证据并排 | 原质检轮次服务 |
| 发货 | 选库存 → 填本次物流 → 复核 | 同一流程在弹层中办理 | 原发货与库存服务 |
| 标签、附件 | 单据内办理 | 单据内办理，工具里跨单检索 | 原标签、打印任务、证据服务 |
| 客户与账单 | 客户详情内打开 | 列表与客户详情并排 | 客户 ID、账期及账单契约 |

### 1.2 本轮不新增的业务

- 不增加生产工序、不改计费公式、不重写库存核算、不放宽角色权限。
- 不增加跨材质子单、混合数量单位、公司料自动成本核销等未完成业务。
- 不增加批量交工、批量质检、批量发货、批量审批或批量入账。
- 原型中的多单标签预览可做；实际打印先复用已有单单打印批次接口，逐项显示结果，不发明不存在的多业务单原子打印接口。
- 不新增 Vue Router、Pinia、UI 大型组件库或另一套客户端；先使用现有 Vue 3、TypeScript、provide/inject 与 CSS。

### 1.3 视觉实施基准

采用两份 HTML 的浅色、深绿导航及低饱和状态色。正式样式通过现有语义令牌实现，不能把原型的全局选择器直接复制进 `styles.css`。

| 项目 | 实施值 / 规则 |
|---|---|
| 布局断点 | 沿用现有 `900px` 手机断点；桌面在空间不足时收起详情或改为抽屉，避免把整页撑宽 |
| 内容文字 | 正文与表格主数据以 13–14px 为基准，次要字段 12px；不照搬原型里部分 9–10px 标注 |
| 编辑控件 | 手机字体至少 16px，触控有效区域至少 44px；紧凑展示通过分组和隐藏低频字段实现 |
| 页面标题 | 同一视图只保留一个主标题；不重复显示 AppShell 标题与模块大标题 |
| 表格 | 电脑使用紧凑表格；手机用卡片。确需横向滚动时只允许表格容器滚动 |
| 长要求 | 保留完整加工要求；展开阅读，不截断后提交，不用样例文本补空值 |
| 状态 | 颜色配文字，不能只靠红绿；未知值显示“待核对”或原始可理解文字 |
| 底部操作 | 手机主操作与底栏分层，计入安全区、键盘和内容底部留白；不能遮住最后一项输入 |
| 弹层 | 支持 Escape、关闭按钮、合理焦点管理；关闭有未保存内容的表单时保留草稿或明确确认 |

## 2. 现行源码与接口定位

以下路径是本次静态核对的真实入口。执行时若代码已有变化，先定位同名职责，更新必要引用，不照旧内容覆盖新代码。

### 2.1 页面与依赖表

| 职责 | 现有文件 | 实施要求 |
|---|---|---|
| 应用导航、会话、全局目标 | `frontend/src/App.vue` | 保留登录恢复、异步加载重试、旧菜单过滤和 provide 协议 |
| 侧栏、顶部、手机底栏 | `frontend/src/components/AppShell.vue`、`frontend/src/styles.css` | 在现有外壳改造，不建立第二个根应用 |
| 手机扫码桥接 | `features/scan/mobileScanBridge.ts`、`ScanInput.vue` | 当前页扫码优先；卸载解绑；取消挂起请求 |
| 规范目标与查询 | `features/scan/traceTarget.ts`、`useScanContext.ts`、`scanDraft.ts` | 继续使用规范身份和查询代次，不能用搜索字符串代替 |
| 当前手机详情与动作 | `features/m1/MobileOrderDetail.vue`、`MobileOpsWorkspace.vue` | 逐步接入统一详情；保留本岗和顶岗过滤 |
| 首页与轻任务 | `features/m1/M1Dashboard.vue`、`services/deskTaskApi.ts` | 指派、完成、作废和业务单据分别处理 |
| 列表来源 | `services/progressApi.ts`、`processProgressApi.ts`、`businessWorkspaceApi.ts` | 两种进度契约并不相同，见第 5 节 |
| 收货、模板 | `features/intake/IntakePanel.vue`、`RequirementComposer.vue` | 保留多明细、最终要求、模板版本、档案选择与草稿证据 |
| 工序进度与正式交工 | `features/m1/ProcessProgressPanel.vue`、`features/production/ProductionPanel.vue` | 表单职责重排，不把百分比写入交工服务 |
| 质检 | `features/quality/QualityPanel.vue`、`services/qualityCheckApi.ts` | 使用服务端实际条款及可检、复检、放行状态 |
| 发货 | `features/shipping/ShippingPanel.vue`、`services/businessOperationApi.ts` | 维度来自已选库存，沿用正式发货请求 |
| 库存 | `features/m1/InventoryTraceWorkspace.vue`、`PrepStockPanel.vue` | `PrepStockPanel` 当前直接调用库存 API，暂不强制拆服务层 |
| 标签与打印 | `features/m1/LabelWorkspace.vue`、`labelPrint.ts`、`services/labelApi.ts` | 打印批次调用目前部分内联在 Workspace，提取时不能遗漏核纸与改派 |
| 证据与预览 | `features/evidence/EvidenceCapture.vue`、`EvidencePreviewLink.vue`、`services/evidenceApi.ts` | 复用分页、签名预览及待关联恢复 |
| 客户与财务 | `CustomerArchiveWorkspace.vue`、`FinanceWorkspace.vue`、`services/masterDataApi.ts`、`financeApi.ts` | 用稳定客户 ID，不把名称当键 |
| 其他现行能力 | 审批、登记导入、设备、账号、配置、运维 Workspace | 移入口，保留真实页面与服务消费者 |

表中未写完整前缀的前端路径均以 `frontend/src/` 为根，未写 features 前缀的 Workspace 均位于 `frontend/src/features/m1/`。

### 2.2 已存在的契约：直接复用

| 用途 | 当前实际接口 / 消费方 | 容易误用之处 |
|---|---|---|
| 规范定位 | `POST /api/scan/context`，`queryScanContext` | `requiresSelection=true` 时不构造可提交目标；`actions` 不能代替写接口授权 |
| 过程说明 | `POST /api/scan/progress-records` | 操作人取会话，不是任意填写的姓名 |
| 进度一览 | `GET /api/business/operations/progress` | 返回数组；参数是 `customer/process/dueDate/status`；没有页信息与百分比 |
| 百分比列表 | `GET /api/business/operations/process-progress` | 返回数组；参数是 `query/process/dueBefore/page/size`；没有全量总数 |
| 百分比目标、历史 | 上述路径的 `/target?code=`、`/history?orderCode=&process=` | 母单已拆分可能要求选子单，不能取第一张 |
| 提交百分比 | `POST /api/business/operations/process-progress` | 提交 `expectedVersion` 和 `requestId`，五档进度、回退原因保留 |
| 新收货 | `POST /api/business/operations/mobile-intakes`，`submitMobileIntake` | 带客户 ID、来源、明细、要求及模板快照，不混用母单确认接口 |
| 母单资料 | `/api/business/operations/mother-orders/{code}` 与 `/api/business/documents/mother-orders/{code}` | 两处 DTO 不同；分别 alias 导入，不能把相同函数名当成同一响应 |
| 母单拆分 | `motherSplitApi.ts` 中 `/mother-orders/{code}/split` 等 | 从来源行分配；KEEP 和 SPLIT 两种决定保留 |
| 岗位交工 | `processStationApi.ts` 的 `lookupProcessStation/submitProcessStation` | 正式交工使用原服务，不改走百分比接口 |
| 质检全链 | `/api/business/operations/quality-checks` 及 `/rework`、`/rework-reports`、`/rechecks`、`/releases` | 不合格后的返工、复检、放行分别调用 |
| 正式发货 | `POST /api/business/operations/outbound-shipments` | 保留库存完整维度、物流与原请求；物流扫码只填运单号 |
| 三类库存 | `GET /api/inventory/material-stock/overview`、`/batches`、`/movements` 与原加工库存 Workspace | 数量以单位分组；备料与公司料不是加工母单 |
| 库存单据动作 | `PrepStockPanel.vue` 中 `company-* / prep-* / freezes / corrections` 等路径 | 逐动作保留，不因整理卡片减少退回、冻结或冲正能力 |
| 标签取数 | `/api/label/mother-orders/{code}`、`/work-orders/{code}` 等现有标签 API | 按真实对象和服务端支持的标签类型取数，不按前缀猜测 |
| 打印与核纸 | `POST /api/label/print-batches`、`/print-target-jobs/{id}/paper-checks`、`/{id}/reroute` | 批次是目标工位任务集合，不等于任意多个业务单号的原子批处理 |
| 轻任务 | `/api/business/desk-tasks` 及 `/completions`、`/cancellations` | 使用 `taskCode/expectedVersion/requestId`，不写库存或工序 |
| 客户账单与收款 | `GET /api/finance/statements`、`POST /api/finance/receipts` | `customerId/yearMonth` 上下文、应收行 `receivableCode` 和 `idempotencyKey` 保留 |
| 改单 | `/api/business/order-changes` 及财务提交、确认、拒绝、生效路径 | 当前部分调用在 `ProcessProgressPanel.vue` 内，移动时不能留下重复写入口 |
| 证据 | `evidenceApi.ts` 的分页、上传与 bind；现有预览组件 | 正式操作编号到手后关联；运单不是操作或附件归属编号 |

不要把本文标为“拟新增”的查询接口当作上述现有 API 调用成功。每个新增 API 先实现并通过定向契约测试，再接页面。

## 3. 页面归并与权限：不用新菜单覆盖旧授权

### 3.1 新展示分区与旧编码分离

新建**展示层分区** `WorkspaceSection`，与现有 `TabKey`／服务端菜单编码分开。保留 `activateTab` 或等价的兼容适配，现有 `zhubao-open-tab` 调用不必在首个增量全部改完。

| 新展示分区 | 可用的原有菜单编码 | 规则 |
|---|---|---|
| 工作台 / 待办 | `dashboard` | 按原首页访问；轻任务按钮仍看专门权限 |
| 订单 | `inbound/production/progress/quality/inventory` 中实际获准的部分 | 显示入口不等于获得所有来源；Q1 查询按本人来源能力裁剪 |
| 生产作业 | `production` 或 `progress` 的现有可读能力 | 只显示获准工序；提交另外核对 `BUSINESS_PRODUCTION_REPORT` |
| 质量作业 | `quality` | 冻结、返工、复检、放行各自保留授权 |
| 库存 | `inventory` | 不是“角色是库管就显示”；以真实菜单与能力为准 |
| 客户 | `customers` | 财务页签单独授权，不能因客户页可见展示金额 |
| 财务 | `finance` | 分项读写权限继续使用现行契约 |
| 审批 | `approvals` | 跟随服务端，不硬编码“只有管理员”；任务决策继续接口授权 |
| 基础资料 / 工具 | `customers/label/documents/sample` 等对应实际授权 | 每个工具独立过滤，不能因整个分组可见开放全部子项 |
| 系统管理 | `users/hardware/operations/configuration` 对应授权 | 用户管理仍仅管理员；个人改密不能要求管理员权限 |
| 扫码动作 | 现有 `mobile` 或当前已授权表单的扫码上下文 | 保留当前页桥接，无现场写权限仍可按现有读取能力查单 |

`mobile` 作为旧页面适配入口保留到其所有调用迁完；不向 `menuCodes` 人为塞入 `orders/tools` 伪造服务端授权。若服务端已有等价新编码，先核对真实下发再复用。

### 3.2 动作判断次序

1. 已恢复有效会话；读取真实菜单、`effectivePermissions`、顶岗与数据范围。
2. 该对象已成功规范定位，并且不处于“须选单”“加载”“已失效”状态。
3. 当前目标支持这个动作；读取 `scanContext.actions` 或相应业务 View 的 `ready/canRecheck/canRelease` 等。
4. 保留原表单更细的业务条件；写接口再次进行服务端授权和状态校验。

按钮隐藏用于减少入口；状态阻断显示具体原因。缺少有效目标时不渲染可提交表单。不要复制原型的 `roles` 对象；已有会话兼容分支须定向验证，不顺带扩大为全项目权限改造。

### 3.3 原 17 页能力不丢失

来料与进度并入订单展示；生产和质检保留岗位视图；标签、附件进入单内与工具；配置分个人／存储；其他页面按[桌面 17 页去向表](./desktop-ui-review/桌面调研与跨端设计说明.md)逐行核对。**每行必须有真实可达入口或明确的尚未迁移入口，不允许以“设计稿未画”为由删除。**

## 4. 共享前端结构与目标生命周期

### 4.1 建议新增文件

以下是本次实现的固定职责划分。若仓库已出现同等实现，优先复用，不再创建同功能文件。

```text
frontend/src/features/workspace/
  workspaceNavigation.ts          展示分区与旧菜单映射
  workspaceContext.ts             只读选择、返回位置和动作会话类型
  useWorkspaceContext.ts          当前应用实例内的共享上下文
frontend/src/features/orders/
  OrderWorkspace.vue              查询、列表与详情的编排
  OrderList.vue                   桌面表格 / 手机卡片
  OrderDetail.vue                 共享详情内容与页签
  OrderActionHost.vue             本单业务表单容器
  OrderActionMenu.vue             本单其他动作
  orderWorkspaceTypes.ts          Q1 DTO 与显示适配
frontend/src/services/
  orderWorkspaceApi.ts            Q1 拟新增列表 API
```

`OrderDetail` 先提取 `MobileOrderDetail` 的可复用展示，不复制一套取数；`OrderActionHost` 挂载原业务 Panel，不重写它们的提交实现。数据状态与布局分开；不创建通用低代码表单、全局事件总线或第二套领域模型。

### 4.2 应保留的上下文

```ts
// 查询代次与服务器版本分开，以下是本次拟新增的前端类型。
interface OrderSelection {
  objectType: ScanObjectType;
  code: string;
  motherOrderCode: string | null;
  workOrderCode: string | null;
  executionRoundCode: string | null;
  selectionRevision: number;
}

interface OrderReturnLocation {
  section: WorkspaceSection;
  query: string;
  customerId: string;
  process: string;
  facet: string;
  dueBefore: string;
  page: number;
  scrollTop: number;
}
```

- `ScanObjectType` 复用现有联合类型，暂不擅自添加 `EXECUTION_ROUND`。执行轮次独立保留 `executionRoundCode`；真正传给扫码、工序或质检的对象类型必须由现有解析能力确认。
- `selectionRevision` 只是浏览器查询代次，不能作为 `expectedVersion`；工序进度版本、订单业务版本、标签版本与质检轮次编号分别保存。
- 详情来源是 `useScanContext`／`queryScanContext` 的服务端结果；`canonicalTarget` 返回空时不得打开可提交动作。
- 原 `TraceTarget.intent/token`、`zhubao-trace-target`、`zhubao-label-target`、`zhubao-open-label`、`zhubao-visible-tabs` 与 `zhubao-open-tab` 在过渡期由适配层保留。

### 4.3 查询、切单与提交的状态规则

| 事件 | 必须发生 | 禁止发生 |
|---|---|---|
| 查找新单据 | 查询代次递增、旧提交目标失效，展示加载 | 旧单据按钮仍能提交 |
| 同码多对象 | 展示候选，点击后按精确类型和单号再查询 | 直接选 `relatedTargets[0]` |
| 改列表筛选／翻页 | 清空多选；旧选中不在新结果时收起详情 | 留着筛选前单据继续发货或打印 |
| A 单迟到响应落在 B 单之后 | 检查目标键和代次后丢弃 | 覆盖 B 单要求、金额、附件或按钮 |
| 返回列表 | 恢复筛选、页码和位置；必要时局部刷新 | 丢掉搜索，自动打开另一单 |
| 写入成功 | 保存真实操作回执；刷新当前单和相关列表 | 自行在浏览器推算最终库存或发货状态 |
| 写入失败、结果明确未提交 | 显示原因；按原表单规则修正或重试 | 隐式改用其他对象再次提交 |
| 超时或响应未知 | 保留 payload、版本和原 requestId／幂等键，先核对或原请求回放 | 自动生成新编号提交第二笔 |
| 成功后列表刷新失败 | 仍展示“已成功”与真实回执，提供只读刷新 | 显示“提交失败”诱导重发 |
| 权限变化、退出或会话失效 | 取消挂起扫码、清空共享业务上下文及本账号草稿引用 | 下一个账号看到前一个账号的单据或文件 |

### 4.4 草稿与请求编号

- 本轮先使用当前会话内草稿；不把客户资料、附件或请求 payload 新增到 `localStorage`。已有安全恢复能力按原组件保留。
- 草稿键至少包含账号、动作、对象类型、精确单号、工序或轮次；新收货使用独立 draftId，不能统一挂在“当前订单”上。
- 复用 `features/field/stableRequest.ts` 的 `nextRequestAttempt` 或已有各动作幂等实现。内容未变时不换编号；换业务内容、目标或成功开始下一笔时按原业务规则新建。
- 表单弹层关闭或手机返回，不应因卸载而丢失结果未知请求。原请求状态需提升到动作会话，或保证原面板不被销毁；明确成功或核对后才清理。
- 不同时挂载多个注册相机桥接的隐藏 `ScanInput`。仅当前可见动作或统一找单登记，卸载时对应解绑。

## 5. Q1：统一订单列表查询（拟新增，先实现再接页面）

### 5.1 为什么需要 Q1

`progressApi` 缺货品、数量单位、百分比和总数；`processProgressApi` 缺规范对象类型及完整单据展示字段，数组分页也不能计算总数。原 Workspace 的生产 `progress` 还可能是完成数量比例，**不能当作本工序的五档进度**。正式订单中心不能用前端全表拼接加猜测填充实现。

本方案确定新增一个只读应用查询。若开始执行时已有等价契约，复用并记录映射，不重复新增。Q1 不增加持久化表，不修改业务写入路径。

### 5.2 请求定义

拟新增：`GET /api/business/order-workspace`。

| 参数 | 默认 / 范围 | 含义 |
|---|---|---|
| `view` | `ORDERS`；另有 `PRODUCTION/QUALITY` | 展示视图。服务端校验可用来源，客户端不能藉此扩大范围 |
| `query` | 空，最大 100 字符 | 精确单号／运单或货品、客户关键字；运单过滤返回各原单，不合并身份 |
| `customerId` | 可空，稳定 ID | 仅在当前可见数据内筛选；不接受展示名称作为 ID |
| `process` | 可空 | 原服务使用的正式工序名称或稳定码；实现时统一一种，不混用 |
| `facet` | `ALL` | 只读展示分类，见下文，不修改业务状态 |
| `dueBefore` | 可空，`yyyy-MM-dd` | 截止日期，含当天；与旧 `progress` 的日期前缀参数语义不同 |
| `page/size` | `1/20`，size 最大 100 | 先授权过滤、再组合筛选和排序、最后分页 |

非法日期、负页码、不支持分类返回可识别的参数错误；不静默变成全量查询。日期按项目的中国业务日期口径；缺交期排序最后，相同交期按母单、对象类型、单号和行键稳定排序。

### 5.3 响应定义

继续使用现有 `ApiResponse<T>` 包裹。下列类型为拟新增契约，类型名称可按 Java record 风格落地，语义不得省略。

```ts
interface OrderWorkspacePage {
  items: OrderWorkspaceRow[];
  page: number;
  size: number;
  totalRows: number;
  facetCounts: Record<string, number>;
  availableViews: Array<'ORDERS' | 'PRODUCTION' | 'QUALITY'>;
  generatedAt: string;
}

interface OrderWorkspaceRow {
  rowKey: string;
  objectType: ScanObjectType;
  code: string;
  motherOrderCode: string;
  workOrderCode: string | null;
  executionRoundCode: string | null;
  currentProcess: string | null;
  customerId: number | null;
  customerName: string | null;
  goodsName: string | null;
  materialCode: string | null;
  materialName: string | null;
  quantity: string | null;
  quantityUnit: string | null;
  weight: string | null;
  weightUnit: string | null;
  dueDate: string | null;
  businessStatus: string;
  facet: string;
  processPercent: 0 | 30 | 60 | 90 | 100 | null;
  processProgressVersion: number | null;
  processFormallyDelivered: boolean | null;
  operatorName: string | null;
  updatedAt: string | null;
}
```

列表不返回金额、附件原始地址、库存写入 payload 或需要另行授权的财务字段。`null` 表示未知／未取得，不能显示成真实 0；不伪造单位、工人或交期。数量和重量用十进制文本展示，保留小数，不用 `parseInt`。详情和正式动作再次加载并校验原服务上下文，不直接把这一行当库存维度提交。

### 5.4 行、分类与计数口径

1. **一行是一个当前可办理目标。** 未拆母单显示母单；已拆单优先显示各子单，母单作为归组字段，不再把母单总量当一条独立加工任务重复统计。未分配余量如原拆单服务支持则独立标注，不能伪造成另一件货。
2. 执行轮次单独保留身份，不能覆盖原工单或原交工记录。Q1 必须核对 `OrderRequirementRepository/ExecutionRound` 与当前扫码解析，必要时在同一读增量补齐规范解析；无法明确匹配时只读显示并禁止对应正式动作，记录具体缺口，不能悄悄当作原子单提交。
3. `rowKey` 由服务端依据对象、单号、工序／轮次生成稳定唯一值。母单号或货品名均不能单独作为行键。
4. 展示分类限定 `ALL/UNSPLIT/WAITING_START/IN_PROCESS/WAITING_QUALITY/REWORK/READY_TO_SHIP/SHIPPED/UNKNOWN`。它是 UI 只读适配，不是新增领域状态。允许按岗位只开放相关分类。
5. 原“待拆分、待开工、在制、待质检、已质检、返工”等事实保留在 `businessStatus`。**已质检不等于已放行，不等于可发货。**只有现行质检放行事实和相应可发库存支持时才能归 `READY_TO_SHIP`；来源不足归 `UNKNOWN` 并显示原因，不猜。
6. `processPercent` 来自当前有效工序进度记录，与正式交付分别呈现；不从库存状态、已交工道数、`WorkOrder.progress` 或工序序号计算百分比。
7. `totalRows` 是当前查询条件下的可见目标行数；`facetCounts` 在相同可见范围、视图、搜索、客户、工序和期限内统计，只排除 facet 自身条件。单位写“项／目标”，不能写成不去重的“母单数”。
8. 原型首页的 12／28／3 等数字不迁入产品。需要母单数时按 `motherOrderCode` 去重另命名；没有全量查询能力的旧接口仅显示“本页”，不得推导全局总数。

### 5.5 后端实施位置与边界

新增于 `backend/src/main/java/com/zhubao/erp/business/`：

| 层 | 拟新增 / 修改 | 职责 |
|---|---|---|
| domain | `OrderWorkspaceQuery`、`OrderWorkspaceRow`、`OrderWorkspacePage` 等 record | 只读查询模型，不作为写命令 |
| app | `OrderWorkspaceQueryService` | 聚合现有事实、授权、筛选、分类、统计与分页 |
| web | `OrderWorkspaceController` | `requireSession`，参数校验，返回现有响应包装 |
| infra | 默认无需新增仓储 | 使用现有 `BusinessDocumentRepository`、进度和要求仓储；必要批量读方法同时补内存／JDBC |

授权必须复用 `BusinessDataAccess`、`DocumentVisibility`、会话菜单及现有角色／范围规则；身份由会话解析，不接收客户端 `operator/role/customerScope`。匿名拒绝；有查单能力但范围内无数据返回空页，不能把 403 吞成空页。

行可见与字段可见分别校验。`customerName/operatorName` 等按当前账号合法读取能力投影，不能把各来源接口字段取并集后全部返回；没有相应读取能力时返回 `null` 或不提供该字段。网络响应本身也不得包含禁止字段，不能仅靠隐藏 DOM。现有未裁剪缺口不能作为扩大新接口字段暴露的依据；细节读取与 Q1 使用一致的字段白名单，并验证不同岗位的响应差异。

避免每行调用 `scan/context` 或重新全表扫描。每次请求按现有仓储能力一次装入所需事实建索引，再计算；已有 `ProgressBoardService.listFrom` 是当前批量快照思路的参考。它不是新契约的完整实现，不能复制其旧状态判断替代放行、发货和轮次事实。

先实现范围正确的内存及 JDBC 兼容读路径；若当前仓储只能装入全集，可作为过渡但需记录规模限制和调用次数测试。不要为此修改已发布迁移或创建第二套订单存储。确有查询性能问题再单独提出与当前接口相关的优化。

### 5.6 Q1 的必要验证

- 匿名、本人／班组／部门／授权客户范围；空客户范围不能放大，母子单客户冲突不能选一边。
- 母单拆分前后、KEEP、多个子单、执行轮次，身份不丢、行键不重复。
- `dueBefore` 含当天、日期为空、稳定排序、分页边界、筛选计数一致。
- 五档进度和正式交工不混用；已质检但未放行不进可发货分类。
- 不能通过 `view/customerId/query` 读到原来不可见单据、客户、金额或附件。
- 内存与 JDBC 相同语义；至少相关仓储的读路径或 H2 MySQL 兼容夹具通过，不把 H2 结果声称为真实 MySQL 验收。
- 查询不写库存、财务、工序、打印任务或证据；批量装载的调用数不会随每行重复全表查询。

## 6. 按顺序执行的任务包

默认串行。一个任务内部可以继续拆小提交，但不得跨越依赖去同时改同一个文件。以下“完成”均指代码接入真实服务并通过所列验收，原型截图不算实现完成。

### T00：基线与迁移清单

**输入：** 第 0–5 节、相关现行源码。**修改：** 只在核心文档追加简短基线记录。

1. 记录当前 HEAD、相关工作区改动与已有扫码、轻任务、库存依赖。
2. 列出 17 页到新入口的映射，标记实际旧菜单、直接写服务和必须保留的低频能力。
3. 判断 Q1 是否已存在等价实现；若没有，按 Q1 实施，不用旧数组接口假装完整订单中心。
4. 确认本次被授权手机／两端；后端查询增量限于支持 UI，不扩大业务规则。

**交付：** 真实文件和接口基线；未发现必要阻断时继续 T01，不停留在计划。

### T01：展示导航和共享上下文

**依赖：** T00。**文件：** `App.vue`、`AppShell.vue`、新 workspace 适配文件、相关样式和测试。

1. 引入展示分区映射，保留旧 `TabKey`、菜单过滤与 provide 适配。
2. 手机底栏改为新结构；库存与更多子项分别过滤；扫码仍是动作，不创建菜单权限。
3. 保留 `mobileScanBridge` 当前表单优先策略：已有表单可扫描就直接打开，未进入表单才导航到找单；退出／切换时清理 pending。
4. 抽取当前应用实例内的目标、返回位置和动作会话。首个增量旧列表与页面继续可达，未接好 Q1 的区域使用原入口，不展示无效新页面。
5. 不改变登录恢复、必须改密、PWA 和工作区异步错误重试行为。

**验收：** 同一账号两端没有额外菜单；旧跳转可用；当前表单扫码不丢草稿；无手机菜单的账号不进入现场写页；权限撤销和退出清上下文。

**定向验证：** `App.test.ts`、`ScanInput.test.ts`、`mobileScanBridge.test.ts`、`MobileOpsWorkspace.test.ts`，以及新适配测试。共享类型变更时做前端类型检查。

### T02：实现 Q1 与前端查询适配

**依赖：** T00，前端接入需 T01。**文件：** 第 5.5 节及 `services/orderWorkspaceApi.ts`、`orderWorkspaceTypes.ts`、必要 API 专题小节。

1. 先完成 DTO、服务、会话校验、授权、分类与计数，保留旧查询接口兼容。
2. 在前端定义等价类型和一个查询函数；状态显示映射单独写明，不能影响正式状态。
3. 添加第 5.6 节验证；若执行轮次存在解析缺口，在同一范围补齐并验证原标签／扫码兼容。
4. 接口真实通过后，在 API 专题记录“新增只读契约”；不提前记录成功。

**验收：** 同样筛选两端返回同样身份和字段；分页、计数及空／无权／错误分开；金额不混入订单 DTO。没有真实 API 返回前，不把假数据接入 LIVE 页面。

### T03：统一订单列表与详情

**依赖：** T01、T02。**文件：** 新 orders 目录；`MobileOrderDetail.vue`、原 `ProgressWorkspace.vue` 与相关调用消费者。

1. `OrderWorkspace` 调用 Q1；手机卡片和桌面表格复用相同行模型，生产／质量只是受权限约束的 view。
2. 选择行后按明确类型及 code 定位；复用 `useScanContext` 的代次与 `canonicalTarget`。扫码候选选择同一流程。
3. 将详情展示整理为概况／流转／附件。工序条款与母子单要求分来源显示；缺失显示未填写，不拿模板现值覆盖历史。
4. 切筛选、翻页、多选、返回按第 4.3 节处理；页面不默认把第一行当可提交对象。
5. 流转先显示现有可验证事实，明确所属单据；跨单聚合接口不足时保留本单记录并记录缺口，不能写假时间线。
6. 原 `ChainReview` 仍保留其有效消费者，避免顶部追溯和单内详情重叠打开两层。同一事件只触发一个目标查看器。

**验收：** 扫码、列表、客户、待办打开相同目标；A→B 迟到响应不覆盖；返回保留筛选；多母单／轮次不串单；没有无权限客户、金额或附件残留。

**定向验证：** 新列表／详情用例、`useScanContext.test.ts`、`MobileOrderDetail.test.ts`、`ProgressWorkspace.test.ts`、`ChainReview.test.ts`、相关目标守卫测试。

### T04：待办首页与更多／工具

**依赖：** T01、T03。**文件：** `M1Dashboard.vue`，必要的待办展示子组件，新展示分组。

1. 首页优先展示待办类型、期限、对象和下一步；只保留本岗常用操作，详细列表进入订单。
2. 轻任务继续调用 `deskTaskApi`；本人、我发出、管理员全部未完成的范围照现行规则。
3. 业务作业打开统一详情或本岗表单；审批待办按真实审批 API 读取，不能把可见列表数当全量待审批数。
4. 单个来源失败时保留其他成功来源并标记失败，避免整个首页被吞成无待办。
5. 更多／工具依旧提供原 17 页入口；个人安全与管理员存储入口分开。

**验收：** 轻任务完成不改工序；无指派权限不显示新建；列表计数口径准确或明确本页；业务和审批不能通用“勾选完成”。

**定向验证：** `M1Dashboard.test.ts`、`ApprovalWorkspace.test.ts` 及实际改变的入口测试；没有后端变更不跑后端套件。

### T05：收货表单与成功后的单据操作

**依赖：** T03。**文件：** `IntakePanel.vue`、`RequirementComposer.vue`、`InboundWorkspace.vue`、`MobileOpsWorkspace.vue`、`OrderActionHost.vue`。

1. 手机分两段；电脑按同一草稿模型分组。不要在桌面复制另一份建单 payload。
2. 第一步选客户稳定 ID、来源和来料明细；运单可不填，不强制快递公司。加工／寄存选项接现有不同业务。
3. 第二步处理模板、步骤、最终要求、交期和证据；模板替换手改要求时明确确认，500 字限制不截断。
4. 新建仍调用 `submitMobileIntake`。原母单确认、KEEP／SPLIT 仍在原链路，不因“已生成母单”而自动确认或拆单。
5. 成功页面只使用新母单真实返回值；打印、拆单、附件参数来自该返回，不从之前选中的订单取。
6. 保存超时保留原 requestId；上传先留草稿，拿到正式编号后显式关联。继续收下一件重置来源、模板快照和本笔请求，不串文件。

**验收：** 三种来料来源、无运单、同运单多母单、两条同单位材质明细、模板手改保护、草稿往返、保存后新母单打印、寄存不生成加工母单。

**定向验证：** `IntakePanel.test.ts`、`RequirementComposer.test.ts`、`InboundWorkspace.flow.test.ts`、`MotherSplitPanel.test.ts` 中受影响用例；后端契约未改不联跑后端。

### T06：工序进度与正式交工

**依赖：** T03。**文件：** `ProcessProgressPanel.vue`、`ProductionPanel.vue`、`OrderActionHost.vue`，必要只提取两者共享展示。

1. 给已有 Panel 增加可选“已定位目标／嵌入展示”入口，默认 props 保持原页面兼容；有明确目标时自动查询业务 View，收起重复的定位表单。
2. 同一动作容器中切换进度与交工；分别保留请求、版本、证据和成功回执。切换后不能沿用另一个动作的幂等键。
3. 进度五档、版本冲突与回退原因按原服务；100% 仍提示尚需正式交工。
4. 交工先核对当前岗位条款，再按原 `submitProcessStation` 契约确认；不要为了原型字段而往现有不支持的接口塞数量重量。
5. 有多工序或子单选择时仍由用户明确选；已交付的旧轮次只读，重做使用新轮次。
6. 将改单内容从进度主表单移入独立动作，保留其全部原调用及证据。

**验收：** 无需重复手输目标；100% 不新增交工／库存记录；正式交工只成功一次；进度回退和冲突可识别；轮次与原单没有混用。

**定向验证：** `ProcessProgressPanel.test.ts`、`ProductionPanel.test.ts`；后端变动才选 `ProcessProgressServiceTest/ProcessStationServiceTest` 及直接调用链。

### T07：质检、返工、复检与放行

**依赖：** T03。**文件：** `QualityPanel.vue`、`QualityWorkspace.vue`、`OrderActionHost.vue`。

1. 从 `lookupQualityCheck` 的 `lines` 渲染检查项，不能用原型固定 3 条替代真实要求。
2. 所有条款完成判定、必要缺陷原因已填写才提交。过程说明保留独立入口。
3. 结果不合格后根据真实 View 显示冻结、开返工；返工报工后根据 `canRecheck` 展示复检。
4. 结果合格仍按 `canRelease/releaseBlock` 显示独立放行；不按最后一条“合格”直接写成已放行。
5. 质检员来自会话；轮次详情、检验历史、损耗申请与试算入口保留。

**验收：** 未判全不可提交；不合格需原因；返工未报工不能复检；未具备放行条件不可放行；旧响应、权限变化和重复提交不串单。

**定向验证：** `QualityPanel.test.ts`、`QualityScanGuard.test.ts`、`QualityExceptionInputs.test.ts`；服务改动才增加质检直接链路测试。

### T08：库存与发货

**依赖：** T03。**文件：** `InventoryTraceWorkspace.vue`、`PrepStockPanel.vue`、`ShippingPanel.vue`、动作容器。

1. 保留加工库存／客户寄存备料／公司料三类；公司料、备料批次不伪装为加工母单。
2. 库存列表先显示，按批次打开流水和动作；全部出入库检索独立可达。
3. 选中批次和位置改变时同步失效旧维度。未放行或不可发批次不提供发货，改为查看来源或阻断原因。
4. 发货按 `operationContext.balanceCandidates/selectedBalance` 等现行来源明确选择完整维度，禁止第一条兜底。
5. 核对本次数量、重量、物流后复核提交；用户自提等物流方式按原接口规则，不新增假必填。
6. 保留退货、售后复核、公司料领退、备料转加工、冻结、解冻、确认及冲正，原型未画出的也要有入口。

**验收：** 10.5 不变 10；克／颗不相加；领用不自动成本核销；不同客户同材质不混批；物流扫码不自动发货；超时回放与成功后刷新失败正确。

**定向验证：** `PrepStockPanel.test.ts`、`ShippingPanel.test.ts`、`InventoryBalanceSelection.test.ts`、`businessWorkspaceApi.test.ts` 中实际影响用例。库存契约变更另跑相应服务与仓储链路。

### T09：单内标签、证据与改单

**依赖：** T03、T05–T08 的必要回执。**文件：** `LabelWorkspace.vue`、`labelPrint.ts`、证据组件、`ProcessProgressPanel.vue` 里原改单调用。

1. 优先抽出 `OrderLabelPanel.vue` 供详情和旧标签 Workspace 共用，打印队列／模板管理仍留工具或管理页。
2. 按规范对象决定现有标签 API。旧 `zhubao-open-label(code)` 当前默认母单，新增精确目标适配时保留旧调用语义；子单和轮次不能继续硬填 `MOTHER_ORDER`。
3. 工位、队列、份数、快照及版本进入指纹；同一请求超时复用，改单换版后创建符合现行规则的新请求。
4. 多选仅保留同一账号范围内的精确行目标；先预览。若提供逐单任务提交，逐项显示成功／失败／未知，禁止“部分成功也显示全部成功”。
5. 证据页复用已有分页查询及签名预览，保留原件入口。聚合只归组，不重新归属母单，不公开缓存其他客户文件。
6. 改单表单仍调用 `/order-changes` 链路；财务确认金额不变／差额／拒绝与生效分开，按基准业务版本提交。

**验收：** 新母单打印正确；子单所属母单及版本正确；不把提交任务写成已出纸；原文件和历史关联保留；财务确认前不能擅自生效；轮次不覆盖原交付。

**定向验证：** `LabelWorkspace.test.ts`、`labelPrint.test.ts`、`EvidenceCapture.test.ts`、`EvidencePreviewLink.test.ts`、`evidenceApi.test.ts`、改单现行相关用例。

### T10：客户、财务、审批与管理入口

**依赖：** T03、T04。**文件：** 对应 Workspace 和工具分区，新增导航目标适配。

1. 客户列表和详情分开；详情内在制打开统一精确目标。材质／要求模板从基础资料进入，编辑行为不改变。
2. 增加可选账单上下文 `{customerId, yearMonth, receivableCode?}`。客户内开账单与财务内开账单均按同一稳定 ID 查询；不同客户切换清空旧收款表单和异步响应。
3. 收款仍传真实 `allocations[{receivableCode,amount}]` 和原 `idempotencyKey`；账单展示总额不能直接作为无分摊收款的默认命令。
   现行允许预收的场景必须保留：本次收款与分摊金额可不同，不把“收款金额不得超过本账单未收”作为新增业务规则；分摊上限与未分摊余额按现有财务服务处理。
4. 财务其余工资、耗材、资产、折旧、外协、应付、价格协议、经营明细入口保留；金额显隐只是展示，不影响授权或查询过滤。
5. 审批列表从可见任务进入，按真实场景、允许身份、申请人与审批人分离、版本／状态提交；非管理员获得审批菜单时不能被新导航挡掉。
6. 登记导入保留预检→复核→显式入账，账号保留顶岗与组织客户工序授权；存储、网关、运维、回收站、备份、升级、PWA 仍可达。

**验收：** A 客户账单不能给 B 客户登记收款；不授权的财务字段不出现在 DOM；审批不能自批或重复成功；原低频能力逐行可达。

**定向验证：** `CustomerArchiveWorkspace.test.ts`、`FinanceWorkspace.test.ts`、`ApprovalWorkspace.test.ts` 及实际动到的管理页用例。不因“移动菜单”全跑全部后台。

### T11：桌面布局与跨端收尾

**依赖：** 前述共享契约和相关业务表单已验证；桌面实施已获用户授权。

1. 接入电脑表格／右侧详情、岗位列表 view、主动打开表单；同一业务数据与动作容器沿用共享实现。
2. 1024px 及空间不足时使用收起／抽屉，1366px 笔记本可顺畅核对；不能强迫整个窗口横向滚动。
3. 每项设计稿交互接真实服务或明确保留原入口；移除临时占位、重复标题、重复定位面板和遗留双弹层。
4. 对照 17 页清单逐项走通入口，确认退货、复检、补打、导入、改密、备份等没有因精简丢失。
5. 只有查清运行时、类型、测试、脚本和装配消费者后才清理旧展示代码。历史迁移、库存数据、财务数据不删除。

**验收：** 第 7 节场景通过；核心文档记录真实进度与未验收项。正式打包、真机扫码、佳博出纸与线上发布按后续任务处理，不伪称完成。

## 7. 具体验收场景

在隔离测试夹具或本地测试数据中构造。不要为了验收往现有线上库造客户、订单、付款或打印任务。

| 编号 | 前置及步骤 | 必须看到的结果 |
|---|---|---|
| AC01 | 只有报工权限的操作工登录，分别打开电脑和手机 | 看到可读单及报工；没有收款、账号管理、任意库存转移 |
| AC02 | 业务管理只勾 INTAKE，不勾 REPORT/QUALITY/SHIP | 只出现对应收货写入口；查到其他状态也不能提交未授权动作 |
| AC03 | 授权客户范围为空；搜索明确的其他客户单号 | 空列表或可识别拒绝；不显示客户、数量、附件和金额 |
| AC04 | 相同运单有两张母单，扫码／全局输入该运单 | 先选目标；取消后没有任何正式操作，文件归属不变化 |
| AC05 | 打开 A 单立即打开 B 单，A 响应最后才到 | B 的要求、库存、证据和操作目标保持不变 |
| AC06 | 选择 A 后按客户筛选使 A 消失 | A 的详情、批量选择及提交按钮失效；不能提交隐藏旧单 |
| AC07 | 一母单拆两子单，再有一已完成工序改单重做轮次 | 看到明确母子单和轮次身份；进度、交工、质检不落回旧单 |
| AC08 | 手机收货第一段填不同货名，前进再返回；电脑关闭再打开同草稿 | 原输入仍在；没有重新套模板覆盖手改要求 |
| AC09 | 新建母单前浏览另一客户订单，完成建单后立即打印 | 标签取刚生成母单；不是先前选中的单；打印任务仍待核纸 |
| AC10 | 建单请求超时，以相同内容重试 | 原 requestId 回放；没有多生成一张母单或多笔库存 |
| AC11 | 把工序进度从 60% 改到 100%，再刷新 | 仅进度记录变化；交工、质检、库存均未被隐式写入 |
| AC12 | 从 90% 回退到 30%，未填原因；再填原因提交 | 先拒绝；后按 expectedVersion 正常记录，不覆盖并发新版本 |
| AC13 | 没确认岗位要求直接正式交工；确认后连点两次 | 先阻断；后只成功一次；失败／超时不换请求造成第二笔 |
| AC14 | 质检只判部分条款，或不合格缺原因；之后补齐 | 未完成时拒绝；补齐后按真实结果冻结／进入后续，未自动放行 |
| AC15 | 返工尚未报工就点复检；合格但服务端不可放行 | 展示真实阻断原因；不能绕过状态办理 |
| AC16 | 同客户同材质有两条不同库位／状态库存 | 手选完整维度；未放行批次无发货动作，不猜第一条 |
| AC17 | 10.500 克、120 颗、客户料和公司料分别查看 | 显示 10.5 克和 120 颗；不混加、不错归客户 |
| AC18 | 发货成功后故意让刷新接口失败 | 仍显示真实发货回执与刷新失败，不能诱导重发 |
| AC19 | 先开锦和账单，再开云璟账单，旧请求迟到；登记收款 | 当前客户 ID、账期、应收行均是云璟；旧表单不残留 |
| AC20 | 分摊超过本次收款或对应应收余额；另测合法未分摊预收 | 非法分摊按原服务拒绝；合法预收仍保留，不篡改账单单价 |
| AC21 | 轻任务完成；审批申请人自己尝试决定 | 轻任务只改该任务；自批拒绝；与工序库存无关 |
| AC22 | 新旧标签、历史附件、待关联草稿文件分别回看 | 标签版本清楚；签名失效可重新取址；待关联文件仍可正确恢复 |
| AC23 | 表单中扫码，退出表单后再点击底部扫码；拒绝相机 | 前者填当前表单，后者统一找单；拒绝后可手输／相册，不触发已卸载组件 |
| AC24 | 退出账号 A，登录账号 B，打开首页／更多／最近单据 | 没有 A 的缓存、草稿、金额、文件或挂起扫码操作 |
| AC25 | 逐行点击现有 17 页对应新入口 | 所有必要能力仍可到达；无效入口明确错误而非静默空白 |

视觉检查至少选手机 360/390/430，电脑 1024/1366/1440；同时检查 900/901 切换、长单号、长要求、空列表和错误提示。只验证受本轮改动影响的视图，相关代码未变化时不反复重跑。

### 7.1 场景与增量对应

| 增量 | 优先执行的场景 | 验证重点 |
|---|---|---|
| T01 | AC01、AC02、AC23、AC24 | 原菜单兼容、扫码生命周期、会话切换 |
| T02–T03 | AC03–AC07 | 服务端范围、分页／计数、身份及异步串单 |
| T04 | AC01、AC02、AC21、AC25 中入口部分 | 待办分类、指派与决策分别授权 |
| T05 | AC04、AC08–AC10、AC22 的收货证据 | 草稿、来源明细、新单打印与回放 |
| T06 | AC07、AC11–AC13 | 进度与正式交工、版本和轮次 |
| T07 | AC07、AC14–AC15、AC22 的质检证据 | 条款、异常、复检与放行 |
| T08 | AC16–AC18 | 完整库存维度、单位和发货回执 |
| T09 | AC07、AC09、AC22 | 标签快照、证据归属与改单 |
| T10 | AC19–AC21、AC25 中管理能力 | 客户账单、合法预收、审批与低频能力 |
| T11 | AC25 及本轮变动的跨端场景 | 页面可达、布局及共享实现回归 |

已经通过且相关代码没有变化的场景复用结果；共享实现后续改变时，只重验受影响场景。T02 的后端范围与分页用例按第 5.6 节补齐，不用纯页面点击代替授权测试。

## 8. 验证命令与提交

### 8.1 前端

项目没有名为 `typecheck` 的现成 script；使用已安装的 `vue-tsc`。以下从仓库根目录运行，按任务选择路径，不是每次全部执行。

```powershell
# 共享类型和跨组件 props 改动时执行。
pnpm --filter zhubao-erp-frontend exec vue-tsc --noEmit

# T01 示例：导航和扫码。
pnpm --filter zhubao-erp-frontend exec vitest run src/App.test.ts src/features/scan/ScanInput.test.ts src/features/scan/mobileScanBridge.test.ts src/features/m1/MobileOpsWorkspace.test.ts

# T05 示例：收货和拆单消费者。
pnpm --filter zhubao-erp-frontend exec vitest run src/features/intake/IntakePanel.test.ts src/features/intake/RequirementComposer.test.ts src/features/m1/InboundWorkspace.flow.test.ts src/features/m1/MotherSplitPanel.test.ts

# T06/T07/T08 中只选择本次实际修改的组件，下面展示参数写法。
pnpm --filter zhubao-erp-frontend exec vitest run src/features/m1/ProcessProgressPanel.test.ts src/features/production/ProductionPanel.test.ts
```

新建组件的测试路径由新增文件确定后写入本次验证记录，不引用不存在的测试。不为纯样式补镜像实现的单元测试；交互和失败恢复需要有实际价值的断言。

### 8.2 后端

仅 Q1 或其他真实后端变更时执行对应链路。新增 Q1 测试名按此文档创建，先有文件再运行：

```powershell
powershell -ExecutionPolicy Bypass -File scripts/maven.ps1 -f backend/pom.xml '-Dtest=OrderWorkspaceQueryServiceTest,OrderWorkspaceControllerTest' test
```

若改到进度、扫码、质检或库存的共同实现，再追加对应既有测试；例如 `ProgressBoardServiceTest`、`ProcessProgressServiceTest`、`ScanContextServiceTest`、`ScanAccountScopeTest`。只有测试依赖确实跨模块时才扩大，不把所有名字凑进一次全量检查。

出现 Mockito、数据库或工具环境失败时如实记录为未取得结论，修复必要环境或保持阻断；不能把“没有结果”写成通过。不得为了让测试通过删掉越权、失败、幂等或并发断言。

### 8.3 每个增量提交前

```powershell
git diff --check
git diff --stat
git diff --cached --stat
```

确认只暂存本增量，使用语义化标题与中文正文；例如 `feat(ui): unify mobile navigation and order context`。已有同文件改动混在一起时选择性暂存，不能顺手提交别人的业务代码。Git 写入受限时走当前工具的正常审批路径，不绕过权限。

核心文档只追加本增量事实：改了什么、实际跑了什么、通过或失败、尚未关闭的真实问题、下一步。新增／修改接口才更新 API 专题；启动导航变化才更新 README。不另造一套重复进度总览。

普通 UI 开发不自动跑前后端全量、生产构建、Docker、镜像、APK 或包门禁。实际修改打包／部署逻辑或用户明确要求时，才触发对应门禁。

## 9. 中断恢复与失败处理

- **上下文不足：** 先看核心文档最近记录、相关提交和 `git diff`，定位最后一个已通过增量；不重做已完成阶段。
- **查询字段缺失：** 修 Q1 DTO／只读投影或显示明确未知，不用样例兜底，不去修改业务写入来迎合卡片。
- **已有接口权限不一致：** 复现并确定是否本次引入；本次引入则修复，既存跨域问题独立记录，不能通过放宽接口权限解决 UI 显示。
- **未支持的轮次标签／扫码：** 保留精确身份和只读记录；列为具体未完成项，补解析及必要验证后再开放动作，不能算全链完成。
- **旧面板仍有消费者：** 继续作为兼容包装，逐项迁消费者；不以新页面已能显示为理由删除。
- **设计与现有业务冲突：** 业务事实、权限和正确对象优先；调整布局或文案，记录与原型差异及原因。业务规则改变才向用户明确提出选择。
- **桌面未授权：** 不停止手机与共享实现；完成已授权部分，列出待确认桌面文件范围。

不得将“本阶段已完成”扩大为“整个 ERP 改造已完成”；不得将本地浏览器、H2 或模拟打印结果扩大为线上、真实 MySQL、相机或佳博出纸验收。

## 10. 可直接发给 Grok 的启动指令

```text
请在 F:\APPkaifa\zhubao-ERP 中执行 docs/prototypes/跨端UI改造实施文档.md。

先读取 AGENTS.md、项目核心和实施文档指定的设计稿，再按 T00 → T11 的依赖实施。
本次授权的改造范围：手机端和电脑端，按两份设计稿及实施文档执行。
这次是开发改造，不包括部署或操作真实业务。

不要再给泛化计划。先检查工作区，完成 T00 基线，开始第一个已授权小增量。
保留已有未提交业务改动；不要把 HTML 的样例数据或模拟代码复制进正式产品。
Q1 是拟新增只读查询，先做真实契约和定向验证，再接统一列表。
菜单合并不扩大权限；母单、子单、轮次、库存、金额、幂等和证据规则按文档保留。

每个增量完成必要验证后独立提交，更新核心文档；没有真实阻断时继续后续已授权增量。
每次汇报：任务编号、实际文件、用户可见变化、验证结果、重要未完成项和下一步。
不要自动跑全仓测试、打包、Docker 或发布。不要把未验证的功能写成已完成。
```

上面的指令已包含两端开发授权，用户将其发给 Grok 后，Grok 无需再次索取相同范围的确认。若只希望先做手机，将范围句改为“本次只授权手机端与必要共享部分，保留现有桌面布局”。

## 11. 开发者每阶段的完成回执格式

```text
增量：Txx / 子增量名称
完成行为：用户从哪里进入、现在看到什么、办理哪一个真实业务动作
变更文件：列实际路径
接口：复用哪些；新增或修改哪些；是否更新契约文档
业务约束：本增量涉及的身份、范围、版本、幂等与证据如何保留
验证：命令 / 页面场景 / 实际结果，不复制未执行的原型测试结论
提交：本增量提交号
剩余：阻断或尚未接通能力，写清影响；没有则写无
下一步：下一个已授权任务编号
```

本文是执行计划，任务状态以[项目核心](../00_项目核心.md)中的实际实施与验证记录为准。
