package com.zhubao.erp.sampling.app;

import com.zhubao.erp.business.domain.CustomerProfile;
import com.zhubao.erp.business.infra.InMemoryMasterDataRepository;
import com.zhubao.erp.common.error.BusinessException;
import com.zhubao.erp.document.domain.EvidenceAttachment;
import com.zhubao.erp.document.infra.InMemoryDocumentManagementRepository;
import com.zhubao.erp.inventory.domain.OwnerType;
import com.zhubao.erp.inventory.domain.StockMeasurement;
import com.zhubao.erp.ops.infra.MigrationCatalog;
import com.zhubao.erp.sampling.domain.SamplingDeliveryView;
import com.zhubao.erp.sampling.domain.SamplingMaterialUse;
import com.zhubao.erp.sampling.domain.SamplingOrderView;
import com.zhubao.erp.sampling.infra.InMemorySamplingDeliveryRepository;
import com.zhubao.erp.sampling.infra.InMemorySamplingMaterialRepository;
import com.zhubao.erp.sampling.infra.InMemorySamplingOperationRepository;
import com.zhubao.erp.sampling.infra.InMemorySamplingOrderRepository;
import com.zhubao.erp.sampling.infra.InMemorySamplingVersionRepository;
import com.zhubao.erp.sampling.infra.JdbcSamplingDeliveryRepository;
import com.zhubao.erp.sampling.infra.JdbcSamplingOrderRepository;
import com.zhubao.erp.security.app.PermissionGuard;
import com.zhubao.erp.security.domain.DataScope;
import com.zhubao.erp.security.domain.OperatorContext;
import com.zhubao.erp.security.domain.RoleCode;
import com.zhubao.erp.work.app.BusinessWorkUnit;
import com.zhubao.erp.work.app.JdbcBusinessWorkUnit;
import com.zhubao.erp.work.app.MemoryBusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
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

class SamplingDeliveryTest {

    private final OperatorContext keeper = new OperatorContext("打样员", RoleCode.ADMIN, DataScope.ALL);
    private final PermissionGuard guard = new PermissionGuard();

