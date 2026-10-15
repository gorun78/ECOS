package com.chinacreator.gzcm.workspace.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.chinacreator.gzcm.workspace.exception.SecurityEngineUnavailableException;
import com.chinacreator.gzcm.workspace.scenario.ForbiddenOperationException;

/**
 * 导出通道契约与接线钉（#142/B4-3b，差距文档 D-B4-10/D-B4-11/D-B4-13/D-B4-14/D-B4-16）。
 *
 * <p><b>本类解决的假绿问题</b>：workspace 侧 {@code ExportDecision} 消费的是
 * security-engine REST 应答里的<b>字符串键名</b>（{@code rls.policyRefs} 等），
 * 两侧分处不同 Maven 模块、编译期零耦合 —— security 侧改键名时 workspace 侧只会
 * <b>静默读到 null</b>，导出件随即退回"无证据"形态而不报错。因此这里做三件编译做不到的事：</p>
 * <ol>
 *   <li>{@link #bundleKeyNamesMatchSecurityEngineSource()} —— 直接读 security-engine 生产源文件，
 *       对键名字面量做双向核对（源文件找不到即失败，禁跳过）；</li>
 *   <li>{@link #exportPathIsWiredToTheSingleDecisionEntry()} /
 *       {@link #permissionScopeConstantCannotComeBack()} —— 把"必须走 /decision"和
 *       "不得再用常量自称权限范围"钉成源码级断言；</li>
 *   <li>咽喉六态行为断言（{@link ScenarioReadGuard#exportDecision} 真实对象 + mock 客户端），
 *       其中含一条<b>反向钉</b>：OPA 护栏未被咨询时不得 403（D-B4-14/D-B4-18 的裁决），
 *       谁把它改回硬门，这条会红并给出理由。</li>
 * </ol>
 */
@DisplayName("导出通道：跨模块键名契约 + 单入口接线 + 咽喉六态")
class ExportDecisionContractTest {

    private static final String SECURITY_BUNDLE_SRC =
            "ecos_backend/engine/security-engine/security-engine-impl/src/main/java/"
                    + "com/chinacreator/gzcm/engine/security/decision/DecisionBundle.java";
    private static final String SECURITY_DECISION_SRC =
            "ecos_backend/engine/security-engine/security-engine-impl/src/main/java/"
                    + "com/chinacreator/gzcm/engine/security/decision/SecurityDecisionService.java";
    private static final String WORKSPACE_CONSUMER_SRC =
            "ecos_backend/workspace/workspace-impl/src/main/java/"
                    + "com/chinacreator/gzcm/workspace/security/ExportDecision.java";
    private static final String WORKSPACE_MAIN_TREE =
            "ecos_backend/workspace/workspace-impl/src/main/java";

    /** 旧形态的权限自证常量（B4-3b 废除，出现即假绿回潮）。 */
    private static final String BANNED_SCOPE_CONSTANT = "same-pipeline-as-query";

    // ═══════════════ ① 跨模块键名契约 ═══════════════

