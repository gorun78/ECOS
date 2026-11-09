package com.chinacreator.gzcm.cli;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;

/**
 * 统一参数解析（详设第 16 篇 §16.2）。
 *
 * <p>凭据来源顺序：{@code --token} → 环境变量 {@code ECOS_TOKEN} → {@code --token-file} →
 * {@code --profile} 指向的配置文件。任何路径下凭据都不进入输出。</p>
 */
public final class CliOptions {

    static final String DEFAULT_BASE_URL = "http://localhost:8080";
    static final long DEFAULT_TIMEOUT_MS = 30_000L;

    private final List<String> commandWords = new ArrayList<>();
    private final Map<String, String> values = new LinkedHashMap<>();
    private final List<String> booleans = new ArrayList<>();

    private CliOptions() {
    }

    public static CliOptions parse(String[] args, int commandWordCount) {
        CliOptions o = new CliOptions();
        for (int i = 0; i < args.length; i++) {
            String a = args[i];
            if (i < commandWordCount) {
                o.commandWords.add(a);
                continue;
            }
            if (a.equals("--help") || a.equals("-h") || a.equals("--version") || a.equals("--dry-run")) {
                o.booleans.add(a);
            } else if (a.startsWith("--")) {
                int eq = a.indexOf('=');
                if (eq > 0) {
                    o.values.put(a.substring(0, eq), a.substring(eq + 1));
                } else {
                    if (i + 1 >= args.length) {
                        throw new CliException(ExitCodes.USAGE, "参数缺少取值: " + a);
                    }
                    o.values.put(a, args[++i]);
                }
            } else {
                throw new CliException(ExitCodes.USAGE, "无法识别的参数: " + a);
            }
        }
        o.output(); // 提前校验 --output，避免命令执行到一半才报用法错
        return o;
    }

    public List<String> commandWords() {
        return commandWords;
    }

    public boolean flag(String name) {
        return booleans.contains(name);
    }

    public String value(String name) {
        return values.get(name);
    }

    public String output() {
        String v = values.getOrDefault("--output", "json").toLowerCase(Locale.ROOT);
        if (!v.equals("json") && !v.equals("table")) {
            throw new CliException(ExitCodes.USAGE, "--output 只支持 json|table，实得: " + v);
        }
        return v;
    }

    public String traceId() {
        return values.getOrDefault("--trace-id", TraceIds.next());
    }

    public long timeoutMs() {
        String raw = values.get("--timeout");
        if (raw == null) {
            return DEFAULT_TIMEOUT_MS;
        }
        long v;
        try {
            v = Long.parseLong(raw);
        } catch (NumberFormatException e) {
            throw new CliException(ExitCodes.USAGE, "--timeout 必须是毫秒整数: " + raw);
        }
        if (v <= 0 || v > 120_000L) {
            throw new CliException(ExitCodes.USAGE, "--timeout 取值须在 1~120000ms: " + v);
        }
        return v;
    }

    public String baseUrl() {
        String profileBase = profileSetting("baseUrl");
        String explicit = values.get("--base-url");
        if (explicit != null) {
            return trimSlash(explicit);
        }
        String env = System.getenv("ECOS_CLI_BASE_URL");
        if (env != null && !env.isBlank()) {
            return trimSlash(env.trim());
        }
        return profileBase != null ? trimSlash(profileBase) : DEFAULT_BASE_URL;
    }

    /** 返回凭据或 null；null 表示未配置（调用方记 BLOCKED，不得重试成假成功）。 */
    public String token() {
        String explicit = values.get("--token");
        if (explicit != null && !explicit.isBlank()) {
            return explicit.trim();
        }
        String env = System.getenv("ECOS_TOKEN");
        if (env != null && !env.isBlank()) {
            return env.trim();
        }
        String tokenFile = values.get("--token-file");
        if (tokenFile != null) {
            Path p = Paths.get(tokenFile);
            try {
                String t = Files.readString(p).trim();
                if (!t.isEmpty()) {
                    return t;
                }
            } catch (Exception e) {
                throw new CliException(ExitCodes.USAGE, "无法读取 --token-file: " + p);
            }
        }
        String profileToken = profileSetting("token");
        return profileToken == null || profileToken.isBlank() ? null : profileToken.trim();
    }

    private String profileSetting(String key) {
        String profile = values.get("--profile");
        if (profile == null) {
            return null;
        }
        if (profile.isBlank() || profile.contains("/") || profile.contains("\\") || profile.contains("..")) {
            throw new CliException(ExitCodes.USAGE, "--profile 只接受配置名（不含路径分隔符）: " + profile);
        }
        Path file = Paths.get(System.getProperty("user.home"), ".config", "ecos", "cli-" + profile + ".properties");
        if (!Files.isRegularFile(file)) {
            throw new CliException(ExitCodes.BLOCKED,
                    "profile 配置不存在: " + file + "（需含 baseUrl/token 两键；文件内容不会被回显）");
        }
        Properties props = new Properties();
        try (var in = Files.newInputStream(file)) {
            props.load(in);
        } catch (Exception e) {
            throw new CliException(ExitCodes.BLOCKED, "profile 配置不可读: " + file);
        }
        return props.getProperty(key);
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
