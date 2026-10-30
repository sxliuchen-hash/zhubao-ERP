# -*- coding: utf-8 -*-
"""GR-A02 只读存量核对。

读取本地 JSON 快照，列出母单、必要子单、收货与库存来源、标签实例、照片引用和少量下游计数。
只用只读方式打开快照。不连接数据库，不执行 DDL/DML，不读取或改写照片原件。

缺表写成「待核」，不当成零行。表在快照里行数为 0，也只说明这份快照如此，不能据此判断业务库没有记录。
样本目录只验证工具本身。无论快照种类如何，报告里的线上盘点都保持「待核」。

用法（在仓库根目录）：
  python scripts/gr-a02-readonly-stock-audit.py report --snapshot scripts/gr-a02-sample/snapshot.json
  python scripts/gr-a02-readonly-stock-audit.py diff --left scripts/gr-a02-sample/snapshot.json --right scripts/gr-a02-sample/snapshot-delta.json
  python scripts/gr-a02-readonly-stock-audit.py check
"""

from __future__ import annotations

import argparse
import copy
import hashlib
import json
import os
import sys
from decimal import Decimal, InvalidOperation
from pathlib import Path

TOOL = "gr-a02-readonly-stock-audit"
P0_CODE = "MD-20261029-0003"
QTY = Decimal("0.001")

# 这些表缺席时对应段落记待核。母单表缺席则无法生成清单。
OPTIONAL_TABLES = (
    "biz_work_order",
    "biz_receipt_line",
    "biz_child_line_allocation",
    "inv_stock_ledger",
    "biz_print_batch",
    "biz_print_job",
    "biz_label_reprint_log",
    "biz_label_trace",
    "doc_evidence_attachment",
    "doc_business_evidence_link",
    "biz_process_batch_handoff",
    "biz_quality_requirement_check",
    "biz_outbound_order",
    "fin_order_settlement",
)
MOTHER_FIELDS = (
    "quantity",
    "weight",
    "quantity_unit",
    "ledger_code",
    "customer_id",
    "evidence_code",
)


def configure_stdio() -> None:
    for stream in (sys.stdout, sys.stderr):
        reconfigure = getattr(stream, "reconfigure", None)
        if reconfigure is not None:
            reconfigure(encoding="utf-8")


def read_only_bytes(path: Path) -> bytes:
    """只读打开。调用方负责在结束后再次读取并核对字节未变。"""
    fd = os.open(path, os.O_RDONLY)
    try:
        chunks = []
        while True:
            block = os.read(fd, 1024 * 1024)
            if not block:
                break
            chunks.append(block)
        return b"".join(chunks)
    finally:
        os.close(fd)


def sha256_text(raw: bytes) -> str:
    return hashlib.sha256(raw).hexdigest()


def parse_snapshot(raw: bytes, label: str) -> dict:
    try:
        data = json.loads(raw.decode("utf-8"))
    except (UnicodeError, json.JSONDecodeError) as exc:
        raise SystemExit(f"无法读取快照 {label}：{exc}") from exc
    if not isinstance(data, dict) or not isinstance(data.get("tables"), dict):
        raise SystemExit(f"快照 {label} 须为对象，且包含 tables")
    meta = data.get("meta") or {}
    if not isinstance(meta, dict):
        raise SystemExit(f"快照 {label} 的 meta 须为对象")
    return data


def text(value) -> str:
    if value is None:
        return ""
    return str(value).strip()


def amount(value):
    """按原值比较。不超过三位小数时渲染为三位，便于和 DECIMAL(18,3) 对照；更长的小数不舍入。"""
    raw = text(value)
    if raw == "":
        return None, False
    try:
        number = Decimal(raw)
    except InvalidOperation:
        return None, False
    exponent = number.as_tuple().exponent
    if isinstance(exponent, int) and exponent < -3:
        return format(number, "f"), True
    return f"{number.quantize(QTY):.3f}", False


def rows_of(snapshot: dict, name: str):
    tables = snapshot["tables"]
    if name not in tables:
        return None
    rows = tables[name]
    if not isinstance(rows, list):
        raise SystemExit(f"表 {name} 须为数组")
    return rows


