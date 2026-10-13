package com.zhubao.erp.register.app;

import com.zhubao.erp.business.app.BusinessOperationService;
import com.zhubao.erp.business.app.ProcessStationService;
import com.zhubao.erp.business.domain.ProductionReportResult;
import com.zhubao.erp.business.domain.ReportProductionCommand;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.inventory.domain.StockTransferCommand;
import com.zhubao.erp.inventory.infra.InMemoryStockLedgerRepository;
import com.zhubao.erp.machine.app.MachineOutputService;
import com.zhubao.erp.machine.infra.InMemoryMachineOutputRepository;
import com.zhubao.erp.register.domain.ConfirmRegisterImportCommand;
import com.zhubao.erp.register.domain.PostRegisterImportBatchCommand;
import com.zhubao.erp.register.domain.RegisterImportBatch;
import com.zhubao.erp.register.domain.RegisterImportPostingExecutionResult;
import com.zhubao.erp.register.domain.RegisterImportPostingPlan;
import com.zhubao.erp.register.domain.RegisterImportPreview;
import com.zhubao.erp.register.domain.RegisterImportPreviewRow;
import com.zhubao.erp.register.infra.InMemoryRegisterImportBatchRepository;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.RoleCode;
import org.mockito.ArgumentCaptor;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RegisterImportPostingExecutionServiceTest {

    private final RegisterImportTemplateService templateService = new RegisterImportTemplateService();
    private final InMemoryRegisterImportBatchRepository batchRepository = new InMemoryRegisterImportBatchRepository();
    private final RegisterImportConfirmationService confirmationService = new RegisterImportConfirmationService(
            templateService,
            batchRepository
    );
    private final RegisterImportPostingPlanService postingPlanService = new RegisterImportPostingPlanService(confirmationService);
    private final InMemoryStockLedgerRepository stockRepository = new InMemoryStockLedgerRepository();
    private final StockLedgerService stockLedgerService = new StockLedgerService(stockRepository);
    private final BusinessOperationService businessOperationService = new BusinessOperationService(
            stockLedgerService
    );
    private final InMemoryMachineOutputRepository machineOutputRepository = new InMemoryMachineOutputRepository();
    private final MachineOutputService machineOutputService = new MachineOutputService(
            machineOutputRepository
    );
    private final RegisterImportPostingExecutionService service = new RegisterImportPostingExecutionService(
            confirmationService,
            postingPlanService,
            businessOperationService,
            machineOutputService
    );

    @Test
    void postsReadyInboundBatchThroughBusinessOperationAndMarksBatchPosted() {
        RegisterImportBatch batch = confirmationService.confirm(new ConfirmRegisterImportCommand(
                inboundPreviewWithBusinessKeys(),
                "何琳",
                "现场复核通过"
        ));

        RegisterImportPostingExecutionResult result = service.post(
                batch.importBatchNo(),
                new PostRegisterImportBatchCommand(false, "库管员 何琳", "M3.78 显式入账测试")
        );
        RegisterImportBatch postedBatch = confirmationService.batch(batch.importBatchNo());
        RegisterImportPostingPlan postedPlan = postingPlanService.plan(batch.importBatchNo());

        assertThat(result.status()).isEqualTo("POSTED");
        assertThat(result.postedDrafts()).isEqualTo(1);
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).businessCode()).isEqualTo("MD-REG-20260708-001");
        assertThat(result.items().get(0).ledgerCode()).startsWith("STK-");
        assertThat(stockRepository.findAllLedgers()).hasSize(1);
        assertThat(stockRepository.findAllBalances()).hasSize(1);
        assertThat(postedBatch.status()).isEqualTo("POSTED");
        assertThat(postedBatch.rows().get(0).status()).isEqualTo("POSTED");
        assertThat(postedPlan.status()).isEqualTo("POSTED");
        assertThatThrownBy(() -> service.post(batch.importBatchNo(), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不能重复执行");
    }

    @Test
    void rejectsBlockedPostingPlanWithoutWritingLedger() {
        RegisterImportBatch batch = confirmationService.confirm(new ConfirmRegisterImportCommand(
                inboundPreviewWithoutBusinessKeys(),
                "何琳",
                "现场复核通过"
        ));

        assertThatThrownBy(() -> service.post(batch.importBatchNo(), null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("存在阻断行");
        assertThat(stockRepository.findAllLedgers()).isEmpty();
        assertThat(confirmationService.batch(batch.importBatchNo()).status()).isEqualTo("CONFIRMED");
    }

    @Test
    void postsEngravingBatchAndCreatesMachineOutputRecord() {
        seedWipStock("MD-REG-ENG-001", "WO-REG-ENG-001", "MAT-18K-RING", 1001L);
        ProcessStationService stations = mock(ProcessStationService.class);
        AuthUserAccount account = new AuthUserAccount("worker", "hash", "会话雕刻员", RoleCode.OPERATOR,
                DataScope.SELF, true, true).withAllowedProcesses(List.of("雕刻"));
        when(stations.reportProduction(any(), eq(account))).thenReturn(new ProductionReportResult(
                "WO-REG-ENG-001", "STK-STATION", "WIP", "SEMI-FINISHED", StockStatus.WIP,
                StockStatus.SEMI_FINISHED, new BigDecimal("12"), new BigDecimal("4.8"), "雕刻", account.operator(), ""));
        var secured = new RegisterImportPostingExecutionService(confirmationService, postingPlanService,
                businessOperationService, machineOutputService, stations);
        RegisterImportBatch batch = confirmationService.confirm(new ConfirmRegisterImportCommand(
                engravingPreviewWithBusinessKeys(),
                "车间复核员",
                "雕刻登记表复核通过"
        ));

        RegisterImportPostingExecutionResult result = secured.post(
                batch.importBatchNo(),
                new PostRegisterImportBatchCommand(false, "车间组长 李华", "雕刻设备产量入账"),
                account
        );

        assertThat(result.status()).isEqualTo("POSTED");
        assertThat(result.items()).hasSize(1);
        assertThat(result.items().get(0).message()).contains("已生成设备产量记录 MOR-");
        assertThat(stockRepository.findAllLedgers()).hasSize(1);
        verify(stations).reportProduction(any(), eq(account));
        assertThat(machineOutputRepository.findByImportBatchNo(batch.importBatchNo()))
                .singleElement()
                .satisfies(record -> {
                    assertThat(record.productCategory()).isEqualTo("戒托");
                    assertThat(record.machineNoSnapshot()).isEqualTo("ENG-03");
                    assertThat(record.outputQuantity()).isEqualByComparingTo("12");
                    assertThat(record.quantityUnit()).isEqualTo("件");
                    assertThat(record.operatorSignatureName()).isEqualTo("周凯");
                    assertThat(record.ledgerCode()).startsWith("STK-");
                    assertThat(record.sourceLineNo()).isEqualTo(6);
                    assertThat(record.importBatchNo()).isEqualTo(batch.importBatchNo());
                });
    }

    @Test
    void accountPostingUsesStationRulesAndPreservesRegisterMetadata() {
        ProcessStationService stations = mock(ProcessStationService.class);
        AuthUserAccount account = new AuthUserAccount("worker", "hash", "会话雕刻员", RoleCode.OPERATOR,
                DataScope.SELF, true, true).withAllowedProcesses(List.of("雕刻"));
        when(stations.reportProduction(any(), eq(account))).thenReturn(new ProductionReportResult(
                "WO-REG-ENG-001", "STK-STATION", "WIP", "SEMI-FINISHED", StockStatus.WIP,
                StockStatus.SEMI_FINISHED, new BigDecimal("12"), new BigDecimal("4.8"), "雕刻", account.operator(), ""));
        var secured = new RegisterImportPostingExecutionService(confirmationService, postingPlanService,
                businessOperationService, machineOutputService, stations);
        var batch = confirmationService.confirm(new ConfirmRegisterImportCommand(engravingPreviewWithBusinessKeys(), "复核员", ""));

        var result = secured.post(batch.importBatchNo(), new PostRegisterImportBatchCommand(false, "伪造操作人", "来源备注"), account);

        ArgumentCaptor<ReportProductionCommand> captured = ArgumentCaptor.forClass(ReportProductionCommand.class);
        verify(stations).reportProduction(captured.capture(), eq(account));
        assertThat(captured.getValue().operator()).isEqualTo(account.operator());
        assertThat(captured.getValue().machineNo()).isEqualTo("ENG-03");
        assertThat(captured.getValue().sourceRegisterLineNo()).isEqualTo(6);
        assertThat(captured.getValue().sourceRegisterSequence()).isEqualTo("1");
        assertThat(captured.getValue().remark()).contains("来源备注", batch.importBatchNo());
        assertThat(result.operator()).isEqualTo(account.operator());
        assertThat(stockRepository.findAllLedgers()).isEmpty();
        assertThat(machineOutputRepository.findByImportBatchNo(batch.importBatchNo())).singleElement()
                .satisfies(record -> assertThat(record.ledgerCode()).isEqualTo("STK-STATION"));
    }

    @Test
    void stationRejectionDoesNotPostBatchOrCreateMachineOutput() {
        ProcessStationService stations = mock(ProcessStationService.class);
        AuthUserAccount account = new AuthUserAccount("worker", "hash", "未绑定岗位", RoleCode.OPERATOR, DataScope.SELF, true, true);
        when(stations.reportProduction(any(), eq(account))).thenThrow(new BusinessException("当前账号不能做这道工序"));
        var secured = new RegisterImportPostingExecutionService(confirmationService, postingPlanService,
                businessOperationService, machineOutputService, stations);
        var batch = confirmationService.confirm(new ConfirmRegisterImportCommand(engravingPreviewWithBusinessKeys(), "复核员", ""));

        assertThatThrownBy(() -> secured.post(batch.importBatchNo(), new PostRegisterImportBatchCommand(false, "伪造操作人", ""), account))
                .isInstanceOf(BusinessException.class).hasMessageContaining("不能做这道工序");
        assertThat(confirmationService.batch(batch.importBatchNo()).status()).isEqualTo("CONFIRMED");
        assertThat(stockRepository.findAllLedgers()).isEmpty();
        assertThat(machineOutputRepository.findByImportBatchNo(batch.importBatchNo())).isEmpty();
    }

    private RegisterImportPreview inboundPreviewWithBusinessKeys() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("sourceSheetName", "Sheet1");
        fields.put("sourceRegisterType", "签收入库登记表");
        fields.put("sourceRegisterLineNo", "4");
        fields.put("sourceRegisterSequence", "1");
        fields.put("motherOrderCode", "MD-REG-20260708-001");
        fields.put("customerId", "1001");
        fields.put("ownerCustomerId", "1001");
        fields.put("materialCode", "MAT-18K-RING");
        fields.put("warehouseCode", "RAW-CUSTOMER");
        fields.put("inboundDate", "2026-07-08");
        fields.put("expressCompany", "顺丰");
        fields.put("courierNo", "SF-IN-20260708-001");
        fields.put("goodsName", "客户来料配件");
        fields.put("quantity", "320");
        fields.put("quantityUnit", "件");
        fields.put("weight", "18.500");
        fields.put("operator", "库管员 何琳");
        fields.put("processingRequirement", "戒托抛光");
        fields.put("dueDate", "2026-07-20");
        fields.put("customerName", "恒信珠宝");

        return preview(
                "IMP-REG-INBOUND-POSTING-EXEC",
                "REG-INBOUND",
                "签收入库登记表",
                "入库登记表.xlsx",
                fields
        );
    }

    private RegisterImportPreview inboundPreviewWithoutBusinessKeys() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("sourceSheetName", "Sheet1");
        fields.put("sourceRegisterType", "签收入库登记表");
        fields.put("sourceRegisterLineNo", "5");
        fields.put("sourceRegisterSequence", "2");
        fields.put("inboundDate", "2026-07-08");
        fields.put("courierNo", "YT-IN-20260708-002");
        fields.put("goodsName", "复检返修件");
        fields.put("quantity", "48");
        fields.put("quantityUnit", "件");

        return preview(
                "IMP-REG-INBOUND-POSTING-BLOCKED",
                "REG-INBOUND",
                "签收入库登记表",
                "入库登记表.xlsx",
                fields
        );
    }

    private RegisterImportPreview engravingPreviewWithBusinessKeys() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("sourceSheetName", "雕刻机统计单（日）");
        fields.put("sourceRegisterType", "雕刻机统计单（日）");
        fields.put("sourceRegisterLineNo", "6");
        fields.put("sourceRegisterSequence", "1");
        fields.put("motherOrderCode", "MD-REG-ENG-001");
        fields.put("workOrderCode", "WO-REG-ENG-001");
        fields.put("customerId", "1001");
        fields.put("customerNameSnapshot", "恒信珠宝");
        fields.put("materialCode", "MAT-18K-RING");
        fields.put("fromWarehouseCode", "WIP");
        fields.put("toWarehouseCode", "SEMI-FINISHED");
        fields.put("quantity", "12");
        fields.put("quantityUnit", "件");
        fields.put("weight", "4.800");
        fields.put("workDate", "2026-07-08");
        fields.put("process", "雕刻");
        fields.put("productCategory", "戒托");
        fields.put("machineLineNo", "3");
        fields.put("machineNo", "ENG-03");
        fields.put("operator", "周凯");
        fields.put("operatorSignatureName", "周凯");

        return preview(
                "IMP-REG-ENGRAVING-POSTING-EXEC",
                "REG-ENGRAVING",
                "雕刻机统计单（日）",
                "雕刻登记表.xlsx",
                fields
        );
    }

    private void seedWipStock(String motherOrderCode, String workOrderCode, String materialCode, long customerId) {
        stockLedgerService.transfer(new StockTransferCommand(
                "test_seed",
                "SEED-" + workOrderCode,
                null,
                new StockDimension(
                        OwnerType.CUSTOMER,
                        customerId,
                        customerId,
                        motherOrderCode,
                        workOrderCode,
                        materialCode,
                        "WIP",
                        StockStatus.WIP
                ),
                new BigDecimal("30"),
                new BigDecimal("12.000"),
                "测试前置在制库存"
        ));
    }

    private RegisterImportPreview preview(
            String batchNo,
            String templateCode,
            String templateName,
            String sourceFileName,
            Map<String, String> fields
    ) {
        return new RegisterImportPreview(
                batchNo,
                templateCode,
                templateName,
                sourceFileName,
                1,
                1,
                0,
                0,
                true,
                "可进入人工复核或确认导入",
                List.of(new RegisterImportPreviewRow(
                        Integer.parseInt(fields.get("sourceRegisterLineNo")),
                        fields.get("sourceRegisterSequence"),
                        "READY",
                        fields,
                        List.of()
                ))
        );
    }
}
