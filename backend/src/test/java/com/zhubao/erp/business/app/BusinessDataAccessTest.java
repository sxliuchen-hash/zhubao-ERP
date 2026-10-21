package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ChildWorkOrderRecord;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.business.domain.OutboundOrderRecord;
import com.zhubao.erp.business.domain.ProcessStationHandoff;
import com.zhubao.erp.business.domain.QualityRequirementCheck;
import com.zhubao.erp.business.infra.InMemoryBusinessDocumentRepository;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.inventory.domain.StockTransferCommand;
import com.zhubao.erp.inventory.infra.InMemoryStockLedgerRepository;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class BusinessDataAccessTest {

    private final InMemoryBusinessDocumentRepository documents = new InMemoryBusinessDocumentRepository();
    private final StockLedgerService stocks = new StockLedgerService(new InMemoryStockLedgerRepository());
    private final BusinessDataAccess access = new BusinessDataAccess(documents, stocks);

    @Test
    void resolvesMotherChildOutboundQualityAndReworkByRealCustomer() {
        seed();
        documents.saveChildWorkOrder(new ChildWorkOrderRecord("ZD-ACCESS", "MD-ACCESS", 101L, "真实客户", "抛光", "2026-10-20",
                "MAT", "戒托", BigDecimal.ONE, "件", BigDecimal.ONE, "RAW", "抛光", "verify", "ledger", "开单员", "2026-10-10"));
        documents.saveOutboundOrder(new OutboundOrderRecord("OUT-ACCESS", "MD-ACCESS", "ZD-ACCESS", 101L, 101L, "MAT", "RAW", "OUT",
                "2026-10-10", "", BigDecimal.ONE, "件", BigDecimal.ONE, "", "", "", "", "", null, "", "发货员", "", "", "SHIPPED", "", "2026-10-10"));
        documents.saveQualityRequirementCheck(new QualityRequirementCheck("QC-ACCESS", "ZD-ACCESS", "MD-ACCESS", "质检员",
                "不合格", true, "", "FG-ACCESS", "2026-10-10", List.of()));
        OperatorContext allowed = context("查看人", RoleCode.RECEIVER, DataScope.CUSTOMER_RANGE, "真实客户");
        OperatorContext denied = context("查看人", RoleCode.RECEIVER, DataScope.CUSTOMER_RANGE, "102");
        for (String code : List.of("MD-ACCESS", "ZD-ACCESS", "OUT-ACCESS", "QC-ACCESS", "FG-ACCESS")) {
            assertThat(access.canRead(allowed, code)).as(code).isTrue();
            assertThat(access.canRead(denied, code)).as(code).isFalse();
        }
        assertThat(access.canRead(allowed, "不存在")).isFalse();
        assertThat(access.canRead(context("空授权", RoleCode.ADMIN, DataScope.CUSTOMER_RANGE), "MD-ACCESS")).isFalse();
    }

    @Test
    void appliesSelfAssociationAndDepartmentResponsibilitiesAfterCustomerFilter() {
        seed();
        StockDimension dimension = dimension("MD-ACCESS", 101L);
        assertThat(access.canRead(context("开单员", RoleCode.OPERATOR, DataScope.SELF), dimension)).isTrue();
        assertThat(access.canRead(context("陌生人", RoleCode.OPERATOR, DataScope.DEPARTMENT), dimension)).isFalse();
        assertThat(access.canRead(context("库管", RoleCode.WAREHOUSE_KEEPER, DataScope.DEPARTMENT), dimension)).isFalse();
        assertThat(access.canRead(context("库管", RoleCode.WAREHOUSE_KEEPER, DataScope.DEPARTMENT, "102"), dimension)).isFalse();
        assertThat(access.canRead(context("库管", RoleCode.WAREHOUSE_KEEPER, DataScope.DEPARTMENT), dimension("MD-UNKNOWN", 101L))).isFalse();
        documents.saveProcessStationHandoff(new ProcessStationHandoff("MD-ACCESS", "抛光", "加工人", "", "2026-10-10"));
        assertThat(access.canRead(context("加工人", RoleCode.OPERATOR, DataScope.SELF), dimension)).isTrue();
    }

    @Test
    void resolvesLegacyLedgerCodesAndRejectsAmbiguousOwnership() {
        seed();
        stockEvent("LEGACY-ACCESS", "MD-ACCESS", 101L);
        OperatorContext allowed = context("查看人", RoleCode.RECEIVER, DataScope.CUSTOMER_RANGE, "101");
        assertThat(access.canRead(allowed, "LEGACY-ACCESS")).isTrue();
        documents.saveMotherOrder(mother("MD-OTHER", 102L));
        stockEvent("LEGACY-ACCESS", "MD-OTHER", 102L);
        assertThat(access.canRead(allowed, "LEGACY-ACCESS")).isFalse();
    }

    @Test
    void refusesChildWhenItsCustomerDiffersFromTheMother() {
        seed();
        documents.saveChildWorkOrder(new ChildWorkOrderRecord("ZD-OTHER", "MD-ACCESS", 102L, "另一客户", "抛光", "2026-10-20",
                "MAT", "戒托", BigDecimal.ONE, "件", BigDecimal.ONE, "RAW", "抛光", "verify-2", "ledger", "开单员", "2026-10-10"));
        OperatorContext company = context("管理员", RoleCode.ADMIN, DataScope.ALL);
        OperatorContext motherSide = context("查看人", RoleCode.RECEIVER, DataScope.CUSTOMER_RANGE, "101");
        OperatorContext childSide = context("查看人", RoleCode.RECEIVER, DataScope.CUSTOMER_RANGE, "102");
        assertThat(access.canRead(company, "MD-ACCESS")).isTrue();
        assertThat(access.canRead(company, "ZD-OTHER")).isFalse();
        assertThat(access.canRead(motherSide, "ZD-OTHER")).isFalse();
        assertThat(access.canRead(childSide, "ZD-OTHER")).isFalse();

        documents.saveMotherOrder(mother("SAME-1", 101L));
        documents.saveChildWorkOrder(new ChildWorkOrderRecord("SAME-1", "MD-ACCESS", 102L, "另一客户", "抛光", "2026-10-20",
                "MAT", "戒托", BigDecimal.ONE, "件", BigDecimal.ONE, "RAW", "抛光", "verify-3", "ledger", "开单员", "2026-10-10"));
        assertThat(access.canRead(company, "SAME-1")).isFalse();
    }

    @Test
    void genericLabelScopeChecksStoredOrdersOnly() {
        seed();
        OperatorContext allowed = context("查看人", RoleCode.RECEIVER, DataScope.CUSTOMER_RANGE, "101");
        OperatorContext denied = context("查看人", RoleCode.RECEIVER, DataScope.CUSTOMER_RANGE, "102");
        assertThatCode(() -> access.assertCanReadIfStored(allowed, "MD-ACCESS")).doesNotThrowAnyException();
        assertThatThrownBy(() -> access.assertCanReadIfStored(denied, "MD-ACCESS"))
                .isInstanceOf(PermissionDeniedException.class);
        assertThatCode(() -> access.assertCanReadIfStored(denied, "CK-NOT-STORED")).doesNotThrowAnyException();
        documents.saveChildWorkOrder(new ChildWorkOrderRecord("ZD-OTHER", "MD-ACCESS", 102L, "另一客户", "抛光", "2026-10-20",
                "MAT", "戒托", BigDecimal.ONE, "件", BigDecimal.ONE, "RAW", "抛光", "verify-4", "ledger", "开单员", "2026-10-10"));
        assertThatThrownBy(() -> access.assertCanReadIfStored(context("管理员", RoleCode.ADMIN, DataScope.ALL), "ZD-OTHER"))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void checksCommandCustomerAgainstStoredMotherAndAllowsUnknownOnlyForCompanyScope() {
        seed();
        OperatorContext allowed = context("查看人", RoleCode.RECEIVER, DataScope.CUSTOMER_RANGE, "真实客户");
        assertThatCode(() -> access.assertDocumentCustomer(allowed, 101L, "MD-ACCESS")).doesNotThrowAnyException();
        assertThatThrownBy(() -> access.assertDocumentCustomer(allowed, 102L, "MD-ACCESS")).isInstanceOf(PermissionDeniedException.class);
        assertThatThrownBy(() -> access.assertDocumentCustomer(allowed, 101L, "MD-UNKNOWN")).isInstanceOf(PermissionDeniedException.class);
        assertThatCode(() -> access.assertDocumentCustomer(context("管理", RoleCode.ADMIN, DataScope.ALL), 101L, "MD-UNKNOWN"))
                .doesNotThrowAnyException();
    }

    private void seed() { documents.saveMotherOrder(mother("MD-ACCESS", 101L)); }

    private MotherOrderRecord mother(String code, long customerId) {
        return new MotherOrderRecord(code, customerId, customerId, "MAT", "2026-10-10", "", "SF-" + code, "", "收货员", "拆包员", "戒托",
                "CUSTOMER", true, "RAW", BigDecimal.TEN, "件", BigDecimal.TEN, "RAW_WAITING", "", "", null, "", "开单员", "", "",
                "INBOUND_CONFIRMED", "2026-10-10", "抛光", "2026-10-20", "真实客户");
    }

    private OperatorContext context(String operator, RoleCode role, DataScope scope, String... customers) {
        return new OperatorContext(operator, role, scope, false, "", List.of(customers));
    }

    private StockDimension dimension(String motherCode, long customerId) {
        return new StockDimension(OwnerType.CUSTOMER, customerId, customerId, motherCode, null, "MAT", "RAW", StockStatus.RAW_WAITING);
    }

    private void stockEvent(String businessCode, String motherCode, long customerId) {
        stocks.transfer(new StockTransferCommand("customer_return", businessCode, null, dimension(motherCode, customerId), BigDecimal.ONE, BigDecimal.ONE, "归属测试"));
    }
}
