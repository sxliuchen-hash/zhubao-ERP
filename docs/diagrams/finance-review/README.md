# 财务设计图解

日期：2026-10-30。目标：客户每次发货后出本次账单，员工整张加工目标完成后核算。以下是设计图，不代表已上线。

配套说明：[加工费灵活计费与财务设计复盘](../../加工费灵活计费与财务设计复盘.md)。

| 图 | 直接查看 | 放大 | 可编辑图源 |
|---|---|---|---|
| 客户按次出账与员工整单核算 | [PNG](./01-billing-and-labor-flow.png) | [SVG](./01-billing-and-labor-flow.svg) | [DOT](./01-billing-and-labor-flow.dot) |
| 灵活计费逻辑 | [PNG](./02-flexible-pricing.png) | [SVG](./02-flexible-pricing.svg) | [DOT](./02-flexible-pricing.dot) |

修改DOT图源后用已有Graphviz重新生成对应SVG和PNG；不直接编辑生成图片。统一使用宋体`SimSun`。

```powershell
dot -Tsvg docs/diagrams/finance-review/01-billing-and-labor-flow.dot -o docs/diagrams/finance-review/01-billing-and-labor-flow.svg
dot -Tpng -Gdpi=120 docs/diagrams/finance-review/01-billing-and-labor-flow.dot -o docs/diagrams/finance-review/01-billing-and-labor-flow.png
```