    @Test
    void 部分交付不结束打样单且保留版本和来源() {
        MemoryBusinessWorkUnit unit = new MemoryBusinessWorkUnit(new InMemoryCommandRequestRepository());
        InMemorySamplingOrderRepository orders = new InMemorySamplingOrderRepository();
        InMemorySamplingVersionRepository versions = new InMemorySamplingVersionRepository();
        InMemorySamplingOperationRepository operations = new InMemorySamplingOperationRepository();
        InMemorySamplingMaterialRepository materials = new InMemorySamplingMaterialRepository();
        InMemoryDocumentManagementRepository documents = new InMemoryDocumentManagementRepository();
        SamplingOrderView order = open(orders, unit, "s05-order-01");
        new SamplingVersionService(orders, versions, unit, guard).add(
                "s05-ver-0001", keeper, order.samplingCode(), "MODEL", "直径 18", "", "", "", "", "", "", null);
        new SamplingVersionService(orders, versions, unit, guard).confirm(
                "s05-yes-0001", keeper, order.samplingCode(), "MODEL", 1, "", "");
        documents.saveAttachment(photo("ATT-1", order.samplingCode()));
        new SamplingOperationService(orders, versions, operations, documents, unit, guard).report(
                "s05-rpt-0001", keeper, order.samplingCode(), "SAMPLE_MAKE", "MODEL", 1,
                List.of(), new BigDecimal("5"), "颗", "ATT-1");
        unit.execute(new CommandCall("s05-use-seed", keeper.operator(), "SEED_USE", "SAMPLING_MATERIAL", order.samplingCode(), "seed"),
                session -> {
                    materials.insert(new SamplingMaterialUse(
                            "SU-S05", order.samplingCode(), OwnerType.COMPANY, "RC-1", null, "LY-1", "ITEM", "B1",
                            new BigDecimal("5"), "颗", StockMeasurement.unknown(), BigDecimal.ZERO, null, 1
                    ), session);
                    return "SU-S05";
                });
        SamplingDeliveryService service = new SamplingDeliveryService(
                orders, versions, operations, materials, new InMemorySamplingDeliveryRepository(), unit, guard);

        SamplingDeliveryView first = service.deliver(
                "s05-dlv-0001", keeper, order.samplingCode(), "MODEL", 1, "SU-S05", new BigDecimal("2"), "颗", "ATT-DLV-1");
        SamplingDeliveryView replay = service.deliver(
                "s05-dlv-0001", keeper, order.samplingCode(), "MODEL", 1, "SU-S05", new BigDecimal("2"), "颗", "ATT-DLV-1");
        assertThat(replay.deliveryId()).isEqualTo(first.deliveryId());
        assertThat(first.remainingQuantity()).isEqualByComparingTo("3");
        assertThat(first.orderClosed()).isFalse();
        assertThat(first.orderStatus()).isEqualTo("OPEN");
        assertThat(first.feeState()).isEqualTo("UNCONFIRMED");
        assertThat(first.versionKind()).isEqualTo("MODEL");
        assertThat(first.versionNo()).isEqualTo(1);
        assertThat(first.sourceOwner()).isEqualTo("COMPANY");
        assertThat(orders.find(order.samplingCode()).orElseThrow().status().name()).isEqualTo("OPEN");

        SamplingDeliveryView rest = service.deliver(
                "s05-dlv-0002", keeper, order.samplingCode(), "MODEL", 1, "", new BigDecimal("3"), "颗", "ATT-DLV-2");
        assertThat(rest.remainingQuantity()).isEqualByComparingTo("0");
        assertThat(rest.orderClosed()).isFalse();
        assertThat(orders.find(order.samplingCode()).orElseThrow().version()).isEqualTo(1);
        assertThatThrownBy(() -> service.deliver(
                "s05-dlv-0003", keeper, order.samplingCode(), "MODEL", 1, "", new BigDecimal("1"), "颗", "ATT-DLV-3"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("交付数量超过已报工份额");
        assertThat(service.list(keeper, order.samplingCode())).hasSize(2);
    }

    @Test
    void jdbc部分交付失败回滚且订单仍进行中() throws Exception {
        JdbcDataSource dataSource = dataSource();
        JdbcBusinessWorkUnit unit = new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource));
        JdbcSamplingOrderRepository orders = new JdbcSamplingOrderRepository(dataSource);
        InMemorySamplingVersionRepository versions = new InMemorySamplingVersionRepository();
        InMemorySamplingOperationRepository operations = new InMemorySamplingOperationRepository();
        InMemoryDocumentManagementRepository documents = new InMemoryDocumentManagementRepository();
        SamplingOrderView order = open(orders, unit, "s05-jdbc-od");
        new SamplingVersionService(orders, versions, unit, guard).add(
                "s05-jdbc-v1", keeper, order.samplingCode(), "BEAD", "孔径", "", "", "", "", "", "", null);
        new SamplingVersionService(orders, versions, unit, guard).confirm(
                "s05-jdbc-y1", keeper, order.samplingCode(), "BEAD", 1, "", "");
        documents.saveAttachment(photo("ATT-J1", order.samplingCode()));
        new SamplingOperationService(orders, versions, operations, documents, unit, guard).report(
                "s05-jdbc-r1", keeper, order.samplingCode(), "SAMPLE_BEAD", "BEAD", 1,
                List.of(), new BigDecimal("4"), "颗", "ATT-J1");
        JdbcSamplingDeliveryRepository deliveries = new JdbcSamplingDeliveryRepository(dataSource);
        SamplingDeliveryService service = new SamplingDeliveryService(
                orders, versions, operations, new InMemorySamplingMaterialRepository(), deliveries, unit, guard);
        SamplingDeliveryView saved = service.deliver(
                "s05-jdbc-d1", keeper, order.samplingCode(), "BEAD", 1, "", new BigDecimal("1"), "颗", "ATT-J-D1");
        assertThat(saved.remainingQuantity()).isEqualByComparingTo("3");
        assertThat(saved.orderClosed()).isFalse();
        SamplingDeliveryService failing = new SamplingDeliveryService(
                orders, versions, operations, new InMemorySamplingMaterialRepository(), deliveries,
                new JdbcBusinessWorkUnit(dataSource, new JdbcCommandRequestRepository(dataSource), () -> {
                    throw new IllegalStateException("失败");
                }), guard);
        assertThatThrownBy(() -> failing.deliver(
                "s05-jdbc-x1", keeper, order.samplingCode(), "BEAD", 1, "", new BigDecimal("1"), "颗", "ATT-J-D2"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(deliveries.list(order.samplingCode())).hasSize(1);
        assertThat(orders.find(order.samplingCode()).orElseThrow().status().name()).isEqualTo("OPEN");
    }

    private SamplingOrderView open(com.zhubao.erp.sampling.domain.SamplingOrderRepository orders, BusinessWorkUnit unit, String requestId) {
        InMemoryMasterDataRepository customers = new InMemoryMasterDataRepository();
        CustomerProfile customer = customers.saveCustomer(new CustomerProfile(0, "", "甲客户", "", "", "", "", true));
        return new SamplingOrderService(customers, orders, unit, guard).open(
                requestId, keeper, customer.customerId(), "CUSTOMER_SUPPLIED", "样品", "张三", "", "");
    }

    private static EvidenceAttachment photo(String code, String businessCode) {
        return new EvidenceAttachment(code, "DOC-" + code, "SAMPLING", businessCode, code + ".jpg", "image/jpeg",
                "mem", "LOCAL", "UPLOAD", "可用", "打样员", "sum", "2026-10-31T10:00:00", "", "", "SAMPLING");
    }

    private static JdbcDataSource dataSource() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_UPPER=false;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            for (String file : new String[]{
                    "V44__m3_command_request.sql", "V114__m3_sampling_order.sql", "V118__m3_sampling_delivery.sql"}) {
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
