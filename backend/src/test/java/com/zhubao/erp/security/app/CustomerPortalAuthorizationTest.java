package com.zhubao.erp.security.app;

import com.zhubao.erp.business.app.ActivityBagService;
import com.zhubao.erp.business.app.BagSplitService;
import com.zhubao.erp.business.app.OutsourcingDispatchService;
import com.zhubao.erp.business.domain.CustomerProfile;
import com.zhubao.erp.business.infra.InMemoryActivityBagRepository;
import com.zhubao.erp.business.infra.InMemoryMasterDataRepository;
import com.zhubao.erp.business.infra.InMemoryOutsourcingDispatchRepository;
import com.zhubao.erp.business.infra.InMemoryOutsourcingReturnRepository;
import com.zhubao.erp.business.infra.InMemoryOutsourcingSourceRepository;
import com.zhubao.erp.document.app.CustomerEvidenceAccessService;
import com.zhubao.erp.document.domain.EvidenceAttachment;
import com.zhubao.erp.document.infra.InMemoryCustomerEvidenceGrantRepository;
import com.zhubao.erp.document.infra.InMemoryDocumentManagementRepository;
import com.zhubao.erp.inventory.app.CustomerOwnedOutsideShares;
import com.zhubao.erp.inventory.app.CustomerOwnedStockQuery;
import com.zhubao.erp.inventory.app.CustomerStatementCutoffQuery;
import com.zhubao.erp.inventory.domain.CustomerOwnedStockView;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockBalance;
import com.zhubao.erp.inventory.domain.StockDimension;
import com.zhubao.erp.inventory.domain.StockLedgerEntry;
import com.zhubao.erp.inventory.domain.StockLedgerRepository;
import com.zhubao.erp.inventory.domain.StockStatus;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.PermissionCode;
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
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 客户入口只读自己获准的范围。打样和金工未实现时不返回空成功，也不扩大内部或扫码权限。
 */
class CustomerPortalAuthorizationTest {

