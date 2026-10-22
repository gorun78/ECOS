package com.chinacreator.gzcm.common.base;

import com.chinacreator.gzcm.common.testscan.SourceCommentStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.lang.reflect.Method;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 五枚单参错误工厂的 <b>机读码缺席棘轮门</b>（#142/B4-3e-27，收口差距 <b>D-B4-65</b>）。
 *
 * <p><b>差距形态</b>：{@link ApiResponse} 的 {@code badRequest/unauthorized/forbidden/notFound/
 * internalError} 五枚单参工厂走三参私有构造器，<b>从不填</b> {@code errorCode}；本类带
 * {@code @JsonInclude(NON_NULL)} ⇒ JSON 面连 {@code errorCode} 这个键都不出现（不是空串、
 * 不是 null 值）。调用方按机读码分派时拿到的是「字段不存在」，与 D-B4-55（把
 * {@code String.valueOf(int)} 当机读码填进去）是同一坑的两半：55 是「填了假的」，本条是
 * 「干脆没填」。同条还顺出第二面：{@code internalError} 的数字码是
 * {@link ApiResponse#CODE_INTERNAL_ERROR}{@code = -1}，与状态行 500 不同号。</p>
 *
 * <p><b>本门钉五件事</b>：
 * <ol>
 *   <li><b>余面量化、只减不增</b>：全后端 {@code src/main/java}（排除 {@code target/}／
 *       {@code archive/}）内单参工厂调用点逐文件计数入基线。新增即红，<b>下降同样即红</b>
 *       （逼后续收口批同批下调基线，防「基线陈旧＝已收口」的假绿，与
 *       {@code SecurityDenialHttpStatusRatchetTest} 同构）。</li>
 *   <li><b>入口锚点已带码且不得回退</b>：gateway {@code GlobalExceptionHandler} 六枚平台入口
 *       兜底腿（400 参数校验／400 参数格式／400 请求体非法 JSON／401 未认证／403 权限不足×2）
 *       必须经新增的带码重载取 {@code SystemErrorCode} 常量；第七枚
 *       {@code handleNotFound} <b>有意保留单参</b>并登记豁免理由（见该腿 javadoc）——
 *       业务域「资源不存在」的码归各域码表，信封类无权替全仓 255 处 {@code notFound}
 *       指定一个平台码，那是用批量替换冒充归因。豁免腿若哪天被补码，本门因「豁免面陈旧」变红，
 *       逼着同批改判据。</li>
 *   <li><b>族码字面量单源</b>：{@code "ECOS-SYS-<digits>"} 串在 main 面只允许
 *       {@code SystemErrorCode} 本类承载，其余任何出现即红（零容忍、不设棘轮）。这条钉的是
 *       「码表抄一份到调用方」形态——本批把 {@code SystemEventTopicsV1Controller}
 *       的 {@code ERROR_SYS_UNAVAILABLE} 副本改指常量即为其取证。</li>
 *   <li><b>重载形态被钉住</b>：五枚 {@code (String errorCode, String message)} 重载必须存在、
 *       数字码取信封既有约定（400/401/403/404），其中带码 {@code internalError} 取
 *       {@link ApiResponse#CODE_SERVER_ERROR}{@code = 500}（同号）；同时钉单参版
 *       {@code internalError} 仍是 -1，防止后来者误以为负数码约定已废除。</li>
 *   <li><b>判据能变红</b>：合成夹具在仓外 {@code @TempDir} 取证，不往仓库塞违例文件。</li>
 * </ol>
 *
 * <p><b>假绿防护</b>：① 扫描根解析 + 实扫文件数阈值（读不到 ≠ 无违例）；② 先经
 * {@link SourceCommentStripper} 剥注释再比对——本类与 {@link ApiResponse} 的 javadoc 里
 * 大量提到 {@code badRequest(...)}，不剥注释会把说明文字计成违例，也会让真实违例被
 * 「注释里也有」的口径稀释（该盲区系 D-B4-40 已登记形态，本门直接复用其单源剥器）。</p>
 */
@Timeout(120)
class NoMachineCodeFactoryFaceRatchetTest {

    /**
     * 判据：单参工厂调用点（{@code ApiResponse.xxx(} 形态，含静态导入后的裸调用不计＝只认限定名）。
     *
     * <p>末段 {@code (?!\s*SystemErrorCode\s*\.)} 把带码重载排除在外——本门追缴的是「机读码缺席」
     * 这一面，带码出口属已收口形态，不能被计成违例，否则 6 枚入口腿迁到带码重载后总数不降，
     * 「只减不增」的口径就失去意义。该排除由 {@link #fixtureCountsSingleArgSitesOnlyAndIgnoresComments}
     * 的合成夹具取证（夹具里带码那一枚若被计入，剥注释后的读数会从 3 变 4）。</p>
     */
    private static final Pattern SINGLE_ARG_FACTORY = Pattern.compile(
            "ApiResponse\\s*\\.\\s*(badRequest|unauthorized|forbidden|notFound|internalError)\\s*\\(\\s*+(?!SystemErrorCode\\s*\\.)");

    /** 带码重载调用点：第二枚实参前必须出现 {@code SystemErrorCode.} 常量引用。 */
    private static final Pattern CODED_FACTORY = Pattern.compile(
            "ApiResponse\\s*\\.\\s*(badRequest|unauthorized|forbidden|notFound|internalError)\\s*\\(\\s*SystemErrorCode\\s*\\.\\s*(ECOS_SYS_\\w+)");

    private static final String HANDLER =
            "ecos_backend/gateway/src/main/java/com/chinacreator/gzcm/gateway/handler/GlobalExceptionHandler.java";

    /** 扫描面阈值：main 面实扫 java 文件数下限（读不到／路径变更 ≠ 零违例）。 */
    private static final int MIN_SCANNED_MAIN_FILES = 2000;

    /**
     * 余面基线（#142/B4-3e-27 本批实跑读数，判据＝{@link SourceCommentStripper} 剥注释后逐次计数，
     * 排除 {@code target/}／{@code archive/}／非 {@code src/main/java}，并排除带 {@code SystemErrorCode.}
     * 的带码重载）。改前同判据读数＝{@code 945} 处／{@code 151} 文件（分枚 badRequest 333／
     * unauthorized 14／forbidden 26／notFound 255／internalError 317）；本批把 gateway 入口七枚腿中
     * 六枚迁到带码重载（badRequest −3、unauthorized −1、forbidden −2，{@code handleNotFound} 登记豁免
     * 保留单参）⇒ −6。改前分枚由 {@code git show HEAD} 的 handler 腿清单逐枚对账（7 腿：90/198/260
     * badRequest、100 notFound、147 unauthorized、157/186 forbidden）。
     */
    private static final int BASELINE_SITES = 939;
    private static final int BASELINE_FILES = 151;

    /** 分枚基线：只减不增，且合计必须等于 {@link #BASELINE_SITES}（防「拆细了但总数对不上」）。 */
    private static final Map<String, Integer> BASELINE_BY_FACTORY = new TreeMap<>(Map.of(
            "badRequest", 330,
            "unauthorized", 13,
            "forbidden", 24,
            "notFound", 255,
            "internalError", 317));

    /** gateway 入口面已带码的六枚腿（锚点，不得回退）。 */
    private static final int ENTRY_LEGS_CODED = 6;

    /** {@code ECOS-SYS-*} 族码字面量（带引号的串），单源只允许 {@link #SYSTEM_ERROR_CODE_SOURCE} 承载。 */
    private static final Pattern SYS_FAMILY_LITERAL = Pattern.compile("\"ECOS-SYS-\\d+\"");

    /** 族码常量单源文件（唯一允许出现 {@code "ECOS-SYS-<digits>"} 字面量的 main 面文件）。 */
    private static final String SYSTEM_ERROR_CODE_SOURCE =
            "ecos_backend/runtime/common-api/src/main/java/com/chinacreator/gzcm/common/error/SystemErrorCode.java";

    /** 登记豁免：唯一保留单参的入口腿。 */
    private static final List<String> REGISTERED_ENTRY_EXEMPTIONS = List.of("handleNotFound");

    // ── 1. 余面棘轮 ─────────────────────────────────

    @Test
    @DisplayName("单参工厂余面按实跑基线只减不增，下降（基线陈旧）同样变红")
    void noMachineCodeFactoryFaceIsRatcheted() throws IOException {
        Scan scan = scanMainFace();
        assertTrue(scan.scannedFiles >= MIN_SCANNED_MAIN_FILES,
                "扫描面缩水：实扫 main 面 java 文件 " + scan.scannedFiles + " < 阈值 "
                        + MIN_SCANNED_MAIN_FILES + "，判据不可信（读不到 ≠ 无违例）");

        Map<String, Integer> byFactory = scan.countByFactory();
        int sites = scan.totalSites();
        int files = scan.filesWithHit();

        if (sites > BASELINE_SITES) {
            fail("单参（无机读码）工厂调用点上升：" + sites + " > 基线 " + BASELINE_SITES
                    + "，分枚=" + byFactory + "；新增出口必须走带码重载或登记基线并说明归因理由。"
                    + "命中文件（前 20）=" + firstN(new ArrayList<>(scan.hitsByFile.keySet()), 20));
        }
        // 一次给出全部三个实跑读数（总数／文件数／分枚），避免「改一个数跑一轮」的往返
        String actualFace = "sites=" + sites + ", files=" + files + ", byFactory=" + byFactory;
        String expectedFace = "sites=" + BASELINE_SITES + ", files=" + BASELINE_FILES
                + ", byFactory=" + BASELINE_BY_FACTORY;
        assertEquals(expectedFace, actualFace,
                "单参工厂余面与基线不符。上升＝新增无机读码出口；下降＝基线陈旧，收口批必须同批下调 "
                        + "BASELINE_SITES/BASELINE_FILES/BASELINE_BY_FACTORY 并在台账留痕");

        int sum = byFactory.values().stream().mapToInt(Integer::intValue).sum();
        assertEquals(BASELINE_SITES, sum,
                "分枚合计必须等于总数基线，否则说明拆分口径与扫描口径不一致");
    }

    // ── 2. 入口锚点 ─────────────────────────────────

    @Test
    @DisplayName("gateway 入口六枚兜底腿必须经带码重载取 SystemErrorCode 常量")
    void platformEntryLegsCarryRegisteredMachineCodes() throws IOException {
        Path handler = locateRepoRoot().resolve(HANDLER);
        assertTrue(Files.exists(handler), "入口 handler 文件不在位：" + handler);
        String src = SourceCommentStripper.strip(Files.readString(handler, StandardCharsets.UTF_8));

        List<String> coded = new ArrayList<>();
        Matcher m = CODED_FACTORY.matcher(src);
        while (m.find()) {
            coded.add(m.group(1) + "→" + m.group(2));
        }
        assertEquals(ENTRY_LEGS_CODED, coded.size(),
                "入口带码腿数与凭证不符（回退即红）：实跑 " + coded);

        // 单参腿只允许登记豁免的那一枚
        List<String> legacy = new ArrayList<>();
        Matcher s = SINGLE_ARG_FACTORY.matcher(src);
        while (s.find()) {
            legacy.add(s.group(1));
        }
        assertEquals(1, legacy.size(),
                "入口面单参（无机读码）腿必须只剩登记豁免的一枚，实跑=" + legacy);
        assertEquals("notFound", legacy.get(0),
                "豁免腿的形态变了（handleNotFound 被补码或改名）：请同批撤豁免并改判据，实跑=" + legacy);
        assertTrue(src.contains("handleNotFound"),
                "豁免反查：基线登记的 handleNotFound 腿已不在 handler 内，豁免清单陈旧");
        for (String ex : REGISTERED_ENTRY_EXEMPTIONS) {
            assertTrue(src.contains(ex), "豁免方法 " + ex + " 不在位，豁免清单需随批修剪");
        }
    }

    // ── 2b. 族码字面量副本（D-B4-65 余项「副本面」收口腿）──

    @Test
    @DisplayName("ECOS-SYS-* 族码字面量在 main 面只由 SystemErrorCode 单源承载（副本即红）")
    void sysFamilyLiteralsAreSingleSourced() throws IOException {
        Path root = locateRepoRoot();
        List<Path> files = new ArrayList<>();
        collectJavaUnder(root.resolve("ecos_backend"), files);
        assertTrue(files.size() >= MIN_SCANNED_MAIN_FILES,
                "扫描面缩水：实扫 main 面 java 文件 " + files.size() + " < 阈值 "
                        + MIN_SCANNED_MAIN_FILES + "，判据不可信（读不到 ≠ 无副本）");

        List<String> copies = new ArrayList<>();
        for (Path f : files) {
            String rel = root.relativize(f).toString().replace('\\', '/');
            if (SYSTEM_ERROR_CODE_SOURCE.equals(rel)) {
                continue;
            }
            String stripped = SourceCommentStripper.strip(Files.readString(f, StandardCharsets.UTF_8));
            Matcher m = SYS_FAMILY_LITERAL.matcher(stripped);
            while (m.find()) {
                copies.add(rel + " → " + m.group());
            }
        }
        assertEquals(List.of(), copies,
                "main 面出现 ECOS-SYS-* 字面量副本：出口应改取 SystemErrorCode 常量，"
                        + "新增族码请先登记进该族并同步 §3.2.7。副本清单=" + copies);
    }

    // ── 3. 重载契约 ─────────────────────────────────

    @Test
    @DisplayName("五枚带码重载存在、数字码与单参版同号、机读码原样进信封")
    void codedOverloadsExistAndEmitMachineCode() throws Exception {
        assertCoded("badRequest", ApiResponse.CODE_BAD_REQUEST);
        assertCoded("unauthorized", ApiResponse.CODE_UNAUTHORIZED);
        assertCoded("forbidden", ApiResponse.CODE_FORBIDDEN);
        assertCoded("notFound", ApiResponse.CODE_NOT_FOUND);
        // 带码 internalError 取 500（与状态行同号），单参版仍 -1，见下一条
        assertCoded("internalError", ApiResponse.CODE_SERVER_ERROR);
    }

    @Test
    @DisplayName("单参 legacy 工厂数字码逐枚保持不变（-1 约定属另批裁决，本批不偷改）")
    void legacySingleArgFactoriesKeepTheirNumericCodes() {
        assertEquals(ApiResponse.CODE_BAD_REQUEST,
                ApiResponse.<Void>badRequest("m").getCode(), "badRequest 单参版数字码被改＝动了 333 处存量语义面");
        assertEquals(ApiResponse.CODE_UNAUTHORIZED,
                ApiResponse.<Void>unauthorized("m").getCode());
        assertEquals(ApiResponse.CODE_FORBIDDEN,
                ApiResponse.<Void>forbidden("m").getCode());
        assertEquals(ApiResponse.CODE_NOT_FOUND,
                ApiResponse.<Void>notFound("m").getCode());
        assertEquals(-1, ApiResponse.<Void>internalError("m").getCode(),
                "CODE_INTERNAL_ERROR 的 -1 是信封 v1「code<0＝系统错误」约定；改它属另批裁决");
        assertEquals(ApiResponse.CODE_INTERNAL_ERROR,
                ApiResponse.<Void>internalError("m").getCode(), "-1 必须来自常量而非散落字面量");
        assertNull(ApiResponse.<Void>internalError("m").getErrorCode(),
                "单参版仍不填机读码＝本门基线追缴的对象，若哪天偷偷批量填码而不改基线，这里必须给出证据");
    }

    private static void assertCoded(String name, int expectedCode) throws Exception {
        Method method = ApiResponse.class.getMethod(name, String.class, String.class);
        Object returned = method.invoke(null, "ECOS-SYS-TEST", "msg");
        ApiResponse<?> envelope = (ApiResponse<?>) returned;
        assertEquals(expectedCode, envelope.getCode(), name + " 带码重载的数字码不符");
        assertEquals("ECOS-SYS-TEST", envelope.getErrorCode(), name + " 带码重载未把机读码放进信封");
        assertEquals("msg", envelope.getMessage());
        assertNull(envelope.getData(), name + " 错误出口的 data 必须为 null");
        assertNotNull(envelope.getTimestamp());
    }

    // ── 4. 判据能变红（仓外合成夹具）────────────────

    @Test
    @DisplayName("合成夹具：带码调用不计、单参调用逐条计入、注释内提及不计")
    void fixtureCountsSingleArgSitesOnlyAndIgnoresComments(@TempDir Path tmp) throws IOException {
        Path f = tmp.resolve("SyntheticFactoryCarrier.java");
        Files.writeString(f, String.join("\n",
                "class Carrier {",
                "  Object a = ApiResponse.badRequest(SystemErrorCode.ECOS_SYS_400, m);",   // 带码＝不计
                "  Object b = ApiResponse.notFound(m);",                                    // 计 1
                "  Object c = ApiResponse.forbidden(m);",                                   // 计 1
                "  Object d = ApiResponse.internalError(m);",                               // 计 1
                "  // ApiResponse.notFound(m) 只是说明文字，不计",
                "}",
                ""), StandardCharsets.UTF_8);
        List<String> raw = List.of(Files.readString(f, StandardCharsets.UTF_8));
        assertEquals(3, countSites(raw.get(0)), "剥注释后应恰计 3 处单参调用（带码与注释各排除一枚）");
        // 反向：若不剥注释，说明文字会被计成违例＝本门刻意规避的口径漂移
        assertEquals(4, countSitesNaive(raw.get(0)),
            "该夹具同时取证「不剥注释会多计一枚」，判据口径变更必须留下可见差异");
    }

    private static int countSites(String src) {
        return countIn(SourceCommentStripper.strip(src));
    }

    private static int countSitesNaive(String src) {
        return countIn(src);
    }

    private static int countIn(String text) {
        Matcher m = SINGLE_ARG_FACTORY.matcher(text);
        int n = 0;
        while (m.find()) {
            n++;
        }
        return n;
    }

    // ── 扫描实现 ────────────────────────────────────

    private static final class Scan {
        int scannedFiles;
        final Map<String, Integer> hitsByFile = new TreeMap<>();
        final Map<String, Integer> byFactory = new TreeMap<>();

        int totalSites() {
            return hitsByFile.values().stream().mapToInt(Integer::intValue).sum();
        }

        int filesWithHit() {
            return hitsByFile.size();
        }

        Map<String, Integer> countByFactory() {
            return new TreeMap<>(byFactory);
        }
    }

    private static Scan scanMainFace() throws IOException {
        Path root = locateRepoRoot();
        Scan scan = new Scan();
        List<Path> files = new ArrayList<>();
        collectJavaUnder(root.resolve("ecos_backend"), files);
        for (Path f : files) {
            String rel = root.relativize(f).toString().replace('\\', '/');
            String stripped = SourceCommentStripper.strip(Files.readString(f, StandardCharsets.UTF_8));
            Matcher m = SINGLE_ARG_FACTORY.matcher(stripped);
            int n = 0;
            while (m.find()) {
                n++;
                scan.byFactory.merge(m.group(1), 1, Integer::sum);
            }
            if (n > 0) {
                scan.hitsByFile.put(rel, n);
            }
        }
        scan.scannedFiles = files.size();
        return scan;
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

    private static List<String> firstN(List<String> in, int n) {
        return in.size() <= n ? in : in.subList(0, n);
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
