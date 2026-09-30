package com.chinacreator.gzcm.common.security.anonymous;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/**
 * 匿名/豁免端点单一真源（详细设计-00 · C.3.2 · W05）。
 *
 * <p>现状三处分散（F-7/F-8）：
 * <ul>
 *   <li>{@code SecurityConfig#permitAll} — 8 条（要不要认证）</li>
 *   <li>{@code ClearanceMvcConfig#excludePathPatterns} — 9 条（要不要做准入等级裁决）</li>
 *   <li>{@code ClearanceInterceptor#doPreHandle} — 9 条前缀（重复的第 2 处，还口径不同）</li>
 * </ul>
 * 三处集合互相偏离，且只按 {@code /api/v1/*} 单路径，实际受
 * {@code VersionPrefixRewriteFilter} 改写出 {@code /api/*} 双形态。
 *
 * <p>目标态：SecurityConfig / ClearanceMvcConfig / ClearanceInterceptor <b>全部</b>
 * 由本 registry 生成，新增未登记的路径即由
 * {@code AnonymousEndpointInventoryTest} 判 FAIL。
 * 分两个类别：<b>ANONYMOUS</b>（无需 JWT，加入 SecurityConfig permitAll）和
 * <b>CLEARANCE_EXEMPT</b>（需 JWT 但跳过准入等级裁决，如 security 控制面本身不需要 clearance）。
 * 两条都可有 {@code dualPath=true} 自动补 /api/* 变体。</p>
 *
 * <p>C.3.2 数据模型：{@code (pattern, category, dualPath, justification, approver, date)}。
 * 每条必须写"为什么可匿名/豁免"（health 类只允许 {@code /api/v1/{engine}/health} 形态）。</p>
 */
public final class AnonymousEndpointRegistry {

    public enum Category {
        /** 完全匿名：无需 JWT，进入 SecurityConfig permitAll。 */
        ANONYMOUS,
        /** 仍然认证（走 JWT），但跳过 ClearanceInterceptor 的准入等级裁决。 */
        CLEARANCE_EXEMPT
    }

    public static final class Entry {
        private final String pattern;
        private final Category category;
        private final boolean dualPath;
        private final String reason;
        private final String owner;
        private final String date;

        public Entry(String pattern, Category category, boolean dualPath,
                     String reason, String owner, String date) {
            this.pattern = pattern;
            this.category = category;
            this.dualPath = dualPath;
            this.reason = reason;
            this.owner = owner;
            this.date = date;
        }

        public String getPattern() { return pattern; }
        public Category getCategory() { return category; }
        public boolean isDualPath() { return dualPath; }
        public String getReason() { return reason; }
        public String getOwner() { return owner; }
        public String getDate() { return date; }
    }

    private static final List<Entry> ENTRIES = new ArrayList<>();

    static {
        // ── 认证族（匿名） ─────────────────────────
        reg("/auth/**",           Category.ANONYMOUS, false,
                "legacy auth 前缀（承接旧 BFF 直连）", "用户", "2026-09-28");
        reg("/api/v1/auth/**",    Category.ANONYMOUS, true,
                "登录/登出/refresh 需匿名", "用户", "2026-09-28");
        // ── health 族（匿名） ─────────────────────
        reg("/api/v1/engine/health",      Category.ANONYMOUS, false,
                "引擎健康检查（子引擎名兜底单段）", "用户", "2026-09-28");
        reg("/api/v1/engine/*/health",    Category.ANONYMOUS, false,
                "引擎健康检查（子引擎名通配）", "用户", "2026-09-28");
        reg("/api/v1/knowledge/health",   Category.ANONYMOUS, false,
                "知识库健康检查（含重写变体）", "用户", "2026-09-28");
        reg("/api/health",               Category.ANONYMOUS, false,
                "gateway 健康检查独立入口", "用户", "2026-09-28");
        reg("/health",                   Category.ANONYMOUS, false,
                "compose/探活入口", "用户", "2026-09-28");
        reg("/actuator/health",          Category.ANONYMOUS, false,
                "Spring Actuator 健康", "用户", "2026-09-28");
        // ── 错误兜底（匿名） ──────────────────────
        reg("/error",                        Category.ANONYMOUS, false,
                "Spring ErrorPage 兜底路径", "用户", "2026-09-28");

        // ── CLEARANCE_EXEMPT：需认证但跳过准入等级裁决 ─────────
        // 保持 H9-T2b 语义：engine 业务端点仍须受准入校验，只豁免 health 子路径。
        // security 控制面自身：避免无限自指（policy-engine/evaluate 由 security 自己裁决）
        reg("/api/v1/security",     Category.CLEARANCE_EXEMPT, true,
                "security 控制面自身不需要 clearance 裁决",
                "用户", "2026-09-28");
        // 审计查询：任何已过认证的 L1 用户可查，不需要 L3
        reg("/api/v1/audit",        Category.CLEARANCE_EXEMPT, true,
                "审计日志查询需认证，但不做额外准入等级裁决",
                "用户", "2026-09-28");
        // 知识库抽取：cognitive → kb service-to-service 调用暂无服务凭证（H10 待落地）
        reg("/api/v1/knowledge/extract/**", Category.CLEARANCE_EXEMPT, true,
                "认知引擎调用 kb 抽取（H10 前）", "用户", "2026-09-28");
    }

