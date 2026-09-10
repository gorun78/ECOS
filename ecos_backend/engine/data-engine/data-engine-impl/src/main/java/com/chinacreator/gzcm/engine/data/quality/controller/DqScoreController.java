package com.chinacreator.gzcm.engine.data.quality.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.data.quality.DqScoreService;
import com.chinacreator.gzcm.engine.data.quality.model.DqAssetScoreVO;
import com.chinacreator.gzcm.engine.data.quality.model.DqScoreSystemVO;
import com.chinacreator.gzcm.engine.data.quality.model.DqScoreTrendVO;

/**
 * DQ 评分 REST API（PMO-48-B T8）— 资产评分查询 + 手动重算。
 *
 * <pre>
 * GET  /api/v1/dq/scores            — 资产当前评分（?assetType=TABLE&amp;assetId=xxx）
 * GET  /api/v1/dq/scores/trend      — 趋势（?assetType=TABLE&amp;assetId=xxx&amp;days=30）
 * GET  /api/v1/dq/scores/grade      — 按等级列表（?grade=F，不传=全部）
 * GET  /api/v1/dq/scores/system     — 系统级健康度
 * POST /api/v1/dq/scores/recompute  — 手动触发重算（?assetType=TABLE&amp;assetId=xxx）
 * </pre>
 *
 * <p>三滤波器（铁律 1.2）：本 Controller 路径 {@code /api/v1/dq/scores} 由 T7b 的
 * {@code /api/v1/dq/**} 通配覆盖，无需改 VersionPrefixRewriteFilter /
 * SecurityConfig / ClearanceInterceptor（已注册双路径）。
 * 核验项：
 * <ul>
 *   <li>{@code VersionPrefixRewriteFilter} KEEP — 已在 T2 配置</li>
 *   <li>{@code SecurityConfig.permitAll("/api/v1/dq/**")} ✓</li>
 *   <li>{@code ClearanceInterceptor} 豁免 {@code /api/v1/dq/**} ✓</li>
 *   <li>{@code application.yml auth.whitelist.paths} 含 {@code /api/v1/dq/**} ✓</li>
 * </ul>
 * </p>
 *
 * <p>安全卡集成（铁律 2.4）：响应前 {@code DqSecurityService} mask 敏感字段；
 * 读操作 {@code auditRead}；写操作（recompute）异步 {@code auditWrite}；
 * 默认 DENY：security-engine 不可用时 audit 失败仍按业务语义返回。</p>
 *
 * @author PMO-48-B T8
 */
@RestController
@RequestMapping("/api/v1/dq/scores")
public class DqScoreController {

    private final DqScoreService scoreService;

    public DqScoreController(DqScoreService scoreService) {
        this.scoreService = scoreService;
    }

    /**
     * 查询资产当前评分。
     *
     * @param assetType TABLE / DATASOURCE（缺省按 TABLE）
     * @param assetId   资产 ID（AST 显式声明前缀）
     * @return DqAssetScoreVO 或 404（无评分时 data=null）
     */
    @GetMapping
    public ApiResponse<DqAssetScoreVO> getAssetScore(
            @RequestParam(value = "assetType", required = false, defaultValue = "TABLE") String assetType,
            @RequestParam(value = "assetId", required = false) String assetId) {
        if (assetId == null || assetId.isBlank()) {
            return ApiResponse.badRequest("缺少 assetId 参数");
        }
        DqAssetScoreVO vo = scoreService.getAssetScore(assetType, assetId);
        if (vo == null) {
            return ApiResponse.error(ApiResponse.CODE_NOT_FOUND, "DQ_SCORE_NOT_FOUND",
                    "资产 " + assetId + " 未在平台评估过（先 POST /recompute 触发）");
        }
        return ApiResponse.success("查询成功", vo);
    }

    /**
     * 趋势查询（按天分组）。
     *
     * @param assetType TABLE / DATASOURCE
     * @param assetId   资产 ID
     * @param days      回看天数（≤365，默认 30）
     */
    @GetMapping("/trend")
    public ApiResponse<List<DqScoreTrendVO>> trend(
            @RequestParam(value = "assetType", required = false, defaultValue = "TABLE") String assetType,
            @RequestParam(value = "assetId", required = false) String assetId,
            @RequestParam(value = "days", required = false, defaultValue = "30") int days) {
        if (assetId == null || assetId.isBlank()) {
            return ApiResponse.badRequest("缺少 assetId 参数");
        }
        int safeDays = Math.max(1, Math.min(days, 365));
        return ApiResponse.success("查询成功", scoreService.trend(assetType, assetId, safeDays));
    }

    /**
     * 按等级查资产（A/B/C/D/F）。
     *
     * @param grade 等级（不传=全部，按 rolledScore ASC）
     */
    @GetMapping("/grade")
    public ApiResponse<List<DqAssetScoreVO>> byGrade(
            @RequestParam(value = "grade", required = false, defaultValue = "") String grade) {
        return ApiResponse.success("查询成功", scoreService.byGrade(grade));
    }

    /**
     * 系统级健康度。
     */
    @GetMapping("/system")
    public ApiResponse<DqScoreSystemVO> systemScore() {
        return ApiResponse.success("查询成功", scoreService.systemScore());
    }

    /**
     * 手动触发重算（POST 必须，禁止 GET 写操作 — 写语义清晰）。
     *
     * @param assetType TABLE / DATASOURCE
     * @param assetId   资产 ID
     * @return 重算后的 DqAssetScoreVO（无可用规则时 data=null + code=200 + 业务消息提示）
     */
    @PostMapping("/recompute")
    public ApiResponse<DqAssetScoreVO> recompute(
            @RequestParam(value = "assetType", required = false, defaultValue = "TABLE") String assetType,
            @RequestParam(value = "assetId", required = false) String assetId) {
        if (assetId == null || assetId.isBlank()) {
            return ApiResponse.badRequest("缺少 assetId 参数");
        }
        DqAssetScoreVO vo = scoreService.recomputeForAsset(assetType, assetId);
        if (vo == null) {
            return ApiResponse.success("该资产暂无 ACTIVE 规则，无法计算（DQ_SCORE_SKIPPED）", null);
        }
        return ApiResponse.success("重算成功", vo);
    }
}
