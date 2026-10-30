package com.zhubao.erp.inventory.app;

import com.zhubao.erp.business.domain.CustomerProfile;
import com.zhubao.erp.business.infra.InMemoryMasterDataRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.CustomerStatementCutoff;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.inventory.domain.StockLedgerEntry;
import com.zhubao.erp.inventory.domain.StockLedgerRepository;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.security.app.AuthSessionService;
import com.zhubao.erp.security.app.CustomerPortalIdentityService;
import com.zhubao.erp.security.app.NavigationMenuService;
import com.zhubao.erp.security.app.PasswordExpiryPolicy;
import com.zhubao.erp.security.app.PasswordHasher;
import com.zhubao.erp.security.app.PasswordPolicy;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.infra.InMemoryAuthAuditRepository;
import com.zhubao.erp.security.infra.InMemoryAuthLoginSessionRepository;
import com.zhubao.erp.security.infra.InMemoryAuthUserAccountRepository;
import com.zhubao.erp.security.infra.InMemoryCustomerPortalBindingRepository;
import com.zhubao.erp.security.infra.InMemoryCustomerPortalIdentityActionRepository;
import com.zhubao.erp.security.infra.InMemoryNavigationMenuRepository;
import com.zhubao.erp.work.domain.WorkSession;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 截止日对账只读数量。账单和在厂在外未核定，不填 0，也不出库出账。 */
class CustomerStatementCutoffTest {

    @Test
    void 截止日后的收料不计入且导出与页面同口径() {
        long customerId = bind();
        Instant cutoff = Instant.parse("2026-10-03T00:00:00Z");
        FixedLedger ledger = new FixedLedger(List.of(
                entry("in-1", Instant.parse("2026-10-01T00:00:00Z"), "inbound", null,
                        owned(customerId, "MD-20261030-0001", StockStatus.RAW_WAITING), "1000", "7.260"),
                entry("ship-1", Instant.parse("2026-10-02T00:00:00Z"), "shipment",
                        owned(customerId, "MD-20261030-0001", StockStatus.FINISHED),
                        owned(customerId, "MD-20261030-0001", StockStatus.SHIPPED), "300", "1.000"),
                entry("loss-1", Instant.parse("2026-10-02T01:00:00Z"), "process_loss",
                        owned(customerId, "MD-20261030-0001", StockStatus.WIP), null, "10", null),
                entry("later-1", Instant.parse("2026-10-04T00:00:00Z"), "inbound", null,
                        owned(customerId, "MD-20261030-0001", StockStatus.RAW_WAITING), "50", "0.400"),
                entry("company-1", Instant.parse("2026-10-01T00:00:00Z"), "inbound", null,
                        new StockDimension(OwnerType.COMPANY, null, customerId, "MD-20261030-0001", "",
                                "胶水", "WH", StockStatus.RAW_WAITING, ""), "70", "0"),
                entry("sample-1", Instant.parse("2026-10-01T00:00:00Z"), "inbound", null,
                        owned(customerId, "DY-20261030-0001", StockStatus.RAW_WAITING), "5", "0.100")
        ));
        CustomerStatementCutoffQuery query = new CustomerStatementCutoffQuery(ledger, identities);
        CustomerStatementCutoff statement = query.statement(portal(), cutoff);

        assertThat(statement.receivedQuantity()).isEqualByComparingTo("1000");
        assertThat(statement.shippedQuantity()).isEqualByComparingTo("300");
        assertThat(statement.lossQuantity()).isEqualByComparingTo("10");
        assertThat(statement.lossWeight()).isEqualTo("未称");
        assertThat(statement.factoryAndOutside()).contains("尚未核定");
        assertThat(statement.bills()).contains("尚未核定");
        assertThat(statement.afterCutoff()).anyMatch(line -> line.contains("50") && line.contains("未计入"));
        assertThat(statement.pending()).anyMatch(line -> line.contains("打样"));
        assertThat(statement.exportText()).contains("收料,1000");
        assertThat(statement.exportText()).contains("出厂,300");
        assertThat(statement.exportText()).contains("损耗,10");
        assertThat(statement.exportText()).contains("未计入本次截止日");
        assertThat(statement.exportText()).doesNotContain("账单,0");
        assertThat(ledger.writes).isZero();
        assertThatThrownBy(() -> query.statement(portal(), null)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> query.acknowledge(portal(), cutoff))
                .isInstanceOf(BusinessException.class).hasMessageContaining("不能自动出库或出账");
        assertThat(ledger.writes).isZero();
    }

