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
import com.chinacreator.gzcm.common.security.rls.RlsExecutionContract;
import com.chinacreator.gzcm.workspace.exception.SecurityEngineUnavailableException;
import com.chinacreator.gzcm.workspace.scenario.ForbiddenOperationException;

/**
 * 导出通道契约与接线钉（#142/B4-3b + B4-3c，差距文档 D-B4-10/D-B4-11/D-B4-13/D-B4-14/
 * D-B4-16/D-B4-18/D-B4-21/D-B4-22）。
 *
 * <p><b>本类解决的假绿问题</b>：workspace 侧 {@code ExportDecision} 消费的是
 * security-engine REST 应答里的<b>字符串键名</b>（{@code rls.policyRefs} 等），
 * 两侧分处不同 Maven 模块、编译期零耦合 —— security 侧改键名时 workspace 侧只会
 * <b>静默读到 null</b>，导出件随即退回"无证据"形态而不报错。因此这里做五件编译做不到的事：</p>
 * <ol>
 *   <li>{@link #bundleKeyNamesMatchSecurityEngineSource()} —— 直接读 security-engine 生产源文件，
 *       对键名字面量做双向核对（源文件找不到即失败，禁跳过）。#142/B4-3d-5 起分两形态：
 *       无单源常量的 bundle 键仍按字面量成对核对；RLS 执行契约面／护栏义务键改由
 *       {@code assertReadThroughSingleSourceConstant} 三段核对（消费侧引常量 + 单源定义该字面量
 *       + 消费侧代码位禁第二形态内联），覆盖面严于原单向字面量核对，不因形态迁移而放宽。
 *       <b>#142/B4-3d-7 起 RLS 段从"键名核对"升级为"取面核对"</b>（差距 D-B4-24b）：
 *       键名收进单源常量并不等于读对了面 —— 表达面 {@code predicateTemplate} 不含渲染器的
 *       拒绝归一，「一条策略都没命中」在表达面是 {@code 1=1}、在执行契约面才是 {@code 1=0}
 *       ＋机读理由，故消费侧只准整体委托 {@code RlsExecutionContract.read}，
 *       自取两面键（常量形态或字面量形态）一律即红；</li>
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
 *       workspace 靠 {@code OPA_ERROR_FAIL_CLOSED} 反推状态）回潮同样即红；</li>
 *   <li>D-B4-24b 的两条<b>故意分叉</b>取面钉（{@link #executionFaceDenyOverridesAllowLookingTemplate}、
 *       {@link #executionFaceAllowOverridesDenyLookingTemplate}）——夹具把两面的真实配对摊开，
 *       读错面即用例红，禁把"两面同值"的夹具当成"契约已覆盖"。</li>
 * </ol>
 */
