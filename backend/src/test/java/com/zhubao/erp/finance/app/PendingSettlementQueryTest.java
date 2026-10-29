package com.zhubao.erp.finance.app;

import com.zhubao.erp.business.app.OrderDeliveryCompletionQuery;
import com.zhubao.erp.business.domain.*;
import com.zhubao.erp.business.infra.InMemoryBusinessDocumentRepository;
import com.zhubao.erp.finance.domain.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class PendingSettlementQueryTest {
    private final InMemoryBusinessDocumentRepository documents = new InMemoryBusinessDocumentRepository();
    private final OrderDeliveryCompletionQuery deliveries = mock(OrderDeliveryCompletionQuery.class);
    private final OrderSettlementRepository settlements = mock(OrderSettlementRepository.class);
    private final OrderSettlementService service = mock(OrderSettlementService.class);
    private final PendingSettlementQuery query = new PendingSettlementQuery(documents, deliveries, settlements, service);

    @BeforeEach void setup() {
        when(settlements.policyEnabled()).thenReturn(true);
        when(settlements.latest(anyString(), anyString(), isNull())).thenReturn(Optional.empty());
        when(service.reviewLegacy(any())).thenReturn(new OrderSettlementService.LegacyReview(false, ""));
        documents.saveMotherOrder(new MotherOrderRecord("MD-1", 101L, 101L, "MAT", "2026-10-01", "", "", "", "", "", "货品", "CUSTOMER", true, "RAW", BigDecimal.ONE, "件", BigDecimal.ONE, "RAW_WAITING", "", "", null, "", "收货员", "", "", "OPEN", "2026-10-01", "加工", "2026-10-31", "客户甲"));
    }

    @Test void 未交齐或仅有进度不进入已交货缺项() {
        child("ZD-1", "2026-10-01");
        when(deliveries.assess("CHILD", "ZD-1")).thenReturn(assessment("ZD-1", false, "NOT_COMPLETE"));
        assertThat(query.list("2026-10", null)).isEmpty();
        verifyNoInteractions(service);
    }

    @Test void 截至月末的旧月已交货未结单也列出且不重复列母单() {
        child("ZD-1", "2026-09-15");
        when(deliveries.assess("CHILD", "ZD-1")).thenReturn(assessment("ZD-1", true, "COMPLETE"));
        assertThat(query.list("2026-10", null)).singleElement().satisfies(row -> {
            assertThat(row.workOrderCode()).isEqualTo("ZD-1");
            assertThat(row.reason()).isEqualTo("已交货待结单");
        });
        assertThat(query.list("2026-08", null)).isEmpty();
        assertThat(query.list("2026-10", 999L)).isEmpty();
    }

    @Test void 交货待核和旧账覆盖不明分别展示且不写账() {
        child("ZD-1", ""); child("ZD-2", "2026-10-27");
        when(deliveries.assess("CHILD", "ZD-1")).thenReturn(assessment("ZD-1", false, "PENDING_REVIEW"));
        when(deliveries.assess("CHILD", "ZD-2")).thenReturn(assessment("ZD-2", true, "COMPLETE"));
        when(service.reviewLegacy(argThat(row -> row != null && row.targetCode().equals("ZD-2"))))
                .thenReturn(new OrderSettlementService.LegacyReview(true, "历史待核：覆盖关系不明"));
        assertThat(query.list("2026-10", null)).extracting(UnpricedItem::reason)
                .containsExactly("交货待核：缺少交货证据；发货日期待核", "历史待核：覆盖关系不明");
        verify(settlements, never()).markConfirmed(anyString(), anyInt(), anyString(), anyString(), anyString(), any());
    }

    @Test void 已确认目标及已有确认的调整草稿不重复列为未结单() {
        child("ZD-1", "2026-10-27");
        when(deliveries.assess("CHILD", "ZD-1")).thenReturn(assessment("ZD-1", true, "COMPLETE"));
        var confirmed = mock(OrderSettlement.class);
        when(confirmed.status()).thenReturn(SettlementStatus.CONFIRMED);
        when(settlements.latest("CHILD", "ZD-1", null)).thenReturn(Optional.of(confirmed));
        assertThat(query.list("2026-10", null)).isEmpty();
        var draft = mock(OrderSettlement.class);
        when(draft.status()).thenReturn(SettlementStatus.DRAFT);
        when(draft.previousCode()).thenReturn("SET-1"); when(draft.rootCode()).thenReturn("SET-1");
        when(settlements.latest("CHILD", "ZD-1", null)).thenReturn(Optional.of(draft));
        when(settlements.find("SET-1", null)).thenReturn(Optional.of(confirmed));
        assertThat(query.list("2026-10", null)).isEmpty();
    }

    @Test void 未启用新结单策略不伪造缺项() {
        when(settlements.policyEnabled()).thenReturn(false);
        assertThat(query.list("2026-10", null)).isEmpty();
        verifyNoInteractions(deliveries);
    }

    private OrderDeliveryAssessment assessment(String code, boolean eligible, String state) {
        return new OrderDeliveryAssessment("CHILD", code, "MD-1", 101L, "客户甲", true, state, eligible, "revision", eligible ? List.of() : List.of(new OrderDeliveryAssessment.DeliveryBlock("EVIDENCE", "缺少交货证据", true)), List.of(), List.of(), List.of());
    }
    private void child(String code, String date) {
        documents.saveChildWorkOrder(new ChildWorkOrderRecord(code, "MD-1", 101L, "客户甲", "加工", "2026-10-31", "MAT", "货品", BigDecimal.ONE, "件", BigDecimal.ONE, "RAW", "抛光", code, "", "收货员", "2026-10-01"));
        documents.saveOutboundOrder(new OutboundOrderRecord("OUT-" + code, "MD-1", code, 101L, 101L, "MAT", "FG", "CUSTOMER", date, "成品", BigDecimal.ONE, "件", BigDecimal.ONE, "", "", "", "", "", null, "", "发货员", "", "", "SHIPPED", "L-" + code, date));
    }
}
