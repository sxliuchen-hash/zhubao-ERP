package com.zhubao.erp.ops.infra;

import com.zhubao.erp.ops.domain.SchemaProbe;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Locale;

/**
 * 用数据库元数据核对旧库结构。探针失败表示该版本不能算作已安装。
 */
public final class JdbcSchemaInspector {

    private final DataSource dataSource;

    public JdbcSchemaInspector(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public boolean matches(SchemaProbe probe) {
        return switch (probe.kind()) {
            case TABLE -> tableExists(probe.tableName());
            case COLUMN -> columnExists(probe.tableName(), probe.columnName());
            case ROW -> rowExists(probe);
        };
    }

    public boolean tableExists(String tableName) {
        try (Connection connection = dataSource.getConnection()) {
            return tableExists(connection, tableName);
        } catch (SQLException ex) {
            throw new IllegalStateException("无法检查数据表：" + tableName, ex);
        }
    }

    public boolean hasBusinessTable() {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            try (ResultSet tables = meta.getTables(connection.getCatalog(), connection.getSchema(), "%", new String[]{"TABLE"})) {
                while (tables.next()) {
                    String name = tables.getString("TABLE_NAME");
                    if (name != null && !name.equalsIgnoreCase("schema_migration") && inCurrentDatabase(connection, tables)) {
                        return true;
                    }
                }
            }
            return false;
        } catch (SQLException ex) {
            throw new IllegalStateException("无法检查数据库是否为空", ex);
        }
    }

    public boolean columnExists(String tableName, String columnName) {
        try (Connection connection = dataSource.getConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            return columnExists(meta, connection, tableName, columnName)
                    || columnExists(meta, connection, tableName.toUpperCase(Locale.ROOT), columnName.toUpperCase(Locale.ROOT));
        } catch (SQLException ex) {
            throw new IllegalStateException("无法检查字段：" + tableName + "." + columnName, ex);
        }
    }

    private boolean rowExists(SchemaProbe probe) {
        if (!tableExists(probe.tableName())) {
            return false;
        }
        String sql = "select count(*) from " + probe.tableName() + " where " + probe.columnName() + " = ?";
        try (Connection connection = dataSource.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, probe.expectedValue());
            try (ResultSet rs = statement.executeQuery()) {
                return rs.next() && rs.getLong(1) > 0;
            }
        } catch (SQLException ex) {
            return false;
        }
    }

    private static boolean tableExists(Connection connection, String tableName) throws SQLException {
        if (tableExists(connection, tableName, tableName)) {
            return true;
        }
        return tableExists(connection, tableName.toUpperCase(Locale.ROOT), tableName.toUpperCase(Locale.ROOT));
    }

    private static boolean tableExists(Connection connection, String pattern, String expected) throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        try (ResultSet tables = meta.getTables(connection.getCatalog(), connection.getSchema(), pattern, new String[]{"TABLE"})) {
            while (tables.next()) {
                String name = tables.getString("TABLE_NAME");
                if (name != null && name.equalsIgnoreCase(expected) && inCurrentDatabase(connection, tables)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean columnExists(DatabaseMetaData meta, Connection connection, String tableName, String columnName) throws SQLException {
        try (ResultSet columns = meta.getColumns(connection.getCatalog(), connection.getSchema(), tableName, columnName)) {
            while (columns.next()) {
                String name = columns.getString("COLUMN_NAME");
                if (name != null && name.equalsIgnoreCase(columnName) && inCurrentDatabase(connection, columns)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean inCurrentDatabase(Connection connection, ResultSet row) throws SQLException {
        String catalog = connection.getCatalog();
        String schema = connection.getSchema();
        String rowCatalog = row.getString("TABLE_CAT");
        String rowSchema = row.getString("TABLE_SCHEM");
        if (catalog != null && rowCatalog != null && !catalog.equalsIgnoreCase(rowCatalog)) {
            return false;
        }
        if (schema != null && rowSchema != null && !schema.equalsIgnoreCase(rowSchema)) {
            return false;
        }
        if (rowSchema != null && (rowSchema.equalsIgnoreCase("INFORMATION_SCHEMA")
                || rowSchema.equalsIgnoreCase("PERFORMANCE_SCHEMA")
                || rowSchema.equalsIgnoreCase("SYS")
                || rowSchema.equalsIgnoreCase("SYSTEM_LOBS"))) {
            return false;
        }
        return rowCatalog == null || !(rowCatalog.equalsIgnoreCase("mysql")
                || rowCatalog.equalsIgnoreCase("information_schema")
                || rowCatalog.equalsIgnoreCase("performance_schema")
                || rowCatalog.equalsIgnoreCase("sys"));
    }
}
