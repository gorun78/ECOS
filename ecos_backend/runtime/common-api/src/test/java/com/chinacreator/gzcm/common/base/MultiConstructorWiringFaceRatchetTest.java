package com.chinacreator.gzcm.common.base;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chinacreator.gzcm.common.testscan.SourceCommentStripper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

/**
 * 「Spring 语义类上的多构造器而无构造器级显式装配标注」面棘轮门（#142/B4-3e-75，新登记
 * <b>D-B4-97</b>）。
 *
 * <p><b>为什么存在</b>：#142/B4-3e-12 给 ontology {@code SecurityEngineClient} 加包私有
 * 三参测试缝构造器后，该类变「两构造器、均无 {@code @Autowired}、无公共无参候选」——
 * Spring 6 的构造器选择规则在此组合下退化为 {@code SimpleInstantiationStrategy}，
 * 直接调 {@code <init>()} ⇒ <b>BeanInstantiationException「No default constructor
 * found」</b>，gateway :8080／buszhi :18083／workspace :18090 三个 fat-jar 宿主 context
 * refresh 全灭（本批 22:11 实取证，_win_tasks/logs 三份 stdout 同一条根因链）。该回归
 * 单测面零感知（本模块对该类全部手工 {@code new}，装载 context 的集成用例零枚），
 * 只会在生产启动现形。全仓复扫同形＝本批把 5 枚嫌疑件逐枚实读定档
 * （两枚真改：{@code SecurityEngineClient} 炸型／{@code OlapEngineResolver} 静默丢配置型
 * —Spring 6 在「有公共无参候选」形下静默选无参腿，{@code dw.olap.engine} 被吃掉而无告警），
 * 余 5 枚 benign（详见基线沿革）。没有扫描判据，「同源形零枚」逐文件人眼巡视，下次再长一枚
 * 要重做全仓人眼——本门把判据钉成可复跑读数。</p>
 *
 * <p><b>判据（本门口径）</b>：主源码面（{@code src/main/java}，排除 {@code target/}／
 * {@code archive/}）经 {@link SourceCommentStripper} 剥注释后，对每个带 Spring 语义类级
 * 注解（{@code @Component/@Service/@RestController/@Controller/@Configuration/@Endpoint/
 * @ControllerAdvice/@RestControllerAdvice}）的类，在<b>类体外花括号配对内</b>数构造器
 * 声明（行初、可选修饰符、类名后紧跟 {@code (}，类名与括号间只允许空白）」＝构造器，
 * 否则是同名方法）；≥2 枚时，检查各构造器声明前 200 字符内是否有构造器级
 * {@code @Autowired}（短名或全限定名）——全无为一枚面。另：任一构造器<b>参数位</b>出现
 * {@code @Autowired} 记为「参位形态」，仍计入面（在 Spring 语义下它不等于构造器级标注，
 * 是否需要升级属人工裁决项、本门只钉不改）。面集按「文件相对路径 → 枚数」整张比对，
 * 双向只减不增。</p>
 *
 * <p><b>已知豁免（不入基线、判据性排除，非静默放行）</b>：类内有公共无参构造器的双构造器
 * 类（如 {@code AiToolRateLimitGate()} / {@code OlapEngineResolver()} 的测试缝无参腿）
 * ——Spring 6 有确定的选腿规则（无参优先），两型结果分别为「生产取无参腿」（腿语义须
 * 与生产口径等价，如限流门自解析时钟）与「静默丢配置」（这是 <b>D-B4-97b 线脚</b>，
 * 已随本批 {@code OlapEngineResolver} 加 {@code @Autowired} 收口；判据仍把该形计面，
 * 以便未来同类被钉名）。</p>
 *
 * <p><b>双向</b>：面集上升＝新写了未标注装配的多构造器 Spring 语义类（D-B4-97 面）；
 * 面集下降＝收口后基线陈旧，必须同批下调 {@link #BASELINE_FACE} 并在台账留痕。</p>
 *
 * <p><b>已声明盲区</b>：① 文件含多个顶层类时只检首个 {@code class}（仓内惯例一文件一类，
 * 复扫实读校验过）；② 构造器声明跨行修饰符链（如 {@code @X @Y\n public C(…)}）判定窗口
 * 200 字符固定、已含 FQ 全限定形态取最大长度覆盖；③ 本门只钉源码面，不查已编译构件。</p>
 */
