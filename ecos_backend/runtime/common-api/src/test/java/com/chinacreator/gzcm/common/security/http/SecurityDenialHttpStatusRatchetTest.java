package com.chinacreator.gzcm.common.security.http;

import com.chinacreator.gzcm.common.security.rls.RlsExecutionContract;
import com.chinacreator.gzcm.common.testscan.SourceCommentStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 安全拒绝态的 <b>HTTP 状态行保真棘轮门</b>（#142/B4-3e-18，收口差距 <b>D-B4-59</b> 的
 * 「属主端点裸回」面；与同族 {@code GuardrailObligationVocabularySingleSourceTest}（D-B4-31）、
 * {@code RateLimitObligationConsumerGateTest}（D-B4-21）、gateway
 * {@code DataBridgeHttpStatusFidelityTest}（D-B4-52）同构）。
 *
 * <p><b>差距形态</b>：{@code ApiResponse} 只描述 body，<b>没有任何 HTTP 状态能力</b>。
 * Controller 直接 {@code return ApiResponse.forbidden(...)} 时，Spring 落的状态行是
 * <b>HTTP 200</b>：拒绝语义只活在信封的 {@code code} 里，传输层看不到。后果不是「不好看」，
 * 而是三条真实后果——①§4.5.4／§4.7.2 要求的「不可用（503）≠ 无权限（403）」分态
 * 在状态行上同形，任何只看状态行的中间层（网关、重试、告警、Playwright 用例）都无法分派；
 * ②前端 {@code if (!res.ok) throw} 一律读成成功，拒绝被渲染成「成功 0 行」；
 * ③{@code RestTemplate} 侧非 2xx 才抛异常，于是 200 信封被当成成功应答继续解 {@code data}。</p>
 *
 * <p><b>本门钉三件事</b>：
 * <ol>
 *   <li><b>锚点已收口且不得回退</b>：data-engine 属主端点 {@code QueryController#execute}
 *       的三条安全拒绝出口（503 不可用／403 策略 DENY／403 绑定失败）必须经
 *       {@code ResponseEntity} 落真状态行——基线里它的违例数钉死为 {@code 0}，
 *       同时断言它<b>确实引用了</b>状态行承载手段与 {@link RlsExecutionContract#SEC_UNAVAILABLE_HTTP}
 *       单源（只加「零违例」不加委托断言，下一版重构把 {@code ResponseEntity} 摘掉仍能全绿）。</li>
 *   <li><b>余面量化、只减不增</b>：全后端主源码内其余 {@code return ApiResponse.forbidden/
 *       unauthorized(...)} 与 {@code return ApiResponse.error(401|403|503, ...)}
 *       逐文件计数入基线；新增持有者或既有文件计数上升＝即刻变红，
 *       计数<b>下降同样变红</b>（逼着收口批把基线一起下调，防「基线陈旧＝已收口」的假绿）。
 *       其它失败码形态（400/404/405/409/410/500）<b>不在本门判据面内</b>：它们不是安全裁决线，
 *       混进来会把「权限」与「参数错误」稀释成同一个信号，且那部分属主侧修法要另立批次。
 *       本门另设一条 {@code nonDenialFailureLegsAreRecordedNotSilentlyIgnored} 把它的实测量
 *       钉成只减不增上限，确保「不入面」是登记过的取舍，不是漏判。</li>
 *   <li><b>判据能变红</b>：{@code matcherTurnsRedOnSyntheticBareLiteral} 用仓外合成文本取证，
 *       不往仓库塞违例夹具。</li>
 * </ol>
 *
 * <p><b>假绿防护</b>：① 扫描根解析文件数阈值（读不到 ≠ 无违例）；② 先经
 * {@link SourceCommentStripper} 剥注释再比对（文档里提 {@code forbidden} 不算违例，
 * 口径单源同 D-B4-40）；③ 基线里每个文件都必须<b>实扫到</b>，登记面失真即红；
 * ④ 断言用的锚点路径逐字命中，不靠文件名猜测。</p>
 */
class SecurityDenialHttpStatusRatchetTest {

    /** 已收口锚点：data-engine 属主查询端点（基线违例数钉死 0，回退即红）。 */
    private static final String QUERY_CONTROLLER =
            "ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/"
                    + "gzcm/engine/data/controller/QueryController.java";

    /**
     * 判据 A（本门面内）：安全拒绝／不可用态经裸 {@code ApiResponse} 出口返回
     * ＝状态行恒 200。只认 {@code return ApiResponse.xxx(} 这一「直接返回」形态：
     * 收口后的 {@code return ResponseEntity.status(...).body(ApiResponse.forbidden(...))}
     * 不命中，正是「信封不变、只补状态行」这一修法应有的读数。
     *
     * <p><b>类型见证分支（#142/B4-3e-31 收口差距 D-B4-68）</b>：本判据在 2026-10-22 前只写
     * {@code ApiResponse\s*\.\s*(?:forbidden...)}，而 Java 允许显式类型见证
     * {@code return ApiResponse.<Map<String, Object>>error(503, ...)}——点号后先跟
     * {@code <…>}，原式在 {@code <} 处断链，于是这类腿<b>结构性失明</b>（不是漏登记，是看不见）。
     * 实测盲区面＝5 处／2 文件（gateway {@code RuntimeAlertV1Controller} 3×503、
     * kb {@code KbProfileController} 1×503＋1×403），真实裸回拒绝面＝27＋5＝32 处／11 文件。
     * 见证分组写成 {@code <[^();]*?>}：禁 {@code (} 与 {@code ;} ⇒ 只吃类型参数，绝不跨到
     * 实参表或下一条语句；{@code return} 锚点保证 {@code ResponseEntity...body(ApiResponse.<T>...)}
     * 这类已收口形态仍不误伤。</p>
     */
    private static final Pattern BARE_DENIAL_LEG = Pattern.compile(
            "return\\s+ApiResponse\\s*\\.\\s*(?:<[^();]*?>\\s*)?"
                    + "(?:forbidden\\s*\\(|unauthorized\\s*\\(|error\\s*\\(\\s*(?:401|403|503)\\b)");

    /**
     * 判据 B（面外登记量）：其它失败码裸回（400/404/405/409/410/500），只记量不判违例。
     * 同批补同一枚类型见证分支——判据 B 若仍瞎，「面外量化」就只是「同一盲区的另一个读数」，
     * 上限会冒充成已收口的证据。
     */
    private static final Pattern BARE_OTHER_FAILURE_LEG = Pattern.compile(
            "return\\s+ApiResponse\\s*\\.\\s*(?:<[^();]*?>\\s*)?"
                    + "(?:badRequest\\s*\\(|notFound\\s*\\(|internalError\\s*\\("
                    + "|error\\s*\\(\\s*(?:400|404|405|409|410|500)\\b)");

    /**
     * 判据 B 的只减不增上限（#142/B4-3e-31 按补盲后的口径<b>重测</b>登记，本批不收口）：
     * 全后端主源码裸回非拒绝态 <b>921 处</b>（盲前面 910 ⇒ 类型见证补盲净新增 11 处；
     * 读数由 {@code nonDenialFailureLegsAreRecordedNotSilentlyIgnored} 的上限断言在本批
     * 门禁会话里实跑产出，非估算——同批手工 grep 估成 922，被该读数推翻后照实订正）。
     *
     * <p>这个量级正是 {@code ApiResponse}「只描述 body、不带状态行」的代价面，比 D-B4-59 的
     * 属主锚点大两个数量级 ⇒ 本门只把<b>安全裁决线</b>（判据 A，补盲后实测 32 处／11 文件）
     * 逐文件入基线，非拒绝态面以总量上限登记在册。上限写成实测值而非"顺手归零"，是为了让
     * 「有意不扩面」成为一个<b>可被读数推翻</b>的声明，而不是一句注释。旧值 910 系 D-B4-68
     * 盲区口径下的读数，保留在此作为口径变更凭据：任何「上限＝历史读数」的比对都必须先确认
     * 判据面是否含类型见证分支。</p>
     */
    private static final int OTHER_LEG_CEILING = 921;

    /**
     * 基线（实测于 #142/B4-3e-18）：文件 → 裸回安全拒绝态处数。
     * <b>只减不增</b>；下调必须与收口批同批改，让「基线」永远等于「实扫」。
     */
    private static final Map<String, Integer> BASELINE = baseline();

    private static Map<String, Integer> baseline() {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put(QUERY_CONTROLLER, 0);
        m.put("ecos_backend/engine/kb-engine/kb-engine-impl/src/main/java/com/chinacreator/gzcm/"
                + "engine/kb/controller/NavTaxonController.java", 13);
        m.put("ecos_backend/gateway/src/main/java/com/chinacreator/gzcm/gateway/handler/"
                + "GlobalExceptionHandler.java", 3);
        m.put("ecos_backend/services/sysman/impl/sysman-impl/src/main/java/com/chinacreator/gzcm/"
                + "sysman/security/RequirePermissionAspect.java", 3);
        m.put("ecos_backend/services/sysman/impl/sysman-boot/src/main/java/com/chinacreator/gzcm/"
                + "sysman/boot/handler/GlobalExceptionHandler.java", 2);
        m.put("ecos_backend/engine/security-engine/security-engine-impl/src/main/java/com/chinacreator/"
                + "gzcm/engine/security/controller/DecisionController.java", 2);
        m.put("ecos_backend/services/sysman/impl/sysman-impl/src/main/java/com/chinacreator/gzcm/"
                + "sysman/controller/SecurityPolicyController.java", 1);
        m.put("ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/java/com/chinacreator/"
                + "gzcm/engine/ontology/service/PublishGateService.java", 1);
        m.put("ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java/com/chinacreator/gzcm/"
                + "engine/ai/controller/AIPAgentController.java", 1);
        m.put("ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/"
                + "engine/data/controller/WriteChannelController.java", 1);
        // ── #142/B4-3e-31（收口 D-B4-68）：判据补类型见证分支后新入面的两枚持有者 ──
        m.put("ecos_backend/gateway/src/main/java/com/chinacreator/gzcm/gateway/controller/"
                + "RuntimeAlertV1Controller.java", 3);
        m.put("ecos_backend/engine/kb-engine/kb-engine-impl/src/main/java/com/chinacreator/gzcm/"
                + "engine/kb/controller/KbProfileController.java", 2);
        return m;
    }

    /** 属主侧「修法登记」：这些出口已被判为「必须走状态行」，禁再退回头。 */
    private static final List<String> CLOSED_OWNERS = List.of(QUERY_CONTROLLER);

    // ── 验收 1：全后端裸回面逐文件计数，只减不增 ──────────────────────────────

    @Test
    @DisplayName("裸回安全拒绝态面 = 基线逐文件同数：新增/上升即红，下降也即红（逼基线同批下调）")
    void bareDenialLegsMatchTheRegisteredBaselineExactly() {
        List<Scanned> scanned = backendMainSources();
        assertTrue(scanned.size() > 500,
                "主源码扫描根解析到的文件数异常（" + scanned.size() + "）——「读不到」不得当成「无违例」");

        Map<String, Integer> actual = new TreeMap<>();
        for (Scanned s : scanned) {
            int hits = countHits(BARE_DENIAL_LEG, s.code());
            if (hits > 0) {
                actual.put(s.relPath(), hits);
            }
        }

        Map<String, Integer> expected = new TreeMap<>(BASELINE);
        List<String> diff = new ArrayList<>();
        for (Map.Entry<String, Integer> e : expected.entrySet()) {
            int got = actual.getOrDefault(e.getKey(), 0);
            if (got > e.getValue()) {
                diff.add("新增/上升: " + e.getKey() + " 基线 " + e.getValue() + " → 实扫 " + got);
            } else if (got < e.getValue()) {
                diff.add("基线陈旧（该文件已收窄，须同批下调基线并登记批次，否则「基线」会冒充「已收口」）: "
                        + e.getKey() + " 基线 " + e.getValue() + " → 实扫 " + got);
            }
        }
        for (Map.Entry<String, Integer> e : actual.entrySet()) {
            if (!expected.containsKey(e.getKey())) {
                diff.add("未登记的新持有者（属主侧修法须走裁决批）: " + e.getKey() + " 实扫 " + e.getValue());
            }
        }
        int totalExpected = expected.values().stream().mapToInt(Integer::intValue).sum();
        int totalActual = actual.values().stream().mapToInt(Integer::intValue).sum();
        assertTrue(diff.isEmpty(),
                "安全拒绝态的 HTTP 状态行保真面失真（实扫合计 " + totalActual + " 处／基线合计 "
                        + totalExpected + " 处）:\n" + String.join("\n", diff));
    }

    // ── 验收 2：锚点委托真在链上（摘掉 ResponseEntity 即红） ──────────────────

    @Test
    @DisplayName("已收口锚点必须引用状态行承载手段与不可用态单源常量")
    void closedAnchorsStillDelegateToStatusLineCarrier() {
        String q = codeOf(QUERY_CONTROLLER);
        assertTrue(q.contains("ResponseEntity"),
                "QueryController 的拒绝腿又退回裸 ApiResponse＝D-B4-59 复现（状态行将恒 200）");
        assertTrue(q.contains("ResponseEntity.status("),
                "拒绝腿必须显式携带状态行（ResponseEntity.status(...)），ok() 只放行成功腿");
        assertTrue(q.contains("RlsExecutionContract.SEC_UNAVAILABLE_HTTP"),
                "不可用态的 503 未取单源常量（状态行与信封两处各写一遍＝会漂移）");
        assertEquals(RlsExecutionContract.SEC_UNAVAILABLE_HTTP, 503,
                "不可用态单源常量已漂移，本门与属主锚点的读数口径不再一致");
        assertFalse(BARE_DENIAL_LEG.matcher(q).find(),
                "QueryController 仍有裸回的安全拒绝出口（锚点收口被绕过）");
        for (String owner : CLOSED_OWNERS) {
            assertTrue(codeOf(owner).contains("RlsExecutionContract"),
                    owner + " 登记为已收口锚点，却不再引用安全裁决契约单源");
        }
    }

    // ── 验收 3：面外失败码的量化登记（取舍是登记的，不是漏判） ────────────────

    @Test
    @DisplayName("其它失败码裸回面（400/404/405/409/410/500）实扫数 ≤ 登记上限，越界即须显式扩基线")
    void nonDenialFailureLegsAreRecordedNotSilentlyIgnored() {
        int total = 0;
        for (Scanned s : backendMainSources()) {
            total += countHits(BARE_OTHER_FAILURE_LEG, s.code());
        }
        assertTrue(total <= OTHER_LEG_CEILING,
                "非安全裁决线的裸回面已扩张到 " + total + " 处（登记上限 " + OTHER_LEG_CEILING
                        + "）。本门有意不收口这类形态（拒绝语义与参数错误必须分信号），"
                        + "但扩张必须显式改基线并说明理由");
    }

    // ── 验收 4：判据能变红（反向 fixture，不落仓） ────────────────────────────

    @Test
    @DisplayName("反向取证：合成裸回必须被判据 A 抓到，而收口后的 ResponseEntity 形态必须放过")
    void matcherTurnsRedOnSyntheticBareLiteral() {
        String bare = String.join("\n", List.of(
                "class SyntheticBareDenial {",
                "    ApiResponse<Map<String,Object>> execute() {",
                "        return ApiResponse.forbidden(\"访问被拒绝\");",
                "    }",
                "}"));
        assertEquals(1, countHits(BARE_DENIAL_LEG, bare),
                "判据抓不到裸回 forbidden＝本门是一枚恒绿门（D-B4-59 的复现形态将无人报警）");

        String bare503 = "return ApiResponse.error(503, \"ECOS-SEC-501\", \"安全服务不可用\");";
        assertEquals(1, countHits(BARE_DENIAL_LEG, bare503), "判据必须抓到裸回的不可用态");

        String honest = String.join("\n", List.of(
                "return ResponseEntity.status(RlsExecutionContract.SEC_UNAVAILABLE_HTTP)",
                "        .body(ApiResponse.error(RlsExecutionContract.SEC_UNAVAILABLE_HTTP, CODE, \"不可用\"));",
                "return ResponseEntity.status(HttpStatus.FORBIDDEN).body(ApiResponse.forbidden(\"拒绝\"));"));
        assertEquals(0, countHits(BARE_DENIAL_LEG, honest),
                "收口后的正确形态被判成违例＝判据会把修法本身拦在门外，基线永远降不下去");

        String commentOnly = SourceCommentStripper.strip(
                "// 历史缺陷：return ApiResponse.forbidden(...) 会让状态行恒 200\n"
                        + "class Clean {}\n");
        assertEquals(0, countHits(BARE_DENIAL_LEG, commentOnly),
                "注释里的键名是文档行为，判据不得把它算成违例（口径单源 SourceCommentStripper）");

        String otherCode = "return ApiResponse.error(409, \"资源冲突\");";
        assertEquals(0, countHits(BARE_DENIAL_LEG, otherCode), "非安全裁决线码不在本门面内（刻意取舍）");
        assertEquals(1, countHits(BARE_OTHER_FAILURE_LEG, otherCode),
                "非安全裁决线码必须被判据 B 计量，否则「不入面」就变成「看不见」");

        // ── D-B4-68 反向取证：类型见证形态（补盲前的失明面）必须被抓到 ──
        String witnessed = "return ApiResponse.<Map<String, Object>>error(503, \"ECOS-AUTH-005\", \"底座不可用\");";
        assertEquals(1, countHits(BARE_DENIAL_LEG, witnessed),
                "判据对 ApiResponse.<T>error(503,…) 类型见证形态失明＝D-B4-68 复现，"
                        + "该形态的状态行同样恒 200，看不见就等于没有门");
        String witnessedForbidden = "return ApiResponse.<KbProfileDO>forbidden(\"权限不足\");";
        assertEquals(1, countHits(BARE_DENIAL_LEG, witnessedForbidden),
                "类型见证后的 forbidden()/unauthorized() 同样必须在面内");
        String witnessedOther = "return ApiResponse.<String>notFound(\"没有匹配画像\");";
        assertEquals(0, countHits(BARE_DENIAL_LEG, witnessedOther), "见证分支不得把非裁决线码拉进面内");
        assertEquals(1, countHits(BARE_OTHER_FAILURE_LEG, witnessedOther),
                "判据 B 必须同等看见类型见证形态（否则「面外量化」沿用同一盲区读数）");

        String witnessedClosed = "return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)"
                + ".body(ApiResponse.<Map<String, Object>>error(503, CODE, \"不可用\"));";
        assertEquals(0, countHits(BARE_DENIAL_LEG, witnessedClosed),
                "收口后的类型见证形态判成违例＝把修法本身拦在门外，基线永远降不下去");

        String witnessedNotStatement = "return ApiResponse.<Void>error(ApiResponse.CODE_BAD_REQUEST, \"BAD_REQUEST\");";
        assertEquals(0, countHits(BARE_DENIAL_LEG, witnessedNotStatement),
                "见证分组必须只吃类型参数：实参表里的常量名/字符串不得被当成状态字面量（防跨界误判）");
    }

    // ── 扫描与夹具 ────────────────────────────────────────────────────────────

    private record Scanned(String relPath, String code) {
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

    /** 剥注释后的源码（口径单源，防把字符串里的 // 当行注释起点——D-B4-40）。 */
    private static String stripComments(String source) {
        return SourceCommentStripper.strip(source);
    }

    /** 单条违例行文本上限：只截行宽，绝不截条数（与 D-B4-62 的计数口径一致）。 */
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
