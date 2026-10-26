package com.chinacreator.gzcm.engine.data.fact.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.common.security.decision.DataScopeContract;
import com.chinacreator.gzcm.engine.data.fact.FactType;
import com.chinacreator.gzcm.engine.data.fact.model.ActionOutcomeRequest;
import com.chinacreator.gzcm.engine.data.fact.model.FactBatchSummary;
import com.chinacreator.gzcm.engine.data.fact.model.FactImportRequest;
import com.chinacreator.gzcm.engine.data.fact.model.FactImportResult;
import com.chinacreator.gzcm.engine.data.fact.model.FactTemplateVO;
import com.chinacreator.gzcm.engine.data.fact.service.FactImportService;
import com.chinacreator.gzcm.engine.data.fact.service.FactTemplateService;
import com.chinacreator.gzcm.engine.data.model.ActionOutcomeVO;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * 业务事实 REST 面 — 详细设计-02 §D.2（只增不改；operationId 见方法 javadoc）：
 * <ul>
 *   <li>{@code POST /api/v1/datanet/facts/{factType}/import} — importBusinessFacts（≤100 行/批，EN03）</li>
 *   <li>{@code GET  /api/v1/datanet/facts/{factType}/template} — getFactImportTemplate（CSV 头 + 单位 + 2 示例行，无 DB）</li>
 *   <li>{@code GET  /api/v1/datanet/facts/{factType}} — listFacts（分页 + projectId/period/batchId/dqStatus 过滤）</li>
 *   <li>{@code GET  /api/v1/datanet/facts/batches/{batchId}} — getFactBatch（每表按 dq_status 计数）</li>
 *   <li>{@code POST /api/v1/datanet/facts/action-outcomes} — writeActionOutcome（W→D 反馈链）</li>
 * </ul>
 *
 * <p>行级 DQ 拒绝返回 HTTP 200 + rejected 明细（C.5）；整批级违规（超行数 / DQ-F05 币种冲突）
 * 抛 {@link DataBridgeException} 400 {@code ECOS-DATA-023}，关账期 409 {@code ECOS-DATA-022}，
 * PUBLISHED 触达 409 {@code ECOS-DATA-021}（GlobalExceptionHandler 统一转 ApiResponse）。
 *
 * <p>三滤波器（VersionPrefixRewrite / SecurityConfig permitAll / ClearanceInterceptor 双路径）
 * 属 batch-3 gateway 配套，本撮仅落引擎侧端点（详见交付报告）。
 */
@RestController
@RequestMapping("/api/v1/datanet/facts")
public class FactController {

    private final FactImportService importService;
    private final FactTemplateService templateService;

    public FactController(FactImportService importService, FactTemplateService templateService) {
        this.importService = importService;
        this.templateService = templateService;
    }

    /** importBusinessFacts — 批次导入（≤100 行）。行级被拒 → 200 + rejected 明细。 */
    @PostMapping("/{factType}/import")
    public ApiResponse<FactImportResult> importBusinessFacts(@PathVariable("factType") String factType,
                                                             @RequestBody FactImportRequest req) {
        FactType type = parseType(factType);
        return ApiResponse.success(importService.importFacts(type, req));
    }

    /** getFactImportTemplate — 导入模板（CSV 表头 + 单位 + 2 示例行；纯数据，无 DB）。 */
    @GetMapping("/{factType}/template")
    public ApiResponse<FactTemplateVO> getFactImportTemplate(@PathVariable("factType") String factType) {
        return ApiResponse.success(templateService.template(parseType(factType)));
    }

    /**
     * listFacts — 分页查询（page 1-based / pageSize 默认 20 上限 200）。
     *
     * <p>TODO(F02-01-decide): batch-3 决策管道 RLS/CLS/mask（分册 01 decide）。
     */
    @GetMapping("/{factType}")
    public ApiResponse<Map<String, Object>> listFacts(
            @PathVariable("factType") String factType,
            @RequestParam(value = "page", defaultValue = "1") long page,
            @RequestParam(value = "pageSize", defaultValue = "20") long pageSize,
            @RequestParam(value = "projectId", required = false) String projectId,
            @RequestParam(value = "period", required = false) String period,
            @RequestParam(value = "batchId", required = false) String batchId,
            @RequestParam(value = "dqStatus", required = false) String dqStatus) {
        FactType type = parseType(factType);
        return ApiResponse.success(importService.listFacts(type, page, pageSize,
                projectId, period, batchId, dqStatus));
    }

