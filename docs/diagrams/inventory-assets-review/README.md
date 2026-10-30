# 客户库存与物料资产图集

日期：2026-10-30。配套规则统一见[总方案客户库存](../../大版本整体重构方案.md#customer-stock)及[本厂实物章节](../../大版本整体重构方案.md#factory-assets)。原独立调研已归档，本目录仅维护图源及产物。均为目标设计，不代表已实施；客户入口已确认先用手机网页登录。

| 图 | 查看与放大 | 可编辑图源 |
|---|---|---|
| 01 库存与资产功能架构 | [PNG](./01-ownership-and-functions.png) · [SVG](./01-ownership-and-functions.svg) | [DOT](./01-ownership-and-functions.dot) |
| 02 实物操作与费用关系 | [PNG](./02-operations-and-finance.png) · [SVG](./02-operations-and-finance.svg) | [DOT](./02-operations-and-finance.dot) |

图中文字统一指定宋体`SimSun`；修改DOT后重新生成SVG及PNG，并检查文字、连线和边界。例如：

```powershell
dot -Tsvg docs/diagrams/inventory-assets-review/01-ownership-and-functions.dot -o docs/diagrams/inventory-assets-review/01-ownership-and-functions.svg
dot -Tpng -Gdpi=120 docs/diagrams/inventory-assets-review/01-ownership-and-functions.dot -o docs/diagrams/inventory-assets-review/01-ownership-and-functions.png
```
