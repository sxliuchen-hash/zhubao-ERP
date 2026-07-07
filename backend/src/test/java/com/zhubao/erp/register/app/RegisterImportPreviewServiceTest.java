package com.zhubao.erp.register.app;

import com.zhubao.erp.register.domain.RegisterImportPreview;
import com.zhubao.erp.register.domain.RegisterImportPreviewRow;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;

class RegisterImportPreviewServiceTest {

    private final RegisterImportTemplateService templateService = new RegisterImportTemplateService();
    private final RegisterImportPreviewService previewService = new RegisterImportPreviewService(templateService);

    @Test
    void skipsInboundTemplateRowsThatOnlyContainMonthDayPlaceholders() throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Sheet1");
            sheet.createRow(0).createCell(0).setCellValue("签收入库登记表");
            Row header = sheet.createRow(2);
            header.createCell(0).setCellValue("序号");
            header.createCell(1).setCellValue("入库日期");
            header.createCell(2).setCellValue("快递名称");
            header.createCell(3).setCellValue("快递单号");
            header.createCell(4).setCellValue("寄货人\n姓名");
            header.createCell(5).setCellValue("收货人\n姓名");
            header.createCell(6).setCellValue("拆箱签字");
            header.createCell(7).setCellValue("货品名称");
            header.createCell(8).setCellValue("数量");
            Row emptyTemplateRow = sheet.createRow(3);
            emptyTemplateRow.createCell(0).setCellValue("1");
            emptyTemplateRow.createCell(1).setCellValue("     月    日");
            Row data = sheet.createRow(4);
            data.createCell(0).setCellValue("2");
            data.createCell(1).setCellValue("7月6日");
            data.createCell(2).setCellValue("顺丰");
            data.createCell(3).setCellValue("SF1048329001");
            data.createCell(4).setCellValue("张先生");
            data.createCell(5).setCellValue("何琳");
            data.createCell(6).setCellValue("何琳");
            data.createCell(7).setCellValue("戒托来料");
            data.createCell(8).setCellValue("12件");

            RegisterImportPreview preview = previewService.preview(
                    "REG-INBOUND",
                    "2026-07-07",
                    "入库登记表.xlsx",
                    bytes(workbook)
            );

