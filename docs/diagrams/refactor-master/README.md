# 整体重构图集

日期：2026-10-30。对应[大版本整体重构方案](../../大版本整体重构方案.md)，统一呈现今天确认的加工、交付、费用、客户库存及本厂实物管理。均为目标设计，尚未实施。

| 图 | 查看与放大 | 可编辑图源 |
|---|---|---|
| 01 整体目标架构 | [PNG](./01-overall-architecture.png) · [SVG](./01-overall-architecture.svg) | [DOT](./01-overall-architecture.dot) |
| 02 整体交付依赖 | [PNG](./02-delivery-roadmap.png) · [SVG](./02-delivery-roadmap.svg) | [DOT](./02-delivery-roadmap.dot) |

逐环节逻辑另见[加工图集](../system-review/README.md)、[财务图集](../finance-review/README.md)、[库存资产图集](../inventory-assets-review/README.md)。阶段编号只统筹这些既有任务，不另立一套执行任务。

图中文字统一指定宋体`SimSun`。修改DOT后重新生成SVG和PNG，不直接改生成图片。例如：

```powershell
dot -Tsvg docs/diagrams/refactor-master/01-overall-architecture.dot -o docs/diagrams/refactor-master/01-overall-architecture.svg
dot -Tpng -Gdpi=120 docs/diagrams/refactor-master/01-overall-architecture.dot -o docs/diagrams/refactor-master/01-overall-architecture.png
```
