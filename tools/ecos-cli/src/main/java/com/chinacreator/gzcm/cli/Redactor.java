package com.chinacreator.gzcm.cli;

import java.util.Locale;

/** 凭据/敏感串脱敏（输出、日志、异常消息统一入口）。 */
public final class Redactor {

    /** JWT 形态：三段 base64url，第二段通常是 payload。 */
    private static final String JWT = "eyJ[A-Za-z0-9_-]{4,}\\.[A-Za-z0-9_-]{4,}\\.[A-Za-z0-9_-]*";
    private static final String KEY_VALUE = "(?i)(\"?(token|access_token|refresh_token|password|passwd|secret"
            + "|authorization|api[_-]?key|x-api-key|approval-token)\"?\\s*[:=]\\s*\"?)[^\"\\s,}&]+";

    private Redactor() {
    }

    public static String redact(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String out = text
                .replaceAll("(?i)(bearer\\s+)[A-Za-z0-9._~+/=-]+", "$1***")
                .replaceAll(JWT, "***")
                .replaceAll(KEY_VALUE, "$1***")
                // URL query 中的敏感参数：?token=… &access_token=…
                .replaceAll("(?i)([?&](?:token|access_token|refresh_token|api[_-]?key|approval-token)=)[^&\\s\"']+",
                        "$1***");
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