@Timeout(180)
class MultiConstructorWiringFaceRatchetTest {

    /** Spring 语义类级注解（判据枚举面，缺一枚是同族盲区）。 */
    private static final Pattern SPRING_SEMANTIC_ANNOTATION = Pattern.compile(
            "@(Component|Service|RestController|Controller|Configuration|Endpoint"
                    + "|ControllerAdvice|RestControllerAdvice)(\\b|\\()");

    /** 构造器级显式装配标注（短名或全限定名，两者都要识别——仓内 FQ 形式实存）。 */
    private static final Pattern CTDOR_LEVEL_WIRING = Pattern.compile(
            "@(?:(?:org\\.springframework\\.beans\\.factory\\.annotation)\\.)?Autowired\\b");

    /** 扫描面阈值：main 面实扫 java 文件数下限（读不到 ≠ 无违例）。 */
    private static final int MIN_SCANNED_MAIN_FILES = 2000;

    /** 锚点件：本批实炸现场的类文件（回退／改名即红，防锚失效假绿）。 */
    private static final String ANCHOR_SECURITY_ENGINE_CLIENT =
            "ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/java/"
            + "com/chinacreator/gzcm/engine/ontology/security/SecurityEngineClient.java";

    /**
     * 面集基线（#142/B4-3e-75 建门批实跑读数＝<b>18 文件 / 37 处构造器声明</b>，键＝仓根相对路径，
     * 值＝该文件内被本门判据认定的<b>构造器声明格数</b>（顶格数，不论腿是否已标注，便于钉迁移）。
     * 初版 5/8 手工基线凭逐枚备忘录推算，实跑判据因内部类构造器误计+非法正则歇工到本批修完，
     * 现基线＝判据自证后实跑读数，双向锁；逐枚盯面分型如下：</p>
     *
     * <ul>
     *   <li><b>「无 public 无参＋任一构造器带 {@code @Autowired}」＝良性（钉名防迁）</b>：
     *       {@code SecurityEngineClient}（本批炸型修复后在位）、{@code OlapEngineResolver}（本批静默型
     *       修复后在位）、{@code DqRcaServiceImpl}、{@code NavTaxonServiceImpl}、{@code CaliberService}、
     *       {@code OntologyVersionService}（三腿，7 参生产腿带 {@code @Autowired}，2 条测试兼容腿无标注）、
     *       {@code PublishGateService}、{@code ClearanceInterceptor}、{@code RoleServiceImpl}、
     *       {@code TenantServiceImpl}；</li>
     *   <li><b>「有公共无参＋任一构造器带/参位 {@code @Autowired}」＝良性（钉名）</b>：
     *       {@code AiToolRateLimitGate}（无参腿＝生产腿，自解析时钟语义等价）、
     *       {@code DataSourceServiceImpl}（1 参腿带 {@code @Autowired(required=false)}，
     *       public 无参兼容腿走 {@code this(null)} 属 fail-fast 面）、
     *       {@code DataNetResourceClient}（public 无参腿＝生产腿，字段级 @Autowired(required=false)
     *       独立于构造器路径）、{@code SystemDatabaseAccessImpl}（1 参 {@code @Autowired}，
     *       public 无参兼容腿留 null 抛 warn）、{@code DltConsumer}／{@code AuditRetryTask}（
     *       主构造器 FQ 形 {@code @Autowired}）；</li>
     *   <li><b>「测试缝薄接旧签名＋生产腿 {@code @Autowired}」＝良性（钉名防迁）</b>：
     *       {@code SessionManagerImpl}（1 参 {@code @Autowired}＋包私有 2 参数值腿）、
     *       {@code ForecastRunOrchestrationService}（3 参兼容腿薄接 4 参 {@code @Autowired} 主）。</li>
     * </ul>
     * <p>双向：新增文件＝同期 regressed D-B4-97/97b（context 起不来或静默丢配置）直红；
     * 收口批须同步下调此表并在台账留痕。</p>
     */
    private static final Map<String, Integer> BASELINE_FACE = new TreeMap<>(Map.ofEntries(
            Map.entry("ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java/"
                    + "com/chinacreator/gzcm/engine/ai/security/AiToolRateLimitGate.java", 2),
            Map.entry("ecos_backend/engine/data-engine/data-engine-impl/src/main/java/"
                    + "com/chinacreator/gzcm/engine/data/datasource/service/impl/DataSourceServiceImpl.java", 2),
            Map.entry("ecos_backend/engine/data-engine/data-engine-impl/src/main/java/"
                    + "com/chinacreator/gzcm/engine/data/quality/service/DqRcaServiceImpl.java", 2),
            Map.entry("ecos_backend/engine/data-engine/data-engine-impl/src/main/java/"
                    + "com/chinacreator/gzcm/engine/data/writechannel/OlapEngineResolver.java", 2),
            Map.entry("ecos_backend/engine/kb-engine/kb-engine-impl/src/main/java/"
                    + "com/chinacreator/gzcm/engine/kb/service/nav/NavTaxonServiceImpl.java", 2),
            Map.entry("ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/java/"
                    + "com/chinacreator/gzcm/engine/ontology/client/DataNetResourceClient.java", 2),
            Map.entry("ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/java/"
                    + "com/chinacreator/gzcm/engine/ontology/security/SecurityEngineClient.java", 2),
            Map.entry("ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/java/"
                    + "com/chinacreator/gzcm/engine/ontology/service/CaliberService.java", 2),
            Map.entry("ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/java/"
                    + "com/chinacreator/gzcm/engine/ontology/service/OntologyVersionService.java", 3),
            Map.entry("ecos_backend/engine/ontology-engine/ontology-engine-impl/src/main/java/"
                    + "com/chinacreator/gzcm/engine/ontology/service/PublishGateService.java", 2),
            Map.entry("ecos_backend/runtime/llm-gateway/src/main/java/"
                    + "com/chinacreator/gzcm/runtime/llm/session/SessionManagerImpl.java", 2),
            Map.entry("ecos_backend/runtime/runtime-core/src/main/java/"
                    + "com/chinacreator/gzcm/runtime/core/database/impl/SystemDatabaseAccessImpl.java", 2),
            Map.entry("ecos_backend/runtime/runtime-event/src/main/java/"
                    + "com/chinacreator/gzcm/runtime/eventbus/dlq/DltConsumer.java", 2),
            Map.entry("ecos_backend/runtime/runtime-task/src/main/java/"
                    + "com/chinacreator/gzcm/runtime/core/audit/retry/AuditRetryTask.java", 2),
            Map.entry("ecos_backend/services/sysman/impl/sysman-impl/src/main/java/"
                    + "com/chinacreator/gzcm/sysman/iam/service/impl/RoleServiceImpl.java", 2),
            Map.entry("ecos_backend/services/sysman/impl/sysman-impl/src/main/java/"
                    + "com/chinacreator/gzcm/sysman/iam/service/impl/TenantServiceImpl.java", 2),
            Map.entry("ecos_backend/services/sysman/impl/sysman-impl/src/main/java/"
                    + "com/chinacreator/gzcm/sysman/security/ClearanceInterceptor.java", 2),
            Map.entry("ecos_backend/workspace/workspace-impl/src/main/java/"
                    + "com/chinacreator/gzcm/workspace/forecast/ForecastRunOrchestrationService.java", 2)));

