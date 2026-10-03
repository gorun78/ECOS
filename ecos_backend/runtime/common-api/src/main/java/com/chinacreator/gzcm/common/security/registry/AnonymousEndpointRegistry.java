package com.chinacreator.gzcm.common.security.registry;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * W05（详细设计-00 C.3.2，M0）— 匿名/豁免端点单源注册表。
 *
 * <p>现状三处分散（F-7/F-8）：{@code SecurityConfig.permitAll}（8 条）、
 * {@code ClearanceMvcConfig.EXCLUDE_PATTERNS}（7 条，双路径只覆盖一半）、
 * {@code ClearanceInterceptor} 内联 startsWith（9 条前缀）。本注册表是**唯一真源**，
 * 三处消费方一律从本类生成（自动生成 /api/* 与 /api/v1/* 两形态），
 * 未登记即默认 DENY。门禁 = {@code AnonymousEndpointInventoryTest}
 * （断言三处实际注册集合 == 本表展开集合，新增未登记 permitAll → FAIL）。</p>
 *
 * <h3>登记纪律（C.3.2）</h3>
 * <ul>
 *   <li>每条必须写"为什么可匿名"（justification）+ 批准人 + 日期</li>
 *   <li>health 类只允许 {@code /api/v1/{engine}/health} 形态（及其 /api/ 双形态）</li>
 *   <li>{@link Scope#ANONYMOUS}：无需凭证可达（permitAll + MVC exclude + 拦截器跳过）</li>
 *   <li>{@link Scope#CLEARED_EXEMPT}：需认证但豁免 clearance 等级校验（仅拦截器消费）</li>
 * </ul>
 */
public final class AnonymousEndpointRegistry {

    private AnonymousEndpointRegistry() {
    }

    public enum Scope {
        /** 无需凭证可达（SecurityConfig permitAll + ClearanceMvcConfig exclude + 拦截器跳过） */
        ANONYMOUS,
        /** 需认证，但豁免 clearance 等级校验（仅 ClearanceInterceptor 消费） */
        CLEARED_EXEMPT
    }

    /** 单条登记：pattern（Ant 风格，可含单个 *）+ 元数据 */
    public record Entry(String[] patterns, Scope scope, String justification, String approver, String date) {
    }

    // ─────────────────────────────────────────────────────────────
    // 唯一真源清单（2026-09-30 定版，随详细设计-00 C.3.2 批准）
    // ─────────────────────────────────────────────────────────────
    private static final List<Entry> ENTRIES = List.of(
            new Entry(new String[]{"/auth/**", "/api/auth/**", "/api/v1/auth/**"}, Scope.ANONYMOUS,
                    "登录/刷新凭证自身即为流程对象，匿名面必须存在", "H9-T1", "2026-09-28"),
            new Entry(new String[]{"/api/engine/*/health", "/api/v1/engine/*/health"}, Scope.ANONYMOUS,
                    "引擎健康探测供 preflight/监控无凭证访问（health 类仅 /health 形态）", "H9-T1", "2026-09-28"),
            new Entry(new String[]{"/api/knowledge/health", "/api/v1/knowledge/health"}, Scope.ANONYMOUS,
                    "kb 引擎健康探测（health 形态）", "H9-T1", "2026-09-28"),
            new Entry(new String[]{"/api/health", "/health"}, Scope.ANONYMOUS,
                    "平台级存活探针（supervisor/dockers 探活）", "H9-T1", "2026-09-28"),
            new Entry(new String[]{"/actuator/health"}, Scope.ANONYMOUS,
                    "Actuator 存活探针（仅暴露 health，actuator 其余端点不可达）", "H9-T1", "2026-09-28"),
            new Entry(new String[]{"/error"}, Scope.ANONYMOUS,
                    "Spring 错误页转发基址（4xx/5xx 兜底渲染，不放行它无任何业务数据）", "H9-T1", "2026-09-28"),
            new Entry(new String[]{"/api/security/**", "/api/v1/security/**"}, Scope.CLEARED_EXEMPT,
                    "安全控制面（security-engine REST：RLS/CLS/ABAC/脱敏门面）仅要求认证；" +
                    "裁决语义由 01 册承载，不做 clearance 等级二次校验", "H9-T2", "2026-09-28"),
            new Entry(new String[]{"/api/audit/**", "/api/v1/audit/**"}, Scope.CLEARED_EXEMPT,
                    "审计读取面（认证后可读；审计写由引擎侧落库，此处仅查询门面）", "H9-T2", "2026-09-28"),
            new Entry(new String[]{"/api/knowledge/extract/**", "/api/v1/knowledge/extract/**"}, Scope.CLEARED_EXEMPT,
                    "存量无凭证服务间调用（cognitive → kb 抽取链路）；H10 服务凭证落地后应降级删除此条", "H9-T2b", "2026-09-29"),
            new Entry(new String[]{"/api/knowledge/health", "/api/v1/knowledge/health",
                    "/api/engine/*/health", "/api/v1/engine/*/health"}, Scope.ANONYMOUS,
                    "【校订 2026-09-30】C.3.2 收口：拦截器豁免与 MVC exclude 须双路径同集合（F-8 三处不一致），" +
                    "本条把 /api/* 形态与 /api/v1/* 形态并发显式登记，InventoryTest 按集合比对不受序影响", "W05-C1", "2026-09-30")
    );

