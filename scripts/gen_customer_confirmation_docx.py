# -*- coding: utf-8 -*-
"""生成《客户确认清单汇总表》Word 文件。一次性脚本，运行后可删除。"""
from docx import Document
from docx.shared import Pt, RGBColor
from docx.enum.text import WD_ALIGN_PARAGRAPH

OUT = "output/客户确认清单汇总表.docx"

doc = Document()

# 设置默认中文字体
style = doc.styles["Normal"]
style.font.name = "微软雅黑"
style.font.size = Pt(10.5)
try:
    from docx.oxml.ns import qn
    style.element.rPr.rFonts.set(qn("w:eastAsia"), "微软雅黑")
except Exception:
    pass


def set_cell(cell, text, bold=False):
    cell.text = ""
    p = cell.paragraphs[0]
    run = p.add_run(text)
    run.bold = bold
    run.font.name = "微软雅黑"
    try:
        from docx.oxml.ns import qn
        run._element.rPr.rFonts.set(qn("w:eastAsia"), "微软雅黑")
    except Exception:
        pass
    run.font.size = Pt(9)


def add_table(headers, rows):
    table = doc.add_table(rows=1, cols=len(headers))
    table.style = "Table Grid"
    for i, h in enumerate(headers):
        set_cell(table.rows[0].cells[i], h, bold=True)
    for row in rows:
        cells = table.add_row().cells
        for i, val in enumerate(row):
            set_cell(cells[i], val)
    doc.add_paragraph("")


def heading(text, level=1):
    h = doc.add_heading(text, level=level)
    for run in h.runs:
        run.font.name = "微软雅黑"
        try:
            from docx.oxml.ns import qn
            run._element.rPr.rFonts.set(qn("w:eastAsia"), "微软雅黑")
        except Exception:
            pass


# 标题
title = doc.add_heading("客户确认清单汇总表", level=0)
for run in title.runs:
    run.font.name = "微软雅黑"
    try:
        from docx.oxml.ns import qn
        run._element.rPr.rFonts.set(qn("w:eastAsia"), "微软雅黑")
    except Exception:
        pass

doc.add_paragraph("日期：____________    填写人：____________    联系方式：____________")
note = doc.add_paragraph(
    "说明：把需要客户确认的所有事项汇总成一张表，带 ⭐ 为必须先定的。系统的标准单据由我方设计，"
    "客户只需提供“内容”并确认字段，无需自己整理规范单据。可直接在“客户确认结论”列填写，"
    "或口头/语音告诉我们由我们代填后请您核对。"
)
for run in note.runs:
    run.font.size = Pt(9)

heading("A. 算账与业务规则（⭐ 最重要，决定能否开始试点）", 1)
add_table(
    ["序号", "问题", "我们的建议默认", "客户确认结论"],
    [
        ["A1 ⭐", "损耗按重量、件数还是金额算？", "贵重料按重量、普通件按数量，金额只做参考", ""],
        ["A2 ⭐", "每道工序标准损耗率是多少（如抛光允许损耗 %）？", "由客户给各工序数值", ""],
        ["A3 ⭐", "什么算合理损耗、什么算超标？", "返修/留样/客户确认报废单独记，不算超标", ""],
        ["A4 ⭐", "损耗责任算个人、班组还是只记录？", "一期只记录只试算，不真扣工资", ""],
        ["A5 ⭐", "质检结果分哪几种？", "合格/返工/报废/让步通过/待复检", ""],
        ["A6 ⭐", "返工有无次数上限？超了要审批吗？", "记录次数，超限提示审批，不自动禁止", ""],
        ["A7 ⭐", "客户退货后怎么处理？", "复核后分流：入库/返工/报废/冻结", ""],
        ["A8 ⭐", "出货的交货类目有哪几种？", "成品/返修件/补发件/样品/退补件", ""],
    ],
)

heading("B. 客户需提供的基础数据（⭐ 内容客户给，格式我们做）", 1)
add_table(
    ["序号", "要什么", "最少几条", "每条包含", "客户提供情况"],
    [
        ["B1 ⭐", "常打交道的客户名单", "5 个", "简称、联系人、默认发货方式", ""],
        ["B2 ⭐", "常用物料清单", "20 个", "名称、规格、按克/颗/件、是否贵重", ""],
        ["B3 ⭐", "加工工序清单", "10 个", "工序名、是否用机器、是否计件、是否质检、是否称重", ""],
        ["B4 ⭐", "典型产品加工流程", "3 套", "每类产品从头到尾的工序顺序", ""],
        ["B5", "员工/班组名单", "10 个", "姓名（可编号）、班组、能做哪些工序", ""],
        ["B6", "仓库/存放区划分", "6 个", "名称、用途（来料/在制/成品/返工/报废）", ""],
    ],
)