    // ── 1. 面集棘轮 ─────────────────────────────────

    @Test
    @DisplayName("多构造器 Spring 语义类装配标注面按实跑基线双向锁定（新 face 即红、收口后基线不同步也红）")
    void multiCtorWiringFaceIsRatcheted() throws IOException {
        Scan scan = scanMainFace();
        assertTrue(scan.scannedFiles >= MIN_SCANNED_MAIN_FILES,
                "扫描面缩水：实扫 main 面 java 文件 " + scan.scannedFiles + " < 阈值 "
                        + MIN_SCANNED_MAIN_FILES + "，判据不可信（读不到 ≠ 无违例）");

        int total = scan.face.values().stream().mapToInt(Integer::intValue).sum();
        int baselineTotal = BASELINE_FACE.values().stream().mapToInt(Integer::intValue).sum();
        assertTrue(total <= baselineTotal,
                "多构造器 Spring 语义类装配标注面上升：" + total + " > 基线 " + baselineTotal
                        + "，面集=" + scan.face + "；新增构造器未加构造器级 @Autowired 且无公共无参候选"
                        + "＝生产 context 起不来或配置静默丢失（D-B4-97 / D-B4-97b），"
                        + "参照 SecurityEngineClient 本批实炸教训：构造器加测试缝必须同批钉"
                        + " @Autowired 到生产腿");
        // 整张双向比对：新文件入面即红、收口未同步基线也红
        assertEquals(BASELINE_FACE, scan.face,
                "多构造器装配标注面与基线不符。新增文件＝新 face（D-B4-97，如给既有 Spring "
                        + "语义类加测试缝构造器又不给生产腿 @Autowired）；文件消失或枚数减少"
                        + "＝基线陈旧，收口批必须同批下调 BASELINE_FACE 并在台账留痕。实跑面集="
                        + scan.face);
    }

