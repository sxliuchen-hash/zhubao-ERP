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
    void 实际确认收货写入的类型与单位进入客户对账() {
        long customer = bind();
        var ledger = new com.zhubao.erp.inventory.infra.InMemoryStockLedgerRepository();
        var documents = new com.zhubao.erp.business.infra.InMemoryBusinessDocumentRepository();
        var operation = new com.zhubao.erp.business.app.BusinessOperationService(new StockLedgerService(ledger), null, documents,
                com.zhubao.erp.business.BusinessWorkFixture.unit());
        operation.confirmInbound(new com.zhubao.erp.business.domain.ConfirmInboundCommand("MD-20261101-0001", customer, null,
                "牙骨", "2026-11-01", "顺丰", "SF-ACTUAL", "客户", "库管", "库管", "测试珠料", "登记表", 1, "1", "原料库",
                new BigDecimal("500"), "颗", new BigDecimal("20"), "库管", "照片证据", "收货", "抛光", "2026-11-30", "恒信珠宝", "actual-receipt-statement"));
        var query = new CustomerStatementCutoffQuery(ledger, identities, new MotherOrderUnitSource(documents));
        var statement = query.statement(portal(), Instant.now().plusSeconds(1));
        assertThat(statement.receivedQuantity()).isEqualByComparingTo("500");
        assertThat(statement.quantities()).singleElement().satisfies(line -> {
            assertThat(line.sourceCode()).isEqualTo("MD-20261101-0001");
            assertThat(line.unit()).isEqualTo("颗");
        });
        assertThat(statement.pending()).noneMatch(line -> line.contains("inbound_confirm"));
        var sources = new com.zhubao.erp.business.infra.InMemoryOutsourcingSourceRepository();
        var bags = new com.zhubao.erp.business.infra.InMemoryActivityBagRepository();
        var custody = new com.zhubao.erp.business.app.OutsourcingDispatchService(sources,
                new com.zhubao.erp.business.infra.InMemoryOutsourcingDispatchRepository(),
                new com.zhubao.erp.business.infra.InMemoryOutsourcingReturnRepository(), bags, null,
                com.zhubao.erp.business.BusinessWorkFixture.unit());
        var stock = new CustomerOwnedStockQuery(ledger, identities, new CustomerOwnedOutsideShares(sources, custody, bags), new MotherOrderUnitSource(documents));
        assertThat(stock.query(portal()).lines()).anySatisfy(line -> assertThat(line.quantity()).isEqualByComparingTo("500"));
    }

    @Test
    void 颗克分列且正式收货与冲正按截止日计算净额() {
        long customerId = bind();
        Instant time = Instant.parse("2026-10-01T00:00:00Z");
        var pieces = owned(customerId, "MD-20261030-0001", StockStatus.RAW_WAITING);
        var grams = owned(customerId, "MD-20261030-0002", StockStatus.RAW_WAITING);
        var unknown = owned(customerId, "MD-20261030-0003", StockStatus.RAW_WAITING);
        var source = entry("actual-inbound", time, "inbound_confirm", null, pieces, "500", "20");
        var reversal = new StockLedgerEntry("reverse-inbound", time.plusSeconds(1), "inbound_reversal", "MD-20261030-0001",
                pieces, null, new BigDecimal("500"), new BigDecimal("20"), "更正", source.code());
        var corrected = entry("corrected-inbound", time.plusSeconds(2), "inbound_confirm", null, pieces, "480", "19");
        var weightReceipt = entry("weight-inbound", time, "inbound_confirm", null, grams, "0.5", "0.5");
        var unknownReceipt = entry("unknown-inbound", time, "inbound_confirm", null, unknown, "7", null);
        var ledger = new FixedLedger(List.of(source, reversal, corrected, weightReceipt, unknownReceipt));
        var query = new CustomerStatementCutoffQuery(ledger, identities,
                mother -> mother.endsWith("0001") ? "颗" : mother.endsWith("0002") ? "克" : "");
        var before = query.statement(portal(), time);
        assertThat(before.quantities()).anySatisfy(group -> {
            assertThat(group.unit()).isEqualTo("颗");
            assertThat(group.receivedQuantity()).isEqualByComparingTo("500");
        });
        var after = query.statement(portal(), time.plusSeconds(3));
        assertThat(after.receivedQuantity()).isNull();
        assertThat(after.quantities()).hasSize(2).anySatisfy(group -> {
            assertThat(group.unit()).isEqualTo("颗");
            assertThat(group.receivedQuantity()).isEqualByComparingTo("480");
        }).anySatisfy(group -> {
            assertThat(group.unit()).isEqualTo("克");
            assertThat(group.receivedQuantity()).isEqualByComparingTo("0.5");
        });
        assertThat(after.pending()).anyMatch(line -> line.contains("数量单位待核"));
        assertThat(after.exportText()).contains("收料,按单位分别核对").contains("\"颗\",480").contains("\"克\",0.5");
        assertThat(ledger.writes).isZero();
    }

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
        CustomerStatementCutoffQuery query = new CustomerStatementCutoffQuery(ledger, identities, code -> "颗");
        CustomerStatementCutoff statement = query.statement(portal(), cutoff);

        assertThat(statement.receivedQuantity()).isEqualByComparingTo("1000");
        assertThat(statement.shippedQuantity()).isEqualByComparingTo("300");
        assertThat(statement.lossQuantity()).isEqualByComparingTo("10");
        assertThat(statement.lossWeight()).isEqualTo("未称");
        assertThat(statement.factoryAndOutside()).contains("本版不显示截止日的在厂和在外");
        assertThat(statement.bills()).isEqualTo("本版不向客户开放账单");
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

    private long bind() {
        identities = new CustomerPortalIdentityService(accounts, bindings, customers,
                new InMemoryCustomerPortalIdentityActionRepository(accounts, bindings, new InMemoryAuthAuditRepository()),
                sessions());
        CustomerProfile saved = customers.saveCustomer(new CustomerProfile(0, "HX", "恒信珠宝", "", "", "", "", true));
        identities.bind(new OperatorContext("系统管理员", RoleCode.ADMIN, DataScope.ALL, false, "", List.of()),
                "portal-a", saved.customerId());
        return saved.customerId();
    }

    private AuthSessionService sessions() {
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

    private CustomerPortalIdentityService identities;
    private final InMemoryAuthUserAccountRepository accounts = new InMemoryAuthUserAccountRepository(new PasswordHasher());
    private final InMemoryCustomerPortalBindingRepository bindings = new InMemoryCustomerPortalBindingRepository();
    private final InMemoryMasterDataRepository customers = new InMemoryMasterDataRepository();

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
