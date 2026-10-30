package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ActivityBag;
import com.zhubao.erp.business.domain.BagWeightState;
import com.zhubao.erp.business.domain.BusinessKind;
import com.zhubao.erp.business.domain.QuantityDispositionResult;
import com.zhubao.erp.business.domain.RouteSnapshot;
import com.zhubao.erp.business.infra.InMemoryActivityBagRepository;
import com.zhubao.erp.business.infra.InMemoryBagWeightRepository;
import com.zhubao.erp.business.infra.InMemoryNodePortionReader;
import com.zhubao.erp.business.infra.InMemoryQuantityDispositionRepository;
import com.zhubao.erp.business.infra.InMemoryRouteSnapshotRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.document.domain.EvidenceAttachment;
import com.zhubao.erp.document.domain.EvidenceCodes;
import com.zhubao.erp.document.infra.InMemoryDocumentManagementRepository;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.StockMeasurement;
import com.zhubao.erp.inventory.infra.InMemoryStockLedgerRepository;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.work.app.MemoryBusinessWorkUnit;
import com.zhubao.erp.work.infra.InMemoryCommandRequestRepository;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProcessWeightReconciliationTest {

    @Test
    void 子袋不继承原重且补录不重复数量() {
        InMemoryActivityBagRepository bags = new InMemoryActivityBagRepository();
        InMemoryNodePortionReader portions = new InMemoryNodePortionReader();
        InMemoryBagWeightRepository weightRecords = new InMemoryBagWeightRepository();
        InMemoryRouteSnapshotRepository routes = new InMemoryRouteSnapshotRepository();
        InMemoryQuantityDispositionRepository dispositions = new InMemoryQuantityDispositionRepository();
        InMemoryDocumentManagementRepository documents = new InMemoryDocumentManagementRepository();
        MemoryBusinessWorkUnit unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        StockLedgerService stocks = new StockLedgerService(new InMemoryStockLedgerRepository());
        ActivityBagService bagService = new ActivityBagService(bags);
        ActivityBag parent = bagService.open(
                "MD-20261030-0401", "牙骨", "REQ-1", "LINE-1", new BigDecimal("500"),
                StockMeasurement.known(new BigDecimal("7.260")), null);
        routes.insert(new RouteSnapshot("RS-1", BusinessKind.PROCESSING, parent.targetCode(), 1,
                List.of("CUT", "BLANK"), true), null);
        documents.saveAttachment(new EvidenceAttachment(
                "ATT-1", "DOC-1", "来料母单", parent.targetCode(), "a.jpg", "image/jpeg",
                "local://a", "hot", "PWA 拍照上传", "已上传", "操作工", "sha256:a",
                "2026-10-30 10:00:00", "无", "green", EvidenceCodes.MOTHER_ORDER));
        ProcessAvailabilityQuery query = new ProcessAvailabilityQuery(
                bags, routes, portions, new PermissionGuard(), dispositions);
        QuantityDispositionService dispositionsService = new QuantityDispositionService(
                bags, bagService, routes, query, portions, portions, dispositions, documents, stocks, unit);
        WeighingService weighing = new WeighingService(bags, weightRecords, unit);
        dispositionsService.setReweigh(weighing);
        OperatorContext operator = new OperatorContext("操作工", RoleCode.OPERATOR, DataScope.ALL);
        QuantityDispositionResult split = dispositionsService.dispose(new com.zhubao.erp.business.domain.QuantityDispositionCommand(
                "weight-c04-01", parent.id(), parent.version(), new BigDecimal("250"), BigDecimal.ZERO,
                BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO, "ATT-1", StockMeasurement.unknown(), null), operator);

        ActivityBag handoff = bags.find(split.handoffBagId()).orElseThrow();
        ActivityBag remainder = bags.find(split.remainderBagId()).orElseThrow();
        assertThat(handoff.weight().value()).isNull();
        assertThat(remainder.weight().value()).isNull();
        assertThat(weighing.current(handoff.id()).state()).isEqualTo(BagWeightState.PENDING_REWEIGH);
        assertThat(weighing.current(remainder.id()).state()).isEqualTo(BagWeightState.PENDING_REWEIGH);
        assertThat(weighing.current(handoff.id()).value()).isNull();
        assertThat(bags.find(parent.id()).orElseThrow().weight().value()).isEqualByComparingTo("7.260");
        assertThatThrownBy(() -> weighing.assertReadyForTransfer(remainder.id()))
                .isInstanceOf(BusinessException.class)
                .hasMessage("需要本份额的实际重量后才能转移或发货");

        int portionsBefore = portions.size();
        BigDecimal quantityBefore = remainder.quantity();
        weighing.supplement("weight-c04-02", remainder.id(), remainder.version(), new BigDecimal("1.250"), operator);
        weighing.supplement("weight-c04-02", remainder.id(), remainder.version(), new BigDecimal("1.250"), operator);
        ActivityBag weighed = bags.find(remainder.id()).orElseThrow();
        assertThat(weighed.quantity()).isEqualByComparingTo(quantityBefore);
        assertThat(weighed.weight().value()).isEqualByComparingTo("1.250");
        assertThat(portions.size()).isEqualTo(portionsBefore);
        assertThat(weighing.current(remainder.id()).state()).isEqualTo(BagWeightState.KNOWN);
        weighing.assertReadyForTransfer(remainder.id());
        assertThat(stocks.ledgers()).isEmpty();
        assertThatThrownBy(() -> weighing.supplement(
                "weight-c04-03", handoff.id(), handoff.version(), null, operator))
                .isInstanceOf(BusinessException.class)
                .hasMessage("请填写实际称重，未知重量不能按 0 补录");
    }
}