    // ── 2. 锚点件（本批实炸现场）─────────────────

    @Test
    @DisplayName("锚点：SecurityEngineClient 生产构造器必须仍带构造器级 @Autowired（摘除即红）")
    void anchorSecurityEngineClientKeepsCtorLevelAutowired() throws IOException {
        Path file = locateRepoRoot().resolve(ANCHOR_SECURITY_ENGINE_CLIENT);
        assertTrue(Files.exists(file), "锚点文件不在位：" + file);
        String src = SourceCommentStripper.strip(Files.readString(file, StandardCharsets.UTF_8));
        // 类内构造器个数面必须仍 ≥2（两腿：生产＋包私有测试缝），腿数跌 1 说明骨架被拆——
        // 骨架拆掉虽无本门故障但领域同源责任消失，钉住可判断「收口形态改变」需同步重设锚点。
        int ctors = countConstructors(src, "SecurityEngineClient");
        assertTrue(ctors >= 2,
                "锚点骨架变化：SecurityEngineClient 构造器声明格数 " + ctors
                        + " < 2，生产腿与测试缝腿的既认定档被拆改，本锚点计数同步重设前不得放行");
        // 核心判据（能变红）：生产两参构造器须带 base-url 的 @Value 路由，且其「声明头部」
        // （参数行前 260 字符——可越过其上一行的 @Autowired 行）里必须留驻构造器级 @Autowired
        Matcher prod = Pattern.compile(
                "@Value\\(\\\"\\$\\{service\\.security\\.base-url[^)]*\\)\\s*String").matcher(src);
        assertTrue(prod.find(), "锚点：生产两参构造器（@Value base-url 腿）不在位，判据锚点失效");
        String head = src.substring(Math.max(0, prod.start() - 260), prod.start());
        // 构造器名须在前窗内（确认该 @Value 属于 SecurityEngineClient 构造器而非他处字段默认值）
        assertTrue(head.contains("SecurityEngineClient("),
                "锚点结构异常：@Value base-url 出现点前 260 字符内找不到 SecurityEngineClient(——"
                        + "构造器骨架被移动，判据窗口须随订正");
        assertTrue(CTDOR_LEVEL_WIRING.matcher(head).find(),
                "锚点回归：生产构造器摘掉构造器级 @Autowired。双构造器且无标注⇒Spring6 "
                        + "SimpleInstantiationStrategy⇒No default constructor（本批实炸三宿主），摘除即红");
        // 测试缝腿（三参 RestTemplate）不应持 @Autowired——若有人挪标注到测试缝腿，
        // 它成为唯一标注腿、生产腿失标。检法＝测试缝声明头部（包私有、无参修饰符、类名直跟）
        // 前 220 字符内不得出现构造器级 @Autowired
        Matcher testseam = Pattern.compile(
                "(?:^|\\n)\\s*(?:(?:public|private|protected)\\s+)*"
                        + "SecurityEngineClient\\s*\\(\\s*String[^)]*RestTemplate"
        ).matcher(src);
        assertTrue(testseam.find(), "锚点：包私有测试缝三参构造器（RestTemplate 腿）不在位");
        int seamHeadStart = Math.max(0, testseam.start() - 220);
        String seamHead = src.substring(seamHeadStart, testseam.start());
        assertFalse(CTDOR_LEVEL_WIRING.matcher(seamHead).find(),
                "标注迁移：@Autowired 挪到了测试缝构造器上——生产腿失标，形态与本批事故同因");
    }

