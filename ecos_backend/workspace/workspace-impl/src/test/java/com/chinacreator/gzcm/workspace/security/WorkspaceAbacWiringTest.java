package com.chinacreator.gzcm.workspace.security;

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
 *       拒绝/不可用 → 403，非平凡谓词无 SQL 载体 → fail-closed 拒绝（宁 DENY 不超发），
 *       列裁决拒绝 → 空行集；空串=无策略才放行。</li>
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

        // 守卫单源：RLS/CLS 裁决只经 AbacQueryFilter（其内部再走 security-engine REST 客户端）
        String guard = stripComments(
                Files.readString(pkgRoot.resolve("security/ScenarioReadGuard.java"), StandardCharsets.UTF_8));
        assertTrue(guard.contains("abacQueryFilter.buildRowFilterCondition("),
                "ScenarioReadGuard 行裁决必须委托 AbacQueryFilter（security-engine 单源）");
        assertTrue(guard.contains("abacQueryFilter.filterColumns("),
                "ScenarioReadGuard 列裁决必须委托 AbacQueryFilter（security-engine 单源）");
    }

    // ═══════════════ 行为面：默认 DENY ═══════════════

    @Test
    void securityEngineFailureDeniesNotPasses() {
        WorkspaceSecurityEngineClient client = mock(WorkspaceSecurityEngineClient.class);
        ScenarioReadGuard guard = new ScenarioReadGuard(new AbacQueryFilter(client));

        // (1) security-engine 不可用/拒绝 → "1=0" → 403，禁放行
        when(client.applyRls(eq("scenario_graph"), eq("scenario_graph"), any()))
                .thenReturn("1=0");
        ForbiddenOperationException denied = assertThrows(ForbiddenOperationException.class,
                () -> guard.assertReadable("scenario_graph"));
        assertEquals(403, denied.getHttpStatus());
        assertEquals("PERMISSION_DENIED", denied.getCode());

        // (2) 非平凡谓词而本端点无 SQL 下载体 → fail-closed 拒绝（宁 DENY 不超发，
        //     禁全量捞回内存再滤，F07-18-1 红线）
        when(client.applyRls(anyString(), anyString(), any()))
                .thenReturn("org_id = 'o-1' AND owner = 'u-1'");
        assertThrows(ForbiddenOperationException.class, () -> guard.assertReadable("scenario_run"));

        // (3) 正向对照：空串=无策略登记 → 放行（DENY 用例不是恒抛造成的假通过）
        when(client.applyRls(anyString(), anyString(), any())).thenReturn("");
        assertDoesNotThrow(() -> guard.assertReadable("scenario_list"));

        // (4) CLS 拒绝/security 不可用 → 空行集
        when(client.allowedColumns(eq("scenario_run"), anyList())).thenReturn(List.of());
        List<Map<String, Object>> rows = new ArrayList<>();
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("runId", "r-1");
        r.put("amount", 100);
        rows.add(r);
        assertTrue(guard.applyColumns("scenario_run", rows).isEmpty(),
                "F07-18 违规：列裁决拒绝时仍返回原始行（默认 DENY 失守）");

        // (5) CLS 正向对照：允许列保留、未列裁剪
        when(client.allowedColumns(eq("scenario_run"), anyList())).thenReturn(List.of("runId"));
        List<Map<String, Object>> pruned = guard.applyColumns("scenario_run", rows);
        assertEquals(1, pruned.size());
        assertTrue(pruned.get(0).containsKey("runId") && !pruned.get(0).containsKey("amount"));
    }

    // ═══════════════ 辅助 ═══════════════

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
        return src.replaceAll("/\\*[^*]*\\*+(?:[^/*][^*]*\\*+)*/", " ")
                .replaceAll("//[^\\r\\n]*", " ");
    }
}
