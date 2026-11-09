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
        try {
            if ("table".equals(format)) {
                Stdio.println(toTable(out));
            } else {
                Stdio.println(MAPPER.writeValueAsString(out));
            }
        } catch (Exception e) {
            throw new CliException(ExitCodes.FAILED, "输出序列化失败: " + e.getClass().getSimpleName());
        }
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
        } else if (data instanceof List<?> rows) {
            sb.append("rows   : ").append(rows.size()).append(System.lineSeparator());
        } else if (data != null) {
            sb.append("data   : ").append(data).append(System.lineSeparator());
        }
        return sb.toString().stripTrailing();
    }
}
