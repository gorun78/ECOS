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
     * 判据 B 的只减不增上限（#142/B4-3e-32 按属主侧收口后<b>重测</b>登记）：
     * 全后端主源码裸回非拒绝态 <b>915 处</b>（上一读数为 #142/B4-3e-31 的 921，本批把
     * gateway {@code RuntimeAlertV1Controller#close}（404＋400）与 kb {@code KbProfileController}
     * （400＋409×2＋404）共 <b>6 枚</b>同方法内的非拒绝腿一并写上状态行 ⇒ 921−6＝915；
     * 读数由 {@code nonDenialFailureLegsAreRecordedNotSilentlyIgnored} 的上限断言在本批门禁会话里
     * 以「上限置 0 探测」实跑产出，非估算，也非由旧值做算术推出——算式只用作差异归因，
     * 归因与实际读数不符时以读数为准）。
     *
     * <p>这个量级正是 {@code ApiResponse}「只描述 body、不带状态行」的代价面，比 D-B4-59 的
     * 属主锚点大两个数量级 ⇒ 本门只把<b>安全裁决线</b>（判据 A，#142/B4-3e-31 补盲后实测
     * 32 处／11 文件，#142/B4-3e-32 属主侧收口后 <b>27 处／9 文件</b>）逐文件入基线，
     * 非拒绝态面以总量上限登记在册。上限写成实测值而非"顺手归零"，是为了让
     * 「有意不扩面」成为一个<b>可被读数推翻</b>的声明，而不是一句注释。历史读数 910（D-B4-68
     * 盲区口径）与 921（补盲后、属主侧收口前）保留在此作为口径变更凭据：任何「上限＝历史读数」
     * 的比对都必须先确认判据面是否含类型见证分支、以及该批是否已做属主侧收口。</p>
     *
     * <p><b>#142/B4-3e-33 重设＝915→871</b>：kb {@code NavTaxonController} 一条 505 行的
     * CRUD 控制器独占判据 A 的 13 枚 403 腿，同方法内的非拒绝腿（badRequest 15＋notFound 12
     * ＋internalError 17＝44 枚）与本批 6 次同型收口不同——本批是把整个方法签名一次性改成
     * {@code ResponseEntity}，非拒绝腿若留在裸回形态根本编不过（＝「只补拒绝态」在这种
     * 满篇 try/catch 的控制器上没有可选形态）。故 44 枚一并写上状态行，且状态行取信封
     * {@code code} 经 {@code DataBridgeException.resolveHttpStatus} 单源推出。读数由本批
     * 门禁会话的「上限置 0 探测」实跑产出（871），与旧值做算术差的 915−44＝871 只是归因核对，
     * 二者相符。</p>
     */
    private static final int OTHER_LEG_CEILING = 871;

    /**
     * 判据 A 的合法承载态识别（#142/B4-3e-34，收口差距 <b>D-B4-69</b>「判据对
     * {@code @ResponseStatus} 注解承载态不可见」）。
     *
     * <p>差距形态与前两枚同族——不是漏登记，是<b>结构性看不见</b>：判据只锚
     * {@code return ApiResponse.} 的文本形态，而 {@code @ExceptionHandler} 方法上的
     * {@code @ResponseStatus} 由 Spring 在异常解析阶段直接写响应状态行，这类裸回的
     * 状态行<b>并不恒 200</b>。对余面 14 处逐腿解析上位方法注解块后实测：
     * <b>5 处</b>属注解承载（gateway {@code GlobalExceptionHandler} L165/175/204、
     * sysman-boot {@code GlobalExceptionHandler} L114/126），真裸回 <b>9 处</b>。
     * 危险的方向不是「基线虚高」本身，而是「已保真的和未保长的在同一个数字里长得一样」
     * ⇒ 收口进度不可读、后续批次的差异归因失去基准，且一旦有人为降基数而给普通
     * {@code @RequestMapping} 方法补 {@code @ResponseStatus}，会造出「成功腿配 403 行」的新缺陷。</p>
     *
     * <p>识别口径因此写成「同一方法<b>同时</b>带 {@code @ExceptionHandler} 与
     * {@code @ResponseStatus}」，而非「方法上有 {@code @ResponseStatus}」：普通映射方法上的
     * 该注解对成功腿同样生效，单注解不构成豁免。定不到属主方法时<b>不豁免</b>
     * （向红兜底），使解析器本身的失效方向是「多报」而不是「漏报」。</p>
     */
    private static final Pattern HANDLER_METHOD_SIGNATURE = Pattern.compile(
            "^\\s*(?:(?:public|protected|private|static|final|synchronized|abstract)\\s+)+"
                    + "[\\w<>.,?\\[\\]\\s]+\\s+\\w+\\s*\\(");

    /**
     * 注解承载面基线（实测于 #142/B4-3e-34）：文件 → 由 {@code @ResponseStatus} 真实承载
     * 状态行的裸回腿数。与判据 A 一样做<b>双向等值</b>断言——这条面既不是违例面也不是
     * 免检面：它上涨＝有人把新拒绝腿留在裸回形态、只靠注解兜底（属主侧修法未落地）；
     * 它下跌＝注解被摘掉而腿没接上状态行，那 1 枚会同时出现在判据 A 面并被抓住。
     */
    private static final Map<String, Integer> ANNOTATION_CARRIED_BASELINE =
            annotationCarriedBaseline();

    private static Map<String, Integer> annotationCarriedBaseline() {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put("ecos_backend/gateway/src/main/java/com/chinacreator/gzcm/gateway/handler/"
                + "GlobalExceptionHandler.java", 3);
        m.put("ecos_backend/services/sysman/impl/sysman-boot/src/main/java/com/chinacreator/"
                + "gzcm/sysman/boot/handler/GlobalExceptionHandler.java", 2);
        return m;
    }

    /**
     * 基线（实测于 #142/B4-3e-18）：文件 → 裸回安全拒绝态处数。
     * <b>只减不增</b>；下调必须与收口批同批改，让「基线」永远等于「实扫」。
     */
    private static final Map<String, Integer> BASELINE = baseline();

    private static Map<String, Integer> baseline() {
        Map<String, Integer> m = new LinkedHashMap<>();
        m.put(QUERY_CONTROLLER, 0);
        // ── #142/B4-3e-33 归零：本组最大持有者（13 枚 403 裸回腿）属主侧状态行已落地 ──
        m.put("ecos_backend/engine/kb-engine/kb-engine-impl/src/main/java/com/chinacreator/gzcm/"
                + "engine/kb/controller/NavTaxonController.java", 0);
        // ── #142/B4-3e-34 归零（判据侧，非属主侧）：gateway／sysman-boot 属主 handler 的 5 枚腿
        //    经实测由 @ExceptionHandler＋@ResponseStatus 同法声明真实承载状态行，
        //    移出违例面、改钉入 ANNOTATION_CARRIED_BASELINE；此处钉 0＝注解被摘即回退成红 ──
        m.put("ecos_backend/gateway/src/main/java/com/chinacreator/gzcm/gateway/handler/"
                + "GlobalExceptionHandler.java", 0);
        m.put("ecos_backend/services/sysman/impl/sysman-impl/src/main/java/com/chinacreator/gzcm/"
                + "sysman/security/RequirePermissionAspect.java", 3);
        m.put("ecos_backend/services/sysman/impl/sysman-boot/src/main/java/com/chinacreator/"
                + "gzcm/sysman/boot/handler/GlobalExceptionHandler.java", 0);
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
        // ── #142/B4-3e-32 归零：两枚持有者的属主侧状态行已落地，基线钉 0＝回退即红 ──
        m.put("ecos_backend/gateway/src/main/java/com/chinacreator/gzcm/gateway/controller/"
                + "RuntimeAlertV1Controller.java", 0);
        m.put("ecos_backend/engine/kb-engine/kb-engine-impl/src/main/java/com/chinacreator/gzcm/"
                + "engine/kb/controller/KbProfileController.java", 0);
        return m;
    }

    /** 属主侧「修法登记」：这些出口已被判为「必须走状态行」，禁再退回头。 */
    private static final List<String> CLOSED_OWNERS = List.of(QUERY_CONTROLLER);

    /**
     * #142/B4-3e-32 新增的收口持有者：判据 A 归零且必须自己声明状态行承载手段。
     *
     * <p>与 {@link #CLOSED_OWNERS} 分开列，因两者的单源不同——QueryController 的不可用态取
     * {@link RlsExecutionContract#SEC_UNAVAILABLE_HTTP}，而本组一枚取该契约常量
     * （RuntimeAlertV1）、一枚取本域码表 {@code KbErrorCodes.httpStatus()}（KbProfile，
     * 域内 403/503/409 属 {@code ECOS-KB-*} 而非安全裁决线）。故这里只钉「确有状态行承载手段
     * ＋判据 A 零命中」这两条共性，不假装它们共用同一枚常量。</p>
     */
    private static final List<String> CONVERTED_OWNERS = List.of(
            "ecos_backend/gateway/src/main/java/com/chinacreator/gzcm/gateway/controller/"
                    + "RuntimeAlertV1Controller.java",
            "ecos_backend/engine/kb-engine/kb-engine-impl/src/main/java/com/chinacreator/gzcm/"
                    + "engine/kb/controller/KbProfileController.java",
            "ecos_backend/engine/kb-engine/kb-engine-impl/src/main/java/com/chinacreator/gzcm/"
                    + "engine/kb/controller/NavTaxonController.java");

    // ── 验收 1：全后端裸回面逐文件计数，只减不增 ──────────────────────────────

    @Test
    @DisplayName("裸回安全拒绝态面 = 基线逐文件同数：新增/上升即红，下降也即红（逼基线同批下调）")
    void bareDenialLegsMatchTheRegisteredBaselineExactly() {
        List<Scanned> scanned = backendMainSources();
        assertTrue(scanned.size() > 500,
                "主源码扫描根解析到的文件数异常（" + scanned.size() + "）——「读不到」不得当成「无违例」");

        Map<String, Integer> actual = new TreeMap<>();
        for (Scanned s : scanned) {
            int hits = countDenialLegsByCarrier(s.code())[0];
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

    // ── 验收 1b：注解承载面单独入基线，双向等值（#142/B4-3e-34 收口 D-B4-69） ────

    @Test
    @DisplayName("注解承载面 = 登记基线逐文件同数：腿被挪进豁免面或注解被摘都即红")
    void annotationCarriedLegsArePinnedNotSilentlyExcluded() {
        Map<String, Integer> actual = new TreeMap<>();
        int totalBare = 0;
        for (Scanned s : backendMainSources()) {
            int[] split = countDenialLegsByCarrier(s.code());
            totalBare += split[0];
            if (split[1] > 0) {
                actual.put(s.relPath(), split[1]);
            }
        }

        Map<String, Integer> expected = new TreeMap<>(ANNOTATION_CARRIED_BASELINE);
        List<String> diff = new ArrayList<>();
        for (Map.Entry<String, Integer> e : expected.entrySet()) {
            int got = actual.getOrDefault(e.getKey(), 0);
            if (got > e.getValue()) {
                diff.add("豁免面扩张（新拒绝腿只靠注解兜底、属主侧状态行未落地）: "
                        + e.getKey() + " 基线 " + e.getValue() + " → 实扫 " + got);
            } else if (got < e.getValue()) {
                diff.add("注解被摘或方法结构改到解析器看不见（该腿应已出现在判据 A 面并被抓住）: "
                        + e.getKey() + " 基线 " + e.getValue() + " → 实扫 " + got);
            }
        }
        for (Map.Entry<String, Integer> e : actual.entrySet()) {
            if (!expected.containsKey(e.getKey())) {
                diff.add("未登记的豁免持有者（豁免必须点名，否则「看不见」就冒充「已判过」）: "
                        + e.getKey() + " 实扫 " + e.getValue());
            }
        }
        assertTrue(diff.isEmpty(),
                "@ResponseStatus 注解承载面失真:\n" + String.join("\n", diff));

        int totalExpectedCarried = expected.values().stream().mapToInt(Integer::intValue).sum();
        int totalActualCarried = actual.values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(5, totalExpectedCarried,
                "豁免面登记量已偏离 #142/B4-3e-34 的逐腿实测值 5——判据口径变更必须重测后同批改注释");
        assertEquals(totalExpectedCarried, totalActualCarried,
                "豁免面合计与实扫不符（实扫 " + totalActualCarried + "）");
        assertEquals(9, totalBare,
                "判据 A 真裸回合计应为 #142/B4-3e-34 实测的 9 处，实扫 " + totalBare
                        + "——与基线合计不一致时，说明承载态识别把不该豁免的腿豁免了（或反之）");
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

    // ── 验收 2b：#142/B4-3e-32 收口持有者自证状态行承载手段 ──────────────────

    /**
     * <b>本门的失明面（登记，不假装没有）</b>：判据 A 与这里的 {@code contains(...)} 都是
     * 文本层判据，只会看见「形态」，看不见「状态值」。变异取证（夹具用后已清，复扫
     * {@code MUTANT} = 0）实测：把属主腿折成 {@code ResponseEntity.status(HttpStatus.OK)}
     * 或 {@code ResponseEntity.ok(...)} 时，本门 5/5 <b>全绿</b>，而两枚行为门各自变红
     * （kb {@code KbProfileStatusFidelityTest} 2 红 403→200、gateway
     * {@code AlertCloseEndpointContractTest#repositoryFailureIs503} 1 红 503→200）。
     * ⇒ 本门的职责是「裸回面只减不增」，「折叠保真」由行为门守；关账 D-B4-59 必须以两门
     * 同时在链为前提，缺行为门即半只眼。
     */
    @Test
    @DisplayName("本批收口的三枚持有者：判据 A 零命中且确有状态行承载手段在链上")
    void convertedOwnersCarryTheirStatusLine() {
        for (String owner : CONVERTED_OWNERS) {
            String code = codeOf(owner);
            assertFalse(BARE_DENIAL_LEG.matcher(code).find(),
                    owner + " 又出现裸回的安全拒绝／不可用出口＝D-B4-59＋D-B4-68 在本文件复现");
            assertTrue(code.contains("ResponseEntity.status("),
                    owner + " 登记为已收口持有者，源码里却没有任何 ResponseEntity.status(...) 腿"
                            + "（只靠基线归零＝摘掉修法仍全绿的假绿面）");
        }
        String alert = codeOf(CONVERTED_OWNERS.get(0));
        assertTrue(alert.contains("RlsExecutionContract.SEC_UNAVAILABLE_HTTP"),
                "RuntimeAlertV1Controller 的 503 未取不可用态单源常量（状态行与信封两处各写一遍＝会漂移）");
        String profile = codeOf(CONVERTED_OWNERS.get(1));
        assertTrue(profile.contains("KbErrorCodes.PERMISSION_DENIED.httpStatus()")
                        && profile.contains("KbErrorCodes.TASK_BASE_UNAVAILABLE.httpStatus()"),
                "KbProfileController 的 403/503 状态行未取本域码表单源（改码不改行＝保真会漂移）");
        String nav = codeOf(CONVERTED_OWNERS.get(2));
        assertTrue(nav.contains("DataBridgeException.resolveHttpStatus(body.getCode())"),
                "NavTaxonController 的状态行不再从信封 code 单源推出＝本类 13 枚 403 腿的"
                        + "「改码即改行」不变式被摘掉，退回两处各写一遍的漂移形态");
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

    // ── 验收 4b：承载态拆分能变红（#142/B4-3e-34 收口 D-B4-69） ────────────────

    @Test
    @DisplayName("反向取证：@ResponseStatus 注解承载腿计入豁免面，摘掉注解必须落回违例面")
    void carrierSplitTurnsRedWhenAnnotationIsRemoved() {
        String annotatedHandler = String.join("\n", List.of(
                "class SyntheticHandlers {",
                "    @ExceptionHandler(AccessDeniedException.class)",
                "    @ResponseStatus(HttpStatus.FORBIDDEN)",
                "    public ApiResponse<Map<String,Object>> handleDenied(AccessDeniedException e) {",
                "        return ApiResponse.forbidden(\"权限不足\");",
                "    }",
                "}"));
        int[] withAnnotation = countDenialLegsByCarrier(annotatedHandler);
        assertEquals(0, withAnnotation[0],
                "同一方法带 @ExceptionHandler＋@ResponseStatus 时状态行由注解真实承载，"
                        + "仍计入违例面＝D-B4-69 的「已保真与未保真长得一样」，收口进度不可读");
        assertEquals(1, withAnnotation[1], "注解承载腿必须计入豁免面：不记就等于不判");

        String stripped = annotatedHandler.replace(
                "    @ResponseStatus(HttpStatus.FORBIDDEN)\n", "");
        int[] withoutAnnotation = countDenialLegsByCarrier(stripped);
        assertEquals(1, withoutAnnotation[0],
                "拆掉 @ResponseStatus 后该腿状态行恒 200＝真裸回。判据不随之回弹＝"
                        + "豁免面成了一张只进不出的免检票（本批门会恒绿）");
        assertEquals(0, withoutAnnotation[1], "注解已不在，豁免面不得继续记这一腿");

        assertEquals(countHits(BARE_DENIAL_LEG, annotatedHandler),
                withAnnotation[0] + withAnnotation[1],
                "拆分只改变记账面、不得改变看见的腿数（旧口径总数与新口径两面之和必须相等）");

        String plainMapping = String.join("\n", List.of(
                "class SyntheticPlainMapping {",
                "    @GetMapping(\"/deny\")",
                "    @ResponseStatus(HttpStatus.FORBIDDEN)",
                "    public ApiResponse<Map<String,Object>> deny() {",
                "        return ApiResponse.forbidden(\"权限不足\");",
                "    }",
                "}"));
        int[] plain = countDenialLegsByCarrier(plainMapping);
        assertEquals(1, plain[0],
                "普通映射方法单挂 @ResponseStatus 对成功腿同样生效＝会造出「成功腿配 403 行」"
                        + "的新缺陷，单注解不构成豁免；此处放过＝为降基数开后门");
        assertEquals(0, plain[1], "未与 @ExceptionHandler 同法的注解不得进豁免面");

        String orphanLeg = "        return ApiResponse.forbidden(\"权限不足\");";
        int[] orphan = countDenialLegsByCarrier(orphanLeg);
        assertEquals(1, orphan[0],
                "解析器定不到属主方法时必须向红兜底（多报可解释，漏报＝盲区重新长回来）");
        assertEquals(0, orphan[1], "定不到属主的腿不得记为注解承载");
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

    /**
     * 判据 A 的承载态拆分（#142/B4-3e-34 收口 D-B4-69）。
     *
     * <p>返回 {@code [真裸回, 注解承载]}：前者进违例面（基线只减不增），后者进
     * {@link #ANNOTATION_CARRIED_BASELINE}（同样只减不增）。两数之和必须等于旧口径的
     * 总命中数——拆分只改变「哪一面记账」，不改变「看见多少腿」，所以本方法不得吃掉任何一腿：
     * 逐腿判定后一律归入两面之一。</p>
     */
    private static int[] countDenialLegsByCarrier(String code) {
        String[] lines = code.split("\\R", -1);
        int bare = 0, annotated = 0;
        for (int i = 0; i < lines.length; i++) {
            Matcher m = BARE_DENIAL_LEG.matcher(lines[i]);
            while (m.find()) {
                if (enclosingHandlerCarriesResponseStatus(lines, i)) {
                    annotated++;
                } else {
                    bare++;
                }
            }
        }
        return new int[] {bare, annotated};
    }

    /**
     * 命中行的上位方法是否「同时」声明 {@code @ExceptionHandler} 与 {@code @ResponseStatus}。
     *
     * <p>解析方向必须是<b>从签名向上</b>收集连续的注解行：向下扫会把方法体内的其它语句
     * 当成注解块（实测恒判 NONE＝把 5 处真实注解承载读成裸回，反向假绿）。
     * 签名定位失败或注解块不邻接（中间隔了空行以外的内容）时返回 {@code false}，
     * 让解析器的失效方向是「多报违例」而不是「漏报违例」。</p>
     */
    private static boolean enclosingHandlerCarriesResponseStatus(String[] lines, int hitLine) {
        int sig = -1;
        for (int i = hitLine; i >= 0 && sig < 0; i--) {
            if (HANDLER_METHOD_SIGNATURE.matcher(lines[i]).find()) {
                sig = i;
            }
        }
        if (sig < 0) {
            return false;
        }
        boolean responseStatus = false;
        boolean exceptionHandler = false;
        for (int i = sig - 1; i >= 0; i--) {
            String t = lines[i].trim();
            if (t.isEmpty()) {
                continue;
            }
            if (t.startsWith("@")) {
                responseStatus |= t.startsWith("@ResponseStatus");
                exceptionHandler |= t.startsWith("@ExceptionHandler");
                continue;
            }
            break;
        }
        return responseStatus && exceptionHandler;
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