    /** 全部登记条目（不可变） */
    public static List<Entry> entries() {
        return Collections.unmodifiableList(ENTRIES);
    }

    /** SecurityConfig permitAll 目标集合（ANONYMOUS 全 pattern，保序） */
    public static List<String> permitAllPatterns() {
        return patternsOf(Scope.ANONYMOUS);
    }

    /** ClearanceMvcConfig excludePathPatterns 目标集合（ANONYMOUS + CLEARED_EXEMPT，双形态已内置） */
    public static List<String> mvcExcludePatterns() {
        List<String> out = new ArrayList<>();
        for (Entry e : ENTRIES) {
            for (String p : e.patterns()) {
                out.add(p);
            }
        }
        return Collections.unmodifiableList(out);
    }

    /** ClearanceInterceptor 豁免判定集合（与 MVC 同源；拦截器用 isAnonymous/isClearedExempt 查询） */
    public static List<String> interceptorExemptPatterns() {
        return mvcExcludePatterns();
    }

    private static List<String> patternsOf(Scope scope) {
        List<String> out = new ArrayList<>();
        for (Entry e : ENTRIES) {
            if (e.scope() == scope) {
                Collections.addAll(out, e.patterns());
            }
        }
        return Collections.unmodifiableList(out);
    }

    /** ANONYMOUS 判定（路径级，供拦截器/诊断端点） */
    public static boolean isAnonymous(String path) {
        return matchesAny(permitAllPatterns(), path);
    }

    /** CLEARED_EXEMPT 判定（认证后豁免 clearance 等级校验） */
    public static boolean isClearedExempt(String path) {
        return matchesAny(patternsOf(Scope.CLEARED_EXEMPT), path);
    }

    /** 拦截器统一豁免判定（ANONYMOUS + CLEARED_EXEMPT） */
    public static boolean isExempt(String path) {
        return isAnonymous(path) || isClearedExempt(path);
    }

    // ── Ant 子集匹配（** 前缀通配 / 单 * 段内匹配；不引入 spring-web 依赖） ──

    public static boolean matchesPattern(String pattern, String path) {
        if (pattern == null || path == null) {
            return false;
        }
        if (pattern.equals(path)) {
            return true;
        }
        if (pattern.endsWith("/**")) {
            return path.startsWith(pattern.substring(0, pattern.length() - 3));
        }
        if (pattern.contains("*")) {
            StringBuilder re = new StringBuilder("^");
            for (char c : pattern.toCharArray()) {
                if (c == '*') {
                    re.append("[^/]*");
                } else if ("\\.[]{}()+-^$|?".indexOf(c) >= 0) {
                    re.append('\\').append(c);
                } else {
                    re.append(c);
                }
            }
            re.append('$');
            return path.matches(re.toString());
        }
        return false;
    }

    private static boolean matchesAny(List<String> patterns, String path) {
        for (String p : patterns) {
            if (matchesPattern(p, path)) {
                return true;
            }
        }
        return false;
    }
}
