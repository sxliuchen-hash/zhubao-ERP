package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.business.infra.InMemoryBusinessDocumentRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 母单原号在保存和合法修改后仍然可查。
 */
class MotherOrderIdentityRegressionTest {

    @Test
    void 原号可查且合法修改不换号() {
        InMemoryBusinessDocumentRepository repository = new InMemoryBusinessDocumentRepository();
        String code = "MD-20261030-0001";
        repository.saveMotherOrder(mother(code, "原备注"));

        assertThat(repository.findMotherOrderByCode(code)).get()
                .extracting(MotherOrderRecord::code, MotherOrderRecord::remark)
                .containsExactly(code, "原备注");

        repository.saveMotherOrder(mother(code, "改后备注"));

        MotherOrderRecord saved = repository.findMotherOrderByCode(code).orElseThrow();
        assertThat(saved.code()).isEqualTo(code);
        assertThat(saved.remark()).isEqualTo("改后备注");
        assertThat(repository.listMotherOrders()).extracting(MotherOrderRecord::code).containsExactly(code);
    }

    private static MotherOrderRecord mother(String code, String remark) {
        return new MotherOrderRecord(
                code, 8L, 8L, "AU9999", "2026-10-30", "", "",
                "", "", "", "样本货品", "CUSTOMER", true, "WH01",
                new BigDecimal("10.000"), "颗", new BigDecimal("1.200"), "IN_STOCK", "LG-A05",
                "INTAKE_LINES:1", 1, "", "operator", "EV-A05", remark, "ACTIVE", "2026-10-30 10:00",
                "来料加工", "", "样本客户"
        );
    }
}
