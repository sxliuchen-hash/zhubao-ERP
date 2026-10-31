package com.zhubao.erp.sampling.app;

import com.zhubao.erp.business.domain.CustomerProfile;
import com.zhubao.erp.business.domain.MotherOrderRecord;
import com.zhubao.erp.business.infra.InMemoryBusinessDocumentRepository;
import com.zhubao.erp.business.infra.InMemoryMasterDataRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.ops.infra.MigrationCatalog;
import com.zhubao.erp.sampling.domain.SamplingOrderView;
import com.zhubao.erp.sampling.domain.SamplingProductionLinkView;
import com.zhubao.erp.sampling.infra.InMemorySamplingOrderRepository;
import com.zhubao.erp.sampling.infra.InMemorySamplingProductionLinkRepository;
import com.zhubao.erp.sampling.infra.InMemorySamplingVersionRepository;
import com.zhubao.erp.sampling.infra.JdbcSamplingOrderRepository;
import com.zhubao.erp.sampling.infra.JdbcSamplingProductionLinkRepository;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.work.app.BusinessWorkUnit;
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
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SamplingProductionReferenceTest {

    private final OperatorContext keeper = new OperatorContext("打样员", RoleCode.ADMIN, DataScope.ALL);
    private final PermissionGuard guard = new PermissionGuard();

    @Test
    void 引用快照不随改版变化且取消关联不删除样品() {
        MemoryBusinessWorkUnit unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        InMemoryBusinessDocumentRepository documents = new InMemoryBusinessDocumentRepository();
        documents.saveMotherOrder(mother("MD-20261031-0001", "原加工要求"));
        documents.saveMotherOrder(mother("MD-20261031-0002", "另一张母单"));
        InMemorySamplingOrderRepository orders = new InMemorySamplingOrderRepository();
        InMemorySamplingVersionRepository versions = new InMemorySamplingVersionRepository();
        SamplingOrderView order = open(orders, unit, "s08-order-01");
        SamplingVersionService versionService = new SamplingVersionService(orders, versions, unit, guard);
        versionService.add("s08-ver-0001", keeper, order.samplingCode(), "MODEL", "直径 18", "", "", "", "", "", "", null);
        versionService.confirm("s08-yes-0001", keeper, order.samplingCode(), "MODEL", 1, "", "");
        SamplingProductionLinkService service = new SamplingProductionLinkService(
                documents, orders, versions, new InMemorySamplingProductionLinkRepository(), unit, guard);

        SamplingProductionLinkView linked = service.link(
                "s08-link-001", keeper, "MD-20261031-0001", order.samplingCode(), "MODEL", 1);
        SamplingProductionLinkView replay = service.link(
                "s08-link-001", keeper, "MD-20261031-0001", order.samplingCode(), "MODEL", 1);
        assertThat(replay.snapshotText()).isEqualTo(linked.snapshotText());
        assertThat(linked.snapshotText()).isEqualTo("直径 18");
        assertThat(linked.linked()).isTrue();
        assertThat(documents.findMotherOrderByCode("MD-20261031-0001").orElseThrow().processingRequirement())
                .isEqualTo("原加工要求");

        versionService.add("s08-ver-0002", keeper, order.samplingCode(), "MODEL", "直径 19", "", "", "", "", "", "", null);
        assertThat(service.find(keeper, "MD-20261031-0001").snapshotText()).isEqualTo("直径 18");
        assertThat(service.find(keeper, "MD-20261031-0001").versionNo()).isEqualTo(1);
        assertThat(service.find(keeper, "MD-20261031-0002").linked()).isFalse();

        SamplingProductionLinkView removed = service.unlink("s08-off-0001", keeper, "MD-20261031-0001");
        assertThat(removed.linked()).isFalse();
        assertThat(versions.find(order.samplingCode(), com.zhubao.erp.sampling.domain.SamplingVersionKind.MODEL, 1)).isPresent();
        assertThat(documents.findMotherOrderByCode("MD-20261031-0001").orElseThrow().processingRequirement())
                .isEqualTo("原加工要求");
        assertThatThrownBy(() -> service.link(
                "s08-bad-0001", keeper, order.samplingCode(), order.samplingCode(), "MODEL", 1))
                .isInstanceOf(BusinessException.class)
                .hasMessage("只能引用到普通加工母单");
    }

    @Test
    void jdbc引用可回放且失败不留下关联() throws Exception {
        JdbcDataSource dataSource = dataSource();
        JdbcBusinessWorkUnit unit = new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource));
        InMemoryBusinessDocumentRepository documents = new InMemoryBusinessDocumentRepository();
        documents.saveMotherOrder(mother("MD-20261031-0008", "原加工要求"));
        JdbcSamplingOrderRepository orders = new JdbcSamplingOrderRepository(dataSource);
        InMemorySamplingVersionRepository versions = new InMemorySamplingVersionRepository();
        SamplingOrderView order = open(orders, unit, "s08-jdbc-od");
        new SamplingVersionService(orders, versions, unit, guard).add(
                "s08-jdbc-v1", keeper, order.samplingCode(), "BEAD", "孔径 2", "", "", "", "", "", "", null);
        new SamplingVersionService(orders, versions, unit, guard).confirm(
                "s08-jdbc-y1", keeper, order.samplingCode(), "BEAD", 1, "", "");
        JdbcSamplingProductionLinkRepository links = new JdbcSamplingProductionLinkRepository(dataSource);
        SamplingProductionLinkService service = new SamplingProductionLinkService(documents, orders, versions, links, unit, guard);
        SamplingProductionLinkView linked = service.link(
                "s08-jdbc-l1", keeper, "MD-20261031-0008", order.samplingCode(), "BEAD", 1);
        assertThat(linked.snapshotText()).isEqualTo("孔径 2");
        assertThat(documents.findMotherOrderByCode("MD-20261031-0008").orElseThrow().processingRequirement())
                .isEqualTo("原加工要求");
        SamplingProductionLinkService failing = new SamplingProductionLinkService(
                documents, orders, versions, links,
                new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource), () -> {
                    throw new IllegalStateException("失败");
                }), guard);
        documents.saveMotherOrder(mother("MD-20261031-0009", "保持原文"));
        assertThatThrownBy(() -> failing.link(
                "s08-jdbc-x1", keeper, "MD-20261031-0009", order.samplingCode(), "BEAD", 1))
                .isInstanceOf(IllegalStateException.class);
        assertThat(links.find("MD-20261031-0009")).isEmpty();
        assertThat(service.find(keeper, "MD-20261031-0009").linked()).isFalse();
    }

    private SamplingOrderView open(com.zhubao.erp.sampling.domain.SamplingOrderRepository orders, BusinessWorkUnit unit, String requestId) {
        InMemoryMasterDataRepository customers = new InMemoryMasterDataRepository();
        CustomerProfile customer = customers.saveCustomer(new CustomerProfile(0, "", "甲客户", "", "", "", "", true));
        return new SamplingOrderService(customers, orders, unit, guard).open(
                requestId, keeper, customer.customerId(), "CUSTOMER_SUPPLIED", "样品", "张三", "", "");
    }

    private static MotherOrderRecord mother(String code, String requirement) {
        return new MotherOrderRecord(
                code, 1L, 1L, "XY", "2026-10-31",
                "", "", "", "", "",
                "珠", "CUSTOMER", true, "WH",
                new BigDecimal("10"), "颗", new BigDecimal("1.0"), "RAW_WAITING", "",
                "", null, "", "", "",
                "备注", "OPEN", "2026-10-31T00:00:00", requirement, "", "甲客户");
    }

    private static JdbcDataSource dataSource() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String file : new String[]{
                    "V44__m3_command_request.sql", "V114__m3_sampling_order.sql", "V121__m3_sampling_production_link.sql"}) {
                String sql = Files.readString(Path.of("src/main/resources/db/mysql/" + file), StandardCharsets.UTF_8);
                for (String item : MigrationCatalog.statements(sql)) {
                    if (item.trim().regionMatches(true, 0, "create", 0, 6)) {
                        statement.execute(item);
                    }
                }
            }
        }
        return dataSource;
    }
}
