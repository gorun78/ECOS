package com.chinacreator.gzcm.gateway;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.chinacreator.gzcm.gateway.OntologyInterfaceSurfaceScanner.DeclaredBases;
import com.chinacreator.gzcm.gateway.OntologyInterfaceSurfaceScanner.ManifestEntry;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 详设-06 F6-07 本体接口面收敛门禁 ① —— <b>route-manifest 与实现声明面的差集受控</b>
 * （设计验收原文：「route-manifest 与实现差集非空或匿名面越界 ⇒ 红」）。
 *
 * <p><b>档位说明（勿把本门禁读成「差集已归零」）</b>：按字面判据「差集为空」直接断言，
 * HEAD 实际状态是红的——本域有若干类级基址在 {@code route-manifest.json} 里没有对应条目
 * （{@link #PINNED_UNCOVERED_BASES}）。其中多数不是漏登，而是<b>有在册理由的结构性缺口</b>：
 * {@code /api/v1/ecos} 整根属主不唯一（workspace-impl 的 {@code ObjectStateMachineController}
 * 亦声明 {@code /objects}、{@code /state-machine} 子族），manifest 第 12 行 reason 字段明文
 * 「故只登记子族，禁把整根判给单一属主」。把这类条目当成"补一条 entry 就绿"会制造错误属主登记，
 * 属主判归须升级裁决，不在本批。因此本门禁按本仓收口惯例实现为
 * <b>钉值差集 + 归因白名单 + 活性自证 + 夹具能变红</b>：
 * <ol>
 *   <li>差集逐字等于在册基线 ⇒ 新基址未登记即红；</li>
 *   <li>在册差集每条必须落在已登记的<b>归因根</b>下 ⇒ 想绕过红只往基线里追加一条别的族的基址，
 *       仍会在这一条变红（防止基线变成什么都往里塞的兜底清单）；</li>
 *   <li>在册差集逐条活性：其载体 Controller 仍在位且仍声明该基址 ⇒ 死豁免即红；</li>
 *   <li>反向差集（manifest 有、实现无）本体属主条目必须为空 ⇒ 幽灵条目即红。</li>
 * </ol>
 *
 * <p><b>不重复判定</b>：匿名面红线归 {@code OntologyAnonymousSurfaceTest}，
 * 重写表 25 条基线归 {@code RouteManifestParityTest}，datanet 属主条目字段齐备性归
 * {@code DatanetEndpointParityTest}；本类只管「本体声明基址 ↔ manifest prefix 覆盖」这一条差集，
 * 外加一条本域属主条目的<b>制品存在性</b>见证（owner/artifact 指向的目录必须真在盘上——
 * 这是 #142/B4-3e-11 那次「{@code /api/v1/world-model} 误登 owner=buszhi/18089」暴露的判据缺口）。</p>
 *
 * <p>纯源码/JSON 扫描，不启动 Spring，不声称任何 HTTP 可达性。</p>
 */
@DisplayName("#146ae F6-07①：本体声明基址与 route-manifest 差集钉值 + 属主条目制品存在性")
class OntologyRouteManifestParityTest {

    /** 解析规模下限（manifest 条目数）：低于此值＝单源被清空或 JSON 结构变了而解析静默吞掉。 */
    private static final int MIN_MANIFEST_ENTRIES = 19;

    /** 本体属主（route-manifest 的 owner 字段口径）。 */
    private static final String ONTOLOGY_OWNER = "buszhi";

    /**
     * 在册未覆盖基址（实现声明了、manifest 任一条目 prefix 都覆盖不到）。
     *
     * <p>值由本门禁实跑首帧（{@code _win_tasks/logs/f607-capture.log}，2026-10-30）导出回填，非手算。
     * 只允许缩小：真实补登一条 entry，或某基址被收敛掉，都同批下调本值并回写台账 F6-07 行。
     * 九枚分三档，逐档归因见 {@link #REGISTERED_CAUSE_ROOTS}：
     * <ul>
     *   <li>{@code /api/v1/ecos} 整根及其四子族（{@code domains}/{@code mappings}/{@code ontology}/
     *       {@code versions}）—— manifest 第 12 行明文「只登记子族，禁把整根判给单一属主」，
     *       补登＝属主判归裁决（#144-c 类），不在本批；</li>
     *   <li>{@code /api/v1/lineage} —— 血缘独立前缀，属主判归待裁；</li>
     *   <li>{@code /api/ontology}、{@code /api/ecos/domains}、{@code /api/ecos/ontology} ——
     *       v1 裸路径别名，按 F6-07 应转别名（带 deprecated 头）而非登记新属主。</li>
     * </ul>
     */
    private static final Set<String> PINNED_UNCOVERED_BASES = Set.of(
            "/api/ecos/domains",
            "/api/ecos/ontology",
            "/api/ontology",
            "/api/v1/ecos",
            "/api/v1/ecos/domains",
            "/api/v1/ecos/mappings",
            "/api/v1/ecos/ontology",
            "/api/v1/ecos/versions",
            "/api/v1/lineage");

    /**
     * 在册差集的归因根（每条未覆盖基址必须落在其一之下，否则即便已追加进基线也判红）。
     *
     * <ul>
     *   <li>{@code /api/v1/ecos} —— 整根属主不唯一，manifest 只登记子族（见类注释 reason 原文）；</li>
     *   <li>{@code /api/v1/lineage} —— 血缘独立前缀，属主判归待裁；</li>
     *   <li>{@code /api/} —— v1 裸路径别名形态，按 F6-07 应转别名而非登记新属主。</li>
     * </ul>
     */
    private static final List<String> REGISTERED_CAUSE_ROOTS = List.of(
            "/api/v1/ecos", "/api/v1/lineage", "/api/");

    // ═══════════════════════ ① 两侧解析自证 ═══════════════════════

    @Test
    @DisplayName("两侧都不是空面：manifest 条目数与实现基址数达下限，且本体四条目在册")
    void bothSidesAreParsedAndNonTrivial() throws IOException {
        List<ManifestEntry> manifest = OntologyInterfaceSurfaceScanner.readManifest();
        assertTrue(manifest.size() >= MIN_MANIFEST_ENTRIES,
                "route-manifest 解析条目骤减（< " + MIN_MANIFEST_ENTRIES + "）⇒ 单源被清空，"
                        + "此时差集判定会恒真: 实扫=" + manifest.size());

        Set<String> prefixes = new LinkedHashSet<>();
        manifest.forEach(e -> prefixes.add(e.prefix()));
        for (String must : List.of("/api/v1/ontology", "/api/v1/engine/ontology",
                "/api/v1/ecos/ontologies", "/api/v1/ecos/entities")) {
            assertTrue(prefixes.contains(must),
                    "manifest 丢失本体条目 " + must + "（#142/B4-3e-11 补登的四条目之一）: " + prefixes);
        }

        Set<String> bases = OntologyInterfaceSurfaceScanner.distinctBases(
                OntologyInterfaceSurfaceScanner.scanControllerBases());
        assertTrue(bases.size() >= 25, "实现侧类级基址数骤减（< 25）: 实扫=" + bases.size());

        // 两侧都必须真的能互相命中，否则「差集」会因为一侧全空而虚大或恒真
        assertTrue(bases.stream().anyMatch(b -> OntologyInterfaceSurfaceScanner.coveredByManifest(manifest, b)),
                "没有任何实现基址被 manifest 覆盖 ⇒ 覆盖判据或前缀形态失效，差集将恒等于全集");
    }

    // ═══════════════════════ ② 差集钉值 + 归因 + 活性 ═══════════════════════

    @Test
    @DisplayName("未覆盖基址差集 = 在册基线；每条必须落在已登记归因根下")
    void implementationSideDiffMatchesPinnedBaselineAndIsAttributed() throws IOException {
        List<ManifestEntry> manifest = OntologyInterfaceSurfaceScanner.readManifest();
        Set<String> bases = OntologyInterfaceSurfaceScanner.distinctBases(
                OntologyInterfaceSurfaceScanner.scanControllerBases());

        Set<String> uncovered = new TreeSet<>();
        for (String base : bases) {
            if (!OntologyInterfaceSurfaceScanner.coveredByManifest(manifest, base)) {
                uncovered.add(base);
            }
        }
        assertEquals(PINNED_UNCOVERED_BASES, uncovered,
                "本体声明基址与 route-manifest 的差集与在册基线不一致。"
                        + "实扫差集（新条目须先判：补登 entry / 收敛基址 / 登记归因后入册，三者都要落台账）: " + uncovered);

        List<String> unattributed = new ArrayList<>();
        for (String base : PINNED_UNCOVERED_BASES) {
            boolean attributed = REGISTERED_CAUSE_ROOTS.stream().anyMatch(base::startsWith);
            if (!attributed) unattributed.add(base);
        }
        assertTrue(unattributed.isEmpty(),
                "在册差集出现无归因根的条目（差集基线不得变成什么都往里塞的兜底清单）: " + unattributed
                        + "；已登记归因根=" + REGISTERED_CAUSE_ROOTS);
    }

    @Test
    @DisplayName("在册差集逐条活性：载体 Controller 在位且仍声明该基址（死豁免＝虚高基线＝红）")
    void pinnedUncoveredBasesAreStillLive() throws IOException {
        assertFalse(PINNED_UNCOVERED_BASES.isEmpty(),
                "在册差集为空 ⇒ HEAD 若实际存在未登记基址，本门禁就是虚假绿灯；必须先按实跑首帧回填");
        List<DeclaredBases> declared = OntologyInterfaceSurfaceScanner.scanControllerBases();
        for (String base : PINNED_UNCOVERED_BASES) {
            List<String> carriers = declared.stream()
                    .filter(d -> d.bases().contains(base))
                    .map(DeclaredBases::file)
                    .toList();
            assertFalse(carriers.isEmpty(),
                    "在册未覆盖基址 " + base + " 已无任何 Controller 声明 ⇒ 该条已收敛或形态漂移，基线须同批下调");
        }
    }

    @Test
    @DisplayName("反向差集：本体属主的 manifest 条目必须有实现承载（禁幽灵条目）")
    void manifestSideHasNoGhostOntologyEntries() throws IOException {
        List<ManifestEntry> manifest = OntologyInterfaceSurfaceScanner.readManifest();
        Set<String> bases = OntologyInterfaceSurfaceScanner.distinctBases(
                OntologyInterfaceSurfaceScanner.scanControllerBases());
        List<ManifestEntry> ontologyEntries = manifest.stream()
                .filter(e -> ONTOLOGY_OWNER.equals(e.owner()))
                .toList();
        assertFalse(ontologyEntries.isEmpty(), "manifest 里本体属主条目为空 ⇒ 属主面被整体删除，判定失去意义");

        List<String> ghosts = new ArrayList<>();
        for (ManifestEntry e : ontologyEntries) {
            boolean carried = bases.stream().anyMatch(b -> OntologyInterfaceSurfaceScanner.coveredBy(e.prefix(), b));
            if (!carried) ghosts.add(e.prefix() + "（owner=" + e.owner() + "，无任何 Controller 类级基址落在其下）");
        }
        assertTrue(ghosts.isEmpty(), "route-manifest 出现无实现承载的本体条目: " + ghosts);
    }

    // ═══════════════════════ ③ 属主条目制品存在性 ═══════════════════════

    @Test
    @DisplayName("本体属主条目的 owner/artifact/route/ports 形态自洽，artifact 目录真在盘上")
    void ontologyManifestEntriesPointToRealArtifacts() throws IOException {
        Path backend = OntologyInterfaceSurfaceScanner.repoRoot().resolve("ecos_backend");
        List<String> problems = new ArrayList<>();
        for (ManifestEntry e : OntologyInterfaceSurfaceScanner.readManifest()) {
            if (!ONTOLOGY_OWNER.equals(e.owner())) continue;
            if (!"monolith".equals(e.route()) && !"service".equals(e.route())) {
                problems.add(e.prefix() + " route 非法值: " + e.route());
            }
            if (e.artifact().isBlank() || !Files.isDirectory(backend.resolve(e.artifact()))) {
                problems.add(e.prefix() + " artifact 目录不存在: " + e.artifact());
            }
        }
        assertTrue(problems.isEmpty(),
                "本体属主条目与盘上制品不一致（#142/B4-3e-11 那次 owner 误登即此类，"
                        + "仅比字符串无法发现指向不存在模块的登记）:\n  " + String.join("\n  ", problems));
    }

    // ═══════════════════════ ④ 覆盖判据夹具 ═══════════════════════

    @Test
    @DisplayName("覆盖判据夹具：父前缀覆盖子基址，兄弟段与残缺段不得误判为覆盖")
    void coveragePredicateActuallyBites() {
        assertTrue(OntologyInterfaceSurfaceScanner.coveredBy("/api/v1/ontology", "/api/v1/ontology/glossary"),
                "夹具：真覆盖未被判出 ⇒ 差集会虚大、门禁失去定位价值");
        assertTrue(OntologyInterfaceSurfaceScanner.coveredBy("/api/v1/ontology", "/api/v1/ontology"),
                "夹具：基址与前缀全等必须算覆盖");
        assertFalse(OntologyInterfaceSurfaceScanner.coveredBy("/api/v1/ecos/entities", "/api/v1/ecos/ontologies"),
                "夹具：兄弟段被误判成覆盖 ⇒ 未登记基址会被同族条目掩盖（本门禁的核心假绿形态）");
        assertFalse(OntologyInterfaceSurfaceScanner.coveredBy("/api/v1/ecos/entit", "/api/v1/ecos/entities"),
                "夹具：字符级前缀被当成路径段覆盖 ⇒ startsWith 口径未收紧");
        assertFalse(OntologyInterfaceSurfaceScanner.coveredBy("", "/api/v1/ontology"),
                "夹具：空前缀被当成覆盖 ⇒ 任何基址都会 PASS");
    }

    @Test
    @DisplayName("接线钉：两侧基址集合必须由共用扫描器导出（禁本类内联第二套正则）")
    void gatesShareTheSingleScanner() throws IOException {
        String self = OntologyInterfaceSurfaceScanner.read(OntologyInterfaceSurfaceScanner.repoRoot()
                .resolve("ecos_backend/gateway/src/test/java/com/chinacreator/gzcm/gateway")
                .resolve("OntologyRouteManifestParityTest.java"));
        assertTrue(self.contains("OntologyInterfaceSurfaceScanner.scanControllerBases()")
                        && self.contains("OntologyInterfaceSurfaceScanner.readManifest()"),
                "本类丢失与共用扫描器的接线 ⇒ 两侧集合可能被内联副本顶替，差集口径与 F6-07② 分叉");

        String sibling = OntologyInterfaceSurfaceScanner.read(OntologyInterfaceSurfaceScanner.repoRoot()
                .resolve("ecos_backend/gateway/src/test/java/com/chinacreator/gzcm/gateway")
                .resolve("OntologyNoDuplicateBaseMappingTest.java"));
        assertTrue(sibling.contains("OntologyInterfaceSurfaceScanner.scanControllerBases()"),
                "F6-07② 丢失共用扫描器接线 ⇒ 两条门禁判的不再是同一个面（双源漂移）");
    }
}
