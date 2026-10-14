package com.chinacreator.gzcm.gateway;

import com.chinacreator.gzcm.common.security.anonymous.AnonymousEndpointRegistry;
import com.chinacreator.gzcm.common.security.anonymous.AnonymousEndpointRegistry.Category;
import com.chinacreator.gzcm.common.security.anonymous.AnonymousEndpointRegistry.Entry;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 详设-00 §1.10 / §1.16 S0 出口 + 4.2.9 —— 匿名面白名单基线门禁（0 未登记例外）。
 *
 * <p><b>为什么需要本类（差距分析 §1.10 / §1.14 / 15.2 挂账项）</b>：既有
 * {@code AnonymousEndpointInventoryTest} 只校验 registry <b>内部</b>语义（族覆盖、双路径展开、
 * 子集关系），<b>没有任何门禁证明"匿名面只有 registry 这一处"</b>——即 S0 出口条件
 * 「匿名面白名单 0 例外」缺载体。差距分析 §1.10 原文「匿名面 preflight 白名单基线未实装」、
 * §1.14 缺项「匿名面 preflight 基线」指的就是这一层。按 R9② 补代码实现。</p>
 *
 * <p><b>四类断言（每一类都对应一个真实假绿入口）</b>：
 * <ol>
 *   <li><b>单源唯一性（全仓扫描）</b>：{@code ecos_backend/**\/src/main/java} 内
 *       Spring Security 的 {@code .permitAll(} / {@code requestMatchers(} / {@code .ignoring(}
 *       调用点必须<b>恰好 1 个文件</b>（sysman {@code SecurityConfig}），且该文件由
 *       {@code AnonymousEndpointRegistry.permitAllPatterns()} 取数——第二处 permitAll 就是
 *       "绕过白名单的匿名面"，是 S0 的主要假绿入口；</li>
 *   <li><b>白名单闭合（钉死字面集）</b>：ANONYMOUS 展开集与 CLEARANCE_EXEMPT 展开集分别
 *       与钉住集合<b>相等</b>（多＝新增匿名面未评审，少＝匿名面静默消失）。家族前缀规则
 *       再兜一层：ANONYMOUS 只允许 auth / health / error 三族（4.2.9「health 形态限定」）；</li>
 *   <li><b>元数据齐备</b>：每条必须有 reason / owner / ISO 日期（C.3.2 数据模型
 *       {@code (pattern, category, dualPath, justification, approver, date)}）；缺任一条＝
 *       "无主匿名面"，无法审计即视为违例；</li>
 *   <li><b>route-manifest ↔ registry</b>：manifest 是 owner 级语义真源，其每条
 *       {@code anonymous[]} 必须落在 registry 的<b>有效匿名/豁免可达面</b>内（禁幻影匿名声明）；
 *       反向由第 2 条的钉死集合控制。唯一"记在 anonymous 字段但实为服务间豁免"的
 *       {@code /api/v1/knowledge/extract/**} 单列钉住（H10 待收口，见下）。</li>
 * </ol>
 *
 * <p><b>如实口径（禁把本类读成 S0 已结案）</b>：本类达成的是「<b>0 未登记例外</b>」——
 * 白名单外不得有任何匿名面。白名单<b>内</b>现存 1 项待收口豁免
 * （{@code /api/v1/knowledge/extract/**}，服务间凭证 H10 未落地前的 CLEARANCE_EXEMPT），
 * 该条由 {@link #h10PendingExemptionIsPinnedAndCannotGrow()} 钉住数量=1 且必须带 H10 字样，
 * 因此它既不会被静默扩大，也不会被静默宣称收口。差距分析 15.2 行「S0 零例外未达成（6 违例基线）」
 * 指 {@code route-rewrite-lint.ps1} 的重写表可达性违例面，与本类的匿名面是两个不同的面，
 * 本类<b>不</b>主张那 6 条已消。</p>
 */
@DisplayName("S0 出口：匿名面白名单基线（单源唯一 + 集合闭合 + 元数据齐备 + manifest 对账）")
class AnonymousSurfaceBaselineTest {

    private static final Path BACKEND_ROOT_REL = Paths.get("ecos_backend");

    private static final String REGISTRY_REL =
            "runtime/common-api/src/main/java/com/chinacreator/gzcm/common/security/anonymous/"
                    + "AnonymousEndpointRegistry.java";
    private static final String SECURITY_CONFIG_REL =
            "services/sysman/impl/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/security/SecurityConfig.java";
    private static final String CLEARANCE_MVC_REL =
            "services/sysman/impl/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/security/ClearanceMvcConfig.java";
    private static final String CLEARANCE_INTERCEPTOR_REL =
            "services/sysman/impl/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/security/ClearanceInterceptor.java";
    private static final String MANIFEST_REL =
            "gateway/src/main/resources/route-manifest.json";

    /** 钉死：ANONYMOUS 类展开后的 permitAll 字面集（详设-00 C.3.2 W05 现状全量）。 */
    private static final Set<String> PINNED_ANONYMOUS_PATTERNS = Set.of(
            "/auth/**",
            "/api/v1/auth/**", "/api/auth/**",
            "/api/v1/engine/health", "/api/v1/engine/*/health", "/api/v1/knowledge/health",
            "/api/health", "/health", "/actuator/health",
            "/error");

    /** 钉死：CLEARANCE_EXEMPT 类展开字面集（需 JWT，仅跳过准入等级裁决）。 */
    private static final Set<String> PINNED_CLEARANCE_EXEMPT_PATTERNS = Set.of(
            "/api/v1/security", "/api/security",
            "/api/v1/audit", "/api/audit",
            "/api/v1/knowledge/extract/**", "/api/knowledge/extract/**");

    /** 钉死：登记在 manifest {@code anonymous[]} 字段、但实际形态是服务间豁免（H10 待收口）的条目。 */
    private static final Set<String> PINNED_SERVICE_TO_SERVICE_EXEMPTIONS = Set.of(
            "/api/v1/knowledge/extract/**");

    /** 4.2.9 家族前缀：ANONYMOUS 只允许这三族，其它前缀出现即业务面被匿名打开。 */
    private static final List<String> ANONYMOUS_ALLOWED_FAMILIES = List.of(
            "/auth/", "/api/v1/auth/", "/api/auth/",
            "/api/v1/engine/", "/api/v1/knowledge/",
            "/api/health", "/health", "/actuator/health", "/error");

    // ════════════════ ① 单源唯一性（全仓扫描） ════════════════

    @Test
    @DisplayName("全仓 permitAll/requestMatchers/ignoring 调用点恰好 1 个文件，且取数自 AnonymousEndpointRegistry")
    void anonymousSurfaceHasExactlyOneCodeSite() throws IOException {
        Path root = repoRoot();
        List<Path> javaFiles = mainJavaFiles(root);
        assertTrue(javaFiles.size() > 300, "扫描到的 main 源文件数异常少（" + javaFiles.size()
                + "）——扫描根漂移会让本门禁变成空扫假绿");

        List<String> permitAllSites = new ArrayList<>();
        List<String> literalMatchers = new ArrayList<>();
        List<String> ignoringSites = new ArrayList<>();
        for (Path f : javaFiles) {
            String src = stripComments(Files.readString(f, StandardCharsets.UTF_8));
            String rel = root.relativize(f).toString().replace('\\', '/');
            if (src.contains(".permitAll(")) permitAllSites.add(rel);
            if (src.contains(".ignoring(")) ignoringSites.add(rel);
            Matcher m = Pattern.compile("requestMatchers\\s*\\(\\s*\"").matcher(src);
            if (m.find()) literalMatchers.add(rel);
        }

        String securityConfigRel = "ecos_backend/" + SECURITY_CONFIG_REL;
        assertEquals(List.of(securityConfigRel), relOf(root, SECURITY_CONFIG_REL),
                "Spring Security 配置载体位置漂移（SecurityConfig 改名/搬家会让本门禁失去锚点）");
        assertEquals(List.of(securityConfigRel), permitAllSites,
                "全仓 .permitAll( 调用点必须恰好是 SecurityConfig 这 1 处（匿名面单源）：\n"
                        + String.join("\n", permitAllSites));
        assertEquals(1, permitAllSites.size(),
                ".permitAll( 调用点必须恰好 1 处（匿名面单源）。实得 " + permitAllSites.size() + " 处：\n"
                        + String.join("\n", permitAllSites));
        assertTrue(ignoringSites.isEmpty(),
                "出现 WebSecurity.ignoring()＝绕过 SecurityFilterChain 的匿名面，S0 直接不成立：\n"
                        + String.join("\n", ignoringSites));
        assertTrue(literalMatchers.isEmpty(),
                "requestMatchers 不得内联字面量路径（必须来自 registry 列表）：\n"
                        + String.join("\n", literalMatchers));

        String securitySrc = read(root.resolve(BACKEND_ROOT_REL).resolve(SECURITY_CONFIG_REL));
        assertTrue(securitySrc.contains("AnonymousEndpointRegistry.permitAllPatterns()"),
                "SecurityConfig 不再由 registry 单源取数 ⇒ 匿名面回到手工维护（H9-T1 前的三处偏离形态）");
        assertTrue(securitySrc.contains("anyRequest().authenticated()"),
                "默认面必须是 authenticated（缺该行＝fail-open）");
    }

    // ════════════════ ② 白名单闭合 ════════════════

    @Test
    @DisplayName("ANONYMOUS / CLEARANCE_EXEMPT 展开集与钉住集合双向相等（新增须评审，静默删除也变红）")
    void whitelistIsClosedInBothDirections() {
        assertEquals(PINNED_ANONYMOUS_PATTERNS, new TreeSet<>(
                        AnonymousEndpointRegistry.permitAllPatterns()),
                "匿名面（permitAll）字面集与白名单基线不等 ⇒ 有未评审的放行或被悄悄撤掉的放行");
        Set<String> exempt = new TreeSet<>();
        for (Entry e : AnonymousEndpointRegistry.byCategory(Category.CLEARANCE_EXEMPT)) {
            exempt.add(e.getPattern());
            if (e.isDualPath() && e.getPattern().startsWith("/api/v1/")) {
                exempt.add("/api/" + e.getPattern().substring("/api/v1/".length()));
            }
        }
        assertEquals(PINNED_CLEARANCE_EXEMPT_PATTERNS, exempt,
                "clearance 豁免面字面集与白名单基线不等");
    }

    @Test
    @DisplayName("4.2.9 形态限定：ANONYMOUS 只准 auth/health/error 三族，health 类必须以 /health 结尾")
    void anonymousEntriesStayWithinTheirFamilies() {
        for (Entry e : AnonymousEndpointRegistry.byCategory(Category.ANONYMOUS)) {
            String p = e.getPattern();
            boolean inFamily = ANONYMOUS_ALLOWED_FAMILIES.stream().anyMatch(f ->
                    p.equals(f) || p.startsWith(f) || (f.endsWith("/") && p.startsWith(f)));
            assertTrue(inFamily, "匿名面条目落在 auth/health/error 三族之外（业务面被匿名打开）: " + p);
            if (p.contains("health")) {
                assertTrue(p.endsWith("/health") || p.endsWith("/health/**") || p.endsWith("/actuator/health"),
                        "health 类只允许 /api/v1/{engine}/health 形态（4.2.9），实得: " + p);
            }
        }
        // 反向兜底：健康探针族不得被当成业务豁免
        assertFalse(AnonymousEndpointRegistry.isAnonymousOrExempt("/api/v1/engine/data/lineage"),
                "engine data 业务路径不得进豁免面（H9-T2b）");
        assertFalse(AnonymousEndpointRegistry.isAnonymousOrExempt("/api/v1/workspace/scenarios/sc001"),
                "场景工作台端点必须走 JWT（详设-07 D-3：N1~N12 不得加 permitAll）");
        assertFalse(AnonymousEndpointRegistry.isAnonymousOrExempt("/api/v1/business/scenarios/sc001/cognition/detect"),
                "认知端点 N4 不得加 permitAll（F07-15 三滤波器口径）");
    }

    // ════════════════ ③ 元数据齐备 ════════════════

    @Test
    @DisplayName("C.3.2 数据模型：每条匿名/豁免必须带 reason+owner+ISO 日期，无主条目即违例")
    void everyEntryCarriesJustificationApproverDate() {
        List<String> defects = new ArrayList<>();
        for (Entry e : AnonymousEndpointRegistry.all()) {
            if (isBlank(e.getReason())) defects.add(e.getPattern() + " 缺 justification");
            if (isBlank(e.getOwner())) defects.add(e.getPattern() + " 缺 approver");
            if (e.getCategory() == null) defects.add(e.getPattern() + " 缺 category");
            try {
                LocalDate.parse(e.getDate());
            } catch (RuntimeException ex) {
                defects.add(e.getPattern() + " 日期非 ISO-8601: " + e.getDate());
            }
            if (!e.getPattern().startsWith("/")) {
                defects.add("pattern 必须以 / 开头: " + e.getPattern());
            }
        }
        assertTrue(defects.isEmpty(), "匿名面元数据缺陷（无主/无理由即不可审计）：\n"
                + String.join("\n", defects));
    }

    @Test
    @DisplayName("H10 待收口豁免钉住：服务间豁免数量=1 且 reason 必须写 H10（禁静默扩大或静默宣称收口）")
    void h10PendingExemptionIsPinnedAndCannotGrow() {
        List<Entry> extractExemptions = AnonymousEndpointRegistry.all().stream()
                .filter(e -> e.getCategory() == Category.CLEARANCE_EXEMPT
                        && e.getPattern().endsWith("/extract/**"))
                .toList();
        assertEquals(1, extractExemptions.size(),
                "以 /extract/** 形态存在的服务间豁免应恰好 1 条（H10 未落地前的既有事实）；实得 "
                        + extractExemptions.size());
        assertTrue(extractExemptions.get(0).getReason().contains("H10"),
                "该条 reason 必须点明 H10（服务间凭证）待收口，否则后续读者会把它当常态: "
                        + extractExemptions.get(0).getReason());
    }

    // ════════════════ ④ route-manifest ↔ registry ════════════════

    @Test
    @DisplayName("route-manifest 每条 anonymous[] 必须被 registry 真实覆盖；服务间豁免单列钉住")
    void manifestAnonymousClaimsAreBackedByRegistry() throws IOException {
        Path root = repoRoot();
        JsonNode entries = new ObjectMapper()
                .readTree(read(root.resolve(BACKEND_ROOT_REL).resolve(MANIFEST_REL))
                        .getBytes(StandardCharsets.UTF_8))
                .path("entries");
        assertTrue(entries.isArray() && entries.size() > 0, "route-manifest entries 缺失");

        Set<String> seen = new TreeSet<>();
        List<String> phantoms = new ArrayList<>();
        List<String> exemptionOnly = new ArrayList<>();
        for (JsonNode e : entries) {
            String prefix = e.path("prefix").asText();
            for (JsonNode a : e.path("anonymous")) {
                String p = a.asText();
                if (!seen.add(p)) continue;
                for (String rep : representatives(p)) {
                    if (!AnonymousEndpointRegistry.isAnonymousOrExempt(rep)) {
                        phantoms.add(prefix + " 声明 anonymous=" + p + " 但 " + rep
                                + " 未被 registry 覆盖（幻影匿名声明＝文档比代码更宽）");
                    }
                }
                // 分类口径（字面集，非通配）：p 落在 clearance 豁免展开集里、但不在匿名展开集里
                //   ⇒ 该端点"要 JWT、只跳过准入裁决"＝服务间凭证待收口面（H10）。
                //   通配覆盖判定由上面的 phantoms 断言负责，这里故意只做字面比对，
                //   否则 health 类（由 /api/v1/engine/*/health 通配放行）会被误判成豁免面扩大。
                if (AnonymousEndpointRegistry.clearanceExemptPatterns().contains(p)
                        && !AnonymousEndpointRegistry.permitAllPatterns().contains(p)) {
                    exemptionOnly.add(p);
                }
            }
        }
        assertTrue(phantoms.isEmpty(), String.join("\n", phantoms));
        assertEquals(PINNED_SERVICE_TO_SERVICE_EXEMPTIONS, new TreeSet<>(exemptionOnly),
                "manifest 里记在 anonymous 字段但只由 CLEARANCE_EXEMPT 支撑的条目"
                        + "（＝H10 待收口面）必须恰好等于钉住集合，多一条就是匿名面扩大");
    }

    /**
     * 通配 pattern → 具体路径代表集，用 registry 自身的段级匹配做覆盖判定。
     * <p>{@code **} 取「零段 / 一段 / 两段」三个代表（其接受集由字面前缀决定，代表齐即覆盖）；
     * 单段 {@code *} 用一个非通配段替代。此近似对 manifest 现有形态（前缀 + 尾通配）是充分的，
     * 若将来出现中段双星号需要更宽的展开，本方法要同步扩展而不是放宽断言。</p>
     */
    private static List<String> representatives(String pattern) {
        List<String> out = new ArrayList<>();
        int dbl = pattern.indexOf("**");
        if (dbl >= 0) {
            String base = pattern.substring(0, dbl);
            while (base.endsWith("/")) base = base.substring(0, base.length() - 1);
            out.add(base);
            out.add(base + "/SEG");
            out.add(base + "/SEG/SEG");
        } else if (pattern.contains("*")) {
            out.add(pattern.replace("*", "SEG"));
        } else {
            out.add(pattern);
        }
        return out;
    }

    // ── 夹具 ─────────────────────────────────────────

    private static List<Path> mainJavaFiles(Path root) throws IOException {
        Path backend = root.resolve(BACKEND_ROOT_REL);
        try (Stream<Path> s = Files.walk(backend)) {
            return s.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .filter(p -> p.toString().replace('\\', '/').contains("/src/main/java/"))
                    .filter(p -> !p.toString().replace('\\', '/').contains("/target/"))
                    .sorted()
                    .toList();
        }
    }

    /**
     * 门禁锚定文件的<b>实际</b>相对路径（正斜杠）：文件不存在即 FAIL。
     * 早期版本这里是常量自证（{@code "ecos_backend/" + rel}）——那种断言永远不会红，属空转，
     * 现改为对真实文件系统取 relativize，路径漂移才抓得住。
     */
    private static List<String> relOf(Path root, String backendRel) {
        Path f = root.resolve(BACKEND_ROOT_REL).resolve(backendRel);
        assertTrue(Files.isRegularFile(f), "门禁锚定的文件不存在（载体搬家 ⇒ 本门禁失去锚点）: " + f);
        return List.of(root.relativize(f).toString().replace('\\', '/'));
    }

    /** 去块注释与行注释：javadoc 里的 {@code permitAll} 字样不是调用点，否则本门禁会被注释刷红。 */
    private static String stripComments(String src) {
        String noBlock = Pattern.compile("/\\*.*?\\*/", Pattern.DOTALL).matcher(src).replaceAll(" ");
        StringBuilder sb = new StringBuilder();
        for (String line : noBlock.split("\r?\n")) {
            int idx = line.indexOf("//");
            sb.append(idx >= 0 ? line.substring(0, idx) : line).append('\n');
        }
        return sb.toString();
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    private static String read(Path f) throws IOException {
        assertTrue(Files.isRegularFile(f), "事实源文件缺失: " + f);
        return Files.readString(f, StandardCharsets.UTF_8);
    }

    private static Path repoRoot() throws IOException {
        Path p = Paths.get("").toAbsolutePath();
        while (p != null) {
            if (Files.isDirectory(p.resolve("ecos_backend"))) return p;
            Path parent = p.getParent();
            if (parent == null || parent.equals(p)) break;
            p = parent;
        }
        throw new IllegalStateException("cwd 未锚定 ECOS 仓库根（找不到 ecos_backend/）");
    }
}
