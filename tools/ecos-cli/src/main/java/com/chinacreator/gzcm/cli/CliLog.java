package com.chinacreator.gzcm.cli;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * 结构化日志（JSONL，默认落 {@code ~/.cache/ecos-cli/logs/cli-YYYY-MM-DD.jsonl}）。
 *
 * <p>三条硬规则：</p>
 * <ol>
 *   <li><b>默认不落敏感内容</b>：不写 token、请求体、响应体；只写方法/路径/状态/耗时/trace。</li>
 *   <li><b>写盘失败不阻断业务</b>：日志是旁路，IO 异常静默降级（stderr 不打堆栈，避免二次泄漏）。</li>
 *   <li><b>级别由环境变量控制</b>：{@code ECOS_CLI_LOG_LEVEL=OFF|ERROR|WARN|INFO|DEBUG}，默认 INFO。</li>
 * </ol>
 */
public final class CliLog {

    public enum Level { OFF, ERROR, WARN, INFO, DEBUG }

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final Level CONFIGURED = resolveLevel();

    private CliLog() {
    }

    public static Level level() {
        return CONFIGURED;
    }

    public static boolean enabled(Level level) {
        return CONFIGURED != Level.OFF && level.ordinal() <= CONFIGURED.ordinal();
    }

    public static void event(String event, Map<String, Object> fields) {
        write(Level.INFO, event, fields);
    }

    public static void warn(String event, Map<String, Object> fields) {
        write(Level.WARN, event, fields);
    }

    public static void error(String event, Map<String, Object> fields) {
        write(Level.ERROR, event, fields);
    }

    public static void debug(String event, Map<String, Object> fields) {
        write(Level.DEBUG, event, fields);
    }

    private static void write(Level level, String event, Map<String, Object> fields) {
        if (!enabled(level)) {
            return;
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("ts", java.time.Instant.now().toString());
        row.put("level", level.name());
        row.put("event", event);
        if (fields != null) {
            fields.forEach((k, v) -> row.put(k, v instanceof String s ? Redactor.redact(s) : v));
        }
        try {
            String line = MAPPER.writeValueAsString(row) + System.lineSeparator();
            Files.createDirectories(logDir());
            Files.writeString(logFile(), line, StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE, StandardOpenOption.APPEND);
        } catch (IOException | RuntimeException ignored) {
            // 旁路失败不影响命令结果
        }
    }

    static Path logDir() {
        return Paths.get(System.getProperty("user.home"), ".cache", "ecos-cli", "logs");
    }

    static Path logFile() {
        return logDir().resolve("cli-" + LocalDate.now() + ".jsonl");
    }

    private static Level resolveLevel() {
        String raw = System.getenv("ECOS_CLI_LOG_LEVEL");
        if (raw == null || raw.isBlank()) {
            return Level.INFO;
        }
        try {
            return Level.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return Level.INFO;
        }
    }
}