    @Test
    @DisplayName("锚点：OlapEngineResolver 生产构造器（@Value dw.olap.engine 腿）必须仍带构造器级 @Autowired")
    void anchorOlapEngineResolverKeepsCtorLevelAutowired() throws IOException {
        Path file = locateRepoRoot().resolve(
                "ecos_backend/engine/data-engine/data-engine-impl/src/main/java/"
                        + "com/chinacreator/gzcm/engine/data/writechannel/OlapEngineResolver.java");
        assertTrue(Files.exists(file), "锚点文件不在位：" + file);
        String src = SourceCommentStripper.strip(Files.readString(file, StandardCharsets.UTF_8));
        // 判据同 SecurityEngineClient 锚点：锚在 @Value 参数上，声明头部（可越过其上
        // 一行 @Autowired）须留驻构造器级 @Autowired
        Matcher prod = Pattern.compile(
                "@Value\\(\\\"\\$\\{dw\\.olap\\.engine:[^)]*\\)\\s*String").matcher(src);
        assertTrue(prod.find(), "锚点：OlapEngineResolver 生产单参构造器（@Value dw.olap.engine 腿）不在位");
        String head = src.substring(Math.max(0, prod.start() - 260), prod.start());
        assertTrue(head.contains("OlapEngineResolver("),
                "锚点结构异常：@Value dw.olap.engine 出现点前 260 字符内找不到 OlapEngineResolver(——"
                        + "构造器骨架被移动，判据窗口须随订正");
        assertTrue(CTDOR_LEVEL_WIRING.matcher(head).find(),
                "锚点回归：OlapEngineResolver 生产腿摘掉 @Autowired——双构造器（有公共无参测试缝腿）"
                        + "形下 Spring 6 静默选无参腿⇒dw.olap.engine 配置被吃掉（D-B4-97b 静默形）");
    }

    // ── 3. 扫描实现 ────────────────────────────────────

    /** 构造器名与括号间只允许空白⇒实为构造器而非同名方法；用于锚点计数（简单形态）。 */
    static int countConstructors(String strippedSrc, String className) {
        int n = 0;
        Matcher m = Pattern.compile("(?:^|\\n)\\s*(?:(?:public|private|protected|static|final|synchronized)\\s+)*"
                + Pattern.quote(className) + "\\s*\\(").matcher(strippedSrc);
        while (m.find()) {
            n++;
        }
        return n;
    }

    private static final class Scan {
        int scannedFiles;
        final Map<String, Integer> face = new TreeMap<>();
    }

