package com.chinacreator.gzcm.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chinacreator.gzcm.gateway.OntologyInterfaceSurfaceScanner.DeclaredBases;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 详设-06 F6-07 本体接口面收敛门禁 ② —— <b>同基址双 Controller 零新增</b>
 * （设计验收原文见差距分析 §F6/详设-06 收敛条：规范前缀 {@code /api/v1/ontology/**} +
 * 职责子族 {@code git}/{@code copilot}，其余前缀转别名并带 deprecated 头）。
 *
 * <p><b>判据的档位（关键，别把本门禁读成「已收敛」）</b>：F6-07 的收敛目标是
 * 「一个基址一支 Controller」。按该字面判据直接断言「重复基址集为空」，在 HEAD 实际状态上是红的
 * ——本域 37 支 Controller 里存在多组同基址拆分（见 {@link #PINNED_DUPLICATE_BASE_GROUPS}）。
 * 收敛本身是 <b>P1 待办</b>，不在本批范围（合并 Controller 类＝动既有对外形态，须另批裁决）。
 * 因此本门禁按本仓既有「钉值基线 + 只能减」的收口形态实现：
 * <ol>
 *   <li><b>钉值等式</b>：重复基址分组集合必须<b>逐字</b>等于在册基线。新增一支与在册基址同基的
 *       Controller ⇒ 集合变大 ⇒ 立刻变红（这是本门禁当下的实际约束力：禁止债务继续增殖）。</li>
 *   <li><b>在册组必须仍活</b>（{@link #pinnedDuplicateGroupsAreStillLive()}）：每条在册分组的
 *       每个成员文件仍在位、且其解析结果仍含该基址。成员被删/改基址而忘了下调基线 ⇒ 变红
 *       ＝死豁免即红（同 {@code OntologyAnonymousSurfaceTest} 惰性自证先例）。</li>
 *   <li><b>判据自身能变红</b>（{@link #duplicateDetectorActuallyBites()}）：合成夹具一对同基址
 *       必须被检出，一对父子前缀必须<b>不</b>被检出。否则「恒不检出」的实现也能让上面两条绿。</li>
 * </ol>
 *
 * <p><b>别名面（F6-07 的「转别名 + deprecated 头」半条）</b>：本域有 Controller 把
 * {@code /api/v1/...} 与裸 {@code /api/...} 双字面量并列声明（数组形态）。F6-07 要求这类别名
 * 带 deprecated 头并保留 ≥2 迭代，实扫 <b>0 处</b>带弃用承载（{@link #aliasCarriersArePinnedButCarryNoDeprecationFace()}）。
 * 本门禁把「别名载体集合」钉成在册基线、把「弃用承载面为零」写成<b>可跑观测</b>（检测器由合成夹具自证有效），
 * 而不是断言「必须为零」的成长门——否则收敛推进反而会把门禁变红。零承载这一事实进台账欠账行，不外推为已收敛。</p>
 *
 * <p><b>不主张</b>：可达性、承流态、鉴权面（鉴权面归 {@code OntologyAnonymousSurfaceTest} 三红线，
 * 禁在此重复判定造成双源）。</p>
 *
 * <p>纯源码扫描，不启动 Spring。设计验收命令原文：
 * {@code mvn -pl gateway -Dtest='OntologyNoDuplicateBaseMappingTest'}。</p>
 */
@DisplayName("#146ae F6-07②：本体域同基址双 Controller 面钉值基线（禁增殖）+ 别名面弃用承载可跑观测")
class OntologyNoDuplicateBaseMappingTest {

    /** 解析规模下限（Controller 文件数）：低于此值＝目录布局或解析器失效，各判定会退化成恒真。 */
    private static final int MIN_SCANNED_CONTROLLER_FILES = 35;

    /** 解析规模下限（去重后的类级基址数）。 */
    private static final int MIN_DISTINCT_BASES = 25;

    /**
     * 在册同基址分组基线（{@code 基址 -> [成员文件升序]} 的逐字形态）。
     *
     * <p>本值由本门禁<b>实跑首帧</b>（{@code _win_tasks/logs/f607-capture.log}，2026-10-30）
     * 导出的 surefire 断言消息回填，非手算。只允许缩小：每次真实收敛（合并 Controller 或改基址）
     * 同批下调本值并回写台账差距分析 F6-07 行；新增分组＝违例，直接把红留在 CI。</p>
     *
     * <p>六组的性质逐组登记，避免「基线＝一串无名条目」：
     * <ol>
     *   <li>{@code /api/v1/ecos}（5 支）—— 整根多职责拆分，属主判归受 manifest 第 12 行
     *       「禁把整根判给单一属主」约束，收敛须与 F6-07① 的差集归因同批改；</li>
     *   <li>{@code /api/v1/ontology}（3 支）与 {@code /api/ontology}（2 支）—— 规范前缀上
     *       的领域/实体映射/版本比对三职责 + v1 兼容别名重叠；</li>
     *   <li>{@code /api/v1/ecos/ontologies}（3 支）与 {@code /api/v1/ontology/glossary}（3 支）——
     *       定义族与术语族的职责拆分；</li>
     *   <li>{@code /api/v1/ecos/domains}（2 支）—— 领域 CRUD 与自动发现预览双载体。</li>
     * </ol>
     */
    private static final Set<String> PINNED_DUPLICATE_BASE_GROUPS = Set.of(
            "/api/ontology -> [OntologyCompatController.java, VersionDiffController.java]",
            "/api/v1/ecos -> [AutoDiscoverController.java, OntologyActionController.java, "
                    + "OntologyEntityGlobalController.java, OntologyRelationshipController.java, "
                    + "OntologyRuleController.java]",
            "/api/v1/ecos/domains -> [AutoDiscoverPreviewController.java, OntologyDomainController.java]",
            "/api/v1/ecos/ontologies -> [OntologyController.java, OntologyVersionController.java, "
                    + "OntologyWorkbenchObjectController.java]",
            "/api/v1/ontology -> [OntologyDomainApiController.java, OntologyEntityMappingController.java, "
                    + "VersionDiffController.java]",
            "/api/v1/ontology/glossary -> [GlossaryController.java, GlossaryGraphController.java, "
                    + "GlossaryRelationController.java]");

    /**
     * 在册别名载体（同一 Controller 类级声明里 {@code /api/v1/} 与裸 {@code /api/} 双形态并列）。
     * 同样由实跑首帧导出；F6-07 要求这类别名带 deprecated 头，见
     * {@link #aliasCarriersArePinnedButCarryNoDeprecationFace()} 的零承载观测。
     */
    private static final Set<String> PINNED_ALIAS_CARRIERS = Set.of(
            "AutoDiscoverPreviewController.java [/api/v1/ecos/domains, /api/ecos/domains]",
            "OntologySourceController.java [/api/v1/ecos/ontology, /api/ecos/ontology]",
            "VersionDiffController.java [/api/v1/ontology, /api/ontology]");

    /** 弃用承载判据：Controller 源码里出现任一形态即视为「别名已带弃用标识」。 */
    private static final List<Pattern> DEPRECATION_FACES = List.of(
            Pattern.compile("\\bDeprecation\\b"),
            Pattern.compile("\\bSunset\\b"),
            Pattern.compile("X-Deprecated", Pattern.CASE_INSENSITIVE),
            Pattern.compile("\\bdeprecated\\s*=\\s*true"),
            Pattern.compile("@Deprecated\\b"));

    // ═══════════════════════ ① 解析面自证 ═══════════════════════

    @Test
    @DisplayName("解析面非平凡：Controller 文件数与基址数达到下限，且关键基址在册（防「扫不到 ⇒ 门禁恒真」）")
    void parseUniverseIsNonTrivial() throws IOException {
        List<DeclaredBases> declared = OntologyInterfaceSurfaceScanner.scanControllerBases();
        assertTrue(declared.size() >= MIN_SCANNED_CONTROLLER_FILES,
                "本体 Controller 解析数骤减（< " + MIN_SCANNED_CONTROLLER_FILES + "）⇒ 目录/解析器失效，"
                        + "此时重复分组与别名分组都可能恒真: 实扫=" + declared.size());

        Set<String> bases = OntologyInterfaceSurfaceScanner.distinctBases(declared);
        assertTrue(bases.size() >= MIN_DISTINCT_BASES,
                "类级基址去重数骤减（< " + MIN_DISTINCT_BASES + "）: 实扫=" + bases.size() + " " + bases);

        for (String must : List.of("/api/v1/ontology", "/api/v1/ontology/glossary",
                "/api/v1/ontology/calibers", "/api/v1/engine/ontology",
                "/api/v1/engine/ontology/git", "/api/v1/engine/ontology/copilot",
                "/api/v1/ecos/ontologies")) {
            assertTrue(bases.contains(must),
                    "解析结果丢失关键基址 " + must + "（F6-07 规范前缀与职责子族的锚点）: 实扫=" + bases);
        }
        // 注释噪音必须已被剥掉：Javadoc 里出现过的路径不得进基址集
        assertFalse(bases.contains("/api/v1/..."), "注释未剥离：Javadoc 占位路径混入基址集: " + bases);
    }

    // ═══════════════════════ ② 同基址分组钉值 ═══════════════════════

    @Test
    @DisplayName("同基址分组集合 = 在册基线（新增分组即红；缩小须同批下调基线并回写台账）")
    void duplicateBaseGroupsMatchPinnedBaseline() throws IOException {
        Map<String, List<String>> groups =
                OntologyInterfaceSurfaceScanner.duplicateBaseGroups(OntologyInterfaceSurfaceScanner.scanControllerBases());
        assertEquals(PINNED_DUPLICATE_BASE_GROUPS, render(groups),
                "本体域同基址双/多 Controller 分组与在册基线不一致。"
                        + "实扫全集（供对账，勿直接抄进基线——先判是增殖还是收敛）:\n  "
                        + String.join("\n  ", render(groups)));
    }

    @Test
    @DisplayName("在册分组必须仍活：成员文件在位且仍声明该基址（死豁免＝基线虚高＝红）")
    void pinnedDuplicateGroupsAreStillLive() throws IOException {
        assertFalse(PINNED_DUPLICATE_BASE_GROUPS.isEmpty(),
                "在册基线为空 ⇒ 本门禁只剩「零重复」的字面主张；HEAD 实际存在重复分组，"
                        + "空基线即为虚假绿灯，必须先按实跑首帧回填");
        List<DeclaredBases> declared = OntologyInterfaceSurfaceScanner.scanControllerBases();
        Map<String, List<String>> groups = OntologyInterfaceSurfaceScanner.duplicateBaseGroups(declared);
        Set<String> files = new LinkedHashSet<>();
        for (DeclaredBases d : declared) {
            files.add(d.file());
        }
        for (String pinned : PINNED_DUPLICATE_BASE_GROUPS) {
            int arrow = pinned.indexOf(" -> [");
            assertTrue(arrow > 0, "在册分组形态异常（应 '基址 -> [A, B]'）: " + pinned);
            String base = pinned.substring(0, arrow);
            List<String> live = groups.get(base);
            assertTrue(live != null && render(Map.of(base, live)).contains(pinned),
                    "在册分组已失真（基址 " + base + " 的当前分组与在册逐字不符）⇒ 基线该下调: 在册=" + pinned
                            + " 实扫=" + (live == null ? "该基址不再重复" : live));
            // 成员级活性：文件仍在位，且其解析基址仍含该基址
            List<String> stale = new ArrayList<>();
            for (String member : membersOf(pinned)) {
                boolean hit = declared.stream()
                        .filter(d -> d.file().equals(member))
                        .anyMatch(d -> d.bases().contains(base));
                if (!hit) {
                    stale.add(member + "（" + (files.contains(member) ? "文件在位但不再声明该基址" : "文件缺失") + "）");
                }
            }
            assertTrue(stale.isEmpty(), "在册分组成员失效: " + stale + " ∈ " + pinned);
        }
    }

    // ═══════════════════════ ③ 判据能变红 ═══════════════════════

    @Test
    @DisplayName("分组判据夹具自证：同基址必检出，父子前缀必不检出（禁「恒不检出」式假绿）")
    void duplicateDetectorActuallyBites() {
        List<DeclaredBases> fixture = List.of(
                new DeclaredBases("AlphaController.java", List.of("/api/v1/ontology/glossary")),
                new DeclaredBases("BetaController.java", List.of("/api/v1/ontology/glossary", "/api/glossary")),
                new DeclaredBases("GammaController.java", List.of("/api/v1/engine/ontology/git")),
                new DeclaredBases("DeltaController.java", List.of("/api/v1/engine/ontology")));
        Map<String, List<String>> groups = OntologyInterfaceSurfaceScanner.duplicateBaseGroups(fixture);

        assertEquals(List.of("AlphaController.java", "BetaController.java"), groups.get("/api/v1/ontology/glossary"),
                "夹具：同基址两支未被检出 ⇒ 分组判据失效");
        assertFalse(groups.containsKey("/api/v1/engine/ontology/git"),
                "夹具：父子前缀（/git 与父基址）被误判成重复 ⇒ 会把 F6-07 的正解职责子族一并否掉");
        assertEquals(Set.of("/api/v1/ontology/glossary"), groups.keySet(),
                "夹具分组集超出预期，判据口径漂移");

        // 别名形态解析夹具：数组字面量必须两个都进基址集
        List<String> dual = OntologyInterfaceSurfaceScanner.classLevelBasesOf(
                "@RestController\n@RequestMapping({\"/api/v1/ontology\", \"/api/ontology\"})\npublic class FixtureController {}");
        assertEquals(List.of("/api/v1/ontology", "/api/ontology"), dual,
                "数组形态类级映射未全展开（只取首枚会让别名面隐形，D-B4-47 同型盲区）");
    }

    // ═══════════════════════ ④ 别名面与弃用承载（可跑观测） ═══════════════════════

    @Test
    @DisplayName("别名载体集合 = 在册基线；弃用承载面实扫为零（检测器由夹具证明有效，零承载进台账欠账）")
    void aliasCarriersArePinnedButCarryNoDeprecationFace() throws IOException {
        List<DeclaredBases> declared = OntologyInterfaceSurfaceScanner.scanControllerBases();
        Set<String> carriers = new TreeSet<>();
        for (DeclaredBases d : declared) {
            boolean v1 = d.bases().stream().anyMatch(b -> b.startsWith("/api/v1/"));
            boolean bare = d.bases().stream().anyMatch(b -> b.startsWith("/api/") && !b.startsWith("/api/v1/"));
            if (v1 && bare) carriers.add(d.file() + " " + d.bases());
        }
        assertEquals(PINNED_ALIAS_CARRIERS, carriers,
                "本体域「双形态别名」载体集合与在册基线不一致（新增别名载体＝违 F6-07「其余前缀转别名」的受控形态，"
                        + "删除/改名载体＝基线需同批下调）。实扫: " + carriers);

        Set<String> withDeprecation = new TreeSet<>();
        for (DeclaredBases d : declared) {
            String code = OntologyInterfaceSurfaceScanner.stripComments(
                    OntologyInterfaceSurfaceScanner.read(
                            OntologyInterfaceSurfaceScanner.repoRoot()
                                    .resolve("ecos_backend")
                                    .resolve(OntologyInterfaceSurfaceScanner.ONTOLOGY_CONTROLLER_DIR_REL)
                                    .resolve(d.file())));
            if (hasDeprecationFace(code)) withDeprecation.add(d.file());
        }
        // F6-07 要求别名面带 deprecated 头；HEAD 实扫为零 ⇒ 断言「观测值 = 0」并把零值送进台账欠账行。
        assertEquals(0, withDeprecation.size(),
                "本体 Controller 出现弃用承载面（F6-07 别名收敛推进中，属预期方向）⇒ 本观测值需随台账一起改档: "
                        + withDeprecation);

        // 检测器有效性：夹具必须能被认出带弃用承载，否则上面的「零」是恒零假绿。
        // 注释形态走「先剥注释再判定」这条真实调用链（与上方实扫同一组合），否则 Javadoc 里的字样会误判。
        assertFalse(hasDeprecationFace(
                        OntologyInterfaceSurfaceScanner.stripComments(
                                "@RequestMapping(\"/api/ontology\") // Deprecation header here")),
                "夹具：注释内的 Deprecation 字样经「剥注释→判定」链后仍被认成承载面 ⇒ 计数口径会把文档提及算成弃用头");
        assertTrue(hasDeprecationFace("response.setHeader(\"Deprecation\", \"true\");"),
                "夹具：真实弃用头写法未被检出 ⇒ 「实扫为零」结论不可信");
        assertTrue(hasDeprecationFace("@Deprecated\npublic class Fixture {}"),
                "夹具：Java 侧 @Deprecated 标记未被检出 ⇒ 计数口径缺一形态");
    }

    // ═══════════════════════ 辅助 ═══════════════════════

    private static boolean hasDeprecationFace(String code) {
        for (Pattern p : DEPRECATION_FACES) {
            Matcher m = p.matcher(code);
            if (m.find()) return true;
        }
        return false;
    }

    private static List<String> membersOf(String pinned) {
        int l = pinned.indexOf("[");
        int r = pinned.lastIndexOf("]");
        List<String> out = new ArrayList<>();
        if (l < 0 || r < l) return out;
        for (String part : pinned.substring(l + 1, r).split(",")) {
            String v = part.trim();
            if (!v.isEmpty()) out.add(v);
        }
        return out;
    }

    private static Set<String> render(Map<String, List<String>> groups) {
        Set<String> out = new TreeSet<>();
        groups.forEach((base, members) -> out.add(base + " -> " + members));
        return out;
    }
}
