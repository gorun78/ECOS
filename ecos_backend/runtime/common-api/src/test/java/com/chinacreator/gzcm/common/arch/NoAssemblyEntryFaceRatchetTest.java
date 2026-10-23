package com.chinacreator.gzcm.common.arch;

import com.chinacreator.gzcm.common.testscan.SourceCommentStripper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * 「无装配入口面」泛化棘轮门（D-B4-80，#142/B4-3e-49）。
 *
 * <p><b>本门要治的形态</b>：D-B4-77 的死面清单（3e-45 落账）是<b>手抄枚举行</b>——只钉住两枚持有者的
 * 10 枚腿，全仓同类的「既没被 Spring 装配、又没有任何代码引用」的类从未盘点。手抄清单与真仓之间没有
 * 任何机制保证同步：新增一枚死面不会红，删掉一枚死面也不会红，账面就此变成一张会过期的抄本。</p>
 *
 * <p><b>判据（全部可推导，禁逐枚手工抄）</b>：候选面 = {@code ecos_backend/**&#47;src/main/java/**} 下文件名
 * 以 {@code Impl.java} 结尾的类（排除 {@code /target/} 与 {@code /archive/}）；
 * 判死 = ①<b>剥注释后的代码面</b>不含任何 Spring 装配型注解 ②在「主源码＋测试源码＋资源文件」三个面上，
 * 除自身文件外<b>零整词引用</b>；②还要做<b>传递闭包</b>——只被同样判死的类引用的成员，引用不算数，
 * 否则「父死子活」会把整条死链误判成活面。</p>
 *
 * <p><b>两枚见证各有分工</b>：{@code SchemaRegistryServiceImpl} 的所谓引用全部写在注释里
 * （{@code DataSourceServiceImpl}／{@code DataDescriptionDiscoveryImpl} 的 javadoc），只有<b>剥注释</b>才进得来；
 * {@code DataDescriptionDiscoveryImpl} 的引用来自一枚同判死的父类，只有<b>传递闭包</b>才进得来。
 * 任一机制被摘掉，见证案即红——这是本门「能变红」的构造性证明，不是靠注释承诺。</p>
 *
 * <p><b>口径边界（写明，不装）</b>：本门是<b>类级</b>口径。{@code BaseJdbcAdapter} 不在此面内——
 * 它有子类引用、类本身是活面，死的是它内部八枚方法腿（<b>方法级</b>形态），由
 * {@code DependencyUnreadyIllegalStateRatchetTest} 的承流三档账面管账；这条口径边界由负见证案显式钉住，
 * 禁把两种形态混成一张清单。</p>
 */
class NoAssemblyEntryFaceRatchetTest {

    /** 装配型注解：命中任一即视为「有装配入口」，不入死面。 */
    private static final Set<String> ASSEMBLY_ANNOTATIONS = Set.of(
            "@Service", "@Component", "@Repository", "@Controller", "@RestController",
            "@Configuration", "@ControllerAdvice", "@Mapper");

    /**
     * 派生死面集的钉值基线（<b>只减不增</b>：消死面即下移，须书面重设；新增死面直接红）。
     * 取值只能来自本门实跑红字读数，禁算式推出——实测读数 44（#142/B4-3e-49 首次派生）。
     */
    private static final int DERIVED_DEAD_FACE_BASELINE = 44;

    /**
     * 主源码 {@code *Impl.java} 候选面下限（防扫描器失明的假绿；只设下限，新增合法）。
     * 实测候选面 #142/B4-3e-49 取红字读数＝<b>175</b> 枚（其中 44 枚判死），下限留 150 只防扫描器失明、不容纳新增。
     */
    private static final int MAIN_IMPL_FILE_FLOOR = 150;

    /** 注释面见证：只有剥注释生效才判得死。 */
    private static final String WIRE_BY_COMMENT_WITNESS =
            "ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/"
                    + "engine/data/datadescription/service/impl/SchemaRegistryServiceImpl.java";

    /** 传递死簇见证：只有传递闭包生效才判得死。 */
    private static final String TRANSITIVE_CLUSTER_WITNESS =
            "ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/"
                    + "engine/data/datadescription/discovery/impl/DataDescriptionDiscoveryImpl.java";

    /** 方法级形态的另一面：本门不得把它当类级死面重复计账。 */
    private static final String METHOD_LEVEL_FACE =
            "ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/"
                    + "engine/data/datasource/storage/adapter/jdbc/BaseJdbcAdapter.java";

    // ── 扫描面 ─────────────────────────────────────────────────────────

    private record Face(String relPath, String text) {
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

    private static String normalized(Path root, Path f) {
        return root.relativize(f).toString().replace('\\', '/');
    }

    /**
     * 引用面两档：主源码 java ＋ 资源文件（{@code xml}/{@code yml}/{@code yaml}/{@code properties}）。
     * java 取<b>剥注释后</b>代码面；资源面取<b>原文</b>——装配描述符里的命中本来就可能是真装配，
     * 注释命中只会让死面集变小（偏保守），不会把活面误判成死面。
     *
     * <p><b>测试源码被有意排除</b>，两条理由都写死在这里：①本口径问的是<b>装配入口</b>，
     * 测试里的 {@code new XxxImpl()} 不构成生产承流路径（同一判断见 3e-45 的「无 HTTP 承流路径」档与
     * D-B4-54「死测试面」教训）；②门禁台账<b>必然具名</b>每一枚死面（下面的见证常量就是），
     * 若把台账自身的具名当活引用，就会「登记即销账」——每登记一枚死面就自动把它摘出死面集，
     * 账面归零靠抄名单实现，正是本门要治的假绿形态。</p>
     */
    private static List<Face> scanReferenceFace() {
        Path root = repoRoot().resolve("ecos_backend");
        List<Face> out = new ArrayList<>();
        try (var walk = Files.walk(root)) {
            List<Path> files = new ArrayList<>();
            walk.filter(Files::isRegularFile).forEach(p -> {
                String rel = p.toString().replace('\\', '/');
                if (rel.contains("/target/") || rel.contains("/archive/") || rel.contains("/src/test/")) {
                    return;
                }
                if (rel.endsWith(".java") || rel.endsWith(".xml")
                        || rel.endsWith(".yml") || rel.endsWith(".yaml") || rel.endsWith(".properties")) {
                    files.add(p);
                }
            });
            for (Path f : files) {
                String raw = new String(Files.readAllBytes(f), StandardCharsets.UTF_8);
                out.add(new Face(normalized(root, f), f.toString().endsWith(".java")
                        ? SourceCommentStripper.strip(raw) : raw));
            }
        } catch (IOException e) {
            throw new AssertionError("扫描引用面失败: " + e.getMessage(), e);
        }
        assertFalse(out.isEmpty(), "引用面扫描结果为空＝扫描器失明，不得按「无死面」放行");
        return out;
    }

    /** 扫描面在一次 JVM 会话内算一次即可（五个门案共用，避免全仓遍历被跑五遍）。 */
    private static final class Scan {
        private static final List<Face> FACE = scanReferenceFace();
        private static final Map<String, String> CANDIDATES = implCandidates(FACE);
        private static final Set<String> DEAD = deriveDeadFaces(FACE, CANDIDATES);
    }

    private static List<Face> face() {
        return Scan.FACE;
    }

    private static Map<String, String> candidates() {
        return Scan.CANDIDATES;
    }

    private static Set<String> dead() {
        return Scan.DEAD;
    }

    /** 候选面：主源码 {@code *Impl.java}，返回「相对路径 → 剥注释代码面」。 */
    private static Map<String, String> implCandidates(List<Face> face) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Face f : face) {
            String rel = "ecos_backend/" + f.relPath();
            if (rel.contains("/src/main/java/") && rel.endsWith("Impl.java")) {
                out.put(rel, f.text());
            }
        }
        return out;
    }

    private static String simpleName(String relPath) {
        int slash = relPath.lastIndexOf('/');
        return relPath.substring(slash + 1).replace(".java", "");
    }

    /** 整词命中：前后不得是标识符字符，避免 {@code DatabaseXxxImpl} 冒充 {@code XxxImpl}。 */
    private static boolean containsWord(String text, String word) {
        for (int i = text.indexOf(word); i >= 0; i = text.indexOf(word, i + 1)) {
            boolean leftOk = i == 0 || !isWordChar(text.charAt(i - 1));
            int after = i + word.length();
            boolean rightOk = after >= text.length() || !isWordChar(text.charAt(after));
            if (leftOk && rightOk) {
                return true;
            }
        }
        return false;
    }

    private static boolean isWordChar(char c) {
        return Character.isLetterOrDigit(c) || c == '_' || c == '$';
    }

    /**
     * 派生死面集：先按「无装配注解」筛，再对「零外部整词引用」做<b>传递闭包</b>直到不动点。
     *
     * @return 相对路径 → 该成员在死面集之外发现的活引用文件（空列表＝判死）
     */
    private static Set<String> deriveDeadFaces(List<Face> face, Map<String, String> candidates) {
        Set<String> aliveByAssembly = new TreeSet<>();
        for (Map.Entry<String, String> e : candidates.entrySet()) {
            for (String annotation : ASSEMBLY_ANNOTATIONS) {
                if (e.getValue().contains(annotation)) {
                    aliveByAssembly.add(e.getKey());
                    break;
                }
            }
        }
        Set<String> suspects = new TreeSet<>(candidates.keySet());
        suspects.removeAll(aliveByAssembly);
        boolean shrunk = true;
        while (shrunk) {
            shrunk = false;
            for (String rel : new TreeSet<>(suspects)) {
                if (!liveReferences(rel, suspects, face).isEmpty()) {
                    suspects.remove(rel);
                    shrunk = true;
                }
            }
        }
        return suspects;
    }

    /** 死面集之外的整词引用文件（自身文件永远排除）。 */
    private static List<String> liveReferences(String rel, Set<String> deadSuspects, List<Face> face) {
        String word = simpleName(rel);
        List<String> hits = new ArrayList<>();
        for (Face f : face) {
            String other = "ecos_backend/" + f.relPath();
            if (other.equals(rel) || deadSuspects.contains(other)) {
                continue;
            }
            if (containsWord(f.text(), word)) {
                hits.add(other);
            }
        }
        return hits;
    }

    // ── 门案 ───────────────────────────────────────────────────────────

    @Test
    @Timeout(value = 600)
    @DisplayName("无装配入口面由判据派生并钉值：只减不增，漂即红且点名成员（D-B4-80）")
    void derivedDeadFaceSetIsNailedDownAndOnlyShrinks() {
        Set<String> dead = dead();
        assertFalse(dead.isEmpty(),
                "派生死面集为空＝「全仓无装配入口面」，这个读数只可能来自扫描器失明，不得默认通过");
        assertEquals(DERIVED_DEAD_FACE_BASELINE, dead.size(),
                () -> "实跑派生死面集 " + dead.size() + " 枚，与基线 " + DERIVED_DEAD_FACE_BASELINE
                        + " 不符。多出的成员（新增死面，必须收口或登记）＝"
                        + tail(dead, 64));
    }

    @Test
    @Timeout(value = 600)
    @DisplayName("两枚见证分工：注释面引用不算引用、父死子亦死，缺任一机制即红（D-B4-80 盲区防回归）")
    void commentMentionsAndTransitiveClustersNeitherResurrectADeadFace() {
        Set<String> dead = dead();
        assertTrue(candidates().containsKey(WIRE_BY_COMMENT_WITNESS),
                "注释面见证不在候选面内＝候选面口径（主源码 *Impl.java）被改动，本门失去意义");
        assertTrue(dead.contains(WIRE_BY_COMMENT_WITNESS),
                () -> "「" + WIRE_BY_COMMENT_WITNESS + "」的引用全部写在注释里，却未判死＝"
                        + "引用面又在拿注释冒充代码（D-B4-62 同型）。当前它在死面集之外的引用文件＝"
                        + liveReferences(WIRE_BY_COMMENT_WITNESS, dead, face()));
        assertTrue(dead.contains(TRANSITIVE_CLUSTER_WITNESS),
                () -> "「" + TRANSITIVE_CLUSTER_WITNESS + "」只被同样判死的父类构造，却未判死＝"
                        + "传递闭包被摘掉，整条死链会被误读成活面（D-B4-77 重新变成待补欠账）");
    }

    @Test
    @Timeout(value = 600)
    @DisplayName("负见证与口径边界：带装配注解的类不得入集，方法级死腿不得在类级面重复计账")
    void assemblyAnnotatedAndMethodLevelFacesStayOutOfThisLedger() {
        Set<String> dead = dead();
        assertFalse(dead.contains(DATASOURCE_IMPL_HOLDER),
                "「" + simpleName(DATASOURCE_IMPL_HOLDER) + "」带 @Service 装配注解却出现在死面集＝"
                        + "装配注解面失效，全仓每一个被扫描的 Spring Bean 都会被误报成死面");
        assertFalse(dead.contains(METHOD_LEVEL_FACE),
                "「" + simpleName(METHOD_LEVEL_FACE) + "」是<b>方法级</b>死腿形态（类本身有子类引用），"
                        + "本门是类级口径——把它计进来就是同一处欠账记两遍，"
                        + "其账面归 DependencyUnreadyIllegalStateRatchetTest 的承流三档门管");
        assertFalse(candidates().containsKey(METHOD_LEVEL_FACE),
                "方法级死腿的持有文件本不在「主源码 *Impl.java」候选面内，若它出现在面上＝候选面口径被放宽，"
                        + "类级账与方法级账将开始重复计账");
    }

    @Test
    @Timeout(value = 600)
    @DisplayName("每一枚派生成员逐条重算零活引用：集算对了不等于引用面没被低估")
    void everyDerivedMemberActuallyHasNoLiveReference() {
        Set<String> dead = dead();
        for (String rel : dead) {
            assertEquals(List.of(), liveReferences(rel, dead, face()),
                    () -> "派生成员「" + rel + "」在死面集之外仍被引用，不该算死面");
        }
    }

    @Test
    @Timeout(value = 600)
    @DisplayName("候选面自身活性：主源码 *Impl.java 扫描数不得低于下限（防扫描器失明的假绿）")
    void candidateFaceIsWideEnoughToBeBelieved() {
        assertTrue(candidates().size() >= MAIN_IMPL_FILE_FLOOR,
                () -> "主源码 *Impl.java 只扫到 " + candidates().size() + " 枚，低于下限 "
                        + MAIN_IMPL_FILE_FLOOR + "＝扫描面失明（路径口径／排除规则被动过），"
                        + "此时任何「死面为零」的读数都是假绿");
    }

    private static final String DATASOURCE_IMPL_HOLDER =
            "ecos_backend/engine/data-engine/data-engine-impl/src/main/java/com/chinacreator/gzcm/"
                    + "engine/data/datasource/service/impl/DataSourceServiceImpl.java";

    private static String tail(Set<String> items, int limit) {
        Set<String> out = new LinkedHashSet<>();
        for (String s : items) {
            if (out.size() >= limit) {
                break;
            }
            out.add(s);
        }
        return String.join(" | ", out);
    }
}
