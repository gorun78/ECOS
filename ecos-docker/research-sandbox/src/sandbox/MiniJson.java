package sandbox;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 极简 JSON 读写（沙盒专用，零第三方依赖）。
 *
 * <p><b>为什么手写而不引 Jackson</b>：沙盒镜像里不装任何第三方包是有意的攻击面收缩；
 * 且沙盒只需处理「小型、结构固定」的实验规格。本实现严格遵循 RFC 8259 子集：
 * 对象/数组/字符串/数字/布尔/null，不含注释与尾逗号。</p>
 *
 * <p><b>安全取向</b>：解析失败<b>必须抛异常</b>（由调用方转成非 0 code），
 * 绝不返回「尽力解析的半成品」——那会让上层把残缺数据当有效读数。</p>
 */
final class MiniJson {

    private final String src;
    private int pos;

    private MiniJson(String src) {
        this.src = src;
    }

    static Object parse(String text) {
        MiniJson p = new MiniJson(text);
        p.skipWs();
        Object v = p.value();
        p.skipWs();
        if (p.pos != p.src.length()) {
            throw new IllegalArgumentException("JSON 尾部有多余内容 @" + p.pos);
        }
        return v;
    }

    // ── 解析 ────────────────────────────────────────────────────────────────

    private Object value() {
        if (pos >= src.length()) {
            throw new IllegalArgumentException("JSON 意外结束");
        }
        char c = src.charAt(pos);
        return switch (c) {
            case '{' -> object();
            case '[' -> array();
            case '"' -> string();
            case 't' -> literal("true", Boolean.TRUE);
            case 'f' -> literal("false", Boolean.FALSE);
            case 'n' -> literal("null", null);
            default -> {
                if (c == '-' || (c >= '0' && c <= '9')) {
                    yield number();
                }
                throw new IllegalArgumentException("非法 JSON 字符 '" + c + "' @" + pos);
            }
        };
    }

    private Map<String, Object> object() {
        expect('{');
        Map<String, Object> out = new LinkedHashMap<>();
        skipWs();
        if (peek() == '}') {
            pos++;
            return out;
        }
        while (true) {
            skipWs();
            if (peek() != '"') {
                throw new IllegalArgumentException("对象键必须是字符串 @" + pos);
            }
            String key = string();
            skipWs();
            expect(':');
            skipWs();
            out.put(key, value());
            skipWs();
            char c = peek();
            if (c == ',') {
                pos++;
                continue;
            }
            if (c == '}') {
                pos++;
                return out;
            }
            throw new IllegalArgumentException("对象缺少 , 或 } @" + pos);
        }
    }

    private List<Object> array() {
        expect('[');
        List<Object> out = new ArrayList<>();
        skipWs();
        if (peek() == ']') {
            pos++;
            return out;
        }
        while (true) {
            skipWs();
            out.add(value());
            skipWs();
            char c = peek();
            if (c == ',') {
                pos++;
                continue;
            }
            if (c == ']') {
                pos++;
                return out;
            }
            throw new IllegalArgumentException("数组缺少 , 或 ] @" + pos);
        }
    }

    private String string() {
        expect('"');
        StringBuilder sb = new StringBuilder();
        while (true) {
            if (pos >= src.length()) {
                throw new IllegalArgumentException("字符串未闭合");
            }
            char c = src.charAt(pos++);
            if (c == '"') {
                return sb.toString();
            }
            if (c == '\\') {
                if (pos >= src.length()) {
                    throw new IllegalArgumentException("转义未完成");
                }
                char e = src.charAt(pos++);
                switch (e) {
                    case '"' -> sb.append('"');
                    case '\\' -> sb.append('\\');
                    case '/' -> sb.append('/');
                    case 'b' -> sb.append('\b');
                    case 'f' -> sb.append('\f');
                    case 'n' -> sb.append('\n');
                    case 'r' -> sb.append('\r');
                    case 't' -> sb.append('\t');
                    case 'u' -> {
                        if (pos + 4 > src.length()) {
                            throw new IllegalArgumentException("\\u 需要 4 位十六进制");
                        }
                        String hex = src.substring(pos, pos + 4);
                        pos += 4;
                        sb.append((char) Integer.parseInt(hex, 16));
                    }
                    default -> throw new IllegalArgumentException("不支持的转义 \\" + e);
                }
                continue;
            }
            if (c < 0x20) {
                throw new IllegalArgumentException("字符串中不允许裸控制字符");
            }
            sb.append(c);
        }
    }

    private BigDecimal number() {
        int start = pos;
        if (peek() == '-') {
            pos++;
        }
        while (pos < src.length() && isNumChar(src.charAt(pos))) {
            pos++;
        }
        String lit = src.substring(start, pos);
        try {
            return new BigDecimal(lit);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("非法数字 '" + lit + "' @" + start);
        }
    }

    private static boolean isNumChar(char c) {
        return (c >= '0' && c <= '9') || c == '.' || c == 'e' || c == 'E' || c == '+' || c == '-';
    }

    private Object literal(String lit, Object val) {
        if (!src.startsWith(lit, pos)) {
            throw new IllegalArgumentException("非法字面量 @" + pos);
        }
        pos += lit.length();
        return val;
    }

    private void expect(char c) {
        if (pos >= src.length() || src.charAt(pos) != c) {
            throw new IllegalArgumentException("期望 '" + c + "' @" + pos);
        }
        pos++;
    }

    private char peek() {
        if (pos >= src.length()) {
            throw new IllegalArgumentException("JSON 意外结束");
        }
        return src.charAt(pos);
    }

    private void skipWs() {
        while (pos < src.length()) {
            char c = src.charAt(pos);
            if (c == ' ' || c == '\t' || c == '\n' || c == '\r') {
                pos++;
            } else {
                break;
            }
        }
    }

    // ── 序列化（仅用于快照摘要，稳定输出） ──────────────────────────────────

    static String write(Object v) {
        StringBuilder sb = new StringBuilder();
        writeTo(sb, v);
        return sb.toString();
    }

    private static void writeTo(StringBuilder sb, Object v) {
        if (v == null) {
            sb.append("null");
        } else if (v instanceof Map<?, ?> m) {
            sb.append('{');
            List<String> keys = new ArrayList<>();
            for (Object k : m.keySet()) {
                keys.add(String.valueOf(k));
            }
            keys.sort(String::compareTo); // 键排序 ⇒ 同内容同摘要
            boolean first = true;
            for (String k : keys) {
                if (!first) {
                    sb.append(',');
                }
                first = false;
                writeString(sb, k);
                sb.append(':');
                writeTo(sb, m.get(k));
            }
            sb.append('}');
        } else if (v instanceof List<?> l) {
            sb.append('[');
            for (int i = 0; i < l.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                writeTo(sb, l.get(i));
            }
            sb.append(']');
        } else if (v instanceof String s) {
            writeString(sb, s);
        } else if (v instanceof BigDecimal b) {
            sb.append(b.toPlainString());
        } else if (v instanceof Boolean || v instanceof Number) {
            sb.append(v);
        } else {
            writeString(sb, String.valueOf(v));
        }
    }

    private static void writeString(StringBuilder sb, String s) {
        sb.append('"');
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
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

    private MiniJson() {
        this.src = "";
    }
}
