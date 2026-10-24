# Cursor 库存与加工要求模板接续开发提示词

> 历史归档（2026-10-25）：停止日常维护，不再作为执行指令或当前完成状态。最新状态与未完成事项见[项目核心](../../00_项目核心.md)。

编写日期：2026-10-22。

以下代码块可直接复制给 Cursor。功能进度和验证结果仍统一维护在[项目核心](../../00_项目核心.md)，本文件是接手执行说明，不是另一份进度表。

```text
请在 F:\APPkaifa\zhubao-ERP 接续完成《docs/库存管理与母单加工要求模板改造方案.md》的本期开发，直接完成实现、定向验证和独立提交，不只补文档。按小增量一个一个完成，常规实现选择按你的推荐执行，不需要每步请求确认。

先读根 AGENTS.md、docs/00_项目核心.md 中最新接续节点，以及改造方案第 1、10、12、13 节；随后只读当前任务相关小节和代码。以 frontend/src/App.vue 的实际调用链定位功能，不通读归档，也不按旧阶段任务自动扩展。

一、保留当前工作区并确认基线

1. 当前工作区包含大量尚未提交的库存、模板页面、组织迁移和打印改动。先看 git status、git diff 和近期提交；不要 reset、clean、覆盖或重做已有实现，不要直接 git add .。
2. 已完成的 T1 后端主数据节点包括 RequirementTemplate、RequirementText、MasterDataService、MasterDataRepository、内存/JDBC 主数据仓储、MasterDataController 的模板接口和 V69。核对最新 feat(business) 模板主数据提交及核心文档。此节点的定向验证为 RequirementTemplateServiceTest 8 项、JdbcMasterDataRepositoryTest 3 项通过，使用 H2 MySQL 模式；这不等于页面或整个库存方案验收通过。
3. V69、V70、V71 已在当前工作区分别用于模板、库存单据和账号组织。提交中仅注册已提交的迁移；其余索引、探针和升级测试可能还有未提交差异。先核实真实目录、类路径索引、探针和测试，保持版本连续，不重编号、不改已发布迁移。V71 属于之前权限工作，不能漏掉它的原有差异或混入无关功能提交。
4. 已有打印接收程序、LabelWorkspace.vue、README、.gitignore 及打印教程的改动保留，不并入本轮库存/模板提交，不部署或操作测试服业务库。

二、不可改变的业务边界

1. 开粗、车形（车成珠型）、雕刻、抛光的本次勾选仅组成母单加工要求文字。不得因此新增正式报工工序、岗位授权、工资或计费项目。
2. 客户寄存收货单独入库，不建生产任务；公司料、客户料分别记权属。多材质和不同单位分别成行、成批，数量不混加。
3. 从备料建加工单必须在一个 BusinessWorkUnit/WorkSession 内建单和转库存，不能再次收货入库。先覆盖单材质生产，保留现行跨材质子单限制。
4. 公司料领用只表示转入在制并记用途，不自动消耗、并入客户料、产生财务金额。盒位二维码、多投入成品、公司料成本核销和自有产品销售不在本期。
5. 未称量可以保存待核，正式入账不得填假重量。最新库存草稿暂按数量和实称重量都大于零处理；若按颗物料要以零重量正式入账，需要先落定业务规则，不能直接放宽。现有库存测试里有以零重量表示裸石已入库的旧样本，须与当前规则核对，不能为了测试通过替换成无意义数据或放宽校验。

三、从这里继续，先处理已知缺口

A. 先闭合 T2/T3 的页面和母单链路：
- 核对 RequirementComposer、RequirementTemplatePanel、IntakePanel、CustomerArchiveWorkspace、masterDataApi 以及 MotherRequirementSource 仓储和建单调用。
- 模板编辑/停用不改变历史订单原文；切模板保留补充说明；手改最终文字后不得静默覆盖；重开下一单不得残留上单步骤或来源。
- 自定义步骤支持勾选、取消、排序；最终预览与 processingRequirement 实际保存一致；超过字段容量明确拒绝。扫码、子单、标签、质检仍读已保存文本。
- 模板维护页面的加载权限、在途操作、版本冲突与成功后新建请求编号需要定向核验。
- 早期合跑 RequirementComposer.test.ts、IntakePanel.test.ts、PrepStockPanel.test.ts 时，IntakePanel 有 1 项 5 秒超时和后续 1 项调用次数断言失败。应先定向重跑收货测试，分清机器负载/未结束异步造成的串扰与真实缺陷；不能只增加超时时间后宣称问题消失。

B. 再逐项闭合 I1—I7，复用已有 MaterialStockService/StockDesk/StockDocument 及 PrepStockPanel：
- 已有原单退回、解除冻结的校验移入工作单元，并为 JDBC 增加 related(sourceDocumentCode, session) 锁定读取，但最新修改尚未做对应 JDBC 并发/回滚验证。覆盖同请求回放、两个不同请求累计超原单、错误客户/权属/仓库/批次/物料、解除冻结原单串用和真实并发。
- 当前请求快照已改成完整 JSON，确认入账须区分实收每行内容，不再只比较行数。验证改内容、换账号、业务成功响应丢失等场景；核对规范化规则和所有页面提交是否遗漏字段。
- 核对库存与新单据详情读取是否都遵守客户、本人、班组/部门范围，包含 MaterialStockController 重建账号时保留组织字段，不能让独立库存接口绕过已提交的权限修正。
- 最新草稿会同步未开工、未入财务母单的领入/退回数量与明细，并拒绝已有下游事实时直接操作；ProgressBoardService/ProcessProgressService 增加零数量过滤。必须验证首笔备料建单、补料、部分/全部退回、来源明细、母单数量、库存、拆单与财务保护的一致性；不能把编译通过当成这些链路正确。
- 页面已补多条收货、整单待核确认、筛选、权限按钮、详情迟到响应保护、解冻与管理员冲正，以及收货用途“现在加工/先寄存”入口，均属待验收草稿。核对多行待核卡片唯一身份、确认时完整行数与顺序、错行/漏行、差异说明、加载失败和切单后旧响应。
- 核对公司料用途/实际领用人能在详情与流水追到，原单退回应尽量选择原记录，避免仅靠手填单号。历史缺来源或单位时如实展示，限制无法核实的写操作。
- 冲正草稿仍须核查：原收货事实、单位、版本/并发、原因与权限，不能把它做成可任意改余额的按钮；退还客户要生成正式离厂流水并排除在厂实物。
- 必须补库存正式单据仓储与台账/请求结果在同一 JDBC 事务的验证，特别是多行中途失败回滚、请求结果保存失败回滚、原单退回并发上限和备料建单不重复入库。当前只有早期内存链路样本，不能写成最新 JDBC 全链已通过。

四、验证、提交和记录

按 AGENTS 做最小充分验证。优先运行本增量的相关服务、仓储、控制器/组件测试和必要类型检查；定向失败先定位，不自动跑全仓、不打包、不部署、不为门禁启动 Docker。

模板节点参考命令：
powershell -ExecutionPolicy Bypass -File scripts/maven.ps1 -f backend/pom.xml "-Dtest=RequirementTemplateServiceTest,JdbcMasterDataRepositoryTest" test

库存节点从 MaterialStockServiceTest、JdbcStockLedgerRepositoryTest 及你新增的 JDBC 单据链路测试着手，迁移检查使用 DatabaseUpgradeServiceTest 中实际受影响用例；不要机械复用旧测试数字。

前端参考命令：
pnpm --filter zhubao-erp-frontend exec vitest run src/features/intake/RequirementComposer.test.ts src/features/intake/IntakePanel.test.ts src/features/m1/PrepStockPanel.test.ts
pnpm --filter zhubao-erp-frontend exec vue-tsc --noEmit

若机器在测试合跑时明显资源不足，可串行执行相关测试文件，保留真实失败信息；不得用忽略断言、删除测试或吞掉接口错误来通过验证。

每个节点独立提交中文摘要，只提交相关已验证文件和本节点的文档差异。注意核心文档、迁移索引/探针和升级测试含多个未提交节点时要按差异暂存，不能把缺失的 V70/V71 注册进只有 V69 的提交。更新 docs/00_项目核心.md 的实际完成、阻断与验证；API/规则确实变化时才更新对应专题，不再维护第二份逐项进度表。

交付时说明本次完成节点、提交号、实际测试及仍未闭合事项。把“草稿存在”“类型通过”“H2 通过”“真实 MySQL 通过”“已部署”分别写清，不能合称完成。持续完成本期任务，不自动执行方案第 11 节后续扩展。
```
