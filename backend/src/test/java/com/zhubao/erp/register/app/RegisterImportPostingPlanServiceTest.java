package com.zhubao.erp.register.app;

import com.zhubao.erp.register.domain.ConfirmRegisterImportCommand;
import com.zhubao.erp.register.domain.CorrectRegisterImportRowCommand;
import com.zhubao.erp.register.domain.RegisterImportBatch;
import com.zhubao.erp.register.domain.RegisterImportPostingPlan;
import com.zhubao.erp.register.domain.RegisterImportPreview;
import com.zhubao.erp.register.domain.RegisterImportPreviewRow;
import com.zhubao.erp.register.infra.InMemoryRegisterImportBatchRepository;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RegisterImportPostingPlanServiceTest {

    private final RegisterImportTemplateService templateService = new RegisterImportTemplateService();
    private final InMemoryRegisterImportBatchRepository repository = new InMemoryRegisterImportBatchRepository();
    private final RegisterImportConfirmationService confirmationService = new RegisterImportConfirmationService(
            templateService,
            repository
    );
    private final RegisterImportPostingPlanService service = new RegisterImportPostingPlanService(confirmationService);

    @Test
    void buildsBlockedPostingPlanWhenConfirmedBatchStillLacksBusinessKeys() {
        RegisterImportBatch batch = confirmationService.confirm(new ConfirmRegisterImportCommand(
                outboundPreviewWithoutBusinessKeys(),
                "何琳",
                "现场复核通过"
        ));

        RegisterImportPostingPlan plan = service.plan(batch.importBatchNo());

        assertThat(plan.importBatchNo()).isEqualTo(batch.importBatchNo());
        assertThat(plan.targetCommand()).isEqualTo("ShipFinishedStockCommand");
        assertThat(plan.status()).isEqualTo("BLOCKED");
        assertThat(plan.blockedDrafts()).isEqualTo(1);
        assertThat(plan.nextAction()).contains("补齐业务主数据");
        assertThat(plan.drafts()).hasSize(1);
        assertThat(plan.drafts().get(0).mappedFields()).containsEntry("logisticsNo", "SF-OUT-20260706-022");
        assertThat(plan.drafts().get(0).missingFields())
                .extracting("field")
                .contains("shipmentCode", "motherOrderCode", "workOrderCode", "customerId", "materialCode", "weight");
    }

    @Test
    void rebuildsPostingPlanAsReadyAfterReviewCorrectionCompletesBusinessKeys() {
        RegisterImportBatch batch = confirmationService.confirm(new ConfirmRegisterImportCommand(
                outboundPreviewWithoutBusinessKeys(),
                "何琳",
                "现场复核通过"
        ));

        confirmationService.correctRowFields(
                batch.importBatchNo(),
                3,
                new CorrectRegisterImportRowCommand(
                        Map.of(
                                "shipmentCode", "OUT-REG-001",
                                "motherOrderCode", "MO-REG-001",
                                "workOrderCode", "WO-REG-001",
                                "customerId", "1001",
                                "materialCode", "MAT-18K-RING",
                                "weight", "9.200"
                        ),
                        "何琳",
                        "主数据匹配补录"
                )
        );

        RegisterImportPostingPlan plan = service.plan(batch.importBatchNo());

        assertThat(plan.status()).isEqualTo("READY");
        assertThat(plan.readyDrafts()).isEqualTo(1);
        assertThat(plan.blockedDrafts()).isZero();
        assertThat(plan.nextAction()).contains("出库单");
        assertThat(plan.drafts().get(0).mappedFields())
                .containsEntry("shipmentCode", "OUT-REG-001")
                .containsEntry("customerId", "1001")
                .containsEntry("weight", "9.200");
        assertThat(plan.drafts().get(0).missingFields()).isEmpty();
    }

    @Test
    void marksDraftReadyWhenInboundRowAlreadyContainsBusinessPostingFields() {
        RegisterImportBatch batch = confirmationService.confirm(new ConfirmRegisterImportCommand(
                inboundPreviewWithBusinessKeys(),
                "何琳",
                "现场复核通过"
        ));

        RegisterImportPostingPlan plan = service.plan(batch.importBatchNo());

        assertThat(plan.status()).isEqualTo("READY");
        assertThat(plan.readyDrafts()).isEqualTo(1);
        assertThat(plan.blockedDrafts()).isZero();
        assertThat(plan.nextAction()).contains("母单/签收入库命令");
        assertThat(plan.drafts().get(0).mappedFields()).containsEntry("warehouseCode", "RAW-CUSTOMER");
        assertThat(plan.drafts().get(0).missingFields()).isEmpty();
    }

    private RegisterImportPreview outboundPreviewWithoutBusinessKeys() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("sourceSheetName", "Sheet1");
        fields.put("sourceRegisterType", "交货情况登记表");
        fields.put("sourceRegisterLineNo", "3");
        fields.put("sourceRegisterSequence", "1");
        fields.put("deliveryDate", "2026-07-06");
        fields.put("customerNameSnapshot", "雅辰饰品");
        fields.put("deliveryCategory", "成品");
        fields.put("quantity", "620");
        fields.put("quantityUnit", "件");
        fields.put("paperExpressText", "顺丰 SF-OUT-20260706-022");
        fields.put("expressCompany", "顺丰");
        fields.put("logisticsNo", "SF-OUT-20260706-022");
        fields.put("handler", "何琳");

        return preview(
                "IMP-REG-OUTBOUND-POSTING-PLAN",
                "REG-OUTBOUND",
                "交货情况登记表",
                "出货登记表.xlsx",
                fields
        );
    }

    private RegisterImportPreview inboundPreviewWithBusinessKeys() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("sourceSheetName", "Sheet1");
        fields.put("sourceRegisterType", "签收入库登记表");
        fields.put("sourceRegisterLineNo", "4");
        fields.put("sourceRegisterSequence", "1");
        fields.put("motherOrderCode", "MD-REG-20260708-001");
        fields.put("customerId", "1001");
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

        return preview(
                "IMP-REG-INBOUND-POSTING-PLAN",
                "REG-INBOUND",
                "签收入库登记表",
                "入库登记表.xlsx",
                fields
        );
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