            assertThat(preview.totalRows()).isEqualTo(1);
            RegisterImportPreviewRow row = preview.rows().get(0);
            assertThat(row.status()).isEqualTo("READY");
            assertThat(row.sourceLineNo()).isEqualTo(5);
            assertThat(row.normalizedFields()).containsEntry("sourceSheetName", "Sheet1");
            assertThat(row.normalizedFields()).containsEntry("sourceRegisterSequence", "2");
            assertThat(row.normalizedFields()).containsEntry("inboundDate", "2026-07-06");
        }
    }

    @Test
    void previewsOutboundRegisterAndKeepsPaperExpressText() throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Sheet1");
            sheet.createRow(0).createCell(0).setCellValue("交货情况登记表");
            Row header = sheet.createRow(1);
            header.createCell(0).setCellValue("序号");
            header.createCell(1).setCellValue("日期");
            header.createCell(2).setCellValue("客户");
            header.createCell(3).setCellValue("交货类目");
            header.createCell(4).setCellValue("交货数量");
            header.createCell(5).setCellValue("快递");
            header.createCell(6).setCellValue("经办人");
            header.createCell(7).setCellValue("备注");
            Row data = sheet.createRow(2);
            data.createCell(0).setCellValue("1");
            data.createCell(1).setCellValue("7月6日");
            data.createCell(2).setCellValue("雅辰饰品");
            data.createCell(3).setCellValue("成品");
            data.createCell(4).setCellValue("620件");
            data.createCell(5).setCellValue("顺丰 SF-OUT-20260706-022");
            data.createCell(6).setCellValue("何琳");
            data.createCell(7).setCellValue("客户成品出货");

            RegisterImportPreview preview = previewService.preview(
                    "REG-OUTBOUND",
                    "2026-07-07",
                    "出货登记表.xlsx",
                    bytes(workbook)
            );

            assertThat(preview.totalRows()).isEqualTo(1);
            assertThat(preview.readyRows()).isEqualTo(1);
            assertThat(preview.blockedRows()).isZero();
            RegisterImportPreviewRow row = preview.rows().get(0);
            assertThat(row.status()).isEqualTo("READY");
            assertThat(row.normalizedFields()).containsEntry("deliveryDate", "2026-07-06");
            assertThat(row.normalizedFields()).containsEntry("quantity", "620");
            assertThat(row.normalizedFields()).containsEntry("quantityUnit", "件");
            assertThat(row.normalizedFields()).containsEntry("paperExpressText", "顺丰 SF-OUT-20260706-022");
            assertThat(row.normalizedFields()).containsEntry("expressCompany", "顺丰");
            assertThat(row.normalizedFields()).containsEntry("logisticsNo", "SF-OUT-20260706-022");
        }
    }

    @Test
    void previewsPolishingRowsWithHeaderContextInherited() throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Sheet1");
            sheet.createRow(0).createCell(0).setCellValue("工作项目记录单（日）");
            Row context = sheet.createRow(2);
            context.createCell(0).setCellValue("姓名：");
            context.createCell(1).setCellValue("王强");
            context.createCell(3).setCellValue("日期");
            context.createCell(5).setCellValue("7月6日");
            Row header = sheet.createRow(3);
            header.createCell(0).setCellValue("序号");
            header.createCell(1).setCellValue("工作项目");
            Row data = sheet.createRow(4);
            data.createCell(0).setCellValue("1");
            data.createCell(1).setCellValue("返工抛光");
            data.createCell(2).setCellValue("09:10");
            data.createCell(3).setCellValue("10:05");
            data.createCell(4).setCellValue("已完成");
            data.createCell(5).setCellValue("已接收");
            data.createCell(6).setCellValue("返工复检前处理");

            RegisterImportPreview preview = previewService.preview(
                    "REG-POLISHING",
                    "2026-07-07",
                    "抛光登记表.xlsx",
                    bytes(workbook)
            );

            assertThat(preview.totalRows()).isEqualTo(1);
            RegisterImportPreviewRow row = preview.rows().get(0);
            assertThat(row.status()).isEqualTo("READY");
            assertThat(row.normalizedFields()).containsEntry("operator", "王强");
            assertThat(row.normalizedFields()).containsEntry("workDate", "2026-07-06");
            assertThat(row.normalizedFields()).containsEntry("workItemName", "返工抛光");
            assertThat(row.normalizedFields()).containsEntry("handoffAuditStatus", "已接收");
        }
    }

    @Test
    void previewsEngravingRowsWithMergedContextInherited() throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Sheet1");
            sheet.createRow(0).createCell(0).setCellValue("雕刻机统计单（日）");
            sheet.addMergedRegion(new CellRangeAddress(3, 10, 0, 0));
            sheet.addMergedRegion(new CellRangeAddress(3, 10, 1, 1));
            sheet.addMergedRegion(new CellRangeAddress(3, 10, 6, 6));
            Row firstMachine = sheet.createRow(3);
            firstMachine.createCell(0).setCellValue("1");
            firstMachine.createCell(1).setCellValue("7月6日");
            firstMachine.createCell(2).setCellValue("恒信珠宝");
            firstMachine.createCell(3).setCellValue("戒托");
            firstMachine.createCell(4).setCellValue("1");
            firstMachine.createCell(5).setCellValue("168件");
            firstMachine.createCell(6).setCellValue("周凯");
            Row secondMachine = sheet.createRow(4);
            secondMachine.createCell(4).setCellValue("2");
            secondMachine.createCell(5).setCellValue("80件");

            RegisterImportPreview preview = previewService.preview(
                    "REG-ENGRAVING",
                    "2026-07-07",
                    "雕刻登记表.xlsx",
                    bytes(workbook)
            );

            assertThat(preview.totalRows()).isEqualTo(2);
            assertThat(preview.blockedRows()).isZero();
            RegisterImportPreviewRow inherited = preview.rows().get(1);
            assertThat(inherited.status()).isEqualTo("READY");
            assertThat(inherited.normalizedFields()).containsEntry("sourceRegisterSequence", "1");
            assertThat(inherited.normalizedFields()).containsEntry("workDate", "2026-07-06");
            assertThat(inherited.normalizedFields()).containsEntry("customerNameSnapshot", "恒信珠宝");
            assertThat(inherited.normalizedFields()).containsEntry("productCategory", "戒托");
            assertThat(inherited.normalizedFields()).containsEntry("machineLineNo", "2");
            assertThat(inherited.normalizedFields()).containsEntry("machineNo", "2");
            assertThat(inherited.normalizedFields()).containsEntry("operatorSignatureName", "周凯");
        }
    }

    @Test
    void skipsBlankEngravingTemplateMachineRows() throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Sheet1");
            sheet.createRow(0).createCell(0).setCellValue("雕刻机统计单（日）");
            sheet.addMergedRegion(new CellRangeAddress(3, 10, 0, 0));
            sheet.addMergedRegion(new CellRangeAddress(3, 10, 1, 1));
            sheet.addMergedRegion(new CellRangeAddress(3, 10, 6, 6));
            Row firstMachine = sheet.createRow(3);
            firstMachine.createCell(0).setCellValue("1");
            firstMachine.createCell(1).setCellValue("     月         日");
            firstMachine.createCell(4).setCellValue("1");
            Row secondMachine = sheet.createRow(4);
            secondMachine.createCell(4).setCellValue("2");

            RegisterImportPreview preview = previewService.preview(
                    "REG-ENGRAVING",
                    "2026-07-07",
                    "雕刻登记表.xlsx",
                    bytes(workbook)
            );

            assertThat(preview.totalRows()).isZero();
            assertThat(preview.rows()).isEmpty();
        }
    }

    @Test
    void inheritsEngravingContextFromBlankFirstMachineLine() throws IOException {
        try (Workbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Sheet1");
            sheet.createRow(0).createCell(0).setCellValue("雕刻机统计单（日）");
            sheet.addMergedRegion(new CellRangeAddress(3, 10, 0, 0));
            sheet.addMergedRegion(new CellRangeAddress(3, 10, 1, 1));
            sheet.addMergedRegion(new CellRangeAddress(3, 10, 6, 6));
            Row firstMachine = sheet.createRow(3);
            firstMachine.createCell(0).setCellValue("1");
            firstMachine.createCell(1).setCellValue("7月6日");
            firstMachine.createCell(2).setCellValue("恒信珠宝");
            firstMachine.createCell(3).setCellValue("戒托");
            firstMachine.createCell(4).setCellValue("1");
            firstMachine.createCell(6).setCellValue("周凯");
            Row secondMachine = sheet.createRow(4);
            secondMachine.createCell(4).setCellValue("2");
            secondMachine.createCell(5).setCellValue("80件");

            RegisterImportPreview preview = previewService.preview(
                    "REG-ENGRAVING",
                    "2026-07-07",
                    "雕刻登记表.xlsx",
                    bytes(workbook)
            );

            assertThat(preview.totalRows()).isEqualTo(1);
            RegisterImportPreviewRow row = preview.rows().get(0);
            assertThat(row.status()).isEqualTo("READY");
            assertThat(row.normalizedFields()).containsEntry("customerNameSnapshot", "恒信珠宝");
            assertThat(row.normalizedFields()).containsEntry("productCategory", "戒托");
            assertThat(row.normalizedFields()).containsEntry("machineLineNo", "2");
            assertThat(row.normalizedFields()).containsEntry("operatorSignatureName", "周凯");
        }
    }

    private byte[] bytes(Workbook workbook) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        workbook.write(out);
        return out.toByteArray();
    }
}