    @Test
    @DisplayName("键名契约 — security 侧写入的 bundle 键名与 workspace 侧读取的字面量逐字成对")
    void bundleKeyNamesMatchSecurityEngineSource() throws IOException {
        String bundleSrc = readRepoFile(SECURITY_BUNDLE_SRC);
        String decisionSrc = readRepoFile(SECURITY_DECISION_SRC);
        String consumerSrc = readRepoFile(WORKSPACE_CONSUMER_SRC);

        // security 侧：bundle 结构四源 + 两 id，范围证据键由 SecurityDecisionService 单点写入
        for (String key : List.of("\"rls\"", "\"columns\"", "\"masks\"", "\"opa\"",
                "\"decisionId\"", "\"traceId\"")) {
            assertTrue(bundleSrc.contains(key), "DecisionBundle.toMap 键集变化须同步消费侧: " + key);
        }
        assertTrue(decisionSrc.contains("\"policyRefs\""),
                "范围证据出处（policyRefs）必须由裁决入口单点写入，否则导出件无证据可引");
        // 加严：refs 必须 rls 段与 columns 段各挂一处。只出现一次＝其中一段被漏改，
        // 消费侧对该段静默读 null（正是本类要防的假绿形态），门禁须能红。
        int refsSites = countOccurrences(decisionSrc, "\"policyRefs\"");
        assertTrue(refsSites >= 2,
                "policyRefs 须同时写入 rls 段与 columns 段，实得 " + refsSites + " 处：单侧漏改会静默降级而非报错");
        for (String key : List.of("predicateTemplate", "denyIfEmpty", "obligations", "policyRefs")) {
            assertTrue(decisionSrc.contains(key) || bundleSrc.contains(key),
                    "bundle 键 " + key + " 在 security 侧已消失（键名漂移）");
        }

        // workspace 侧：读的就是这些字面量，缺一即静默 null（本断言让漂移变红而非静默降级）
        for (String key : List.of("policyRefs", "predicateTemplate", "denyIfEmpty",
                "obligations", "decisionId")) {
            assertTrue(consumerSrc.contains("\"" + key + "\""),
                    "ExportDecision 不再读取 bundle 键 " + key + "：两侧契约断裂");
        }
    }

    // ═══════════════ ② 接线与禁常量 ═══════════════

    @Test
    @DisplayName("接线钉 — 导出咽喉只经三通道唯一入口 /api/v1/security/decision（purpose=export）")
    void exportPathIsWiredToTheSingleDecisionEntry() throws IOException {
        String clientSrc = readRepoFile("ecos_backend/workspace/workspace-impl/src/main/java/"
                + "com/chinacreator/gzcm/workspace/security/WorkspaceSecurityEngineClient.java");
        String filterSrc = readRepoFile("ecos_backend/workspace/workspace-impl/src/main/java/"
                + "com/chinacreator/gzcm/workspace/security/AbacQueryFilter.java");
        String guardSrc = readRepoFile("ecos_backend/workspace/workspace-impl/src/main/java/"
                + "com/chinacreator/gzcm/workspace/security/ScenarioReadGuard.java");
        String exportSrc = readRepoFile("ecos_backend/workspace/workspace-impl/src/main/java/"
                + "com/chinacreator/gzcm/workspace/forecast/ForecastExportService.java");

        assertTrue(clientSrc.contains("\"/api/v1/security/decision\""),
                "workspace 侧必须存在 /decision 单入口调用（D-B4-10：EXPORT 通道此前生产零调用）");
        assertTrue(filterSrc.contains("securityEngineClient.decide("),
                "workspace 出裁决只经 AbacQueryFilter 委托，不得新增第三个出口");
        assertTrue(guardSrc.contains("PURPOSE_EXPORT = \"export\""),
                "通道字面量必须与 security 侧 Purpose.EXPORT 的 wire 形态同源");
        assertTrue(guardSrc.contains("decisionBundle(entityCode, PURPOSE_EXPORT)"),
                "导出咽喉必须取自裁决入口，不得退回 /rls/apply 分散面");
        assertTrue(exportSrc.contains("readGuard.exportDecision(ENTITY_CODE)"),
                "导出件第①步必须是 exportDecision（四源 + 范围证据），而非只看谓词");
        assertTrue(exportSrc.contains("decision.permissionScope()")
                        && exportSrc.contains("decision.watermark()"),
                "文件头的权限范围/水印必须逐字引用裁决产物");
    }

