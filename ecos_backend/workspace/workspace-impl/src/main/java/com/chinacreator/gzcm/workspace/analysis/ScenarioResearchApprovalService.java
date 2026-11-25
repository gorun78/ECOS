package com.chinacreator.gzcm.workspace.analysis;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.common.exception.NotFoundException;
import com.chinacreator.gzcm.common.security.header.ServiceContext;
import com.chinacreator.gzcm.workspace.dao.WorkspaceDataDao;

/**
 * L3 研究候选审批（可行性报告 v2.0 §5.5「候选唯一通道」，2026-11-25）。
 *
 * <p><b>候选是什么</b>：候选就是 {@code ecos_scenario_run} 里的一条 {@code RESEARCH_RUN} 行——
 * 不复制运行数据、不新建候选实体（禁双份真相）。审批只追加一条留痕（V366）。</p>
 *
 * <p><b>三道硬约束</b>：</p>
 * <ol>
 *   <li><b>批准必须带 approvalRef</b>：防「生成即审批」（与 data-governance 同口径）；</li>
 *   <li><b>append-only</b>：一次运行可先驳回再批准，历史逐条保留，禁 UPDATE 覆盖；</li>
 *   <li><b>只改审批态，不改运行数据</b>：不写蓝图状态机、不改场景指标、不把
 *       {@code run_mode} 从 SANDBOX 升成 FORMAL——正式口径仍须由人另行发起 FORMAL 运行。</li>
 * </ol>
 *
 * <p><b>审批 ≠ 生效</b>：本服务只在留痕表记「人已看过并接受这轮候选」，
 * 不做任何自动回写；下游若要据此推进蓝图，必须走各自既有的候选→发布通道。</p>
 */
@Service
public class ScenarioResearchApprovalService {

    private static final Logger log = LoggerFactory.getLogger(ScenarioResearchApprovalService.class);

    static final String APPROVED = "APPROVED";
    static final String REJECTED = "REJECTED";

    private final WorkspaceDataDao dao;

    public ScenarioResearchApprovalService(WorkspaceDataDao dao) {
        this.dao = dao;
    }

    /**
     * 审批一轮研究候选。
     *
     * @param decision    APPROVED / REJECTED（其它值 400）
     * @param approvalRef 批准时必填（驳回可空）
     */
    public Map<String, Object> decide(String runId, String decision, String approvalRef,
                                      String reason, String evidenceDigest) {
        // 先判存在性：运行不存在时不该被词表/凭据错误掩盖（404 优先于 400）
        List<Map<String, Object>> rows = dao.findResearchRun(runId);
        if (rows.isEmpty()) {
            throw new NotFoundException("RESEARCH-404: 研究运行不存在: " + runId);
        }

        String normalized = decision == null ? "" : decision.trim().toUpperCase();
        if (!APPROVED.equals(normalized) && !REJECTED.equals(normalized)) {
            throw new BusinessException(400, "RESEARCH-400: decision 只接受 APPROVED/REJECTED");
        }
        if (APPROVED.equals(normalized) && (approvalRef == null || approvalRef.isBlank())) {
            throw new BusinessException(400, "RESEARCH-400: 批准必须携带 approvalRef（禁生成即审批）");
        }

        Map<String, Object> run = rows.get(0);
        String scenarioId = str(run.get("scenario_id"));
        String stopReason = str(run.get("stop_reason"));
        String sotaRunId = str(run.get("sota_run_id"));

        String approver = currentUser();
        String roles = currentRoles();
        String digest = (evidenceDigest == null || evidenceDigest.isBlank())
                ? digestOf(runId, stopReason, String.valueOf(run.get("advice")), String.valueOf(run.get("exec_trace")))
                : evidenceDigest;

        String id = "apr-" + UUID.randomUUID().toString().substring(0, 12);
        dao.insertResearchApproval(id, runId, scenarioId, normalized,
                blankToNull(approvalRef), blankToNull(reason), approver, roles,
                blankToNull(stopReason), blankToNull(sotaRunId), digest);
        log.info("研究候选审批 run={} decision={} approver={} ref={}", runId, normalized, approver, approvalRef);

        Map<String, Object> out = new LinkedHashMap<>();
        out.put("approvalId", id);
        out.put("runId", runId);
        out.put("scenarioId", scenarioId);
        out.put("decision", normalized);
        out.put("approver", approver);
        out.put("approvalRef", blankToNull(approvalRef));
        out.put("evidenceDigest", digest);
        out.put("candidateOnly", true);
        out.put("note", "审批仅留痕：不自动回写蓝图/指标，正式口径须另行发起 FORMAL 运行");
        return out;
    }

    /** 某轮运行的审批历史（空=尚未审批）。 */
    public List<Map<String, Object>> history(String runId) {
        if (dao.findResearchRun(runId).isEmpty()) {
            throw new NotFoundException("RESEARCH-404: 研究运行不存在: " + runId);
        }
        return dao.listRunApprovals(runId);
    }

    // ── 主体与摘要 ───────────────────────────────────────────────────────────

    /** 取当前审批主体；取不到即 401，禁匿名审批（审批必须可追责）。 */
    private String currentUser() {
        ServiceContext ctx = ServiceContext.current();
        if (ctx != null && ctx.getUserId() != null && !ctx.getUserId().isBlank()) {
            return ctx.getUserId();
        }
        throw new BusinessException(401, 401, "RESEARCH-401: 审批需要已认证主体（信任头缺失）");
    }

    private String currentRoles() {
        try {
            ServiceContext ctx = ServiceContext.current();
            if (ctx != null && ctx.getRoles() != null && !ctx.getRoles().isBlank()) {
                return ctx.getRoles();
            }
        } catch (RuntimeException e) {
            log.debug("审批角色快照读取失败（不影响审批）: {}", e.getMessage());
        }
        return null;
    }

    /** 证据摘要：为「人审批时所依据的候选内容」留一个可回查的指纹。 */
    static String digestOf(String runId, String stopReason, String advice, String trace) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            String payload = runId + '|' + stopReason + '|' + advice + '|' + trace;
            byte[] hash = md.digest(payload.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder("sha256:");
            for (byte b : hash) {
                sb.append(String.format("%02x", b));
            }
            return sb.toString();
        } catch (Exception e) {
            return null;
        }
    }

    private static String str(Object v) {
        return v == null ? "" : String.valueOf(v);
    }

    private static String blankToNull(String v) {
        return v == null || v.isBlank() ? null : v;
    }
}
