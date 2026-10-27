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
     *
     * <p><b>#142/B4-3e-36 同批下调（−3 处／−1 文件）**：sysman {@code RequirePermissionAspect} 的三枚
     * 拒绝腿（{@code unauthorized} 1／{@code forbidden} 2）由「返回单参信封」整体改为抛
     * {@code UnauthorizedException}／{@code ForbiddenException}——改后该类不再出现在本判据面上，
     * 故文件数一并 −1。这不是「给它们补机读码」，而是<b>换通路</b>：机读码改由承载面（gateway／
     * sysman-boot {@code GlobalExceptionHandler}）经 {@code SystemErrorCode.ECOS_SYS_401/403} 统一注入，
     * 属主侧只携带语义。该文件此前是本枚余面在 sysman 域的唯一持有者，归零＝该域从这条面上退出，
     * 基线若不同批下调，本门会以「基线陈旧」报红（本门双向）。</p>
     *
     * <p><b>#142/B4-3e-53 同批下调（−9 处／0 文件）**：sysman 配置面三文件（{@code ConfigController}
     * ／{@code SystemParamController}／{@code SysConfigController}）的 15 枚「依赖未装配」腿由
     * 返回单参 {@code internalError} 信封改为抛 {@code DataBridgeException(503, …, ECOS-SYS-503)}
     * 承载（差距 <b>D-B4-83</b> 收口件）。−9 而非 −15：其中 6 枚原本就在 {@code catch} 内表达
     * 真实服务故障，不属本判据的迁移面。文件数不变＝三文件各自仍持有 {@code internalError} 兜底腿，
     * 只是不再用它掩盖缺装配。</p>
     *
     * <p><b>#142/B4-3e-56 同批下调（−2 处／0 文件）**：security 属主 {@code AuditController} 的
     * 「审计服务未就绪」折叠腿（{@code get}／{@code writeLog} 各 1 枚
     * {@code return ApiResponse.internalError("审计服务未就绪")}）改为抛
     * {@code DataBridgeException(503, 503, ECOS-SEC-501)} 承载（差距 <b>D-B4-86</b> 收口件）。
     * 本批实际迁移的未装配腿有 6 枚，但入本判据面的只有这 2 枚：另 4 枚折叠形态是
     * {@code 200＋空集}／{@code 200＋全 0 计数}／{@code 200＋valid=false}（走 {@code success(...)}，
     * 不在单参无机读码工厂面内），由 {@code AuditUnavailableCarrierTest} 的源码面判据钉零。
     * 文件数不变＝该类仍持 6 枚 {@code catch} 内真实故障兜底 {@code internalError}，
     * 那是与缺装配不同的态，本批不动。读数取自本批门禁会话内 {@code -pl runtime/common-api} 全量实跑
     * 的失败报文（{@code sites=925, files=150, internalError=306}），非由旧值做算术推出；
     * 算式 927−2＝925 与 308−2＝306 只用作差异归因，归因与实跑读数不符时以实跑读数为准。</p>
     */
    private static final int BASELINE_SITES = 925;
    private static final int BASELINE_FILES = 150;

    /** 分枚基线：只减不增，且合计必须等于 {@link #BASELINE_SITES}（防「拆细了但总数对不上」）。 */
    private static final Map<String, Integer> BASELINE_BY_FACTORY = new TreeMap<>(Map.of(
            "badRequest", 330,
            "unauthorized", 12,
            "forbidden", 22,
            "notFound", 255,
            "internalError", 306));

    /** gateway 入口面已带码的六枚腿（锚点，不得回退）。 */
    private static final int ENTRY_LEGS_CODED = 6;

    /** {@code ECOS-SYS-*} 族码字面量（带引号的串），单源只允许 {@link #SYSTEM_ERROR_CODE_SOURCE} 承载。 */
    private static final Pattern SYS_FAMILY_LITERAL = Pattern.compile("\"ECOS-SYS-\\d+\"");

    /** 族码常量单源文件（唯一允许出现 {@code "ECOS-SYS-<digits>"} 字面量的 main 面文件）。 */
    private static final String SYSTEM_ERROR_CODE_SOURCE =
            "ecos_backend/runtime/common-api/src/main/java/com/chinacreator/gzcm/common/error/SystemErrorCode.java";

    /**
     * {@code ECOS-RATE-*} 族码字面量（带引号的串），单源只允许 {@link #RATE_LIMIT_ERROR_CODE_SOURCE} 承载。
     *
     * <p><b>#142/B4-3e-92 新登</b>（收口差距 P1.1「ECOS-RATE 族不存在」子项）：本批把
     * {@code RateLimitFilter} 与 {@code QuotaFilter} 两枚 429 出口改指 {@code RateLimitErrorCode}
     * 常量后，仿 sysFamily 的字面量零容差判据防止后续收口批把族码再复制到任何调用方。</p>
     */
    private static final Pattern RATE_FAMILY_LITERAL = Pattern.compile("\"ECOS-RATE-\\d+\"");

    /** {@code ECOS-RATE-*} 族码字面量单源文件（唯一承载点）。 */
    private static final String RATE_LIMIT_ERROR_CODE_SOURCE =
            "ecos_backend/runtime/common-api/src/main/java/com/chinacreator/gzcm/common/error/RateLimitErrorCode.java";

    /**
     * 属主两枚 429 出口的判据：该两文件必须各出现一次 {@code RateLimitErrorCode.ECOS_RATE_<n>}
     * 常量引用（合法出口＝改指向常量）。校验文本已剥注释，防止 javadoc 提及被误判。
     */
    private static final Pattern RATE_CODE_REFERENCE = Pattern.compile(
            "RateLimitErrorCode\\s*\\.\\s*ECOS_RATE_\\d+");

    /** 属主出口文件（两枚 429 腿的合法性锚点，缺一即红＝判据面漂移）。 */
    private static final String RATE_LIMIT_FILTER_SOURCE =
            "ecos_backend/gateway/src/main/java/com/chinacreator/gzcm/gateway/filter/RateLimitFilter.java";

    /** 属主出口文件（配额耗尽两腿的合法性锚点）。 */
    private static final String QUOTA_FILTER_SOURCE =
            "ecos_backend/gateway/src/main/java/com/chinacreator/gzcm/gateway/filter/QuotaFilter.java";

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

    // ── 2c. ECOS-RATE 族码字面量单源 + 属主 429 出口锚点（#142/B4-3e-92 新登，P1.1）──

    @Test
    @DisplayName("ECOS-RATE-* 族码字面量在 main 面只由 RateLimitErrorCode 单源承载（副本即红）")
    void rateFamilyLiteralsAreSingleSourced() throws IOException {
        Path root = locateRepoRoot();
        List<Path> files = new ArrayList<>();
        collectJavaUnder(root.resolve("ecos_backend"), files);
        assertTrue(files.size() >= MIN_SCANNED_MAIN_FILES,
                "扫描面缩水：实扫 main 面 java 文件 " + files.size() + " < 阈值 "
                        + MIN_SCANNED_MAIN_FILES + "，判据不可信（读不到 ≠ 无副本）");

        List<String> copies = new ArrayList<>();
        for (Path f : files) {
            String rel = root.relativize(f).toString().replace('\\', '/');
            if (RATE_LIMIT_ERROR_CODE_SOURCE.equals(rel)) {
                continue;
            }
            String stripped = SourceCommentStripper.strip(Files.readString(f, StandardCharsets.UTF_8));
            Matcher m = RATE_FAMILY_LITERAL.matcher(stripped);
            while (m.find()) {
                copies.add(rel + " → " + m.group());
            }
        }
        assertEquals(List.of(), copies,
                "main 面出现 ECOS-RATE-* 字面量副本：出口应改取 RateLimitErrorCode 常量，"
                        + "新增族请先登记进该族并同步 §3.2.7 冻结表与附录 C。副本清单=" + copies);
    }

    /**
     * 属主两枚 429 出口必须从 {@code RateLimitErrorCode} 单源取码。此为 P1.1「配额耗尽
     * 仅裸 429」收口的正向锚点，防后续批次改回 {@code ApiResponse.error(429,"...")} 单参
     * 或加回字面量"ECOS-RATE-429/430"副本。
     *
     * <p>判据＝剥注释后每文件恰 1 枚 {@code RateLimitErrorCode.ECOS_RATE_\d+} 引用：
     * 零枚＝改回单参／新造副本；多枚＝同一出口重复取码，属奇怪但须看板（防未来
     * Filter 拆分成两个 429 出口而常量只在第一处生效）。</p>
     */
    @Test
    @DisplayName("RateLimitFilter 与 QuotaFilter 两枚 429 出口必须取 RateLimitErrorCode 常量单源")
    void quotaAndRateLimit429ExitsPointToSingleSource() throws IOException {
        Path root = locateRepoRoot();
        assertCarriesRateConstants(root.resolve(RATE_LIMIT_FILTER_SOURCE));
        assertCarriesRateConstants(root.resolve(QUOTA_FILTER_SOURCE));
    }

    private static void assertCarriesRateConstants(Path filterPath) throws IOException {
        assertTrue(Files.exists(filterPath), "属主过滤文件不在位：" + filterPath);
        String stripped = SourceCommentStripper.strip(Files.readString(filterPath, StandardCharsets.UTF_8));
        List<String> hits = new ArrayList<>();
        Matcher m = RATE_CODE_REFERENCE.matcher(stripped);
        while (m.find()) {
            hits.add(m.group());
        }
        assertEquals(1, hits.size(),
                "属主 429 出口必须恰 1 处 RateLimitErrorCode.ECOS_RATE_<n> 引用；"
                        + "0 处＝改回裸 429／单参工厂（判据 C 面亦会红），"
                        + ">1 处＝出口重复取码必须同批清点。文件=" + filterPath + "，实跑=" + hits);
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
