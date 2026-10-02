package sordland.data;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class Json {
    private Json() {
    }
    public static Object parse(Path path) throws IOException {
        try (Reader reader = new BufferedReader(Files.newBufferedReader(path, StandardCharsets.UTF_8), 65536)) {
            Parser p = new Parser(reader);
            Object value = p.value(0);
            p.space();
            if (p.peek() != -1) throw p.error("Unexpected trailing content");
            return value;
        }
    }
    @SuppressWarnings("unchecked") public static Map<String, Object> object(Object value) {
        return value instanceof Map<?,?> ?(Map<String, Object>) value: Map.of();
    }
    public static List<Object> list(Object value) {
        if (value instanceof List<?> a) return new ArrayList<>(a);
        if (!(value instanceof Map<?,?>)) return List.of();
        Map<String, Object> map = object(value);
        return map.entrySet().stream().filter(e -> e.getKey().matches("\\d+")).sorted(Comparator.comparingLong(e -> Long.parseLong(e.getKey()))).map(Map.Entry::getValue).toList();
    }
    public static String string(Object value) {
        return value == null ? "": String.valueOf(value);
    }
    public static String string(Map<String, Object> map, String key) {
        return string(map.get(key));
    }
    public static int integer(Object value, int fallback) {
        if (value instanceof Number n) return n.intValue();
        try {
            return Integer.parseInt(string(value));
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
    public static String pretty(Object value) {
        StringBuilder b = new StringBuilder();
        pretty(value, b, 0);
        return b.toString();
    }
    private static void pretty(Object value, StringBuilder b, int depth) {
        if (value instanceof Map<?,?> m) {
            b.append('{');
            boolean first = true;
            for (var e: m.entrySet()) {
                if (!first) b.append(',');
                b.append('\n').append("  ".repeat(depth + 1));
                quote(String.valueOf(e.getKey()), b);
                b.append(": ");
                pretty(e.getValue(), b, depth + 1);
                first = false;
            }
            if (!first) b.append('\n').append("  ".repeat(depth));
            b.append('}');
        } else if (value instanceof List<?> a) {
            b.append('[');
            boolean first = true;
            for (Object v: a) {
                if (!first) b.append(", ");
                pretty(v, b, depth + 1);
                first = false;
            }
            b.append(']');
        } else if (value instanceof String s) quote(s, b);
        else b.append(value == null ? "null": value);
    }
    private static void quote(String s, StringBuilder b) {
        b.append('"');
        for (int i = 0; i<s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> b.append("\\\"");
                case '\\' -> b.append("\\\\");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> {
                    if (c<32) b.append(String.format("\\u%04x",(int) c));
                    else b.append(c);
                }
            }
        }
        b.append('"');
    }
    private static final class Parser {
        final Reader reader;
        int next = -2;
        long position;
        Parser(Reader r) {
            reader = r;
        }
        int peek() throws IOException {
            if (next == -2) next = reader.read();
            return next;
        }
        int take() throws IOException {
            if ((position & 8191) == 0 && Thread.currentThread().isInterrupted()) throw new InterruptedIOException("JSON reading cancelled");
            int c = peek();
            next = -2;
            position++;
            return c;
        }
        IOException error(String message) {
            return new IOException(message + " near character " + position);
        }
        void space() throws IOException {
            while (peek() == ' ' || peek() == '\n' || peek() == '\r' || peek() == '\t') take();
        }
        Object value(int depth) throws IOException {
            if (depth>512) throw error("JSON nesting exceeds 512 levels");
            space();
            return switch (peek()) {
                case '{' -> objectValue(depth + 1);
                case '[' -> array(depth + 1);
                case '"' -> text();
                case 't' -> literal("true", true);
                case 'f' -> literal("false", false);
                case 'n' -> literal("null", null);
                default -> number();
            };
        }
        Object literal(String spelling, Object value) throws IOException {
            for (char c: spelling.toCharArray()) if (take() != c) throw error("Invalid literal");
            return value;
        }
        Map<String, Object> objectValue(int depth) throws IOException {
            take();
            space();
            Map<String, Object> map = new LinkedHashMap<>();
            if (peek() == '}') {
                take();
                return Collections.unmodifiableMap(map);
            } while (true) {
                space();
                if (peek() != '"') throw error("Expected object key");
                String key = text();
                space();
                if (take() != ':') throw error("Expected colon");
                if (map.containsKey(key)) throw error("Duplicate object key " + key);
                map.put(key, value(depth));
                space();
                int c = take();
                if (c == '}') break;
                if (c != ',') throw error("Expected comma or closing brace");
            }
            return Collections.unmodifiableMap(map);
        }
        List<Object> array(int depth) throws IOException {
            take();
            space();
            List<Object> list = new ArrayList<>();
            if (peek() == ']') {
                take();
                return List.of();
            } while (true) {
                list.add(value(depth));
                space();
                int c = take();
                if (c == ']') break;
                if (c != ',') throw error("Expected comma or closing bracket");
            }
            return Collections.unmodifiableList(list);
        }
        String text() throws IOException {
            if (take() != '"') throw error("Expected string");
            StringBuilder b = new StringBuilder();
            while (true) {
                int c = take();
                if (c == '"') return b.toString();
                if (c<32) throw error("Unterminated string or control character");
                if (c != '\\') {
                    b.append((char) c);
                    continue;
                }
                int e = take();
                switch (e) {
                    case '"', '\\', '/' -> b.append((char) e);
                    case 'b' -> b.append('\b');
                    case 'f' -> b.append('\f');
                    case 'n' -> b.append('\n');
                    case 'r' -> b.append('\r');
                    case 't' -> b.append('\t');
                    case 'u' -> {
                        int n = 0;
                        for (int i = 0; i<4; i++) {
                            int digit = Character.digit(take(), 16);
                            if (digit<0) throw error("Invalid Unicode escape");
                            n =(n<<4) | digit;
                        }
                        b.append((char) n);
                    }
                    default -> throw error("Invalid string escape");
                }
            }
        }
        Number number() throws IOException {
            StringBuilder b = new StringBuilder();
            if (peek() == '-') b.append((char) take());
            if (peek() == '0') b.append((char) take());
            else {
                if (peek()<'1' || peek()>'9') throw error("Expected JSON value");
                digits(b);
            }
            boolean decimal = false;
            if (peek() == '.') {
                decimal = true;
                b.append((char) take());
                if (peek()<'0' || peek()>'9') throw error("Expected fraction digits");
                digits(b);
            }
            if (peek() == 'e' || peek() == 'E') {
                decimal = true;
                b.append((char) take());
                if (peek() == '+' || peek() == '-') b.append((char) take());
                if (peek()<'0' || peek()>'9') throw error("Expected exponent digits");
                digits(b);
            }
            try {
                if (decimal) return new java.math.BigDecimal(b.toString());
                return Long.parseLong(b.toString());
            } catch (NumberFormatException e) {
                try {
                    return new java.math.BigDecimal(b.toString());
                } catch (NumberFormatException ignored) {
                    throw error("Invalid number");
                }
            }
        }
        void digits(StringBuilder b) throws IOException {
            while (peek() >= '0' && peek() <= '9') b.append((char) take());
        }
    }
}
