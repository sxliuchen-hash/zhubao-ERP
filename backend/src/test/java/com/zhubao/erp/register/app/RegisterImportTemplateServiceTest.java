package com.zhubao.erp.register.app;

import com.zhubao.erp.register.domain.RegisterImportTemplate;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class RegisterImportTemplateServiceTest {

    private final RegisterImportTemplateService service = new RegisterImportTemplateService();

    @Test
    void exposesFourFieldRegisterTemplatesWithCommandMappings() {
        List<RegisterImportTemplate> templates = service.findTemplates();

        assertThat(templates).hasSize(4);
        assertThat(templates)
                .extracting(RegisterImportTemplate::code)
                .containsExactly("REG-INBOUND", "REG-OUTBOUND", "REG-POLISHING", "REG-ENGRAVING");
        assertThat(templates.get(0).fields())
                .anySatisfy(field -> assertThat(field.targetField()).isEqualTo("sourceRegisterSequence"))
                .anySatisfy(field -> assertThat(field.targetField()).isEqualTo("sourceSheetName"))
                .anySatisfy(field -> assertThat(field.targetField()).isEqualTo("sourceRegisterType"))
                .anySatisfy(field -> assertThat(field.targetField()).isEqualTo("sourceRegisterLineNo"))
                .anySatisfy(field -> assertThat(field.targetField()).isEqualTo("quantity + quantityUnit"))
                .anySatisfy(field -> assertThat(field.targetField()).isEqualTo("remark"));
        assertThat(templates.get(1).fields())
                .anySatisfy(field -> assertThat(field.targetField()).isEqualTo("paperExpressText + expressCompany + logisticsNo"));
        assertThat(templates.get(2).validationRules())
                .anySatisfy(rule -> assertThat(rule.description()).contains("姓名和日期必须展开"));
        assertThat(templates.get(3).validationRules())
                .anySatisfy(rule -> assertThat(rule.description()).contains("上下文继承"));
        assertThat(templates.get(3).fields())
                .anySatisfy(field -> assertThat(field.targetField()).isEqualTo("machineLineNo"));
    }

    @Test
    void summarizesTemplateReadinessWithPreviewParserReady() {
        RegisterImportTemplateService.RegisterImportSummary summary = service.summary();

        assertThat(summary.templates()).isEqualTo(4);
        assertThat(summary.fields()).isEqualTo(48);
        assertThat(summary.requiredFields()).isGreaterThan(20);
        assertThat(summary.commandReadyTemplates()).isEqualTo(4);
        assertThat(summary.parserStatus()).contains("preview parser ready");
    }

    @Test
    void coversObservedReferenceRegisterColumns() {
        Map<String, List<String>> observedColumns = Map.of(
                "REG-INBOUND",
                List.of("序号", "入库日期", "快递名称", "快递单号", "寄货人/姓名", "收货人/姓名", "拆箱签字", "货品名称", "数量", "备注"),
                "REG-OUTBOUND",
                List.of("序号", "日期", "客户", "交货类目", "交货数量", "快递", "经办人", "备注"),
                "REG-POLISHING",
                List.of("姓名", "日期", "序号", "工作项目", "开始时间", "完成时间", "完成情况", "交单审核", "备注"),
                "REG-ENGRAVING",
                List.of("序号", "日期", "客户", "品类", "机器编号", "雕刻数量", "操机签字", "备注", "机器编号行号")
        );

        observedColumns.forEach((templateCode, columns) -> assertThat(sourceColumns(templateCode)).containsAll(columns));
    }

    private List<String> sourceColumns(String templateCode) {
        return service.findTemplates().stream()
                .filter(template -> template.code().equals(templateCode))
                .findFirst()
                .orElseThrow()
                .fields()
                .stream()
                .map(field -> field.sourceColumn())
                .toList();
    }
}