    @Test
    @DisplayName("禁常量回潮 — workspace 生产代码位（剥注释后）不得再出现自证权限范围的硬编码常量")
    void permissionScopeConstantCannotComeBack() throws IOException {
        // 门禁自身的有效性对照（禁把"剥注释"做成新的假绿源）：
        // 状态机若把字符串字面量也吞掉，第一条会红；若完全不剥注释，第二条会红。
        assertTrue(stripComments("String s = \"rls+cls(" + BANNED_SCOPE_CONSTANT + ", SEC-01)\";")
                .contains(BANNED_SCOPE_CONSTANT), "剥注释后代码位字符串字面量必须仍在（否则本门恒绿＝假绿）");
        assertFalse(stripComments("/** 旧常量 " + BANNED_SCOPE_CONSTANT + " 已废 */").contains(BANNED_SCOPE_CONSTANT),
                "javadoc/行注释里的说明性提及不算代码位（本仓红线：文档只加订正注、原文不删）");

        Path root = repoFile(WORKSPACE_MAIN_TREE);
        List<String> hits = new ArrayList<>();
        int scanned;
        try (Stream<Path> walk = Files.walk(root)) {
            List<Path> sources = walk.filter(p -> p.toString().endsWith(".java")).toList();
            scanned = sources.size();
            for (Path p : sources) {
                try {
                    if (stripComments(Files.readString(p)).contains(BANNED_SCOPE_CONSTANT)) {
                        hits.add(p.toString());
                    }
                } catch (IOException e) {
                    throw new IllegalStateException("读取失败: " + p, e);
                }
            }
        }
        assertTrue(scanned > 0, "扫描根解析出 0 个 .java 源文件 = 门禁空转，禁把「没扫到」当成「没违例」");
        assertTrue(hits.isEmpty(),
                "导出件权限范围必须是裁决证据（decision=<id> rls=[命中策略]），不得写常量: " + hits);
    }

    // ═══════════════ ③ 咽喉六态行为 ═══════════════

    @Test
    @DisplayName("放行态 — 恒真谓词 + 有命中策略：范围证据逐字含裁决号/策略名/护栏态，水印取义务")
    void grantedDecisionCarriesFullEvidence() {
        WorkspaceSecurityEngineClient client = mock(WorkspaceSecurityEngineClient.class);
        when(client.decide(anyString(), anyList(), anyString())).thenReturn(
                bundle(List.of("rls-finance-all"), "(1=1)", List.of(), "allow", null,
                        "财务负责人全量导出 2026-10-15"));
        ExportDecision d = guard(client).exportDecision("forecast_run");

        verify(client).decide("forecast_run", List.of(), "export");
        assertEquals("decision=dec-1 purpose=export rls=[rls-finance-all] cls=[NONE]"
                        + " predicate=(1=1) opa=allow",
                d.permissionScope());
        assertEquals("财务负责人全量导出 2026-10-15", d.watermark());
        assertEquals("dec-1", d.decisionId());
        assertFalse(d.guardrailUnconsulted());
    }

    @Test
    @DisplayName("无裁决证据（security 明确拒绝/应答缺段）⇒ 403，禁把「没有裁决」渲染成「裁决通过」")
    void missingBundleIsDeny() {
        WorkspaceSecurityEngineClient client = mock(WorkspaceSecurityEngineClient.class);
        when(client.decide(anyString(), anyList(), anyString())).thenReturn(null);

        ForbiddenOperationException ex = assertThrows(ForbiddenOperationException.class,
                () -> guard(client).exportDecision("forecast_run"));
        assertReasonContains(ex, "无裁决证据");
    }

    @Test
    @DisplayName("进程不可用 ⇒ 503 ECOS-SEC-501 原样上抛，不折叠成 403「无权限」（§4.5.4 分态）")
    void unavailableEngineKeepsItsOwnState() {
        WorkspaceSecurityEngineClient client = mock(WorkspaceSecurityEngineClient.class);
        when(client.decide(anyString(), anyList(), anyString())).thenThrow(
                new SecurityEngineUnavailableException("/api/v1/security/decision", "connect timed out", null));

        SecurityEngineUnavailableException ex = assertThrows(SecurityEngineUnavailableException.class,
                () -> guard(client).exportDecision("forecast_run"));
        assertEquals(503, ex.getHttpStatus());
        assertEquals("ECOS-SEC-501", ex.getCode());
    }

