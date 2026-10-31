package com.zhubao.erp.inventory.app;

import com.zhubao.erp.business.app.ActivityBagService;
import com.zhubao.erp.business.app.BagSplitService;
import com.zhubao.erp.business.app.OutsourcingDispatchService;
import com.zhubao.erp.business.domain.ActivityBag;
import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.business.domain.OutsourcingDispatch;
import com.zhubao.erp.business.domain.OutsourcingTask;
import com.zhubao.erp.business.infra.InMemoryActivityBagRepository;
import com.zhubao.erp.business.infra.InMemoryOutsourcingDispatchRepository;
import com.zhubao.erp.business.infra.InMemoryOutsourcingReturnRepository;
import com.zhubao.erp.business.infra.InMemoryOutsourcingSourceRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.domain.CustomerOwnedStockView;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockBalance;
import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.inventory.domain.StockLedgerEntry;
import com.zhubao.erp.inventory.domain.StockLedgerRepository;
import com.zhubao.erp.inventory.domain.StockMeasurement;
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
import com.zhubao.erp.security.error.PermissionDeniedException;
import com.zhubao.erp.security.infra.InMemoryAuthAuditRepository;
import com.zhubao.erp.security.infra.InMemoryAuthLoginSessionRepository;
import com.zhubao.erp.security.infra.InMemoryAuthUserAccountRepository;
import com.zhubao.erp.security.infra.InMemoryCustomerPortalBindingRepository;
import com.zhubao.erp.security.infra.InMemoryCustomerPortalIdentityActionRepository;
import com.zhubao.erp.security.infra.InMemoryNavigationMenuRepository;
import com.zhubao.erp.work.app.MemoryBusinessWorkUnit;
import com.zhubao.erp.work.domain.WorkSession;
import com.zhubao.erp.work.infra.InMemoryCommandRequestRepository;
import com.zhubao.erp.business.domain.CustomerProfile;
import com.zhubao.erp.business.infra.InMemoryMasterDataRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** 客户自有库存只按权属客户读取。外协不重复计入，公司料和未知重量都不写成客户的零。 */
class CustomerOwnedStockQueryTest {

