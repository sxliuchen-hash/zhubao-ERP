package com.zhubao.erp.ops.infra;

import com.zhubao.erp.ops.domain.MigrationScript;
import com.zhubao.erp.ops.domain.SchemaProbe;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 从目录或类路径装入迁移。历史文件只读，执行文本可应用明确的语法兼容修正。
 */
public final class MigrationCatalog {

    private static final Pattern FILE_NAME = Pattern.compile("^V(\\d+)__.+\\.sql$", Pattern.CASE_INSENSITIVE);
    private static final Pattern IDENTIFIER = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

    private MigrationCatalog() {
    }

    public static List<MigrationScript> loadDirectory(Path directory) {
        try {
            Map<Integer, SchemaProbe> probes = readProbes(Files.readString(directory.resolve("upgrade-probes.tsv"), StandardCharsets.UTF_8));
            List<Path> files = Files.list(directory)
                    .filter(path -> FILE_NAME.matcher(path.getFileName().toString()).matches())
                    .sorted(Comparator.comparingInt(MigrationCatalog::versionOf))
                    .toList();
            List<MigrationScript> scripts = new ArrayList<>();
            for (Path file : files) {
                String name = file.getFileName().toString();
                int version = versionOf(file);
                byte[] bytes = Files.readAllBytes(file);
                scripts.add(script(version, name, bytes, probes));
            }
            validateContiguous(scripts);
            return List.copyOf(scripts);
        } catch (IOException ex) {
            throw new IllegalStateException("无法读取迁移目录：" + directory, ex);
        }
    }

    public static List<MigrationScript> loadClasspath() {
        ClassLoader loader = MigrationCatalog.class.getClassLoader();
        URL index = loader.getResource("db/mysql/migration-index.txt");
        URL probes = loader.getResource("db/mysql/upgrade-probes.tsv");
        if (index == null || probes == null) {
            throw new IllegalStateException("类路径缺少迁移索引或结构探针");
        }
        try {
            Map<Integer, SchemaProbe> probeMap = readProbes(new String(probes.openStream().readAllBytes(), StandardCharsets.UTF_8));
            List<MigrationScript> scripts = new ArrayList<>();
            for (String line : new String(index.openStream().readAllBytes(), StandardCharsets.UTF_8).split("\\R")) {
                String name = line.trim();
                if (name.isEmpty()) {
                    continue;
                }
                Matcher matcher = FILE_NAME.matcher(name);
                if (!matcher.matches()) {
                    throw new IllegalStateException("无法识别迁移文件：" + name);
                }
                try (InputStream sql = loader.getResourceAsStream("db/mysql/" + name)) {
                    if (sql == null) {
                        throw new IllegalStateException("迁移索引指向不存在的文件：" + name);
                    }
                    byte[] bytes = sql.readAllBytes();
                    scripts.add(script(Integer.parseInt(matcher.group(1)), name, bytes, probeMap));
                }
            }
            scripts.sort(Comparator.comparingInt(MigrationScript::version));
            validateContiguous(scripts);
            return List.copyOf(scripts);
        } catch (IOException ex) {
            throw new IllegalStateException("无法从类路径读取迁移", ex);
        }
    }

    public static List<String> statements(String sql) {
        String withoutComments = stripComments(sql);
        List<String> parts = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean quote = false;
        for (int i = 0; i < withoutComments.length(); i++) {
            char ch = withoutComments.charAt(i);
            if (ch == '\'') {
                if (quote && i + 1 < withoutComments.length() && withoutComments.charAt(i + 1) == '\'') {
                    current.append("''");
                    i++;
                    continue;
                }
                quote = !quote;
                current.append(ch);
                continue;
            }
            if (ch == ';' && !quote) {
                addStatement(parts, current);
                continue;
            }
            current.append(ch);
        }
        addStatement(parts, current);
        return List.copyOf(parts);
    }