def table_state(snapshot: dict, name: str) -> dict:
    rows = rows_of(snapshot, name)
    if rows is None:
        return {"state": "absent", "rows": None, "note": "待核"}
    note = "本快照该表行数为 0，不能据此判断业务库没有这类记录" if len(rows) == 0 else ""
    return {"state": "present", "rows": len(rows), "note": note}


def canonical(report: dict) -> str:
    return json.dumps(report, ensure_ascii=False, sort_keys=True, indent=2) + "\n"


def add_finding(findings: list, code: str, mother: str, subject: str, detail: str) -> None:
    findings.append({
        "code": code,
        "mother": mother,
        "subject": subject,
        "detail": detail,
    })


def batch_qr_and_preview(row: dict):
    qr = text(row.get("qr"))
    preview = row.get("preview_image_present")
    raw = row.get("content_snapshot")
    parsed = None
    if isinstance(raw, str) and raw.strip():
        try:
            parsed = json.loads(raw)
        except json.JSONDecodeError:
            parsed = None
    elif isinstance(raw, dict):
        parsed = raw
    if isinstance(parsed, dict):
        if qr == "":
            qr = text(parsed.get("qr"))
        if preview is None:
            preview = "previewImage" in parsed or "preview_image" in parsed
    return qr, preview


def count_downstream(snapshot: dict, table: str, mother: str, children: set[str]) -> dict:
    state = table_state(snapshot, table)
    if state["state"] != "present":
        return {"state": "absent", "count": None}
    count = 0
    for row in rows_of(snapshot, table) or []:
        if not isinstance(row, dict):
            continue
        if table == "biz_process_batch_handoff":
            code = text(row.get("order_code"))
            if code == mother or code in children:
                count += 1
        else:
            if text(row.get("mother_order_code")) == mother:
                count += 1
    return {"state": "present", "count": count}


