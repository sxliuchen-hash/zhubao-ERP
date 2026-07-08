package com.zhubao.erp.register.app;

import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.register.domain.ConfirmRegisterImportCommand;
import com.zhubao.erp.register.domain.CorrectRegisterImportRowCommand;
import com.zhubao.erp.register.domain.RegisterImportBatch;
import com.zhubao.erp.register.domain.RegisterImportPreview;
import com.zhubao.erp.register.domain.RegisterImportPreviewMessage;
import com.zhubao.erp.register.domain.RegisterImportPreviewRow;
import com.zhubao.erp.register.infra.InMemoryRegisterImportBatchRepository;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RegisterImportConfirmationServiceTest {

    private final RegisterImportTemplateService templateService = new RegisterImportTemplateService();
    private final InMemoryRegisterImportBatchRepository repository = new InMemoryRegisterImportBatchRepository();
    private final RegisterImportConfirmationService service = new RegisterImportConfirmationService(
            templateService,
            repository
    );

    @Test
    void confirmsExecutablePreviewAsStructuredBatch() {
        RegisterImportBatch batch = service.confirm(new ConfirmRegisterImportCommand(
                readyPreview(),
                "何琳",
                "现场复核通过"
        ));

        assertThat(batch.importBatchNo()).isEqualTo("IMP-REG-OUTBOUND-20260707190000");
        assertThat(batch.status()).isEqualTo("CONFIRMED");
        assertThat(batch.targetCommand()).isEqualTo("ShipFinishedStockCommand");
        assertThat(batch.confirmedBy()).isEqualTo("何琳");
        assertThat(batch.nextAction()).contains("结构化导入批次");
        assertThat(batch.rows()).hasSize(1);
        assertThat(batch.rows().get(0).normalizedFields()).containsEntry("logisticsNo", "SF-OUT-20260706-022");
        assertThat(repository.findByBatchNo(batch.importBatchNo())).contains(batch);
    }

    @Test
    void rejectsBlockedPreviewBeforePersistence() {
        RegisterImportPreview blocked = new RegisterImportPreview(
                "IMP-REG-OUTBOUND-BLOCKED",
                "REG-OUTBOUND",
                "交货情况登记表",
                "出货登记表.xlsx",
                1,
                0,
                0,
                1,
                false,
                "先处理阻断行，再确认导入",
                List.of(new RegisterImportPreviewRow(
                        3,
                        "1",
                        "BLOCKED",
                        Map.of("paperExpressText", ""),
                        List.of(new RegisterImportPreviewMessage(
                                "OUT-LOGISTICS",
                                "BLOCK",
                                "logisticsNo",
                                "物流单号不能为空",
                                "从纸质快递栏拆分或手工补录"
                        ))
                ))
        );

        assertThatThrownBy(() -> service.confirm(new ConfirmRegisterImportCommand(blocked, "何琳", "")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("存在阻断行");
        assertThat(repository.findByBatchNo("IMP-REG-OUTBOUND-BLOCKED")).isEmpty();
    }

    @Test
    void rejectsDuplicateConfirmedBatchNo() {
        service.confirm(new ConfirmRegisterImportCommand(readyPreview(), "何琳", "首次确认"));

        assertThatThrownBy(() -> service.confirm(new ConfirmRegisterImportCommand(readyPreview(), "何琳", "重复确认")))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("导入批次已确认");
    }

    @Test
    void correctsConfirmedBatchRowFieldsWithoutPostingInventory() {
        RegisterImportBatch batch = service.confirm(new ConfirmRegisterImportCommand(
                readyPreview(),
                "何琳",
                "首次确认"
        ));

        RegisterImportBatch corrected = service.correctRowFields(
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
                        "匹配客户、母单、工单和物料"
                )
        );

        assertThat(corrected.status()).isEqualTo("CONFIRMED");
        assertThat(corrected.nextAction()).contains("复核补录").contains("重新生成入账映射计划");
        assertThat(corrected.rows().get(0).status()).isEqualTo("READY");
        assertThat(corrected.rows().get(0).normalizedFields())
                .containsEntry("logisticsNo", "SF-OUT-20260706-022")
                .containsEntry("shipmentCode", "OUT-REG-001")
                .containsEntry("materialCode", "MAT-18K-RING");
    }

    @Test
    void rejectsCorrectionForLockedSourceAuditFields() {
        RegisterImportBatch batch = service.confirm(new ConfirmRegisterImportCommand(
                readyPreview(),
                "何琳",
                "首次确认"
        ));

        assertThatThrownBy(() -> service.correctRowFields(
                batch.importBatchNo(),
                3,
                new CorrectRegisterImportRowCommand(
                        Map.of("sourceRegisterLineNo", "99"),
                        "何琳",
                        "尝试覆盖来源行号"
                )
        ))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("来源审计字段不能通过补录接口修改");
    }

    private RegisterImportPreview readyPreview() {
        Map<String, String> fields = new LinkedHashMap<>();
        fields.put("sourceSheetName", "Sheet1");
        fields.put("sourceRegisterType", "交货情况登记表");
        fields.put("sourceRegisterLineNo", "3");
        fields.put("sourceRegisterSequence", "1");
        fields.put("deliveryDate", "2026-07-06");
        fields.put("customerNameSnapshot", "雅辰饰品");
        fields.put("paperExpressText", "顺丰 SF-OUT-20260706-022");
        fields.put("logisticsNo", "SF-OUT-20260706-022");
        fields.put("handler", "何琳");

        return new RegisterImportPreview(
                "IMP-REG-OUTBOUND-20260707190000",
                "REG-OUTBOUND",
                "交货情况登记表",
                "出货登记表.xlsx",
                1,
                1,
                0,
                0,
                true,
                "可进入人工复核或确认导入",
                List.of(new RegisterImportPreviewRow(
                        3,
                        "1",
                        "READY",
                        fields,
                        List.of()
                ))
        );
    }
}
