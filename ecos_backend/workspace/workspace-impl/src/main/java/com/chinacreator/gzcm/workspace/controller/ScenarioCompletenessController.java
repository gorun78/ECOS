package com.chinacreator.gzcm.workspace.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.workspace.scenario.BindingNode;
import com.chinacreator.gzcm.workspace.scenario.CompletenessDataLoader;
import com.chinacreator.gzcm.workspace.scenario.CompletenessReport;
import com.chinacreator.gzcm.workspace.scenario.LinkEdge;
import com.chinacreator.gzcm.workspace.scenario.ScenarioCompletenessService;
import com.chinacreator.gzcm.workspace.scenario.WorkspaceSecurityAudit;
import com.chinacreator.gzcm.workspace.security.ScenarioReadGuard;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 完整度端点（卷07 F07-02 N1，后端单源，前端只渲染不重算）。
 * <pre>GET /api/v1/workspace/scenarios/{id}/completeness</pre>
 */
@RestController
@RequestMapping("/api/v1/workspace/scenarios")
public class ScenarioCompletenessController {

    private final CompletenessDataLoader loader;
    private final ScenarioCompletenessService completenessService;
    private final WorkspaceSecurityAudit audit;
    private final ScenarioReadGuard readGuard;

    public ScenarioCompletenessController(CompletenessDataLoader loader,
                                          ScenarioCompletenessService completenessService,
                                          WorkspaceSecurityAudit audit,
                                          ScenarioReadGuard readGuard) {
        this.loader = loader;
        this.completenessService = completenessService;
        this.audit = audit;
        this.readGuard = readGuard;
    }

    @GetMapping("/{id}/completeness")
    public ApiResponse<CompletenessReport> completeness(@PathVariable("id") String scenarioId,
                                                        @RequestParam(name = "refresh", required = false, defaultValue = "false") boolean refresh) {
        // F07-18：拓扑/完整度属敏感读，先过 RLS fail-DENY 咽喉（security 不可用 → 403，禁 200 数据）
        readGuard.assertReadable("scenario_completeness");
        List<BindingNode> bindings = loader.bindingsOf(scenarioId);
        List<LinkEdge> links = loader.linksOf(scenarioId);
        CompletenessReport report = refresh
                ? completenessService.compute(scenarioId, bindings, links)
                : completenessService.cached(scenarioId, bindings, links);
        // 敏感读审计（C-6：完整度揭示资源全貌）
        audit.record("scenario:completeness:read", scenarioId, report.getVerdict().name());
        return ApiResponse.success(report);
    }
}