def build_report(snapshot: dict, digest: str) -> dict:
    meta = snapshot.get("meta") or {}
    findings: list[dict] = []
    mothers_in = rows_of(snapshot, "biz_mother_order")
    if mothers_in is None:
        add_finding(findings, "mother_table_absent", "", "", "快照没有 biz_mother_order，不能生成清单")
        return {
            "tool": TOOL,
            "snapshot_kind": text(meta.get("kind")) or "unknown",
            "snapshot_sha256": digest,
            "online_inventory": "待核",
            "note": "本报告只覆盖所给快照，不是服务器盘点。",
            "tables": {},
            "extra_tables": sorted(set(snapshot["tables"]) - {"biz_mother_order"}),
            "mothers": [],
            "findings": findings,
            "p0": {"code": P0_CODE, "in_snapshot": False, "note": "本快照未包含，线上仍待核"},
        }

    children_by_mother: dict[str, list[dict]] = {}
    child_codes: set[str] = set()
    work_orders = rows_of(snapshot, "biz_work_order")
    if work_orders is not None:
        for row in work_orders:
            if not isinstance(row, dict) or text(row.get("code")) == "":
                add_finding(findings, "snapshot_row_unreadable", "", "biz_work_order", "子单缺少编号")
                continue
            code = text(row.get("code"))
            child_codes.add(code)
            children_by_mother.setdefault(text(row.get("mother_order_code")), []).append(row)

    receipts_by_mother: dict[str, list[dict]] = {}
    receipt_keys: set[tuple[str, str]] = set()
    receipt_rows = rows_of(snapshot, "biz_receipt_line")
    if receipt_rows is not None:
        for row in receipt_rows:
            if not isinstance(row, dict):
                continue
            mother = text(row.get("mother_order_code"))
            line_no = text(row.get("line_no"))
            receipts_by_mother.setdefault(mother, []).append(row)
            if mother and line_no:
                receipt_keys.add((mother, line_no))

    allocations_by_child: dict[str, list[dict]] = {}
    allocation_rows = rows_of(snapshot, "biz_child_line_allocation")
    if allocation_rows is not None:
        for row in allocation_rows:
            if isinstance(row, dict):
                allocations_by_child.setdefault(text(row.get("child_order_code")), []).append(row)

    ledgers = {}
    ledger_rows = rows_of(snapshot, "inv_stock_ledger")
    if ledger_rows is not None:
        for row in ledger_rows:
            if isinstance(row, dict) and text(row.get("code")):
                ledgers[text(row.get("code"))] = row

    mother_codes: set[str] = set()
    mothers_out = []
    for row in mothers_in:
        if not isinstance(row, dict) or text(row.get("code")) == "":
            add_finding(findings, "snapshot_row_unreadable", "", "biz_mother_order", "母单缺少编号")
            continue
        code = text(row.get("code"))
        mother_codes.add(code)
        quantity, quantity_long = amount(row.get("quantity"))
        weight, weight_long = amount(row.get("weight"))
        if quantity is None or weight is None:
            add_finding(findings, "mother_amount_unreadable", code, "", "母单数量或重量无法读取")
        if quantity_long or weight_long:
            add_finding(findings, "quantity_scale_unexpected", code, "", "数量或重量小数超过三位，未舍入")
        child_rows = children_by_mother.get(code, [])
        child_code_list = sorted(text(item.get("code")) for item in child_rows)
        child_set = set(child_code_list)
        related = {code, *child_set}

        receipt_lines = receipts_by_mother.get(code, []) if receipt_rows is not None else []
        receipt_quantity = Decimal("0")
        receipt_weight = Decimal("0")
        receipt_ok = True
        for line in receipt_lines:
            line_quantity, line_quantity_long = amount(line.get("quantity"))
            line_weight, line_weight_long = amount(line.get("weight"))
            if line_quantity is None or line_weight is None:
                receipt_ok = False
                add_finding(findings, "receipt_amount_unreadable", code, text(line.get("line_no")), "收货行数量或重量无法读取")
                continue
            if line_quantity_long or line_weight_long:
                add_finding(findings, "quantity_scale_unexpected", code, text(line.get("line_no")), "收货行小数超过三位，未舍入")
            receipt_quantity += Decimal(line_quantity)
            receipt_weight += Decimal(line_weight)
            ledger_code = text(line.get("ledger_code"))
            if ledger_rows is not None:
                if ledger_code == "" or ledger_code not in ledgers:
                    add_finding(findings, "ledger_missing", code, ledger_code, "收货行台账编号在库存流水中不存在")
                else:
                    ledger = ledgers[ledger_code]
                    ledger_quantity, _ = amount(ledger.get("quantity"))
                    ledger_weight, _ = amount(ledger.get("weight"))
                    if ledger_quantity != line_quantity or ledger_weight != line_weight:
                        add_finding(
                            findings, "ledger_amount_mismatch", code, ledger_code,
                            f"收货行 {line_quantity}/{line_weight}，流水 {ledger_quantity}/{ledger_weight}",
                        )

        source = text(row.get("source_register_type"))
        if receipt_rows is None:
            receipt_state = "待核"
            receipt_quantity_text = None
            receipt_weight_text = None
        elif not receipt_lines and source.startswith("INTAKE_LINES"):
            receipt_state = "lines_absent"
            receipt_quantity_text = "0.000"
            receipt_weight_text = "0.000"
            add_finding(findings, "receipt_lines_absent", code, "", "来源标明逐行收货，但快照中没有收货行")
        elif not receipt_lines:
            receipt_state = "not_line_intake"
            receipt_quantity_text = None
            receipt_weight_text = None
        else:
            receipt_quantity_text = f"{receipt_quantity.quantize(QTY):.3f}"
            receipt_weight_text = f"{receipt_weight.quantize(QTY):.3f}"
            if receipt_ok and (receipt_quantity_text != quantity or receipt_weight_text != weight):
                receipt_state = "mismatch"
                add_finding(
                    findings, "receipt_sum_mismatch", code, "",
                    f"母单 {quantity}/{weight}，收货行合计 {receipt_quantity_text}/{receipt_weight_text}",
                )
            else:
                receipt_state = "matched" if receipt_ok else "unreadable"

        if work_orders is not None:
            for child in child_rows:
                child_code = text(child.get("code"))
                child_quantity, _ = amount(child.get("quantity"))
                child_weight, _ = amount(child.get("weight"))
                allocations = allocations_by_child.get(child_code, []) if allocation_rows is not None else None
                if allocation_rows is None:
                    continue
                if not allocations:
                    continue
                allocated_quantity = Decimal("0")
                allocated_weight = Decimal("0")
                allocation_ok = True
                for allocation in allocations:
                    allocation_quantity, _ = amount(allocation.get("quantity"))
                    allocation_weight, _ = amount(allocation.get("weight"))
                    if allocation_quantity is None or allocation_weight is None:
                        allocation_ok = False
                        add_finding(findings, "allocation_amount_unreadable", code, child_code, "分配数量或重量无法读取")
                        continue
                    allocated_quantity += Decimal(allocation_quantity)
                    allocated_weight += Decimal(allocation_weight)
                    source_line = text(allocation.get("source_line_no"))
                    if receipt_rows is not None and (code, source_line) not in receipt_keys:
                        add_finding(findings, "allocation_source_missing", code, child_code, f"分配来源行 {source_line} 不在收货行中")
                if allocation_ok and (f"{allocated_quantity.quantize(QTY):.3f}" != child_quantity or f"{allocated_weight.quantize(QTY):.3f}" != child_weight):
                    add_finding(
                        findings, "allocation_sum_mismatch", code, child_code,
                        f"子单 {child_quantity}/{child_weight}，分配合计 {allocated_quantity.quantize(QTY):.3f}/{allocated_weight.quantize(QTY):.3f}",
                    )

        mother_ledger = text(row.get("ledger_code"))
        if ledger_rows is not None and (mother_ledger == "" or mother_ledger not in ledgers):
            add_finding(findings, "ledger_missing", code, mother_ledger, "母单台账编号在库存流水中不存在")

        def related_count(table: str, field: str) -> int | None:
            state_rows = rows_of(snapshot, table)
            if state_rows is None:
                return None
            count = 0
            for item in state_rows:
                if isinstance(item, dict) and text(item.get(field)) in related:
                    count += 1
            return count

        label_batch_count = related_count("biz_print_batch", "business_code")
        label_job_count = related_count("biz_print_job", "business_code")
        reprint_count = related_count("biz_label_reprint_log", "business_code")
        trace_rows = rows_of(snapshot, "biz_label_trace")
        if trace_rows is None:
            trace_count = None
        else:
            trace_count = 0
            for item in trace_rows:
                if not isinstance(item, dict):
                    continue
                if text(item.get("mother_order_code")) == code or text(item.get("business_code")) in related:
                    trace_count += 1

        attachments = rows_of(snapshot, "doc_evidence_attachment")
        attachment_codes = []
        if attachments is not None:
            for item in attachments:
                if isinstance(item, dict) and text(item.get("business_code")) == code:
                    attachment_codes.append(text(item.get("code")))
        links = rows_of(snapshot, "doc_business_evidence_link")
        link_count = None
        if links is not None:
            link_count = 0
            for item in links:
                if isinstance(item, dict) and text(item.get("target_code")) in related:
                    link_count += 1

        files = snapshot["tables"].get("files")
        if attachments is None:
            photo_file_state = "待核"
        elif "files" not in snapshot["tables"]:
            photo_file_state = "待核"
        else:
            listed = {text(item) for item in files} if isinstance(files, list) else set()
            missing_file = False
            for item in attachments:
                if not isinstance(item, dict) or text(item.get("business_code")) != code:
                    continue
                location = text(item.get("storage_location"))
                if location == "" or location not in listed:
                    missing_file = True
                    add_finding(findings, "photo_file_missing", code, text(item.get("code")), "快照文件清单中没有该附件存放位置")
            photo_file_state = "缺失" if missing_file else "齐全"

        mothers_out.append({
            "code": code,
            "customer_id": text(row.get("customer_id")),
            "quantity": quantity,
            "quantity_unit": text(row.get("quantity_unit")),
            "weight": weight,
            "ledger_code": mother_ledger,
            "source_register_type": source,
            "evidence_code": text(row.get("evidence_code")),
            "children": child_code_list,
            "receipt_state": receipt_state,
            "receipt_line_count": None if receipt_rows is None else len(receipt_lines),
            "receipt_quantity": receipt_quantity_text,
            "receipt_weight": receipt_weight_text,
            "label_batch_count": label_batch_count,
            "label_job_count": label_job_count,
            "reprint_count": reprint_count,
            "trace_count": trace_count,
            "photo_attachment_count": None if attachments is None else len(attachment_codes),
            "photo_link_count": link_count,
            "photo_file_state": photo_file_state,
            "downstream": {
                "biz_process_batch_handoff": count_downstream(snapshot, "biz_process_batch_handoff", code, child_set),
                "biz_quality_requirement_check": count_downstream(snapshot, "biz_quality_requirement_check", code, child_set),
                "biz_outbound_order": count_downstream(snapshot, "biz_outbound_order", code, child_set),
                "fin_order_settlement": count_downstream(snapshot, "fin_order_settlement", code, child_set),
            },
        })

    known_codes = mother_codes | child_codes
    batches = rows_of(snapshot, "biz_print_batch")
    if batches is not None:
        for row in batches:
            if not isinstance(row, dict):
                continue
            business = text(row.get("business_code"))
            batch_id = text(row.get("id"))
            if business not in known_codes:
                add_finding(findings, "label_target_missing", "", business, f"打印批次 {batch_id} 对不上母单或子单")
            qr, preview = batch_qr_and_preview(row)
            if "qr" not in row and "content_snapshot" not in row:
                pass
            elif qr == "":
                add_finding(findings, "label_qr_absent", "", business, f"打印批次 {batch_id} 没有二维码内容")
            else:
                parts = qr.split("|")
                if len(parts) < 5 or parts[0] != "JG":
                    add_finding(findings, "label_qr_unparsed", "", business, f"打印批次 {batch_id} 的二维码不是现行 JG 格式")
                elif parts[2] != business:
                    add_finding(findings, "label_qr_mismatch", "", business, f"二维码编码 {parts[2]} 与业务号不一致")
            if "preview_image_present" in row or "content_snapshot" in row:
                if preview is False:
                    add_finding(findings, "label_preview_absent", "", business, f"打印批次 {batch_id} 快照标明没有冻结预览图")

    links = rows_of(snapshot, "doc_business_evidence_link")
    attachments = rows_of(snapshot, "doc_evidence_attachment")
    if links is not None and attachments is not None:
        known_attachments = {text(item.get("code")) for item in attachments if isinstance(item, dict)}
        for row in links:
            if not isinstance(row, dict):
                continue
            attachment_code = text(row.get("attachment_code"))
            if attachment_code not in known_attachments:
                add_finding(
                    findings, "photo_link_attachment_missing", text(row.get("target_code")), attachment_code,
                    "照片引用指向的附件编号不在附件表中",
                )

    known_names = {"biz_mother_order", "files", *OPTIONAL_TABLES}
    tables = {"biz_mother_order": table_state(snapshot, "biz_mother_order")}
    for name in OPTIONAL_TABLES:
        tables[name] = table_state(snapshot, name)
    if "files" in snapshot["tables"]:
        file_rows = snapshot["tables"]["files"]
        tables["files"] = {
            "state": "present" if isinstance(file_rows, list) else "unreadable",
            "rows": len(file_rows) if isinstance(file_rows, list) else None,
            "note": "",
        }
    else:
        tables["files"] = {"state": "absent", "rows": None, "note": "待核"}

    mothers_out.sort(key=lambda item: item["code"])
    findings.sort(key=lambda item: (item["code"], item["mother"], item["subject"], item["detail"]))
    p0_present = P0_CODE in mother_codes
    return {
        "tool": TOOL,
        "snapshot_kind": text(meta.get("kind")) or "unknown",
        "snapshot_sha256": digest,
        "online_inventory": "待核",
        "note": "本报告只覆盖所给快照，不是服务器盘点。缺表为待核，快照内零行也不表示业务库为空。",
        "tables": tables,
        "extra_tables": sorted(set(snapshot["tables"]) - known_names),
        "mothers": mothers_out,
        "findings": findings,
        "p0": {
            "code": P0_CODE,
            "in_snapshot": p0_present,
            "note": "快照含该母单，数量与下游以本报告该行事实为准" if p0_present else "本快照未包含，线上仍待核",
        },
    }


