package com.zhubao.erp.label.app;

import com.zhubao.erp.business.app.*;
import com.zhubao.erp.business.domain.*;
import com.zhubao.erp.business.infra.*;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.infra.InMemoryStockLedgerRepository;
import com.zhubao.erp.label.infra.*;
import com.zhubao.erp.security.domain.*;
import com.zhubao.erp.work.app.MemoryBusinessWorkUnit;
import com.zhubao.erp.work.infra.InMemoryCommandRequestRepository;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class LabelStorageEditionServiceTest {
    @Test void 换贴创建新保存版本而同版补打保留原快照() {
        var catalog = new MasterDataService(new InMemoryMasterDataRepository());
        catalog.saveMaterial(new MaterialProfile("XY", "星月", "", "", true, List.of("COLD", "VACUUM"), ""));
        var documents = new InMemoryBusinessDocumentRepository();
        var unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        var stock = new StockLedgerService(new InMemoryStockLedgerRepository());
        var operations = new BusinessOperationService(stock, null, documents, unit);
        var intakes = new MobileIntakeService(operations, documents, unit, catalog, stock);
        var one = BigDecimal.ONE;
        String code = intakes.intake(new MobileIntakeCommand("", "恒信珠宝", 1001L, "抛光", "2026-11-01", "珠子", one, one, "", "XY", "件", "label-storage-intake", "CUSTOMER_DELIVERY", List.of(new MobileIntakeLine("XY", "珠子", one, "件", one))), new OperatorContext("收货员", RoleCode.RECEIVER, DataScope.ALL)).motherOrderCode();
        var labels = new LabelService(documents);
        var editions = new InMemoryLabelStorageEditionRepository(); labels.setStorageEditions(editions);
        var jobs = new PrintJobService(new InMemoryPrintJobRepository(), ""); jobs.setDocumentAccess(documents, null); jobs.setLabelService(labels);
        var original = jobs.create("storage-print-1", code, "AUTO", 1, "收货员");
        catalog.saveMaterial(new MaterialProfile("XY", "星月", "", "", true, List.of("DARK"), ""));
        assertThat(labels.generateMotherLabel(code).storageSources().get(0).marks()).containsExactly("冷", "真");
        var service = new LabelStorageEditionService(editions, documents, catalog, labels, unit);
        assertThat(service.adopt(code, "adopt-001", 0, "现场核对后更新", "收货员", "")).isEqualTo(1);
        assertThat(service.adopt(code, "adopt-001", 0, "现场核对后更新", "收货员", "")).isEqualTo(1);
        assertThat(labels.generateMotherLabel(code).storageSources().get(0).marks()).containsExactly("光");
        assertThat(labels.generateMotherLabel(code).qrContent()).endsWith("-S1");
        assertThat(service.history(code)).singleElement().satisfies(row -> assertThat(row.reason()).isEqualTo("现场核对后更新"));
        assertThatThrownBy(() -> labels.reprint(new com.zhubao.erp.label.domain.ReprintLabelCommand("MOTHER", code, "原版补打", "收货员", "old-snapshot"))).hasMessageContaining("原打印任务同版补打");
        assertThat(documents.listReceiptLines(code).get(0).storageRequirements().conditions()).containsExactly("COLD", "VACUUM");
        assertThat(jobs.retry(original.id(), "storage-reprint-1", "同版补打", "收货员").contentSnapshot()).isEqualTo(original.contentSnapshot());
        assertThatThrownBy(() -> service.adopt(code, "adopt-stale", 0, "旧界面再次提交", "收货员", "")).hasMessageContaining("重新预览");
    }
}
