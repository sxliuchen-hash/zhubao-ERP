package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.MobileIntakeCommand;
import com.zhubao.erp.business.domain.MobileIntakeResult;
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
        assertThat(stock.findAllLedgers()).hasSize(1);
        assertThat(service.lookup("sf 1048329001").found()).isTrue();
        assertThat(service.lookup("sf 1048329001").motherOrderCode()).isEqualTo(created.motherOrderCode());
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
        assertThat(service.lookup("SF1048329001").candidates()).hasSize(2);
        assertThat(service.lookup("SF1048329001").motherOrderCode()).isBlank();
        assertThat(documents.listMotherOrders()).hasSize(2);
        assertThat(stock.findAllLedgers()).hasSize(2);
    }

    @Test
    void departmentWithoutCustomerListCanCreateMother() {
        OperatorContext keeper = new OperatorContext("库管员", RoleCode.WAREHOUSE_KEEPER, DataScope.DEPARTMENT);

        MobileIntakeResult created = service.intake(new MobileIntakeCommand(
                "SF1048329002", "恒信珠宝", null, "抛光", "2026-10-20", "戒托",
                new BigDecimal("1"), new BigDecimal("2"), "", "", "件", "dept-intake-01"), keeper);

        assertThat(created.customerName()).isEqualTo("恒信珠宝");
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
