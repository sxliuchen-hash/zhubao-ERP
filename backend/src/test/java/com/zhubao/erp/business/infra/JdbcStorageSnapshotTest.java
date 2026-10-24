package com.zhubao.erp.business.infra;

import com.zhubao.erp.business.app.MasterDataService;
import com.zhubao.erp.business.domain.*;
import com.zhubao.erp.inventory.domain.*;
import com.zhubao.erp.inventory.infra.JdbcStockDocumentRepository;
import com.zhubao.erp.ops.infra.MigrationCatalog;
import com.zhubao.erp.work.app.JdbcBusinessWorkUnit;
import com.zhubao.erp.work.domain.CommandCall;
import com.zhubao.erp.work.infra.JdbcCommandRequestRepository;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

/** 使用真实 JDBC 连接执行迁移与回滚；H2 的 MySQL 兼容模式不等同于目标 MySQL 验收。 */
class JdbcStorageSnapshotTest {
    @Test void 标签换贴版本使用JDBC保存回滚及重读() throws Exception {
        var ds = new JdbcDataSource(); ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        try (var c = ds.getConnection(); var statement = c.createStatement()) {
            for (String migration : List.of("V44__m3_command_request.sql", "V75__m3_label_storage_edition.sql"))
                for (String part : MigrationCatalog.statements(Files.readString(Path.of("src/main/resources/db/mysql", migration)))) statement.execute(part);
        }
        var repo = new com.zhubao.erp.label.infra.JdbcLabelStorageEditionRepository(ds);
        var unit = new JdbcBusinessWorkUnit(ds, new JdbcCommandRequestRepository(ds));
        var edition = new com.zhubao.erp.label.domain.LabelStorageEdition("MD-1", 1, List.of(com.zhubao.erp.label.domain.StorageLabelSource.of("MD-1:1", "明细1", "XY", new StorageRequirements(List.of("COLD", "VACUUM"), ""))), "edition-001", "核对换贴", "收货员", "2026-10-25T10:00:00");
        var call = new CommandCall("edition-001", "收货员", "LABEL_STORAGE_EDITION", "MOTHER_ORDER", "MD-1", "换贴");
        assertThatThrownBy(() -> unit.execute(call, session -> { repo.save(edition, session); throw new IllegalStateException("回滚探针"); })).hasMessageContaining("回滚探针");
        assertThat(repo.latest("MD-1", null)).isEmpty();
        unit.execute(call, session -> { repo.save(edition, session); return "1"; });
        assertThat(new com.zhubao.erp.label.infra.JdbcLabelStorageEditionRepository(ds).latest("MD-1", null)).contains(edition);
    }

    @Test void 来源快照持久化重读和同事务失败回滚() throws Exception {
        var ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:" + UUID.randomUUID() + ";MODE=MySQL;DB_CLOSE_DELAY=-1");
        try (var c = ds.getConnection(); var sql = c.createStatement()) {
            sql.execute("create table biz_mother_order(code varchar(80) primary key)");
            sql.execute("create table biz_work_order(code varchar(90) primary key)");
            for (String migration : List.of("V44__m3_command_request.sql", "V59__m3_customer_material_catalog.sql", "V60__m3_receipt_line_allocation.sql", "V70__m3_stock_document.sql", "V74__m3_material_storage_requirements.sql")) {
                for (String part : MigrationCatalog.statements(Files.readString(Path.of("src/main/resources/db/mysql", migration)))) sql.execute(part);
            }
            sql.execute("insert into biz_mother_order values ('MD-1')");
            sql.execute("insert into biz_work_order values ('WO-1')");
        }
        var master = new MasterDataService(new JdbcMasterDataRepository(ds));
        var saved = master.saveMaterial(new MaterialProfile("XY", "星月", "", "", true, List.of("COLD", "VACUUM"), "密封保存"));
        master.saveMaterial(new MaterialProfile("XY", "星月", "", "旧请求更新", true));
        assertThat(new JdbcMasterDataRepository(ds).findMaterial("XY").orElseThrow().storageConditions()).containsExactly("COLD", "VACUUM");
        assertThat(master.storageChanges("XY")).hasSize(1);
        var snapshot = StorageRequirements.of(saved);
        var docs = new JdbcBusinessDocumentRepository(ds);
        var stock = new JdbcStockDocumentRepository(ds);
        var unit = new JdbcBusinessWorkUnit(ds, new JdbcCommandRequestRepository(ds));
        var call = new CommandCall("snapshot-1", "收货员", "TEST", "MOTHER_ORDER", "MD-1", "快照");
        assertThatThrownBy(() -> unit.execute(call, session -> {
            docs.saveReceiptLine("MD-1", new ReceiptLine(1, "XY", "星月", "珠子", BigDecimal.ONE, "件", BigDecimal.ONE, "STK-1", snapshot), session);
            throw new IllegalStateException("故障探针");
        })).hasMessageContaining("故障探针");
        try (var c = ds.getConnection(); var sql = c.createStatement(); var rs = sql.executeQuery("select count(*) from biz_receipt_line")) { rs.next(); assertThat(rs.getInt(1)).isZero(); }
        unit.execute(call, session -> {
            docs.saveReceiptLine("MD-1", new ReceiptLine(1, "XY", "星月", "珠子", BigDecimal.ONE, "件", BigDecimal.ONE, "STK-1", snapshot), session);
            docs.saveChildAllocation(new ChildLineAllocation("WO-1", "MD-1", 1, "XY", BigDecimal.ONE, BigDecimal.ONE, "STK-2", snapshot), session);
            stock.save(new StockDocument("SC-1", "PREP_RECEIPT", "POSTED", "CUSTOMER", 1001L, "恒信珠宝", "WH", "", "", "", "", "", "", "收货员", "receipt-1", "2026-10-25", "2026-10-25T09:00:00", "", "IN", List.of(new StockDocumentLine(1, "XY", "件", BigDecimal.ONE, BigDecimal.ONE, "PC-REAL", "", null, true, BigDecimal.ONE, BigDecimal.ONE, "STK-3", snapshot))), session);
            return "ok";
        });
        master.saveMaterial(new MaterialProfile("XY", "星月", "", "", true, List.of("DARK"), "新说明"));
        assertThat(new JdbcBusinessDocumentRepository(ds).listReceiptLines("MD-1").get(0).storageRequirements()).isEqualTo(snapshot);
        assertThat(docs.listChildAllocations("MD-1", null).get(0).storageRequirements()).isEqualTo(snapshot);
        assertThat(new JdbcStockDocumentRepository(ds).find("SC-1").orElseThrow().lines().get(0).storageRequirements()).isEqualTo(snapshot);
    }
}
