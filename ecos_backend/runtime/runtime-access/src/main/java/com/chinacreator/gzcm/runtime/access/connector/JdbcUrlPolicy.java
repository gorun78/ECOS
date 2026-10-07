package com.chinacreator.gzcm.runtime.access.connector;

import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * R1.3（详细设计-02 W49）JDBC URL 白名单校验 — 收敛于 {@link JdbcConnector} 单点。
 *
 * <p>背景：旧实现把 {@code cfg.get("jdbcUrl")} 原样透传给 {@code DriverManager.getConnection}，
 * 用户/数据源配置可注入不受控的连接参数（反序列化 RCE、文件加载、TLS 关闭、嵌入 scheme 等）。
 * 本类只断言"用户直传 URL"里的参数是否在安全域内；{@link JdbcConnector#buildJdbcUrl} 拼装出的
 * 系统自产 URL 不经本校验（拼装项是白名单子集，不存在风险）。</p>
 *
 * <p>命中任一违规即抛 {@link IllegalArgumentException}（消息不含 URL 原文，避免把连接细节外泄）。</p>
 */
final class JdbcUrlPolicy {

    private static final List<Rule> DENY_RULES = List.of(
        new Rule("allowloadlocalinfile",     StrEq("true", "1")),
        new Rule("allowurlinselect",         StrEq("true", "1")),
        new Rule("deserializestoredobjects", StrEq("true", "1")),
        new Rule("autodeserialize",          StrEq("true", "1")),
        new Rule("trustservercertificate",   StrEq("true")),
        new Rule("encrypt",                  StrEq("false")),
        new Rule("sslmode",                  StrEq("disable", "allow")),
        new Rule("sslfactory", v -> "org.postgresql.ssl.NonValidatingSSLFactory".equalsIgnoreCase(v))
    );

    private JdbcUrlPolicy() {
    }

    /** 校验通过则原样返回；命中违规参数抛 {@link IllegalArgumentException}。 */
    static String validate(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        String u = url.trim();
        if (!u.regionMatches(true, 0, "jdbc:", 0, 5)) {
            throw new IllegalArgumentException("仅允许 jdbc: 前缀的连接 URL");
        }
        String lower = u.toLowerCase(Locale.ROOT);
        if (lower.contains("file:") || lower.contains("ldap:") || u.contains("::memory:")) {
            throw new IllegalArgumentException("连接 URL 含禁止的嵌入 scheme");
        }
        for (String p : splitParams(u)) {
            int eq = p.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            String key = p.substring(0, eq).trim().toLowerCase(Locale.ROOT);
            String val = p.substring(eq + 1).trim();
            Rule r = find(key);
            if (r != null && r.predicate.test(val)) {
                throw new IllegalArgumentException("连接 URL 含禁止参数 " + key);
            }
        }
        return u;
    }

    /** 按 ';', '&', '?' 分割参数段；每段应形如 {@code key=value}（对 {@code jdbc:xxx://h/p?...} 可安全拆解）。 */
    private static String[] splitParams(String url) {
        return url.split("[;&?]");
    }

    private static Rule find(String key) {
        for (Rule r : DENY_RULES) {
            if (r.key.equals(key)) {
                return r;
            }
        }
        return null;
    }

    private static Predicate<String> StrEq(String... literals) {
        return v -> {
            if (v == null) {
                return false;
            }
            String s = v.trim().toLowerCase(Locale.ROOT);
            for (String l : literals) {
                if (s.equals(l)) {
                    return true;
                }
            }
            return false;
        };
    }

    private record Rule(String key, Predicate<String> predicate) {
    }
}