def finding_key(item: dict) -> str:
    return " ".join(part for part in (item["code"], item["mother"], item["subject"]) if part)


def diff_text(left: dict, right: dict) -> str:
    lines = [f"left {left['snapshot_sha256']}", f"right {right['snapshot_sha256']}"]
    left_findings = {finding_key(item) for item in left["findings"]}
    right_findings = {finding_key(item) for item in right["findings"]}
    for key in sorted(right_findings - left_findings):
        lines.append(f"+ {key}")
    for key in sorted(left_findings - right_findings):
        lines.append(f"- {key}")
    left_mothers = {item["code"]: item for item in left["mothers"]}
    right_mothers = {item["code"]: item for item in right["mothers"]}
    for code in sorted(set(left_mothers) | set(right_mothers)):
        if code not in left_mothers:
            lines.append(f"+ mother {code}")
            continue
        if code not in right_mothers:
            lines.append(f"- mother {code}")
            continue
        for field in MOTHER_FIELDS:
            before = left_mothers[code].get(field)
            after = right_mothers[code].get(field)
            if before != after:
                lines.append(f"~ {code} {field} {before} {after}")
    return "\n".join(lines) + "\n"


def load_report(path: Path) -> tuple[dict, str]:
    raw = read_only_bytes(path)
    snapshot = parse_snapshot(raw, str(path))
    digest = sha256_text(raw)
    report = build_report(snapshot, digest)
    again = read_only_bytes(path)
    if again != raw:
        raise SystemExit(f"读取期间快照被改写：{path}")
    return report, canonical(report)