@DisplayName("导出通道：跨模块键名契约 + 取面钉 + 单入口接线 + 咽喉分态 + 词表单源")
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
        // #142/B4-3d-6 起 "columns" 段键的字面量唯一落点搬进 DecisionChannelContract.KEY_BUNDLE_COLUMNS
        // （toMap 侧改引常量后本字面量在 bundleSrc 消失）——不是放宽：改按单源常量形态核对
        // （第 1 段生产侧引常量 + 第 2 段单源定义该字面量 + 第 3 段生产侧代码位禁内联该字面量回潮），
        // 覆盖面严于原字面量单段核对，键名漂移同样变红。
        for (String key : List.of("\"rls\"", "\"masks\"", "\"opa\"",
                "\"decisionId\"", "\"traceId\"")) {
            assertTrue(bundleSrc.contains(key), "DecisionBundle.toMap 键集变化须同步消费侧: " + key);
        }
        String channelContractSrc = readRepoFile(
                "ecos_backend/runtime/common-api/src/main/java/"
                        + "com/chinacreator/gzcm/common/security/decision/DecisionChannelContract.java");
        String bundleCode = stripComments(bundleSrc);
        assertReadThroughSingleSourceConstant(bundleCode, channelContractSrc,
                "KEY_BUNDLE_COLUMNS", "columns", "DecisionBundle");
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
        assertReadThroughSingleSourceConstant(consumerCode, guardrailContractSrc,
                "KEY_OBLIGATIONS", "obligations");
        assertReadThroughSingleSourceConstant(consumerCode, guardrailContractSrc,
                "KEY_DENIED_REASON", "deniedReason");
        assertReadThroughSingleSourceConstant(consumerCode, guardrailContractSrc,
                "KEY_OBLIGATION", "obligation");
        assertReadThroughSingleSourceConstant(consumerCode, guardrailContractSrc,
                "OBLIGATION_WATERMARK", "watermark");

        // RLS 面（#142/B4-3d-7 收口差距 D-B4-24b 后改判）：
        // 旧断言钉的是「ExportDecision 经单源常量读 predicateTemplate / denyIfEmpty」——
        // 常量形态确实不再有第二套键名，但<b>面</b>读错了：表达面不含渲染器的拒绝归一，
        // 零命中在表达面是 "1=1"（看起来像放行）、在执行契约面才是 1=0 + 机读理由。
        // 因此本门从「键名核对」升级为「取面核对」：必须整体委托 RlsExecutionContract.read，
        // 两面键（predicateTemplate / denyIfEmpty）无论以常量名还是字面量形态重现即红。
        // 放宽回去＝把真门改成假门，故禁删本段，只许再加严。
        assertTrue(consumerCode.contains("RlsExecutionContract.read("),
                "D-B4-24b 违规: ExportDecision 未整体委托单源读执行契约（自取键子集＝第二套分态口径）");
        assertTrue(rlsContractSrc.contains("KEY_WHERE_CLAUSE = \"whereClause\""),
                "单源的执行面键定义漂移（消费侧按 read 取数会静默读到缺失）");
        assertTrue(expressionFaceReads(consumerCode).isEmpty(),
                "D-B4-24b 违规: ExportDecision 仍在自选 RLS 面键分态: " + expressionFaceReads(consumerCode));
        // 执行态四判据必须来自状态而非文本比对（旧形态按 normalize 后的字面量分三态）
        for (String accessor : List.of("rlsExecution.denied()", "rlsExecution.allowAll()",
                "rlsExecution.filtered()", "rlsExecution.noPolicyMatched()", "rlsExecution.denyIfEmpty()")) {
            assertTrue(consumerCode.contains(accessor),
                    "D-B4-24b 违规: ExportDecision 未用单源状态判据 " + accessor + "（退回字面量比对即门禁空转）");
        }
        // 门禁有效性对照（禁把本门做成恒绿的假门）：收口前的取面形状必须被同一条判据抓到，
        // 且收口后的委托形状不得被误报——两者缺一，本门就是噪声或空转。
        assertTrue(!expressionFaceReads(stripComments(
                "String t = String.valueOf(rls.get(RlsExecutionContract.KEY_PREDICATE_TEMPLATE));")).isEmpty(),
                "取面判据对 D-B4-24b 缺陷原形不命中＝假门禁，必须修判据而不是删用例");
        assertTrue(!expressionFaceReads(stripComments(
                "boolean deny = Boolean.TRUE.equals(rls.get(\"denyIfEmpty\"));")).isEmpty(),
                "取面判据对字面量形态不命中＝假门禁");
        assertTrue(expressionFaceReads(stripComments(
                "RlsExecutionContract.Execution exec = RlsExecutionContract.read(bundle.get(\"rls\"), subject);")).isEmpty(),
                "收口后的委托形状被误报（判据与生产码同判即噪声）");
    }

    /**
     * 取面判据（真实文件与门禁有效性对照共用同一份，禁两处各写一套）：
     * 消费侧代码位出现 RLS 表达面键／单键自读（常量名或字面量两种形态）即违例。
     */
    private static List<String> expressionFaceReads(String strippedConsumerCode) {
        List<String> hits = new ArrayList<>();
        for (String bannedConstant : List.of("KEY_PREDICATE_TEMPLATE", "KEY_DENY_IF_EMPTY")) {
            if (strippedConsumerCode.contains(bannedConstant)) {
                hits.add("自读单源键常量 " + bannedConstant);
            }
        }
        for (String bannedLiteral : List.of("\"predicateTemplate\"", "\"denyIfEmpty\"", "\"whereClause\"")) {
            if (strippedConsumerCode.contains(bannedLiteral)) {
                hits.add("代码位内联 RLS 面键 " + bannedLiteral);
            }
        }
        return hits;
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
        assertReadThroughSingleSourceConstant(consumerCode, contractSrc, constName, wireKey, "ExportDecision");
    }

    /**
     * 同上，多一个位点名（生产侧等其它代码位复用同一三段核对时区分排障信息）。
     */
    private static void assertReadThroughSingleSourceConstant(String siteCode, String contractSrc,
            String constName, String wireKey, String siteLabel) {
        assertTrue(siteCode.contains(constName),
                siteLabel + " 未引单源常量 " + constName + "（" + wireKey + " 的读取面失控）");
        assertTrue(contractSrc.contains(constName + " = \"" + wireKey + "\""),
                "单源常量 " + constName + " 不再定义为 \"" + wireKey + "\"：线契约键名漂移");
        assertFalse(siteCode.contains("\"" + wireKey + "\""),
                siteLabel + " 代码位内联 \"" + wireKey + "\" = 绕过单源的第二形态，漂移将静默失配");
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
        // security 侧生产面写入 policyState（护栏态是 403/503 分态的唯一依据）。
        // #142/B4-3e-2 收口形态：生产侧与消费侧一律经单源常量，禁任一侧内联字面量
        // （本断言原钉 "policyState" 裸字面量，那是收口前的形态，钉它会与单源门禁互相矛盾）。
        assertTrue(decisionSrc.contains("GuardrailDecisionContract.KEY_POLICY_STATE"),
                "裁决入口必须把护栏态写进 bundle.opa，否则消费侧只能靠布尔反推（旧假绿形态）");
        assertReadThroughSingleSourceConstant(decisionSrc, contractSrc,
                "KEY_POLICY_STATE", "policyState", "security 裁决生产面");
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
        // #146h 遗留红收口：本条原钉 gateSrc 含 evaluateBodyForAdjudication(，
        // 咽喉接线自 #142/B4-3e-78（D-B4-30）并流 /decision 后改经 AiSecurityEngineClient
        // 的 decisionBody＋decideData，本行漏更（B4-3e-78 只实跑 ai/security/common-api
        // 三模块，workspace 相从未跑到）掩盖成脱案红 —— 现钉当前咽喉的在位形态：
        // 裁决体仍必须经咽喉单源构造，禁调用方另拼 policy 字段（汇编位置既非闸也非 client 即红）。
        assertTrue(gateSrc.contains("AiSecurityEngineClient.decisionBody(")
                        && gateSrc.contains(".decideData("),
                "工具裁决闸必须经咽喉单源构造裁决体（#142/B4-3e-78 起主裁决腿并流 /decision，"
                        + "键面经 AiSecurityEngineClient.decisionBody、传输经 decideData），"
                        + "禁在调用方另拼 policy 字段");

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
                        + " rlsState=ALLOW_ALL predicate=(1=1) opa=allow",
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

    /**
     * 无权用户行（0 命中策略）⇒ 403。
     *
     * <p><b>#142/B4-3d-7 翻正（差距 D-B4-24b，原文保留）</b>：本用例旧形态只把
     * {@code predicateTemplate="1=1"} 写进夹具并断理由含 {@code denyIfEmpty} ——
     * 那钉的是"消费者靠 {@code policyRefs} 读空反推裁决"这条<b>第二套口径</b>，
     * 而生产者的真实配对是「表达面 {@code 1=1} ＋执行面 {@code 1=0} +
     * {@code RLS_NO_POLICY_MATCHED}」。现断言改钉执行契约面的机读理由（DENIED 态第一道分支），
     * 键漂移时不再静默放行；「发恒真片段却自报未命中」这类异常配对由
     * {@link #selfReportedNoPolicyMatchedWithAllowAllClauseIsSecondGate} 单独钉第二道。</p>
     */
    @Test
    @DisplayName("无权用户行 — 0 命中策略（生产配对：表达面 1=1 + 执行面 1=0）⇒ 403 且带生产者机读理由")
    void noPolicyMatchedWithDenyIfEmptyIsDeny() {
        WorkspaceSecurityEngineClient client = mock(WorkspaceSecurityEngineClient.class);
        when(client.decide(anyString(), anyList(), anyString())).thenReturn(
                bundle(List.of(), "1=1", List.of(), GuardrailDecisionContract.STATE_ALLOW, null, null));

        ForbiddenOperationException ex = assertThrows(ForbiddenOperationException.class,
                () -> guard(client).exportDecision("forecast_run"));
        assertReasonContains(ex, PRODUCTION_ZERO_POLICY_REASON);
        assertEquals("DENIED", ex.getDetails().get("state"),
                "拒绝必须落执行契约状态（供审计分态检索），实得: " + ex.getDetails());
    }

    /**
     * 第二道门：应答发的是<b>恒真片段</b>但自报「一条策略都没命中」。
     *
     * <p>真实生产者不会产生这组配对（零命中已被渲染成 {@code 1=0}），所以本用例钉的是
     * <b>漂移面</b>：{@code RlsExecutionContract.read} 只在执行面判态，遇到这种自相矛盾的应答
     * 保持 ALLOW_ALL 态，导出咽喉据 {@code noPolicyMatched && denyIfEmpty} 仍须 DENY。
     * 少了这道门，"执行面恒真 + 出处读空"就成了新的 fail-open 入口。</p>
     */
    @Test
    @DisplayName("第二道门 — 执行面恒真但自报未命中策略 ⇒ 仍 403（禁把「没出处」当成「不限制行」）")
    void selfReportedNoPolicyMatchedWithAllowAllClauseIsSecondGate() {
        WorkspaceSecurityEngineClient client = mock(WorkspaceSecurityEngineClient.class);
        when(client.decide(anyString(), anyList(), anyString())).thenReturn(
                bundleWithRls(rlsSegment(List.of("rls-silent"), "1=1", "(1=1)", List.of(), true, null),
                        List.of(), GuardrailDecisionContract.STATE_ALLOW, null, null));

        ForbiddenOperationException ex = assertThrows(ForbiddenOperationException.class,
                () -> guard(client).exportDecision("forecast_run"));
        assertReasonContains(ex, "denyIfEmpty");
    }

    /**
     * 取面钉·拒绝半边（D-B4-24b 的缺陷原形）。
     *
     * <p>两面<b>故意分叉</b>：表达面 {@code "(1=1)"}（旧写法据此判 ALLOW_ALL ⇒ 放行导出），
     * 执行契约面 {@code 1=0} ＋理由。本用例只有在消费者<b>读执行面</b>时才绿；
     * 若有人把 {@code ExportDecision} 改回读 {@code predicateTemplate}，本用例即红 ——
     * 这就是本类对 D-B4-24b 的可执行钉，禁以"注释里写了要求"替代。</p>
     */
    @Test
    @DisplayName("取面钉 — 表达面看着放行、执行面拒绝 ⇒ 必须 403（读表达面＝D-B4-24b 缺陷原形）")
    void executionFaceDenyOverridesAllowLookingTemplate() {
        Map<String, Object> b = bundleWithRls(
                rlsSegment(List.of("rls-x"), "(1=1)", RlsExecutionContract.DENY_CLAUSE,
                        List.of(), false, "RLS_TEMPLATE_REJECTED: lexically illegal"),
                List.of(), GuardrailDecisionContract.STATE_ALLOW, null, null);

        ExportDecision d = ExportDecision.fromBundle("forecast_run", b);
        assertEquals(RlsExecutionContract.State.DENIED, d.rlsState(),
                "表达面是恒真文本，态必须取自执行契约面");

        WorkspaceSecurityEngineClient client = mock(WorkspaceSecurityEngineClient.class);
        when(client.decide(anyString(), anyList(), anyString())).thenReturn(b);
        ForbiddenOperationException ex = assertThrows(ForbiddenOperationException.class,
                () -> guard(client).exportDecision("forecast_run"));
        assertReasonContains(ex, "RLS_TEMPLATE_REJECTED");
    }

    /** 取面钉·放行半边：反向分叉（表达面像拒绝文本、执行面恒真）⇒ 必须放行，证明判据只认执行面。 */
    @Test
    @DisplayName("取面钉 — 表达面写着 1=0、执行契约给恒真 ⇒ 放行（禁把文本比对当分态依据）")
    void executionFaceAllowOverridesDenyLookingTemplate() {
        Map<String, Object> b = bundleWithRls(
                rlsSegment(List.of("rls-finance-all"), "1=0", "(1=1)", List.of(), false, null),
                List.of(), GuardrailDecisionContract.STATE_ALLOW, null, null);

        WorkspaceSecurityEngineClient client = mock(WorkspaceSecurityEngineClient.class);
        when(client.decide(anyString(), anyList(), anyString())).thenReturn(b);

        ExportDecision d = guard(client).exportDecision("forecast_run");

        assertEquals(RlsExecutionContract.State.ALLOW_ALL, d.rlsState());
        assertTrue(d.permissionScope().contains("rlsState=ALLOW_ALL"),
                "范围证据必须带状态，审计才分得开「不限制行」与「被载体挡住的过滤」: " + d.permissionScope());
    }

    /**
     * D-B4-24b 的后果里最重的一种漂移形态：<b>应答根本没发执行契约面</b>（旧 security 版本／
     * {@code asPredicatePart} 的 {@code copyIfPresent} 因源缺键而跳过）。
     *
     * <p>收口前 {@code ExportDecision} 读表达面 {@code predicateTemplate="1=1"} ⇒ 判成恒真放行，
     * 导出件于是带着「范围证据齐全」的外观把全表捞出去；收口后单源把该形态归一为
     * {@link RlsExecutionContract#REASON_WHERE_ABSENT}（D-B4-17 同源理由码）⇒ 403。
     * 本用例是"读错面"与"读对面"的分水岭，禁删。</p>
     */
    @Test
    @DisplayName("缺执行契约面 — 应答只给表达面 ⇒ 403 RLS_WHERE_CLAUSE_ABSENT，禁读成「不限制行」（fail-open 原形）")
    void absentExecutionFaceIsDenyNotAllowAll() {
        Map<String, Object> b = bundleWithRls(
                rlsSegment(List.of("rls-finance-all"), "1=1", "(1=1)", List.of(), false, null),
                List.of(), GuardrailDecisionContract.STATE_ALLOW, null, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> rls = (Map<String, Object>) b.get("rls");
        rls.remove(RlsExecutionContract.KEY_WHERE_CLAUSE);

        ExportDecision d = ExportDecision.fromBundle("forecast_run", b);
        assertEquals(RlsExecutionContract.State.DENIED, d.rlsState(),
                "表达面写着恒真不构成放行依据：缺执行契约即无法证明裁决可用");

        WorkspaceSecurityEngineClient client = mock(WorkspaceSecurityEngineClient.class);
        when(client.decide(anyString(), anyList(), anyString())).thenReturn(b);
        ForbiddenOperationException ex = assertThrows(ForbiddenOperationException.class,
                () -> guard(client).exportDecision("forecast_run"));
        assertReasonContains(ex, RlsExecutionContract.REASON_WHERE_ABSENT_CODE);
    }

    @Test
    @DisplayName("缺陷 D-B4-13 — 非平凡谓词（部门/项目维度）不可下推至导出件载体 ⇒ 403 而非全量外带")
    void nonTrivialPredicateCannotBePushedDownIsDeny() {
        // 生产者真实配对：模板 (department_id = :userDeptId) → 片段 (department_id = ?) + 1 个参数
        Map<String, Object> b = bundleWithRls(
                rlsSegment(List.of("rls-dept-scope"), "(department_id = :userDeptId)",
                        "(department_id = ?)", List.of("D-01"), false, null),
                List.of(), GuardrailDecisionContract.STATE_ALLOW, null, null);

        assertEquals(RlsExecutionContract.State.FILTERED, ExportDecision.fromBundle("forecast_run", b).rlsState());

        WorkspaceSecurityEngineClient client = mock(WorkspaceSecurityEngineClient.class);
        when(client.decide(anyString(), anyList(), anyString())).thenReturn(b);

        ForbiddenOperationException ex = assertThrows(ForbiddenOperationException.class,
                () -> guard(client).exportDecision("forecast_run"));
        assertReasonContains(ex, "无法下推");
        assertEquals("(department_id = ?)", ex.getDetails().get("predicate"),
                "拒绝原因必须带上<b>执行面</b>片段（可绑定形态），禁回显 :name 模板");
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
        // 两面分开给：执行面恒真（单源据此判 ALLOW_ALL，不进 DENIED 分支）＋自报未命中，
        // 于是本用例唯一考的就是「denyIfEmpty 键缺失时默认取保守值 true」。
        Map<String, Object> b = bundleWithRls(
                rlsSegment(List.of("rls-finance-all"), "1=1", "(1=1)", List.of(), true, null),
                List.of(), GuardrailDecisionContract.STATE_ALLOW, null, null);
        @SuppressWarnings("unchecked")
        Map<String, Object> rls = (Map<String, Object>) b.get("rls");
        rls.remove(RlsExecutionContract.KEY_DENY_IF_EMPTY);
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
     * <p><b>#142/B4-3d-7 起 rls 段按「两面分离」的生产者真实配对给</b>（差距 D-B4-24b）：
     * 表达面 {@code predicateTemplate} 出自 {@code RowLevelSecurityServiceImpl.mergePolicies}
     * （零命中时兜底文本是 {@code "1=1"}），执行契约面出自 {@code RlsPredicateRenderer.render}
     * （零命中归一为 {@code whereClause="1=0"} + {@code noPolicyMatched=true}
     * + {@code rlsDenyReason="RLS_NO_POLICY_MATCHED"}；{@code denyReason!=null} 时参数与
     * 未解析表恒空）。本方法据 {@code rlsRefs} 是否为空复刻这对配对，
     * 于是「读表达面」与「读执行面」在零命中用例里<b>必然给出相反结论</b> ——
     * 夹具若把两面写成同值，本类的取面钉就退化成恒绿噪声。</p>
     *
     * <p>需要<b>非平凡谓词</b>（FILTERED）或<b>故意让两面分叉</b>的用例不走本重载，
     * 直接经 {@link #rlsSegment} + {@link #bundleWithRls} 显式给执行面。</p>
     *
     * @param opaPolicyState {@link GuardrailDecisionContract} 四态之一；传 null 即<b>不写</b>
     *                       {@code policyState} 键（旧形态漂移面，另有用例断其保守行为）
     */
    private static Map<String, Object> bundle(List<String> rlsRefs, String predicateTemplate,
                                              List<String> clsRefs, String opaPolicyState,
                                              String deniedReason, String watermark) {
        boolean noPolicy = rlsRefs.isEmpty();
        return bundleWithRls(rlsSegment(rlsRefs, predicateTemplate,
                noPolicy ? RlsExecutionContract.DENY_CLAUSE : predicateTemplate,
                List.of(), noPolicy, noPolicy ? PRODUCTION_ZERO_POLICY_REASON : null),
                clsRefs, opaPolicyState, deniedReason, watermark);
    }

    /** 生产者零命中时的机读拒绝理由（{@code RlsPredicateRenderer.Rendered.deny} 的首参原文）。 */
    private static final String PRODUCTION_ZERO_POLICY_REASON = "RLS_NO_POLICY_MATCHED";

    /**
     * rls 段装配（键名一律取 {@link RlsExecutionContract} 常量 —— 与
     * {@code SecurityDecisionService.asPredicatePart} 的透传形态同形，含 {@code policyRefs} 追加）。
     *
     * @param predicateTemplate 表达面（含 {@code :name} 占位符的策略原文／兜底文本）
     * @param whereClause       执行契约面（渲染器产物：可绑定片段或 {@code 1=0}）
     */
    private static Map<String, Object> rlsSegment(List<String> rlsRefs, String predicateTemplate,
                                                  String whereClause, List<Object> whereParams,
                                                  boolean noPolicyMatched, String rlsDenyReason) {
        Map<String, Object> rls = new LinkedHashMap<>();
        rls.put(RlsExecutionContract.KEY_PREDICATE_TEMPLATE, predicateTemplate);
        rls.put("bindings", List.of());
        rls.put("combine", "AND");
        rls.put(RlsExecutionContract.KEY_DENY_IF_EMPTY, true);
        rls.put("policyRefs", rlsRefs);
        rls.put(RlsExecutionContract.KEY_WHERE_CLAUSE, whereClause);
        rls.put(RlsExecutionContract.KEY_WHERE_PARAMS, whereParams);
        rls.put(RlsExecutionContract.KEY_UNRESOLVED_BINDINGS, List.of());
        rls.put(RlsExecutionContract.KEY_NO_POLICY_MATCHED, noPolicyMatched);
        if (rlsDenyReason != null) {
            rls.put(RlsExecutionContract.KEY_RLS_DENY_REASON, rlsDenyReason);
        }
        return rls;
    }

    /** 用给定 rls 段形态组装完整 bundle（其余三段与本类既有夹具一致）。 */
    private static Map<String, Object> bundleWithRls(Map<String, Object> rls, List<String> clsRefs,
                                                     String opaPolicyState, String deniedReason,
                                                     String watermark) {
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
