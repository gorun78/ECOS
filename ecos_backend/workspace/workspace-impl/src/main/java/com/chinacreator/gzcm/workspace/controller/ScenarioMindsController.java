package com.chinacreator.gzcm.workspace.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.common.exception.NotFoundException;
import com.chinacreator.gzcm.workspace.scenario.MindSaveDTO;
import com.chinacreator.gzcm.workspace.scenario.MindUpdatePartialDTO;
import com.chinacreator.gzcm.workspace.scenario.ScenarioMindService;
import com.chinacreator.gzcm.workspace.scenario.ScenarioMindVO;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 场景心智变体 REST API — PMO-60 v2.0 P1。
 *
 * <p>路径契约：
 * <pre>
 * GET    /api/v1/workspace/scenarios/{id}/minds            — 心智列表
 * POST   /api/v1/workspace/scenarios/{id}/minds            — 新建/更新心智（upsert）
 * PATCH  /api/v1/workspace/scenarios/{id}/minds/{mindId}   — 更新心智（三要素/四件套/active）
 * DELETE /api/v1/workspace/scenarios/{id}/minds/{mindId}   — 逻辑删除心智
 *
 * 兼容端点（v1.x 不断流）：
 * GET    /api/v1/workspace/scenarios/{id}/mind-model       — 获取 base mind
 * POST   /api/v1/workspace/scenarios/{id}/mind-model       — 保存 base mind
 * DELETE /api/v1/workspace/scenarios/{id}/mind-model       — 删除 base mind
 * </pre></p>
 */
@RestController
@RequestMapping("/api/v1/workspace/scenarios")
public class ScenarioMindsController {

    private static final Logger log = LoggerFactory.getLogger(ScenarioMindsController.class);

    private final ScenarioMindService mindService;

    public ScenarioMindsController(ScenarioMindService mindService) {
        this.mindService = mindService;
    }

    // ═══════════════ v2.0 多心智端点 ═══════════════

    /** 心智列表（激活优先排序）。 */
    @GetMapping("/{id}/minds")
    public ApiResponse<List<ScenarioMindVO>> listMinds(@PathVariable String id) {
        return ApiResponse.success(mindService.listMinds(id));
    }

    /** 新建心智（upsert 语义：同 label 则更新）。 */
    @PostMapping("/{id}/minds")
    public ApiResponse<ScenarioMindVO> createMind(@PathVariable String id,
                                                   @RequestBody MindSaveDTO dto) {
        return ApiResponse.success(mindService.upsertMind(id, dto, null, false));
    }

    /** 更新心智（PATCH 语义：改三要素/四件套/active，只更新非 null 字段）。 */
    @PatchMapping("/{id}/minds/{mindId}")
    public ApiResponse<ScenarioMindVO> updateMind(@PathVariable String id,
                                                    @PathVariable Long mindId,
                                                    @RequestBody MindUpdatePartialDTO patch) {
        // PATCH 语义：只更新非 null 字段
        MindSaveDTO dto = new MindSaveDTO();
        if (patch.getMindLabel() != null) {
            dto.setMindLabel(patch.getMindLabel());
        }
        if (patch.getInitialBelief() != null) {
            dto.setInitialBelief(patch.getInitialBelief());
        }
        if (patch.getEvidenceIds() != null) {
            dto.setEvidenceIds(patch.getEvidenceIds());
        }
        if (patch.getHypothesisIds() != null) {
            dto.setHypothesisIds(patch.getHypothesisIds());
        }
        if (patch.getModelIds() != null) {
            dto.setModelIds(patch.getModelIds());
        }
        if (patch.getCognitiveEndpoints() != null) {
            dto.setCognitiveEndpoints(patch.getCognitiveEndpoints());
        }
        if (patch.getInitialConfidence() != null) {
            dto.setInitialConfidence(patch.getInitialConfidence());
        }
        // 若 body 未指定 label，使用已有 mind 的 label（精确更新）
        String label = patch.getMindLabel() != null ? patch.getMindLabel() : findMindLabel(id, mindId);
        return ApiResponse.success(mindService.upsertMind(id, dto, label, false));
    }

