package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.CustomerProfile;
import com.zhubao.erp.business.domain.MaterialProfile;
import com.zhubao.erp.business.domain.RequirementShortcut;
import com.zhubao.erp.business.domain.MobileIntakeCommand;
import com.zhubao.erp.business.infra.InMemoryBusinessDocumentRepository;
import com.zhubao.erp.business.infra.InMemoryMasterDataRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.infra.InMemoryStockLedgerRepository;
import com.zhubao.erp.security.app.CustomerAuthorizationService;
import com.zhubao.erp.security.app.PasswordHasher;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.CustomerAuthorization;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.infra.InMemoryAuthUserAccountRepository;
import com.zhubao.erp.security.infra.InMemoryCustomerAuthorizationRepository;
import com.zhubao.erp.work.app.MemoryBusinessWorkUnit;
import com.zhubao.erp.work.infra.InMemoryCommandRequestRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class MasterDataServiceTest {
    @Test
    void stableIdAndHistoricalSnapshotSurviveRename() {
        MasterDataService catalog = new MasterDataService(new InMemoryMasterDataRepository());
        CustomerProfile customer = catalog.saveCustomer(new CustomerProfile(0, "", "新客户", "", "", "", "", true));
        MaterialProfile material = catalog.saveMaterial(new MaterialProfile("", "18K 白金", "", "", true));
        assertThat(customer.customerId()).isGreaterThan(1004);
        assertThat(material.materialCode()).startsWith("MAT-");
        assertThatThrownBy(() -> catalog.saveMaterial(new MaterialProfile("", "18K 白金", "", "", true)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("材质名称已存在");

        InMemoryBusinessDocumentRepository documents = new InMemoryBusinessDocumentRepository();
        MemoryBusinessWorkUnit unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        MobileIntakeService intake = new MobileIntakeService(new BusinessOperationService(
                new StockLedgerService(new InMemoryStockLedgerRepository()), null, documents, unit),
                documents, unit, catalog);
        var command = new MobileIntakeCommand("", "客户端旧名称", customer.customerId(), "抛光", "2026-10-20",
                "戒托", BigDecimal.ONE, BigDecimal.ONE, "", material.materialCode(), "件",
                "catalog-intake-01", "CUSTOMER_DELIVERY");
        var result = intake.intake(command, new OperatorContext("何琳", RoleCode.RECEIVER, DataScope.ALL));
        assertThat(result.customerName()).isEqualTo("新客户");
        catalog.saveCustomer(new CustomerProfile(customer.customerId(), customer.customerCode(), "已改名客户",
                "新客户", "", "", "", true));
        assertThat(intake.view(result.motherOrderCode()).customerName()).isEqualTo("新客户");
        assertThat(catalog.customerForIntake(customer.customerId()).name()).isEqualTo("已改名客户");
        CustomerProfile renamed = catalog.saveCustomer(new CustomerProfile(customer.customerId(), customer.customerCode(), "已改名客户",
                "", "", "", "", true));
        assertThat(renamed.aliases()).contains("新客户");
        assertThat(catalog.stableCustomerScope(List.of("新客户", customer.customerCode(), "客户#" + customer.customerId(), "客户#999999", "未知客户")))
                .containsExactly(String.valueOf(customer.customerId()));
        catalog.saveCustomer(new CustomerProfile(0, "", "新客户", "", "", "", "", true));
        assertThat(catalog.stableCustomerScope(List.of("新客户", "客户#" + customer.customerId())))
                .containsExactly(String.valueOf(customer.customerId()));
        assertThatThrownBy(() -> intake.intake(new MobileIntakeCommand("", "新客户", null, "抛光",
                "2026-10-20", "戒托", BigDecimal.ONE, BigDecimal.ONE, "", material.materialCode(),
                "件", "catalog-intake-02", "CUSTOMER_DELIVERY"),
                new OperatorContext("何琳", RoleCode.RECEIVER, DataScope.ALL)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("请从客户档案选择客户");
    }

    @Test
    void newCustomerJoinsTheCreatorRestrictedScope() {
        InMemoryAuthUserAccountRepository accounts = new InMemoryAuthUserAccountRepository(new PasswordHasher());
        accounts.save(new AuthUserAccount("recv", "hash", "收货员", RoleCode.RECEIVER,
                DataScope.CUSTOMER_RANGE, true, false, List.of("1001")));
        CustomerAuthorizationService authorizations = new CustomerAuthorizationService(new InMemoryCustomerAuthorizationRepository());
        MasterDataService catalog = new MasterDataService(new InMemoryMasterDataRepository(), authorizations, accounts);
        OperatorContext receiver = new OperatorContext("收货员", RoleCode.RECEIVER, DataScope.CUSTOMER_RANGE, false, "", List.of("1001"));

        CustomerProfile created = catalog.saveCustomer(new CustomerProfile(0, "", "翠玉坊", "", "", "", "", true), receiver, "recv");

        assertThat(accounts.findByUsername("recv").orElseThrow().customerScope())
                .containsExactly("1001", String.valueOf(created.customerId()));
        assertThat(authorizations.authorizationsFor("recv")).isEmpty();
        assertThat(catalog.customersFor(new OperatorContext("库管", RoleCode.WAREHOUSE_KEEPER, DataScope.DEPARTMENT)))
                .isEmpty();

        authorizations.grant("recv", "1002", "雅辰饰品", new OperatorContext("管理员", RoleCode.ADMIN, DataScope.ALL));
        CustomerProfile another = catalog.saveCustomer(new CustomerProfile(0, "", "瑞金行", "", "", "", "", true), receiver, "recv");
        assertThat(authorizations.authorizationsFor("recv"))
                .extracting(CustomerAuthorization::customerCode)
                .containsExactly("1002", String.valueOf(another.customerId()));
    }

    @Test
    void requirementShortcutsStaySharedAndRejectDuplicates() {
        MasterDataService catalog = new MasterDataService(new InMemoryMasterDataRepository());
        RequirementShortcut first = catalog.saveShortcut(new RequirementShortcut(0, "镜面抛光", 0));
        RequirementShortcut second = catalog.saveShortcut(new RequirementShortcut(0, "改圈", 0));
        assertThat(first.shortcutId()).isPositive();
        assertThat(second.sortOrder()).isGreaterThan(first.sortOrder());
        assertThat(catalog.shortcuts()).extracting(RequirementShortcut::phrase).containsExactly("镜面抛光", "改圈");
        assertThatThrownBy(() -> catalog.saveShortcut(new RequirementShortcut(0, " 镜面抛光 ", 0)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("已存在");
        assertThatThrownBy(() -> catalog.saveShortcut(new RequirementShortcut(0, "抛光、电镀", 0)))
                .isInstanceOf(BusinessException.class).hasMessageContaining("顿号");
        assertThat(catalog.removeShortcut(first.shortcutId()).phrase()).isEqualTo("镜面抛光");
        assertThat(catalog.shortcuts()).extracting(RequirementShortcut::phrase).containsExactly("改圈");
        assertThatThrownBy(() -> catalog.removeShortcut(first.shortcutId()))
                .isInstanceOf(BusinessException.class).hasMessageContaining("不存在");
    }
}
