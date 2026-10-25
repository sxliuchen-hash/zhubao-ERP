package com.zhubao.erp.document.app;

import com.zhubao.erp.business.app.BusinessDataAccess;
import com.zhubao.erp.business.infra.InMemoryBusinessDocumentRepository;
import com.zhubao.erp.document.domain.*;
import com.zhubao.erp.document.infra.*;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.domain.*;
import com.zhubao.erp.inventory.infra.*;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.util.List;
import static org.assertj.core.api.Assertions.*;
import static com.zhubao.erp.document.AttachmentTestMedia.jpeg;

class PrepReceiptEvidenceTest {
    @TempDir Path root;
    @Test void 寄存待核和已入库均用真实收货编号上传关联且拒绝越权伪造操作() {
        var orders = new InMemoryBusinessDocumentRepository(); var stocks = new StockLedgerService(new InMemoryStockLedgerRepository());
        var stockDocs = new InMemoryStockDocumentRepository(); var documents = new InMemoryDocumentManagementRepository(); var links = new InMemoryBusinessEvidenceLinkRepository();
        var access = new BusinessDataAccess(orders, stocks, null, stockDocs);
        var settings = new AttachmentStorageProperties(); settings.setLocalRoot(root.toString());
        var storage = new LocalAttachmentObjectStorage(settings); var management = new DocumentManagementService(documents, storage);
        var capture = new DocumentCaptureService(documents, storage, management, orders, new DocumentCaptureProperties(), stockDocs, access);
        var evidence = new BusinessEvidenceService(documents, links, orders, stocks, management, access, new PermissionGuard(), null, stockDocs);
        var keeper = new OperatorContext("库管", RoleCode.WAREHOUSE_KEEPER, DataScope.ALL);
        var outsider = new OperatorContext("其他库管", RoleCode.WAREHOUSE_KEEPER, DataScope.CUSTOMER_RANGE, false, "", List.of("1002"));
        byte[] photo = jpeg("prep-photo");
        for (String status : List.of("PENDING", "POSTED")) {
            String code = "JC-" + status;
            stockDocs.save(new StockDocument(code, "PREP_RECEIPT", status, "CUSTOMER", 1001L, "甲", "WH", "", "", "", "", "", "", "库管", "request-" + status, "2026-10-25", "2026-10-25T09:00:00", "", "IN", List.of(new StockDocumentLine(1, "XY", "件", BigDecimal.ONE, BigDecimal.ONE, "PC-" + status, "", null, true, BigDecimal.ONE, BigDecimal.ONE, "STK-" + status, null))), new com.zhubao.erp.work.domain.WorkSession() { public java.util.Optional<java.sql.Connection> connection() { return java.util.Optional.empty(); } public void onRollback(Runnable undo) { } });
            assertThat(access.canRead(outsider, code)).isFalse();
            assertThatThrownBy(() -> capture.storeVerified("STOCK_DOCUMENT", code, "UNPACK", "a.jpg", "image/jpeg", new java.io.ByteArrayInputStream(photo), photo.length, outsider)).isInstanceOf(com.zhubao.erp.security.error.PermissionDeniedException.class);
            var uploaded = capture.storeVerified("STOCK_DOCUMENT", code, "UNPACK", "a.jpg", "image/jpeg", new java.io.ByteArrayInputStream(photo), photo.length, keeper);
            assertThat(capture.storeVerified("STOCK_DOCUMENT", code, "UNPACK", "a.jpg", "image/jpeg", new java.io.ByteArrayInputStream(photo), photo.length, keeper).code()).isEqualTo(uploaded.code());
            var command = new BindEvidenceCommand(uploaded.code(), "STOCK_DOCUMENT", code, "INTAKE", "UNPACK", "prep_receipt", code, "", "");
            var bound = evidence.bind(command, keeper); assertThat(evidence.bind(command, keeper).linkCode()).isEqualTo(bound.linkCode());
            assertThatThrownBy(() -> evidence.bind(new BindEvidenceCommand(uploaded.code(), "STOCK_DOCUMENT", code, "INTAKE", "UNPACK", "prep_receipt", "FAKE", "", ""), keeper)).hasMessageContaining("真实收货单");
            assertThatThrownBy(() -> evidence.bind(command, outsider)).isInstanceOf(com.zhubao.erp.security.error.PermissionDeniedException.class);
            assertThat(evidence.query("STOCK_DOCUMENT", code, "INTAKE", "prep_receipt", code, keeper).links()).hasSize(1);
        }
        assertThat(stocks.ledgers()).isEmpty(); assertThat(orders.listMotherOrders()).isEmpty();
        assertThatThrownBy(() -> capture.storeVerified("STOCK_DOCUMENT", "JC-FAKE", "UNPACK", "a.jpg", "image/jpeg", new java.io.ByteArrayInputStream(photo), photo.length, keeper)).hasMessageContaining("不存在");
    }
}
