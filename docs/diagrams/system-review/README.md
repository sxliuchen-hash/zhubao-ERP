# 系统设计图集

日期：2026-10-30。配套说明见[系统设计复盘与功能图解](../../系统设计复盘与功能图解.md)。现状图按源码基准`85b3149`绘制，目标图不是已上线能力。

目标图已按本次确认的“客户每次发货后出账、员工整单完成后核算”更新；两张财务专图及计算说明见[财务设计复盘](../../加工费灵活计费与财务设计复盘.md)。

客户库存、公司料、耗材／工具与固定资产的扩展架构和流程另见[库存资产图集](../inventory-assets-review/README.md)，与本图集共用实物及财务来源。

PNG用于直接阅读，SVG可放大，DOT为可编辑图源；修改图源后应重新生成对应SVG和PNG，不直接修改生成图片。

| 图 | 查看与放大 | 可编辑图源 |
|---|---|---|
| 01 现有系统架构 | [PNG](./01-current-architecture.png) · [SVG](./01-current-architecture.svg) | [DOT](./01-current-architecture.dot) |
| 02 目标业务架构 | [PNG](./02-target-architecture.png) · [SVG](./02-target-architecture.svg) | [DOT](./02-target-architecture.dot) |
| 03 业务流程 | [PNG](./03-business-flow.png) · [SVG](./03-business-flow.svg) | [DOT](./03-business-flow.dot) |
| 04 对象与编号关系 | [PNG](./04-object-relationships.png) · [SVG](./04-object-relationships.svg) | [DOT](./04-object-relationships.dot) |
| 05 数量示例 | [PNG](./05-quantity-example.png) · [SVG](./05-quantity-example.svg) | [DOT](./05-quantity-example.dot) |
| 06 报工与提交边界 | [PNG](./06-reporting-transaction.png) · [SVG](./06-reporting-transaction.svg) | [DOT](./06-reporting-transaction.dot) |
| 07 状态与办理门槛 | [PNG](./07-status-gates.png) · [SVG](./07-status-gates.svg) | [DOT](./07-status-gates.dot) |
| 08 打印与扫码逻辑 | [PNG](./08-access-printing.png) · [SVG](./08-access-printing.svg) | [DOT](./08-access-printing.dot) |
| 09 功能逻辑 | [PNG](./09-functional-logic.png) · [SVG](./09-functional-logic.svg) | [DOT](./09-functional-logic.dot) |
| 10 改造依赖与试点 | [PNG](./10-implementation-roadmap.png) · [SVG](./10-implementation-roadmap.svg) | [DOT](./10-implementation-roadmap.dot) |

## 重新生成

使用已有Graphviz，针对修改的图源执行，例如：

```powershell
dot -Tsvg docs/diagrams/system-review/03-business-flow.dot -o docs/diagrams/system-review/03-business-flow.svg
dot -Tpng -Gdpi=120 docs/diagrams/system-review/03-business-flow.dot -o docs/diagrams/system-review/03-business-flow.png
```

图中文字统一指定宋体`SimSun`。重新生成后核对中文、连线、标签和边界；图中的实线／虚线及文字用于说明现有基础和目标能力，不表示发布验收结果。
