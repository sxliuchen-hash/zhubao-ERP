package com.zhubao.erp.sampling.app;

import com.zhubao.erp.business.domain.CustomerProfile;
import com.zhubao.erp.business.infra.InMemoryMasterDataRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.document.domain.EvidenceAttachment;
import com.zhubao.erp.document.infra.InMemoryDocumentManagementRepository;
import com.zhubao.erp.ops.infra.MigrationCatalog;
import com.zhubao.erp.sampling.domain.SamplingOperationView;
import com.zhubao.erp.sampling.domain.SamplingOrderView;
import com.zhubao.erp.sampling.infra.InMemorySamplingOperationRepository;
import com.zhubao.erp.sampling.infra.InMemorySamplingOrderRepository;
import com.zhubao.erp.sampling.infra.InMemorySamplingVersionRepository;
import com.zhubao.erp.sampling.infra.JdbcSamplingOperationRepository;
import com.zhubao.erp.sampling.infra.JdbcSamplingOrderRepository;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.security.error.PermissionDeniedException;
import com.zhubao.erp.work.app.JdbcBusinessWorkUnit;
import com.zhubao.erp.work.app.MemoryBusinessWorkUnit;
import com.zhubao.erp.work.infra.InMemoryCommandRequestRepository;
import com.zhubao.erp.work.infra.JdbcCommandRequestRepository;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SamplingOperationReportTest {

    private final OperatorContext keeper = new OperatorContext("打样员", RoleCode.ADMIN, DataScope.ALL);
    private final PermissionGuard guard = new PermissionGuard();

    @Test
    void 已确认版本和照片才能报工且不跑普通工序() {
        Fixture fixture = memory();
        SamplingOrderView order = open(fixture, "s04-order-01");
        confirm(fixture, order.samplingCode(), "s04-ver-0001", "s04-yes-0001");
        fixture.versions.add("s04-ver-0002", keeper, order.samplingCode(), "MODEL", "直径 19", "",
                "", "", "", "", "", null);
        photo(fixture.documents, "ATT-OK", order.samplingCode(), "image/jpeg", "可用", "SAMPLING");

        SamplingOperationView made = fixture.operations.report(
                "s04-rpt-0001", keeper, order.samplingCode(), "SAMPLE_MAKE", "MODEL", 1,
                List.of("CARVE"), new BigDecimal("2"), "颗", "ATT-OK");
        SamplingOperationView replay = fixture.operations.report(
                "s04-rpt-0001", keeper, order.samplingCode(), "SAMPLE_MAKE", "MODEL", 1,
                List.of("CARVE"), new BigDecimal("2"), "颗", "ATT-OK");
        assertThat(replay.reportId()).isEqualTo(made.reportId());
        assertThat(made.stepName()).isEqualTo("选料与加工");
        assertThat(made.makingChoiceLabel()).isEqualTo("雕刻");
        assertThat(made.quantity()).isEqualByComparingTo("2");
        assertThat(fixture.operations.list(keeper, order.samplingCode())).hasSize(1);
        assertThat(fixture.orders.find(order.samplingCode()).orElseThrow().status().name()).isEqualTo("OPEN");

        photo(fixture.documents, "ATT-FLAT", order.samplingCode(), "image/png", "可用", "SAMPLING");
        fixture.operations.report("s04-rpt-0002", keeper, order.samplingCode(), "SAMPLE_FLAT", "MODEL", 1,
                List.of(), new BigDecimal("1"), "颗", "ATT-FLAT");
        assertThat(fixture.operations.list(keeper, order.samplingCode())).hasSize(2);

        assertThatThrownBy(() -> fixture.operations.report(
                "s04-bad-step", keeper, order.samplingCode(), "SHAPE", "MODEL", 1,
                List.of(), new BigDecimal("1"), "颗", "ATT-OK"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("打样不使用普通加工工序");
        photo(fixture.documents, "ATT-BOTH", order.samplingCode(), "image/jpeg", "可用", "SAMPLING");
        var both = fixture.operations.report(
                "s04-both-01", keeper, order.samplingCode(), "SAMPLE_MAKE", "MODEL", 1,
                List.of("CARVE", "SHAPE"), new BigDecimal("1"), "颗", "ATT-BOTH");
        assertThat(both.makingChoiceLabel()).isEqualTo("雕刻和车型");
        assertThatThrownBy(() -> fixture.operations.report(
                "s04-bad-photo", keeper, order.samplingCode(), "SAMPLE_MODEL", "MODEL", 1,
                List.of(), new BigDecimal("1"), "颗", ""))
                .isInstanceOf(BusinessException.class)
                .hasMessage("没有有效的现场照片");
        assertThatThrownBy(() -> fixture.operations.report(
                "s04-bad-ver", keeper, order.samplingCode(), "SAMPLE_MODEL", "MODEL", 2,
                List.of(), new BigDecimal("1"), "颗", "ATT-FLAT"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("版本与已确认版本不符");
        photo(fixture.documents, "ATT-OTHER", "DY-20260101-0001", "image/jpeg", "可用", "SAMPLING");
        assertThatThrownBy(() -> fixture.operations.report(
                "s04-bad-own", keeper, order.samplingCode(), "SAMPLE_BEAD", "MODEL", 1,
                List.of(), new BigDecimal("1"), "颗", "ATT-OTHER"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("照片不属于当前打样单");
        assertThatThrownBy(() -> fixture.operations.report(
                "s04-bad-re", keeper, order.samplingCode(), "SAMPLE_BEAD", "MODEL", 1,
                List.of(), new BigDecimal("1"), "颗", "ATT-OK"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("这张照片已经用于其他业务，不能当作本次拍摄");
        assertThat(fixture.operations.list(keeper, order.samplingCode())).hasSize(3);

        OperatorContext worker = new OperatorContext("加工", RoleCode.OPERATOR, DataScope.ALL);
        assertThatThrownBy(() -> fixture.operations.report(
                "s04-deny-01", worker, order.samplingCode(), "SAMPLE_BEAD", "MODEL", 1,
                List.of(), new BigDecimal("1"), "颗", "ATT-FLAT"))
                .isInstanceOf(PermissionDeniedException.class);
    }

    @Test
    void 失败不留下报工() {
        Fixture fixture = memory();
        SamplingOrderView order = open(fixture, "s04-order-02");
        confirm(fixture, order.samplingCode(), "s04-ver-0003", "s04-yes-0003");
        photo(fixture.documents, "ATT-FAIL", order.samplingCode(), "image/jpeg", "可用", "SAMPLING");
        SamplingOperationService failing = new SamplingOperationService(
                fixture.orders, fixture.versionRows, fixture.operationRows, fixture.documents,
                new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository(), () -> {
                    throw new IllegalStateException("失败");
                }), guard);
        assertThatThrownBy(() -> failing.report(
                "s04-fail-01", keeper, order.samplingCode(), "SAMPLE_BEAD", "MODEL", 1,
                List.of(), new BigDecimal("1"), "颗", "ATT-FAIL"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(fixture.operationRows.list(order.samplingCode())).isEmpty();
        assertThat(fixture.orders.find(order.samplingCode()).orElseThrow().version()).isEqualTo(1);
    }

    @Test
    void jdbc报工可回放且失败回滚() throws Exception {
        JdbcDataSource dataSource = dataSource();
        JdbcBusinessWorkUnit unit = new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource));
        JdbcSamplingOrderRepository orders = new JdbcSamplingOrderRepository(dataSource);
        InMemorySamplingVersionRepository versions = new InMemorySamplingVersionRepository();
        InMemoryDocumentManagementRepository documents = new InMemoryDocumentManagementRepository();
        JdbcSamplingOperationRepository reports = new JdbcSamplingOperationRepository(dataSource);
        InMemoryMasterDataRepository customers = new InMemoryMasterDataRepository();
        CustomerProfile customer = customers.saveCustomer(new CustomerProfile(0, "", "甲客户", "", "", "", "", true));
        SamplingOrderView order = new SamplingOrderService(customers, orders, unit, guard).open(
                "s04-jdbc-od", keeper, customer.customerId(), "COMPANY_PREPARED", "样品", "张三", "", "");
        SamplingVersionService versionService = new SamplingVersionService(orders, versions, unit, guard);
        versionService.add("s04-jdbc-v1", keeper, order.samplingCode(), "BEAD", "孔径 2", "", "", "", "", "", "", null);
        versionService.confirm("s04-jdbc-y1", keeper, order.samplingCode(), "BEAD", 1, "", "");
        photo(documents, "ATT-JDBC", order.samplingCode(), "image/jpeg", "可用", "SAMPLING");
        SamplingOperationService service = new SamplingOperationService(orders, versions, reports, documents, unit, guard);
        SamplingOperationView saved = service.report(
                "s04-jdbc-r1", keeper, order.samplingCode(), "SAMPLE_BEAD", "BEAD", 1,
                List.of(), new BigDecimal("3"), "颗", "ATT-JDBC");
        SamplingOperationView replay = service.report(
                "s04-jdbc-r1", keeper, order.samplingCode(), "SAMPLE_BEAD", "BEAD", 1,
                List.of(), new BigDecimal("3"), "颗", "ATT-JDBC");
        assertThat(replay.reportId()).isEqualTo(saved.reportId());
        assertThat(reports.list(order.samplingCode())).hasSize(1);
        SamplingOperationService failing = new SamplingOperationService(orders, versions, reports, documents,
                new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource), () -> {
                    throw new IllegalStateException("失败");
                }), guard);
        photo(documents, "ATT-JDBC2", order.samplingCode(), "image/jpeg", "可用", "SAMPLING");
        assertThatThrownBy(() -> failing.report(
                "s04-jdbc-x1", keeper, order.samplingCode(), "SAMPLE_FLAT", "BEAD", 1,
                List.of(), new BigDecimal("1"), "颗", "ATT-JDBC2"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(reports.list(order.samplingCode())).hasSize(1);
        assertThat(orders.find(order.samplingCode()).orElseThrow().status().name()).isEqualTo("OPEN");
    }

    private Fixture memory() {
        MemoryBusinessWorkUnit unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        return new Fixture(new InMemorySamplingOrderRepository(), new InMemorySamplingVersionRepository(),
                new InMemorySamplingOperationRepository(), new InMemoryDocumentManagementRepository(), unit);
    }

    private SamplingOrderView open(Fixture fixture, String requestId) {
        InMemoryMasterDataRepository customers = new InMemoryMasterDataRepository();
        CustomerProfile customer = customers.saveCustomer(new CustomerProfile(0, "", "甲客户", "", "", "", "", true));
        return new SamplingOrderService(customers, fixture.orders, fixture.unit, guard).open(
                requestId, keeper, customer.customerId(), "CUSTOMER_SUPPLIED", "样品", "张三", "", "");
    }

    private void confirm(Fixture fixture, String samplingCode, String addId, String confirmId) {
        fixture.versions.add(addId, keeper, samplingCode, "MODEL", "直径 18", "", "", "", "", "", "", null);
        fixture.versions.confirm(confirmId, keeper, samplingCode, "MODEL", 1, "", "");
    }

    private static void photo(InMemoryDocumentManagementRepository documents, String code, String businessCode,
                              String media, String status, String objectType) {
        documents.saveAttachment(new EvidenceAttachment(
                code, "DOC-" + code, "SAMPLING", businessCode, code + ".jpg", media, "mem", "LOCAL", "UPLOAD",
                status, "打样员", "sum", "2026-10-31T10:00:00", "", "", objectType));
    }

    private static JdbcDataSource dataSource() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String file : new String[]{
                    "V44__m3_command_request.sql", "V114__m3_sampling_order.sql", "V117__m3_sampling_operation.sql"}) {
                String sql = Files.readString(Path.of("src/main/resources/db/mysql/" + file), StandardCharsets.UTF_8);
                for (String item : MigrationCatalog.statements(sql)) {
                    String trimmed = item.trim();
                    if (trimmed.regionMatches(true, 0, "create", 0, 6)) {
                        statement.execute(item);
                    }
                }
            }
        }
        return dataSource;
    }

    private final class Fixture {
        private final InMemorySamplingOrderRepository orders;
        private final InMemorySamplingVersionRepository versionRows;
        private final InMemorySamplingOperationRepository operationRows;
        private final InMemoryDocumentManagementRepository documents;
        private final MemoryBusinessWorkUnit unit;
        private final SamplingVersionService versions;
        private final SamplingOperationService operations;

        private Fixture(InMemorySamplingOrderRepository orders, InMemorySamplingVersionRepository versionRows,
                        InMemorySamplingOperationRepository operationRows, InMemoryDocumentManagementRepository documents,
                        MemoryBusinessWorkUnit unit) {
            this.orders = orders;
            this.versionRows = versionRows;
            this.operationRows = operationRows;
            this.documents = documents;
            this.unit = unit;
            this.versions = new SamplingVersionService(orders, versionRows, unit, guard);
            this.operations = new SamplingOperationService(orders, versionRows, operationRows, documents, unit, guard);
        }
    }
}
