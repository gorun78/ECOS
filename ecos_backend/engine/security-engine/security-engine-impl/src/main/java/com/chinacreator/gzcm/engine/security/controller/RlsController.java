package com.chinacreator.gzcm.engine.security.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.common.security.rls.RlsExecutionContract;
import com.chinacreator.gzcm.engine.security.decision.SubjectClearanceResolutionService;
import com.chinacreator.gzcm.engine.security.decision.SubjectDepartmentResolutionService;
import com.chinacreator.gzcm.engine.security.rls.RlsSubjectContext;
import com.chinacreator.gzcm.engine.security.service.RowLevelSecurityServiceImpl;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping({"/api/security/rls", "/api/v1/security/rls"})
public class RlsController {

    private static final Logger log = LoggerFactory.getLogger(RlsController.class);

    private final RowLevelSecurityServiceImpl rlsService;
    private final SubjectDepartmentResolutionService subjectDepartmentResolver;
    private final SubjectClearanceResolutionService subjectClearanceResolver;

    public RlsController(RowLevelSecurityServiceImpl rlsService,
                         SubjectDepartmentResolutionService subjectDepartmentResolver,
                         SubjectClearanceResolutionService subjectClearanceResolver) {
        this.rlsService = rlsService;
        this.subjectDepartmentResolver = subjectDepartmentResolver;
        this.subjectClearanceResolver = subjectClearanceResolver;
    }

    /**
     * POST {@code /api/v1/security/rls/apply}。
     *
     * <p><b>#142/B4-3c-2（D-B4-17）</b>：应答新增 {@code whereClause} / {@code whereParams}
     * （JDBC {@code ?} 片段 + 同序参数）与 {@code noPolicyMatched} / {@code rlsDenyReason}。
     * 此前应答没有 {@code whereClause}，workspace/ontology/ai 三个客户端读该字段取到
     * {@code null} 后一律返空串 ⇒ 行级过滤"不加即全放行"。现在渲染由生产者单源完成，
     * 渲染不出可信片段就是 {@code 1=0}。</p>
     *
     * <p><b>主体属性来源</b>：请求体只接受 {@code userId}（服务间中继的既有契约，签名不改）；
     * {@code tenantId} 只取信任链 {@code ServiceContext}（JWT {@code tenant_id}），
     * <b>不接受</b>请求体自报租户。{@code orgId} 由
     * {@link SubjectDepartmentResolutionService} 从属主（sysman {@code td_user_organization}
     * 主机构关联）服务端解析真值（#142/B4-3e-80 收口 D-B4-23 主体域腿），同样
     * <b>不接受</b>请求体自报机构；主机构不唯一或关联缺失 ⇒ 如实未解析 ⇒ 依赖
     * {@code CONTEXT_ORG} 的策略被拒绝，而不是被渲染成 {@code department_id = NULL}。
     * {@code clearanceLevel} 同样由 {@link SubjectClearanceResolutionService} 经属主（sysman
     * 五档密级链 {@code USER > ROLE > ORG > TENANT > GLOBAL}）服务端解析真值
     * （#142/B4-3e-81 收口 D-B4-23 余项腿）。<b>未解析就是未解析</b>：五档查尽无档案行 ⇒
     * 如实 {@code null} ⇒ 依赖 {@code CONTEXT_CLEARANCE_LEVEL} 的策略落
     * {@code RLS_CONTEXT_UNRESOLVED}；<b>不</b>照抄准入拦截器末端的「无档案兜底 L1」，
     * 那等于对一个不存在的事实做行范围放行。</p>
     *
     * <p><b>主体域真值源不可用 ≠ 策略不匹配</b>：属主门面缺席或取数报错时，本端点<b>抛</b>
     * {@link DataBridgeException} 携 503 态（{@link RlsExecutionContract#SEC_UNAVAILABLE_HTTP}／
     * {@link RlsExecutionContract#SEC_UNAVAILABLE_CODE} 单源），由宿主全局处理器写上状态行；
     * 裸回 {@code ApiResponse} 无 HTTP 状态能力，改返回值写不出 503（D-B4-59 族）。
     * {@code catch (DataBridgeException e) { throw e; }} 放行腿必须与 {@code catch (Exception)}
     * 兜底同时存在，否则不可用态会被折回 500。机构腿与密级腿各用独立 helper 承载，
     * 理由片段点明断的是哪条腿（取证要能分面，禁两条腿共用一句「主体域不可用」）。</p>
     */
    @PostMapping("/apply")
    public ApiResponse<Map<String, Object>> apply(@RequestBody Map<String, Object> body) {
        String tableName = (String) body.get("tableName");
        String userId = (String) body.get("userId");
        if (tableName == null || tableName.isBlank()) {
            return ApiResponse.badRequest("tableName不能为空");
        }
        if (userId == null || userId.isBlank()) {
            return ApiResponse.badRequest("userId不能为空");
        }
        try {
            return ApiResponse.success(rlsService.apply(tableName, subjectContext(userId)));
        } catch (DataBridgeException e) {
            // 放行腿：主体域真值源不可用的 503 承载必须原样上抛给宿主状态行，禁被下面兜底折回 500
            throw e;
        } catch (Exception e) {
            log.error("RLS策略应用失败", e);
            return ApiResponse.internalError("策略应用失败");
        }
    }