    private static Scan scanMainFace() throws IOException {
        Path root = locateRepoRoot();
        Scan scan = new Scan();
        List<Path> files = new ArrayList<>();
        collectJavaUnder(root.resolve("ecos_backend"), files);
        for (Path f : files) {
            String rel = root.relativize(f).toString().replace('\\', '/');
            String stripped = SourceCommentStripper.strip(Files.readString(f, StandardCharsets.UTF_8));
            Integer hits = debtCount(stripped);
            if (hits != null) {
                scan.face.put(rel, hits);
            }
        }
        scan.scannedFiles = files.size();
        return scan;
    }

    /**
     * 文件级判定：首个顶层 {@code class} 带 Spring 语义注解且（类体<b>顶层</b>，排除内部类
     * 声明头之后）构造器声明 ≥2 时返回该格数（不论腿是否已标注；任一腿已标注＝该模块宿主
     * context 已被本批实证起活，未标注腿属「迁移/参位裁决」盯面，入基线不单判死刑）；
     * 否则返回 null（非面）。
     * <p><b>与 3e-59 门同源的判据取舍</b>：构造器识别＝「行初＋可选修饰符＋类名紧跟
     * {@code (}」（类名与括号间只允许空白，否则是同名方法）；本版本相对初版的两处可复跑
     * 订正：① 初版在整段类体上数，内部类声明头部（如 {@code public SnapshotFrozenException(
     * String}）被误计为外层构造器（CaliberService 4 实为 2／SystemDatabaseAccessImpl 3 实为
     * 2／DqRcaServiceImpl 3 实为 2），现扫内层 {@code }{@code { } 对并跳过；② 初版标注尾巴
     * 判定正则 {@code "(?s)*…"} 是非法式（dangling meta-character），现改「空白或纯修饰符链」
     * 两条合法分支。内部类自身的多构造器形态＝盲区（首 {@code class} 锚定口径，见声明盲区），
     * 由合成夹具取证留证。</p>
     */
    static Integer debtCount(String stripped) {
        Matcher cm = Pattern.compile(
                "(?:^|\\n)(?:@\\w+(?:\\s*\\([^)]*\\))?\\s*)*"
                        + "(public\\s+|abstract\\s+|final\\s+)*class\\s+([A-Za-z0-9_]+)").matcher(stripped);
        if (!cm.find()) {
            return null;
        }
        int classStart = cm.start();
        String className = cm.group(2);
        // 类级注解判定：类声明前 240 字符内（含声明方自身带检）
        int prefixStart = Math.max(0, classStart - 240);
        if (!SPRING_SEMANTIC_ANNOTATION.matcher(stripped.substring(prefixStart, cm.end())).find()
                && !SPRING_SEMANTIC_ANNOTATION.matcher(cm.group()).find()) {
            return null;
        }
        // 类体：类头之后第一个 { 到配对 }
        int bodyOpen = stripped.indexOf('{', cm.end());
        if (bodyOpen < 0) {
            return null;
        }
        int bodyClose = matchingBrace(stripped, bodyOpen);
        if (bodyClose < 0) {
            return null;
        }
        String body = stripped.substring(bodyOpen + 1, bodyClose);
        // 内部类声明头部起点（body 相对下标）：其后至配对 } 为内层世界，构造器计数跳过
        List<int[]> innerSpans = new ArrayList<>();
        Matcher im = Pattern.compile("(?:^|\\n)\\s*(?:(?:public|private|protected|static|final|abstract|sealed)\\s+)+"
                + "class\\s+").matcher(body);
        while (im.find()) {
            int o = body.indexOf('{', im.end());
            if (o < 0 || o >= body.length()) {
                continue;
            }
            int c = matchingBrace(body, o);
            if (c < 0) {
                continue;
            }
            innerSpans.add(new int[]{o, c});
        }
        Matcher dm = Pattern.compile("(?:^|\\n)\\s*(?:(?:public|private|protected|static|final|synchronized)\\s+)*"
                + Pattern.quote(className) + "\\s*\\(").matcher(body);
        int total = 0;
        int annotated = 0;
        while (dm.find()) {
            if (insideAny(innerSpans, dm.start())) {
                continue;   // 内部类里的同名构造器／同名方法：非本类构造器
            }
            total++;
            int headStart = Math.max(0, dm.start() - 240);
            String head = body.substring(headStart, dm.start());
            // 头部窗口尾降噪：若构造器在 body 开头（无先于它的字段）则无从判定，保守判非面
            int t = head.length();
            while (t > 0 && Character.isWhitespace(head.charAt(t - 1))) {
                t--;
            }
            if (t == 0) {
                return null;
            }
            // 标注判尾巴：@ 之后到声明之间只允许空白或纯修饰符链，才是挂在<b>本构造器</b>上
            Matcher am = CTDOR_LEVEL_WIRING.matcher(head);
            boolean found = false;
            while (am.find()) {
                String tailActual = head.substring(am.end(), t);
                if (tailActual.isBlank()
                        || tailActual.matches("(?s)(?:(?:public|private|protected|static|final|synchronized)\\s+)+")) {
                    found = true;
                    break;
                }
            }
            if (found) {
                annotated++;
            }
        }
        if (total < 2) {
            return null;
        }
        return total;   // 不论腿是否已标注：入基线逐枚盯（已标注钉名＝防迁移；未标注＝事故/裁决面）
    }