    /** 逻辑删除心智（删 base 自动补位）。 */
    @DeleteMapping("/{id}/minds/{mindId}")
    public ApiResponse<Void> deleteMind(@PathVariable String id, @PathVariable Long mindId) {
        mindService.deleteMind(id, mindId);
        return ApiResponse.success();
    }

    // ═══════════════ B44/P1-20：认知域 Mind 引用面（只增端点，既有 Long 面零改动）═══════════════

    /**
     * 按认知 Mind 引用切换激活位（当前支持 {@code active:true|false}）。
     * 0 行受影响 ⇒ 404（目标不存在或未绑定），禁伪装成功。
     * <p>DI-2：请求体改强类型 record（键面与旧 Map 的 {@code active} 一致；
     * 缺省/空体沿用旧语义「非显式 false 即激活」）。</p>
     */
    @PatchMapping("/{id}/minds/ref/{mindRef}")
    public ApiResponse<ScenarioMindVO> updateByRef(@PathVariable("id") String id,
                                                   @PathVariable("mindRef") String mindRef,
                                                   @RequestBody(required = false) MindActiveRequest patch) {
        boolean active = patch == null || !Boolean.FALSE.equals(patch.active());
        int rows = mindService.setActiveByRef(id, mindRef, active);
        if (rows == 0) {
            throw new NotFoundException("MIND-404: mindRef 不存在或未绑定: " + mindRef);
        }
        ScenarioMindVO vo = new ScenarioMindVO();
        vo.setScenarioId(id);
        vo.setMindRef(mindRef);
        vo.setActiveMind(active ? 1 : 0);
        return ApiResponse.success(vo);
    }

    /** 激活位切换请求体（DI-2 强类型；未知键按旧 Map 行为忽略）。 */
    @com.fasterxml.jackson.annotation.JsonIgnoreProperties(ignoreUnknown = true)
    public record MindActiveRequest(Boolean active) {
    }

    /** 按认知 Mind 引用解绑（0 行受影响 ⇒ 404，如实失败）。 */
    @DeleteMapping("/{id}/minds/ref/{mindRef}")
    public ApiResponse<Void> deleteByRef(@PathVariable("id") String id,
                                         @PathVariable("mindRef") String mindRef) {
        int rows = mindService.unbindByRef(id, mindRef);
        if (rows == 0) {
            throw new NotFoundException("MIND-404: 场景未绑定该 Mind: " + mindRef);
        }
        return ApiResponse.success();
    }

    // ═══════════════ 兼容端点（v1.x /mind-model）═══════════════

    /** 兼容：获取 base mind。 */
    @GetMapping("/{id}/mind-model")
    public ApiResponse<ScenarioMindVO> getMindModel(@PathVariable String id) {
        ScenarioMindVO base = mindService.getMindBase(id);
        if (base == null) {
            throw new NotFoundException("MIND-404: base mind 不存在: scenario=" + id);
        }
        return ApiResponse.success(base);
    }

    /** 兼容：保存 base mind（upsert）。 */
    @PostMapping("/{id}/mind-model")
    public ApiResponse<ScenarioMindVO> saveMindModel(@PathVariable String id,
                                                       @RequestBody MindSaveDTO dto) {
        return ApiResponse.success(mindService.saveBaselineMind(id, dto));
    }

    /** 兼容：删除 base mind。 */
    @DeleteMapping("/{id}/mind-model")
    public ApiResponse<Void> deleteMindModel(@PathVariable String id) {
        ScenarioMindVO base = mindService.getMindBase(id);
        if (base == null) {
            throw new NotFoundException("MIND-404: base mind 不存在: scenario=" + id);
        }
        mindService.deleteMind(id, base.getId());
        return ApiResponse.success();
    }

    // ═══════════════ 私有辅助 ═══════════════

    /** 查找 mind 的 label（PATCH 时无 mindLabel 字段时回退用）。 */
    private String findMindLabel(String scenarioId, Long mindId) {
        ScenarioMindVO mind = mindService.getMind(mindId);
        return mind.getMindLabel();
    }
}
