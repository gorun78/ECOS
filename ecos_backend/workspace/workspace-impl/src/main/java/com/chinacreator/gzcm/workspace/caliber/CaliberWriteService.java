package com.chinacreator.gzcm.workspace.caliber;

import com.chinacreator.gzcm.workspace.caliber.CaliberOwnerClient.OwnerReply;
import com.chinacreator.gzcm.workspace.exception.ActionValidationException;
import com.chinacreator.gzcm.workspace.exception.CaliberApproverIdentityMissingException;
import com.chinacreator.gzcm.workspace.exception.CaliberOwnerFailureException;
import com.chinacreator.gzcm.workspace.exception.CaliberOwnerUnavailableException;
import com.chinacreator.gzcm.workspace.exception.CaliberStatePreconditionException;
import com.chinacreator.gzcm.workspace.exception.CaliberWriteNotLiveException;
import com.chinacreator.gzcm.workspace.scenario.ForbiddenOperationException;
import com.chinacreator.gzcm.workspace.scenario.WorkspaceSecurityAudit;
import com.chinacreator.gzcm.workspace.security.ScenarioReadGuard;
import com.chinacreator.gzcm.common.security.caller.CallerCredentialSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 口径写面编排服务（#146ad 附录A 第 31 条 {@code createCaliber} / {@code submitCaliber} /
 * {@code approveCaliber} / {@code supersedeCaliber}，契约行 = 详设-12 §12.4.2 line 8252~8255）。
 *
 * <h3>四枚逐枚定档（禁整族外推，形态不同的腿不许共用一个结论）</h3>
 * <ol>
 *   <li><b>create ＝ 真值双腿</b>：属主有 {@code POST /calibers}（主行）与
 *       {@code POST /calibers/{code}/versions}（版本行）两条写入口，契约的「新建 DRAFT 口径」
 *       由这两条腿合成。两次调用不在同一事务内 ⇒ 响应显式 {@code atomic=false}；
 *       腿二失败时主行成为孤儿，本面<b>不静默回滚也绝不谎报「已回滚」</b>——
 *       以 {@code log.error} 留 caliberId/code 取证后原样上抛（跨引擎补偿事务需属主侧提供，另批）。</li>
 *   <li><b>approve ＝ 真值单腿</b>：属主 {@code .../approve} 端点在
 *       route-manifest {@code route="monolith"} 下经 gateway :8080 承流，审批主语由
 *       {@code CaliberController#approve} 从 {@code UserContext} 取，委托侧经
 *       {@link CaliberOwnerClient} 的凭证中继即可把 JWT 主体送到位 ⇒ 状态行与 {@code approvedBy}
 *       都是属主真值，不是合成值。<b>契约「非 PENDING_APPROVAL ⇒ 409」的判据落不到那个状态名上</b>
 *       （属主状态机白名单只有 DRAFT/REVIEWING/APPROVED/RETIRED），本面按属主实际可批状态
 *       {@code [DRAFT, REVIEWING]} 在出网前判 409，并把这一形态差登记进
 *       {@code notApplicableReasons}。</li>
 *   <li><b>submit ＝ 真实 501</b>：属主无送审端点、状态机白名单无 {@code PENDING_APPROVAL}
 *       （全仓实扫：该词只出现在前端文案与设计文档，口径版本行的写入/推进路径零承载），
 *       借道属主 approve 会把「送审」偷换成「批准」——那是把状态机写假。
 *       400/403/409 三条前置腿做实，落库腿显式 501 + {@code missing[]}。</li>
 *   <li><b>supersede ＝ 真实 501</b>：契约要求「新版 APPROVED、旧版 SUPERSEDED」，
 *       而版本表无 {@code SUPERSEDED} 状态载体、无 {@code superseded_by} 列、
 *       主行也无「当前版本号」列可指向新版；本批<b>不引入新 DDL 迁移</b>（不可逆动作须授权），
 *       故同样做实前置腿后显式 501，禁调属主 retire 端点把「升版」冒充成「退休」。</li>
 * </ol>
 *
 * <h3>为什么 create 的 DRAFT 落点在版本行，不在主行</h3>
 * 属主 {@code CaliberService#insertCaliber} 对空 status 的兜底是 {@code ACTIVE}，而跨引擎只读消费面
 * {@code getByCaliberCodeAndCurrentVersion} 以主表 {@code status='ACTIVE'} 为过滤条件 ⇒
 * 拿 DRAFT 顶主行会让新口径对全部消费面隐形。因此主行走属主兜底、
 * <b>DRAFT 显式落版本行</b>（{@code insertVersion} 的缺省态正是 DRAFT），响应带
 * {@code draftStatusCarrier="versionRow"} 说明载体位置。契约「新口径（DRAFT）」由此成立且可查。
 *
 * <h3>失败面只上抛，状态行由 handler 唯一渲染（D-B4-59 workspace 族口径）</h3>
 * 本类不含任何 {@code ApiResponse.error(...)} 折叠腿；409/403/501/400 全部由委托侧
 * <b>在出网之前</b>依据属主详情面的版本行状态与入口凭证载体得出，
 * 不去嗅探属主人读 message（那会造出第二套判据）。
 */
@Service
public class CaliberWriteService {

    private static final Logger log = LoggerFactory.getLogger(CaliberWriteService.class);

    /** 属主 approve 咽喉实际可批的版本行状态（{@code isForwardTransition} 白名单实测值，非契约词表）。 */
    private static final List<String> APPROVABLE_STATUSES = List.of("DRAFT", "REVIEWING");

    /** 契约送审的起点状态。 */
    private static final String DRAFT_STATUS = "DRAFT";

    /** 主行可透传列（与读面同一份载体清单）。 */
    private static final List<String> HEAD_WRITE_FIELDS =
            List.of("code", "name", "unit", "currency", "ownerRole", "dimensionJson");

    /**
     * 写面真欠原因单源（每条可由 grep / 属主源码实扫复验；文案不写跨引擎 schema/表限定名，
     * 与 {@code WorkspaceLayeringArchTest} 冻结判据同源）。
     */
    private static final Map<String, String> WRITE_REASONS = Map.of(
            "headStatusDraft",
            "属主 insertCaliber 对空 status 兜底 ACTIVE，且跨引擎只读面按主表 status='ACTIVE' 过滤 ⇒ "
                    + "主行不写 DRAFT（会把新口径对消费面隐形）；DRAFT 的真实载体是版本行 status 列",
            "changeReasonPersistence",
            "属主版本表建表脚本 V167.1 十六列清单内无变更原因列（实扫零命中 change_reason）⇒ "
                    + "入参收下但无落库载体，不下发属主、不折进 dimensionJson 冒充",
            "crossCallAtomicity",
            "主行与版本行是两条属主写端点（POST /calibers 与 POST /calibers/{code}/versions），"
                    + "跨服务无分布式事务；腿二失败留孤儿主行，取证走日志面",
            "pendingApprovalState",
            "属主状态机白名单（isForwardTransition）仅 DRAFT/REVIEWING/APPROVED/RETIRED 四态流转，"
                    + "PENDING_APPROVAL 在口径版本行的写入与推进路径零承载 ⇒ 送审无落点",
            "supersededStateAndBackRef",
            "版本表无 SUPERSEDED 状态载体、无 superseded_by 列，主表亦无当前版本号列可指向新版 ⇒ "
                    + "契约「新版 APPROVED + 旧版 SUPERSEDED」两半都无数据载体",
            "approvalRoleAndOpaGate",
            "契约档位「caliber:approve + 财务角色 + OPA」在本面的实际载体只有 RLS 读咽喉 "
                    + "assertReadable(caliber_definition)；caliber:approve 权限点全仓 0 命中、"
                    + "财务角色与 OPA 策略面无口径档位 ⇒ 档位差在册，非本批可合成",
            "auditEventDelivery",
            "审计出口 WorkspaceSecurityEngineClient#audit 为 fail-soft（内部 catch 全部异常，"
                    + "EventBusService 未装配即只记日志）⇒ 本面只声明已发起，不能声明已落达");

    private final CaliberOwnerClient ownerClient;
    private final ScenarioReadGuard readGuard;
    private final CaliberAddressing addressing;
    private final CaliberReadService readService;
    private final WorkspaceSecurityAudit securityAudit;

    public CaliberWriteService(CaliberOwnerClient ownerClient, ScenarioReadGuard readGuard,
                               CaliberAddressing addressing, CaliberReadService readService,
                               WorkspaceSecurityAudit securityAudit) {
        this.ownerClient = ownerClient;
        this.readGuard = readGuard;
        this.addressing = addressing;
        this.readService = readService;
        this.securityAudit = securityAudit;
    }

    /**
     * 新建口径（契约 #3 {@code createCaliber}：主行 + 首条 DRAFT 版本行）。
     *
     * @param request 契约入参：{@code code}/{@code name}/{@code formula} 必填，
     *                {@code unit}/{@code currency}/{@code ownerRole}/{@code dimensionJson}（核算映射）/
     *                {@code additive}/{@code periodGranularity}/{@code changeReason} 选填
     * @return 新建结果面（{@code atomic=false} 显式声明两腿非同一事务）
     * @throws com.chinacreator.gzcm.workspace.exception.CaliberFormulaInvalidException
     *         DSL 四关违一（400，携符号定位或属主闸名）
     * @throws ActionValidationException         必填字段缺位（400 逐字段）
     * @throws ForbiddenOperationException      RLS 明确拒绝/漂移（403）
     * @throws CaliberOwnerUnavailableException 属主不可得（503）
     * @throws CaliberOwnerFailureException     属主显式回失败信封（500，带 ownerCode 取证）
     */
    public Map<String, Object> create(Map<String, Object> request) {
        List<String> missing = new ArrayList<>();
        for (String field : List.of("code", "name", "formula")) {
            if (blank(request, field)) {
                missing.add(field);
            }
        }
        if (!missing.isEmpty()) {
            throw new ActionValidationException(missing);
        }
        readGuard.assertReadable(CaliberAddressing.ENTITY_CODE);
        // 四关预检复用读面同一入口（判定权在属主 DSL 单源，本面不预校验公式）
        readService.validateOwnerFormula(request);

        String code = String.valueOf(required(request, "code"));
        Map<String, Object> headPayload = new LinkedHashMap<>();
        addressing.copyPresent(request, headPayload, HEAD_WRITE_FIELDS);

        OwnerReply headReply = ownerClient.createHeadRow(headPayload);
        addressing.requireSuccess(headReply, "create");
        Map<String, Object> headRow = headReply.object();
        String caliberId = stringOf(headRow.get("id"));
        String headStatus = stringOf(headRow.get("status"));

        Map<String, Object> versionPayload = new LinkedHashMap<>();
        versionPayload.put("formula", required(request, "formula"));
        putIfPresent(request, versionPayload, List.of("additive", "periodGranularity"));
        versionPayload.put("status", DRAFT_STATUS);

        Map<String, Object> versionRow;
        try {
            OwnerReply versionReply = ownerClient.addVersion(code, versionPayload);
            addressing.requireSuccess(versionReply, "createVersion");
            versionRow = addressing.normalizeVersionRow(versionReply.object());
        } catch (RuntimeException ex) {
            // 腿二失败 = 主行已在属主侧落库，版本行缺失。禁谎报「已回滚」，也禁静默吞掉：
            // 留取证后原样上抛，让状态行如实反映「口径已建但不可用」
            log.error("口径主行已建而版本行写入失败（孤儿主行待人工处置）caliberId={} code={} cause={}",
                    caliberId, code, ex.getClass().getSimpleName());
            throw ex;
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("caliberId", caliberId);
        data.put("code", stringOf(headRow.get("code")));
        data.put("name", stringOf(headRow.get("name")));
        data.put("headStatus", headStatus);
        data.put("versionNo", versionRow.get("versionNo"));
        data.put("versionStatus", versionRow.get("status"));
        data.put("status", versionRow.get("status"));
        // 契约「新口径 = DRAFT」的载体位置声明：DRAFT 在版本行，不在主行（成因见类注释）
        data.put("draftStatusCarrier", "versionRow");
        data.put("atomic", false);
        data.put("changeReason", stringOf(request.get("changeReason")));
        data.put("changeReasonStored", false);
        data.put("dimensionMappingCarrier", headRow.containsKey("dimensionJson")
                ? "dimensionJson" : null);
        data.put("dataSource", CaliberAddressing.DATA_SOURCE);
        data.put("writeNotApplicableReasons", pickWriteReasons(
                "headStatusDraft", "changeReasonPersistence", "crossCallAtomicity"));
        log.info("calibers 新建承流 code={} caliberId={} 主行状态={} 版本行状态={}",
                code, caliberId, headStatus, versionRow.get("status"));
        return data;
    }

    /**
     * 批准（契约 #5 {@code approveCaliber}：属主真值单腿）。
     *
     * @param caliberId 契约路径 id（两跳解析至属主 code 基寻址）
     * @param request   可选 {@code versionNo}；缺省取版本链里首条可批版本行
     * @return 审批结果面（{@code status}/{@code approvedBy} 均为属主真值）
     * @throws com.chinacreator.gzcm.workspace.exception.CaliberNotFoundException
     *         窗口内解析不到该 id（404）
     * @throws CaliberApproverIdentityMissingException 入口无任何可归因凭证头（403，出网前拦、属主零调用）
     * @throws CaliberStatePreconditionException    版本行状态不在可批集合（409）
     * @throws ForbiddenOperationException          RLS 明确拒绝/漂移（403）
     * @throws CaliberOwnerUnavailableException     属主不可得（503）
     */
    public Map<String, Object> approve(String caliberId, Map<String, Object> request) {
        readGuard.assertReadable(CaliberAddressing.ENTITY_CODE);
        // 审批是「谁批的」唯一有主语的一条腿：入口连一枚凭证头都没有时不拿匿名请求去撞属主 400，
        // 出网前即拦成 403（属主面会回 400 ECOS-ONTO-040，那不是契约态，且审批人不落库）
        if (!CallerCredentialSource.current().present()) {
            throw new CaliberApproverIdentityMissingException(caliberId);
        }

        String code = addressing.resolveCodeById(caliberId);
        Map<String, Object> head = addressing.fetchHeadWithVersions(code, caliberId);
        List<Map<String, Object>> versions = addressing.normalizeVersions(head.get("versions"));
        String requestedVersionNo = blank(request, "versionNo") ? null : String.valueOf(request.get("versionNo"));

        Map<String, Object> target;
        if (requestedVersionNo != null) {
            target = addressing.versionByNo(versions, requestedVersionNo);
        } else {
            target = addressing.firstVersionWithStatus(versions, APPROVABLE_STATUSES);
        }
        String versionNo = target == null ? requestedVersionNo : stringOf(target.get("versionNo"));
        String observed = target == null ? null : stringOf(target.get("status"));
        if (target == null || !APPROVABLE_STATUSES.contains(observed)) {
            throw new CaliberStatePreconditionException(caliberId, code, versionNo, observed, APPROVABLE_STATUSES);
        }

        OwnerReply reply = ownerClient.approveVersion(code, versionNo);
        addressing.requireSuccess(reply, "approve");
        Map<String, Object> ownerTruth = reply.object();

        String resourceId = code + "@" + versionNo;
        securityAudit.record("caliber:approve", resourceId, ownerTruth.get("status"));

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("caliberId", caliberId);
        data.put("code", stringOf(ownerTruth.get("code")));
        data.put("versionNo", stringOf(ownerTruth.get("versionNo")));
        data.put("status", stringOf(ownerTruth.get("status")));
        data.put("approvedBy", stringOf(ownerTruth.get("approvedBy")));
        // 审计：本面只声明「已发起」。audit 出口 fail-soft（异常内部吞 + 总线未装配只记日志），
        // 声明「已落达」就是假绿
        data.put("auditEventAttempted", true);
        data.put("auditEventDeliveryConfirmed", false);
        data.put("contractPreconditionStatus", "PENDING_APPROVAL");
        data.put("observedPreconditionStatuses", APPROVABLE_STATUSES);
        data.put("dataSource", CaliberAddressing.DATA_SOURCE);
        data.put("writeNotApplicableReasons", pickWriteReasons(
                "approvalRoleAndOpaGate", "auditEventDelivery", "pendingApprovalState"));
        log.info("calibers 批准承流 id={} → code={} v={} 审批人={} 状态={}",
                caliberId, code, versionNo, ownerTruth.get("approvedBy"), ownerTruth.get("status"));
        return data;
    }

    /**
     * 送审（契约 #4 {@code submitCaliber}）：400/403/404/409 前置腿做实，落库腿<b>真实 501</b>。
     *
     * @throws com.chinacreator.gzcm.workspace.exception.CaliberNotFoundException
     *         窗口内解析不到该 id（404；契约列未含 404，按真实资源缺失回，不折成 409）
     * @throws CaliberStatePreconditionException    目标版本行不在 DRAFT（409）
     * @throws ForbiddenOperationException          RLS 明确拒绝/漂移（403）
     * @throws CaliberWriteNotLiveException         送审落库腿无载体（501，携逐条可复验 missing）
     */
    public Map<String, Object> submit(String caliberId, Map<String, Object> request) {
        readGuard.assertReadable(CaliberAddressing.ENTITY_CODE);
        String code = addressing.resolveCodeById(caliberId);
        Map<String, Object> head = addressing.fetchHeadWithVersions(code, caliberId);
        List<Map<String, Object>> versions = addressing.normalizeVersions(head.get("versions"));
        Map<String, Object> target = resolveVersion(versions, request, List.of(DRAFT_STATUS));
        if (target == null || !DRAFT_STATUS.equals(stringOf(target.get("status")))) {
            throw new CaliberStatePreconditionException(caliberId, code,
                    target == null ? versionNoOf(request) : stringOf(target.get("versionNo")),
                    target == null ? null : stringOf(target.get("status")), List.of(DRAFT_STATUS));
        }
        // 前置判定全部成立，但「DRAFT → PENDING_APPROVAL」在属主侧无任何落点：
        // 不借道 approve（那是把送审冒充批准），不写库（那是 workspace 越俎代庖属主表）
        throw new CaliberWriteNotLiveException("submitCaliber", "/api/v1/workspace/calibers/" + caliberId + "/submit",
                List.of("属主无送审端点（CaliberController 端点全集内 0 枚 submit 路径）",
                        "属主状态机白名单 isForwardTransition 只允许 DRAFT→REVIEWING/APPROVED、"
                                + "REVIEWING→APPROVED、APPROVED→RETIRED 四段，无 PENDING_APPROVAL 目标态",
                        "口径版本表建表脚本 V167.1 的 status 列无 CHECK 约束，但推进该值的写路径不存在"
                                + "（archiveVersion 之外无 UPDATE）",
                        "恢复路径＝属主侧新增 PENDING_APPROVAL 流转段（或裁决把 REVIEWING 定为送审载体），"
                                + "属主侧改动归本体域，另批处置"));
    }

    /**
     * 升版（契约 #6 {@code supersedeCaliber}）：400 必填 + 403 + 404 + 409 前置腿做实，
     * 新版置 APPROVED／旧版置 SUPERSEDED 的落库腿<b>真实 501</b>。
     *
     * @throws ActionValidationException           newVersionId/changeReason 缺位（400 逐字段）
     * @throws com.chinacreator.gzcm.workspace.exception.CaliberNotFoundException
     *         窗口内解析不到该 id（404）
     * @throws CaliberStatePreconditionException    无 APPROVED 版本行可被取代（409）
     * @throws ForbiddenOperationException          RLS 明确拒绝/漂移（403）
     * @throws CaliberWriteNotLiveException         升版双腿无载体（501，携逐条可复验 missing）
     */
    public Map<String, Object> supersede(String caliberId, Map<String, Object> request) {
        List<String> missing = new ArrayList<>();
        for (String field : List.of("newVersionId", "changeReason")) {
            if (blank(request, field)) {
                missing.add(field);
            }
        }
        if (!missing.isEmpty()) {
            throw new ActionValidationException(missing);
        }
        readGuard.assertReadable(CaliberAddressing.ENTITY_CODE);
        String code = addressing.resolveCodeById(caliberId);
        Map<String, Object> head = addressing.fetchHeadWithVersions(code, caliberId);
        List<Map<String, Object>> versions = addressing.normalizeVersions(head.get("versions"));
        Map<String, Object> incumbent = addressing.currentApproved(versions);
        if (incumbent == null) {
            throw new CaliberStatePreconditionException(caliberId, code, null, null, List.of("APPROVED"));
        }

        throw new CaliberWriteNotLiveException("supersedeCaliber",
                "/api/v1/workspace/calibers/" + caliberId + "/supersede",
                List.of("属主版本表建表脚本 V167.1 十六列清单内无 SUPERSEDED 状态载体"
                                + "（status 列无 CHECK，但推进该值的写路径不存在）",
                        "版本表无 superseded_by 列（实扫零命中）⇒ 新旧版本的后向指认无数据载体",
                        "口径主表 15 列内无「当前版本号」列 ⇒ 契约「新版生效」无主行侧指向载体",
                        "属主状态机白名单无 SUPERSEDED 目标态，借道 retire 端点只会得到 RETIRED（语义不同态）",
                        "恢复路径＝新 DDL 迁移（补 SUPERSEDED 词表 + superseded_by 列 + 主行当前版本指针）"
                                + "或工程裁决把 RETIRED 定为取代载体；本批按「不引入不可逆迁移」红线走 501 取证"));
    }

    // ═══════════════ 私有辅助 ═══════════════

    /** 版本行定位：给定 versionNo 精确取，缺省取版本链里首条状态命中给定词表的行。 */
    private Map<String, Object> resolveVersion(List<Map<String, Object>> versions,
                                               Map<String, Object> request, List<String> statuses) {
        String versionNo = versionNoOf(request);
        return versionNo == null
                ? addressing.firstVersionWithStatus(versions, statuses)
                : addressing.versionByNo(versions, versionNo);
    }

    private String versionNoOf(Map<String, Object> request) {
        return blank(request, "versionNo") ? null : String.valueOf(request.get("versionNo"));
    }

    private static Map<String, String> pickWriteReasons(String... keys) {
        Map<String, String> selected = new LinkedHashMap<>();
        for (String key : keys) {
            selected.put(key, WRITE_REASONS.get(key));
        }
        return selected;
    }

    private static void putIfPresent(Map<String, Object> source, Map<String, Object> target, List<String> fields) {
        for (String field : fields) {
            Object value = source.get(field);
            if (value != null) {
                target.put(field, value);
            }
        }
    }

    private static boolean blank(Map<String, Object> request, String field) {
        Object value = request == null ? null : request.get(field);
        return value == null || String.valueOf(value).isBlank();
    }

    private static Object required(Map<String, Object> request, String field) {
        Object value = request.get(field);
        if (value == null || String.valueOf(value).isBlank()) {
            throw new ActionValidationException(List.of(field));
        }
        return value;
    }

    private static String stringOf(Object value) {
        return value == null ? null : String.valueOf(value);
    }
}
