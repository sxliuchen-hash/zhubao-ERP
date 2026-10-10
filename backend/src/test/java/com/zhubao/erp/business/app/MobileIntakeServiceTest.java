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
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MobileIntakeServiceTest {

    private final InMemoryBusinessDocumentRepository documents = new InMemoryBusinessDocumentRepository();
    private final InMemoryStockLedgerRepository stock = new InMemoryStockLedgerRepository();
    private final MobileIntakeService service = new MobileIntakeService(
            new BusinessOperationService(new StockLedgerService(stock), null, documents),
            documents
    );
    private final OperatorContext receiver = new OperatorContext("收货员 何琴", RoleCode.RECEIVER, DataScope.ALL);

    @Test
    void createsMotherOrderAndRejectsBlankRequirement() {
        assertThatThrownBy(() -> service.intake(command(" ", "2026-10-20"), receiver))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("加工要求不能为空");

        MobileIntakeResult created = service.intake(command("戒托抛光，保留原石", "2026-10-20"), receiver);

        assertThat(created.alreadyExists()).isFalse();
        assertThat(created.motherOrderCode()).startsWith("MD-");
        assertThat(created.courierNo()).isEqualTo("SF1048329001");
        assertThat(created.customerName()).isEqualTo("恒信珠宝");
        assertThat(created.processingRequirement()).isEqualTo("戒托抛光，保留原石");
        assertThat(created.qrContent()).isEqualTo("JG|mother|" + created.motherOrderCode());
        assertThat(stock.findAllLedgers()).hasSize(1);
        assertThat(service.lookup("sf 1048329001").found()).isTrue();
        assertThat(service.lookup("sf 1048329001").motherOrderCode()).isEqualTo(created.motherOrderCode());
    }

    @Test
    void opensExistingMotherOrderWhenCourierIsScannedAgain() {
        MobileIntakeResult created = service.intake(command("镶嵌主石", "2026-10-20"), receiver);

        MobileIntakeResult again = service.intake(command("另一条要求", "2026-11-01"), receiver);

        assertThat(again.alreadyExists()).isTrue();
        assertThat(again.motherOrderCode()).isEqualTo(created.motherOrderCode());
        assertThat(again.processingRequirement()).isEqualTo("镶嵌主石");
        assertThat(documents.listMotherOrders()).hasSize(1);
        assertThat(stock.findAllLedgers()).hasSize(1);
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
                "件"
        );
    }
}
