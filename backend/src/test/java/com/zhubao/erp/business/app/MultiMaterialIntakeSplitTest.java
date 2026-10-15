package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.MobileIntakeCommand;
import com.zhubao.erp.business.domain.MobileIntakeLine;
import com.zhubao.erp.business.domain.SplitMotherOrderCommand;
import com.zhubao.erp.business.domain.SplitMotherOrderLine;
import com.zhubao.erp.business.domain.SplitSourceAllocation;
import com.zhubao.erp.business.domain.MaterialProfile;
import com.zhubao.erp.business.infra.InMemoryBusinessDocumentRepository;
import com.zhubao.erp.business.infra.InMemoryMasterDataRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.infra.InMemoryStockLedgerRepository;
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

class MultiMaterialIntakeSplitTest {
    @Test
    void receiptsPostPerMaterialAndSplitChecksEachSource() {
        InMemoryBusinessDocumentRepository documents = new InMemoryBusinessDocumentRepository();
        InMemoryStockLedgerRepository stock = new InMemoryStockLedgerRepository();
        StockLedgerService ledgers = new StockLedgerService(stock);
        MemoryBusinessWorkUnit unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        MasterDataService catalog = new MasterDataService(new InMemoryMasterDataRepository());
        MaterialProfile secondMaterial = catalog.saveMaterial(new MaterialProfile("", "18K 白金", "", "", true));
        MobileIntakeService intake = new MobileIntakeService(
                new BusinessOperationService(ledgers, null, documents, unit), documents, unit, catalog, ledgers);
        OperatorContext receiver = new OperatorContext("何琳", RoleCode.RECEIVER, DataScope.ALL);
        MobileIntakeCommand command = new MobileIntakeCommand("", "恒信珠宝", 1001L, "抛光", "2026-10-20",
                "戒托", amount("8"), amount("12"), "", "AU9999", "件", "multi-intake-001",
                "CUSTOMER_DELIVERY", List.of(
                new MobileIntakeLine("AU9999", "金戒托", amount("3"), "件", amount("4")),
                new MobileIntakeLine(secondMaterial.materialCode(), "白金戒托", amount("5"), "件", amount("8"))));
        String mother = intake.intake(command, receiver).motherOrderCode();
        assertThat(intake.intake(command, receiver).motherOrderCode()).isEqualTo(mother);
        assertThat(documents.listReceiptLines(mother)).hasSize(2);
        assertThat(documents.findMotherOrderByCode(mother).orElseThrow().quantity()).isEqualByComparingTo("8");
        assertThat(stock.findAllLedgers()).hasSize(2);
        assertThat(stock.findAllLedgers()).extracting(item -> item.to().materialCode())
                .containsExactlyInAnyOrder("AU9999", secondMaterial.materialCode());

        MotherOrderSplitService splits = new MotherOrderSplitService(documents, ledgers, unit);
        SplitMotherOrderCommand overdraw = new SplitMotherOrderCommand(mother, "何琳", List.of(
                split(2, 2, 1), split(2, 2, 1)));
        assertThatThrownBy(() -> splits.split(overdraw, "multi-split-bad"))
                .isInstanceOf(BusinessException.class).hasMessageContaining("来源明细剩余");
        assertThat(documents.listChildWorkOrders(mother)).isEmpty();
        assertThat(stock.findAllLedgers()).hasSize(2);

        SplitMotherOrderCommand valid = new SplitMotherOrderCommand(mother, "何琳", List.of(
                split(2, 2, 1), split(2, 3, 2)));
        var result = splits.split(valid, "multi-split-good");
        assertThat(result.children()).hasSize(2);
        assertThat(result.sourceLines()).hasSize(2);
        assertThat(result.sourceLines().get(0).remainingQuantity()).isEqualByComparingTo("1");
        assertThat(result.sourceLines().get(1).remainingQuantity()).isEqualByComparingTo("3");
        assertThat(documents.listChildAllocations(mother, null)).hasSize(2);
        assertThat(stock.findAllLedgers()).hasSize(4);
        assertThat(splits.split(valid, "multi-split-good").children()).hasSize(2);
        assertThat(stock.findAllLedgers()).hasSize(4);
    }

    private SplitMotherOrderLine split(int quantity, int weight, int source) {
        return new SplitMotherOrderLine(amount(String.valueOf(quantity)), amount(String.valueOf(weight)),
                List.of("抛光"), List.of(new SplitSourceAllocation(source,
                amount(String.valueOf(quantity)), amount(String.valueOf(weight)))));
    }

    private BigDecimal amount(String text) { return new BigDecimal(text); }
}
