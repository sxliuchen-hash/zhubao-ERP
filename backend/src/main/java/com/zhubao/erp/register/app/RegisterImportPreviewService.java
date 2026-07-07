package com.zhubao.erp.register.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.register.domain.RegisterImportPreview;
import com.zhubao.erp.register.domain.RegisterImportPreviewMessage;
import com.zhubao.erp.register.domain.RegisterImportPreviewRow;
import com.zhubao.erp.register.domain.RegisterImportTemplate;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class RegisterImportPreviewService {

    private static final DateTimeFormatter BATCH_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyyMMddHHmmss");
    private static final Pattern MONTH_DAY_PATTERN = Pattern.compile("(\\d{1,2})\\D+(\\d{1,2})");
    private static final Pattern QUANTITY_PATTERN = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)(.*)");

    private final RegisterImportTemplateService templateService;
    private final DataFormatter formatter = new DataFormatter(Locale.CHINA);

    public RegisterImportPreviewService(RegisterImportTemplateService templateService) {
        this.templateService = templateService;
    }

    public RegisterImportPreview preview(String templateCode, String businessDate, String sourceFileName, byte[] content) {
        RegisterImportTemplate template = findTemplate(templateCode);
        try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(content))) {
            Sheet sheet = workbook.getSheetAt(0);
            List<RegisterImportPreviewRow> rows = switch (templateCode) {
                case "REG-INBOUND" -> parseInbound(sheet, businessDate);
                case "REG-OUTBOUND" -> parseOutbound(sheet, businessDate);
                case "REG-POLISHING" -> parsePolishing(sheet, businessDate);
                case "REG-ENGRAVING" -> parseEngraving(sheet, businessDate);
                default -> throw new BusinessException("不支持的登记表模板：" + templateCode);
            };
            return buildPreview(template, sourceFileName, rows);
        } catch (IOException ex) {
            throw new BusinessException("读取登记表失败：" + ex.getMessage());
        }
    }

    private RegisterImportTemplate findTemplate(String templateCode) {
        Optional<RegisterImportTemplate> template = templateService.findTemplates().stream()
                .filter(item -> item.code().equals(templateCode))
                .findFirst();
        return template.orElseThrow(() -> new BusinessException("未知登记表模板：" + templateCode));
    }

    private RegisterImportPreview buildPreview(
            RegisterImportTemplate template,
            String sourceFileName,
            List<RegisterImportPreviewRow> rows
    ) {
        int blockedRows = countByStatus(rows, "BLOCKED");
        int warningRows = countByStatus(rows, "WARNING");
        int readyRows = countByStatus(rows, "READY");
        String batchNo = "IMP-" + template.code() + "-" + BATCH_TIME_FORMATTER.format(LocalDateTime.now());
        return new RegisterImportPreview(
                batchNo,
                template.code(),
                template.name(),
                sourceFileName,
                rows.size(),
                readyRows,
                warningRows,
                blockedRows,
                !rows.isEmpty() && blockedRows == 0,
                blockedRows == 0 ? "可进入人工复核或确认导入" : "先处理阻断行，再确认导入",
                rows
        );
    }

    private int countByStatus(List<RegisterImportPreviewRow> rows, String status) {
        return (int) rows.stream().filter(row -> row.status().equals(status)).count();
    }

    private List<RegisterImportPreviewRow> parseInbound(Sheet sheet, String businessDate) {
        List<RegisterImportPreviewRow> rows = new ArrayList<>();
        for (int rowIndex = 3; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            if (isBlankRow(sheet, rowIndex, 1, 9)) {
                continue;
            }
            Map<String, String> fields = baseFields("签收入库登记表", rowIndex, cellText(sheet, rowIndex, 0));
            fields.put("inboundDate", normalizeDate(cellText(sheet, rowIndex, 1), businessDate));
            fields.put("expressCompany", cellText(sheet, rowIndex, 2));
            fields.put("courierNo", cellText(sheet, rowIndex, 3));
            fields.put("senderName", cellText(sheet, rowIndex, 4));
            fields.put("receiverName", cellText(sheet, rowIndex, 5));
            fields.put("unpackSigner", cellText(sheet, rowIndex, 6));
            fields.put("goodsName", cellText(sheet, rowIndex, 7));
            addQuantity(fields, cellText(sheet, rowIndex, 8));
            fields.put("remark", cellText(sheet, rowIndex, 9));

            List<RegisterImportPreviewMessage> messages = new ArrayList<>();
            require(messages, fields, "inboundDate", "IN-DATE", "入库日期不能为空", "补录业务日期");
            require(messages, fields, "expressCompany", "IN-EXPRESS", "快递名称不能为空", "补录快递名称");
            require(messages, fields, "courierNo", "IN-COURIER", "快递单号不能为空", "转人工复核");
            require(messages, fields, "unpackSigner", "IN-SIGN", "拆箱签字不能为空", "补签或上传签名附件");
            require(messages, fields, "goodsName", "IN-GOODS", "货品名称不能为空", "补录现场货品名称");
            require(messages, fields, "quantity", "IN-QTY", "数量必须大于 0", "补录数量");
            rows.add(row(rowIndex, fields, messages));
        }
        return rows;
    }

    private List<RegisterImportPreviewRow> parseOutbound(Sheet sheet, String businessDate) {
        List<RegisterImportPreviewRow> rows = new ArrayList<>();
        for (int rowIndex = 2; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            if (isBlankRow(sheet, rowIndex, 1, 7)) {
                continue;
            }
            String paperExpressText = cellText(sheet, rowIndex, 5);
            Map<String, String> fields = baseFields("交货情况登记表", rowIndex, cellText(sheet, rowIndex, 0));
            fields.put("deliveryDate", normalizeDate(cellText(sheet, rowIndex, 1), businessDate));
            fields.put("customerNameSnapshot", cellText(sheet, rowIndex, 2));
            fields.put("deliveryCategory", cellText(sheet, rowIndex, 3));
            addQuantity(fields, cellText(sheet, rowIndex, 4));
            fields.put("paperExpressText", paperExpressText);
            fields.put("expressCompany", splitExpressCompany(paperExpressText));
            fields.put("logisticsNo", splitLogisticsNo(paperExpressText));
            fields.put("handler", cellText(sheet, rowIndex, 6));
            fields.put("remark", cellText(sheet, rowIndex, 7));

            List<RegisterImportPreviewMessage> messages = new ArrayList<>();
            require(messages, fields, "deliveryDate", "OUT-DATE", "交货日期不能为空", "补录业务日期");
            require(messages, fields, "customerNameSnapshot", "OUT-CUSTOMER", "客户不能为空", "转待确认客户");
            require(messages, fields, "deliveryCategory", "OUT-CATEGORY", "交货类目不能为空", "补录交货类目");
            require(messages, fields, "quantity", "OUT-QTY", "交货数量必须大于 0", "补录数量");
            require(messages, fields, "paperExpressText", "OUT-EXPRESS-RAW", "纸质快递栏原文不能为空", "补录原文");
            require(messages, fields, "logisticsNo", "OUT-LOGISTICS", "物流单号不能为空", "从纸质快递栏拆分或手工补录");
            require(messages, fields, "handler", "OUT-HANDLER", "经办人不能为空", "补录经办人");
            rows.add(row(rowIndex, fields, messages));
        }
        return rows;
    }

    private List<RegisterImportPreviewRow> parsePolishing(Sheet sheet, String businessDate) {
        List<RegisterImportPreviewRow> rows = new ArrayList<>();
        String operator = "";
        String workDate = "";
        for (int rowIndex = 0; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            String firstCell = cellText(sheet, rowIndex, 0);
            if (firstCell.startsWith("姓名")) {
                operator = cellText(sheet, rowIndex, 1);
                workDate = normalizeDate(cellText(sheet, rowIndex, 3), businessDate);
                continue;
            }
            if (!isNumericLike(firstCell) || isBlankRow(sheet, rowIndex, 1, 6)) {
                continue;
            }
            Map<String, String> fields = baseFields("工作项目记录单（日）", rowIndex, firstCell);
            fields.put("operator", operator);
            fields.put("workDate", workDate);
            fields.put("workItemName", cellText(sheet, rowIndex, 1));
            fields.put("startedAt", cellText(sheet, rowIndex, 2));
            fields.put("finishedAt", cellText(sheet, rowIndex, 3));
            fields.put("completionStatus", cellText(sheet, rowIndex, 4));
            fields.put("handoffAuditStatus", cellText(sheet, rowIndex, 5));
            fields.put("remark", cellText(sheet, rowIndex, 6));

            List<RegisterImportPreviewMessage> messages = new ArrayList<>();
            require(messages, fields, "operator", "POL-OPERATOR", "姓名表头必须展开到明细行", "补录作业人");
            require(messages, fields, "workDate", "POL-DATE", "日期表头必须展开到明细行", "补录工序日期");
            require(messages, fields, "workItemName", "POL-WORK-ITEM", "工作项目不能为空", "补录工作项目");
            require(messages, fields, "completionStatus", "POL-COMPLETE", "完成情况不能为空", "补录完成情况");
            require(messages, fields, "handoffAuditStatus", "POL-AUDIT", "交单审核不能为空", "保存报工草稿");
            rows.add(row(rowIndex, fields, messages));
        }
        return rows;
    }

    private List<RegisterImportPreviewRow> parseEngraving(Sheet sheet, String businessDate) {
        List<RegisterImportPreviewRow> rows = new ArrayList<>();
        for (int rowIndex = 3; rowIndex <= sheet.getLastRowNum(); rowIndex++) {
            if (isBlankRow(sheet, rowIndex, 4, 7)) {
                continue;
            }
            Map<String, String> fields = baseFields("雕刻机统计单（日）", rowIndex, cellText(sheet, rowIndex, 0));
            fields.put("workDate", normalizeDate(cellText(sheet, rowIndex, 1), businessDate));
            fields.put("customerNameSnapshot", cellText(sheet, rowIndex, 2));
            fields.put("productCategory", cellText(sheet, rowIndex, 3));
            fields.put("machineNo", cellText(sheet, rowIndex, 4));
            addQuantity(fields, cellText(sheet, rowIndex, 5));
            fields.put("operatorSignatureName", cellText(sheet, rowIndex, 6));
            fields.put("remark", cellText(sheet, rowIndex, 7));

            List<RegisterImportPreviewMessage> messages = new ArrayList<>();
            require(messages, fields, "workDate", "ENG-DATE", "日期必须从合并区域继承", "导入预览阻断");
            require(messages, fields, "customerNameSnapshot", "ENG-CUSTOMER", "客户必须从合并区域继承", "转待确认客户");
            require(messages, fields, "productCategory", "ENG-CATEGORY", "品类必须从合并区域继承", "补录品类");
            require(messages, fields, "machineNo", "ENG-MACHINE", "机器编号不能为空", "补录机器编号");
            require(messages, fields, "quantity", "ENG-QTY", "雕刻数量必须大于 0", "补录数量");
            require(messages, fields, "operatorSignatureName", "ENG-SIGN", "操机签字不能为空", "保存草稿");
            rows.add(row(rowIndex, fields, messages));
        }
        return rows;
    }

    private Map<String, String> baseFields(String registerType, int rowIndex, String sequence) {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("sourceRegisterType", registerType);
        fields.put("sourceRegisterLineNo", String.valueOf(rowIndex + 1));
        fields.put("sourceRegisterSequence", sequence);
        return fields;
    }

    private RegisterImportPreviewRow row(
            int rowIndex,
            Map<String, String> fields,
            List<RegisterImportPreviewMessage> messages
    ) {
        String status = messages.stream().anyMatch(message -> "BLOCK".equals(message.level()))
                ? "BLOCKED"
                : messages.isEmpty() ? "READY" : "WARNING";
        return new RegisterImportPreviewRow(
                rowIndex + 1,
                fields.get("sourceRegisterSequence"),
                status,
                fields,
                List.copyOf(messages)
        );
    }

    private void require(
            List<RegisterImportPreviewMessage> messages,
            Map<String, String> fields,
            String field,
            String ruleCode,
            String message,
            String fallback
    ) {
        String value = fields.get(field);
        if (value == null || value.isBlank() || "0".equals(value)) {
            messages.add(new RegisterImportPreviewMessage(ruleCode, "BLOCK", field, message, fallback));
        }
    }

    private void addQuantity(Map<String, String> fields, String raw) {
        String trimmed = clean(raw);
        if (trimmed.isBlank()) {
            fields.put("quantity", "");
            fields.put("quantityUnit", "件");
            return;
        }
        Matcher matcher = QUANTITY_PATTERN.matcher(trimmed.replace(",", ""));
        if (matcher.matches()) {
            fields.put("quantity", matcher.group(1));
            String unit = matcher.group(2).replaceAll("[\\s　]", "");
            fields.put("quantityUnit", unit.isBlank() ? "件" : unit);
        } else {
            fields.put("quantity", trimmed);
            fields.put("quantityUnit", "件");
        }
    }

    private String normalizeDate(String raw, String businessDate) {
        String value = clean(raw);
        if (value.isBlank()) {
            return "";
        }
        if (value.matches("\\d{4}-\\d{1,2}-\\d{1,2}")) {
            String[] parts = value.split("-");
            return "%s-%02d-%02d".formatted(parts[0], Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
        }
        Matcher matcher = MONTH_DAY_PATTERN.matcher(value);
        if (matcher.find()) {
            int year = resolveYear(businessDate);
            return "%d-%02d-%02d".formatted(year, Integer.parseInt(matcher.group(1)), Integer.parseInt(matcher.group(2)));
        }
        if (value.matches("\\d{1,2}-\\d{1,2}")) {
            String[] parts = value.split("-");
            return "%d-%02d-%02d".formatted(resolveYear(businessDate), Integer.parseInt(parts[0]), Integer.parseInt(parts[1]));
        }
        return value;
    }

    private int resolveYear(String businessDate) {
        if (businessDate != null && businessDate.matches("\\d{4}-\\d{1,2}-\\d{1,2}")) {
            return Integer.parseInt(businessDate.substring(0, 4));
        }
        return LocalDate.now().getYear();
    }

    private String splitExpressCompany(String raw) {
        String value = clean(raw);
        if (value.isBlank()) {
            return "";
        }
        String[] parts = value.split("\\s+");
        if (parts.length > 1) {
            return parts[0];
        }
        Matcher matcher = Pattern.compile("^([\\p{IsHan}A-Za-z]+)").matcher(value);
        return matcher.find() ? matcher.group(1) : value;
    }

    private String splitLogisticsNo(String raw) {
        String value = clean(raw);
        if (value.isBlank()) {
            return "";
        }
        String[] parts = value.split("\\s+");
        if (parts.length > 1) {
            return parts[parts.length - 1];
        }
        Matcher matcher = Pattern.compile("([A-Za-z]{1,5}[-]?[0-9][A-Za-z0-9-]*)").matcher(value);
        return matcher.find() ? matcher.group(1) : "";
    }

    private boolean isBlankRow(Sheet sheet, int rowIndex, int startColumn, int endColumn) {
        for (int column = startColumn; column <= endColumn; column++) {
            if (!cellText(sheet, rowIndex, column).isBlank()) {
                return false;
            }
        }
        return true;
    }

    private boolean isNumericLike(String value) {
        return value != null && value.matches("\\d+(?:\\.0)?");
    }

    private String cellText(Sheet sheet, int rowIndex, int columnIndex) {
        Row row = sheet.getRow(rowIndex);
        Cell cell = row == null ? null : row.getCell(columnIndex);
        if (cell == null || formatter.formatCellValue(cell).isBlank()) {
            Cell mergedCell = mergedTopLeftCell(sheet, rowIndex, columnIndex);
            if (mergedCell != null) {
                return clean(formatter.formatCellValue(mergedCell));
            }
        }
        return clean(cell == null ? "" : formatter.formatCellValue(cell));
    }

    private Cell mergedTopLeftCell(Sheet sheet, int rowIndex, int columnIndex) {
        for (CellRangeAddress range : sheet.getMergedRegions()) {
            if (range.isInRange(rowIndex, columnIndex)) {
                Row firstRow = sheet.getRow(range.getFirstRow());
                return firstRow == null ? null : firstRow.getCell(range.getFirstColumn());
            }
        }
        return null;
    }

    private String clean(String value) {
        return value == null ? "" : value.replace('\u00A0', ' ').replace('\u3000', ' ').trim();
    }
}