    private static boolean insideAny(List<int[]> spans, int pos) {
        for (int[] sp : spans) {
            if (pos > sp[0] && pos < sp[1]) {
                return true;
            }
        }
        return false;
    }

    /** 从开括号起做字面量感知的花括号配对；不闭合返回 -1。 */
    private static int matchingBrace(String src, int openIndex) {
        int depth = 0;
        for (int i = openIndex; i < src.length(); i++) {
            char c = src.charAt(i);
            if (c == '"' || c == '\'') {
                i = skipLiteral(src, i);
                continue;
            }
            if (c == '{') {
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0) {
                    return i;
                }
            }
        }
        return -1;
    }

    /** 返回字面量结束引号的下标（含文本块），调用方以 {@code i = end} 继续推进。 */
    private static int skipLiteral(String src, int quoteIndex) {
        char q = src.charAt(quoteIndex);
        if (q == '"' && quoteIndex + 2 < src.length()
                && src.charAt(quoteIndex + 1) == '"'
                && src.charAt(quoteIndex + 2) == '"') {
            int i = quoteIndex + 3;
            int n = src.length();
            while (i < n) {
                if (src.charAt(i) == '\\') {
                    i += 2;
                    continue;
                }
                if (i + 2 < n && src.charAt(i) == '"' && src.charAt(i + 1) == '"'
                        && src.charAt(i + 2) == '"') {
                    return i + 2;
                }
                i++;
            }
            return n - 1;
        }
        int i = quoteIndex + 1;
        int n = src.length();
        while (i < n) {
            char c = src.charAt(i);
            if (c == '\\') {
                i += 2;
                continue;
            }
            if (c == q) {
                return i;
            }
            i++;
        }
        return n - 1;
    }

    /** 仓根定位：user.dir 向上找含 {@code ecos_backend/} 的目录。 */
    private static Path locateRepoRoot() {
        Path cur = Path.of(System.getProperty("user.dir")).toAbsolutePath().normalize();
        for (Path p = cur; p != null; p = p.getParent()) {
            if (Files.isDirectory(p.resolve("ecos_backend"))) {
                return p;
            }
        }
        throw new IllegalStateException("无法从 user.dir=" + cur + " 向上定位仓根（缺 ecos_backend/）");
    }

    /** 收集主源码面 java 文件（{@code src/main/java/}，排除 {@code target/} 与 {@code archive/}）。 */
    private static void collectJavaUnder(Path dir, List<Path> out) throws IOException {
        if (!Files.isDirectory(dir)) {
            return;
        }
        try (java.util.stream.Stream<Path> walk = Files.walk(dir)) {
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

}
