package com.zhubao.erp.label.app;

import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.business.infra.InMemoryBusinessDocumentRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.label.domain.GenerateLabelCommand;
import com.zhubao.erp.label.domain.LabelField;
import com.zhubao.erp.label.domain.LabelPayload;
import com.zhubao.erp.label.domain.LabelType;
import com.zhubao.erp.label.infra.InMemoryLabelTemplateRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 标签生成应用服务测试。
 */
class LabelServiceTest {

    @Test
    @DisplayName("通用生成：组装载荷并生成统一格式二维码内容")
    void 通用生成应组装载荷并生成二维码内容() {
        LabelService service = new LabelService();
        GenerateLabelCommand command = new GenerateLabelCommand(
                "OUTBOUND",
                "CK20260709001",
                List.of(new LabelField("客户", "客户#8"), new LabelField(" ", "空字段应被过滤")),
                "签收人：张三",
                "",
                null
        );

        LabelPayload payload = service.generate(command);

        assertThat(payload.type()).isEqualTo(LabelType.OUTBOUND);
        assertThat(payload.typeLabel()).isEqualTo("出货");
        assertThat(payload.primaryCode()).isEqualTo("CK20260709001");
        assertThat(payload.qrContent()).isEqualTo("JG|outbound|CK20260709001");
        assertThat(payload.fields()).singleElement()
                .satisfies(field -> {
                    assertThat(field.name()).isEqualTo("客户");
                    assertThat(field.value()).isEqualTo("客户#8");
                });
        assertThat(payload.footerLeft()).isEqualTo("签收人：张三");
        assertThat(payload.printedAt()).isNotBlank();
    }

    @Test
    @DisplayName("通用生成：类型不合法应抛出业务异常")
    void 类型不合法应抛出业务异常() {
        LabelService service = new LabelService();
        GenerateLabelCommand command = new GenerateLabelCommand("UNKNOWN", "X001", List.of(), null, null, null);

        assertThatThrownBy(() -> service.generate(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的标签类型");
    }

    @Test
    @DisplayName("通用生成：缺少主编码应抛出业务异常")
    void 缺少主编码应抛出业务异常() {
        LabelService service = new LabelService();
        GenerateLabelCommand command = new GenerateLabelCommand("MOTHER", "  ", List.of(), null, null, null);

        assertThatThrownBy(() -> service.generate(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少标签主编码");
    }

    @Test
    @DisplayName("母单标签：从结构化母单记录生成固定信息标签")
    void 母单标签应从母单记录生成固定信息() {
        InMemoryBusinessDocumentRepository repository = new InMemoryBusinessDocumentRepository();
        repository.saveMotherOrder(new MotherOrderRecord(
                "MO20260709001",
                8L,
                8L,
                "AU9999",
                "2026-07-09",
                "顺丰",
                "SF123456789",
                "李寄件",
                "王收货",
                "赵拆包",
                "黄金原料",
                "CUSTOMER",
                true,
                "WH01",
                new BigDecimal("100.000"),
                "件",
                new BigDecimal("520.50"),
                "IN_STOCK",
                "LG001",
                null,
                null,
                null,
                "operator01",
                "EV001",
                null,
                "ACTIVE",
                "2026-07-09 10:00"
        ));
        LabelService service = new LabelService(repository);

        LabelPayload payload = service.generateMotherLabel("MO20260709001");

        assertThat(payload.type()).isEqualTo(LabelType.MOTHER);
        assertThat(payload.primaryCode()).isEqualTo("MO20260709001");
        assertThat(payload.qrContent()).isEqualTo("JG|mother|MO20260709001");
        assertThat(payload.fields()).extracting(LabelField::name)
                .contains("客户", "物料", "数量", "重量", "收货日");
        assertThat(payload.fields()).extracting(LabelField::value)
                .contains("客户#8", "黄金原料", "100 件", "520.5 克", "2026-07-09");
        assertThat(payload.footerLeft()).contains("顺丰").contains("SF123456789");
        assertThat(payload.footerRight()).contains("王收货");
    }

    @Test
    @DisplayName("母单标签：仓储不可用应抛出业务异常")
    void 仓储不可用应抛出业务异常() {
        LabelService service = new LabelService();

        assertThatThrownBy(() -> service.generateMotherLabel("MO001"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("业务单据仓储不可用");
    }

    @Test
    @DisplayName("母单标签：母单不存在应抛出业务异常")
    void 母单不存在应抛出业务异常() {
        LabelService service = new LabelService(new InMemoryBusinessDocumentRepository());

        assertThatThrownBy(() -> service.generateMotherLabel("NOT_EXIST"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未找到结构化母单");
    }

    @Test
    @DisplayName("模板：通用生成默认采用类型默认模板的抬头与打印机设置")
    void 通用生成应采用默认模板抬头与打印设置() {
        InMemoryLabelTemplateRepository templates = new InMemoryLabelTemplateRepository();
        LabelService service = new LabelService(null, templates);
        GenerateLabelCommand command = new GenerateLabelCommand(
                "MOTHER", "MO001", List.of(new LabelField("客户", "客户#8")), null, null, null);

        LabelPayload payload = service.generate(command);

        assertThat(payload.brandTitle()).isEqualTo("珠宝来料加工系统");
        assertThat(payload.templateCode()).isEqualTo("MOTHER-DEFAULT");
        assertThat(payload.printerSetting().widthMm()).isEqualTo(60);
        assertThat(payload.printerSetting().dpi()).isEqualTo(203);
    }

    @Test
    @DisplayName("模板：指定不存在的模板编码应抛出业务异常")
    void 指定不存在模板编码应抛出业务异常() {
        LabelService service = new LabelService(null, new InMemoryLabelTemplateRepository());
        GenerateLabelCommand command = new GenerateLabelCommand(
                "MOTHER", "MO001", List.of(), null, null, "NOT_EXIST");

        assertThatThrownBy(() -> service.generate(command))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("未找到标签模板");
    }

    @Test
    @DisplayName("模板：母单标签按模板字段来源从母单带出取值")
    void 母单标签应按模板字段来源带出取值() {
        InMemoryBusinessDocumentRepository businessRepository = new InMemoryBusinessDocumentRepository();
        businessRepository.saveMotherOrder(new MotherOrderRecord(
                "MO20260709002", 8L, 8L, "AU9999", "2026-07-09", "顺丰", "SF123456789",
                "李寄件", "王收货", "赵拆包", "黄金原料", "CUSTOMER", true, "WH01",
                new BigDecimal("100.000"), "件", new BigDecimal("520.50"), "IN_STOCK", "LG001",
                null, null, null, "operator01", "EV001", null, "ACTIVE", "2026-07-09 10:00"));
        LabelService service = new LabelService(businessRepository, new InMemoryLabelTemplateRepository());

        LabelPayload payload = service.generateMotherLabel("MO20260709002");

        // 默认母单模板字段顺序：客户/物料/数量/重量/收货日
        assertThat(payload.templateCode()).isEqualTo("MOTHER-DEFAULT");
        assertThat(payload.fields()).extracting(LabelField::name)
                .containsExactly("客户", "物料", "数量", "重量", "收货日");
        assertThat(payload.footerLeft()).contains("顺丰");
        assertThat(payload.footerRight()).contains("王收货");
    }
}
