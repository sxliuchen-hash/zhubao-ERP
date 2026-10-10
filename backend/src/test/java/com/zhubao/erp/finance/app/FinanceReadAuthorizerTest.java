package com.zhubao.erp.finance.app;

import com.zhubao.erp.finance.domain.ChargeUnit;
import com.zhubao.erp.finance.domain.PriceAgreement;
import com.zhubao.erp.finance.domain.PriceLine;
import com.zhubao.erp.finance.domain.PricingMode;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FinanceReadAuthorizerTest {

    private final FinanceReadAuthorizer authorizer = new FinanceReadAuthorizer();

    @Test
    void matchesCustomerNameAndRejectsEmptyScopeAsCompanyWide() {
        OperatorContext scoped = new OperatorContext(
                "财务",
                RoleCode.FINANCE_STATISTICIAN,
                DataScope.CUSTOMER_RANGE,
                true,
                "",
                List.of("恒信珠宝")
        );
        authorizer.assertCustomer(scoped, 91004L, "恒信珠宝");
        assertThatThrownBy(() -> authorizer.assertCustomer(scoped, 91005L, "瑞禾珠宝"))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("无权查看该客户");
        assertThatThrownBy(() -> authorizer.assertCompany(scoped))
                .isInstanceOf(PermissionDeniedException.class);

        OperatorContext empty = new OperatorContext(
                "财务",
                RoleCode.FINANCE_STATISTICIAN,
                DataScope.CUSTOMER_RANGE,
                true,
                "",
                List.of()
        );
        assertThatThrownBy(() -> authorizer.assertCustomer(empty, 91004L, "恒信珠宝"))
                .isInstanceOf(PermissionDeniedException.class)
                .hasMessageContaining("没有授权客户");

        List<PriceAgreement> visible = authorizer.visibleAgreements(scoped, List.of(
                agreement(91004L, "恒信珠宝"),
                agreement(91005L, "瑞禾珠宝")
        ));
        assertThat(visible).extracting(PriceAgreement::customerName).containsExactly("恒信珠宝");
    }

    private PriceAgreement agreement(long customerId, String name) {
        return new PriceAgreement(
                "FIN-AGR-" + customerId,
                customerId,
                name,
                PricingMode.WEIGHT,
                "2026-10",
                "",
                List.of(new PriceLine("", ChargeUnit.GRAM, new BigDecimal("10"))),
                "财务",
                "2026-10-10"
        );
    }
}