    public static String checksum(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 不可用", ex);
        }
    }

    private static MigrationScript script(int version, String name, byte[] bytes, Map<Integer, SchemaProbe> probes) {
        SchemaProbe probe = probes.get(version);
        if (probe == null) {
            throw new IllegalStateException("迁移 V" + version + " 缺少结构探针，不能把未知结构登记为已升级");
        }
        String sql = new String(bytes, StandardCharsets.UTF_8);
        // V36 的月份列与 MySQL 保留字冲突；只修正执行文本，历史文件和版本校验和保持原样。
        if (version == 36 && name.equals("V36__m3_finance_operations.sql")) {
            sql = sql.replaceAll("(?<![A-Za-z0-9_`])year_month(?![A-Za-z0-9_`])", "`year_month`");
        }
        return new MigrationScript(version, name, checksum(bytes), sql, probe);
    }

    private static void validateContiguous(List<MigrationScript> scripts) {
        if (scripts.isEmpty()) {
            throw new IllegalStateException("没有迁移脚本");
        }
        int expected = 1;
        for (MigrationScript script : scripts) {
            if (script.version() != expected) {
                throw new IllegalStateException("迁移版本不连续，期望 V" + expected + "，实际 V" + script.version());
            }
            expected++;
        }
    }

    private static int versionOf(Path file) {
        Matcher matcher = FILE_NAME.matcher(file.getFileName().toString());
        if (!matcher.matches()) {
            throw new IllegalStateException("无法识别迁移文件：" + file.getFileName());
        }
        return Integer.parseInt(matcher.group(1));
    }

    static Map<Integer, SchemaProbe> readProbes(String text) {
        Map<Integer, SchemaProbe> probes = new LinkedHashMap<>();
        for (String raw : text.split("\\R")) {
            String line = raw.trim();
            if (line.isEmpty() || line.startsWith("#")) {
                continue;
            }
            String[] parts = raw.split("\t", -1);
            if (parts.length < 3) {
                throw new IllegalStateException("结构探针格式不正确：" + line);
            }
            int version = Integer.parseInt(parts[0].trim());
            SchemaProbe.Kind kind = SchemaProbe.Kind.valueOf(parts[1].trim());
            String table = requiredIdentifier(parts[2].trim(), "表名");
            String column = parts.length > 3 ? parts[3].trim() : "";
            String expected = parts.length > 4 ? parts[4].trim() : "";
            if (kind != SchemaProbe.Kind.TABLE) {
                column = requiredIdentifier(column, "列名");
            }
            if (kind == SchemaProbe.Kind.ROW && expected.isEmpty()) {
                throw new IllegalStateException("行探针缺少期望值：V" + version);
            }
            if (probes.put(version, new SchemaProbe(kind, table, column, expected)) != null) {
                throw new IllegalStateException("结构探针版本重复：V" + version);
            }
        }
        return probes;
    }

    private static String requiredIdentifier(String value, String label) {
        if (!IDENTIFIER.matcher(value).matches()) {
            throw new IllegalStateException(label + "不合法：" + value);
        }
        return value;
    }

    private static String stripComments(String sql) {
        StringBuilder result = new StringBuilder();
        boolean quote = false;
        boolean lineComment = false;
        boolean blockComment = false;
        for (int i = 0; i < sql.length(); i++) {
            char ch = sql.charAt(i);
            char next = i + 1 < sql.length() ? sql.charAt(i + 1) : '\0';
            if (lineComment) {
                if (ch == '\n') {
                    lineComment = false;
                    result.append(ch);
                }
                continue;
            }
            if (blockComment) {
                if (ch == '*' && next == '/') {
                    blockComment = false;
                    i++;
                }
                continue;
            }
            if (quote) {
                result.append(ch);
                if (ch == '\'' && next == '\'') {
                    result.append(next);
                    i++;
                    continue;
                }
                if (ch == '\'') {
                    quote = false;
                }
                continue;
            }
            if (ch == '-' && next == '-') {
                lineComment = true;
                i++;
                continue;
            }
            if (ch == '/' && next == '*') {
                blockComment = true;
                i++;
                continue;
            }
            if (ch == '\'') {
                quote = true;
            }
            result.append(ch);
        }
        return result.toString();
    }

    private static void addStatement(List<String> parts, StringBuilder current) {
        String statement = current.toString().trim();
        current.setLength(0);
        if (!statement.isEmpty()) {
            parts.add(statement);
        }
    }
}
