# 整体重构图集

日期：2026-10-30。对应[完整总方案](../../大版本整体重构方案.md)，统一呈现加工、外协、交付、费用、客户库存、本厂物料工具、固定资产、打样、金工及拍照计数扩展。均为目标设计，尚未实施。

| 图 | 查看与放大 | 可编辑图源 |
|---|---|---|
| 01 整体目标架构 | [PNG](./01-overall-architecture.png) · [SVG](./01-overall-architecture.svg) | [DOT](./01-overall-architecture.dot) |
| 02 整体交付依赖 | [PNG](./02-delivery-roadmap.png) · [SVG](./02-delivery-roadmap.svg) | [DOT](./02-delivery-roadmap.dot) |
| 03 拍照计数预留与数量回填 | [PNG](./03-photo-counting-extension.png) · [SVG](./03-photo-counting-extension.svg) | [DOT](./03-photo-counting-extension.dot) |
| 04 外协实物流转与费用 | [PNG](./04-outsourcing-flow.png) · [SVG](./04-outsourcing-flow.svg) | [DOT](./04-outsourcing-flow.dot) |
| 05 Grok执行分组依赖 | [PNG](./05-grok-work-nodes.png) · [SVG](./05-grok-work-nodes.svg) | [DOT](./05-grok-work-nodes.dot) |

逐环节图源另见[加工图集](../system-review/README.md)、[财务图集](../finance-review/README.md)、[库存资产图集](../inventory-assets-review/README.md)；完整19张图的解释和业务规则均由[总方案](../../大版本整体重构方案.md#diagrams)维护，不再分别维护独立调研正文。P、F、I、A、O、S、M为业务增量，[Grok任务册](../../重构执行/Grok节点执行任务册.md)将其拆为105个执行节点；图05只展示组间关系，单卡准确依赖以任务册为准。资产、打样及金工属于本次完整版本，C1实际计数算法仍为后续接入。

图中文字统一指定宋体`SimSun`。修改DOT后重新生成SVG和PNG，不直接改生成图片。例如：

```powershell
dot -Tsvg docs/diagrams/refactor-master/01-overall-architecture.dot -o docs/diagrams/refactor-master/01-overall-architecture.svg
dot -Tpng -Gdpi=120 docs/diagrams/refactor-master/01-overall-architecture.dot -o docs/diagrams/refactor-master/01-overall-architecture.png
```