    /**
     * 主体域<b>机构</b>真值源不可用的 HTTP 承载：裸回 {@code ApiResponse} 写不出状态行，沿用抛型形态
     * （与 {@code AuditController#unassembled}、{@code DecisionController} 同源，
     * 码/号取 {@link RlsExecutionContract} 单源常量，禁在本类另立字面量）。
     */
    static DataBridgeException subjectSourceUnavailable(Throwable cause) {
        return new DataBridgeException(RlsExecutionContract.SEC_UNAVAILABLE_HTTP,
                RlsExecutionContract.SEC_UNAVAILABLE_HTTP,
                RlsExecutionContract.SEC_UNAVAILABLE_CODE + ": 主体域真值源不可用，RLS 行范围无从构造，"
                        + "按 fail-closed 拒绝服务（§4.7.2 不可用 ≠ 无权限 ≠ 空结果）", cause);
    }

    /**
     * 主体域<b>密级</b>真值源不可用的 HTTP 承载（#142/B4-3e-81）：形态与
     * {@link #subjectSourceUnavailable(Throwable)} 同族（同码同状态，仍取 {@link RlsExecutionContract}
     * 单源），但<b>理由片段独立成句</b>——两条腿共用一句理由，取证就分不出断的是机构隶属还是
     * 密级档案链（同族教训见 D-B4-85／D-B4-90 的分态收口）。
     */
    static DataBridgeException subjectClearanceSourceUnavailable(Throwable cause) {
        return new DataBridgeException(RlsExecutionContract.SEC_UNAVAILABLE_HTTP,
                RlsExecutionContract.SEC_UNAVAILABLE_HTTP,
                RlsExecutionContract.SEC_UNAVAILABLE_CODE + ": 主体密级真值源不可用（含五档链断腿），"
                        + "RLS 密级谓词无从构造，按 fail-closed 拒绝服务"
                        + "（§4.7.2 不可用 ≠ 无权限 ≠ 空结果）", cause);
    }

    /**
     * 上下文组装：userId 走既有请求体契约，tenantId 走信任链单源，
     * orgId 与 clearanceLevel 走属主真值单源（不可用即抛承载）。
     */
    private RlsSubjectContext subjectContext(String userId) {
        String tenantId = null;
        try {
            var ctx = com.chinacreator.gzcm.common.security.header.ServiceContext.current();
            tenantId = ctx == null ? null : ctx.getTenantId();
        } catch (Throwable t) {
            log.debug("读取 ServiceContext 租户失败，按未解析处理: {}", t.getMessage());
        }
        String orgId;
        try {
            orgId = subjectDepartmentResolver.resolvePrimaryOrgId(userId);
        } catch (SubjectDepartmentResolutionService.SubjectSourceUnavailableException e) {
            throw subjectSourceUnavailable(e);
        }
        Integer clearanceLevel;
        try {
            clearanceLevel = subjectClearanceResolver.resolveClearanceLevel(userId, tenantId, orgId);
        } catch (SubjectClearanceResolutionService.SubjectClearanceSourceUnavailableException e) {
            throw subjectClearanceSourceUnavailable(e);
        }
        return new RlsSubjectContext(userId, tenantId, orgId, clearanceLevel);
    }

    @GetMapping("/policies")
    public ApiResponse<List<Map<String, Object>>> listPolicies(
            @RequestParam(required = false) String tableName) {
        return ApiResponse.success(rlsService.listPolicies(tableName));
    }

    @GetMapping("/policies/{id}")
    public ApiResponse<?> getPolicy(@PathVariable String id) {
        Map<String, Object> policy = rlsService.getPolicy(id);
        if (policy == null) return ApiResponse.notFound("策略不存在: " + id);
        return ApiResponse.success(policy);
    }

    @PostMapping("/policies")
    public ApiResponse<?> createPolicy(@RequestBody Map<String, Object> body) {
        try {
            return ApiResponse.success(rlsService.createPolicy(body));
        } catch (Exception e) {
            log.error("创建RLS策略失败", e);
            return ApiResponse.internalError("创建失败");
        }
    }

    @PutMapping("/policies/{id}")
    public ApiResponse<?> updatePolicy(@PathVariable String id, @RequestBody Map<String, Object> body) {
        Map<String, Object> updated = rlsService.updatePolicy(id, body);
        if (updated == null) return ApiResponse.notFound("策略不存在: " + id);
        return ApiResponse.success(updated);
    }

    /**
     * 详细设计-01 D.5.2 {@code upsertRlsPolicy}：携 {@code predicateTemplate}+{@code bindings[]}，
     * 经 {@link com.chinacreator.gzcm.engine.security.rls.RlsPredicateValidator} 白名单校验，非法模板 400
     * （ECOS-SEC-410）。落 V164 新列，source_kind='parameterized'。
     */
    @PutMapping("/policies")
    public ApiResponse<?> upsertPolicy(@RequestBody Map<String, Object> body) {
        try {
            return ApiResponse.success(rlsService.upsertPolicy(body));
        } catch (com.chinacreator.gzcm.engine.security.rls.RlsPredicateValidator.TemplateRejectedException e) {
            // 400 ECOS-SEC-410：谓词模板含非白名单字面量/关键字
            return ApiResponse.badRequest("非法谓词模板（ECOS-SEC-410）: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest(e.getMessage());
        } catch (Exception e) {
            log.error("upsert 参数化 RLS 策略失败", e);
            return ApiResponse.internalError("upsert 失败");
        }
    }

    @DeleteMapping("/policies/{id}")
    public ApiResponse<?> deletePolicy(@PathVariable String id) {
        boolean deleted = rlsService.deletePolicy(id);
        if (!deleted) return ApiResponse.notFound("策略不存在: " + id);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("success", true);
        result.put("id", id);
        return ApiResponse.success(result);
    }
}
