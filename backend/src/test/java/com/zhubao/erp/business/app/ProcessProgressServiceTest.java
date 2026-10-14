package com.zhubao.erp.business.app;

import com.zhubao.erp.business.domain.ConfirmInboundCommand;
import com.zhubao.erp.business.domain.ProcessProgressHead;
import com.zhubao.erp.business.domain.SplitMotherOrderCommand;
import com.zhubao.erp.business.domain.SplitMotherOrderLine;
import com.zhubao.erp.business.infra.InMemoryBusinessDocumentRepository;
import com.zhubao.erp.business.infra.InMemoryProcessProgressRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.inventory.app.StockLedgerService;
import com.zhubao.erp.inventory.infra.InMemoryStockLedgerRepository;
import com.zhubao.erp.scan.app.ScanCodeParser;
import com.zhubao.erp.security.domain.AuthUserAccount;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import com.zhubao.erp.work.app.MemoryBusinessWorkUnit;
import com.zhubao.erp.work.infra.InMemoryCommandRequestRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProcessProgressServiceTest {

    private InMemoryBusinessDocumentRepository documents;
    private InMemoryProcessProgressRepository progress;
    private ProcessProgressService service;
    private AuthUserAccount carver;

    @BeforeEach
    void setUp() {
        documents = new InMemoryBusinessDocumentRepository();
        InMemoryStockLedgerRepository stocks = new InMemoryStockLedgerRepository();
        StockLedgerService ledger = new StockLedgerService(stocks);
        BusinessOperationService operations = new BusinessOperationService(ledger, null, documents);
        BusinessDataAccess access = new BusinessDataAccess(documents, ledger);
        ProcessStationService stations = new ProcessStationService(
                documents, ledger, operations, new ScanCodeParser(), access,
                new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository())
        );
        progress = new InMemoryProcessProgressRepository();
        service = new ProcessProgressService(
                stations, progress, documents, access,
                new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository())
        );
        carver = account("雕刻工 林海", DataScope.ALL, List.of(), "雕刻");
        operations.confirmInbound(inbound("MD-PROGRESS-001"));
        new MotherOrderSplitService(documents, ledger).split(new SplitMotherOrderCommand(
                "MD-PROGRESS-001", "何琳", List.of(
                new SplitMotherOrderLine(new BigDecimal("4"), new BigDecimal("8"), null),
                new SplitMotherOrderLine(new BigDecimal("6"), new BigDecimal("12"), null)
        )));
    }

    @Test
    void splitMotherRequiresAChildAndOneHundredDoesNotDeliver() {
        var mother = service.open("MD-PROGRESS-001", carver);
        assertThat(mother.selectChild()).isTrue();
        assertThat(mother.children()).hasSize(2);
        assertThatThrownBy(() -> service.report("MD-PROGRESS-001", 30, 0, "", "", "progress-mother-01", carver))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请选择具体子单");
        assertThat(progress.listAll()).isEmpty();

        String child = mother.children().get(0).code();
        ProcessProgressHead done = service.report(child, 100, 0, "已做完待交付", "", "progress-done-0001", carver);
        assertThat(done.percent()).isEqualTo(100);
        assertThat(done.version()).isEqualTo(1);
        assertThat(documents.listProcessStationHandoffs(child)).isEmpty();
        assertThat(service.list(carver, child)).singleElement().satisfies(row -> {
            assertThat(row.percent()).isEqualTo(100);
            assertThat(row.businessStatus()).isEqualTo("待正式交付");
            assertThat(row.currentProcess()).isEqualTo("雕刻");
        });
        assertThat(service.report(child, 100, 0, "已做完待交付", "", "progress-done-0001", carver).version()).isEqualTo(1);
        assertThat(service.open(child, carver)).satisfies(opened -> {
            assertThat(opened.percent()).isEqualTo(100);
            assertThat(opened.version()).isEqualTo(1);
            assertThat(opened.orderCode()).isEqualTo(child);
        });
        assertThat(service.history(child, "雕刻", carver)).hasSize(1);
    }

    @Test
    void rejectsStaleVersionAndReasonlessRollback() {
        String child = service.open("MD-PROGRESS-001", carver).children().get(0).code();
        service.report(child, 30, 0, "", "", "progress-thirty-01", carver);
        assertThatThrownBy(() -> service.report(child, 60, 0, "", "", "progress-stale-0001", carver))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("请刷新后重试");
        assertThatThrownBy(() -> service.report(child, 0, 1, "", "", "progress-back-00001", carver))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("必须填写原因");
        ProcessProgressHead corrected = service.report(child, 0, 1, "", "看错工序", "progress-fix-00001", carver);
        assertThat(corrected.percent()).isZero();
        assertThat(service.history(child, "雕刻", carver)).hasSize(2);
    }

    @Test
    void hiddenCustomerCannotListOrOpenTheOrder() {
        AuthUserAccount other = account("其他客户员", DataScope.CUSTOMER_RANGE, List.of("其他客户"), "雕刻");
        assertThat(service.list(other, "")).isEmpty();
        assertThatThrownBy(() -> service.open(service.open("MD-PROGRESS-001", carver).children().get(0).code(), other))
                .isInstanceOf(PermissionDeniedException.class);
    }

    private AuthUserAccount account(String operator, DataScope scope, List<String> customers, String... processes) {
        return new AuthUserAccount(
                "user", "hash", operator, RoleCode.OPERATOR, scope, true, true,
                Instant.now(), 0, null, customers, false, false, List.of(processes)
        );
    }

    private ConfirmInboundCommand inbound(String code) {
        return new ConfirmInboundCommand(
                code, 1001L, 1001L, "AU9999", "2026-10-08", "顺丰", "SF" + code, "",
                "何琳", "何琳", "戒托", "", null, "", "RAW-CUSTOMER",
                new BigDecimal("10"), "件", new BigDecimal("20"), "何琳", "", "测试",
                "内壁刻字", "2026-10-20", "恒信珠宝"
        );
    }
}