    @Test
    @DisplayName("无权用户行 — 0 命中策略 + denyIfEmpty=true ⇒ 403（矩阵第4行第2格，验收 AuditPackDenyTest）")
    void noPolicyMatchedWithDenyIfEmptyIsDeny() {
        WorkspaceSecurityEngineClient client = mock(WorkspaceSecurityEngineClient.class);
        when(client.decide(anyString(), anyList(), anyString())).thenReturn(
                bundle(List.of(), "1=1", List.of(), "allow", null, null));

        ForbiddenOperationException ex = assertThrows(ForbiddenOperationException.class,
                () -> guard(client).exportDecision("forecast_run"));
        assertReasonContains(ex, "denyIfEmpty");
    }

    @Test
    @DisplayName("缺陷 D-B4-13 — 非平凡谓词（部门/项目维度）不可下推至导出件载体 ⇒ 403 而非全量外带")
    void nonTrivialPredicateCannotBePushedDownIsDeny() {
        WorkspaceSecurityEngineClient client = mock(WorkspaceSecurityEngineClient.class);
        when(client.decide(anyString(), anyList(), anyString())).thenReturn(
                bundle(List.of("rls-dept-scope"), "(department_id = :userDeptId)", List.of(), "allow", null, null));

        ForbiddenOperationException ex = assertThrows(ForbiddenOperationException.class,
                () -> guard(client).exportDecision("forecast_run"));
        assertReasonContains(ex, "无法下推");
        assertEquals("(department_id = :userDeptId)", ex.getDetails().get("predicate"),
                "拒绝原因必须带上被拒的谓词，便于回查是哪条策略卡住导出");
    }

    @Test
    @DisplayName("恒假谓词 1=0（含段括号形态 (1=0)）⇒ 403（剥括号归一，禁误判成非平凡谓词）")
    void denyPredicateIsDenyInBothSpellings() {
        for (String spelling : List.of("1=0", "(1=0)", " ( 1=0 ) ")) {
            WorkspaceSecurityEngineClient client = mock(WorkspaceSecurityEngineClient.class);
            when(client.decide(anyString(), anyList(), anyString())).thenReturn(
                    bundle(List.of("rls-x"), spelling, List.of(), "allow", null, null));

            assertThrows(ForbiddenOperationException.class,
                    () -> guard(client).exportDecision("forecast_run"), spelling);
        }
    }

