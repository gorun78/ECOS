package com.chinacreator.gzcm.workspace.security;

import com.chinacreator.gzcm.workspace.testscan.SourceCommentStripper;
import com.chinacreator.gzcm.common.security.cls.ColumnSurfaceContract;
import com.chinacreator.gzcm.common.security.decision.DecisionChannelContract;
import com.chinacreator.gzcm.workspace.scenario.ForbiddenOperationException;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 卷07 F07-18（W182 / C164，P0）验收 —— 场景域读端点安全接线全量化。
 *
 * <p>两面验证：</p>
 * <ol>
 *   <li>{@link #everyReadEndpointAppliesRowAndColumnFilter()} 源码面：C-6 判定表列举的
 *       6 个敏感读 Controller 必须全部经 {@link ScenarioReadGuard} 构造注入并调用
 *       {@code assertReadable}/{@code applyColumns}，且 Controller 层不得绕过守卫
 *       直连 {@link AbacQueryFilter}（单咽喉）；</li>
 *   <li>{@link #securityEngineFailureDeniesNotPasses()} 行为面：security-engine
 *       拒绝/形态漂移 → 403，非平凡谓词无 SQL 载体 → fail-closed 拒绝（宁 DENY 不超发），
 *       列裁决拒绝 → 空行集；<b>只有恒真 {@code 1=1}（裁决明确"不限制行"）才放行</b>。
 *       #142/B4-3c-2（D-B4-17）把本类原判据「空串=无策略才放行」翻正为该口径：
 *       空串的真实含义是应答未发执行契约（生产者从来没有 {@code whereClause} 字段），
 *       当成"无行级限制"即是页面通道 fail-open 的根因。</li>
 * </ol>
 */
class WorkspaceAbacWiringTest {

    // ═══════════════ 源码面：接线全量化 ═══════════════

    /** C-6 判定表 ↔ 已接线 Controller（相对 workspace 包根）。 */
    private static final Map<String, String[]> GUARDED_CONTROLLERS = Map.of(
            "controller/ScenarioBindingLinkController.java", new String[]{"assertReadable"},
            "controller/ScenarioCompletenessController.java", new String[]{"assertReadable"},
            "controller/ScenarioController.java", new String[]{"assertReadable"},
            "controller/ScenarioRunController.java", new String[]{"assertReadable", "applyColumns"},
            "controller/ScenarioActionController.java", new String[]{"assertReadable", "applyColumns"},
            "controller/ScenarioCognitionV2Controller.java", new String[]{"assertReadable"});

    @Test
    void everyReadEndpointAppliesRowAndColumnFilter() throws IOException {
        Path pkgRoot = workspacePackageRoot();
        for (Map.Entry<String, String[]> e : GUARDED_CONTROLLERS.entrySet()) {
            String code = stripComments(Files.readString(pkgRoot.resolve(e.getKey()), StandardCharsets.UTF_8));
            for (String call : e.getValue()) {
                assertTrue(code.contains("readGuard." + call + "("),
                        "F07-18 违规: " + e.getKey() + " 未调用 readGuard." + call);
            }
            assertTrue(code.contains("ScenarioReadGuard readGuard"),
                    "F07-18 违规: " + e.getKey() + " 未构造注入 ScenarioReadGuard（缺客户端必须启动失败，禁软依赖）");
        }

        // 单咽喉：controller 包内除登记豁免外，不得绕过守卫直连 AbacQueryFilter。
        // 豁免=对象域两 Controller（H10-T1 既有接线：其数据载体接 SQL 谓词，RLS 可下推，
        // 不属 ScenarioReadGuard「无载体 fail-closed」形态）；新增直连即失败。
        List<String> registeredExemptions = List.of(
                "ObjectController.java", "ObjectQLController.java");
        List<String> bypass = new ArrayList<>();
        try (var walk = Files.walk(pkgRoot.resolve("controller"))) {
            for (Path p : walk.filter(f -> f.toString().endsWith(".java")).toList()) {
                String rel = pkgRoot.resolve("controller").relativize(p).toString().replace('\\', '/');
                String code = stripComments(Files.readString(p, StandardCharsets.UTF_8));
                if (code.contains("AbacQueryFilter") && !registeredExemptions.contains(rel)) {
                    bypass.add(rel);
                }
            }
        }
        assertTrue(bypass.isEmpty(), "F07-18 违规：Controller 绕过 ScenarioReadGuard 直连 AbacQueryFilter: " + bypass);

        // 守卫单源：RLS/CLS 裁决只经 AbacQueryFilter（其内部再走 security-engine REST 客户端）。
        // #142/B4-3c-2（D-B4-17）：行裁决必须走"片段+参数成对"的 buildRowFilter( 出口——
        // 只取片段的 buildRowFilterCondition( 会把带 ? 的片段当半条 SQL 交出去，故在此钉死禁回归。
        String guard = stripComments(
                Files.readString(pkgRoot.resolve("security/ScenarioReadGuard.java"), StandardCharsets.UTF_8));
        assertTrue(guard.contains("abacQueryFilter.buildRowFilter("),
                "ScenarioReadGuard 行裁决必须委托 AbacQueryFilter.buildRowFilter（成对下推，security-engine 单源）");
        assertTrue(guard.contains("abacQueryFilter.filterColumns("),
                "ScenarioReadGuard 列裁决必须委托 AbacQueryFilter（security-engine 单源）");
    }

    // ═══════════════ 行为面：默认 DENY ═══════════════

    @Test
    void securityEngineFailureDeniesNotPasses() {
        WorkspaceSecurityEngineClient client = mock(WorkspaceSecurityEngineClient.class);
        ScenarioReadGuard guard = new ScenarioReadGuard(new AbacQueryFilter(client));

        // (1) security-engine 明确拒绝 / 应答形态漂移 → 拒绝态 → 403，禁放行
        when(client.applyRlsFilter(eq("scenario_graph"), eq("scenario_graph"), any()))
                .thenReturn(RlsFilter.deny("RLS_NO_POLICY_MATCHED", true, List.of()));
        ForbiddenOperationException denied = assertThrows(ForbiddenOperationException.class,
                () -> guard.assertReadable("scenario_graph"));
        assertEquals(403, denied.getHttpStatus());
        assertEquals("PERMISSION_DENIED", denied.getCode());

        // (2) 非平凡谓词而本端点无 SQL 下载体 → fail-closed 拒绝（宁 DENY 不超发，
        //     禁全量捞回内存再滤，F07-18-1 红线）
        when(client.applyRlsFilter(anyString(), anyString(), any())).thenReturn(RlsFilter.fromData(
                Map.of("whereClause", "(org_id = ? AND owner = ?)",
                        "whereParams", List.of("o-1", "u-1")), "scenario_run"));
        assertThrows(ForbiddenOperationException.class, () -> guard.assertReadable("scenario_run"));

        // (3) 正向对照：恒真 1=1（裁决明确不限制行）→ 放行。
        //     本条在 #142/B4-3c-2 翻正：原对照用的是「空串=无策略→放行」，而空串的真实含义
        //     是"应答未发执行契约"（D-B4-17 的 fail-open 入口），故改为恒真形态；
        //     缺字段/空白形态由 (1) 与 RlsFilterContractTest 钉为拒绝。
        when(client.applyRlsFilter(anyString(), anyString(), any()))
                .thenReturn(RlsFilter.unrestricted());
        assertDoesNotThrow(() -> guard.assertReadable("scenario_list"));

        // (4) CLS 策略拒绝 → 空行集（#142/B4-3d-2：页面通道列面改经 /decision 单腿裁决）
        when(client.decide(eq("scenario_run"), anyList(), eq(DecisionChannelContract.PURPOSE_PAGE)))
                .thenReturn(bundleWithVisible(List.of(), List.of("runId", "amount"),
                        ColumnSurfaceContract.DENY_POLICY));
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("runId", "r-1");
        r.put("amount", 100);
        rows.add(r);
        assertTrue(guard.applyColumns("scenario_run", rows).isEmpty(),
                "F07-18 违规：列裁决拒绝时仍返回原始行（默认 DENY 失守）");

        // (5) CLS 正向对照：允许列保留、未列裁剪
        when(client.decide(eq("scenario_run"), anyList(), eq(DecisionChannelContract.PURPOSE_PAGE)))
                .thenReturn(bundleWithVisible(List.of("runId"), List.of("amount"), null));
        List<Map<String, Object>> pruned = guard.applyColumns("scenario_run", rows);
        assertEquals(1, pruned.size());
        assertTrue(pruned.get(0).containsKey("runId") && !pruned.get(0).containsKey("amount"));
    }

    // ═══════════════ 辅助 ═══════════════

    /** 造一版 {@code /decision} 应答的 {@code columns} 段（权威键形态，见 ColumnSurfaceContract）。 */
    private static Map<String, Object> bundleWithVisible(List<String> visible, List<String> blocked,
                                                         String denyReason) {
        Map<String, Object> seg = new LinkedHashMap<>();
        seg.put(ColumnSurfaceContract.KEY_VISIBLE_COLUMNS, visible);
        seg.put(ColumnSurfaceContract.KEY_BLOCKED_COLUMNS, blocked);
        seg.put(ColumnSurfaceContract.KEY_COLUMN_UNIVERSE_SOURCE, ColumnSurfaceContract.UNIVERSE_REQUEST);
        seg.put(ColumnSurfaceContract.KEY_COLUMN_UNIVERSE_RESOLVED, true);
        if (denyReason != null) seg.put(ColumnSurfaceContract.KEY_CLS_DENY_REASON, denyReason);
        Map<String, Object> bundle = new LinkedHashMap<>();
        bundle.put("columns", seg);
        return bundle;
    }

    private static Path workspacePackageRoot() {
        Path p = Paths.get("").toAbsolutePath();
        while (p != null) {
            Path cand = p.resolve("workspace/workspace-impl/src/main/java/com/chinacreator/gzcm/workspace");
            if (Files.isDirectory(cand)) return cand.normalize();
            Path self = Paths.get("src/main/java/com/chinacreator/gzcm/workspace");
            if (Files.isDirectory(p.resolve(self))) return p.resolve(self).normalize();
            p = p.getParent();
        }
        throw new IllegalStateException("找不到 workspace 源码包根，守门测试无法定位");
    }

    private static String stripComments(String src) {
        return SourceCommentStripper.strip(src);
    }
}