    @Test
    void 按权属汇总且外协不重复加总() {
        long customerId = bindPortal();
        FixedLedger ledger = new FixedLedger(List.of(
                balance(customerId, customerId, "MD-20261030-0001", "牙骨", StockStatus.RAW_WAITING, "400", "7.260"),
                balance(customerId, customerId, "MD-20261030-0001", "牙骨", StockStatus.WIP, "290", "2.000"),
                balance(customerId, customerId, "MD-20261030-0001", "牙骨", StockStatus.FROZEN, "10", "0.100"),
                balance(customerId, customerId, "MD-20261030-0001", "牙骨", StockStatus.SHIPPED, "300", "1.000"),
                balance(customerId, customerId, "MD-20261030-0002", "紫金鼠", StockStatus.RAW_WAITING, "3", null),
                balance(2L, customerId, "MD-20261030-0003", "牙骨", StockStatus.WIP, "8", "0.200"),
                balance(2L, 2L, "MD-20261030-0009", "牙骨", StockStatus.RAW_WAITING, "9", "0.300"),
                new StockBalance(new StockDimension(OwnerType.COMPANY, null, customerId, "MD-20261030-0001", "",
                        "胶水", "WH", StockStatus.RAW_WAITING, ""), new BigDecimal("70"), new BigDecimal("0")),
                balance(customerId, customerId, "DY-20261030-0001", "样珠", StockStatus.WIP, "5", "0.500"),
                balance(customerId, customerId, "GJ-20261030-0001", "足金", StockStatus.WIP, "2", "1.200")
        ));
        CustomerOwnedStockQuery query = query(ledger, outside(customerId));
        CustomerOwnedStockView view = query.query(portal(customerId));

        assertThat(view.customerId()).isEqualTo(customerId);
        assertThat(line(view, "牙骨", "颗", CustomerOwnedStockView.Bucket.CONSIGNMENT).quantity()).isEqualByComparingTo("400");
        assertThat(line(view, "牙骨", "颗", CustomerOwnedStockView.Bucket.CONSIGNMENT).weightRead())
                .isEqualTo(CustomerOwnedStockView.WeightRead.KNOWN);
        assertThat(line(view, "牙骨", "颗", CustomerOwnedStockView.Bucket.CONSIGNMENT).knownWeight()).isEqualByComparingTo("7.260");
        assertThat(line(view, "牙骨", "颗", CustomerOwnedStockView.Bucket.IN_FACTORY).quantity()).isEqualByComparingTo("245");
        assertThat(line(view, "牙骨", "颗", CustomerOwnedStockView.Bucket.OUTSOURCING).quantity()).isEqualByComparingTo("45");
        assertThat(line(view, "牙骨", "颗", CustomerOwnedStockView.Bucket.IN_FACTORY).knownWeight()).isNull();
        assertThat(line(view, "牙骨", "颗", CustomerOwnedStockView.Bucket.OUTSOURCING).weightRead())
                .isEqualTo(CustomerOwnedStockView.WeightRead.UNKNOWN);
        assertThat(line(view, "牙骨", "颗", CustomerOwnedStockView.Bucket.OUTSOURCING).knownWeight()).isNull();
        assertThat(line(view, "牙骨", "颗", CustomerOwnedStockView.Bucket.QUALITY).quantity()).isEqualByComparingTo("10");
        assertThat(line(view, "牙骨", "颗", CustomerOwnedStockView.Bucket.SHIPPED).quantity()).isEqualByComparingTo("300");
        assertThat(line(view, "紫金鼠", "串", CustomerOwnedStockView.Bucket.CONSIGNMENT).quantity()).isEqualByComparingTo("3");
        assertThat(line(view, "紫金鼠", "串", CustomerOwnedStockView.Bucket.CONSIGNMENT).knownWeight()).isNull();
        assertThat(view.lines()).noneMatch(item -> "胶水".equals(item.materialCode()) || item.quantity().compareTo(new BigDecimal("8")) == 0
                || item.quantity().compareTo(new BigDecimal("9")) == 0 || "样珠".equals(item.materialCode())
                || "足金".equals(item.materialCode()));
        BigDecimal custody = view.lines().stream()
                .filter(item -> "牙骨".equals(item.materialCode()) && item.bucket() != CustomerOwnedStockView.Bucket.SHIPPED)
                .map(CustomerOwnedStockView.Line::quantity)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        assertThat(custody).isEqualByComparingTo("700");
        assertThat(view.sources()).anySatisfy(source -> {
            assertThat(source.kind()).isEqualTo(BusinessKind.SAMPLING);
            assertThat(source.state()).isEqualTo("NOT_IMPLEMENTED");
            assertThat(source.withheld()).isTrue();
        });
        assertThat(view.sources()).anySatisfy(source -> {
            assertThat(source.kind()).isEqualTo(BusinessKind.GOLDWORK);
            assertThat(source.state()).isEqualTo("NOT_IMPLEMENTED");
            assertThat(source.withheld()).isTrue();
        });
        assertThat(ledger.writes).isZero();
        var realLedger = new com.zhubao.erp.inventory.infra.InMemoryStockLedgerRepository();
        var stock = new StockLedgerService(realLedger);
        var dimension = new StockDimension(OwnerType.CUSTOMER, customerId, customerId,
                "MD-20261030-0002", "", "复核料", "WH", StockStatus.WIP);
        stock.transfer(new com.zhubao.erp.inventory.domain.StockTransferCommand("inbound_confirm", "RF51-IN", null,
                dimension, new BigDecimal("200"), new BigDecimal("20"), "原称重"));
        new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository()).execute(
                new com.zhubao.erp.work.domain.CommandCall("rf51-query-loss", "clerk", "OUTSIDE_LOSS", "BAG", "BAG-RF51", "5"),
                session -> { stock.postUnweighedLoss("RF51-RETURN", dimension, new BigDecimal("5"), session); return "RF51-RETURN"; });
        var pendingView = new CustomerOwnedStockQuery(realLedger, identities, outside(customerId), code -> "颗")
                .query(portal(customerId));
        assertThat(pendingView.lines()).singleElement().satisfies(item -> {
            assertThat(item.quantity()).isEqualByComparingTo("195");
            assertThat(item.weightRead()).isEqualTo(CustomerOwnedStockView.WeightRead.UNKNOWN);
            assertThat(item.knownWeight()).isNull();
            assertThat(item.reconciliationPending()).isTrue();
        });
        assertThatThrownBy(() -> query.query(new OperatorContext("财务", RoleCode.FINANCE_STATISTICIAN, DataScope.ALL,
                false, "", List.of(), List.of(), "finance", "", "")))
                .isInstanceOf(PermissionDeniedException.class);
    }

    private static CustomerOwnedStockView.Line line(CustomerOwnedStockView view, String material, String unit,
                                                    CustomerOwnedStockView.Bucket bucket) {
        return view.lines().stream()
                .filter(item -> material.equals(item.materialCode()) && unit.equals(item.unit()) && item.bucket() == bucket)
                .findFirst().orElseThrow();
    }

    private static CustomerOwnedStockQuery query(FixedLedger ledger, CustomerOwnedOutsideShares outside) {
        return new CustomerOwnedStockQuery(ledger, identities, outside, code -> switch (code) {
            case "MD-20261030-0001" -> "颗";
            case "MD-20261030-0002" -> "串";
            default -> "";
        });
    }

    private static CustomerOwnedOutsideShares outside(long customerId) {
        InMemoryOutsourcingSourceRepository sources = new InMemoryOutsourcingSourceRepository();
        InMemoryOutsourcingDispatchRepository dispatches = new InMemoryOutsourcingDispatchRepository();
        InMemoryActivityBagRepository bags = new InMemoryActivityBagRepository();
        String target = "MD-20261030-0001";
        String bagId = "BAG-OWN-1";
        bags.save(new ActivityBag(bagId, target, "牙骨", "REQ-1", "LINE-1", "", false,
                new BigDecimal("290"), StockMeasurement.known(new BigDecimal("2.000")), 1), 0, null);
        String taskId = "OT0123456789abcdef0123456789abcdef";
        sources.saveTask(new OutsourcingTask(taskId, "task-owned-1", "WX-POLISH-01", "甲抛光",
                BusinessKind.PROCESSING, target, "POLISH_BEAD", bagId, OwnerType.CUSTOMER,
                Long.toString(customerId), 1), null);
        dispatches.save(new OutsourcingDispatch("OD0123456789abcdef0123456789abcdef", "dispatch-owned-1",
                taskId, "WX-POLISH-01", bagId, bagId, new BigDecimal("45"), "LINE-1",
                OwnerType.CUSTOMER, Long.toString(customerId), 1), null);
        MemoryBusinessWorkUnit unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        OutsourcingDispatchService custody = new OutsourcingDispatchService(sources, dispatches,
                new InMemoryOutsourcingReturnRepository(), bags,
                new BagSplitService(bags, new ActivityBagService(bags), unit), unit);
        return new CustomerOwnedOutsideShares(sources, custody, bags);
    }

    private static StockBalance balance(long ownerCustomerId, long customerId, String mother, String material,
                                        StockStatus status, String quantity, String weight) {
        return new StockBalance(new StockDimension(OwnerType.CUSTOMER, ownerCustomerId, customerId, mother, "",
                material, "WH", status, ""), new BigDecimal(quantity),
                weight == null ? null : new BigDecimal(weight));
    }

    private static OperatorContext portal(long customerId) {
        return new OperatorContext("恒信珠宝", RoleCode.CUSTOMER_PORTAL, DataScope.CUSTOMER_RANGE, false, "",
                List.of(Long.toString(customerId)), List.of(), "portal-a", "", "");
    }

    private static long bindPortal() {
        identities = new CustomerPortalIdentityService(accounts, bindings, customers,
                new InMemoryCustomerPortalIdentityActionRepository(accounts, bindings, new InMemoryAuthAuditRepository()),
                sessions());
        var saved = customers.saveCustomer(new com.zhubao.erp.business.domain.CustomerProfile(
                0, "HX", "恒信珠宝", "", "", "", "", true));
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

    private static CustomerPortalIdentityService identities;
    private static final InMemoryAuthUserAccountRepository accounts = new InMemoryAuthUserAccountRepository(new PasswordHasher());
    private static final InMemoryCustomerPortalBindingRepository bindings = new InMemoryCustomerPortalBindingRepository();
    private static final InMemoryMasterDataRepository customers = new InMemoryMasterDataRepository();

    private static final class FixedLedger implements StockLedgerRepository {
        private final List<StockBalance> rows;
        private int writes;

        private FixedLedger(List<StockBalance> rows) {
            this.rows = new ArrayList<>(rows);
        }

        @Override
        public StockLedgerEntry save(StockLedgerEntry entry) {
            writes++;
            throw new BusinessException("查询不能写库存");
        }

        @Override
        public StockLedgerEntry save(StockLedgerEntry entry, WorkSession session) {
            writes++;
            throw new BusinessException("查询不能写库存");
        }

        @Override
        public List<StockLedgerEntry> findAllLedgers() {
            return List.of();
        }

        @Override
        public List<StockLedgerEntry> findLedgersByCustomers(Set<Long> customerIds, Set<String> motherOrderCodes) {
            return List.of();
        }

        @Override
        public List<StockBalance> findAllBalances() {
            return List.copyOf(rows);
        }

        @Override
        public StockBalance findBalance(StockDimension dimension) {
            throw new BusinessException("查询不能改读单条余额");
        }

        @Override
        public void clear() {
            writes++;
            throw new BusinessException("查询不能清库存");
        }
    }
}
