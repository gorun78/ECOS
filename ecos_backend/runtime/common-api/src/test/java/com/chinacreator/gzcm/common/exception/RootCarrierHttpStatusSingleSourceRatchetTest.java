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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * #142/B4-3e-29（差距 D-B4-66）— 根异常型携带状态腿与区间判定<b>单源</b>门。
 *
 * <p><b>缺陷形态</b>：{@code @ExceptionHandler} 腿的入参型若恰为根异常型
 * {@link DataBridgeException}，其携带的 {@code httpStatus} 可取任意值（根类没有
 * {@code DEFAULT_HTTP_STATUS} 锚定，三枚构造的首参就是状态行）。这种腿一旦返回裸
 * {@code ApiResponse}，状态行就由方法上的 {@code @ResponseStatus} 恒定决定 ——
 * 注解锁死的值与异常自述的值<b>不可能保证同号</b>：sysman-boot 那枚锁 {@code 500}，
 * 于是 403 拒绝在 HTTP 面伪装成 500，与已收口的 gateway 同型腿是同一缺陷在另一宿主的副本。</p>
 *
 * <p><b>本门钉五件事</b>：</p>
 * <ol>
 *   <li><b>判据 A</b>：全后端 main 面参数型为根的 {@code @ExceptionHandler} 腿按文件分枚
 *       {@code equals} 基线（实测 3 枚／3 文件：kb-engine、gateway、sysman-boot），
 *       且逐枚断言返回类型以 {@code ResponseEntity} 开头、方法体引用 {@code resolveHttpStatus}。
 *       增＝新宿主又开一枚裸回根腿，减＝基线陈旧，同样红。</li>
 *   <li><b>判据 B</b>：区间判定字面量（{@code 400} 与 {@code 599} 同现的合法携带区间）在
 *       main 面<b>只允许</b>出现在 {@link DataBridgeException#resolveHttpStatus(int)} 内，
 *       实扫 1 处／1 文件。gateway 的 {@code resolveHttpStatus} 已改为委托，其方法体内
 *       再出现区间字面量＝判据分裂成第二源，直接红。</li>
 *   <li><b>判据 C</b>：无携带状态构造的五枚子类型（Validation／NotFound／Unauthorized／
 *       Forbidden／DataAccess）在 main 面「首参为数值字面量」的构造点 = 0 处 ——
 *       它们的 {@code DEFAULT_HTTP_STATUS} 与宿主注解恒同号，所以<b>注解锁死</b>对这些腿安全；
 *       这条腿是本批「不扩面」的凭据，一旦有人给它们加了带状态构造而没改 handler，立即红。</li>
 *   <li><b>判据 D</b>：反射钉死单源语义（4xx/5xx 原样透出，0／-100／2xx／越界一律 500），
 *       防止委托链上任一层被改成「透传一切」或「全折 500」。</li>
 *   <li><b>判据 E</b>：{@code @TempDir} 仓外合成夹具正反取证 —— 裸回根腿必须被 A 抓到、
 *       区间字面量进 handler 必须被 B 抓到、合规形态必须放过。</li>
 * </ol>
 *
 * <p>取证纪律与同族门（{@code BusinessExceptionStatusParityRatchetTest}）一致：
 * 判据全部先经 {@link SourceCommentStripper} 剥注释，扫描面带阈值守卫防「空扫描＝假绿」。</p>
 */
@Timeout(120)
class RootCarrierHttpStatusSingleSourceRatchetTest {

    /** 参数型恰为根异常型的 handler 腿（子类型腿不在本面：它们没有携带通道）。 */
    private static final Pattern ROOT_CARRIER_LEG = Pattern.compile(
            "@ExceptionHandler\\s*\\(\\s*(?:value\\s*=\\s*)?DataBridgeException\\.class\\s*\\)");

    /** 注解之后的方法声明：返回类型 + 方法名。 */
    private static final Pattern METHOD_SIG = Pattern.compile(
            "public\\s+([\\w.]+(?:<[\\w.,<>\\s]*>)?)\\s+(\\w+)\\s*\\(");

    /** 判据 B：合法携带区间的两个端点字面量同现即为「自带判据」。 */
    private static final Pattern RANGE_JUDGEMENT = Pattern.compile(
            "\\b400\\b[\\s\\S]{0,80}?\\b599\\b");

    /** 判据 B2：handler 腿窗口内自带比较式裁决 —— 越界值可上状态行，属双源。 */
    private static final Pattern SELF_JUDGED_CARRIER = Pattern.compile(
            "getHttpStatus\\s*\\(\\s*\\)\\s*[<>=!]+\\s*\\d+");

    /** 判据 B2 的扫描面：全部 {@code @ExceptionHandler} 腿窗口（不限参数型为根的那三枚）。 */
    private static final Pattern ANY_HANDLER_LEG = Pattern.compile(
            "@ExceptionHandler\\s*\\(");

    /** 判据 C：无携带构造的子类型被写成「首参是数值字面量」＝绕过构造器锁死。 */
    private static final Pattern CARRYING_SITE_ON_LOCKED_TYPE = Pattern.compile(
            "new\\s+(?:ValidationException|NotFoundException|UnauthorizedException"
                    + "|ForbiddenException|DataAccessException)\\s*\\(\\s*+-?\\d");

    /** 单源所在文件（相对仓库根）：判据 B 的唯一豁免点名。 */
    private static final String SINGLE_SOURCE_FILE =
            "ecos_backend/runtime/common-api/src/main/java/com/chinacreator/gzcm/common/exception/"
                    + "DataBridgeException.java";

    /**
     * 「显式常量覆盖」点名豁免表（{@code 相对路径#方法名}），双向基线。
     * <p>kb {@code onBridge} 是本族唯一一枚<b>有意</b>不用携带值的根型腿：注释即契约
     * 「别家 DataBridge 领域异常 → 502」，状态行取显式常量 {@code BAD_GATEWAY}，既不裸回 200
     * 也不折成 403。但它确实把别域携带的 403 报成 502，属 §4.7.2「不可用 ≠ 无权限」的反向可疑面，
     * 是否改走携带值需 kb 域裁决 ⇒ 已登记余项 <b>D-B4-67</b>，本批不扩面。
     * 该腿一旦改走单源，残留的豁免条目即红，逼基线同批下调。</p>
     */
    private static final List<String> EXEMPT_OVERRIDE_LEGS = List.of(
            "ecos_backend/engine/kb-engine/kb-engine-impl/src/main/java/com/chinacreator/gzcm/"
                    + "engine/kb/support/KbErrorAdvice.java#onBridge");

    /** 覆盖腿的最低要求：显式 HTTP 常量直接上状态行（禁裸回、禁注释承诺）。 */
    private static final Pattern EXPLICIT_STATUS_CONSTANT = Pattern.compile(
            "ResponseEntity\\.status\\(\\s*HttpStatus\\.[A-Z_]+\\s*\\)");

    private static final int MIN_LEGAL_CARRIED_STATUS = 400;
    private static final int MAX_LEGAL_CARRIED_STATUS = 599;

    /** 扫描面阈值守卫：2026-10-22 实测 ecos_backend main 面 .java 文件 2339 枚。 */
    private static final int MIN_SCANNED_MAIN_FILES = 2000;

    /** 基线：根型 handler 腿逐文件枚数（实测 3 枚／3 文件，合计 3）。 */
    private static final Map<String, Integer> BASELINE_ROOT_LEGS = baseline(
            "ecos_backend/engine/kb-engine/kb-engine-impl/src/main/java/com/chinacreator/gzcm/"
                    + "engine/kb/support/KbErrorAdvice.java", 1,
            "ecos_backend/gateway/src/main/java/com/chinacreator/gzcm/gateway/handler/"
                    + "GlobalExceptionHandler.java", 1,
            "ecos_backend/services/sysman/impl/sysman-boot/src/main/java/com/chinacreator/gzcm/"
                    + "sysman/boot/handler/GlobalExceptionHandler.java", 1);

    /** 基线：区间判定字面量逐文件处数（实测 1 处／1 文件＝单源本身）。 */
    private static final Map<String, Integer> BASELINE_RANGE_FACE = baseline(SINGLE_SOURCE_FILE, 1);

    // ── 判据 A：根型腿分枚基线 + 逐枚落状态行 ──────────────

    @Test
    @DisplayName("判据A — 根型 @ExceptionHandler 腿 = 基线 3 枚／3 文件，且逐枚走 ResponseEntity + resolveHttpStatus")
    void rootCarrierLegsCarryTheirOwnStatus() throws IOException {
        Scan scan = scanMainFace();
        assertTrue(scan.scannedFiles >= MIN_SCANNED_MAIN_FILES,
                "扫描面异常收缩（实扫 main .java " + scan.scannedFiles + " < 阈值 "
                        + MIN_SCANNED_MAIN_FILES + "）——「空扫描＝假绿」，先修门再判绿");
        assertEquals(BASELINE_ROOT_LEGS, scan.rootLegsByFile,
                "根型 handler 腿分布变动（基线 " + sum(BASELINE_ROOT_LEGS) + " 枚／"
                        + BASELINE_ROOT_LEGS.size() + " 文件 → 实测 "
                        + sum(scan.rootLegsByFile) + " 枚／" + scan.rootLegsByFile.size() + " 文件）；"
                        + "增＝新宿主开了裸回根腿，减＝基线陈旧，两者都要同批改台账");
        assertEquals(0, scan.unresolvedRootLegs.size(),
                "根型腿未走状态行承载手段共 " + scan.unresolvedRootLegs.size() + " 枚：\n  "
                        + String.join("\n  ", scan.unresolvedRootLegs)
                        + "\n缺陷形态：参数型可携带任意 4xx/5xx，而状态行由注解锁死或裸回 200 ⇒"
                        + " 携带值从不上 HTTP 面（§3.2.7 同号性／§4.7.2 不可用 ≠ 无权限）。"
                        + "修法：返体改 ResponseEntity，状态行走 DataBridgeException.resolveHttpStatus 单源。");
        assertEquals(EXEMPT_OVERRIDE_LEGS, scan.overrideLegKeys.stream().sorted().toList(),
                "「显式常量覆盖」豁免表与实扫不一致（基线点名 " + EXEMPT_OVERRIDE_LEGS
                        + " → 实测 " + scan.overrideLegKeys + "）；增＝新覆盖腿没走单源又没登记裁决，"
                        + "减＝覆盖腿已改走单源、豁免条目该同批撤除");
    }

    // ── 判据 B：区间判定单源唯一 ────────────────────────

    @Test
    @DisplayName("判据B — 合法携带区间的判定字面量全后端 main 面只允许 1 处＝单源方法本身")
    void rangeJudgementHasExactlyOneOwner() throws IOException {
        Scan scan = scanMainFace();
        assertEquals(BASELINE_RANGE_FACE, scan.rangeFaceByFile,
                "区间判据面变动（基线 " + sum(BASELINE_RANGE_FACE) + " 处／" + BASELINE_RANGE_FACE.size()
                        + " 文件 → 实测 " + sum(scan.rangeFaceByFile) + " 处／"
                        + scan.rangeFaceByFile.size() + " 文件：\n  "
                        + String.join("\n  ", filesWithCounts(scan.rangeFaceByFile)) + "）。\n"
                        + "判据放在某个 handler 里会逼其它宿主复制第二份；下沉到 common-api 后"
                        + "委托方只准调 DataBridgeException.resolveHttpStatus，不得自带区间字面量。");
        assertEquals(0, scan.rangeOutsideSingleSource.size(),
                "单源之外的自带区间判据 " + scan.rangeOutsideSingleSource.size() + " 处：\n  "
                        + String.join("\n  ", scan.rangeOutsideSingleSource));
        assertEquals(0, scan.selfJudgedSites.size(),
                "handler 腿窗口内的自带比较式裁决（只判正数／非零 ⇒ 越界携带值可直上状态行）"
                        + scan.selfJudgedSites.size() + " 处：\n  "
                        + String.join("\n  ", scan.selfJudgedSites)
                        + "\n修法：改调 DataBridgeException.resolveHttpStatus(ex.getHttpStatus())，"
                        + "由单源决定越界回退 500。注：Controller 侧把携带值当 body code 用的"
                        + "比较式不在本面（窗口只认 @ExceptionHandler 腿），已登记余项 D-B4-67 后半。");
    }

    // ── 判据 C：注解锁死腿的安全性凭据（本批不扩面）──────────

    @Test
    @DisplayName("判据C — 无携带构造的五枚子类型「首参数值」构造点 = 0（注解锁死对它们才安全）")
    void lockedTypesAreNeverConstructedWithACarriedStatus() throws IOException {
        Scan scan = scanMainFace();
        assertEquals(0, scan.lockedTypeCarryingSites.size(),
                "无携带构造的子类型出现数值首参 " + scan.lockedTypeCarryingSites.size() + " 处：\n  "
                        + String.join("\n  ", scan.lockedTypeCarryingSites)
                        + "\n那意味着构造器被加了携带通道，而宿主 handler 仍用注解锁状态行 ⇒"
                        + " D-B4-66 在同一批里重犯。改法：同批改该型所有宿主腿为 ResponseEntity。");
    }

    // ── 判据 D：单源语义反射钉死 ────────────────────────

    @Test
    @DisplayName("判据D — 单源语义：4xx/5xx 原样透出；0／-1／-100／2xx／3xx／600 一律 500")
    void singleSourceSemanticsIsPinned() {
        assertEquals(403, DataBridgeException.resolveHttpStatus(403), "合法拒绝值原样透出");
        assertEquals(503, DataBridgeException.resolveHttpStatus(503), "合法不可用值原样透出");
        assertEquals(400, DataBridgeException.resolveHttpStatus(400), "下界原样透出");
        assertEquals(599, DataBridgeException.resolveHttpStatus(599), "上界原样透出");
        assertEquals(500, DataBridgeException.resolveHttpStatus(0), "未携带 → 500");
        assertEquals(500, DataBridgeException.resolveHttpStatus(-1), "信封负数码不是 HTTP 状态");
        assertEquals(500, DataBridgeException.resolveHttpStatus(-100), "DataBridge 负数码 → 500");
        assertEquals(500, DataBridgeException.resolveHttpStatus(200), "2xx 不是失败语义，禁写进状态行");
        assertEquals(500, DataBridgeException.resolveHttpStatus(302), "3xx 重定向不由异常携带");
        assertEquals(500, DataBridgeException.resolveHttpStatus(600), "越界 → 500");
    }

    // ── 判据 E：能变红（仓外合成夹具）────────────────────

    @Test
    @DisplayName("合成夹具 — 裸回根腿／自带区间判据能变红，合规根腿与注释里的提及放过")
    void fixtureProvesTheGateCanTurnRed(@TempDir Path tmp) throws IOException {
        Path bad = tmp.resolve("BadRootLeg.java");
        Files.writeString(bad, String.join("\n",
                "class BadRootLeg {",
                "  // 注释里的 @ExceptionHandler(DataBridgeException.class) 不该被计成腿",
                "  @ExceptionHandler(DataBridgeException.class)",
                "  @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)",
                "  public ApiResponse<Void> handleDataBridgeException(DataBridgeException ex) {",
                "    return ApiResponse.error(ex.getErrorCode(), ex.getMessage());",
                "  }",
                "  @ExceptionHandler(DataBridgeException.class)",
                "  public ResponseEntity<ApiResponse<Void>> handleSecond(DataBridgeException ex) {",
                "    int status = ex.getHttpStatus() >= 400 && ex.getHttpStatus() <= 599",
                "            ? ex.getHttpStatus() : 500;",
                "    return ResponseEntity.status(status).body(ApiResponse.ok());",
                "  }",
                "  @ExceptionHandler(DataBridgeException.class)",
                "  public ResponseEntity<ApiResponse<Void>> onBridge(DataBridgeException ex) {",
                "    return ResponseEntity.status(HttpStatus.BAD_GATEWAY)",
                "            .body(ApiResponse.error(502, \"ECOS-KB-501\", \"数据桥域异常\"));",
                "  }",
                "  void locked() { throw new ForbiddenException(403, \"不该这样构造\"); }",
                "}",
                ""));
        String code = SourceCommentStripper.strip(Files.readString(bad, StandardCharsets.UTF_8));
        Scan scan = scanSource("BadRootLeg.java", code);

        assertEquals(3, sum(scan.rootLegsByFile), "剥注释后根型腿应为 3 枚（注释里那枚提及不计）");
        assertEquals(3, scan.unresolvedRootLegs.size(), scan.unresolvedRootLegs.toString());
        assertTrue(scan.unresolvedRootLegs.get(0).contains("handleDataBridgeException")
                        && scan.unresolvedRootLegs.get(0).contains("返回类型"),
                "第一枚应抓到裸回那枚：" + scan.unresolvedRootLegs);
        assertTrue(scan.unresolvedRootLegs.get(1).contains("handleSecond")
                        && scan.unresolvedRootLegs.get(1).contains("既未走单源"),
                "第二枚应抓到自带区间判据又不落显式常量的那枚：" + scan.unresolvedRootLegs);
        assertTrue(scan.unresolvedRootLegs.get(2).contains("onBridge")
                        && scan.unresolvedRootLegs.get(2).contains("不在点名豁免表内"),
                "第三枚应抓到「覆盖腿未登记豁免」：" + scan.unresolvedRootLegs);
        assertEquals(List.of("BadRootLeg.java#onBridge"), scan.overrideLegKeys,
                "覆盖腿点名表应只收录显式常量那一枚：" + scan.overrideLegKeys);

        assertEquals(1, sum(scan.rangeFaceByFile),
                "自带区间判据应计 1 处（第二枚腿在方法体里自己写了 400..599）");
        assertEquals(1, scan.rangeOutsideSingleSource.size(), scan.rangeOutsideSingleSource.toString());
        assertEquals(2, scan.selfJudgedSites.size(),
                "第二枚腿的两个比较式都应被 B2 抓到（双保险：B 认区间字面量，B2 认自带比较式）："
                        + scan.selfJudgedSites);
        assertTrue(scan.selfJudgedSites.stream().allMatch(s -> s.contains("getHttpStatus() >=")
                        || s.contains("getHttpStatus() <=")),
                scan.selfJudgedSites.toString());

        assertEquals(1, scan.lockedTypeCarryingSites.size(), scan.lockedTypeCarryingSites.toString());
        assertTrue(scan.lockedTypeCarryingSites.get(0).contains("ForbiddenException"),
                scan.lockedTypeCarryingSites.toString());

        Path selfJudged = tmp.resolve("SelfJudgedLeg.java");
        Files.writeString(selfJudged, String.join("\n",
                "class SelfJudgedLeg {",
                "  @ExceptionHandler(DomainException.class)",
                "  public ResponseEntity<ApiResponse<Void>> onDomain(DomainException ex) {",
                "    int status = ex.getHttpStatus() > 0 ? ex.getHttpStatus() : 500;",
                "    return ResponseEntity.status(status).body(ApiResponse.ok());",
                "  }",
                "}",
                ""));
        Scan judged = scanSource("SelfJudgedLeg.java", SourceCommentStripper.strip(
                Files.readString(selfJudged, StandardCharsets.UTF_8)));
        assertEquals(1, judged.selfJudgedSites.size(),
                "「只判正数」形态必须被 B2 抓到（700 之类越界值可直上状态行）："
                        + judged.selfJudgedSites);

        Path good = tmp.resolve("GoodRootLeg.java");
        Files.writeString(good, String.join("\n",
                "class GoodRootLeg {",
                "  @ExceptionHandler(DataBridgeException.class)",
                "  public ResponseEntity<ApiResponse<Void>> handleDataBridge(DataBridgeException ex) {",
                "    int status = resolveHttpStatus(ex.getHttpStatus());",
                "    return ResponseEntity.status(status).body(",
                "            ApiResponse.error(ex.getErrorCode(), ex.getMessage(), ex.getMessage()));",
                "  }",
                "  void locked() { throw new ForbiddenException(\"只给文案\"); }",
                "}",
                ""));
        Scan clean = scanSource("GoodRootLeg.java",
                SourceCommentStripper.strip(Files.readString(good, StandardCharsets.UTF_8)));
        assertEquals(1, sum(clean.rootLegsByFile), "合规腿仍要计入分布基线");
        assertEquals(List.of(), clean.unresolvedRootLegs, "合规形态不得误报");
        assertEquals(List.of(), clean.rangeFaceByFile.entrySet().stream()
                .filter(e -> !SINGLE_SOURCE_FILE.equals(e.getKey()))
                .map(e -> e.getKey()).toList(), "委托写法不得计成第二源");
        assertEquals(List.of(), clean.lockedTypeCarryingSites);
    }

    // ── 扫描实现 ────────────────────────────────────

    private static final class Scan {
        int scannedFiles;
        final Map<String, Integer> rootLegsByFile = new TreeMap<>();
        final Map<String, Integer> rangeFaceByFile = new TreeMap<>();
        final List<String> unresolvedRootLegs = new ArrayList<>();
        final List<String> overrideLegKeys = new ArrayList<>();
        final List<String> selfJudgedSites = new ArrayList<>();
        final List<String> rangeOutsideSingleSource = new ArrayList<>();
        final List<String> lockedTypeCarryingSites = new ArrayList<>();
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

    private static Scan scanSource(String rel, String code) {
        Scan scan = new Scan();
        scanSource(rel, code, scan);
        return scan;
    }

    private static void scanSource(String rel, String code, Scan scan) {
        Matcher legs = ROOT_CARRIER_LEG.matcher(code);
        while (legs.find()) {
            scan.rootLegsByFile.merge(rel, 1, Integer::sum);
            int from = legs.end();
            int next = code.indexOf("@ExceptionHandler", from);
            String window = code.substring(from, next < 0 ? code.length() : next);
            Matcher sig = METHOD_SIG.matcher(window);
            if (!sig.find()) {
                scan.unresolvedRootLegs.add(rel + " 注解后找不到方法声明，无法证明状态行承载手段");
                continue;
            }
            String returnType = sig.group(1);
            String method = sig.group(2);
            if (!returnType.startsWith("ResponseEntity")) {
                scan.unresolvedRootLegs.add(rel + " #" + method + " 返回类型 " + returnType
                        + "（状态行由注解锁死，携带值不上 HTTP 面）");
            } else if (window.contains("resolveHttpStatus")) {
                // 走单源：合法
            } else if (EXPLICIT_STATUS_CONSTANT.matcher(window).find()) {
                String key = rel + "#" + method;
                scan.overrideLegKeys.add(key);
                if (!EXEMPT_OVERRIDE_LEGS.contains(key)) {
                    scan.unresolvedRootLegs.add(rel + " #" + method
                            + " 覆盖腿不在点名豁免表内（携带值被显式常量取代，须登记裁决依据才可过关）");
                }
            } else {
                scan.unresolvedRootLegs.add(rel + " #" + method
                        + " 既未走单源 resolveHttpStatus，也无显式 HTTP 常量上状态行");
            }
        }

        Matcher anyLeg = ANY_HANDLER_LEG.matcher(code);
        while (anyLeg.find()) {
            int from = anyLeg.end();
            int next = code.indexOf("@ExceptionHandler", from);
            String window = code.substring(from, next < 0 ? code.length() : next);
            Matcher self = SELF_JUDGED_CARRIER.matcher(window);
            while (self.find()) {
                scan.selfJudgedSites.add(rel + " [" + self.group().replaceAll("\\s+", " ") + "]");
            }
        }

        Matcher range = RANGE_JUDGEMENT.matcher(code);
        while (range.find()) {
            scan.rangeFaceByFile.merge(rel, 1, Integer::sum);
            if (!SINGLE_SOURCE_FILE.equals(rel)) {
                scan.rangeOutsideSingleSource.add(rel + " [" + range.group().replace('\n', ' ')
                        .replaceAll("\\s+", " ") + "]");
            }
        }

        Matcher locked = CARRYING_SITE_ON_LOCKED_TYPE.matcher(code);
        while (locked.find()) {
            scan.lockedTypeCarryingSites.add(rel + " [" + locked.group().replaceAll("\\s+", " ") + "]");
        }
    }

    /** 基线表构造器：Map.of 只到 10 对，本门用变参平铺表（保持 TreeMap 有序）。 */
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

    private static List<String> filesWithCounts(Map<String, Integer> m) {
        List<String> out = new ArrayList<>();
        m.forEach((k, v) -> out.add(k + " = " + v));
        return out;
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
