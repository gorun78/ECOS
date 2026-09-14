package com.chinacreator.gzcm.engine.cognitive2.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.cognitive.BeliefDistributionVO;
import com.chinacreator.gzcm.engine.cognitive2.dto.BeliefSaveDTO;
import com.chinacreator.gzcm.engine.cognitive2.service.CognitiveBeliefService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 不确定性判断 REST API — 心智层 P 库（PMO-59 P2a / ADR-9 外部方案层3.2）。
 *
 * <pre>
 * GET  /api/v1/cognitive/beliefs           — 列表（domain 必填强制查询参数 + variableName 可选过滤；
 *                                            Phase 1 验收记录残留风险 4 落点：跨域重名变量隔离）
 * GET  /api/v1/cognitive/beliefs/{id}      — 详情（含分布版本/覆写标记/证据溯源）
 * POST /api/v1/cognitive/beliefs           — 注册（首版或同变量同域下一 version；prob 和=1 强校验）
 * </pre>
 *
 * <p>术语：业务方统一称"不确定性判断"（原稿"信念"改称）；查询强制带 domain 参数校验。</p>
 *
 * <p>P2a 边界：新证据加权更新（{@code beliefs/{variable}/update-by-evidence}）与人工覆写
 * （{@code beliefs/{variable}/override}）P2b 落地（api-contract §3.4 已预登记，路径只增不改）。</p>
 */
@RestController
@RequestMapping("/api/v1/cognitive/beliefs")
public class CognitiveBeliefController {

    private final CognitiveBeliefService beliefService;

    public CognitiveBeliefController(CognitiveBeliefService beliefService) {
        this.beliefService = beliefService;
    }

    /** 列表 — domain 必填（缺省 400）；variableName 可选过滤同变量多版本。 */
    @GetMapping
    public ApiResponse<List<BeliefDistributionVO>> list(
            @RequestParam String domain,
            @RequestParam(required = false) String variableName,
            @RequestParam(required = false) String status) {
        List<BeliefDistributionVO> all = beliefService.list(domain, status);
        if (variableName == null || variableName.isBlank()) {
            return ApiResponse.success(all);
        }
        String filter = variableName.trim();
        return ApiResponse.success(all.stream()
            .filter(vo -> filter.equals(vo.getVariableName()))
            .toList());
    }

    /** 详情；无记录 → 404。 */
    @GetMapping("/{id}")
    public ApiResponse<BeliefDistributionVO> getDetail(@PathVariable String id) {
        BeliefDistributionVO vo = beliefService.getDetail(id);
        if (vo == null) {
            return ApiResponse.notFound("不确定性判断不存在: id=" + id);
        }
        return ApiResponse.success(vo);
    }

    /** 注册（强类型 DTO；Service 层校验 prob 和=1，同变量同域 version=max+1）。 */
    @PostMapping
    public ApiResponse<BeliefDistributionVO> register(@RequestBody BeliefSaveDTO dto) {
        return ApiResponse.success(beliefService.register(dto));
    }
}
