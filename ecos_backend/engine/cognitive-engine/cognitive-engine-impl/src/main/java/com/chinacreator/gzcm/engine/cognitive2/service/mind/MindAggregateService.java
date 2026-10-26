package com.chinacreator.gzcm.engine.cognitive2.service.mind;

import com.chinacreator.gzcm.engine.cognitive.api.CognitiveErrorCode;
import com.chinacreator.gzcm.engine.cognitive.api.mind.MindUpdateVO;
import com.chinacreator.gzcm.engine.cognitive2.dao.MindAssetDao;
import com.chinacreator.gzcm.engine.cognitive2.dao.MindUpdateDao;
import com.chinacreator.gzcm.engine.cognitive2.service.CognitiveConfigQueryService;
import com.chinacreator.gzcm.engine.cognitive2.service.scenario.CognitiveGuardException;
import com.chinacreator.gzcm.sysman.iam.context.UserContext;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * R4 Mind 聚合根读面 + 候选更新闸门服务（详设 v2.1-R4 §8.1.4/§8.4.2 / 差距分析 P0-6 /
 * B3 认知治理批 #141）。
 *
 * <p><b>诚实边界（禁假绿）</b>：</p>
 * <ul>
 *   <li>Mind 详情六段投影中，V292 加列未落库的库上：三轴（validity/review/adoption）与
 *       版本历史字段按「未迁移」回显 null（前置于 {@link MindAssetDao#r4AxisColumnsExist()}
 *       information_schema 探测），<b>禁</b>折叠成 DRAFT/PENDING_REVIEW 假值；</li>
 *   <li>V187 单 {@code status} 列只停写不删（IR03），本服务读面<b>恒不读</b>该列——
 *       治理真值只走三轴；</li>
 *   <li>{@code ACCEPTED} 终态落库为闸门行复核留痕；「受理后同步写正式资产新版本行
 *       （superseded_by 链）」的承流面未实现，作为 D-B3-1 登记项显式挂账
 *       （只登记不裁，禁以闸门终态冒充正式资产已更新）；</li>
 *   <li>闸门表缺表 ⇒ 503 {@code ECOS-COG-501}（同 mind 属主表 schema 预检先例），
 *       透传通道故障不折叠。</ul>
 */
@Service
public class MindAggregateService {

    private static final String OP_SYSTEM = "system";

    private final MindAssetDao mindDao;
    private final MindUpdateDao mindUpdateDao;
    private final CognitiveConfigQueryService configQueryService;

    public MindAggregateService(MindAssetDao mindDao,
                                MindUpdateDao mindUpdateDao,
                                CognitiveConfigQueryService configQueryService) {
        this.mindDao = mindDao;
        this.mindUpdateDao = mindUpdateDao;
        this.configQueryService = configQueryService;
    }

    // ─── 前置门（同 C.1 序列）────────────────────────────────────
    private void assertEnabled() {
        if (!configQueryService.engineEnabled()) throw new CognitiveGuardException(
                CognitiveErrorCode.E500_ENGINE_DISABLED,
                CognitiveErrorCode.MSG_ENGINE_NOT_ENABLED);
    }

    private void assertMindTable() {
        if (!mindDao.mindTableExists()) throw new CognitiveGuardException(
                CognitiveErrorCode.E501_SCHEMA_NOT_READY,
                CognitiveErrorCode.MSG_SCHEMA_NOT_READY);
    }

    private void assertGateTable() {
        if (!mindUpdateDao.mindUpdateTableExists()) throw new CognitiveGuardException(
                CognitiveErrorCode.E501_SCHEMA_NOT_READY,
                CognitiveErrorCode.MSG_SCHEMA_NOT_READY);
    }

    // ─── Mind 聚合根读面（#/decision/mind 详情态）────────────────────
    /**
     * Mind 列表（#/decision/mind 列表态）：既有五字段瘦投影 + 三轴可空。
     *
     * <p>三轴按 {@link MindAssetDao#r4AxisColumnsExist()} 一次性探测：未落库的库上
     * 全行 {validityStatus, reviewStatus, adoptionStatus} = null（禁折叠成假值），
     * {@code r4ColumnsProvisioned=false} 显式告知前端渲染「未迁移」横幅。</p>
     *
     * @param limitRaw 上限夹取 1~200（缺省 50，DAO 侧兜底）。
     * @return 每行键：{mindId, mindKey, name, description, isActive,
     *         validityStatus, reviewStatus, adoptionStatus}，外层附 r4ColumnsProvisioned。
     */
    public Map<String, Object> mindList(Integer limitRaw) {
        assertEnabled();
        assertMindTable();
        int limit = limitRaw == null ? 50 : limitRaw;
        List<Map<String, Object>> rows = mindDao.listAll(limit);
        boolean r4Ready = mindDao.r4AxisColumnsExist();
        List<Map<String, Object>> out = new ArrayList<>(rows.size());
        for (Map<String, Object> r : rows) {
            Map<String, Object> vo = new LinkedHashMap<>();
            vo.put("mindId",         r.get("id"));
            vo.put("mindKey",        r.get("mind_key"));
            vo.put("name",           r.get("name"));
            vo.put("description",    r.get("description"));
            vo.put("isActive",       r.get("is_active"));
            vo.put("validityStatus", null);
            vo.put("reviewStatus",   null);
            vo.put("adoptionStatus", null);
            out.add(vo);
        }
        // r4Ready 时批量回填三轴（无 N+1；DAL 一层 IN 查询）
        if (r4Ready && !out.isEmpty()) {
            List<String> ids = new ArrayList<>(out.size());
            for (Map<String, Object> vo : out) ids.add(String.valueOf(vo.get("mindId")));
            Map<String, Map<String, Object>> axes = mindDao.readAxisViews(ids);
            for (Map<String, Object> vo : out) {
                Map<String, Object> a = axes.get(String.valueOf(vo.get("mindId")));
                if (a != null) {
                    vo.put("validityStatus", a.get("validity_status"));
                    vo.put("reviewStatus",   a.get("review_status"));
                    vo.put("adoptionStatus", a.get("adoption_status"));
                }
            }
        }
        Map<String, Object> wrap = new LinkedHashMap<>();
        wrap.put("r4ColumnsProvisioned", r4Ready);
        wrap.put("items", out);
        return wrap;
    }

    /**
     * 单 Mind 详情投影：既有五字段 + R4 三轴 + 版本历史字段。
     *
     * @return 键：mindId/mindKey/name/description/capabilityMask/closedLoopBounds/
     *         validityStatus/reviewStatus/adoptionStatus/changeReason/supersededBy/
     *         r4ColumnsProvisioned（三轴列是否已落库）。
     */
    public Map<String, Object> mindView(String mindId) {
        assertEnabled();
        assertMindTable();
        if (!mindDao.mindExists(mindId)) throw new CognitiveGuardException(
                CognitiveErrorCode.E400_MIND_NOT_FOUND,
                CognitiveErrorCode.MSG_MIND_NOT_FOUND);
        Map<String, Object> out = new LinkedHashMap<>();
        out.put("mindId", mindId);
        boolean r4Ready = mindDao.r4AxisColumnsExist();
        out.put("r4ColumnsProvisioned", r4Ready);
        if (r4Ready) {
            Map<String, Object> axis = mindDao.readAxisView(mindId);
            out.put("validityStatus",   axis == null ? null : axis.get("validity_status"));
            out.put("reviewStatus",     axis == null ? null : axis.get("review_status"));
            out.put("adoptionStatus",   axis == null ? null : axis.get("adoption_status"));
            out.put("changeReason",     axis == null ? null : axis.get("change_reason"));
            out.put("supersededBy",     axis == null ? null : axis.get("superseded_by"));
        } else {
            // 未迁移诚实态：显式 null，前端按「未迁移」横幅展示，禁假值
            out.put("validityStatus",   null);
            out.put("reviewStatus",     null);
            out.put("adoptionStatus",   null);
            out.put("changeReason",     null);
            out.put("supersededBy",     null);
        }
        return out;
    }

    // ─── 候选更新闸门（W Agent 通道等价字面 = 提交通道）────────────────
    /** 提交候选：恒落 PROPOSED。四要件：agentRunId / targetType 词表 / changeJson / mind 存在。 */
    public MindUpdateVO submitProposal(String mindId, String agentRunId, String targetType,
                                       String targetId, String changeJson,
                                       String evidenceRefsJson, String operator) {
        assertEnabled();
        assertGateTable();
        if (!mindDao.mindExists(mindId)) throw new CognitiveGuardException(
                CognitiveErrorCode.E400_MIND_NOT_FOUND,
                CognitiveErrorCode.MSG_MIND_NOT_FOUND);
        if (agentRunId == null || agentRunId.isBlank()) throw new CognitiveGuardException(
                CognitiveErrorCode.E400_INVALID_PARAM,
                CognitiveErrorCode.MSG_INVALID_PARAM);
        if (!MindUpdateVO.isTargetType(targetType)) throw new CognitiveGuardException(
                CognitiveErrorCode.E400_INVALID_PARAM,
                CognitiveErrorCode.MSG_INVALID_PARAM);
        if (changeJson == null || changeJson.isBlank()) throw new CognitiveGuardException(
                CognitiveErrorCode.E400_INVALID_PARAM,
                CognitiveErrorCode.MSG_INVALID_PARAM);
        String id = UUID.randomUUID().toString();
        Map<String, Object> row = mindUpdateDao.insertProposed(id, mindId, agentRunId,
                targetType, targetId, changeJson, evidenceRefsJson, resolveOperator(operator));
        return toVO(row);
    }

    /** 按 Mind 列候选（倒序；limit 夹取 1~100，DAO 侧兜底）。 */
    public List<MindUpdateVO> listProposals(String mindId, Integer limitRaw) {
        assertEnabled();
        assertGateTable();
        if (!mindDao.mindExists(mindId)) throw new CognitiveGuardException(
                CognitiveErrorCode.E400_MIND_NOT_FOUND,
                CognitiveErrorCode.MSG_MIND_NOT_FOUND);
        int limit = limitRaw == null ? 20 : limitRaw;
        List<Map<String, Object>> rows = mindUpdateDao.listByMind(mindId, limit);
        List<MindUpdateVO> out = new ArrayList<>(rows.size());
        for (Map<String, Object> r : rows) out.add(toVO(r));
        return out;
    }

    /** 回读单行（未找到/已软删 ⇒ 404 ECOS-COG-404U，DAO 已承载）。 */
    public MindUpdateVO getProposal(String updateId) {
        assertEnabled();
        assertGateTable();
        return toVO(mindUpdateDao.findById(updateId));
    }

    /**
     * 受控置终态（唯一写 ACCEPTED/REJECTED 入口）。
     * 复核原因必填（留痕四要件）；行缺失 ⇒ 404U；已终态 ⇒ 409U；并发终态由
     * DAO 的 {@code status='PROPOSED'} WHERE 守卫兜底。
     */
    public MindUpdateVO finalizeProposal(String updateId, String newStatus, String reviewReason,
                                         String operator) {
        assertEnabled();
        assertGateTable();
        if (!MindUpdateVO.STATUS_ACCEPTED.equals(newStatus)
                && !MindUpdateVO.STATUS_REJECTED.equals(newStatus)) {
            throw new CognitiveGuardException(CognitiveErrorCode.E400_INVALID_PARAM,
                    CognitiveErrorCode.MSG_INVALID_PARAM);
        }
        if (reviewReason == null || reviewReason.isBlank()) throw new CognitiveGuardException(
                CognitiveErrorCode.E400_INVALID_PARAM, CognitiveErrorCode.MSG_INVALID_PARAM);
        // 先回读：区分 404（行不存在）与 409（已终态）的判据
        Map<String, Object> existing = mindUpdateDao.findById(updateId); // 缺失 → 404U
        if (MindUpdateVO.isTerminal((String) existing.get("status"))) {
            throw new CognitiveGuardException(CognitiveErrorCode.E409_MIND_UPDATE_STATE,
                    CognitiveErrorCode.MSG_MIND_UPDATE_FINALIZED);
        }
        int n = mindUpdateDao.finalizeProposal(updateId, newStatus,
                resolveOperator(operator), reviewReason, resolveOperator(operator));
        if (n == 0) { // 并发已被置终态（回读与更新之间）→ 409 而非 404
            throw new CognitiveGuardException(CognitiveErrorCode.E409_MIND_UPDATE_STATE,
                    CognitiveErrorCode.MSG_MIND_UPDATE_FINALIZED);
        }
        return toVO(mindUpdateDao.findById(updateId));
    }

    // ─── 装配 ─────────────────────────────────────────────────

    /** 操作者单源：显式入参 &gt; 登录上下文 &gt; "system"（同 OutcomeEvaluateService 先例）。 */
    private static String resolveOperator(String explicit) {
        if (explicit != null && !explicit.isBlank()) return explicit.trim();
        try {
            String u = UserContext.getCurrentUsername();
            if (u != null && !u.isBlank()) return u;
        } catch (Exception ignored) { /* 无请求上下文（如测试线程）→ 走 system */ }
        return OP_SYSTEM;
    }

    private static MindUpdateVO toVO(Map<String, Object> r) {
        return new MindUpdateVO(
                str(r.get("id")), str(r.get("mind_id")), str(r.get("agent_run_id")),
                str(r.get("target_type")), str(r.get("target_id")), str(r.get("change_json")),
                str(r.get("evidence_refs_json")), str(r.get("status")),
                str(r.get("reviewed_by")), r.get("reviewed_at"), str(r.get("review_reason")),
                str(r.get("trace_id")), str(r.get("create_time")));
    }

    private static String str(Object o) {
        return o == null ? null : String.valueOf(o);
    }
}