    private static StockLedgerEntry entry(String code, Instant time, String type, StockDimension from, StockDimension to,
                                          String quantity, String weight) {
        return new StockLedgerEntry(code, time, type, code, from, to,
                new BigDecimal(quantity), weight == null ? null : new BigDecimal(weight), "");
    }

    private static StockDimension owned(long customerId, String mother, StockStatus status) {
        return new StockDimension(OwnerType.CUSTOMER, customerId, customerId, mother, "", "牙骨", "WH", status, "");
    }

    private static long bind() {
        identities = new CustomerPortalIdentityService(accounts, bindings, customers,
                new InMemoryCustomerPortalIdentityActionRepository(accounts, bindings, new InMemoryAuthAuditRepository()),
                sessions());
        CustomerProfile saved = customers.saveCustomer(new CustomerProfile(0, "HX", "恒信珠宝", "", "", "", "", true));
        identities.bind(new OperatorContext("系统管理员", RoleCode.ADMIN, DataScope.ALL, false, "", List.of()),
                "portal-a", saved.customerId());
        return saved.customerId();
    }

    private static AuthSessionService sessions() {
        AuthSessionService sessions = new AuthSessionService(
                new NavigationMenuService(new InMemoryNavigationMenuRepository()),
                new InMemoryAuthAuditRepository(), accounts, new PasswordHasher(), new PasswordExpiryPolicy(),
                new PasswordPolicy(), null, new InMemoryAuthLoginSessionRepository(), new PermissionGuard());
        sessions.setCustomerPortalBindings(bindings);
        return sessions;
    }

    private static OperatorContext portal() {
        return new OperatorContext("恒信珠宝", RoleCode.CUSTOMER_PORTAL, DataScope.CUSTOMER_RANGE, false, "",
                List.of(), List.of(), "portal-a", "", "");
    }

    private static CustomerPortalIdentityService identities;
    private static final InMemoryAuthUserAccountRepository accounts = new InMemoryAuthUserAccountRepository(new PasswordHasher());
    private static final InMemoryCustomerPortalBindingRepository bindings = new InMemoryCustomerPortalBindingRepository();
    private static final InMemoryMasterDataRepository customers = new InMemoryMasterDataRepository();

    private static final class FixedLedger implements StockLedgerRepository {
        private final List<StockLedgerEntry> rows;
        private int writes;

        private FixedLedger(List<StockLedgerEntry> rows) {
            this.rows = rows;
        }

        @Override
        public StockLedgerEntry save(StockLedgerEntry entry) {
            writes++;
            throw new BusinessException("对账不能写库存");
        }

        @Override
        public StockLedgerEntry save(StockLedgerEntry entry, WorkSession session) {
            writes++;
            throw new BusinessException("对账不能写库存");
        }

        @Override
        public List<StockLedgerEntry> findAllLedgers() {
            return List.copyOf(rows);
        }

        @Override
        public List<StockLedgerEntry> findLedgersByCustomers(Set<Long> customerIds, Set<String> motherOrderCodes) {
            return List.of();
        }

        @Override
        public List<com.zhubao.erp.inventory.domain.StockBalance> findAllBalances() {
            return List.of();
        }

        @Override
        public com.zhubao.erp.inventory.domain.StockBalance findBalance(com.zhubao.erp.inventory.domain.StockDimension dimension) {
            throw new BusinessException("对账不能改余额");
        }

        @Override
        public void clear() {
            writes++;
            throw new BusinessException("对账不能清库存");
        }
    }
}