    /** getFactBatch — 批次摘要（五表按 dq_status 计数）。 */
    @GetMapping("/batches/{batchId}")
    public ApiResponse<FactBatchSummary> getFactBatch(@PathVariable("batchId") String batchId) {
        return ApiResponse.success(importService.getBatch(batchId));
    }

    /** writeActionOutcome — W→D 反馈链写入（PUBLISHED 行 → 409 ECOS-DATA-021）。 */
    @PostMapping("/action-outcomes")
    public ApiResponse<Map<String, Object>> writeActionOutcome(@RequestBody ActionOutcomeRequest req) {
        return ApiResponse.success(importService.writeActionOutcome(req));
    }

    /**
     * listActionOutcomes — outcome 物理载体查询（H-3 交付②，V279 表；只读）。
     *
     * <p>定位键二选一（forecastRunId / actionId，均走索引）；两键皆空 → 400 ECOS-DATA-023
     * （禁全库扫描）；V279 未应用 → 503 ECOS-DATA-041。
     */
    @GetMapping("/action-outcomes")
    public ApiResponse<List<ActionOutcomeVO>> listActionOutcomes(
            @RequestParam(value = "forecastRunId", required = false) String forecastRunId,
            @RequestParam(value = "actionId", required = false) String actionId) {
        return ApiResponse.success(importService.listActionOutcomes(forecastRunId, actionId));
    }

    /**
     * listAttributionDepartments — 项目→部门归属真值读回（#142/B4-3e-79，差距 D-B4-36 属主侧；只读）。
     *
     * <p><b>为什么由本域供数而不是 security 侧直读表</b>：{@code
     * ecos_dw.ecos_biz_project_attribution} 的属主是 data-engine（铁律 #5 跨引擎禁操作对方表），
     * 而"AI 工具声明的 projectIds 展开成真实所属域"这件事必须在裁决链的服务端一侧完成
     * ——被裁决方自报的 {@code departmentId} 不可信（D-B4-29 已把该键从工具入参白名单剔除）。
     * 因此真值面留在属主、判定面在 security，两侧各一枚端点/服务，不再有第三份口径。</p>
     *
     * <p><b>形态</b>：{@code {requested, byProject:{projectId:[departmentId...]}, departments:[...]}}，
     * 只含权威态行（{@code is_deleted=0 ∧ is_active=1 ∧ dq_status∈{PASSED,PUBLISHED}}）。
     * 一项目多部门／缺归属一律如实返回空或多值，<b>不</b>在本端点判"能不能采信"
     * ——那是 {@code DeclaredScopeResolutionService} 的单点裁决。</p>
     *
     * <p><b>失败形态</b>：入参违规（项目集空 / 超上限）由 {@link FactImportService}
     * 抛 {@code DataBridgeException}(400, {@code ECOS-DATA-023})，经全局 handler 上状态行；
     * 本方法不返回裸回失败信封（全后端裸回棘轮门判据 B 的只减不增上限，扩面须同批登记）。</p>
     *
     * @param projectIds 受控项目集（可重复参数或逗号分隔，键名取 {@link DataScopeContract} 单源）
     */
    @GetMapping("/attribution/departments")
    public ApiResponse<Map<String, Object>> listAttributionDepartments(
            @RequestParam(value = DataScopeContract.KEY_PROJECT_IDS, required = false) List<String> projectIds) {
        return ApiResponse.success(importService.listAttributionDepartments(projectIds));
    }

    private static FactType parseType(String factType) {
        try {
            return FactType.fromPath(factType);
        } catch (IllegalArgumentException e) {
            throw new DataBridgeException(400, 400,
                    "ECOS-DATA-023：" + e.getMessage(), null);
        }
    }
}