def command_report(path: Path, out: Path | None) -> int:
    report, text_out = load_report(path)
    if out is not None:
        if out.resolve() == path.resolve():
            raise SystemExit("输出路径不能是快照本身")
        out.write_text(text_out, encoding="utf-8")
    else:
        sys.stdout.write(text_out)
    if any(item["code"] == "mother_table_absent" for item in report["findings"]):
        return 2
    return 0


def command_diff(left: Path, right: Path) -> int:
    left_report, _ = load_report(left)
    right_report, _ = load_report(right)
    sys.stdout.write(diff_text(left_report, right_report))
    return 0


def expect(condition: bool, message: str, errors: list[str]) -> None:
    if not condition:
        errors.append(message)


def command_check(sample_dir: Path) -> int:
    base_path = sample_dir / "snapshot.json"
    delta_path = sample_dir / "snapshot-delta.json"
    before = {path: read_only_bytes(path) for path in (base_path, delta_path)}
    errors: list[str] = []

    base_snapshot = parse_snapshot(before[base_path], base_path.name)
    delta_snapshot = parse_snapshot(before[delta_path], delta_path.name)
    intended = copy.deepcopy(base_snapshot)
    changed = False
    for row in intended["tables"]["biz_mother_order"]:
        if text(row.get("code")) == "MD-SAMPLE-0001":
            row["quantity"] = "11.000"
            changed = True
    expect(changed, "样本母单 MD-SAMPLE-0001 不存在", errors)
    expect(delta_snapshot == intended, "差异样本不只有 MD-SAMPLE-0001 的数量从 10.000 改为 11.000", errors)

    first_report, first_text = load_report(base_path)
    _, second_text = load_report(base_path)
    expect(first_text == second_text, "同一快照两次报告不一致", errors)
    expect(first_report["online_inventory"] == "待核", "报告把线上盘点写成了已完成", errors)
    expect(first_report["tables"]["fin_order_settlement"]["state"] == "absent", "缺席的结单表被当成了零行", errors)
    expect(first_report["tables"]["biz_outbound_order"]["rows"] == 0, "样本出库表应是快照内零行", errors)
    expect(first_report["p0"]["in_snapshot"] is False, "合成样本不应包含 P0 母单", errors)
    mother_a = next(item for item in first_report["mothers"] if item["code"] == "MD-SAMPLE-0001")
    expect(mother_a["photo_file_state"] == "待核", "没有文件清单时不应宣称照片原件已核对", errors)
    finding_keys = {finding_key(item) for item in first_report["findings"]}
    for required in (
        "receipt_sum_mismatch MD-SAMPLE-0002",
        "allocation_sum_mismatch MD-SAMPLE-0002 ZD-SAMPLE-0002",
        "label_target_missing ZD-MISSING",
        "label_preview_absent ZD-MISSING",
        "photo_link_attachment_missing MD-SAMPLE-0002 ATT-MISSING",
    ):
        expect(required in finding_keys, f"样本报告缺少差异 {required}", errors)

    delta_report, _ = load_report(delta_path)
    difference = diff_text(first_report, delta_report)
    expect("+ receipt_sum_mismatch MD-SAMPLE-0001" in difference, "差异报告没有列出新增的收货合计差异", errors)
    expect("~ MD-SAMPLE-0001 quantity 10.000 11.000" in difference, "差异报告没有列母单数量变化", errors)
    expect("- receipt_sum_mismatch MD-SAMPLE-0002" not in difference, "差异报告误删了原有差异", errors)

    mutated = copy.deepcopy(base_snapshot)
    mutated["tables"]["inv_stock_ledger"] = [
        row for row in mutated["tables"]["inv_stock_ledger"] if text(row.get("code")) != "L-SAMPLE-A"
    ]
    mutated_report = build_report(mutated, "memory")
    mutated_keys = {finding_key(item) for item in mutated_report["findings"]}
    expect("ledger_missing MD-SAMPLE-0001 L-SAMPLE-A" in mutated_keys, "去掉台账后没有报出台账缺失", errors)
    mutated["tables"]["biz_child_line_allocation"].append({
        "child_order_code": "ZD-SAMPLE-0001",
        "mother_order_code": "MD-SAMPLE-0001",
        "source_line_no": "9",
        "quantity": "0.000",
        "weight": "0.000",
    })
    source_report = build_report(mutated, "memory")
    source_keys = {finding_key(item) for item in source_report["findings"]}
    expect("allocation_source_missing MD-SAMPLE-0001 ZD-SAMPLE-0001" in source_keys, "不存在的收货行没有报出来", errors)

    after = {path: read_only_bytes(path) for path in before}
    expect(after == before, "核对过程改写了样本文件", errors)
    if errors:
        for message in errors:
            print(message, file=sys.stderr)
        return 1
    print(f"样本 {base_path.name} sha256 {first_report['snapshot_sha256']}")
    print("同一快照两次报告一致")
    print("差异报告列出 MD-SAMPLE-0001 的数量变化和新增收货合计差异")
    print("样本文件字节未变")
    print("线上盘点：待核")
    return 0


def main(argv: list[str]) -> int:
    configure_stdio()
    parser = argparse.ArgumentParser(description="只读核对母单、标签、照片和收货来源的本地快照")
    sub = parser.add_subparsers(dest="command", required=True)
    report = sub.add_parser("report")
    report.add_argument("--snapshot", required=True, type=Path)
    report.add_argument("--out", type=Path)
    diff = sub.add_parser("diff")
    diff.add_argument("--left", required=True, type=Path)
    diff.add_argument("--right", required=True, type=Path)
    check = sub.add_parser("check")
    check.add_argument("--sample", type=Path, default=Path(__file__).resolve().parent / "gr-a02-sample")
    args = parser.parse_args(argv)
    if args.command == "report":
        return command_report(args.snapshot, args.out)
    if args.command == "diff":
        return command_diff(args.left, args.right)
    return command_check(args.sample)


if __name__ == "__main__":
    raise SystemExit(main(sys.argv[1:]))
