package com.chinacreator.gzcm.common.exception;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import com.chinacreator.gzcm.common.testscan.SourceCommentStripper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * #142/B4-3e-44（差距 <b>D-B4-74</b>）— 「状态行显式 200 ＋ 信封失败码」折叠面棘轮门。
 *
 * <p><b>缺陷形态（为什么前面四枚状态行门全都没看见）</b>：{@code PMO45DataSourceController} 的
 * 5 枚失败腿写作 {@code ResponseEntity.ok(ApiResponse.error(404|400, …))}——状态行<b>是被显式写出来的</b>
 * （200），不是漏写的裸回。于是：{@code SecurityDenialHttpStatusRatchetTest} 判据 A／B 只锚
 * {@code return ApiResponse.…} 的<b>裸回</b>形态（该文件不裸回）、
 * {@code BusinessExceptionStatusParityRatchetTest} 只锚 {@code new BusinessException(}（该文件不抛）、
 * {@code RootCarrierHttpStatusSingleSourceRatchetTest} 只锚 {@code @ExceptionHandler} 根型腿（该文件是端点）
 * ——三枚门的判据各管一段，这段「写了状态行、写错值」的面落在三段缝隙里。该缺陷族在
 * #142/B4-3e-32 的取证叙述里已被点名过（MUTANT-M1／M2 折叠下棘轮门全绿），但当时只登记为
 * 「两门互补」的论据，<b>没有入册成编号缺陷、也没有判据</b>；本批补齐。</p>
 *
 * <p><b>本门钉四件事</b>：</p>
 * <ol>
 *   <li><b>违例面归零并双向钉死</b>：ok-折叠腿全后端 main 面 = {@link #FOLDED_BASELINE}（本批实测
 *       5→0，逐文件），新增即红；基线不为 0 而实扫为 0 同样红（防「基线冒充已收口」）。</li>
 *   <li><b>合计对账</b>：每一枚 {@code ApiResponse.error(} 出现点必须落入五分类之一，
 *       五类之和 == 出现点总数（D-B4-62「违例条数盲区」同族纪律——判据不得只认自己认识的那一类）。
 *       三类非违例面另设<b>只减不增上限</b>：上限＝实测值而非「顺手归零」，让「有意不扩面」成为
 *       可被读数推翻的声明。</li>
 *   <li><b>承载见证（防删腿假绿）</b>：违例面归零必须与「属主侧确实写上了承载手段」同时成立——
 *       端点里 {@code failure(ApiResponse.error(} 的 helper 单源腿、{@code catch (DataBridgeException}
 *       放行腿、服务侧 {@code DataBridgeException(404}/{@code (503} 携带抛点逐一具名；
 *       只删信封不写状态行也红。</li>
 *   <li><b>判据自证（合成夹具）</b>：不碰仓库文件，直接喂三类形态样本给分类器，断言折叠面数得出
 *       多行／泛型见证／{@code markDeprecated} 包裹各 1 枚，且状态行承载面不误报为折叠。
 *       <b>本判据不是装饰</b>：#142/B4-3e-44 首探即由「裸回腿必须归入裸回面」一条变红，抓出
 *       {@link #statementSlice} 片段止于锚点起点、不含 {@code ApiResponse} 一词，导致
 *       {@link #BARE_RETURN} 恒不命中、99 枚裸回腿整面被误计成「纯信封构造」的判据盲区
 *       （探测链见 {@link #STATUS_CARRIER_CEILING} 注释）。
 *       真仓变异另在批内实跑取证（见台账 v1.0.126 行）。</li>
 * </ol>
 *
 * <p><b>已知边界（如实登记，不得当已收口引用）</b>：分类只看「本枚出现点所属语句片段」，
 * 赋值形态（{@code ApiResponse<T> body = ApiResponse.error(…}）与跨语句的 helper 返回值都只能落到
 * {@link #carriedByOwnerHelper}／{@link #envelopeBuiltForLaterReturn} 两面，其<b>状态行真伪由属主行为门
 * 承担</b>（{@code engine/data-engine/data-engine-impl} 的 {@code DataSourcePreviewStatusFidelityTest}）——
 * 与本仓「静态棘轮门＋属主行为门同时在链」的关账口径一致（承 #142/B4-3e-32／3e-37）。</p>
 */
@Timeout(120)
class SuccessStatusFailureEnvelopeRatchetTest {

    /**
     * 信封失败码出现点锚：含泛型类型见证形态 {@code ApiResponse.<T>error(}（D-B4-68 同口径——
     * 见证在点号<b>之后</b>，判据若把它当不合法语法跳过，就会对这一形态失明）。
     */
    private static final Pattern ENVELOPE_ERROR = Pattern.compile(
            "ApiResponse\\s*\\.\\s*(?:<[^();]*>\\s*)?error\\s*\\(");

    /** 折叠面：同一语句片段内、出现在信封之前的 {@code ResponseEntity.ok(} 或静态导入的 {@code ok(}。 */
    private static final Pattern OK_FOLD = Pattern.compile(
            "(?:ResponseEntity\\s*\\.\\s*)?\\bok\\s*\\(");

    /** 状态行承载面：{@code ResponseEntity.status(} 或链式 {@code .status(}。 */
    private static final Pattern STATUS_CARRIER = Pattern.compile(
            "(?:ResponseEntity\\s*\\.\\s*)?\\.?\\bstatus\\s*\\(");

    /** 裸回面：片段起点即 {@code return ApiResponse.…}（该面由 D-B4-59 族棘轮门另行管账）。 */
    private static final Pattern BARE_RETURN = Pattern.compile("^\\s*return\\s+ApiResponse\\b");

    /** 属主 helper 承载面：{@code failure(} 单源推导（本批 {@code PMO45DataSourceController} 修法形态）。 */
    private static final Pattern OWNER_HELPER = Pattern.compile("\\bfailure\\s*\\(");

    /** 判据 5 锚：成功载荷里塞 {@code error} 键（本批只在属主服务文件钉零，见该方法注释的口径边界）。 */
    private static final Pattern RESULT_ERROR_KEY = Pattern.compile(
            "result\\s*\\.\\s*put\\s*\\(\\s*\"error\"\\s*,");

    /** 扫描面阈值守卫：与同族门一致（读不到源码不得当成无违例）。 */
    private static final int MIN_SCANNED_MAIN_FILES = 2000;

    /** 语句片段回溯上限：超过即认为该出现点属另一语句（防跨语句误判）。 */
    private static final int SLICE_LIMIT = 400;

    /**
     * 违例面基线（#142/B4-3e-44 实测 <b>5 → 0</b> 后归零）：文件 → ok-折叠腿数。
     * 本批唯一持有者 {@code PMO45DataSourceController} 的 5 枚（行 79／89／107／133／138）已改写上状态行。
     */
    private static final Map<String, Integer> FOLDED_BASELINE = Map.of();

    /**
     * 非违例面只减不增上限。读数由本门禁会话内「上限置 0 探测」实跑产出，禁算式推出：
     *
     * <ul>
     *   <li>第 2 次探测（{@code _win_tasks/logs/b43e44-probe2.log}）＝总数 169／折叠 0／状态行 27／
     *       <b>裸回 0</b>／helper 8／纯信封 134 —— 该「裸回 0」是<b>判据失明</b>产物：
     *       {@link #statementSlice} 的片段止于锚点起点，而锚点起点就是 {@code ApiResponse} 一词，
     *       片段里没有 {@code ApiResponse}，{@link #BARE_RETURN} 恒不命中。由判据 4 合成夹具
     *       「裸回腿必须归入裸回面」变红暴露（与 D-B4-68「类型见证腿看不见」同族）。</li>
     *   <li>第 3 次探测（修好片段规则后，{@code b43e44-probe3.log}）＝总数 169／折叠 0／状态行 27／
     *       <b>裸回 99</b>／helper 8／纯信封 35。五类之和 27+99+8+35 = 169 = 总数，
     *       对账（判据 2）成立。四枚上限即按本次实测值钉住。</li>
     *   <li>#142/B4-3e-46 重设（新增「按原始连接配置预览」端点 <b>3</b> 枚信封出现点后由本门实跑变红
     *       产出，{@code t-3e46-full.log}）＝总数 <b>172</b>／折叠 0／状态行承载 <b>28</b>／
     *       裸回 <b>99</b>（未漂移，新端点零裸回）／属主 helper <b>10</b>／纯信封 <b>35</b>（未漂移）。
     *       三类归位与本仓惯用法一致：{@code ResponseEntity.status(..).body(ApiResponse.error(..))}
     *       记状态行承载 ＋1，两枚 {@code failure(ApiResponse.error(..))} 记属主 helper ＋2；
     *       28+99+10+35 = 172 = 总数，对账仍成立。只重设被突破的两枚上限，其余两面按实测保持。</li>
     *   <li>#142/B4-3e-58 重设（D-B4-88 收口批，common-api 全模块红跑实测，
     *       {@code /tmp/full-common-api.log}）＝总数 <b>173</b>／折叠 0／状态行承载 <b>29</b>／
     *       裸回 <b>99</b>（未漂移）／属主 helper <b>10</b>（未漂移）／纯信封 <b>35</b>（未漂移）。
     *       唯一漂移源＝{@code AiErrorAdvice#onBridge} 的
     *       {@code ResponseEntity.status(status).body(ApiResponse.error(status, …))} 新状态行承载腿
     *       （DataBridge 根型异常 advice 补状态行承载腿，收口 D-B4-88 的 503 载体由此腿出状态行），计承载 ＋1；
     *       29+99+10+35 = 173 = 总数，对账成立。只重设被突破的 {@link #STATUS_CARRIER_CEILING}，
     *       其余三面按实测保持。</li>
     * </ul>
     *
     * <p>裸回面 99 枚<b>本门有意不判其对错</b>（归 {@code D-B4-59} 族棘轮门管账），
     * 此处只做「只减不增」计数：该族每收口一枚，本上限随批下调。</p>
     */
    private static final int STATUS_CARRIER_CEILING = 29;
    private static final int BARE_RETURN_CEILING = 99;
    private static final int OWNER_HELPER_CEILING = 10;
    private static final int ENVELOPE_ONLY_CEILING = 35;

    /** 本批属主侧承载见证锚（文件 → 必须出现的形态片段）。缺任一即红＝「删腿」不算修好。 */
    private static final Map<String, List<String>> OWNER_SIDE_CARRIERS = Map.of(
            "ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/"
                    + "engine/data/controller/PMO45DataSourceController.java",
            List.of("private static <T> ResponseEntity<ApiResponse<T>> failure(ApiResponse<T> body)",
                    "DataBridgeException.resolveHttpStatus(body.getCode())",
                    "catch (DataBridgeException e) {",
                    "int status = DataBridgeException.resolveHttpStatus(e.getHttpStatus())",
                    "ResponseEntity.status(status)"),
            "ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/"
                    + "engine/data/service/DataSourceServiceImpl.java",
            List.of("new DataBridgeException(404, 404,",
                    "new DataBridgeException(503, 503,",
                    "DataDomainErrorCode.ECOS_DATA_032",
                    "DataDomainErrorCode.ECOS_DATA_051",
                    // #142/B4-3e-46：按原始配置预览属主腿＋内层列面显式降级键（删键即红）
                    "public Map<String, Object> previewSchemaByConfig(",
                    "result.put(\"fieldsDegraded\", true)",
                    "result.put(\"fieldsReason\""),
            "ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/"
                    + "engine/data/controller/DataSourceController.java",
            List.of("@PostMapping(\"/preview-schema\")",
                    "previewSchema(@RequestBody DataSourceDTO dto)",
                    "return ResponseEntity.status(status).body(ApiResponse.error(status,",
                    "return failure(ApiResponse.error(400, e.getMessage()));"));

    // ── 判据 1：违例面 = 基线逐文件同数 ──────────────────────────────────────

    @Test
    @DisplayName("ok 折叠失败码面 = 基线逐文件同数：新增即红，基线陈旧也即红")
    void foldedFailureEnvelopeMatchesTheRegisteredBaselineExactly() {
        List<Scanned> scanned = backendMainSources();
        assertTrue(scanned.size() > MIN_SCANNED_MAIN_FILES,
                "主源码扫描根解析到的文件数异常（" + scanned.size() + "）——「读不到」不得当成「无违例」");

        Map<String, Integer> actual = new TreeMap<>();
        List<String> details = new ArrayList<>();
        for (Scanned s : scanned) {
            List<String> folded = foldedLegs(s);
            if (!folded.isEmpty()) {
                actual.put(s.relPath(), folded.size());
                details.addAll(folded);
            }
        }

        Map<String, Integer> expected = new TreeMap<>(FOLDED_BASELINE);
        List<String> diff = new ArrayList<>();
        for (Map.Entry<String, Integer> e : expected.entrySet()) {
            int got = actual.getOrDefault(e.getKey(), 0);
            if (got > e.getValue()) {
                diff.add("新增/上升: " + e.getKey() + " 基线 " + e.getValue() + " → 实扫 " + got);
            } else if (got < e.getValue()) {
                diff.add("基线陈旧（该文件已收窄，须同批下调基线并登记批次）: "
                        + e.getKey() + " 基线 " + e.getValue() + " → 实扫 " + got);
            }
        }
        for (Map.Entry<String, Integer> e : actual.entrySet()) {
            if (!expected.containsKey(e.getKey())) {
                diff.add("未登记的新持有者: " + e.getKey() + " 实扫 " + e.getValue());
            }
        }
        assertTrue(diff.isEmpty(),
                "「HTTP 200 ＋ 信封失败码」折叠面失真（实扫合计 " + actual.values().stream().mapToInt(i -> i).sum()
                        + " 处／基线合计 " + expected.values().stream().mapToInt(i -> i).sum() + " 处）:\n"
                        + String.join("\n", diff) + "\n" + String.join("\n", details));
    }

    // ── 判据 2：五分类穷尽对账（防「判据只认自己认识的形态」）────────────────

    @Test
    @DisplayName("每枚 ApiResponse.error 出现点必归入五类之一且各类读数受上限约束")
    void everyEnvelopeOccurrenceIsClassifiedNotSilentlyDropped() {
        int folded = 0;
        int statusCarrier = 0;
        int bareReturn = 0;
        int ownerHelper = 0;
        int envelopeOnly = 0;
        int total = 0;

        for (Scanned s : backendMainSources()) {
            Categories c = classify(s.code());
            total += c.total();
            folded += c.folded();
            statusCarrier += c.statusCarrier();
            bareReturn += c.bareReturn();
            ownerHelper += c.ownerHelper();
            envelopeOnly += c.envelopeOnly();
        }

        assertEquals(total, folded + statusCarrier + bareReturn + ownerHelper + envelopeOnly,
                "分类之和与出现点总数不符＝存在被判据吃掉的腿（D-B4-62 违例条数盲区同族）。"
                        + " 实测: 总数 " + total + "／折叠 " + folded + "／状态行 " + statusCarrier
                        + "／裸回 " + bareReturn + "／helper " + ownerHelper + "／纯信封 " + envelopeOnly);

        assertEquals(0, folded, "折叠面（违例面）读数必须为 0，非 0 时判据 1 已红，此处兜住口径自相矛盾");
        // 四枚上限<b>合成一条</b>断言：一次红色读数即产出全部五面实测值（探针取证只跑一轮，
        // 逐条断言会在第一条红处停住、逼着反复跑门禁才能凑齐数字）。
        assertTrue(statusCarrier <= STATUS_CARRIER_CEILING
                        && bareReturn <= BARE_RETURN_CEILING
                        && ownerHelper <= OWNER_HELPER_CEILING
                        && envelopeOnly <= ENVELOPE_ONLY_CEILING,
                "非违例面只减不增上限被突破（本行红即上限待按实测重设，禁算式推出）。实测: "
                        + "总数 " + total + "／折叠 " + folded
                        + "／状态行承载 " + statusCarrier + "（上限 " + STATUS_CARRIER_CEILING + "）"
                        + "／裸回 " + bareReturn + "（上限 " + BARE_RETURN_CEILING + "，归 D-B4-59 族门管账）"
                        + "／属主 helper 承载 " + ownerHelper + "（上限 " + OWNER_HELPER_CEILING + "）"
                        + "／纯信封构造 " + envelopeOnly + "（上限 " + ENVELOPE_ONLY_CEILING + "）");
    }

    // ── 判据 3：属主侧承载见证（防「删腿冒充收口」）──────────────────────────

    @Test
    @DisplayName("折叠腿是被改写成承载的，不是被删掉的：属主侧载体逐一具名在位")
    void ownerSideCarriersAreInPlace() {
        List<Scanned> scanned = backendMainSources();
        for (Map.Entry<String, List<String>> e : OWNER_SIDE_CARRIERS.entrySet()) {
            String code = scanned.stream()
                    .filter(s -> s.relPath().equals(e.getKey()))
                    .map(Scanned::code)
                    .findFirst()
                    .orElseGet(() -> fail("属主侧承载见证的目标文件不在扫描面内: " + e.getKey()));
            for (String literal : e.getValue()) {
                assertTrue(code.contains(literal),
                        "属主侧载体缺失（违例面归零必须是「改写成承载」而非「删腿」）: "
                                + e.getKey() + " 缺片段 " + literal);
            }
        }
    }

    // ── 判据 4：判据自证（合成夹具，不碰仓库文件）────────────────────────────

    @Test
    @DisplayName("分类判据对多行/泛型见证/装饰包裹三种折叠形态都数得出（能变红自证）")
    void syntheticFixturesProveTheFoldedFaceIsVisible() {
        String multiLineFolded = """
                public class A {
                    ResponseEntity<ApiResponse<Map<String, Object>>> m() {
                        return markDeprecated(ResponseEntity.ok(
                                ApiResponse.error(404, "不存在")));
                    }
                }
                """;
        String genericWitnessFolded = """
                public class B {
                    ResponseEntity<ApiResponse<String>> m() {
                        return ResponseEntity.ok(ApiResponse.<String>error(400, "空"));
                    }
                }
                """;
        String helperWrappedFolded = """
                public class C {
                    ResponseEntity<ApiResponse<String>> m() {
                        return markDeprecated(ok(ApiResponse.error(500, "炸")));
                    }
                }
                """;
        String statusCarried = """
                public class D {
                    ResponseEntity<ApiResponse<String>> m() {
                        return ResponseEntity.status(404)
                                .body(ApiResponse.error(404, "ECOS-DATA-032 — 不存在"));
                    }
                }
                """;
        String bareReturnLeg = """
                public class E {
                    ApiResponse<String> m() {
                        return ApiResponse.error(404, "不存在");
                    }
                }
                """;
        String ownerHelperLeg = """
                public class F {
                    ResponseEntity<ApiResponse<String>> m() {
                        return markDeprecated(failure(ApiResponse.error(400, "空")));
                    }
                }
                """;

        String bareReturnWithWitness = """
                public class G {
                    ApiResponse<String> m() {
                        return ApiResponse.<String>error(404, "不存在");
                    }
                }
                """;

        assertEquals(1, classify(multiLineFolded).folded(), "多行折叠形态必须数得出");
        assertEquals(1, classify(genericWitnessFolded).folded(), "泛型类型见证折叠形态必须数得出");
        assertEquals(1, classify(helperWrappedFolded).folded(), "ok(...) 装饰包裹折叠形态必须数得出");
        assertEquals(0, classify(statusCarried).folded(), "状态行承载腿不得被误判成折叠（否则上限面失真）");
        assertEquals(1, classify(statusCarried).statusCarrier(), "状态行承载腿必须归入承载面");
        assertEquals(1, classify(bareReturnLeg).total(), "裸回锚点本身必须数得出出现点（数不出＝锚失效）");
        assertEquals(1, classify(bareReturnLeg).bareReturn(), "裸回腿必须归入裸回面");
        assertEquals(1, classify(bareReturnWithWitness).bareReturn(),
                "裸回＋类型见证腿必须归入裸回面（本判据首探曾因片段止于锚点起点、不含 ApiResponse 一词而整面失明）");
        assertEquals(1, classify(ownerHelperLeg).ownerHelper(), "属主 helper 腿必须归入 helper 面");
        // 出现点总数只认锚：本夹具里 {@code ApiResponse.error(} 实存 1 枚（泛型声明
        // {@code ResponseEntity<ApiResponse<Map<String, Object>>>} 不是出现点）。
        // 本行原写 {@code 3} 系算式推出、未实测，第 3 次探测即红暴露，按实测订正为 1。
        assertEquals(1, classify(multiLineFolded).total(), "出现点总数只认锚，不认类别");
    }

    // ── 判据 5：属主服务侧不得再以「成功 Map ＋ error 键」表达不可用 ──────────

    @Test
    @DisplayName("DataSourceServiceImpl 的预览通道不再用 result.put(\"error\") 伪装可用（D-B4-75 锚点钉 0）")
    void ownerServiceNoLongerFailsInsideASuccessPayload() {
        String code = backendMainSources().stream()
                .filter(s -> s.relPath().endsWith("engine/data/service/DataSourceServiceImpl.java"))
                .findFirst()
                .map(Scanned::code)
                .orElseGet(() -> fail("属主服务不在扫描面内，锚点不得默认通过"));
        int hits = 0;
        List<Integer> lines = new ArrayList<>();
        Matcher m = RESULT_ERROR_KEY.matcher(code);
        while (m.find()) {
            hits++;
            lines.add(lineNumberOf(code, m.start()));
        }
        assertEquals(0, hits,
                "预览通道把失败塞进成功载荷的形态回退（行 " + lines + "）——"
                        + "客户端读到空集合与读到不可用将再次同形（D-B4-75）");

        // 口径边界如实登记：全仓 main 面仍有约 50 枚 put("error", …)，绝大多数是**作业状态／修复日志
        // 的持久化载荷键**（DqAutoRepairServiceImpl、KgSyncServiceImpl 等），不是 HTTP 成功信封，
        // 本门有意不判其对错、也不做全仓上限（会把两类问题稀释成同一个判定）。仅对本批属主文件钉零。
    }

    // ── 扫描与分类 ────────────────────────────────────────────────────────────

    /** 五分类读数：折叠／状态行承载／裸回／属主 helper／纯信封构造。 */
    private record Categories(int total, int folded, int statusCarrier,
                             int bareReturn, int ownerHelper, int envelopeOnly) {
    }

    /**
     * 对一份剥注释源码做逐出现点分类。
     *
     * <p>判定顺序＝优先级：折叠 &gt; 状态行 &gt; 裸回 &gt; helper &gt; 纯信封。每枚出现点必然归入
     * 且只归入一类（{@link #everyEnvelopeOccurrenceIsClassifiedNotSilentlyDropped} 据此做之和==总数对账）。</p>
     */
    private static Categories classify(String code) {
        int total = 0;
        int folded = 0;
        int statusCarrier = 0;
        int bareReturn = 0;
        int ownerHelper = 0;
        int envelopeOnly = 0;

        Matcher m = ENVELOPE_ERROR.matcher(code);
        while (m.find()) {
            total++;
            String slice = statementSlice(code, m.start(), m.group());
            if (OK_FOLD.matcher(slice).find()) {
                folded++;
            } else if (STATUS_CARRIER.matcher(slice).find()) {
                statusCarrier++;
            } else if (BARE_RETURN.matcher(slice).find()) {
                bareReturn++;
            } else if (OWNER_HELPER.matcher(slice).find()) {
                ownerHelper++;
            } else {
                envelopeOnly++;
            }
        }
        return new Categories(total, folded, statusCarrier, bareReturn, ownerHelper, envelopeOnly);
    }

    /**
     * 出现点所属语句片段：从最近的 {@code ;}／{@code &#123;}／{@code &#125;}／{@code =} 截断，
     * 并<b>把出现点自身的匹配文本补回片段末尾</b>。
     *
     * <p>补回是必需的：{@link #ENVELOPE_ERROR} 的匹配起点就是 {@code ApiResponse} 这个词，
     * 若片段止于 {@code m.start()}，片段里便不含 {@code ApiResponse}，
     * {@link #BARE_RETURN}（判据要认 {@code return ApiResponse.…}）将<b>恒不命中</b>——
     * 裸回面会被整面误计成「纯信封构造」（#142/B4-3e-44 首探实测「裸回 0／纯信封 134」即此盲区，
     * 由本门合成夹具的「能变红」自证暴露）。与 D-B4-68「类型见证腿看不见」同族：<b>锚点起点与判据
     * 所需的上下文不一致</b>＝判据对自己认识的那一类失明。</p>
     */
    private static String statementSlice(String code, int occurrenceStart, String token) {
        int from = 0;
        String boundaryHints = ";{}=";
        for (int i = occurrenceStart - 1; i >= 0; i--) {
            if (code.charAt(i) == '\n') {
                continue;
            }
            if (boundaryHints.indexOf(code.charAt(i)) >= 0) {
                from = i + 1;
                break;
            }
        }
        int start = Math.max(from, occurrenceStart - SLICE_LIMIT);
        return code.substring(start, occurrenceStart) + token;
    }

    /** 折叠腿逐条文本（行号＋截行宽，绝不截条数——D-B4-62 计数口径）。 */
    private static List<String> foldedLegs(Scanned scanned) {
        List<String> out = new ArrayList<>();
        Matcher m = ENVELOPE_ERROR.matcher(scanned.code());
        while (m.find()) {
            if (OK_FOLD.matcher(statementSlice(scanned.code(), m.start(), m.group())).find()) {
                int line = lineNumberOf(scanned.code(), m.start());
                String text = scanned.code().split("\\R", -1)[line - 1].trim();
                out.add(scanned.relPath() + ":" + line + " → "
                        + (text.length() > 160 ? text.substring(0, 160) + "…" : text));
            }
        }
        return out;
    }

    private static int lineNumberOf(String code, int index) {
        int line = 1;
        for (int i = 0; i < index && i < code.length(); i++) {
            if (code.charAt(i) == '\n') {
                line++;
            }
        }
        return line;
    }

    private record Scanned(String relPath, String code) {
    }

    /** 扫描缓存：单遍读盘实测约 50 s，四枚判据共用一份结果（防把 {@code @Timeout} 耗在重复 IO 上）。 */
    private static volatile List<Scanned> SCAN_CACHE;

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
        List<Scanned> cached = SCAN_CACHE;
        if (cached != null) {
            return cached;
        }
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
                out.add(new Scanned(rel, SourceCommentStripper.strip(
                        new String(Files.readAllBytes(f), StandardCharsets.UTF_8))));
            }
        } catch (IOException e) {
            throw new AssertionError("扫描主源码失败: " + e.getMessage(), e);
        }
        SCAN_CACHE = List.copyOf(out);
        return SCAN_CACHE;
    }
}
