package com.chinacreator.gzcm.engine.ontology;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import com.chinacreator.gzcm.engine.ontology.dto.OntologyVersionVO;
import com.chinacreator.gzcm.engine.ontology.dto.VersionContentSource;
import com.chinacreator.gzcm.engine.ontology.git.OntologyGitReadResult;
import com.chinacreator.gzcm.engine.ontology.model.OntologyVersion;
import com.chinacreator.gzcm.engine.ontology.repository.GitArchivePendingRepository;
import com.chinacreator.gzcm.engine.ontology.repository.OntologyRepository;
import com.chinacreator.gzcm.engine.ontology.repository.OntologyVersionRepository;
import com.chinacreator.gzcm.engine.ontology.service.OntologyProposalService;
import com.chinacreator.gzcm.engine.ontology.service.OntologyVersionService;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;

/**
 * #147T 版本读面分态门禁 —— 列表面 = <b>版本索引面</b>，单行/diff 面 = 内容面。
 *
 * <p>裁决依据（册 §2.9.6④/DQ-6 + 铁律 #8「{@code {asset}_version} 表降级为版本索引」）：
 * 列表面若内联内容，已归档行须逐行读 Git 对象库、行数无上界（{@code findAll} 面跨全部本体），
 * 而 §2.9.5② 四版本概念里历史内容只在「按 ref 检回」的单行面出现。前端列表契约
 * {@code ontologyVersionApi.VersionItem} 本就无 {@code snapshot} 字段、内容一律走
 * {@code fetchVersionDetail}，故索引化不削减既有能力。</p>
 *
 * <p>判据面（每条都是「能变红」的行为断言，禁靠 grep 字面量凑绿）：</p>
 * <ul>
 *   <li>列表面：{@code snapshot} 不内联、{@code gitRef} 与 {@code contentSource} 在位、
 *       <b>零次</b> Git 读（成本裁决被钉住，不是只靠注释声明）；</li>
 *   <li>单行面：已归档 ⇒ 内容取自 Git 且 {@code contentSource=GIT}；
 *       Git 读回失败 ⇒ {@code DB_FALLBACK} + 原因上契约面（禁静默兜底）；
 *       未归档 ⇒ {@code DB_IN_USE} 且不触 Git；
 *       Git 通道未装配 ⇒ 归档行也只能标 {@code DB_IN_USE}（禁凭 git_ref 有值就宣称内容来自 Git）；</li>
 *   <li>diff 面：每侧<b>恰好一次</b> Git 读（本批修掉「解析调两遍 ⇒ 一次 diff 打 4 趟对象库」）。</li>
 * </ul>
 *
 * <p>全走 Mockito，不启 Spring 上下文。</p>
 */
class OntologyVersionIndexFaceGateTest {

    private OntologyVersionRepository versionRepo;
    private OntologyGitService gitService;
    private OntologyVersionService svc;

    @BeforeEach
    void setUp() {
        versionRepo = mock(OntologyVersionRepository.class);
        gitService = mock(OntologyGitService.class);
        svc = new OntologyVersionService(
            versionRepo, mock(OntologyRepository.class), mock(OntologyProposalService.class),
            mock(ApplicationEventPublisher.class), Optional.<EventBusService>empty(),
            Optional.of(gitService), Optional.of(mock(GitArchivePendingRepository.class)));
    }

    /** 版本行：snapshot 为非法 JSON ⇒ safeParseJson 原样返回字符串，便于断言来源。 */
    private static OntologyVersion row(String id, String versionNo, String gitRef) {
        OntologyVersion v = new OntologyVersion();
        v.setId(id);
        v.setOntologyId("onto-1");
        v.setVersionNo(versionNo);
        v.setStatus(gitRef == null ? "Draft" : "Published");
        v.setSnapshot("{db-snapshot:'" + versionNo + "'}");
        v.setGitRef(gitRef);
        return v;
    }

    private static OntologyGitReadResult gitHit(String content) {
        OntologyGitReadResult r = new OntologyGitReadResult();
        r.setStatus(OntologyGitReadResult.STATUS_FOUND);
        r.setContent(content);
        return r;
    }

    private static OntologyGitReadResult gitMiss(String reason) {
        OntologyGitReadResult r = new OntologyGitReadResult();
        r.setStatus(OntologyGitReadResult.STATUS_NOT_AVAILABLE);
        r.setReason(reason);
        return r;
    }

    // ═══════════ 列表面 = 索引面（零 Git 读） ═══════════

