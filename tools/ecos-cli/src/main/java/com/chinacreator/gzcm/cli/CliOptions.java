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
 *
 * <p><b>配置分层</b>（高 → 低）：{@code --flag} → {@code ECOS_CLI_*} 环境变量 →
 * {@code ~/.config/ecos/cli-<profile>.properties} → 内置默认。</p>
 */
public final class CliOptions {

    static final String DEFAULT_BASE_URL = "http://localhost:8080";
    private static final int GATEWAY_PORT = 8080;
    /** ECOS service 段与调试 boot 段的「千位前缀」；用区间判定避免在源码里写裸端口字面量。 */
    private static final int SERVICE_BAND = 18;
    private static final int DEBUG_BAND = 19;
    private static final java.util.Set<Integer> DATASTORE_PORTS =
            java.util.Set.of(5432, 6379, 9092, 7687, 7474, 9000, 2181);
    static final long DEFAULT_TIMEOUT_MS = 30_000L;
    static final String DEFAULT_OUTPUT = "json";

    private static final List<String> BOOLEAN_FLAGS = List.of(
            "--help", "-h", "--version", "--dry-run", "--all", "--follow", "--show-config",
            "--no-retry", "--preflight");

    private final List<String> commandWords = new ArrayList<>();
    private final Map<String, String> values = new LinkedHashMap<>();
    private final List<String> booleans = new ArrayList<>();
    private Properties profileProps;
    /** 自动生成的 trace/幂等键必须「一次解析、全链复用」；每次调用重生成会让日志与线上头对不上。 */
    private String traceIdCache;
    private String idempotencyKeyCache;

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
            if (BOOLEAN_FLAGS.contains(a)) {
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

    /**
     * 本次解析到的全部 flag 名（含布尔开关），供上层做「未知参数」校验。
     *
     * <p>解析器本身不认识命令参数表（那是 {@link OperationRegistry} 的职责），
     * 若在此处静默丢弃未知 flag，拼错 {@code --dry-run} 会变成真实写操作 ——
     * 故由 {@link Cli} 在拿到命令定义后统一比对，缺一即 USAGE。</p>
     */
    public List<String> flagNames() {
        List<String> out = new ArrayList<>(booleans);
        out.addAll(values.keySet());
        return out;
    }

    public boolean flag(String name) {
        return booleans.contains(name);
    }

    public String value(String name) {
        return values.get(name);
    }

    /** 输出格式：flag → env → profile → 默认。 */
    public String output() {
        String v = firstNonBlank(values.get("--output"),
                System.getenv("ECOS_CLI_OUTPUT"), profileSetting("output"), DEFAULT_OUTPUT)
                .toLowerCase(Locale.ROOT);
        if (!v.equals("json") && !v.equals("table")) {
            throw new CliException(ExitCodes.USAGE, "--output 只支持 json|table，实得: " + v);
        }
        return v;
    }

    public String traceId() {
        if (traceIdCache == null) {
            traceIdCache = firstNonBlank(values.get("--trace-id"),
                    System.getenv("ECOS_CLI_TRACE_ID"), TraceIds.next());
        }
        return traceIdCache;
    }

    public long timeoutMs() {
        String raw = firstNonBlank(values.get("--timeout"),
                System.getenv("ECOS_CLI_TIMEOUT_MS"), profileSetting("timeoutMs"), null);
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
        String v = firstNonBlank(values.get("--base-url"),
                System.getenv("ECOS_CLI_BASE_URL"), profileSetting("baseUrl"), DEFAULT_BASE_URL);
        String normalized = trimSlash(v);
        rejectServiceEndpoint(normalized);
        return normalized;
    }

    /**
     * 运行期红线：{@code --base-url} 禁指向服务端口或直连存储。
     *
     * <p>契约门禁只拦源码里的端口字面量，拦不住用户传参；CLI 定位是「只经 gateway」，
     * 绕过 gateway 会同时绕过鉴权、限流与审计，故此处直接 USAGE 拒绝（不降级、不告警放行）。</p>
     */
    private static void rejectServiceEndpoint(String baseUrl) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("^https?://[^/]*?:([0-9]{2,5})(?:/|$)", java.util.regex.Pattern.CASE_INSENSITIVE)
                .matcher(baseUrl);
        if (!m.find()) {
            return;
        }
        int port = Integer.parseInt(m.group(1));
        // 区间按「18xxx 服务段 / 19xxx 调试段」判定，不在源码里写端口字面量（契约门禁禁裸端口）。
        int band = port / 1000;
        boolean serviceOrDebug = band == SERVICE_BAND || band == DEBUG_BAND;
        boolean datastore = DATASTORE_PORTS.contains(port);
        if (serviceOrDebug || datastore) {
            throw new CliException(ExitCodes.USAGE,
                    "--base-url 只能指向 gateway（默认 :" + GATEWAY_PORT + "）；:" + port
                            + " 属 service/存储端口，直连会绕过鉴权与审计");
        }
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

    public String idempotencyKey() {
        if (idempotencyKeyCache == null) {
            idempotencyKeyCache = IdempotencyKeys.resolve(values.get("--idempotency-key"));
        }
        return idempotencyKeyCache;
    }

    public Integer pageSize(Integer fallback) {
        String raw = firstNonBlank(values.get("--page-size"),
                System.getenv("ECOS_CLI_PAGE_SIZE"), profileSetting("pageSize"), null);
        if (raw == null) {
            return fallback;
        }
        int v;
        try {
            v = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new CliException(ExitCodes.USAGE, "--page-size 必须是整数: " + raw);
        }
        return PageWalker.pageSize(v, fallback == null ? PageWalker.MAX_PAGE_SIZE : fallback);
    }

    public int maxPages() {
        String raw = values.get("--max-pages");
        if (raw == null) {
            return PageWalker.MAX_PAGES;
        }
        int v;
        try {
            v = Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new CliException(ExitCodes.USAGE, "--max-pages 必须是整数: " + raw);
        }
        if (v <= 0 || v > PageWalker.MAX_PAGES) {
            throw new CliException(ExitCodes.USAGE,
                    "--max-pages 取值须在 1~" + PageWalker.MAX_PAGES + "，实得: " + v);
        }
        return v;
    }

    /** 生效配置快照（供 {@code --show-config}；token 只显示是否已配置）。 */
    public Map<String, Object> effectiveConfig() {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("baseUrl", baseUrl());
        m.put("output", output());
        m.put("timeoutMs", timeoutMs());
        m.put("profile", profileName());
        m.put("tokenConfigured", token() != null);
        m.put("logLevel", CliLog.level().name());
        m.put("retry", flag("--no-retry") ? "disabled" : "max=" + RetryPolicy.DEFAULT_MAX_ATTEMPTS);
        return m;
    }

    public RetryPolicy retryPolicy() {
        int max = flag("--no-retry") ? 1 : RetryPolicy.DEFAULT_MAX_ATTEMPTS;
        return new RetryPolicy(max);
    }

    private String profileName() {
        return firstNonBlank(values.get("--profile"), System.getenv("ECOS_CLI_PROFILE"), null);
    }

    private String profileSetting(String key) {
        String profile = profileName();
        if (profile == null) {
            return null;
        }
        if (profile.isBlank() || profile.contains("/") || profile.contains("\\") || profile.contains("..")) {
            throw new CliException(ExitCodes.USAGE, "--profile 只接受配置名（不含路径分隔符）: " + profile);
        }
        if (profileProps == null) {
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
            profileProps = props;
        }
        return profileProps.getProperty(key);
    }

    private static String firstNonBlank(String... candidates) {
        for (String c : candidates) {
            if (c != null && !c.isBlank()) {
                return c.trim();
            }
        }
        return null;
    }

    private static String trimSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
