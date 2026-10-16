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

import com.chinacreator.gzcm.common.security.guardrail.GuardrailDecisionContract;
import com.chinacreator.gzcm.workspace.exception.SecurityEngineUnavailableException;
import com.chinacreator.gzcm.workspace.scenario.ForbiddenOperationException;

/**
 * 导出通道契约与接线钉（#142/B4-3b + B4-3c，差距文档 D-B4-10/D-B4-11/D-B4-13/D-B4-14/
 * D-B4-16/D-B4-18/D-B4-21/D-B4-22）。
 *
 * <p><b>本类解决的假绿问题</b>：workspace 侧 {@code ExportDecision} 消费的是
 * security-engine REST 应答里的<b>字符串键名</b>（{@code rls.policyRefs} 等），
 * 两侧分处不同 Maven 模块、编译期零耦合 —— security 侧改键名时 workspace 侧只会
 * <b>静默读到 null</b>，导出件随即退回"无证据"形态而不报错。因此这里做四件编译做不到的事：</p>
 * <ol>
 *   <li>{@link #bundleKeyNamesMatchSecurityEngineSource()} —— 直接读 security-engine 生产源文件，
 *       对键名字面量做双向核对（源文件找不到即失败，禁跳过）。#142/B4-3d-5 起分两形态：
 *       无单源常量的 bundle 键仍按字面量成对核对；RLS 执行契约面／护栏义务键改由
 *       {@code assertReadThroughSingleSourceConstant} 三段核对（消费侧引常量 + 单源定义该字面量
 *       + 消费侧代码位禁第二形态内联），覆盖面严于原单向字面量核对，不因形态迁移而放宽；</li>
 *   <li>{@link #exportPathIsWiredToTheSingleDecisionEntry()} /
 *       {@link #permissionScopeConstantCannotComeBack()} —— 把"必须走 /decision"和
 *       "不得再用常量自称权限范围"钉成源码级断言；</li>
 *   <li>咽喉七态行为断言（{@link ScenarioReadGuard#exportDecision} 真实对象 + mock 客户端）。
 *       B4-3c 起护栏<b>是硬门</b>：{@code EVALUATED_DENY} → 403，
 *       {@code POLICY_UNDEFINED}/{@code OPA_UNAVAILABLE}/缺段 → 503 {@code ECOS-SEC-501}
 *       （「不可用 ≠ 无权限」）。B4-3b 那条"护栏未被咨询时不得 403"的反向钉，
 *       按其 javadoc 自述的承诺<b>删除并翻正</b>为上述两态用例，不留双轨；</li>
 *   <li>{@link #guardrailVocabularyIsSingleSourced()} —— 护栏词表（策略名 + 四态 +
 *       应答键名）单源在 {@link GuardrailDecisionContract}，三引擎生产代码位（剥注释后）
 *       出现内联字面量即红；旧形态（security 写 {@code "data.access"}、ai 写 {@code "rbac"}、
 *       workspace 靠 {@code OPA_ERROR_FAIL_CLOSED} 反推状态）回潮同样即红。</li>
 * </ol>
 */
