"""保存补充调查的源码定位和纯闲置导入候选，不修改业务、配置或原调查快照。"""

from pathlib import Path
import csv
import json
import re
import subprocess

目录 = Path(__file__).resolve().parent
根目录 = 目录.parents[2]
已跟踪 = subprocess.check_output(["git", "-c", "core.quotePath=false", "ls-files"], cwd=根目录, text=True, encoding="utf-8").splitlines()
文本 = {}
for 名称 in 已跟踪:
    p = 根目录 / 名称
    if 名称.startswith(("docs/", "archive/")) or p.suffix not in {".java", ".ts", ".vue", ".css", ".cs", ".kt", ".ps1", ".sh", ".sql", ".yml", ".example"}:
        continue
    文本[名称] = p.read_text(encoding="utf-8-sig")

主题 = {
    "旧库存入口": r"PrepStockController|PrepStockService|/prep-stock|/ledger/transfers",
    "库存与新结单": r"class StockLedgerService|class OrderSettlementService|class OrderDeliveryCompletionQuery|POST_DELIVERY|listOutboundOrdersForMother|unpriced\(",
    "旧请求分支": r"legacy-.*UUID|postInbound\(command, null\)|postShipment\(command, null\)|postStation\(submittedCommand, account, null\)|postInboundInSession|requestId\(\"reg-|register-outbound:",
    "设备消费接续": r"maybePostProductionReport|consumptionRepository\.save|processStationService\.reportProduction",
    "打印协议与复用": r"agents/v2/jobs|agents/jobs|class PrintLeaseScheduler|labels\.(prepareBatchSnapshot|withCustomerMark|readEdition|assertBatchReadable)|zhubao\.print-agent\.token|class PrintJobController",
    "浏览器打印旁路": r"openLabelPrint|printBrowser|buildLabelPrintDocument|win\.print",
    "演示工作台闭包": r"BusinessWorkspaceRepository|BusinessWorkspaceSeedData|BusinessWorkspaceDataScopeFilter|workspace\.demo-enabled|dataSource.*DEMO|ZHUBAO_BUSINESS_REPOSITORY|ZHUBAO_BUSINESS_SNAPSHOT_CODE",
    "其他运行时样例": r"DocumentManagementSeedData|KNOWN_CUSTOMERS|seedReviewDevicesWhenEmpty|demo-device-key|demoUsernames|InMemoryHardwarePocRecordRepository",
    "旧新明细": r"biz_mother_order_item|biz_outbound_order_item|biz_receipt_line|biz_child_line_allocation|insertMotherOrderItem|insertOutboundOrderItem",
    "账号预审批": r"validatePreApprovalApplication|enforcePreApprovalForAccountActions|AuthAccountPreApprovalApplicationRepository|createPreApprovalTask|pre-approval-(readiness|drafts|applications|evidence)",
    "账号事务保护": r"actionRepository\.commit|auditRepository\.insertOn|removesEnabledAdmin|revokeCredentialsAfterCommit|retrySessionRevocation|requireApprovedPreApprovalTask",
    "配置与历史命令": r"storage-mode|check-m0|check-m1|business\.snapshot-code|auth\.pre-approval|\"business\", \"business-document\"",
}
证据 = []
for 主题名, 表达式 in 主题.items():
    模式 = re.compile(表达式)
    for 文件, 内容 in 文本.items():
        for 行号, 行 in enumerate(内容.splitlines(), 1):
            命中 = sorted({m[0] for m in 模式.finditer(行)})
            if 命中:
                分类 = "历史迁移" if "/db/mysql/" in 文件 else "测试" if "/test/" in 文件 or ".test." in 文件 else "现行源码或装配"
                证据.append([主题名, 文件, 行号, "；".join(命中), 分类])
with (目录 / "补充引用证据.csv").open("w", encoding="utf-8-sig", newline="") as f:
    writer = csv.writer(f)
    writer.writerow(["主题", "文件", "行号", "匹配标识", "消费者分类"])
    writer.writerows(证据)

# 同文件声明为唯一出现的导入，可发现“导入存在但实际未用”的引用图假边。
闲置导入 = []
for 文件, 内容 in 文本.items():
    if not 文件.startswith("frontend/src/") or not 文件.endswith((".ts", ".vue")) or ".test." in 文件 or "/test/" in 文件:
        continue
    for m in re.finditer(r"import\s+(?:type\s+)?\{([^}]+)\}\s+from\s+['\"]([^'\"]+)['\"]", 内容):
        for 项 in m[1].split(","):
            名称 = re.sub(r"^type\s+", "", 项.strip()).split(" as ")[-1].strip()
            if re.fullmatch(r"[A-Za-z_$][\w$]*", 名称) and len(re.findall(r"\b" + re.escape(名称) + r"\b", 内容)) == 1:
                闲置导入.append([文件, 内容[:m.start()].count("\n") + 1, 名称, m[2]])
with (目录 / "前端闲置导入候选.csv").open("w", encoding="utf-8-sig", newline="") as f:
    writer = csv.writer(f)
    writer.writerow(["文件", "导入起始行", "本地标识符", "导入来源"])
    writer.writerows(闲置导入)

摘要 = {
    "基准提交": subprocess.check_output(["git", "rev-parse", "HEAD"], cwd=根目录, text=True).strip(),
    "主题数": len(主题), "定位记录数": len(证据), "前端闲置导入候选数": len(闲置导入),
    "验证边界": "源码、装配、测试及脚本定位已经复核；用户已确定业务退役范围。未执行源码删除、业务测试、服务器数据库检查或现场出纸验收。",
}
(目录 / "补充调查摘要.json").write_text(json.dumps(摘要, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
print(json.dumps(摘要, ensure_ascii=False))
