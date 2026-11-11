package com.chinacreator.gzcm.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import java.util.List;
import java.util.Map;

/** 输出渲染：{@code --output json|table}。JSON 为面向智能体的默认形态。 */
public final class OutputRenderer {

    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private OutputRenderer() {
    }

    public static void render(Map<String, Object> out, String format) {
        Stdio.println(renderToString(out, format));
    }

    /**
     * 渲染为字符串（唯一出口，<b>出串即脱敏</b>）。
     *
     * <p>后端把凭据回显在 data/error 里时，json 与 table 两种形态都不得带出明文；
     * 脱敏放在序列化之后，才能覆盖 URL query、嵌套字段等任意位置。</p>
     */
    static String renderToString(Map<String, Object> out, String format) {
        String text;
        try {
            text = "table".equals(format) ? toTable(out) : MAPPER.writeValueAsString(out);
        } catch (Exception e) {
            throw new CliException(ExitCodes.FAILED, "输出序列化失败: " + e.getClass().getSimpleName());
        }
        return Redactor.redact(text);
    }

    @SuppressWarnings("unchecked")
    private static String toTable(Map<String, Object> out) {
        StringBuilder sb = new StringBuilder();
        sb.append("status : ").append(out.get("status")).append(System.lineSeparator());
        sb.append("ok     : ").append(out.get("ok")).append(System.lineSeparator());
        sb.append("trace  : ").append(out.get("trace_id")).append(System.lineSeparator());
        Object error = out.get("error");
        if (error instanceof Map<?, ?> e) {
            sb.append("error  : ").append(e.get("code")).append(" — ").append(e.get("message"))
                    .append(System.lineSeparator());
        }
        Object data = out.get("data");
        if (data instanceof Map<?, ?> m && m.get("checks") instanceof List<?> checks) {
            sb.append("checks :").append(System.lineSeparator());
            for (Object c : checks) {
                if (c instanceof Map<?, ?> row) {
                    sb.append("  - ").append(row.get("name")).append(": ").append(row.get("status"))
                            .append(" (").append(row.get("detail")).append(")").append(System.lineSeparator());
                }
            }
        } else if (data instanceof Map<?, ?> m && m.get("commands") instanceof List<?> cmds) {
            sb.append("commands :").append(System.lineSeparator());
            for (Object c : cmds) {
                if (c instanceof Map<?, ?> row) {
                    sb.append("  - ").append(row.get("name")).append(" [").append(row.get("accessMode"))
                            .append("]").append(System.lineSeparator());
                }
            }
        } else if (data instanceof Map<?, ?> m && m.get("steps") instanceof List<?> steps) {
            sb.append("preflight : ").append(m.get("pass")).append(" pass / ")
                    .append(m.get("fail")).append(" fail / ").append(m.get("blocked")).append(" blocked / ")
                    .append(m.get("notVerified")).append(" not_verified；createsRun=")
                    .append(m.get("createsRun")).append(System.lineSeparator());
            for (Object c : steps) {
                if (c instanceof Map<?, ?> row) {
                    sb.append("  - ").append(row.get("step")).append(": ").append(row.get("status"))
                            .append(" (").append(row.get("source")).append(")").append(System.lineSeparator());
                }
            }
        } else if (data instanceof List<?> rows) {
            sb.append("rows   : ").append(rows.size()).append(System.lineSeparator());
            // 只报行数等于把数据丢了 —— table 的用途就是让人看内容。
            // 逐行输出各键值；超宽不截断（截断会误导），交由终端折行。
            for (Object row : rows) {
                if (row instanceof Map<?, ?> m) {
                    String cells = m.entrySet().stream()
                            .map(e -> e.getKey() + "=" + e.getValue())
                            .collect(java.util.stream.Collectors.joining(", "));
                    sb.append("  - ").append(cells).append(System.lineSeparator());
                } else {
                    sb.append("  - ").append(row).append(System.lineSeparator());
                }
            }
        } else if (data != null) {
            sb.append("data   : ").append(data).append(System.lineSeparator());
        }
        return sb.toString().stripTrailing();
    }
}