@DisplayName("导出通道：跨模块键名契约 + 单入口接线 + 咽喉七态 + 词表单源")
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
    private static final String SECURITY_MAIN_TREE =
            "ecos_backend/engine/security-engine/security-engine-impl/src/main/java";
    private static final String AI_MAIN_TREE =
            "ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java";
    private static final String AI_CLIENT_SRC =
            "ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java/"
                    + "com/chinacreator/gzcm/engine/ai/security/AiSecurityEngineClient.java";
    private static final String AI_GATE_SRC =
            "ecos_backend/engine/ai-engine/ai-engine-impl/src/main/java/"
                    + "com/chinacreator/gzcm/engine/ai/security/AgentToolPolicyGate.java";
    private static final String GUARDRAIL_CONTRACT_SRC =
            "ecos_backend/runtime/common-api/src/main/java/"
                    + "com/chinacreator/gzcm/common/security/guardrail/GuardrailDecisionContract.java";
    private static final String RLS_EXECUTION_CONTRACT_SRC =
            "ecos_backend/runtime/common-api/src/main/java/"
                    + "com/chinacreator/gzcm/common/security/rls/RlsExecutionContract.java";

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

        // workspace 侧读的就是这些键，缺一即静默 null（本断言让漂移变红而非静默降级）。
        // #142/B4-3d-5 起分两形态，且**只准加严不准放宽**：
        //   ① bundle 里尚无单源常量的键（policyRefs/decisionId）⇒ 仍按字面量成对核对；
        //   ② RLS 执行契约面 / 护栏义务键 ⇒ 字面量唯一落点已搬进单源常量，消费侧只准引常量。
        // 若因为形态换了就删掉第②类断言，键名漂移从此不可见＝把真门改成假门；
        // 因此改成三段核对：消费侧引了该常量 + 单源把该常量定义成该字面量 + 消费侧代码位
        // 不得再内联该字面量（第二形态回潮即红）。security 侧是否仍写该字面量由上面一段守住。
        for (String key : List.of("policyRefs", "decisionId")) {
            assertTrue(consumerSrc.contains("\"" + key + "\""),
                    "ExportDecision 不再读取 bundle 键 " + key + "：两侧契约断裂");
        }
        String rlsContractSrc = readRepoFile(RLS_EXECUTION_CONTRACT_SRC);
        String guardrailContractSrc = readRepoFile(GUARDRAIL_CONTRACT_SRC);
        String consumerCode = stripComments(consumerSrc);
        assertReadThroughSingleSourceConstant(consumerCode, rlsContractSrc,
                "KEY_PREDICATE_TEMPLATE", "predicateTemplate");
        assertReadThroughSingleSourceConstant(consumerCode, rlsContractSrc,
                "KEY_DENY_IF_EMPTY", "denyIfEmpty");
        assertReadThroughSingleSourceConstant(consumerCode, guardrailContractSrc,
                "KEY_OBLIGATIONS", "obligations");
        assertReadThroughSingleSourceConstant(consumerCode, guardrailContractSrc,
                "KEY_DENIED_REASON", "deniedReason");
        assertReadThroughSingleSourceConstant(consumerCode, guardrailContractSrc,
                "KEY_OBLIGATION", "obligation");
        assertReadThroughSingleSourceConstant(consumerCode, guardrailContractSrc,
                "OBLIGATION_WATERMARK", "watermark");
    }

    /**
     * 单源常量形态核对（第②类）：三段任一不成立即红。
     *
     * <p>第 1 段防「消费侧改回自带字面量却没人发现」，第 2 段防「单源改名而消费侧静默读
     * null」，第 3 段防「单源与内联字面量两套形态并存」。三段合起来的覆盖面严于 B4-3b
     * 原断言（原断言只要求消费侧存在内联字面量）。</p>
     */
    private static void assertReadThroughSingleSourceConstant(String consumerCode, String contractSrc,
            String constName, String wireKey) {
        assertTrue(consumerCode.contains(constName),
                "ExportDecision 未引单源常量 " + constName + "（" + wireKey + " 的读取面失控）");
        assertTrue(contractSrc.contains(constName + " = \"" + wireKey + "\""),
                "单源常量 " + constName + " 不再定义为 \"" + wireKey + "\"：线契约键名漂移");
        assertFalse(consumerCode.contains("\"" + wireKey + "\""),
                "ExportDecision 代码位内联 \"" + wireKey + "\" = 绕过单源的第二形态，漂移将静默失配");
    }

    @Test
    @DisplayName("护栏键契约 — security 写 policyState，workspace 经单源常量读（禁两侧各自内联）")
    void guardrailKeysAreProducedAndConsumedThroughTheSingleSource() throws IOException {
        String contractSrc = readRepoFile(GUARDRAIL_CONTRACT_SRC);
        String decisionSrc = readRepoFile(SECURITY_DECISION_SRC);
        String consumerSrc = stripComments(readRepoFile(WORKSPACE_CONSUMER_SRC));

        // 单源必须定义这四态 + 应答键名（本类其余断言的取值全部来自它）
        for (String literal : List.of("\"data_access\"", "\"EVALUATED_ALLOW\"", "\"EVALUATED_DENY\"",
                "\"POLICY_UNDEFINED\"", "\"OPA_UNAVAILABLE\"", "\"policyState\"", "\"deniedReason\"")) {
            assertTrue(contractSrc.contains(literal),
                    "GuardrailDecisionContract 不再定义 " + literal + "：词表单源被拆，三消费方失配");
        }
        // security 侧生产面写入 policyState（护栏态是 403/503 分态的唯一依据）
        assertTrue(decisionSrc.contains("\"policyState\""),
                "裁决入口必须把护栏态写进 bundle.opa，否则消费侧只能靠布尔反推（旧假绿形态）");
        // workspace 侧只经单源读键与判态，代码位不得再出现内联字面量
        assertTrue(consumerSrc.contains("GuardrailDecisionContract.stateOf(")
                        && consumerSrc.contains("GuardrailDecisionContract.isUnevaluated("),
                "ExportDecision 必须经单源读护栏态并判「未评估」，禁本地另立词表");
        assertFalse(consumerSrc.contains("\"policyState\""),
                "ExportDecision 代码位内联 policyState 字面量 = 单源被绕过，键名漂移将静默失配");
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
        assertTrue(guardSrc.contains("PURPOSE_EXPORT = DecisionChannelContract.PURPOSE_EXPORT"),
                "通道字面量必须取 DecisionChannelContract 单源（#142/B4-3d-2，D-B4-27："
                        + "内联 \"export\" 加上入口对未识别值的静默回落 PAGE，等于词面一失配"
                        + "导出就以页面档被裁决且返回 200）");
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

        List<String> hits = findInCodePositions(WORKSPACE_MAIN_TREE, BANNED_SCOPE_CONSTANT);
        assertTrue(hits.isEmpty(),
                "导出件权限范围必须是裁决证据（decision=<id> rls=[命中策略]），不得写常量: " + hits);
    }

    // ═══════════════ ②b 护栏词表单源 ═══════════════

    /**
     * 护栏词表（策略名 + 四态 + 旧反推串）在<b>三引擎生产代码位</b>必须取自
     * {@link GuardrailDecisionContract}，内联字面量即红。
     *
     * <p>为什么钉这一条（D-B4-14/D-B4-18/D-B4-22）：旧形态是 security 写 {@code "data.access"}
     * （非法名 ⇒ OPA 从未被咨询）、ai 写 {@code "rbac"}（三通道策略面不同源）、
     * workspace 用 {@code OPA_ERROR_FAIL_CLOSED} 反推状态。三处分处不同 Maven 模块，
     * 改名后编译不报错，只会静默失配 —— 而"护栏到底说了话没有"正是 403/503 分态的唯一依据。</p>
     */
    @Test
    @DisplayName("词表单源 — 三引擎代码位禁内联护栏态/策略名字面量；ai 裁决咽喉禁查 rbac")
    void guardrailVocabularyIsSingleSourced() throws IOException {
        // 四态 + 策略名是「跨模块线契约」⇒ 三引擎生产代码位一律只准引单源常量
        List<String> shared = List.of(
                "\"" + GuardrailDecisionContract.STATE_ALLOW + "\"",
                "\"" + GuardrailDecisionContract.STATE_DENY + "\"",
                "\"" + GuardrailDecisionContract.STATE_POLICY_UNDEFINED + "\"",
                "\"" + GuardrailDecisionContract.STATE_UNAVAILABLE + "\"",
                "\"" + GuardrailDecisionContract.POLICY_DATA_ACCESS + "\"",
                "\"data.access\"");

        // 门禁有效性对照（禁把"剥注释后扫"做成恒绿假绿源）：
        // 代码位字面量必须被抓到，javadoc 里的说明性提及必须不被抓。
        String inline = "out.put(\"policyState\", \"" + GuardrailDecisionContract.STATE_DENY + "\");";
        assertTrue(stripComments(inline).contains(shared.get(1)),
                "扫描口径若吞掉代码位字符串字面量，本门恒绿＝假绿");
        assertFalse(stripComments("/** 旧值 \"" + GuardrailDecisionContract.STATE_DENY + "\" 已废 */")
                .contains(shared.get(1)), "注释/订正注里的说明性提及不算违例（本仓红线：原文不删）");

        for (String tree : List.of(SECURITY_MAIN_TREE, AI_MAIN_TREE, WORKSPACE_MAIN_TREE)) {
            List<String> hits = findInCodePositions(tree, shared.toArray(String[]::new));
            assertTrue(hits.isEmpty(), "护栏词表必须取自单源 GuardrailDecisionContract，"
                    + "内联字面量会随改名静默失配 (" + tree + "): " + hits);
        }

        // 消费侧禁"靠拒绝原因串反推护栏态"（旧形态）；生产侧的 deniedReason 是审计痕迹、
        // 由 OpaPolicyService 单点书写，不属本门范围（详设 C.2.5 ③ 义务集语义）。
        List<String> hits = findInCodePositions(WORKSPACE_MAIN_TREE,
                "\"" + GuardrailDecisionContract.STATE_ALLOW + "\"", "\"OPA_ERROR_FAIL_CLOSED\"");
        assertTrue(hits.isEmpty(), "workspace 侧必须按 policyState 分态，"
                + "禁再用 deniedReason 字符串反推（D-B4-14/D-B4-18 的旧口径）: " + hits);

        // AI 通道咽喉：策略名单源 + 接线在位 + 禁 rbac 回潮
        String aiClientSrc = readRepoFile(AI_CLIENT_SRC);
        String gateSrc = readRepoFile(AI_GATE_SRC);
        int sig = aiClientSrc.indexOf("Map<String, Object> evaluateBodyForAdjudication(");
        assertTrue(sig >= 0, "AI 裁决体构造点消失：三通道策略名单源失去落点");
        String adjudicationRegion = aiClientSrc.substring(sig, aiClientSrc.indexOf("\n    }", sig));
        assertTrue(adjudicationRegion.contains("GuardrailDecisionContract.POLICY_DATA_ACCESS"),
                "AI 通道必须查单源策略名 data_access（D-B4-22：此前硬编码 rbac ⇒「三通道同源」在策略面上是假的）");
        assertFalse(adjudicationRegion.contains("\"rbac\""),
                "AI 裁决咽喉回潮查 rbac ⇒ §4.7.2 AI 列（p1 ALLOW / p2 DENY / 按部门域）不可表达");
        assertTrue(gateSrc.contains("evaluateBodyForAdjudication("),
                "工具裁决闸必须经咽喉构造裁决体，禁在调用方另拼 policy 字段");

        // 特征化断言（钉现状非认可）：ai-engine 侧剩余 rbac 代码位只许是这两个非咽喉点
        List<String> rbacFiles = findInCodePositions(AI_MAIN_TREE, "\"rbac\"").stream()
                .map(h -> h.substring(0, h.indexOf(" 含 "))).distinct().sorted().toList();
        assertEquals(List.of("AiSecurityEngineClient.java", "GuardrailsServiceImpl.java"), rbacFiles,
                "ai-engine 侧 rbac 调用面变化须同步登记（D-B4-22 剩余面，新增即需裁决：是否属裁决咽喉）");
    }

    // ═══════════════ ③ 咽喉六态行为 ═══════════════

    @Test
    @DisplayName("放行态 — 恒真谓词 + 有命中策略：范围证据逐字含裁决号/策略名/护栏态，水印取义务")
    void grantedDecisionCarriesFullEvidence() {
        WorkspaceSecurityEngineClient client = mock(WorkspaceSecurityEngineClient.class);
        when(client.decide(anyString(), anyList(), anyString())).thenReturn(
                bundle(List.of("rls-finance-all"), "(1=1)", List.of(), GuardrailDecisionContract.STATE_ALLOW, null,
                        "财务负责人全量导出 2026-10-15"));
        ExportDecision d = guard(client).exportDecision("forecast_run");

        verify(client).decide("forecast_run", List.of(), "export");
        assertEquals("decision=dec-1 purpose=export rls=[rls-finance-all] cls=[NONE]"
                        + " predicate=(1=1) opa=allow",
                d.permissionScope());
        assertEquals("财务负责人全量导出 2026-10-15", d.watermark());
        assertEquals("dec-1", d.decisionId());
        assertFalse(d.guardrailUnevaluated());
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
                bundle(List.of(), "1=1", List.of(), GuardrailDecisionContract.STATE_ALLOW, null, null));

        ForbiddenOperationException ex = assertThrows(ForbiddenOperationException.class,
                () -> guard(client).exportDecision("forecast_run"));
        assertReasonContains(ex, "denyIfEmpty");
    }

    @Test
    @DisplayName("缺陷 D-B4-13 — 非平凡谓词（部门/项目维度）不可下推至导出件载体 ⇒ 403 而非全量外带")
    void nonTrivialPredicateCannotBePushedDownIsDeny() {
        WorkspaceSecurityEngineClient client = mock(WorkspaceSecurityEngineClient.class);
        when(client.decide(anyString(), anyList(), anyString())).thenReturn(
                bundle(List.of("rls-dept-scope"), "(department_id = :userDeptId)", List.of(), GuardrailDecisionContract.STATE_ALLOW, null, null));

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
                    bundle(List.of("rls-x"), spelling, List.of(), GuardrailDecisionContract.STATE_ALLOW, null, null));

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
        Map<String, Object> b = bundle(List.of("rls-finance-all"), "(1=1)", List.of(), GuardrailDecisionContract.STATE_ALLOW, null, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> rls = (Map<String, Object>) b.get("rls");
        rls.remove("policyRefs");
        b.remove("opa");

        ExportDecision d = ExportDecision.fromBundle("forecast_run", b);
        assertEquals(ExportDecision.SCOPE_NOT_WIRED, d.permissionScope());
        assertEquals(ExportDecision.WATERMARK_NOT_ISSUED, d.watermark());
        assertEquals("ABSENT", d.opaState());
        assertTrue(d.guardrailUnevaluated(), "opa 段缺失＝护栏未被证明咨询过，不得当成\"已裁决\"");
    }

    /**
     * 硬门·未评估态（B4-3c 开启，差距文档 D-B4-14/D-B4-18/D-B4-20/D-B4-21）。
     *
     * <p>B4-3b 的反向钉（"护栏未被咨询时不得 403，只记证据"）按其 javadoc 自述的承诺在此
     * <b>删除并翻正</b>：策略名已合规（单源 {@code data_access}），护栏态第一次承载真实语义，
     * 「护栏没给出裁决」必须报 503 {@code ECOS-SEC-501}（可告警、可重试）而不是 403 ——
     * §4.7.2「不可用 ≠ 无权限」；把它折叠成 403 等于把引擎缺陷伪装成用户无权限。</p>
     */
    @Test
    @DisplayName("硬门 503 — 护栏未给出裁决（POLICY_UNDEFINED / OPA_UNAVAILABLE / policyState 漂移）⇒ ECOS-SEC-501，禁折叠成 403")
    void unevaluatedGuardrailIs503NotADeny() {
        for (String state : List.of(GuardrailDecisionContract.STATE_POLICY_UNDEFINED,
                GuardrailDecisionContract.STATE_UNAVAILABLE)) {
            WorkspaceSecurityEngineClient client = mock(WorkspaceSecurityEngineClient.class);
            when(client.decide(anyString(), anyList(), anyString())).thenReturn(
                    bundle(List.of("rls-finance-all"), "(1=1)", List.of(), state,
                            "OPA_POLICY_UNDEFINED", null));

            SecurityEngineUnavailableException ex = assertThrows(
                    SecurityEngineUnavailableException.class,
                    () -> guard(client).exportDecision("forecast_run"), state);
            assertEquals(503, ex.getHttpStatus());
            assertEquals("ECOS-SEC-501", ex.getCode());
            assertTrue(ex.getTechnicalReason().contains("policyState=" + state),
                    "日志面必须带未评估态原值供回查，实得: " + ex.getTechnicalReason());
        }
        // 第三态：policyState 键漂移（旧形态应答）⇒ 无法证明护栏说过话，同走 503
        WorkspaceSecurityEngineClient client = mock(WorkspaceSecurityEngineClient.class);
        when(client.decide(anyString(), anyList(), anyString())).thenReturn(
                bundle(List.of("rls-finance-all"), "(1=1)", List.of(), null, null, null));

        assertThrows(SecurityEngineUnavailableException.class,
                () -> guard(client).exportDecision("forecast_run"),
                "缺 policyState 即护栏未被证明咨询过，禁按 allow 布尔冒充「已裁决」放行");
    }

    @Test
    @DisplayName("硬门 403 — 护栏真实策略拒绝（EVALUATED_DENY）⇒ 403 且带 deniedReason（rego violation 名可回查）")
    void evaluatedDenyIs403WithDeniedReason() {
        Map<String, Object> b = bundle(List.of("rls-proj-owner"), "(1=1)", List.of(),
                GuardrailDecisionContract.STATE_DENY, "AI_SCOPE_OUT_OF_SUBJECT_DEPARTMENT", null);
        ExportDecision d = ExportDecision.fromBundle("forecast_run", b);
        assertFalse(d.guardrailUnevaluated(), "EVALUATED_DENY 是「护栏说了话」的态，不得落 503 分支");
        assertTrue(d.guardrailDenied());
        assertEquals("deny:AI_SCOPE_OUT_OF_SUBJECT_DEPARTMENT", d.opaState());

        WorkspaceSecurityEngineClient client = mock(WorkspaceSecurityEngineClient.class);
        when(client.decide(anyString(), anyList(), anyString())).thenReturn(b);

        ForbiddenOperationException ex = assertThrows(ForbiddenOperationException.class,
                () -> guard(client).exportDecision("forecast_run"));
        assertReasonContains(ex, "OPA 策略裁决 DENY");
        assertEquals("deny:AI_SCOPE_OUT_OF_SUBJECT_DEPARTMENT", ex.getDetails().get("deniedReason"),
                "403 必须带上拒绝原因（rego violation 名），否则审计无从反查是哪条规则卡的");
    }

    @Test
    @DisplayName("漂移保守面 — denyIfEmpty 缺段按 true 处理（宁可拒绝也不放行）")
    void missingDenyIfEmptyFlagFailsClosed() {
        Map<String, Object> b = bundle(List.of(), "1=1", List.of(), GuardrailDecisionContract.STATE_ALLOW, null, null);
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

    /**
     * {@code DecisionBundle.toMap()} 的 REST 形态（键名与 security 侧同源，见契约断言）。
     *
     * @param opaPolicyState {@link GuardrailDecisionContract} 四态之一；传 null 即<b>不写</b>
     *                       {@code policyState} 键（旧形态漂移面，另有用例断其保守行为）
     */
    private static Map<String, Object> bundle(List<String> rlsRefs, String predicate,
                                              List<String> clsRefs, String opaPolicyState,
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
        opa.put("allow", GuardrailDecisionContract.STATE_ALLOW.equals(opaPolicyState));
        if (opaPolicyState != null) {
            opa.put("policyState", opaPolicyState);
        }
        opa.put("policyRef", "ecos." + GuardrailDecisionContract.POLICY_DATA_ACCESS);
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
     * 扫描某模块生产源码树，返回<b>代码位</b>（剥注释后）命中任一 needle 的文件清单。
     *
     * <p>解析出 0 个 {@code .java} 即失败 —— 禁把"没扫到文件"当成"没违例"（门禁空转）。</p>
     */
    private static List<String> findInCodePositions(String rootRepoRelative, String... needles)
            throws IOException {
        Path root = repoFile(rootRepoRelative);
        List<String> hits = new ArrayList<>();
        int scanned;
        try (Stream<Path> walk = Files.walk(root)) {
            List<Path> sources = walk.filter(p -> p.toString().endsWith(".java")).toList();
            scanned = sources.size();
            for (Path p : sources) {
                String code;
                try {
                    code = stripComments(Files.readString(p));
                } catch (IOException e) {
                    throw new IllegalStateException("读取失败: " + p, e);
                }
                for (String needle : needles) {
                    if (code.contains(needle)) {
                        hits.add(p.getFileName() + " 含 " + needle);
                    }
                }
            }
        }
        assertTrue(scanned > 0,
                "扫描根解析出 0 个 .java 源文件 = 门禁空转，禁把「没扫到」当成「没违例」: " + rootRepoRelative);
        return hits;
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