    /**
     * 裁决证据缺失时的痕迹值断言（D-B4-10/D-B4-12 的"无证据不冒充"半边）。
     *
     * <p>此处的放行前提是"命中了策略 + 谓词恒真"，缺的只是 bundle 的出处/义务段：
     * 导出可以发生，但文件头必须写 {@code NOT_WIRED}/{@code NOT_ISSUED}，
     * 让缺陷在产物里可见，而不是被看似合理的默认值抹平。</p>
     */
    @Test
    @DisplayName("证据缺段 — bundle 无 policyRefs/无 opa 段时写 NOT_WIRED/NOT_ISSUED，禁默认值冒充")
    void missingEvidenceSectionsAreMarkedNotDefaulted() {
        Map<String, Object> b = bundle(List.of("rls-finance-all"), "(1=1)", List.of(), "allow", null, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> rls = (Map<String, Object>) b.get("rls");
        rls.remove("policyRefs");
        b.remove("opa");

        ExportDecision d = ExportDecision.fromBundle("forecast_run", b);
        assertEquals(ExportDecision.SCOPE_NOT_WIRED, d.permissionScope());
        assertEquals(ExportDecision.WATERMARK_NOT_ISSUED, d.watermark());
        assertEquals("ABSENT", d.opaState());
        assertTrue(d.guardrailUnconsulted(), "opa 段缺失＝护栏没被咨询，不得当成\"已裁决\"");
    }

    /**
     * 反向钉（本批裁决，差距文档 D-B4-14/D-B4-18）：护栏源因策略名非法而从未被咨询时，
     * 导出咽喉<b>不据此 403</b>，而是把护栏态如实写进范围证据。
     *
     * <p>为什么必须钉反方向：把 {@code opa.allow=false}（根因＝
     * {@code SecurityDecisionService.safeOpa} 用非法策略名 {@code "data.access"} 调
     * {@code OpaPolicyService.evaluate}）当策略拒绝，会把引擎缺陷伪装成用户无权限，
     * 并让导出功能永久不可用 —— 那是另一种假绿（"看起来在 fail-closed"）。
     * 硬门的正确开启点是 B4-3c 补名合规的 {@code data_access} rego 之后；
     * 届时应同时删除本用例并改为 DENY 断言，而不是悄悄改掉。</p>
     */
    @Test
    @DisplayName("反向钉 — 护栏未被咨询（OPA_ERROR_FAIL_CLOSED）时放行但如实记入范围证据，禁伪装成策略拒绝")
    void unconsultedGuardrailIsEvidenceNotADeny() {
        WorkspaceSecurityEngineClient client = mock(WorkspaceSecurityEngineClient.class);
        when(client.decide(anyString(), anyList(), anyString())).thenReturn(
                bundle(List.of("rls-finance-all"), "(1=1)", List.of(), "deny",
                        "OPA_ERROR_FAIL_CLOSED", null));

        ExportDecision d = guard(client).exportDecision("forecast_run");

        assertTrue(d.guardrailUnconsulted());
        assertFalse(d.opaAllowed());
        assertTrue(d.permissionScope().contains("opa=deny:OPA_ERROR_FAIL_CLOSED"), d.permissionScope());
        assertEquals(ExportDecision.WATERMARK_NOT_ISSUED, d.watermark(),
                "护栏没被咨询 ⇒ 水印义务不可能存在，只能如实标 NOT_ISSUED");
    }

    @Test
    @DisplayName("漂移保守面 — denyIfEmpty 缺段按 true 处理（宁可拒绝也不放行）")
    void missingDenyIfEmptyFlagFailsClosed() {
        Map<String, Object> b = bundle(List.of(), "1=1", List.of(), "allow", null, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> rls = (Map<String, Object>) b.get("rls");
        rls.remove("denyIfEmpty");
        WorkspaceSecurityEngineClient client = mock(WorkspaceSecurityEngineClient.class);
        when(client.decide(anyString(), anyList(), anyString())).thenReturn(b);

        assertTrue(ExportDecision.fromBundle("forecast_run", b).rlsDenyIfEmpty());
        assertThrows(ForbiddenOperationException.class,
                () -> guard(client).exportDecision("forecast_run"));
    }

    // ═══════════════ helpers ═══════════════

    private static ScenarioReadGuard guard(WorkspaceSecurityEngineClient client) {
        return new ScenarioReadGuard(new AbacQueryFilter(client));
    }

    /**
     * 拒绝理由断言：{@code ForbiddenOperationException.getMessage()} 只带权限点，
     * 结构化理由在 {@code details.reason}（HTTP 体外发的是 code+message+details，
     * 故断言必须落在真正的载体上，禁对着 message 空转）。
     */
    private static void assertReasonContains(ForbiddenOperationException ex, String needle) {
        Object reason = ex.getDetails().get("reason");
        assertTrue(reason != null && String.valueOf(reason).contains(needle),
                "拒绝理由应含「" + needle + "」，实得 details=" + ex.getDetails());
    }

    /** {@code DecisionBundle.toMap()} 的 REST 形态（键名与 security 侧同源，见契约断言）。 */
    private static Map<String, Object> bundle(List<String> rlsRefs, String predicate,
                                              List<String> clsRefs, String opaAllow,
                                              String deniedReason, String watermark) {
        Map<String, Object> rls = new LinkedHashMap<>();
        rls.put("predicateTemplate", predicate);
        rls.put("bindings", List.of());
        rls.put("combine", "AND");
        rls.put("denyIfEmpty", true);
        rls.put("policyRefs", rlsRefs);

        Map<String, Object> columns = new LinkedHashMap<>();
        columns.put("mode", "allow");
        columns.put("names", List.of());
        columns.put("policyRefs", clsRefs);

        Map<String, Object> opa = new LinkedHashMap<>();
        opa.put("allow", "allow".equals(opaAllow));
        opa.put("policyRef", "ecos.data.access");
        List<Map<String, Object>> obligations = new ArrayList<>();
        if (deniedReason != null) {
            obligations.add(Map.of("obligation", "DENIED", "deniedReason", deniedReason));
        }
        if (watermark != null) {
            obligations.add(Map.of("obligation", "watermark", "watermark", watermark));
        }
        opa.put("obligations", obligations);

        Map<String, Object> b = new LinkedHashMap<>();
        b.put("rls", rls);
        b.put("columns", columns);
        b.put("masks", List.of());
        b.put("opa", opa);
        b.put("decisionId", "dec-1");
        b.put("traceId", "trace-1");
        return b;
    }

    /** 向上定位仓根后读取相对路径文件；找不到即失败（禁把"读不到源文件"当成"无需校验"）。 */
    private static String readRepoFile(String repoRelative) throws IOException {
        return Files.readString(repoFile(repoRelative));
    }

    private static int countOccurrences(String src, String needle) {
        int count = 0, idx = 0;
        while ((idx = src.indexOf(needle, idx)) >= 0) {
            count++;
            idx += needle.length();
        }
        return count;
    }

    /**
     * 剥掉 {@code //} 行注释与 {@code /* } 块注释，保留字符串/字符字面量原样。
     *
     * <p>用于"禁常量回潮"这类源码级门禁：本仓红线要求文档只加订正注、原文不删，
     * 因此 javadoc 里保留被废常量的说明文字是合规的，只有<b>代码位</b>的使用才算回潮。
     * 状态机认字符串边界，故 URL 里的 {@code //} 与注释里的引号都不会误伤；
     * 门禁有效性由调用处的两条对照断言自证。</p>
     */
    static String stripComments(String src) {
        StringBuilder out = new StringBuilder(src.length());
        boolean inString = false, inChar = false, inLine = false, inBlock = false;
        for (int i = 0; i < src.length(); i++) {
            char c = src.charAt(i);
            char n = i + 1 < src.length() ? src.charAt(i + 1) : '\0';
            if (inLine) {
                if (c == '\n') {
                    inLine = false;
                    out.append(c);
                }
                continue;
            }
            if (inBlock) {
                if (c == '*' && n == '/') {
                    inBlock = false;
                    i++;
                }
                continue;
            }
            if (inString || inChar) {
                out.append(c);
                if (c == '\\') {
                    out.append(n);
                    i++;
                } else if (c == '"' && !inChar) {
                    inString = false;
                } else if (c == '\'' && !inString) {
                    inChar = false;
                }
                continue;
            }
            if (c == '"') {
                inString = true;
                out.append(c);
            } else if (c == '\'') {
                inChar = true;
                out.append(c);
            } else if (c == '/' && n == '/') {
                inLine = true;
                i++;
            } else if (c == '/' && n == '*') {
                inBlock = true;
                i++;
            } else {
                out.append(c);
            }
        }
        return out.toString();
    }

    private static Path repoFile(String repoRelative) {
        Path cur = Paths.get("").toAbsolutePath();
        for (int i = 0; i < 8 && cur != null; i++) {
            Path candidate = cur.resolve(repoRelative);
            if (Files.isRegularFile(candidate) || Files.isDirectory(candidate)) return candidate;
            cur = cur.getParent();
        }
        throw new IllegalStateException("未找到源文件/目录（跨模块契约的两侧必须都在，禁跳过校验）: "
                + repoRelative);
    }
}