    @Test
    void 同客户只读获准范围且停用后不能再读() {
        long customerA = bind("portal-a", "恒信珠宝", "HX");
        long customerB = bind("portal-b", "雅辰饰品", "YC");
        Ledger ledger = new Ledger(List.of(
                balance(customerA, "MD-20261030-0001", "牙骨", "120"),
                balance(customerA, "DY-20261030-0001", "样珠", "5"),
                balance(customerA, "GJ-20261030-0001", "足金", "2"),
                balance(customerB, "MD-20261030-0002", "紫金鼠", "9"),
                new StockBalance(new StockDimension(OwnerType.COMPANY, null, customerA, "MD-20261030-0001", "",
                        "胶水", "WH", StockStatus.RAW_WAITING, ""), new BigDecimal("70"), BigDecimal.ONE)
        ), List.of(
                movement("cost-1", customerA, "MD-20261030-0001", "inbound", "1000"),
                movement("wage-1", customerA, "MD-20261030-0001", "payroll", "80")
        ));
        CustomerOwnedStockQuery stock = new CustomerOwnedStockQuery(ledger, identities, outside(), code -> "颗");
        CustomerStatementCutoffQuery statement = new CustomerStatementCutoffQuery(ledger, identities, code -> "颗");
        InMemoryDocumentManagementRepository documents = new InMemoryDocumentManagementRepository();
        documents.saveAttachment(attachment("ATT-A", "bead.jpg", "image/jpeg", "PHOTO"));
        documents.saveAttachment(attachment("ATT-WAGE", "工资表.pdf", "application/pdf", "PAYROLL"));
        CustomerEvidenceAccessService evidence = new CustomerEvidenceAccessService(documents, new InMemoryCustomerEvidenceGrantRepository(), identities);
        assertThatThrownBy(() -> evidence.grant(admin(), customerA, "ATT-A"))
                .isInstanceOf(com.zhubao.erp.common.error.BusinessException.class)
                .hasMessage("本版不向客户开放照片和文件");

        CustomerOwnedStockView viewA = stock.query(portal("portal-a"));
        assertThat(viewA.lines()).extracting(CustomerOwnedStockView.Line::materialCode).contains("牙骨").doesNotContain("紫金鼠", "胶水", "样珠", "足金");
        assertThat(viewA.sources()).anySatisfy(source -> {
            assertThat(source.kind()).isEqualTo(com.zhubao.erp.business.domain.BusinessKind.SAMPLING);
            assertThat(source.state()).isEqualTo("NOT_IMPLEMENTED");
            assertThat(source.withheld()).isTrue();
        });
        assertThat(viewA.sources()).anySatisfy(source -> {
            assertThat(source.kind()).isEqualTo(com.zhubao.erp.business.domain.BusinessKind.GOLDWORK);
            assertThat(source.state()).isEqualTo("NOT_IMPLEMENTED");
            assertThat(source.withheld()).isTrue();
        });
        assertThat(stock.query(portal("portal-b")).lines()).extracting(CustomerOwnedStockView.Line::materialCode)
                .contains("紫金鼠").doesNotContain("牙骨");

        assertThat(evidence.detail(portal("portal-a")).items()).isEmpty();
        assertThat(evidence.detail(portal("portal-a")).visibility()).isEqualTo("CLOSED");
        assertThatThrownBy(() -> evidence.grant(admin(), customerA, "ATT-WAGE"))
                .isInstanceOf(PermissionDeniedException.class).hasMessageContaining("工资或成本");

        var cutoff = statement.statement(portal("portal-a"), Instant.parse("2026-10-31T00:00:00Z"));
        assertThat(cutoff.exportText()).contains("收料,1000");
        assertThat(cutoff.exportText()).doesNotContain("工资").doesNotContain("payroll").doesNotContain(",80");
        assertThat(cutoff.bills()).isEqualTo("本版不向客户开放账单");
        assertThat(cutoff.exportText()).doesNotContain("账单,0");
        assertThatThrownBy(() -> statement.acknowledge(portal("portal-a"), cutoff.cutoff()))
                .isInstanceOf(com.zhubao.erp.common.error.BusinessException.class)
                .hasMessageContaining("不能自动出库或出账");

        PermissionGuard guard = new PermissionGuard();
        OperatorContext portalOperator = identities.portalOperator("portal-a");
        assertThatThrownBy(() -> guard.assertAllowed(PermissionCode.FINANCE_PAYROLL, portalOperator))
                .isInstanceOf(PermissionDeniedException.class);
        assertThatThrownBy(() -> guard.assertAllowed(PermissionCode.FINANCE_COMPANY_READ, portalOperator))
                .isInstanceOf(PermissionDeniedException.class);
        assertThat(new NavigationMenuService(new InMemoryNavigationMenuRepository()).menuCodesFor(RoleCode.CUSTOMER_PORTAL)).isEmpty();
        assertThat(ledger.writes).isZero();

        identities.disable(admin(), "portal-a");
        assertThatThrownBy(() -> stock.query(portal("portal-a"))).isInstanceOf(PermissionDeniedException.class);
        assertThatThrownBy(() -> evidence.detail(portal("portal-a"))).isInstanceOf(PermissionDeniedException.class);
        assertThatThrownBy(() -> statement.statement(portal("portal-a"), cutoff.cutoff()))
                .isInstanceOf(PermissionDeniedException.class);
        assertThat(stock.query(portal("portal-b")).lines()).isNotEmpty();
    }

    private static CustomerOwnedOutsideShares outside() {
        InMemoryActivityBagRepository bags = new InMemoryActivityBagRepository();
        MemoryBusinessWorkUnit unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        return new CustomerOwnedOutsideShares(new InMemoryOutsourcingSourceRepository(),
                new OutsourcingDispatchService(new InMemoryOutsourcingSourceRepository(),
                        new InMemoryOutsourcingDispatchRepository(), new InMemoryOutsourcingReturnRepository(), bags,
                        new BagSplitService(bags, new ActivityBagService(bags), unit), unit), bags);
    }

