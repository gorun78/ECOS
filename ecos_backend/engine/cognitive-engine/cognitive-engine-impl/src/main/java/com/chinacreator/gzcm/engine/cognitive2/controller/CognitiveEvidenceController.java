package com.chinacreator.gzcm.engine.cognitive2.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.cognitive.EvidenceRecordVO;
import com.chinacreator.gzcm.engine.cognitive2.dto.EvidenceSaveDTO;
import com.chinacreator.gzcm.engine.cognitive2.service.CognitiveEvidenceService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 认知证据 REST API — 心智层"感知接入层"输出物（PMO-59 P2a / ADR-9）。
 *
 * <pre>
 * GET  /api/v1/cognitive/evidence            — 证据列表（status/sourceType 可选过滤）
 * GET  /api/v1/cognitive/evidence/{id}       — 证据详情
 * POST /api/v1/cognitive/evidence            — 登记结构化证据（evidence_code 幂等 + 自动冲突检测）
 * </pre>
 *
 * <p>三滤波器（铁律 §1.2）：/api/v1/ 前缀在 VersionPrefixRewriteFilter 中 KEEP
 * （cognitive 既有前缀同规则）；SecurityConfig permitAll 与 ClearanceInterceptor 豁免
 * 已由既有 {@code /api/v1/cognitive/**} 通配覆盖，无新增登记（PMO-59 P2a T4 核对结论）。</p>
 */
@RestController
@RequestMapping("/api/v1/cognitive/evidence")
public class CognitiveEvidenceController {

    private final CognitiveEvidenceService evidenceService;

    public CognitiveEvidenceController(CognitiveEvidenceService evidenceService) {
        this.evidenceService = evidenceService;
    }

    /** 证据列表（status/sourceType 可选过滤，create_time 倒序）。 */
    @GetMapping
    public ApiResponse<List<EvidenceRecordVO>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String sourceType) {
        return ApiResponse.success(evidenceService.list(status, sourceType));
    }

    /** 证据详情；无记录 → 404（统一返回体 code=404）。 */
    @GetMapping("/{id}")
    public ApiResponse<EvidenceRecordVO> getDetail(@PathVariable String id) {
        EvidenceRecordVO vo = evidenceService.getDetail(id);
        if (vo == null) {
            return ApiResponse.notFound("认知证据不存在: id=" + id);
        }
        return ApiResponse.success(vo);
    }

    /** 登记结构化证据（强类型入参，出参返回落库结果含服务端 id）。 */
    @PostMapping
    public ApiResponse<EvidenceRecordVO> register(@RequestBody EvidenceSaveDTO dto) {
        return ApiResponse.success(evidenceService.register(dto));
    }
}
