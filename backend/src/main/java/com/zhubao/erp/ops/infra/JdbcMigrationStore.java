package com.zhubao.erp.ops.infra;

import com.zhubao.erp.ops.domain.MigrationRecord;
import com.zhubao.erp.ops.domain.MigrationScript;
import com.zhubao.erp.ops.domain.UpgradeStatus;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 迁移版本表。只记录执行状态，不假设失败的结构变更可以整体回滚。
 */
public final class JdbcMigrationStore {

    private final DataSource dataSource;

    public JdbcMigrationStore(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public boolean tableExists() {
        return new JdbcSchemaInspector(dataSource).tableExists("schema_migration");
    }

    public void ensureTable() {
        String sql = """
                create table if not exists schema_migration (
                    version int primary key,
                    script_name varchar(255) not null,
                    checksum char(64) not null,
                    status varchar(20) not null,
                    started_at varchar(40) not null,
                    finished_at varchar(40) null,
                    error_message varchar(2000) null
                )
                """;
        try (Connection connection = dataSource.getConnection();
             Statement statement = connection.createStatement()) {
            statement.execute(sql);
        } catch (SQLException ex) {
            throw new IllegalStateException("无法创建迁移版本表", ex);
        }
    }

    public List<MigrationRecord> findAll() {
        if (!tableExists()) {
            return List.of();
        }
        String sql = """
                select version, script_name, checksum, status, started_at, finished_at, error_message
                  from schema_migration
                 order by version
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet rs = statement.executeQuery()) {
            List<MigrationRecord> records = new ArrayList<>();
            while (rs.next()) {
                records.add(new MigrationRecord(
                        rs.getInt("version"),
                        rs.getString("script_name"),
                        rs.getString("checksum"),
                        UpgradeStatus.valueOf(rs.getString("status")),
                        rs.getString("started_at"),
                        rs.getString("finished_at"),
                        rs.getString("error_message")
                ));
            }
            return List.copyOf(records);
        } catch (SQLException ex) {
            throw new IllegalStateException("无法读取迁移版本表", ex);
        }
    }

    public void insertBaseline(MigrationScript script) {
        insert(script, UpgradeStatus.SUCCESS, now(), now(), "基线核对后登记，未重放脚本");
    }

    public void insertApplying(MigrationScript script) {
        insert(script, UpgradeStatus.APPLYING, now(), null, null);
    }

    public void markSuccess(int version) {
        update(version, UpgradeStatus.SUCCESS, null);
    }

    public void markFailed(int version, String message) {
        update(version, UpgradeStatus.FAILED, limit(message));
    }

    public void executeScript(String sql) {
        List<String> statements = MigrationCatalog.statements(sql);
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(true);
            for (int i = 0; i < statements.size(); i++) {
                try (Statement statement = connection.createStatement()) {
                    statement.execute(statements.get(i));
                } catch (SQLException ex) {
                    throw new IllegalStateException("第 " + (i + 1) + " 条语句失败：" + abbreviate(statements.get(i))
                            + "；" + ex.getMessage(), ex);
                }
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("执行迁移脚本失败", ex);
        }
    }

    private void insert(MigrationScript script, UpgradeStatus status, String startedAt, String finishedAt, String message) {
        String sql = """
                insert into schema_migration(
                    version, script_name, checksum, status, started_at, finished_at, error_message
                ) values (?, ?, ?, ?, ?, ?, ?)
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setInt(1, script.version());
            statement.setString(2, script.fileName());
            statement.setString(3, script.checksum());
            statement.setString(4, status.name());
            statement.setString(5, startedAt);
            statement.setString(6, finishedAt);
            statement.setString(7, message);
            statement.executeUpdate();
        } catch (SQLException ex) {
            throw new IllegalStateException("无法写入迁移版本 V" + script.version(), ex);
        }
    }

    private void update(int version, UpgradeStatus status, String message) {
        String sql = """
                update schema_migration
                   set status = ?, finished_at = ?, error_message = ?
                 where version = ?
                """;
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, status.name());
            statement.setString(2, now());
            statement.setString(3, message);
            statement.setInt(4, version);
            if (statement.executeUpdate() != 1) {
                throw new IllegalStateException("迁移版本 V" + version + " 不存在，无法更新状态");
            }
        } catch (SQLException ex) {
            throw new IllegalStateException("无法更新迁移版本 V" + version, ex);
        }
    }

    private static String now() {
        return OffsetDateTime.now().toString();
    }

    private static String limit(String message) {
        if (message == null) {
            return "";
        }
        return message.length() <= 1800 ? message : message.substring(0, 1800);
    }

    private static String abbreviate(String sql) {
        String compact = sql.replaceAll("\\s+", " ").trim();
        return compact.length() <= 160 ? compact : compact.substring(0, 160);
    }
}
