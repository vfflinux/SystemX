package br.com.systemx.biblioteca.util;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Serializer and recursive-descent parser for JSON in pure Java (zero external dependencies).
 */
public final class JsonUtil {

    private JsonUtil() {
    }

    public static String stringify(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(sb, value, 0, false);
        return sb.toString();
    }

    public static String stringifyPretty(Object value) {
        StringBuilder sb = new StringBuilder();
        writeValue(sb, value, 0, true);
        return sb.toString();
    }

    @SuppressWarnings("unchecked")
    private static void writeValue(StringBuilder sb, Object value, int indent, boolean pretty) {
        if (value == null) {
            sb.append("null");
        } else if (value instanceof String s) {
            writeString(sb, s);
        } else if (value instanceof Number || value instanceof Boolean) {
            sb.append(value.toString());
        } else if (value instanceof Map<?, ?> map) {
            sb.append('{');
            boolean first = true;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (!first) sb.append(',');
                if (pretty) {
                    sb.append('\n');
                    appendIndent(sb, indent + 2);
                }
                writeString(sb, String.valueOf(entry.getKey()));
                sb.append(pretty ? ": " : ":");
                writeValue(sb, entry.getValue(), indent + 2, pretty);
                first = false;
            }
            if (!first && pretty) {
                sb.append('\n');
                appendIndent(sb, indent);
            }
            sb.append('}');
        } else if (value instanceof Collection<?> col) {
            sb.append('[');
            boolean first = true;
            for (Object item : col) {
                if (!first) sb.append(',');
                if (pretty) {
                    sb.append('\n');
                    appendIndent(sb, indent + 2);
                }
                writeValue(sb, item, indent + 2, pretty);
                first = false;
            }
            if (!first && pretty) {
                sb.append('\n');
                appendIndent(sb, indent);
            }
            sb.append(']');
        } else {
            writeString(sb, value.toString());
        }
    }

    private static void appendIndent(StringBuilder sb, int spaces) {
        for (int i = 0; i < spaces; i++) sb.append(' ');
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\b' -> sb.append("\\b");
                case '\f' -> sb.append("\\f");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        sb.append('"');
    }

    public static Object parse(String json) {
        if (json == null || json.isBlank()) {
            return new LinkedHashMap<String, Object>();
        }
        Parser parser = new Parser(json);
        Object result = parser.parseValue();
        parser.skipWhitespace();
        if (parser.pos < parser.input.length()) {
            throw new IllegalArgumentException("JSON inválido após posição " + parser.pos);
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    public static Map<String, Object> parseObject(String json) {
        Object parsed = parse(json);
        if (parsed instanceof Map<?, ?> map) {
            return (Map<String, Object>) map;
        }
        throw new IllegalArgumentException("Esperado objeto JSON.");
    }

    public static String getString(Map<String, Object> map, String key) {
        Object v = map.get(key);
        return v == null ? null : String.valueOf(v);
    }

    public static String getStringOrDefault(Map<String, Object> map, String key, String defaultValue) {
        String val = getString(map, key);
        return (val == null || val.isBlank()) ? defaultValue : val;
    }

    public static long getLong(Map<String, Object> map, String key, long defaultValue) {
        Object v = map.get(key);
        if (v instanceof Number n) return n.longValue();
        if (v instanceof String s && !s.isBlank()) {
            try { return Long.parseLong(s.trim()); } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }

    public static int getInt(Map<String, Object> map, String key, int defaultValue) {
        Object v = map.get(key);
        if (v instanceof Number n) return n.intValue();
        if (v instanceof String s && !s.isBlank()) {
            try { return Integer.parseInt(s.trim()); } catch (NumberFormatException ignored) {}
        }
        return defaultValue;
    }

    public static boolean getBoolean(Map<String, Object> map, String key, boolean defaultValue) {
        Object v = map.get(key);
        if (v instanceof Boolean b) return b;
        if (v instanceof String s) return Boolean.parseBoolean(s.trim());
        return defaultValue;
    }

    private static final class Parser {
        private final String input;
        private int pos = 0;

        Parser(String input) {
            this.input = input;
        }

        void skipWhitespace() {
            while (pos < input.length()) {
                char c = input.charAt(pos);
                if (c == ' ' || c == '\t' || c == '\r' || c == '\n') {
                    pos++;
                } else {
                    break;
                }
            }
        }

        Object parseValue() {
            skipWhitespace();
            if (pos >= input.length()) {
                throw new IllegalArgumentException("Fim inesperado do JSON");
            }
            char c = input.charAt(pos);
            return switch (c) {
                case '{' -> parseObject();
                case '[' -> parseArray();
                case '"' -> parseString();
                case 't', 'f' -> parseBoolean();
                case 'n' -> parseNull();
                default -> {
                    if (c == '-' || (c >= '0' && c <= '9')) {
                        yield parseNumber();
                    }
                    throw new IllegalArgumentException("Caractere inesperado '" + c + "' na posição " + pos);
                }
            };
        }

        Map<String, Object> parseObject() {
            Map<String, Object> map = new LinkedHashMap<>();
            pos++; // '{'
            skipWhitespace();
            if (pos < input.length() && input.charAt(pos) == '}') {
                pos++;
                return map;
            }
            while (pos < input.length()) {
                skipWhitespace();
                String key = parseString();
                skipWhitespace();
                if (pos >= input.length() || input.charAt(pos) != ':') {
                    throw new IllegalArgumentException("Esperado ':' na posição " + pos);
                }
                pos++;
                Object value = parseValue();
                map.put(key, value);
                skipWhitespace();
                if (pos < input.length() && input.charAt(pos) == ',') {
                    pos++;
                } else if (pos < input.length() && input.charAt(pos) == '}') {
                    pos++;
                    return map;
                } else {
                    throw new IllegalArgumentException("Esperado ',' ou '}' na posição " + pos);
                }
            }
            throw new IllegalArgumentException("Objeto JSON não fechado");
        }

        List<Object> parseArray() {
            List<Object> list = new ArrayList<>();
            pos++; // '['
            skipWhitespace();
            if (pos < input.length() && input.charAt(pos) == ']') {
                pos++;
                return list;
            }
            while (pos < input.length()) {
                list.add(parseValue());
                skipWhitespace();
                if (pos < input.length() && input.charAt(pos) == ',') {
                    pos++;
                } else if (pos < input.length() && input.charAt(pos) == ']') {
                    pos++;
                    return list;
                } else {
                    throw new IllegalArgumentException("Esperado ',' ou ']' na posição " + pos);
                }
            }
            throw new IllegalArgumentException("Array JSON não fechado");
        }

        String parseString() {
            if (input.charAt(pos) != '"') {
                throw new IllegalArgumentException("Esperado '\"' na posição " + pos);
            }
            pos++;
            StringBuilder sb = new StringBuilder();
            while (pos < input.length()) {
                char c = input.charAt(pos++);
                if (c == '"') {
                    return sb.toString();
                }
                if (c == '\\') {
                    if (pos >= input.length()) {
                        throw new IllegalArgumentException("Escape incompleto");
                    }
                    char esc = input.charAt(pos++);
                    switch (esc) {
                        case '"' -> sb.append('"');
                        case '\\' -> sb.append('\\');
                        case '/' -> sb.append('/');
                        case 'b' -> sb.append('\b');
                        case 'f' -> sb.append('\f');
                        case 'n' -> sb.append('\n');
                        case 'r' -> sb.append('\r');
                        case 't' -> sb.append('\t');
                        case 'u' -> {
                            String hex = input.substring(pos, pos + 4);
                            sb.append((char) Integer.parseInt(hex, 16));
                            pos += 4;
                        }
                        default -> sb.append(esc);
                    }
                } else {
                    sb.append(c);
                }
            }
            throw new IllegalArgumentException("String JSON não fechada");
        }

        Boolean parseBoolean() {
            if (input.startsWith("true", pos)) {
                pos += 4;
                return Boolean.TRUE;
            }
            if (input.startsWith("false", pos)) {
                pos += 5;
                return Boolean.FALSE;
            }
            throw new IllegalArgumentException("Booleano inválido na posição " + pos);
        }

        Object parseNull() {
            if (input.startsWith("null", pos)) {
                pos += 4;
                return null;
            }
            throw new IllegalArgumentException("Null inválido na posição " + pos);
        }

        Number parseNumber() {
            int start = pos;
            if (input.charAt(pos) == '-') pos++;
            while (pos < input.length() && Character.isDigit(input.charAt(pos))) pos++;
            boolean isDouble = false;
            if (pos < input.length() && input.charAt(pos) == '.') {
                isDouble = true;
                pos++;
                while (pos < input.length() && Character.isDigit(input.charAt(pos))) pos++;
            }
            if (pos < input.length() && (input.charAt(pos) == 'e' || input.charAt(pos) == 'E')) {
                isDouble = true;
                pos++;
                if (pos < input.length() && (input.charAt(pos) == '+' || input.charAt(pos) == '-')) pos++;
                while (pos < input.length() && Character.isDigit(input.charAt(pos))) pos++;
            }
            String numStr = input.substring(start, pos);
            return isDouble ? Double.parseDouble(numStr) : Long.parseLong(numStr);
        }
    }
}
