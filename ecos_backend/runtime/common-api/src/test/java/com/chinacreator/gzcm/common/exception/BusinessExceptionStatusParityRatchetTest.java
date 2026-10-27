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
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import com.chinacreator.gzcm.common.testscan.SourceCommentStripper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * #142/B4-3e-28（差距 D-B4-56）— {@code BusinessException} 携带值与 HTTP 状态行<b>同号</b>门。
 *
 * <p>收口前两重折叠叠在一起：①{@code BusinessException} 只有 {@code (message)} 与
 * {@code (errorCode, message)} 两枚构造，两者都把 {@code httpStatus} 写死 400；
 * ②gateway {@code handleBusiness} 返回裸 {@code ApiResponse}，状态行由
 * {@code @ResponseStatus(BAD_REQUEST)} 恒定决定。后果是 main 面 23 处
 * {@code new BusinessException(404|409|500|501|503, …)} 的携带值只进了 body {@code code}，
 * HTTP 面恒 400 —— §3.2.7「返回值 code 与状态行同号」与 §4.7.2「不可用 ≠ 无权限」
 * 在本异常型面上整条失效（{@code ECOS-SEC}/{@code COG-} 系列 503 与策略拒绝 403 都在此折叠）。</p>
 *
 * <p>修法按「API 只增不改」：新增 {@code (httpStatus, errorCode, message)} 三参构造，
 * 既有两枚签名与默认 400 语义逐字不变；23 处非 400 携带点迁到三参形态（body code 原样保留）；
 * {@code handleBusiness} 改回 {@code ResponseEntity} 走 {@code resolveHttpStatus} 单源。</p>
 *
 * <p><b>本门钉五件事</b>：</p>
 * <ol>
 *   <li><b>零容忍 A</b>：双参形态里首参数值非 400 的处数 = 0（收口前实测 23）——
 *       这条腿就是缺陷本身，新增即红。</li>
 *   <li><b>零容忍 B</b>：三参形态必须 {@code httpStatus == errorCode} 且落在 400..599
 *       （异号＝把缺陷换个面值重犯；越界＝拿容器状态行写假语义）。</li>
 *   <li><b>双向基线</b>：双参 400 形态 101 处／18 文件、三参同号形态 23 处／14 文件、
 *       首参非常量数值形态 1 处，按文件分枚 {@code equals}——增＝新违例，减＝基线陈旧同样红。</li>
 *   <li><b>合计对账</b>：三类形态之和必须等于 {@code new BusinessException(} 全站点数，
 *       防「判据只覆盖自己认识的四态」造成的口径外盲区（与 D-B4-62 违例条数盲区同族）。</li>
 *   <li><b>构造面只增不改</b>：反射钉死三枚构造的签名与默认 400 语义，第四枚出现即红。</li>
 * </ol>
 *
 * <p>取证纪律：判据全部先经 {@link SourceCommentStripper} 剥注释（D-B4-40/62 同口径）；
 * 数值首参后的「第二参是否数值」用<b>独占量词</b> {@code \s*+} 判定 —— 普通 {@code \s*} 会被回溯
 * 绕过否头断言（{@code (500,} 后回缩到零宽 ⇒  lookahead 读到空格 ⇒ 三参形态被误计成双参），
 * 该缺陷在 #142/B4-3e-27 已实测过一次，本门按同一教训写死并配夹具反证。</p>
 */
@Timeout(120)
class BusinessExceptionStatusParityRatchetTest {

    /** 双参形态：首参数值、逗号后经全部空白仍非数值（独占量词，防回溯绕过）。 */
    private static final Pattern TWO_ARG_NUMERIC = Pattern.compile(
            "new\\s+BusinessException\\s*\\(\\s*+(-?\\d+)\\s*,\\s*+(?!\\d)(?!\\))");

    /** 三参形态：前两参皆数值，其后跟逗号或闭括号。 */
    private static final Pattern THREE_ARG_NUMERIC = Pattern.compile(
            "new\\s+BusinessException\\s*\\(\\s*+(-?\\d+)\\s*,\\s*+(\\d+)\\s*[,)]");

    /** 首参是字符串字面量 ⇒ 单参 message 形态（合法默认 400）。 */
    private static final Pattern TEXT_FIRST = Pattern.compile(
            "new\\s+BusinessException\\s*\\(\\s*+\"");

    /** 首参是标识符 ⇒ 非数值字面量形态（本批实测 1 处 String.format，属单参 message 的派生）。 */
    private static final Pattern IDENT_FIRST = Pattern.compile(
            "new\\s+BusinessException\\s*\\(\\s*+([A-Za-z_][\\w.]*)");

    /** 全站点锚：用于「判据之和 = 全站点」合计对账。 */
    private static final Pattern ANY_SITE = Pattern.compile(
            "new\\s+BusinessException\\s*\\(");

    private static final int LEGITIMATE_DEFAULT_STATUS = 400;
    private static final int MIN_LEGAL_CARRIED_STATUS = 400;
    private static final int MAX_LEGAL_CARRIED_STATUS = 599;

    /** 扫描面阈值守卫：2026-10-22 实测 ecos_backend main 面 .java 文件 2339 枚。 */
    private static final int MIN_SCANNED_MAIN_FILES = 2000;

    /** 基线：双参 400 形态逐文件处数（合计 101 处／18 文件；100→101 系 #146l-前置补账 ModelRegistryService 4→5）。 */
    private static final Map<String, Integer> BASELINE_TWO_ARG_400 = baseline(
            "ecos_backend/engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/chinacreator/gzcm/engine/cognitive2/service/ForecastServiceImpl.java", 4,
            "ecos_backend/engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/chinacreator/gzcm/engine/cognitive2/service/CognitiveEvidenceService.java", 7,
            "ecos_backend/engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/chinacreator/gzcm/engine/cognitive2/service/mental/BayesianUpdater.java", 3,
            "ecos_backend/engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/chinacreator/gzcm/engine/cognitive2/service/mental/BeliefReplayService.java", 5,
            "ecos_backend/engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/chinacreator/gzcm/engine/cognitive2/service/mental/CounterfactualSimulator.java", 5,
            "ecos_backend/engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/chinacreator/gzcm/engine/cognitive2/service/mental/CognitiveInvalidationConsumer.java", 2,
            "ecos_backend/engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/chinacreator/gzcm/engine/cognitive2/service/mental/MentalReviewService.java", 2,
            "ecos_backend/engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/chinacreator/gzcm/engine/cognitive2/service/CognitiveHypothesisService.java", 8,
            "ecos_backend/engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/chinacreator/gzcm/engine/cognitive2/service/ModelRegistryService.java", 5, // #146l-前置补账：4→5（#146b/P0-8 bae5a686 新增 cognitiveModelType 裸参 400 腿，该批未落账本门）
            "ecos_backend/engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/chinacreator/gzcm/engine/cognitive2/service/CognitiveBeliefService.java", 14,
            "ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/service/GitRepoRootResolver.java", 13,
            "ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/workspace/controller/ScenarioCognitiveController.java", 2,
            "ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/workspace/scenario/ScenarioRunService.java", 3,
            "ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/workspace/scenario/ScenarioSandboxLayoutService.java", 3,
            "ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/workspace/scenario/ScenarioService.java", 12,
            "ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/workspace/scenario/ScenarioMindService.java", 3,
            "ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/workspace/scenario/ScenarioBindingLinkService.java", 7,
            "ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/workspace/forecast/ForecastRunRequestValidator.java", 3);

    /** 基线：三参同号形态逐文件处数（合计 23 处／14 文件＝本批 23 处迁移集）。 */
    private static final Map<String, Integer> BASELINE_THREE_ARG = baseline(
            "ecos_backend/engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/chinacreator/gzcm/engine/cognitive2/controller/CognitiveConfigController.java", 1,
            "ecos_backend/engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/chinacreator/gzcm/engine/cognitive2/service/mental/BeliefReplayService.java", 1,
            "ecos_backend/engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/chinacreator/gzcm/engine/cognitive2/service/mental/CounterfactualSimulator.java", 2,
            "ecos_backend/engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/chinacreator/gzcm/engine/cognitive2/service/CognitiveHypothesisService.java", 3,
            "ecos_backend/engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/chinacreator/gzcm/engine/cognitive2/service/EngineCapabilityRegistryImpl.java", 1,
            "ecos_backend/engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/chinacreator/gzcm/engine/cognitive2/service/EntityLinker.java", 1,
            "ecos_backend/engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/chinacreator/gzcm/engine/cognitive2/service/CognitiveBeliefService.java", 2,
            "ecos_backend/engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/chinacreator/gzcm/engine/cognitive2/config/KbRestClientConfig.java", 1,
            "ecos_backend/engine/cognitive-engine/cognitive-engine-impl/src/main/java/com/chinacreator/gzcm/engine/cognitive2/observability/CognitiveObservability.java", 1,
            "ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/workspace/controller/ScenarioCognitiveController.java", 1,
            "ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/workspace/scenario/ScenarioSandboxLayoutService.java", 4,
            "ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/workspace/scenario/ScenarioService.java", 1,
            "ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/workspace/scenario/ScenarioMindService.java", 3,
            "ecos_backend/workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/workspace/config/WorkspaceRestClientConfig.java", 1);

    /**
     * 基线：首参为标识符（非数值字面量）的站点，逐处点名。
     * <p>{@code DqRuleLifecycleServiceImpl} 那枚是 {@code new BusinessException(String.format(..))}
     * ＝单参 message 的派生形态，默认 400 合法；本表把它点名而不是放行，是为了让「首参换成
     * 域码常量」这种绕过数值判据的写法必须显式改表才可过关。</p>
     */
    private static final Map<String, Integer> BASELINE_IDENT_FIRST = baseline(
            "ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/engine/data/quality/service/DqRuleLifecycleServiceImpl.java", 1);

    // ── 判据 A：双参非 400 零容忍 ──────────────────────

    @Test
    @DisplayName("判据A — main 面「双参形态首参非 400」= 0 处（收口前实测 23 处，新增即红）")
    void legacyTwoArgCarrierNeverPinsWrongStatus() throws IOException {
        Scan scan = scanMainFace();
        assertEquals(0, scan.nonFourHundredTwoArg.size(),
                "双参 BusinessException 携带非 400 首参共 " + scan.nonFourHundredTwoArg.size() + " 处：\n  "
                        + String.join("\n  ", scan.nonFourHundredTwoArg)
                        + "\n缺陷形态：首参只进 body code，状态行被写死 400（§3.2.7 异号）。"
                        + "修法：改三参 new BusinessException(V, V, msg)，body code 逐字不变。");
    }

    // ── 判据 B：三参同号 + 合法区间 ────────────────────

    @Test
    @DisplayName("判据B — 三参形态必须 httpStatus==errorCode 且落在 400..599")
    void explicitCarriersAreSameNumberAndLegal() throws IOException {
        Scan scan = scanMainFace();
        assertEquals(0, scan.parityViolations.size(),
                "三参形态异号／越界共 " + scan.parityViolations.size() + " 处：\n  "
                        + String.join("\n  ", scan.parityViolations)
                        + "\n§3.2.7 要求状态行与 body code 同号；越界值会被出口回退 500，"
                        + "写成三参等于把回退语义藏进调用点。");
    }

    // ── 判据 C：双向基线 + 分枚 ────────────────────────

    @Test
    @DisplayName("判据C — 三种形态的逐文件分布与基线 equals（增＝违例，减＝基线陈旧同样红）")
    void carrierFaceIsTwoSidedRatchet() throws IOException {
        Scan scan = scanMainFace();
        assertTrue(scan.scannedFiles >= MIN_SCANNED_MAIN_FILES,
                "扫描面异常收缩（实扫 main .java " + scan.scannedFiles + " < 阈值 "
                        + MIN_SCANNED_MAIN_FILES + "）——「空扫描＝假绿」，先修门再判绿");
        assertEquals(BASELINE_TWO_ARG_400, scan.twoArg400ByFile,
                "双参 400 形态分布变动（基线 " + sum(BASELINE_TWO_ARG_400) + " 处／"
                        + BASELINE_TWO_ARG_400.size() + " 文件 → 实测 "
                        + sum(scan.twoArg400ByFile) + " 处／" + scan.twoArg400ByFile.size()
                        + " 文件）；若属正当迁移，请同批改台账与基线表");
        assertEquals(BASELINE_THREE_ARG, scan.threeArgByFile,
                "三参同号形态分布变动（基线 " + sum(BASELINE_THREE_ARG) + " 处／"
                        + BASELINE_THREE_ARG.size() + " 文件 → 实测 "
                        + sum(scan.threeArgByFile) + " 处／" + scan.threeArgByFile.size() + " 文件）");
        assertEquals(BASELINE_IDENT_FIRST, scan.identFirstByFile,
                "首参标识符形态变动（基线 " + sum(BASELINE_IDENT_FIRST) + " 处）；"
                        + "首参换成域码常量会让数值判据失明，必须在此点名才可过关");
    }

    @Test
    @DisplayName("判据D — 三类形态之和 = new BusinessException( 全站点数（防判据口径外盲区）")
    void classificationCoversEverySite() throws IOException {
        Scan scan = scanMainFace();
        int classified = sum(scan.twoArg400ByFile) + scan.nonFourHundredTwoArg.size()
                + sum(scan.threeArgByFile) + sum(scan.identFirstByFile) + scan.textFirstSites;
        assertEquals(scan.allSites, classified,
                "全站点 " + scan.allSites + " 与分类之和 " + classified
                        + " 不等 ⇒ 出现了判据未覆盖的新形态（口径外盲区＝假绿）");
        assertEquals(233, scan.allSites,
                "构造点总数双向基线（2026-10-22 实测 232＝100 双参400 + 23 三参同号 + 1 标识符首参 + "
                        + "108 文案首参；#146l-前置补账 232→233＝#146b/P0-8 bae5a686 于 "
                        + "ModelRegistryService 新增 1 枚双参 400 腿未落账本门）；增减都要同批改台账");
    }

    // ── 判据 E：构造面只增不改 ─────────────────────────

    @Test
    @DisplayName("判据E — 三枚构造签名钉死：两枚旧形态恒 400，新三参才把首参放进 httpStatus")
    void constructorFaceIsAdditiveOnly() throws Exception {
        assertEquals(3, BusinessException.class.getDeclaredConstructors().length,
                "BusinessException 构造枚数变动（基线 3：(message)／(errorCode, message)／"
                        + "(httpStatus, errorCode, message)）；第四枚须同批改基线并说明归属");

        BusinessException byMessage = new BusinessException("参数缺失");
        assertEquals(LEGITIMATE_DEFAULT_STATUS, byMessage.getHttpStatus(), "单参腿默认状态行必须仍是 400");
        assertEquals(400, byMessage.getErrorCode(), "单参腿 body code 必须仍是 400");

        BusinessException byCode = new BusinessException(403, "策略拒绝");
        assertEquals(LEGITIMATE_DEFAULT_STATUS, byCode.getHttpStatus(),
                "(errorCode, message) 腿的 httpStatus 恒 400 —— 这条就是 D-B4-56 的旧陷阱，"
                        + "本批改的是调用点改用三参，不是偷偷改这枚的语义");
        assertEquals(403, byCode.getErrorCode(), "旧双参腿 body code 取值不变");

        BusinessException byPair = new BusinessException(403, 403, "策略拒绝");
        assertEquals(403, byPair.getHttpStatus(), "新三参腿必须把首参放进 httpStatus");
        assertEquals(403, byPair.getErrorCode(), "新三参腿 body code 取第二参");
        assertInstanceOf(DataBridgeException.class, byPair, "三参腿仍属 DataBridgeException 体系（出口分派不变）");
    }

    // ── 判据能变红（仓外合成夹具）────────────────────

    @Test
    @DisplayName("合成夹具 — 双参非400／三参异号／越界各能变红，注释里的提及不计，回溯陷阱不重复计数")
    void fixtureProvesTheGateCanTurnRed(@TempDir Path tmp) throws IOException {
        Path f = tmp.resolve("SyntheticCarrier.java");
        Files.writeString(f, String.join("\n",
                "class Carrier {",
                "  void okDefault() { throw new BusinessException(\"只有文案\"); }",
                "  void okTwoArg400() { throw new BusinessException(400, \"参数缺失\"); }",
                "  void badTwoArg404() { throw new BusinessException(404, \"不存在\"); }",
                "  void okThreeArg409() { throw new BusinessException(409, 409, \"冲突\"); }",
                "  void badThreeArgMismatch() { throw new BusinessException(500, 400, \"异号\"); }",
                "  void badThreeArgOutOfRange() { throw new BusinessException(700, 700, \"越界\"); }",
                "  // 注释里写 new BusinessException(404, \"这条不该计数\") 就算违例也不该被读成违例",
                "}",
                ""));
        String raw = Files.readString(f, StandardCharsets.UTF_8);

        Scan stripped = scanSource("SyntheticCarrier.java", SourceCommentStripper.strip(raw));
        assertEquals(6, stripped.allSites, "剥注释后站点数应为 6（注释里那枚提及不计）");
        assertEquals(1, stripped.twoArg400ByFile.values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(List.of("SyntheticCarrier.java [404] 双参携带值不上状态行"),
                stripped.nonFourHundredTwoArg);
        assertEquals(3, stripped.threeArgByFile.values().stream().mapToInt(Integer::intValue).sum());
        assertEquals(2, stripped.parityViolations.size(), stripped.parityViolations.toString());
        assertTrue(stripped.parityViolations.get(0).contains("异号／越界"), stripped.parityViolations.toString());

        Scan unstripped = scanSource("SyntheticCarrier.java", raw);
        assertEquals(7, unstripped.allSites, "不剥注释会多数 1 枚 ⇒ 证明本门的剥注释腿确实在生效");
        assertEquals(2, unstripped.nonFourHundredTwoArg.size(),
                "不剥注释时注释里那枚 404 会被计成违例 ⇒ 反证「先剥注释」不是装饰");
    }

    // ── 扫描实现 ────────────────────────────────────

    private static final class Scan {
        int scannedFiles;
        int allSites;
        int textFirstSites;
        final Map<String, Integer> twoArg400ByFile = new TreeMap<>();
        final Map<String, Integer> threeArgByFile = new TreeMap<>();
        final Map<String, Integer> identFirstByFile = new TreeMap<>();
        final List<String> nonFourHundredTwoArg = new ArrayList<>();
        final List<String> parityViolations = new ArrayList<>();
    }

    private static Scan scanMainFace() throws IOException {
        Path root = locateRepoRoot();
        Scan scan = new Scan();
        List<Path> files = new ArrayList<>();
        collectJavaUnder(root.resolve("ecos_backend"), files);
        for (Path f : files) {
            String rel = root.relativize(f).toString().replace('\\', '/');
            String stripped = SourceCommentStripper.strip(Files.readString(f, StandardCharsets.UTF_8));
            scanSource(rel, stripped, scan);
        }
        scan.scannedFiles = files.size();
        return scan;
    }

    private static Scan scanSource(String rel, String strippedSource) {
        Scan scan = new Scan();
        scanSource(rel, strippedSource, scan);
        return scan;
    }

    /**
     * 单文件扫描体。四类判据互斥：数值首参先看第二参（双参／三参），
     * 再按标识符／字符串字面量分首参形态；四类之和由判据 D 与全站点对账。
     */
    private static void scanSource(String rel, String code, Scan scan) {
        Matcher all = ANY_SITE.matcher(code);
        while (all.find()) {
            scan.allSites++;
        }

        Matcher three = THREE_ARG_NUMERIC.matcher(code);
        while (three.find()) {
            scan.threeArgByFile.merge(rel, 1, Integer::sum);
            int status = Integer.parseInt(three.group(1));
            int bodyCode = Integer.parseInt(three.group(2));
            if (status != bodyCode || status < MIN_LEGAL_CARRIED_STATUS || status > MAX_LEGAL_CARRIED_STATUS) {
                scan.parityViolations.add(rel + " [" + status + "," + bodyCode + "] 三参异号／越界");
            }
        }

        Matcher two = TWO_ARG_NUMERIC.matcher(code);
        while (two.find()) {
            int status = Integer.parseInt(two.group(1));
            if (status == LEGITIMATE_DEFAULT_STATUS) {
                scan.twoArg400ByFile.merge(rel, 1, Integer::sum);
            } else {
                scan.nonFourHundredTwoArg.add(rel + " [" + status + "] 双参携带值不上状态行");
            }
        }

        Matcher ident = IDENT_FIRST.matcher(code);
        while (ident.find()) {
            scan.identFirstByFile.merge(rel, 1, Integer::sum);
        }

        Matcher text = TEXT_FIRST.matcher(code);
        while (text.find()) {
            scan.textFirstSites++;
        }
    }

    /** 基线表构造器：Map.of 只到 10 对，本门三张表最大 18 对，故用变参平铺表（保持 TreeMap 有序）。 */
    private static Map<String, Integer> baseline(Object... keyValues) {
        Map<String, Integer> out = new TreeMap<>();
        for (int i = 0; i < keyValues.length; i += 2) {
            out.put((String) keyValues[i], (Integer) keyValues[i + 1]);
        }
        return out;
    }

    private static int sum(Map<String, Integer> m) {
        return m.values().stream().mapToInt(Integer::intValue).sum();
    }

    private static void collectJavaUnder(Path dir, List<Path> out) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(dir)) {
            walk.filter(Files::isRegularFile)
                    .filter(p -> p.getFileName().toString().endsWith(".java"))
                    .map(p -> p.toAbsolutePath().normalize())
                    .filter(p -> {
                        String s = p.toString().replace('\\', '/');
                        return s.contains("/src/main/java/")
                                && !s.contains("/target/")
                                && !s.contains("/archive/");
                    })
                    .sorted()
                    .forEach(out::add);
        }
    }

    private static Path locateRepoRoot() {
        Path cur = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (Path p = cur; p != null; p = p.getParent()) {
            if (Files.isDirectory(p.resolve("ecos_backend"))) {
                return p;
            }
        }
        return fail("无法定位仓库根（找不到 ecos_backend/），扫描面不可信：user.dir=" + cur);
    }
}
