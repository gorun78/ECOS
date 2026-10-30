package com.chinacreator.gzcm.workspace.caliber;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.workspace.exception.ActionValidationException;
import com.chinacreator.gzcm.workspace.exception.CaliberApproverIdentityMissingException;
import com.chinacreator.gzcm.workspace.exception.CaliberFormulaInvalidException;
import com.chinacreator.gzcm.workspace.exception.CaliberNotFoundException;
import com.chinacreator.gzcm.workspace.exception.CaliberOwnerFailureException;
import com.chinacreator.gzcm.workspace.exception.CaliberOwnerUnavailableException;
import com.chinacreator.gzcm.workspace.exception.CaliberStatePreconditionException;
import com.chinacreator.gzcm.workspace.exception.CaliberWriteNotLiveException;
import com.chinacreator.gzcm.workspace.exception.SecurityEngineUnavailableException;
import com.chinacreator.gzcm.workspace.scenario.ForbiddenOperationException;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 口径契约承流面（#146ac 附录A 第 31 条读面三枚 + #146ad 写面四枚，
 * 契约行 = 详设-12 §12.4.2 line 8248~8255，共 7 枚端点）。
 *
 * <p><b>主权边界</b>：口径的定义/审批/升版全归本体域（属主 {@code CaliberController} 是唯一写入口），
 * 本类只做 workspace 编排面委托——零 SQL、零第二套 DSL 判定。写面四枚逐枚定档见
 * {@link CaliberWriteService}：create/approve 走属主真值腿，submit/supersede 因属主侧无状态载体
 * 而在做实前置判定后显式 501（不借道近似端点把状态机写假）。</p>
 *
 * <p><b>状态行</b>：一律由 {@code WorkspaceExceptionHandler} 唯一渲染（D-B4-59 workspace 族
 * 「切面只能抛」口径），本类不含任何 {@code ApiResponse.error(...)} 折叠腿；
 * 成功腿只回属主真值，真欠列以 {@code null + *Applicable=false + notApplicableReasons}
 * 显式缺席（X-22/X-43），禁兜底值伪装。</p>
 *
 * <p>类级前缀 {@code /api/v1/workspace/calibers} 独占（同基路径禁双 Controller 纪律：
 * {@code ScenarioActionController} 的 {@code /api/v1/workspace} 裸前缀面一字未动）。</p>
 */
@RestController
@RequestMapping("/api/v1/workspace/calibers")
public class CaliberContractController {

    private final CaliberReadService readService;
    private final CaliberWriteService writeService;

    public CaliberContractController(CaliberReadService readService, CaliberWriteService writeService) {
        this.readService = readService;
        this.writeService = writeService;
    }

    /**
     * 口径版本链列表（契约 #1，错误码列 403）。
     *
     * @throws ForbiddenOperationException        RLS 明确拒绝/漂移（403）
     * @throws SecurityEngineUnavailableException  security-engine 进程不可用（503，不可用≠无权限）
     * @throws CaliberOwnerUnavailableException    属主不可得（503）
     * @throws CaliberOwnerFailureException        属主显式回失败信封（500）
     */
    @GetMapping
    public ApiResponse<Map<String, Object>> listCalibers(
            @RequestParam(value = "code", required = false) String code,
            @RequestParam(value = "status", required = false) String status) {
        return ApiResponse.success(readService.list(code, status));
    }

    /**
     * 口径详情（契约 #2，错误码列 403/404；id 基寻址，两跳解析至属主 code 基详情面）。
     *
     * @throws CaliberNotFoundException             窗口内解析不到该 id（404，携解析窗口取证）
     * @throws ForbiddenOperationException          RLS 明确拒绝/漂移（403）
     * @throws SecurityEngineUnavailableException   security-engine 进程不可用（503）
     * @throws CaliberOwnerUnavailableException     属主不可得（503）
     */
    @GetMapping("/{id}")
    public ApiResponse<Map<String, Object>> getCaliber(@PathVariable("id") String id) {
        return ApiResponse.success(readService.detail(id));
    }