heading("C. 由我方设计、客户确认字段的标准单据（客户只需确认，不用提供）", 1)
add_table(
    ["序号", "我方输出的标准单据草案", "请客户确认", "客户确认结论"],
    [
        ["C1 ⭐", "来料/母单登记（对应入库登记表）", "字段够不够、有没有漏", ""],
        ["C2 ⭐", "生产报工/抛光日报（对应抛光登记表）", "报工记哪些、谁签字", ""],
        ["C3 ⭐", "雕刻机产量统计（对应雕刻登记表）", "按机器/按天统计够不够", ""],
        ["C4 ⭐", "出货登记（对应出货登记表）", "快递、类目、经办人字段", ""],
        ["C5", "质检/返工记录", "质检结果、缺陷类型字段", ""],
        ["C6", "退货登记", "退货原因、处理方式字段", ""],
    ],
)

heading("D. 部署与硬件（已确认，记录备查）", 1)
add_table(
    ["序号", "事项", "已确认结论"],
    [
        ["D1", "部署形态", "√ 一期装厂内电脑，只在局域网用"],
        ["D2", "外网/域名", "√ 不需要，局域网够用"],
        ["D3", "扫码方式", "√ 扫码枪客户自购，前期先兼容手机扫码"],
        ["D4", "告警方式", "√ 发到系统内（运维中心查看）"],
        ["D5", "备份存放", "√ 存腾讯云 COS（每天自动备份）"],
        ["D6", "COS 参数", "待客户提供：Bucket、Region、访问密钥"],
        ["D7", "其它设备型号", "标签机/摄像头/电子秤，到货后确认"],
    ],
)

heading("E. 一期范围确认（先说清楚，避免误会）", 1)
doc.add_paragraph("以下一期先不做，二期再加，请客户确认认可：")
for line in [
    "· 工资一期只试算参考，不真实发放/扣款",
    "· 电子秤先人工录入+拍照，自动读数二期再接",
    "· 财务对账、客户小程序、iOS App、库位精细管理，均排后续",
]:
    doc.add_paragraph(line)
doc.add_paragraph("客户确认：□ 认可一期范围    □ 有异议（说明）：__________________________")

heading("签字确认", 1)
add_table(
    ["角色", "姓名", "日期"],
    [
        ["业务负责人", "", ""],
        ["车间/生产负责人", "", ""],
        ["仓库负责人", "", ""],
    ],
)

import os
import time


def safe_save(document, path):
    """保存文档；若目标文件被占用（在编辑器/Word 打开），退到带时间戳的文件名。"""
    os.makedirs(os.path.dirname(path), exist_ok=True)
    try:
        document.save(path)
        print("已生成:", path)
    except PermissionError:
        stamp = time.strftime("%Y%m%d%H%M%S")
        base, ext = os.path.splitext(path)
        alt = f"{base}-{stamp}{ext}"
        document.save(alt)
        print("原文件被占用，已改存:", alt)


safe_save(doc, OUT)


# ============================================================
# 第二个文件：标准单据样表（供客户确认字段）
# ============================================================
OUT2 = "output/标准单据样表.docx"
doc2 = Document()
s2 = doc2.styles["Normal"]
s2.font.name = "微软雅黑"
s2.font.size = Pt(10.5)
try:
    from docx.oxml.ns import qn
    s2.element.rPr.rFonts.set(qn("w:eastAsia"), "微软雅黑")
except Exception:
    pass


def _cn(run):
    run.font.name = "微软雅黑"
    try:
        from docx.oxml.ns import qn
        run._element.rPr.rFonts.set(qn("w:eastAsia"), "微软雅黑")
    except Exception:
        pass


def set_cell2(cell, text, bold=False):
    cell.text = ""
    p = cell.paragraphs[0]
    run = p.add_run(text)
    run.bold = bold
    _cn(run)
    run.font.size = Pt(9)


def form_table(rows):
    table = doc2.add_table(rows=1, cols=4)
    table.style = "Table Grid"
    for i, h in enumerate(["字段", "是否必填", "示例", "说明"]):
        set_cell2(table.rows[0].cells[i], h, bold=True)
    for row in rows:
        cells = table.add_row().cells
        for i, val in enumerate(row):
            set_cell2(cells[i], val)
    doc2.add_paragraph("")


def heading2(text, level=1):
    h = doc2.add_heading(text, level=level)
    for run in h.runs:
        _cn(run)


def form_block(title, purpose, rows):
    heading2(title, 1)
    p = doc2.add_paragraph(purpose)
    for run in p.runs:
        run.font.size = Pt(9)
    form_table(rows)
    doc2.add_paragraph("客户意见：________________________________________")
    doc2.add_paragraph("")


t2 = doc2.add_heading("标准单据样表（供客户确认字段）", level=0)
for run in t2.runs:
    _cn(run)
intro = doc2.add_paragraph(
    "用途：把系统现在能输出的每张标准单据，按真实字段做成样表，供客户逐字段确认“够不够、有没有多余、示例对不对”。"
    "字段来自系统实际实现（基于客户现有的入库/出货/抛光/雕刻 4 张 Excel 提炼）。示例值仅供理解，非真实数据。"
)
for run in intro.runs:
    run.font.size = Pt(9)