    private static void reg(String pattern, Category category, boolean dualPath,
                            String reason, String owner, String date) {
        ENTRIES.add(new Entry(pattern, category, dualPath, reason, owner, date));
    }

    private AnonymousEndpointRegistry() {}

    public static List<Entry> all() {
        return Collections.unmodifiableList(ENTRIES);
    }

    public static List<Entry> byCategory(Category c) {
        List<Entry> out = new ArrayList<>();
        for (Entry e : ENTRIES) if (e.getCategory() == c) out.add(e);
        return out;
    }

    /** 展开 dualPath=true 后所有 pattern（含 /api/v1/* 与 /api/* 两种形态）。 */
    public static Set<String> allPatterns() {
        Set<String> out = new TreeSet<>();
        for (Entry e : ENTRIES) {
            addWithDual(out, e.getPattern(), e.isDualPath());
        }
        return out;
    }

    /** 供 SecurityConfig 生成的 permitAll 列表（保持 registry 定义顺序，诊断页可见）。 */
    public static List<String> permitAllPatterns() {
        List<String> out = new ArrayList<>();
        for (Entry e : ENTRIES) {
            if (e.getCategory() != Category.ANONYMOUS) continue;
            out.add(e.getPattern());
            if (e.isDualPath() && e.getPattern().startsWith("/api/v1/")) {
                out.add("/api/" + e.getPattern().substring("/api/v1/".length()));
            }
        }
        return out;
    }

    /**
     * 供 ClearanceMvcConfig / ClearanceInterceptor 使用：
     * ANONYMOUS + CLEARANCE_EXEMPT 两类的并集（含 dualPath 展开）。
     */
    public static List<String> clearanceExemptPatterns() {
        List<String> out = new ArrayList<>();
        for (Entry e : ENTRIES) {
            out.add(e.getPattern());
            if (e.isDualPath() && e.getPattern().startsWith("/api/v1/")) {
                out.add("/api/" + e.getPattern().substring("/api/v1/".length()));
            }
        }
        return out;
    }

    /**
     * ClearanceInterceptor 用：给定"重写后路径"，判断是否在该路径类别内命中豁免。
     * 双星 = 任多层；单星 = 单段；否则逐段精确。
     */
    public static boolean isAnonymousOrExempt(String path) {
        if (path == null) return false;
        for (String p : allPatterns()) {
            if (matches(p, path)) return true;
        }
        return false;
    }

    /** 兼容别名：等价于 isAnonymousOrExempt。 */
    public static boolean isAnonymous(String path) {
        return isAnonymousOrExempt(path);
    }

    static boolean matches(String pattern, String path) {
        String[] pp = split(p_segmented(pattern));
        String[] ps = split(p_segmented(path));
        int i = 0, j = 0;
        while (i < pp.length && j < ps.length) {
            String seg = pp[i];
            if ("**".equals(seg)) return true;
            if ("*".equals(seg)) { i++; j++; continue; }
            if (!seg.equals(ps[j])) return false;
            i++; j++;
        }
        while (i < pp.length && "**".equals(pp[i])) i++;
        return i == pp.length && j == ps.length;
    }

    private static void addWithDual(Set<String> out, String pattern, boolean dual) {
        out.add(pattern);
        if (dual && pattern.startsWith("/api/v1/")) {
            out.add("/api/" + pattern.substring("/api/v1/".length()));
        }
    }

    private static String p_segmented(String s) {
        return s.startsWith("/") ? s.substring(1) : s;
    }

    private static String[] split(String segsStr) {
        if (segsStr.isEmpty()) return new String[0];
        return segsStr.split("/");
    }

    /** route-manifest / 诊断页：pattern → 元信息（order-稳定 = LinkedHashMap）。 */
    public static Map<String, Map<String, String>> diagnosticEntries() {
        Map<String, Map<String, String>> m = new LinkedHashMap<>();
        for (Entry e : byCategory(Category.ANONYMOUS)) {
            addDiagnostic(m, e);
        }
        for (Entry e : byCategory(Category.CLEARANCE_EXEMPT)) {
            addDiagnostic(m, e);
        }
        return Collections.unmodifiableMap(m);
    }

    private static void addDiagnostic(Map<String, Map<String, String>> m, Entry e) {
        m.put(e.getPattern(), meta(e));
        if (e.isDualPath() && e.getPattern().startsWith("/api/v1/")) {
            m.put("/api/" + e.getPattern().substring("/api/v1/".length()), meta(e));
        }
    }

    private static Map<String, String> meta(Entry e) {
        Map<String, String> meta = new LinkedHashMap<>();
        meta.put("category", e.getCategory().name());
        meta.put("dualPath", String.valueOf(e.isDualPath()));
        meta.put("justification", e.getReason());
        meta.put("approver", e.getOwner());
        meta.put("date", e.getDate());
        return meta;
    }
}
