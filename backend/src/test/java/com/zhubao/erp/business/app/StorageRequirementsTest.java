package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.*;
import com.zhubao.erp.business.infra.*;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.infra.InMemoryStockLedgerRepository;
import com.zhubao.erp.security.domain.*;
import com.zhubao.erp.work.app.MemoryBusinessWorkUnit;
import com.zhubao.erp.work.infra.InMemoryCommandRequestRepository;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.util.List;
import static org.assertj.core.api.Assertions.*;

class StorageRequirementsTest {
    @Test void 旧请求保留条件而显式空列表清除且拒绝冲突条件() {
        var service = new MasterDataService(new InMemoryMasterDataRepository());
        var first = service.saveMaterial(new MaterialProfile("XY", "星月", "", "", true, List.of("VACUUM", "COLD"), "保持密封"));
        assertThat(first.storageConditions()).containsExactly("COLD", "VACUUM");
        var oldClient = service.saveMaterial(new MaterialProfile("XY", "星月", "", "改备注", true));
        assertThat(oldClient.storageConditions()).isEqualTo(first.storageConditions());
        assertThat(oldClient.storageInstruction()).isEqualTo("保持密封");
        assertThatThrownBy(() -> service.saveMaterial(new MaterialProfile("XY", "星月", "", "", true, List.of("COLD", "FROZEN"), ""))).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> new StorageRequirements(List.of("VACUUM", "VACUUM"), "")).isInstanceOf(BusinessException.class);
        assertThat(service.saveMaterial(new MaterialProfile("XY", "星月", "", "", true, List.of(), "")).storageConditions()).isEmpty();
        assertThat(service.storageChanges("XY")).hasSize(2);
        assertThat(StorageRequirements.read(null, null)).isNull();
    }
    @Test void 来源收料和拆单冻结保存条件且修改档案不改历史() {
        var catalog = new MasterDataService(new InMemoryMasterDataRepository());
        catalog.saveMaterial(new MaterialProfile("XY", "星月", "", "", true, List.of("COLD", "VACUUM"), "密封"));
        var docs = new InMemoryBusinessDocumentRepository();
        var ledgers = new StockLedgerService(new InMemoryStockLedgerRepository());
        var unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        var operations = new BusinessOperationService(ledgers, null, docs, unit);
        var intakes = new MobileIntakeService(operations, docs, unit, catalog, ledgers);
        var one = BigDecimal.ONE;
        var result = intakes.intake(new MobileIntakeCommand("", "恒信珠宝", 1001L, "抛光", "2026-11-01", "珠子", one, one, "", "XY", "件", "storage-intake", "CUSTOMER_DELIVERY", List.of(new MobileIntakeLine("XY", "星月珠子", one, "件", one))), new OperatorContext("收货员", RoleCode.RECEIVER, DataScope.ALL));
        catalog.saveMaterial(new MaterialProfile("XY", "星月", "", "", true, List.of("DARK"), "新说明"));
        var lines = docs.listReceiptLines(result.motherOrderCode());
        assertThat(lines.get(0).storageRequirements().conditions()).containsExactly("COLD", "VACUUM");
        var splits = new MotherOrderSplitService(docs, ledgers, unit);
        splits.split(new SplitMotherOrderCommand(result.motherOrderCode(), "收货员", List.of(new SplitMotherOrderLine(new BigDecimal("0.5"), new BigDecimal("0.5"), List.of("抛光"), List.of(new SplitSourceAllocation(1, new BigDecimal("0.5"), new BigDecimal("0.5")))), new SplitMotherOrderLine(new BigDecimal("0.5"), new BigDecimal("0.5"), List.of("抛光"), List.of(new SplitSourceAllocation(1, new BigDecimal("0.5"), new BigDecimal("0.5")))))), "storage-split");
        assertThat(docs.listChildAllocations(result.motherOrderCode(), null).get(0).storageRequirements()).isEqualTo(lines.get(0).storageRequirements());
    }
}