    private static StockBalance balance(long customerId, String mother, String material, String quantity) {
        return new StockBalance(new StockDimension(OwnerType.CUSTOMER, customerId, customerId, mother, "",
                material, "WH", StockStatus.RAW_WAITING, ""), new BigDecimal(quantity), new BigDecimal("1"));
    }

    private static StockLedgerEntry movement(String code, long customerId, String mother, String type, String quantity) {
        return new StockLedgerEntry(code, Instant.parse("2026-10-01T00:00:00Z"), type, code, null,
                new StockDimension(OwnerType.CUSTOMER, customerId, customerId, mother, "", "牙骨", "WH",
                        StockStatus.RAW_WAITING, ""),
                new BigDecimal(quantity), new BigDecimal("1"), "");
    }

    private static EvidenceAttachment attachment(String code, String fileName, String media, String objectType) {
        return new EvidenceAttachment(code, "DOC-1", "来料母单", "MD-20261030-0001", fileName, media,
                "s3://evidence/" + code, "hot", "上传", "已上传", "库管", "sha256:" + code,
                "2026-10-31 00:00", "无", "green", objectType);
    }

    private static long bind(String username, String name, String code) {
        if (identities == null) {
            identities = new CustomerPortalIdentityService(accounts, bindings, customers,
                    new InMemoryCustomerPortalIdentityActionRepository(accounts, bindings, new InMemoryAuthAuditRepository()),
                    sessions());
        }
        CustomerProfile saved = customers.saveCustomer(new CustomerProfile(0, code, name, "", "", "", "", true));
        identities.bind(admin(), username, saved.customerId());
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

    private static OperatorContext admin() {
        return new OperatorContext("系统管理员", RoleCode.ADMIN, DataScope.ALL, false, "", List.of());
    }

    private static OperatorContext portal(String username) {
        return new OperatorContext(username, RoleCode.CUSTOMER_PORTAL, DataScope.CUSTOMER_RANGE, false, "",
                List.of(), List.of(), username, "", "");
    }

    private static CustomerPortalIdentityService identities;
    private static final InMemoryAuthUserAccountRepository accounts = new InMemoryAuthUserAccountRepository(new PasswordHasher());
    private static final InMemoryCustomerPortalBindingRepository bindings = new InMemoryCustomerPortalBindingRepository();
    private static final InMemoryMasterDataRepository customers = new InMemoryMasterDataRepository();

    private static final class Ledger implements StockLedgerRepository {
        private final List<StockBalance> balances;
        private final List<StockLedgerEntry> ledgers;
        private int writes;

        private Ledger(List<StockBalance> balances, List<StockLedgerEntry> ledgers) {
            this.balances = balances;
            this.ledgers = ledgers;
        }

        @Override
        public StockLedgerEntry save(StockLedgerEntry entry) {
            writes++;
            throw new com.zhubao.erp.common.error.BusinessException("客户查询不能写库存");
        }

        @Override
        public StockLedgerEntry save(StockLedgerEntry entry, WorkSession session) {
            writes++;
            throw new com.zhubao.erp.common.error.BusinessException("客户查询不能写库存");
        }

        @Override
        public List<StockLedgerEntry> findAllLedgers() {
            return List.copyOf(ledgers);
        }

        @Override
        public List<StockLedgerEntry> findLedgersByCustomers(Set<Long> customerIds, Set<String> motherOrderCodes) {
            return List.of();
        }

        @Override
        public List<StockBalance> findAllBalances() {
            return List.copyOf(balances);
        }

        @Override
        public StockBalance findBalance(StockDimension dimension) {
            throw new com.zhubao.erp.common.error.BusinessException("客户查询不能改余额");
        }

        @Override
        public void clear() {
            writes++;
            throw new com.zhubao.erp.common.error.BusinessException("客户查询不能清库存");
        }
    }
}
