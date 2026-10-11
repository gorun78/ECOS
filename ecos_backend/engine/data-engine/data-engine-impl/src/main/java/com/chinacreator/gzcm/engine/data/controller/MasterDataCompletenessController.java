package com.chinacreator.gzcm.engine.data.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.data.fact.masterdata.MasterDataCompletenessChecker;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * 主数据完备性检查 REST 面 — 详细设计-09 v1.2 §十一 H-6 交付⑤（只增不改，只读）。
 *
 * <p>{@code GET /api/v1/datanet/facts/master-data-completeness} — PRD-07 主数据完备性
 * 门禁的运行时检验面：逐笔明细须可定位合同基数（MD-C1）并命中归属行（MD-C2），
 * 项目级 Σ归属比率 ≤ 1（MD-C3）；REJECTED 行计入 blockedCount（fc_dq_block_trend 面）。
 *
 * <p>字面路径 {@code master-data-completeness} 与 {@code FactController} 的
 * {@code /{factType}} 模板共存：Spring 字面量优先匹配，语义无歧义。
 * 无任何写端点、无副作用。
 */
@RestController
@RequestMapping("/api/v1/datanet/facts/master-data-completeness")
public class MasterDataCompletenessController {

    private final MasterDataCompletenessChecker checker;

    public MasterDataCompletenessController(MasterDataCompletenessChecker checker) {
        this.checker = checker;
    }

    /** checkMasterDataCompleteness — 全量或按 projectId/period 切片的完备性报告。 */
    @GetMapping
    public ApiResponse<Map<String, Object>> check(
            @RequestParam(value = "projectId", required = false) String projectId,
            @RequestParam(value = "period", required = false) String period,
            @RequestParam(value = "limit", required = false,
                          defaultValue = "" + MasterDataCompletenessChecker.DEFAULT_SCAN_LIMIT)
            int limit) {
        return ApiResponse.success(checker.check(projectId, period, limit).toMap());
    }
}
