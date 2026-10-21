package com.chinacreator.gzcm.common.security.caller;

import com.chinacreator.gzcm.common.security.caller.CallerCredentialSource.Carrier;
import com.chinacreator.gzcm.common.security.header.HeaderAuthInterceptor;
import com.chinacreator.gzcm.common.testscan.SourceCommentStripper;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 调用方凭证载体的<b>单源门禁</b>（#142/B4-3e-21，收口差距 <b>D-B4-61</b>「身份载体无单源」；
 * 与同族 {@code SqlReadOnlyFloorSingleSourceTest}（D-B4-60）、
 * {@code SecurityDenialHttpStatusRatchetTest}（D-B4-59）、
 * {@code GuardrailObligationVocabularySingleSourceTest}（D-B4-31）同构）。
 *
 * <p><b>差距形态</b>：「从当前请求取出调用方凭证、逐字中继到出站引擎调用」在合流前有
 * <b>6 个各自成形的实现面</b>（workspace 两支 + data-engine pipeline + cognitive 回测裁决 +
 * ontology 血缘守卫 + agent-service 查表工具）。前三支逐字节同文地中继
 * {@code Authorization} + 四枚 {@code X-ECOS-*} 信任头，后三支只取 {@code Authorization}；
 * 同一条「无请求上下文」分支在三处<b>静默不加头</b>、在一处翻成<b>显式 503</b>。
 * 复制体各改各的 ⇒ 任何一支漏头、或加了默认主语，那支调用的实际安全语义就跟着变，
 * 而且没有门会报警。</p>
 *
 * <p><b>本门钉四件事</b>：
 * <ol>
 *   <li><b>单源的行为就是纪律本身</b>（{@code carrier*} 用例）：无请求上下文 ⇒ {@link Carrier#EMPTY}
 *       而非 {@code null}、更而非兜底主语；空白头按「未携带」；只中继已带、不升权；
 *       {@link Carrier#present()} 能把「没凭证」与「有凭证但被拒」分开（§4.7.2 不可用 ≠ 无权限）。</li>
 *   <li><b>实现面归零</b>：全后端主源码里「同时出现 {@code RequestContextHolder} 与凭证头字面」的
 *       文件<b>只允许单源自己这 1 个</b>——新增一支本地复制即即刻变红。这是差距的本体判据。</li>
 *   <li><b>面外登记、只减不增</b>：入站侧读 {@code Authorization} 的两支（sysman
 *       {@code JwtAuthenticationFilter} 验签入口、gateway {@code RateLimitFilter} 限流键）
 *       <b>不是中继</b>，判为面外但逐文件计数入基线；审计面「{@code "anonymous"} 兜底主语」的
 *       5 个持有者登记为显式豁免集。两者都写成<b>能被读数推翻</b>的基线，不是一句注释。</li>
 *   <li><b>委托真在链上</b>：6 条腿必须各自引用 {@link CallerCredentialSource} 且引用<b>正确那一面</b>
 *       （全头中继腿用 {@code applyTo(}，单头腿用 {@code currentAuthorization(}）——
 *       只加「零违例」不加本断言，下一版把委托摘掉换回本地读头仍能全绿。</li>
 * </ol>
 *
 * <p><b>假绿防护</b>：① 扫描先经 {@link SourceCommentStripper} 剥注释（javadoc 里提
 * {@code RequestContextHolder} 是文档行为，不算实现面——口径单源同 D-B4-40）；
 * ② 扫描根解析文件数阈值（读不到 ≠ 无违例）；③ 基线每个文件都必须实扫到，登记面失真即红；
 * ④ 反向取证用仓外合成文本，不往仓库塞违例夹具。</p>
 */
class CallerCredentialSourceSingleSourceTest {

    /** 单源自身（实现面唯一合法持有者）。 */
    private static final String SINGLE_SOURCE =
            "ecos_backend/runtime/common-api/src/main/java/com/chinacreator/gzcm/common/security/"
                    + "caller/CallerCredentialSource.java";

    /**
     * 实现面判据的前半：读线程绑定的请求上下文。单独出现不违例（拦截器/切面也要读请求），
     * 必须与后半同时命中才构成「本地取凭证」。
     */
    private static final Pattern REQUEST_CONTEXT = Pattern.compile("RequestContextHolder");

    /** 实现面判据的后半：凭证头的字面引用（{@code Authorization} 或四枚 {@code X-ECOS-*} 常量）。 */
    private static final Pattern CREDENTIAL_HEADER = Pattern.compile(
            "HttpHeaders\\s*\\.\\s*AUTHORIZATION|\"Authorization\"|HeaderAuthInterceptor\\s*\\.\\s*H_"
                    + "(?:USER|ROLE|TENANT|DOMAIN)\\b");

    /** 面外登记：入站认证/限流读 {@code Authorization} 字面（验签入口与限流键，非中继）。 */
    private static final Pattern INBOUND_AUTHORIZATION_READ = Pattern.compile("getHeader\\s*\\(\\s*\"Authorization\"");

    /** 面外登记：审计兜底主语（单源有意不采用；余面只量化不收口）。 */
    private static final Pattern ANONYMOUS_FALLBACK = Pattern.compile("\"anonymous\"");

    /** 入站读 Authorization 的面外基线（实测于 #142/B4-3e-21）：文件 → 处数，只减不增。 */
    private static final Map<String, Integer> INBOUND_BASELINE = inboundBaseline();

    private static Map<String, Integer> inboundBaseline() {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("ecos_backend/gateway/src/main/java/com/chinacreator/gzcm/gateway/filter/RateLimitFilter.java", 1);
        m.put("ecos_backend/services/sysman/impl/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/"
                + "security/JwtAuthenticationFilter.java", 1);
        return m;
    }

    /**
     * 审计兜底主语的面外持有者（实测于 #142/B4-3e-21，剥注释后仍命中的 5 文件）。
     * 这些是<b>审计标注</b>面（缺主语时把操作人记成 anonymous 供审计查询），
     * 不参与安全裁决主语，故不并入本单源；但名单必须显式在册，新增持有者即红。
     */
    private static final Set<String> ANONYMOUS_HOLDERS = Set.of(
            "ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/gzcm/engine/ai/"
                    + "audit/AiSecurityEngineAuditInterceptor.java",
            "ecos_backend/engine/security-engine/security-engine-impl/src/main/java/com/chinacreator/gzcm/"
                    + "engine/security/controller/AuditController.java",
            "ecos_backend/gateway/src/main/java/com/chinacreator/gzcm/gateway/filter/RateLimitFilter.java",
            "ecos_backend/services/sysman/impl/sysman-impl/src/main/java/com/chinacreator/gzcm/sysman/"
                    + "aspect/AuditAspect.java",
            "ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/workspace/audit/"
                    + "WorkspaceSecurityEngineAuditInterceptor.java");

    /** 六条已并轨的出站腿：文件 → 必须引用的单源面（全头中继 / 单头取用）。 */
    private static final Map<String, String> LEGS = legs();

    private static Map<String, String> legs() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/workspace/security/"
                + "CallerCredentialRelay.java", "CallerCredentialSource.applyTo(");
        m.put("ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/workspace/security/"
                + "WorkspaceSecurityEngineClient.java", "CallerCredentialSource.applyTo(");
        m.put("ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/"
                + "data/pipeline/PipelineSecurityService.java", "CallerCredentialSource.applyTo(");
        m.put("ecos_backend/engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/chinacreator/gzcm/"
                + "engine/cognitive2/service/backtest/SecurityPolicyReviewRoleDecider.java",
                "CallerCredentialSource.currentAuthorization(");
        m.put("ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/java/com/chinacreator/gzcm/"
                + "engine/ontology/service/DatNetLineageGuard.java",
                "CallerCredentialSource.currentAuthorization(");
        m.put("ecos_backend/services/agent-service/src/main/java/com/chinacreator/gzcm/services/agent/runtime/"
                + "toolrouter/tools/QueryPhysicalTableTool.java",
                "CallerCredentialSource.currentAuthorization(");
        return m;
    }

    // ── 一、单源行为 = 三条纪律 ────────────────────────────────────────────────

    @Test
    @DisplayName("无请求上下文：载体是显式 EMPTY，不加头也不伪造主语，且绝不返回 null")
    void carrierIsEmptyOnNonRequestThreadWithoutFabricatingASubject() {
        RequestContextHolder.resetRequestAttributes();

        Carrier carrier = CallerCredentialSource.current();
        assertEquals(Carrier.EMPTY, carrier,
                "非请求线程必须取到 EMPTY（伪造一个默认 user/tenant 就是把 plumbing 缺口变成越权主语）");
        assertFalse(carrier.present(), "EMPTY 的 present() 必须为 false：缺失态要能被调用方读出来");
        assertNull(CallerCredentialSource.currentAuthorization(),
                "取不到 Authorization 必须返回 null，不能返回空串或兜底 token");

        HttpHeaders headers = new HttpHeaders();
        CallerCredentialSource.applyTo(headers);
        assertTrue(headers.isEmpty(),
                "无上下文时不得写入任何凭证头（写了假头，属主侧就会把'没有主语'误读成'有主语'）：" + headers);
    }

    @Test
    @DisplayName("逐字中继：五枚凭证头原样转发，不拼接、不改写、不升权")
    void carrierRelaysEveryPresentHeaderVerbatim() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer signed-user-jwt");
        request.addHeader(HeaderAuthInterceptor.H_USER, "u-1001");
        request.addHeader(HeaderAuthInterceptor.H_ROLE, "auditor");
        request.addHeader(HeaderAuthInterceptor.H_TENANT, "tenant-A");
        request.addHeader(HeaderAuthInterceptor.H_DOMAIN, "domain-2");
        bind(request);

        Carrier carrier = CallerCredentialSource.current();
        assertEquals("Bearer signed-user-jwt", carrier.authorization());
        assertEquals("u-1001", carrier.user());
        assertEquals("auditor", carrier.role());
        assertEquals("tenant-A", carrier.tenant());
        assertEquals("domain-2", carrier.domain());
        assertTrue(carrier.present());

        HttpHeaders headers = new HttpHeaders();
        CallerCredentialSource.applyTo(headers, carrier);
        assertEquals(List.of("Bearer signed-user-jwt"), headers.get(HttpHeaders.AUTHORIZATION));
        assertEquals(List.of("u-1001"), headers.get(HeaderAuthInterceptor.H_USER));
        assertEquals(List.of("auditor"), headers.get(HeaderAuthInterceptor.H_ROLE));
        assertEquals(List.of("tenant-A"), headers.get(HeaderAuthInterceptor.H_TENANT));
        assertEquals(List.of("domain-2"), headers.get(HeaderAuthInterceptor.H_DOMAIN));
        assertEquals(5, headers.size(), "中继不得额外发明头（多写一枚就是把信任面扩出去）：" + headers);
    }

    @Test
    @DisplayName("空白头按未携带：不发空值头，'没凭证'与'凭证坏了'必须是两个态")
    void blankHeadersAreTreatedAsAbsentNotAsCarriers() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HttpHeaders.AUTHORIZATION, "   ");
        request.addHeader(HeaderAuthInterceptor.H_TENANT, "");
        bind(request);

        Carrier carrier = CallerCredentialSource.current();
        assertNull(carrier.authorization(), "空白 Authorization 必须归成未携带：发出空值头会让验签器把它当'有凭证但坏了'");
        assertNull(carrier.tenant());
        assertFalse(carrier.present());

        HttpHeaders headers = new HttpHeaders();
        CallerCredentialSource.applyTo(headers);
        assertTrue(headers.isEmpty(), "空白头不得被中继出去: " + headers);
    }

    @Test
    @DisplayName("部分携带只中继有的：缺 Authorization 但带信任头时 present() 仍为真（缺失态可分）")
    void partialCarriersKeepTheirOwnShape() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader(HeaderAuthInterceptor.H_TENANT, "tenant-A");
        bind(request);

        Carrier carrier = CallerCredentialSource.current();
        assertNull(carrier.authorization());
        assertTrue(carrier.present(),
                "present() 的语义是'本腿取到了凭证头'，调用方据此才能把传输缺口报成不可用而不是 DENY");

        HttpHeaders headers = new HttpHeaders();
        CallerCredentialSource.applyTo(headers, carrier);
        assertEquals(1, headers.size(), "未携带的头不得补值: " + headers);
        assertEquals(List.of("tenant-A"), headers.get(HeaderAuthInterceptor.H_TENANT));
    }

    @Test
    @DisplayName("载体按头名取值：五枚受控头可取，非受控头显式拒绝")
    void carrierValueOfCoversOnlyControlledHeaders() {
        Carrier carrier = new Carrier("auth", "u", "r", "t", "d");
        assertEquals("auth", carrier.valueOf(HttpHeaders.AUTHORIZATION));
        assertEquals("u", carrier.valueOf(HeaderAuthInterceptor.H_USER));
        assertEquals("r", carrier.valueOf(HeaderAuthInterceptor.H_ROLE));
        assertEquals("t", carrier.valueOf(HeaderAuthInterceptor.H_TENANT));
        assertEquals("d", carrier.valueOf(HeaderAuthInterceptor.H_DOMAIN));
        assertThrows(IllegalArgumentException.class, () -> carrier.valueOf("X-ECOS-SERVICE"),
                "非受控凭证头必须拒绝取值：静默返回 null 会让调用方把'问错了头'读成'没带'");
    }

    @Test
    @DisplayName("applyTo 对 null 入参保持 no-op：不得把 NPE 变成出站调用失败")
    void applyToIsNullSafe() {
        bind(new MockHttpServletRequest());
        CallerCredentialSource.applyTo(null, CallerCredentialSource.current());
        CallerCredentialSource.applyTo(new HttpHeaders(), Carrier.EMPTY);
        CallerCredentialSource.applyTo(new HttpHeaders(), null);
    }

    // ── 二、实现面归零（差距本体） ─────────────────────────────────────────────

    @Test
    @DisplayName("本地取凭证的实现面 = 恰好单源 1 个文件：新增复制体即红")
    void credentialCarrierImplementationFaceIsTheSingleSourceOnly() {
        List<Scanned> scanned = backendMainSources();
        Set<String> offenders = new TreeSet<>();
        for (Scanned s : scanned) {
            if (REQUEST_CONTEXT.matcher(s.code()).find() && CREDENTIAL_HEADER.matcher(s.code()).find()) {
                offenders.add(s.relPath());
            }
        }
        assertEquals(Set.of(SINGLE_SOURCE), offenders,
                "D-B4-61 复现：除单源外又有文件自己从请求上下文取凭证——复制体各改各的，"
                        + "「无上下文」分支的语义会重新分叉。多出/缺失的清单: " + offenders);
    }

    // ── 三、面外登记（取舍是量化的，不是漏判） ─────────────────────────────────

    @Test
    @DisplayName("入站读 Authorization 的面外基线：逐文件同数，新增/上升/下降都变红")
    void inboundReadersAreRatchetedNotSilentlyIgnored() {
        Map<String, Integer> actual = new TreeMap<>();
        for (Scanned s : backendMainSources()) {
            int hits = countHits(INBOUND_AUTHORIZATION_READ, s.code());
            if (hits > 0) {
                actual.put(s.relPath(), hits);
            }
        }
        List<String> diff = new ArrayList<>();
        for (Map.Entry<String, Integer> e : new TreeMap<>(INBOUND_BASELINE).entrySet()) {
            int got = actual.getOrDefault(e.getKey(), 0);
            if (got != e.getValue()) {
                diff.add(e.getKey() + " 基线 " + e.getValue() + " → 实扫 " + got);
            }
        }
        for (Map.Entry<String, Integer> e : actual.entrySet()) {
            if (!INBOUND_BASELINE.containsKey(e.getKey())) {
                diff.add("未登记的入站读取点（须先判它是验签入口还是中继，中继即本单源的违例）: "
                        + e.getKey() + " 实扫 " + e.getValue());
            }
        }
        assertTrue(diff.isEmpty(), "入站读 Authorization 的面外登记失真:\n" + String.join("\n", diff));
    }

    @Test
    @DisplayName("审计兜底主语余面 = 在册 5 文件：本单源不得引入兜底，余面不得扩")
    void anonymousFallbackHoldersStayOnTheRegister() {
        Set<String> actual = new TreeSet<>();
        for (Scanned s : backendMainSources()) {
            if (ANONYMOUS_FALLBACK.matcher(s.code()).find()
                    && s.code().contains("getHeader(")) {
                actual.add(s.relPath());
            }
        }
        assertFalse(actual.contains(SINGLE_SOURCE),
                "单源里出现 \"anonymous\" 兜底＝把 fail-closed 换成了默认主语（§2.4-6）");
        assertEquals(ANONYMOUS_HOLDERS, actual,
                "审计兜底主语持有者名单失真（审计标注面，不参与裁决主语；新增持有者须显式登记并说明理由）:\n"
                        + String.join("\n", diffOf(ANONYMOUS_HOLDERS, actual)));
    }

    private static List<String> diffOf(Set<String> expected, Set<String> actual) {
        List<String> diff = new ArrayList<>();
        for (String e : new TreeSet<>(expected)) {
            if (!actual.contains(e)) {
                diff.add("已消失但基线未下调: " + e);
            }
        }
        for (String a : new TreeSet<>(actual)) {
            if (!expected.contains(a)) {
                diff.add("新持有者: " + a);
            }
        }
        return diff;
    }

    // ── 四、委托真在链上（摘掉委托即红） ───────────────────────────────────────

    @Test
    @DisplayName("六条出站腿各自引用单源的正确一面")
    void everyLegStillDelegatesToTheSingleSource() {
        List<String> broken = new ArrayList<>();
        for (Map.Entry<String, String> e : LEGS.entrySet()) {
            String code = codeOf(e.getKey());
            if (!code.contains(e.getValue())) {
                broken.add(e.getKey() + " 不再引用 " + e.getValue());
            }
            if (REQUEST_CONTEXT.matcher(code).find()) {
                broken.add(e.getKey() + " 又自己绑定请求上下文（复制体复现）");
            }
        }
        assertTrue(broken.isEmpty(), "D-B4-61 的收口被绕过:\n" + String.join("\n", broken));
    }

    // ── 五、反向取证（判据能变红） ─────────────────────────────────────────────

    @Test
    @DisplayName("合成复制体被判据抓到，委托形态被放过")
    void matcherTurnsRedOnSyntheticLocalCarrier() {
        String localCopy = String.join("\n", List.of(
                "class SyntheticLeg {",
                "    void relay(HttpHeaders headers) {",
                "        RequestAttributes attrs = RequestContextHolder.getRequestAttributes();",
                "        if (attrs instanceof ServletRequestAttributes sa) {",
                "            HttpServletRequest r = sa.getRequest();",
                "            String a = r.getHeader(\"Authorization\");",
                "            if (a != null) headers.set(HttpHeaders.AUTHORIZATION, a);",
                "            String t = r.getHeader(HeaderAuthInterceptor.H_TENANT);",
                "            if (t != null) headers.set(HeaderAuthInterceptor.H_TENANT, t);",
                "        }",
                "    }",
                "}"));
        assertTrue(REQUEST_CONTEXT.matcher(localCopy).find(), "合成体必须命中请求上下文判据");
        assertTrue(CREDENTIAL_HEADER.matcher(localCopy).find(),
                "判据抓不到本地取凭证＝本门是一枚恒绿门（D-B4-61 的复现形态将无人报警）");
        assertEquals(1, countHits(INBOUND_AUTHORIZATION_READ, localCopy),
                "入站读取判据必须能计量 getHeader(\"Authorization\")");

        String honest = String.join("\n", List.of(
                "class DelegatedLeg {",
                "    void relay(HttpHeaders headers) { CallerCredentialSource.applyTo(headers); }",
                "    String auth() { return CallerCredentialSource.currentAuthorization(); }",
                "}"));
        assertFalse(REQUEST_CONTEXT.matcher(honest).find(),
                "收口后的委托形态被同时命中＝判据会把修法本身拦在门外，实现面永远降不到 1");
        assertFalse(CREDENTIAL_HEADER.matcher(honest).find(), "委托形态不得被判成实现面违例");

        String commentOnly = SourceCommentStripper.strip(
                "/** 历史缺陷：三支腿各自 RequestContextHolder + getHeader(\"Authorization\") 复制. */\n"
                        + "class Clean {}\n");
        assertFalse(REQUEST_CONTEXT.matcher(commentOnly).find()
                        && CREDENTIAL_HEADER.matcher(commentOnly).find(),
                "注释里的键名是文档行为，判据不得把它算成实现面（口径单源 SourceCommentStripper）");
    }

    // ── 夹具与扫描 ─────────────────────────────────────────────────────────────

    private record Scanned(String relPath, String code) {
    }

    private static void bind(HttpServletRequest request) {
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    @AfterEach
    void unbind() {
        RequestContextHolder.resetRequestAttributes();
    }

    private static Path repoRoot() {
        Path start = Paths.get(System.getProperty("user.dir")).toAbsolutePath();
        for (int i = 0; i < 12 && start != null; i++) {
            if (Files.isDirectory(start.resolve("ecos_backend"))
                    && Files.isDirectory(start.resolve("ecos-docker"))) {
                return start;
            }
            start = start.getParent();
        }
        return fail("未能从 user.dir 定位仓根（需同时含 ecos_backend 与 ecos-docker），本门禁不得默认通过");
    }

    private static List<Scanned> backendMainSources() {
        Path root = repoRoot().resolve("ecos_backend");
        List<Scanned> out = new ArrayList<>();
        try (var walk = Files.walk(root)) {
            List<Path> files = new ArrayList<>();
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .filter(p -> p.toString().replace('\\', '/').contains("/src/main/java/"))
                    .filter(p -> !p.toString().replace('\\', '/').contains("/target/"))
                    .filter(p -> !p.toString().replace('\\', '/').contains("/archive/"))
                    .forEach(files::add);
            assertTrue(files.size() > 500,
                    "主源码扫描根解析到的文件数异常（" + files.size() + "）——「读不到」不得当成「无违例」");
            for (Path f : files) {
                String rel = repoRoot().relativize(f).toString().replace('\\', '/');
                out.add(new Scanned(rel, stripComments(new String(
                        Files.readAllBytes(f), StandardCharsets.UTF_8))));
            }
        } catch (IOException e) {
            throw new AssertionError("扫描主源码失败: " + e.getMessage(), e);
        }
        return out;
    }

    private static String stripComments(String source) {
        return SourceCommentStripper.strip(source);
    }

    private static int countHits(Pattern pattern, String code) {
        int hits = 0;
        for (String raw : code.split("\\R", -1)) {
            Matcher m = pattern.matcher(raw);
            while (m.find()) {
                hits++;
            }
        }
        return hits;
    }

    private static String codeOf(String relative) {
        Path p = repoRoot().resolve(relative);
        if (!Files.isRegularFile(p)) {
            fail("门禁依赖的文件不存在，不得当作\"无违例\": " + relative);
        }
        try {
            return stripComments(new String(Files.readAllBytes(p), StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new AssertionError("读取源码失败: " + p, e);
        }
    }
}
