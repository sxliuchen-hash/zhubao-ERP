package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.MobileIntakeCommand;
import com.zhubao.erp.business.domain.MobileIntakeResult;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.business.infra.InMemoryBusinessDocumentRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.infra.InMemoryStockLedgerRepository;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.work.app.MemoryBusinessWorkUnit;
import com.zhubao.erp.work.infra.InMemoryCommandRequestRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MobileIntakeServiceTest {

    private final InMemoryBusinessDocumentRepository documents = new InMemoryBusinessDocumentRepository();
    private final InMemoryStockLedgerRepository stock = new InMemoryStockLedgerRepository();
    private final MemoryBusinessWorkUnit unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
    private final MobileIntakeService service = new MobileIntakeService(
            new BusinessOperationService(new StockLedgerService(stock), null, documents, unit), documents, unit);
    private final OperatorContext receiver = new OperatorContext("收货员 何琴", RoleCode.RECEIVER, DataScope.ALL);

    @Test
    void createsMotherOrderAndRejectsBlankRequirement() {
        assertThatThrownBy(() -> service.intake(command(" ", "2026-10-20"), receiver))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("加工要求不能为空");

        MobileIntakeResult created = service.intake(command("戒托抛光，保留原石", "2026-10-20"), receiver);

        assertThat(created.alreadyExists()).isFalse();
        assertThat(created.motherOrderCode()).matches("MD-\\d{8}-\\d{4}");
        assertThat(created.courierNo()).isEqualTo("SF1048329001");
        assertThat(created.customerName()).isEqualTo("恒信珠宝");
        assertThat(created.processingRequirement()).isEqualTo("戒托抛光，保留原石");
        assertThat(created.qrContent()).isEqualTo("JG|mother|" + created.motherOrderCode());
        assertThat(documents.findMotherOrderByCode(created.motherOrderCode()).orElseThrow().evidenceCode()).isBlank();
        assertThat(stock.findAllLedgers()).hasSize(1);
        assertThat(stock.findAllLedgers().get(0).remark()).doesNotContain("证据：");
        assertThat(service.lookup("sf 1048329001", receiver).found()).isTrue();
        assertThat(service.lookup("sf 1048329001", receiver).motherOrderCode()).isEqualTo(created.motherOrderCode());
    }

    @Test
    void waybillStaysOnCourierFieldAndNotEvidenceCode() {
        assertWaybillNotUsedAsEvidence("SF1234567890123", "evidence-gap-15");
        assertWaybillNotUsedAsEvidence("YT12345678901", "evidence-gap-13");
    }

    @Test
    void sameWaybillCreatesAnotherMotherWithNewRequest() {
        MobileIntakeResult created = service.intake(command("镶嵌主石", "2026-10-20"), receiver);

        MobileIntakeCommand second = new MobileIntakeCommand("SF1048329001", "恒信珠宝", null,
                "另一条要求", "2026-11-01", "18K 金戒托", new BigDecimal("12"),
                new BigDecimal("18.500"), "", "MAT-MOBILE", "件", "intake-second-01");
        MobileIntakeResult again = service.intake(second, receiver);

        assertThat(again.alreadyExists()).isFalse();
        assertThat(again.motherOrderCode()).isNotEqualTo(created.motherOrderCode());
        assertThat(service.lookup("SF1048329001", receiver).candidates()).hasSize(2);
        assertThat(service.lookup("SF1048329001", receiver).motherOrderCode()).isBlank();
        assertThat(documents.listMotherOrders()).hasSize(2);
        assertThat(stock.findAllLedgers()).hasSize(2);
    }

    @Test
    void departmentWithoutCustomerListCannotCreateMother() {
        OperatorContext keeper = new OperatorContext("库管员", RoleCode.WAREHOUSE_KEEPER, DataScope.DEPARTMENT);

        assertThatThrownBy(() -> service.intake(new MobileIntakeCommand(
                "SF1048329002", "恒信珠宝", null, "抛光", "2026-10-20", "戒托",
                new BigDecimal("1"), new BigDecimal("2"), "", "", "件", "dept-intake-01"), keeper))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("客户不在当前账号范围内");
    }

    @Test
    void rejectsCustomerOutsideAccountScope() {
        OperatorContext scoped = new OperatorContext(
                "收货员 何琴",
                RoleCode.RECEIVER,
                DataScope.CUSTOMER_RANGE,
                false,
                "",
                List.of("雅辰饰品")
        );

        assertThatThrownBy(() -> service.intake(command("戒托抛光", "2026-10-20"), scoped))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("客户不在当前账号范围内");
    }

    @Test
    void withoutCourierCreatesMotherAndReplaysByRequestOnly() {
        MobileIntakeCommand delivery = new MobileIntakeCommand("", "恒信珠宝", null,
                "抛光", "2026-10-20", "戒托", new BigDecimal("2"), new BigDecimal("3.5"),
                "", "MAT-1", "件", "delivery-request-01", "CUSTOMER_DELIVERY");
        MobileIntakeResult first = service.intake(delivery, receiver);
        MobileIntakeResult replay = service.intake(delivery, receiver);
        assertThat(first.motherOrderCode()).isEqualTo(replay.motherOrderCode());
        assertThat(documents.listMotherOrders()).hasSize(1);
        assertThat(stock.findAllLedgers()).hasSize(1);
        assertThat(documents.findMotherOrderByCode(first.motherOrderCode()).orElseThrow().expressCompany()).isBlank();
        assertThat(documents.findMotherOrderByCode(first.motherOrderCode()).orElseThrow().courierNo()).isBlank();
    }

    private void assertWaybillNotUsedAsEvidence(String waybill, String requestId) {
        MobileIntakeResult created = service.intake(new MobileIntakeCommand(
                waybill, "恒信珠宝", null, "抛光", "2026-10-20", "戒托",
                new BigDecimal("1"), new BigDecimal("2.5"), "顺丰", "MAT-MOBILE", "件", requestId), receiver);
        MotherOrderRecord saved = documents.findMotherOrderByCode(created.motherOrderCode()).orElseThrow();
        assertThat(saved.courierNo()).isEqualTo(waybill);
        assertThat(saved.evidenceCode()).isBlank();
        String remark = stock.findAllLedgers().stream()
                .filter(entry -> entry.documentCode().equals(created.motherOrderCode()))
                .findFirst()
                .orElseThrow()
                .remark();
        assertThat(remark).contains("快递单号：" + waybill).doesNotContain("证据：");
    }

    private MobileIntakeCommand command(String requirement, String dueDate) {
        return new MobileIntakeCommand(
                "SF1048329001",
                "恒信珠宝",
                null,
                requirement,
                dueDate,
                "18K 金戒托",
                new BigDecimal("12"),
                new BigDecimal("18.500"),
                "顺丰",
                "",
                "件",
                "intake-first-01"
        );
    }
}
