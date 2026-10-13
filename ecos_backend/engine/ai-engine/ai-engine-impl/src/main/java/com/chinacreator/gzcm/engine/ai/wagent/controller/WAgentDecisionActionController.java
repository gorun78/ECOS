package com.chinacreator.gzcm.engine.ai.wagent.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ai.wagent.WAgentApiPaths;
import com.chinacreator.gzcm.engine.ai.wagent.orchestrator.WAgentOrchestrationException;
import com.chinacreator.gzcm.engine.ai.wagent.web.WAgentIdentityResolver;
import com.chinacreator.gzcm.engine.ai.wagent.web.WAgentIdentityResolver.Identity;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Decision / Action 端点（详细设计-10 §5.2 #23 recordDecision / #24 draftAction +
 * commitAction，附件 §9.2 双端点对齐）。
 *
 * <p><b>明确降级（无假真理）</b>：本组对象的存储面即 §6.2 表清单
 * {@code ecos_ai.ecos_wagent_decision/_option/_action/_action_effect} 登记为
 * <b>"（挂 V241 待定）"</b>——DDL 未落脚本、DAO/DO 未在位（wagent dao 包实测无
 * Decision/Action 数据面），且 #23 的"引用 pinned 评估记录"凭证校验与 #24b 的
 * "经属主 Engine 写端点提交"（§6.3：Agent 不写对方表）均未接线。⇒ 认证 + 入参
 * fail-fast 校验在位，落库/提交语义如实 {@code 501 NOT_IMPLEMENTED}，
 * <b>禁</b>先建内存台账伪称已记录（R-30/R-32 口径落地前零写面）。</p>
 */
@RestController
public class WAgentDecisionActionController {

    private final WAgentIdentityResolver identityResolver;

    public WAgentDecisionActionController(WAgentIdentityResolver identityResolver) {
        this.identityResolver = identityResolver;
    }

    /** #23 决策登记请求体（readiness_id 应指向 pinned 评估记录；仅形态校验，不落库）。 */
    public record DecisionRecordRequest(String questionId, String goalId, String readinessId,
                                        String rationaleText, String orgId) {
    }

    /** #24a 行动草稿请求体。 */
    public record ActionDraftRequest(String optionId, String title, String ownerId, String role,
                                     String dueDate, String kpiText, String publishTarget,
                                     String publishMode) {
    }

    /** #24b 提交请求体（L3：approval_token 必填）。 */
    public record ActionCommitRequest(String approvalToken) {
    }

    /** #23 决策记录（引用 pinned 评估）——V241 决策表未落地 ⇒ 501。 */
    @PostMapping(WAgentApiPaths.DECISIONS_COLLECTION)
    public ResponseEntity<ApiResponse<Map<String, Object>>> recordDecision(
            @RequestBody DecisionRecordRequest req) {
        identityResolver.require(); // 认证闸先行（fail-closed 401）
        if (req == null || isBlank(req.questionId())) {
            throw WAgentOrchestrationException.validation("question_id 必填（决策必须挂接问题面）");
        }
        if (isBlank(req.readinessId())) {
            throw WAgentOrchestrationException.validation("readiness_id 必填（§5.2 #23 须引用 pinned 评估记录）");
        }
        if (isBlank(req.rationaleText())) {
            throw WAgentOrchestrationException.validation("rationale_text 必填（决策必须留痕）");
        }
        return WAgentDegradation.notImplemented(
                "§6.2 V241 待定：ecos_wagent_decision/_option 表未落地，pinned 评估凭证校验未接");
    }

    /** #24a 行动草稿——V241 行动表未落地 ⇒ 501。 */
    @PostMapping(WAgentApiPaths.ACTIONS_COLLECTION)
    public ResponseEntity<ApiResponse<Map<String, Object>>> draftAction(
            @RequestBody ActionDraftRequest req) {
        identityResolver.require();
        if (req == null || isBlank(req.title())) {
            throw WAgentOrchestrationException.validation("title 必填（行动草稿须可读指派）");
        }
        if (isBlank(req.ownerId())) {
            throw WAgentOrchestrationException.validation("owner_id 必填（无主行动禁草稿）");
        }
        return WAgentDegradation.notImplemented(
                "§6.2 V241 待定：ecos_wagent_action/_action_effect 表未落地（R-30/R-32 裁决口径）");
    }

    /** #24b 行动提交（L3，经属主 Engine 写端点）——提交通道与凭证校验未接线 ⇒ 501。 */
    @PostMapping(WAgentApiPaths.ACTION_COMMIT)
    public ResponseEntity<ApiResponse<Map<String, Object>>> commitAction(
            @PathVariable(WAgentApiPaths.P_ACTION_ID) String actionId,
            @RequestBody(required = false) ActionCommitRequest req) {
        identityResolver.require();
        if (req == null || isBlank(req.approvalToken())) {
            throw WAgentOrchestrationException.validation("approval_token 必填（L3 提交凭证，§5.2 #24）");
        }
        return WAgentDegradation.notImplemented(
                "§6.2 V241 待定：action 存储未落地 + 属主 Engine 写端点通道未接（§6.3 Agent 不写对方表）");
    }

    private static boolean isBlank(String s) {
        return s == null || s.isBlank();
    }
}