form_block(
    "单据一：来料/母单登记（对应入库登记表）",
    "用途：客户来料签收、拆包、入库时填。系统按此生成母单，是全链路追溯的起点。",
    [
        ["入库日期", "必填", "2026-07-08", "纸质只写月日，系统自动补年份"],
        ["快递公司", "必填", "顺丰", ""],
        ["快递单号", "必填", "SF-IN-20260708-001", "全程追溯入口，建议唯一"],
        ["寄货人", "选填", "恒信珠宝", "姓名/客户简称"],
        ["收货人", "选填", "何琴", ""],
        ["拆箱签字", "必填", "何琴", "一期存姓名，二期接电子签名"],
        ["货品名称", "必填", "18K 金戒托", "需对应物料档案"],
        ["数量+单位", "必填", "320 件", "单位可为件/颗/克/条"],
        ["重量", "系统补录", "35.20 克", "原表没有，拆包称重时录入"],
        ["备注", "选填", "含 2 件破损", "异常说明"],
    ],
)
form_block(
    "单据二：生产报工/抛光日报（对应抛光登记表）",
    "用途：工人每天填做了哪些活、完成情况、谁审核。系统按此做工序报工和工资试算。",
    [
        ["姓名", "必填", "王强", "做工的人（与系统登录人分开）"],
        ["日期", "必填", "2026-07-08", ""],
        ["工作项目", "必填", "精抛", "粗抛/精抛/返工抛光等"],
        ["开始时间", "选填", "09:00", ""],
        ["完成时间", "选填", "11:30", "进行中可空"],
        ["完成情况", "必填", "完成 320 件", ""],
        ["交单审核", "必填", "已审核（组长李华）", "没审核不能进下一工序"],
        ["备注", "选填", "有 3 件返工", ""],
    ],
)
form_block(
    "单据三：雕刻机产量统计（对应雕刻登记表）",
    "用途：按天、按 1-8 号机器记录雕刻产量。系统按此做设备产量统计和工资试算。",
    [
        ["日期", "必填", "2026-07-08", "组内同一天可继承"],
        ["客户", "必填", "恒信珠宝", "需对应客户档案"],
        ["品类", "必填", "戒托", "产能统计必填"],
        ["机器编号", "必填", "3 号机（ENG-03）", "1-8 号机逐台登记"],
        ["雕刻数量+单位", "必填", "12 件", ""],
        ["操机签字", "必填", "周凯", "操作机器的人，不能用录入人代替"],
        ["备注", "选填", "3 号机异响", "设备异常说明"],
    ],
)
form_block(
    "单据四：出货登记（对应出货登记表）",
    "用途：成品打包发货时填。系统按此扣减成品库存、生成出货单。",
    [
        ["日期", "必填", "2026-07-08", ""],
        ["客户", "必填", "雅辰饰品", "需对应客户档案"],
        ["交货类目", "必填", "成品", "成品/返修件/补发件/样品/退补件"],
        ["交货数量+单位", "必填", "620 件", "不能超过成品库存"],
        ["快递（原文）", "必填", "顺丰 SF-OUT-20260708-022", "系统保留原文并拆成公司+单号"],
        ["经办人", "必填", "何琳", "与系统登录人分开"],
        ["备注", "选填", "客户要求加急", ""],
    ],
)
form_block(
    "单据五：质检/返工记录（系统新增，原 Excel 无）",
    "用途：质检判定合格与否、记录缺陷和返工。原来靠口头/脑子，系统标准化。",
    [
        ["子工单", "必填", "ZD-20260708-001", "检的是哪个活"],
        ["质检类型", "必填", "成品质检", "工序质检/成品质检/返工复检"],
        ["质检结果", "必填", "返工", "合格/返工/报废/让步通过/待复检"],
        ["缺陷类型", "选填", "表面划痕", ""],
        ["责任归因", "选填", "员工", "员工/工序/材料/客户/待确认"],
        ["质检员", "必填", "陈曦", ""],
        ["附件", "选填", "缺陷照片", ""],
    ],
)
form_block(
    "单据六：退货登记（系统新增，原 Excel 无）",
    "用途：客户退货回来时填，记录原因和处理方式。",
    [
        ["原出货单/快递单号", "必填", "SF-OUT-20260708-022", "关联原来的出货"],
        ["退货日期", "必填", "2026-07-15", ""],
        ["退货数量+单位", "必填", "20 件", ""],
        ["退货原因", "必填", "尺寸不符", ""],
        ["处理方式", "必填", "返工", "重新入库/返工/报废/冻结"],
        ["责任归因", "选填", "工序", ""],
        ["附件", "选填", "退货照片", ""],
    ],
)

heading2("汇总确认", 1)
doc2.add_paragraph("以上 6 张标准单据，客户整体意见：")
doc2.add_paragraph("□ 字段基本够用，可以按此做      □ 需调整（见各表客户意见）")
doc2.add_paragraph("签字：____________    日期：____________")

safe_save(doc2, OUT2)
