package com.zhubao.print;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** 解析打印任务快照所需的受限 JSON，避免把纸面内容交给客户端拼接。 */
final class SnapshotJson {
    private final String source;
    private int index;

    private SnapshotJson(String source) {
        if (source == null || source.length() > 200_000) {
            throw new IllegalArgumentException("标签快照无效");
        }
        this.source = source;
    }

    static Map<String, Object> parse(String source) {
        SnapshotJson parser = new SnapshotJson(source);
        Object result = parser.value(0);
        parser.spaces();
        if (!(result instanceof Map<?, ?> map) || parser.index != source.length()) {
            throw new IllegalArgumentException("标签快照格式无效");
        }
        @SuppressWarnings("unchecked")
        Map<String, Object> typed = (Map<String, Object>) map;
        return typed;
    }

    private Object value(int depth) {
        if (depth > 16) throw new IllegalArgumentException("标签快照层级过深");
        spaces();
        if (index >= source.length()) throw new IllegalArgumentException("标签快照不完整");
        char next = source.charAt(index);
        if (next == '{') return object(depth + 1);
        if (next == '[') return array(depth + 1);
        if (next == '"') return string();
        if (next == 'n') return literal("null", null);
        if (next == 't') return literal("true", true);
        if (next == 'f') return literal("false", false);
        if (next == '-' || Character.isDigit(next)) return number();
        throw new IllegalArgumentException("标签快照字段无效");
    }

    private Map<String, Object> object(int depth) {
        index++;
        Map<String, Object> result = new LinkedHashMap<>();
        spaces();
        if (consume('}')) return result;
        do {
            spaces();
            if (index >= source.length() || source.charAt(index) != '"') {
                throw new IllegalArgumentException("标签快照键无效");
            }
            String key = string();
            spaces();
            expect(':');
            result.put(key, value(depth));
            spaces();
            if (consume('}')) return result;
            expect(',');
        } while (true);
    }

    private List<Object> array(int depth) {
        index++;
        List<Object> result = new ArrayList<>();
        spaces();
        if (consume(']')) return result;
        do {
            result.add(value(depth));
            spaces();
            if (consume(']')) return result;
            expect(',');
        } while (true);
    }

    private String string() {
        expect('"');
        StringBuilder result = new StringBuilder();
        while (index < source.length()) {
            char current = source.charAt(index++);
            if (current == '"') return result.toString();
            if (current == '\\') {
                if (index >= source.length()) break;
                char escaped = source.charAt(index++);
                switch (escaped) {
                    case '"', '\\', '/' -> result.append(escaped);
                    case 'b' -> result.append('\b');
                    case 'f' -> result.append('\f');
                    case 'n' -> result.append('\n');
                    case 'r' -> result.append('\r');
                    case 't' -> result.append('\t');
                    case 'u' -> {
                        if (index + 4 > source.length()) throw new IllegalArgumentException("标签快照转义无效");
                        try {
                            result.append((char) Integer.parseInt(source.substring(index, index + 4), 16));
                        } catch (NumberFormatException ex) {
                            throw new IllegalArgumentException("标签快照转义无效", ex);
                        }
                        index += 4;
                    }
                    default -> throw new IllegalArgumentException("标签快照转义无效");
                }
            } else if (current < 0x20) {
                throw new IllegalArgumentException("标签快照文本无效");
            } else {
                result.append(current);
            }
        }
        throw new IllegalArgumentException("标签快照文本不完整");
    }

    private Number number() {
        int start = index;
        if (source.charAt(index) == '-') index++;
        while (index < source.length() && Character.isDigit(source.charAt(index))) index++;
        if (index < source.length() && source.charAt(index) == '.') {
            index++;
            while (index < source.length() && Character.isDigit(source.charAt(index))) index++;
        }
        try {
            return Double.parseDouble(source.substring(start, index));
        } catch (NumberFormatException ex) {
            throw new IllegalArgumentException("标签快照数字无效", ex);
        }
    }

    private Object literal(String expected, Object value) {
        if (!source.startsWith(expected, index)) throw new IllegalArgumentException("标签快照常量无效");
        index += expected.length();
        return value;
    }

    private void spaces() {
        while (index < source.length() && Character.isWhitespace(source.charAt(index))) index++;
    }

    private void expect(char value) {
        if (!consume(value)) throw new IllegalArgumentException("标签快照结构无效");
    }

    private boolean consume(char value) {
        if (index < source.length() && source.charAt(index) == value) {
            index++;
            return true;
        }
        return false;
    }
}
