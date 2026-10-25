package com.zhubao.erp.inventory.support;

import com.zhubao.erp.ops.infra.MigrationCatalog;
import org.h2.jdbcx.JdbcDataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

/** 使用实际增量脚本与 JDBC；仅适配 H2 的多列 ALTER 和 DROP INDEX 方言。 */
public final class IntakeJdbcFixture {
    private IntakeJdbcFixture() { }
    public static JdbcDataSource create() throws Exception {
        var ds = StockRepositoryFixture.jdbcDataSource();
        try (var c = ds.getConnection(); var sql = c.createStatement()) {
            for (int version : List.of(14, 32, 33, 44, 45, 58, 59, 60, 62, 70, 74, 76)) {
                Path migration;
                try (var files = Files.list(Path.of("src/main/resources/db/mysql"))) {
                    migration = files.filter(p -> p.getFileName().toString().startsWith("V" + version + "__")).findFirst().orElseThrow();
                }
                for (String part : MigrationCatalog.statements(Files.readString(migration))) {
                    String lower = part.toLowerCase(java.util.Locale.ROOT);
                    if (version == 33 && !lower.startsWith("create table")) continue;
                    if (version == 62 && (lower.contains("biz_print_") || lower.startsWith("create table"))) continue;
                    part = part.replaceAll("(?i)drop index (\\w+) on \\w+", "drop index $1");
                    var matcher = java.util.regex.Pattern.compile("(?is)^(alter table \\w+\\s+)(add column .*)$").matcher(part);
                    if (matcher.matches()) {
                        for (String column : matcher.group(2).split("(?i),\\s*(?=add column)")) sql.execute(matcher.group(1) + column);
                    } else sql.execute(part);
                }
            }
        }
        return ds;
    }
}
