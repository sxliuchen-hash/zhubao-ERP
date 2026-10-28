package com.zhubao.erp.business.app;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.assertThat;

class EmptyRuntimeDataTest {
    @Test
    void 内存空库不注入客户附件或网关设备() {
        var customers = new com.zhubao.erp.business.infra.InMemoryMasterDataRepository();
        var documents = new com.zhubao.erp.document.infra.InMemoryDocumentManagementRepository();
        var devices = new com.zhubao.erp.gateway.infra.InMemoryGatewayDeviceRepository();
        assertThat(customers.listCustomers()).isEmpty();
        assertThat(customers.listMaterials()).isNotEmpty();
        assertThat(customers.listTemplates()).isNotEmpty();
        assertThat(documents.packages()).isEmpty();
        assertThat(documents.attachments()).isEmpty();
        assertThat(documents.auditEvents()).isEmpty();
        assertThat(devices.findAll()).isEmpty();
        var accounts = new com.zhubao.erp.security.infra.InMemoryAuthUserAccountRepository(new com.zhubao.erp.security.app.PasswordHasher(), "Bootstrap-Test-2026!");
        assertThat(accounts.findAll()).singleElement().satisfies(admin -> {
            assertThat(admin.username()).isEqualTo("admin");
            assertThat(admin.demoAccount()).isFalse();
            assertThat(admin.passwordChangeRequired()).isTrue();
        });
    }
}
