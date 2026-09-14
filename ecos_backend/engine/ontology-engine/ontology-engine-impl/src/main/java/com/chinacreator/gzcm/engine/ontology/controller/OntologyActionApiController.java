package com.chinacreator.gzcm.engine.ontology.controller;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyActionResultVO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyActionProposalVO;
import com.chinacreator.gzcm.engine.ontology.service.OntologyActionService;

/**
 * 动作设计器 Agent REST API — Action 执行与提案查询（Agent 行动执行端点）。
 *
 * <h3>端点：</h3>
 * <ul>
 *   <li>POST /api/v1/ontology/actions/{id}/execute    — 执行审批通过的 Action（接收 payload，调用 service 执行，返回结果）</li>
 *   <li>GET  /api/v1/ontology/actions/{id}/proposals  — 查看 Action 的待审批提案列表（占位，返回空列表）</li>
 * </ul>
 *
 * <p>本控制器仅新增 Agent 执行/提案相关端点，不改动 {@link OntologyActionController} 的现有 CRUD 签名。</p>
 *
 * <p>T16-1 (2026-09-12)：方法入/出参由 {@code Map<String,Object>} 改为强类型
 * （{@code OntologyActionResultVO} / {@code OntologyActionProposalVO}）；
 * {@code executeAction} 的 payload 入参保留 {@code Map<String,Object>}（业务载荷动态结构，
 * service 接受原始 Map，属于"嵌套数据动态值"外层强类型 VO 约束例外。</p>
 */
@RestController
@RequestMapping("/api/v1/ontology/actions")
public class OntologyActionApiController {

    private static final Logger log = LoggerFactory.getLogger(OntologyActionApiController.class);

    private final OntologyActionService actionService;

    public OntologyActionApiController(OntologyActionService actionService) {
        this.actionService = actionService;
    }

    /**
     * 执行审批通过的 Action。
     *
     * @param id      Action 主键
     * @param payload 执行入参（可为空；动态结构，保留 Map）
     * @return 执行结果（{@link OntologyActionResultVO}）
     */
    @PostMapping("/{id}/execute")
    public ApiResponse<OntologyActionResultVO> executeAction(
            @PathVariable String id,
            @RequestBody(required = false) Map<String, Object> payload) {
        try {
            OntologyActionResultVO result = actionService.executeActionVO(id, payload);
            log.info("Action executed via Agent API: {}", id);
            return ApiResponse.success(result);
        } catch (IllegalArgumentException e) {
            return ApiResponse.notFound(e.getMessage());
        }
    }

    /**
     * 查看 Action 的待审批提案列表（占位实现，返回空列表）。
     *
     * @param id Action 主键
     * @return 待审批提案列表（当前为空）
     */
    @GetMapping("/{id}/proposals")
    public ApiResponse<List<OntologyActionProposalVO>> listProposals(@PathVariable String id) {
        // 占位实现：当前无提案持久化能力，返回空列表
        log.debug("List proposals for action {} (placeholder, empty)", id);
        return ApiResponse.success(Collections.emptyList());
    }
}
