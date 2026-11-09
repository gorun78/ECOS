package com.chinacreator.gzcm.cli;

import java.util.Locale;

/** 凭据/敏感串脱敏（输出、日志、异常消息统一入口）。 */
public final class Redactor {

    private Redactor() {
    }

    public static String redact(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String out = text
                .replaceAll("(?i)(bearer\\s+)[A-Za-z0-9._~+/=-]+", "$1***")
                .replaceAll("(?i)(\"?(token|password|passwd|secret|authorization)\"?\\s*[:=]\\s*\"?)[^\"\\s,}]+", "$1***");
        String envToken = System.getenv("ECOS_TOKEN");
        if (envToken != null && envToken.length() >= 8) {
            out = out.replace(envToken, "***");
        }
        return out;
    }

    static String statusLabel(CliStatus status) {
        return status.name().toUpperCase(Locale.ROOT);
    }
}