    @Test
    @DisplayName("列表面：不内联快照、保留 gitRef/contentSource，且一次 Git 读都不发")
    void listFaceIsIndexOnlyAndIssuesZeroGitReads() {
        when(versionRepo.findByOntology("onto-1")).thenReturn(List.of(
            row("ver1", "1.0.0", "ref-a"), row("ver2", "1.0.1", "ref-b"), row("ver3", "1.0.2", null)));

        List<OntologyVersionVO> list = svc.listVersionsVO("onto-1");

        assertEquals(3, list.size());
        for (OntologyVersionVO vo : list) {
            assertNull(vo.getSnapshot(), "列表面属版本索引面，禁内联快照内容：" + vo.getVersionNo());
            assertTrue(vo.getContentSource() == VersionContentSource.GIT
                    || vo.getContentSource() == VersionContentSource.DB_IN_USE,
                "索引行必须标注内容所在地，禁 null：" + vo.getVersionNo());
        }
        assertEquals("ref-a", list.get(0).getGitRef(), "归档引用是索引面的定位键，禁随内容一起省略");
        assertEquals(VersionContentSource.GIT, list.get(0).getContentSource());
        assertEquals(VersionContentSource.DB_IN_USE, list.get(2).getContentSource(),
            "未归档行内容仍在 DB 快照列（过渡态），不得标 GIT");
        verify(gitService, never()).readVersionAtRef(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("列表面（Map 轨）：snapshot 键保留但置空，禁结构性删除键位")
    void listMapFaceKeepsKeyButNotContent() {
        when(versionRepo.findByOntology("onto-1")).thenReturn(List.of(row("ver1", "1.0.0", "ref-a")));

        List<Map<String, Object>> list = svc.listVersions("onto-1");

        Map<String, Object> row0 = list.get(0);
        assertTrue(row0.containsKey("snapshot"), "键集只增不改：键须在位，值按裁决为空");
        assertNull(row0.get("snapshot"));
        assertEquals("GIT", row0.get("contentSource"));
        assertEquals("ref-a", row0.get("gitRef"));
        verify(gitService, never()).readVersionAtRef(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("跨本体全量列表面（findAll，行数无上界）同样零 Git 读")
    void crossOntologyListFaceAlsoIndexOnly() {
        when(versionRepo.findAll()).thenReturn(List.of(
            row("v1", "1.0.0", "r1"), row("v2", "1.0.1", "r2"),
            row("v3", "1.0.2", "r3"), row("v4", "1.0.3", "r4")));

        List<OntologyVersionVO> all = svc.listAllVersionsVO();

        assertEquals(4, all.size());
        assertTrue(all.stream().allMatch(v -> v.getSnapshot() == null));
        verify(gitService, never()).readVersionAtRef(any(), any(), any(), any(), any());
    }

    // ═══════════ 单行面 = 内容面（来源上契约） ═══════════

    @Test
    @DisplayName("单行面：已归档行内容取自 Git 且标 contentSource=GIT")
    void detailFaceResolvesFromGitAndStampsSource() {
        OntologyVersion v = row("ver1", "2.0.0", "ref-x");
        when(versionRepo.findById("ver1")).thenReturn(Optional.of(v));
        when(gitService.readVersionAtRef("ontology-model", "ontology", "onto-1", "2.0.0", "ref-x"))
            .thenReturn(gitHit("{\"from\":\"git-x\"}"));

        OntologyVersionVO vo = svc.getVersionVO("onto-1", "ver1");

        assertEquals(Map.of("from", "git-x"), vo.getSnapshot(), "内容必须是 Git 对象库读回值");
        assertEquals(VersionContentSource.GIT, vo.getContentSource());
        assertNull(vo.getContentReason());
    }

    @Test
    @DisplayName("单行面：Git 读回失败 ⇒ DB_FALLBACK + 原因上契约（禁静默兜底）")
    void detailFaceStampsFallbackReason() {
        OntologyVersion v = row("ver1", "2.0.0", "ref-x");
        when(versionRepo.findById("ver1")).thenReturn(Optional.of(v));
        when(gitService.readVersionAtRef("ontology-model", "ontology", "onto-1", "2.0.0", "ref-x"))
            .thenReturn(gitMiss("object not found in pack"));

        OntologyVersionVO vo = svc.getVersionVO("onto-1", "ver1");

        assertEquals("{db-snapshot:'2.0.0'}", vo.getSnapshot(), "兜底仍回 DB，但必须带来源标记");
        assertEquals(VersionContentSource.DB_FALLBACK, vo.getContentSource());
        assertEquals("object not found in pack", vo.getContentReason(),
            "兜底原因必须出契约面，否则「历史已切 Git」无法被外部证伪");
    }

    @Test
    @DisplayName("单行面（Map 轨）：contentSource/contentReason 两键在位")
    void detailMapFaceCarriesSourceAndReason() {
        OntologyVersion v = row("ver1", "2.0.0", "ref-x");
        when(versionRepo.findById("ver1")).thenReturn(Optional.of(v));
        when(gitService.readVersionAtRef("ontology-model", "ontology", "onto-1", "2.0.0", "ref-x"))
            .thenReturn(gitMiss("repo root unresolved"));

        Map<String, Object> got = svc.getVersion("onto-1", "ver1");

        assertEquals("DB_FALLBACK", got.get("contentSource"));
        assertEquals("repo root unresolved", got.get("contentReason"));
    }

    @Test
    @DisplayName("单行面：未归档行 ⇒ DB_IN_USE 且不触 Git")
    void detailFaceOnUnarchivedRowNeverTouchesGit() {
        when(versionRepo.findById("ver3")).thenReturn(Optional.of(row("ver3", "1.0.2", null)));

        OntologyVersionVO vo = svc.getVersionVO("onto-1", "ver3");

        assertEquals("{db-snapshot:'1.0.2'}", vo.getSnapshot());
        assertEquals(VersionContentSource.DB_IN_USE, vo.getContentSource());
        verify(gitService, never()).readVersionAtRef(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("Git 通道未装配：归档行也只标 DB_IN_USE（禁凭 git_ref 有值就宣称来自 Git）")
    void absentGitChannelIsNotClaimedAsGitSourced() {
        OntologyVersionService noChannel = new OntologyVersionService(
            versionRepo, mock(OntologyRepository.class), mock(OntologyProposalService.class),
            mock(ApplicationEventPublisher.class), Optional.<EventBusService>empty(),
            Optional.<OntologyGitService>empty(), Optional.of(mock(GitArchivePendingRepository.class)));
        when(versionRepo.findById("ver1")).thenReturn(Optional.of(row("ver1", "2.0.0", "ref-x")));

        Map<String, Object> got = noChannel.getVersion("onto-1", "ver1");

        assertEquals("DB_IN_USE", got.get("contentSource"),
            "通道缺席时内容实际来自 DB；若此处标 GIT 即为假绿");
    }

    // ═══════════ diff 面：每侧恰好一次 Git 读 ═══════════

    @Test
    @DisplayName("diff 面：两侧各一次 Git 读（本批修掉一次 diff 打 4 趟对象库）")
    void diffReadsGitExactlyOncePerSide() {
        OntologyVersion a = row("v1", "1.0.0", "ref-a");
        OntologyVersion b = row("v2", "1.0.1", "ref-b");
        when(versionRepo.findById("v1")).thenReturn(Optional.of(a));
        when(versionRepo.findById("v2")).thenReturn(Optional.of(b));
        when(gitService.readVersionAtRef("ontology-model", "ontology", "onto-1", "1.0.0", "ref-a"))
            .thenReturn(gitHit("{\"from\":\"git-a\"}"));
        when(gitService.readVersionAtRef("ontology-model", "ontology", "onto-1", "1.0.1", "ref-b"))
            .thenReturn(gitHit("{\"from\":\"git-b\"}"));

        Map<String, Object> diff = svc.diff("onto-1", "v1", "v2");

        assertEquals(Map.of("from", "git-a"), diff.get("snapshot1"));
        assertEquals(Map.of("from", "git-b"), diff.get("snapshot2"));
        verify(gitService, times(2)).readVersionAtRef(any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("diffVO 与 Map 轨同源：两侧内容一致")
    void diffVoTracksSameSourceAsMapFace() {
        OntologyVersion a = row("v1", "1.0.0", "ref-a");
        OntologyVersion b = row("v2", "1.0.1", "ref-b");
        when(versionRepo.findById("v1")).thenReturn(Optional.of(a));
        when(versionRepo.findById("v2")).thenReturn(Optional.of(b));
        when(gitService.readVersionAtRef("ontology-model", "ontology", "onto-1", "1.0.0", "ref-a"))
            .thenReturn(gitHit("{\"from\":\"git-a\"}"));
        when(gitService.readVersionAtRef("ontology-model", "ontology", "onto-1", "1.0.1", "ref-b"))
            .thenReturn(gitHit("{\"from\":\"git-b\"}"));

        OntologyVersionVO vo = svc.diffVO("onto-1", "v1", "v2");

        assertEquals(Map.of("from", "git-a"), vo.getSnapshot1(),
            "diffVO 内容与 Map 轨同源（动态快照 Object 契约不变）");
        assertEquals(Map.of("from", "git-b"), vo.getSnapshot2());
        verify(gitService, times(2)).readVersionAtRef(any(), any(), any(), any(), any());
    }
}
