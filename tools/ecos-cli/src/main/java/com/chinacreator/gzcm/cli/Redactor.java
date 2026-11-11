package com.chinacreator.gzcm.cli;

import java.util.Locale;

/** 凭据/敏感串脱敏（输出、日志、异常消息统一入口）。 */
public final class Redactor {

    /** JWT 形态：三段 base64url，第二段通常是 payload。 */
    private static final String JWT = "eyJ[A-Za-z0-9_-]{4,}\\.[A-Za-z0-9_-]{4,}\\.[A-Za-z0-9_-]*";
    private static final String KEY_VALUE = "(?i)(\"?(token|access_token|refresh_token|password|passwd|secret"
            + "|authorization|api[_-]?key|x-api-key|approval-token)\"?\\s*[:=]\\s*\"?)[^\"\\s,}&]+";
    /**
     * 引号包裹的值：以引号为边界整段吃掉。
     *
     * <p>仅用 {@link #KEY_VALUE} 会切在第一个空白处 —— {@code {"token":"abc def"}} 会脱成
     * {@code {"token":"*** def"}}，剩余片段明文落进输出与日志。含空格的口令/凭据并不罕见。</p>
     */
    private static final String KEY_QUOTED = "(?i)(\"?(token|access_token|refresh_token|password|passwd"
            + "|secret|authorization|api[_-]?key|x-api-key|approval-token)\"?\\s*[:=]\\s*\")([^\"\\\\]|\\\\.)+";
    /** 单引号包裹（shell/配置风格）：同上，避免只遮到第一个空格。 */
    private static final String KEY_SINGLE_QUOTED = "(?i)(\"?(token|access_token|refresh_token|password"
            + "|passwd|secret|authorization|api[_-]?key|x-api-key|approval-token)\"?\\s*[:=]\\s*')([^']+)";

    private Redactor() {
    }

    public static String redact(String text) {
        if (text == null || text.isEmpty()) {
            return text;
        }
        String out = text
                .replaceAll("(?i)(bearer\\s+)[A-Za-z0-9._~+/=-]+", "$1***")
                .replaceAll(JWT, "***")
                // 先处理引号值（能跨空格），再处理裸值；顺序不能反，否则裸值规则会先切碎
                .replaceAll(KEY_QUOTED, "$1***")
                .replaceAll(KEY_SINGLE_QUOTED, "$1***")
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