    /**
     * 新建口径（契约 #3，错误码列 400 {@code CALIBER_DSL_INVALID} + 符号定位/403）。
     *
     * <p>属主侧由两条写入口合成（主行 + 首条 DRAFT 版本行），非同一事务 ⇒ 响应显式
     * {@code atomic=false}；四关预检在落库之前，违一即 400 且属主零写调用。</p>
     *
     * @throws ActionValidationException            code/name/formula 必填缺位（400 逐字段）
     * @throws CaliberFormulaInvalidException       DSL 四关违一（400）
     * @throws ForbiddenOperationException           RLS 明确拒绝/漂移（403）
     * @throws CaliberOwnerUnavailableException      属主不可得（503）
     * @throws CaliberOwnerFailureException          属主显式回失败信封（500）
     */
    @PostMapping
    public ApiResponse<Map<String, Object>> createCaliber(
            @RequestBody(required = false) Map<String, Object> request) {
        return ApiResponse.success(writeService.create(request));
    }

    /**
     * 送审（契约 #4，错误码列 400/403/409 {@code CALIBER_STATE_PRECONDITION}）。
     *
     * <p>400/403/404/409 四条前置腿在委托侧做实；{@code DRAFT→PENDING_APPROVAL} 的落库腿
     * 在属主侧零载体，故显式 501 并逐条列可复验的 {@code missing[]}。</p>
     *
     * @throws CaliberNotFoundException              窗口内解析不到该 id（404）
     * @throws CaliberStatePreconditionException     目标版本行不在 DRAFT（409）
     * @throws ForbiddenOperationException            RLS 明确拒绝/漂移（403）
     * @throws CaliberWriteNotLiveException          送审落库腿无载体（501）
     */
    @PostMapping("/{id}/submit")
    public ApiResponse<Map<String, Object>> submitCaliber(
            @PathVariable("id") String id,
            @RequestBody(required = false) Map<String, Object> request) {
        return ApiResponse.success(writeService.submit(id, request));
    }

    /**
     * 批准（契约 #5，错误码列 403/409/503）。
     *
     * <p>审批人取 JWT 主体：委托侧经凭证中继把入口凭证原样送到属主（不合成主语），
     * 入口无任何可归因凭证头时在出网前即拦 403。{@code status}/{@code approvedBy} 均为属主真值。</p>
     *
     * @throws CaliberApproverIdentityMissingException 入口无凭证载体（403，属主零调用）
     * @throws CaliberNotFoundException                窗口内解析不到该 id（404）
     * @throws CaliberStatePreconditionException       版本行状态不在可批集合（409）
     * @throws ForbiddenOperationException              RLS 明确拒绝/漂移（403）
     * @throws CaliberOwnerUnavailableException        属主不可得（503）
     */
    @PostMapping("/{id}/approve")
    public ApiResponse<Map<String, Object>> approveCaliber(
            @PathVariable("id") String id,
            @RequestBody(required = false) Map<String, Object> request) {
        return ApiResponse.success(writeService.approve(id, request));
    }

    /**
     * 升版（契约 #6，错误码列 400/403/409）。
     *
     * <p>契约「新版 APPROVED、旧版 SUPERSEDED」两半在属主侧均无数据载体（版本表无 SUPERSEDED
     * 状态推进路径、无 {@code superseded_by} 列、主行无当前版本指针），本批不引入不可逆迁移 ⇒
     * 做实前置腿后显式 501。</p>
     *
     * @throws ActionValidationException            newVersionId/changeReason 缺位（400 逐字段）
     * @throws CaliberNotFoundException             窗口内解析不到该 id（404）
     * @throws CaliberStatePreconditionException    无 APPROVED 版本行可被取代（409）
     * @throws ForbiddenOperationException            RLS 明确拒绝/漂移（403）
     * @throws CaliberWriteNotLiveException          升版双腿无载体（501）
     */
    @PostMapping("/{id}/supersede")
    public ApiResponse<Map<String, Object>> supersedeCaliber(
            @PathVariable("id") String id,
            @RequestBody(required = false) Map<String, Object> request) {
        return ApiResponse.success(writeService.supersede(id, request));
    }

    /**
     * 公式 DSL 四关预检（契约 #7，错误码列 400；判定权在属主，只校验不落库）。
     *
     * @throws ActionValidationException            formula 缺位（400 逐字段）
     * @throws CaliberFormulaInvalidException       四关违一（400，携逐条违反项与符号定位）
     * @throws ForbiddenOperationException          RLS 明确拒绝/漂移（403）
     * @throws CaliberOwnerUnavailableException     属主不可得（503）
     */
    @PostMapping("/validate")
    public ApiResponse<Map<String, Object>> validateCaliber(
            @RequestBody(required = false) Map<String, Object> request) {
        return ApiResponse.success(readService.validate(request));
    }
}
