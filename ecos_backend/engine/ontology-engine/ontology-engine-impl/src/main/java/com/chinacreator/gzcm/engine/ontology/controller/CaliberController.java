package com.chinacreator.gzcm.engine.ontology.controller;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ontology.dto.CaliberSaveDTO;
import com.chinacreator.gzcm.engine.ontology.dto.CaliberVO;
import com.chinacreator.gzcm.engine.ontology.dto.CaliberVersionSaveDTO;
import com.chinacreator.gzcm.engine.ontology.dto.MetricBindingDTO;
import com.chinacreator.gzcm.engine.ontology.service.CaliberService;
import com.chinacreator.gzcm.sysman.iam.context.UserContext;

/**
 * Caliber Controller（W73/M0 · F03-01 + W76/M0 · rule 4）。
 *
 * <p>口径主权归本体域：本接口是唯一写入口 + 跨引擎只读面。
 * {@code GET /{code}/current} 是 cognitive/workspace/kb 的<b>只读消费面</b>
 * （V214 引用视图语义等价物）。</p>
 *
 * <h3>端点：</h3>
 * <ul>
 *   <li>GET    /api/v1/ontology/calibers — 列表（status 过滤 + limit，默认 50）</li>
 *   <li>GET    /api/v1/ontology/calibers/{code} — 详情（含版本历史）</li>
 *   <li>GET    /api/v1/ontology/calibers/{code}/current — 当前生效（最近 APPROVED）版本行（跨引擎只读）</li>
 *   <li>POST   /api/v1/ontology/calibers — 新建 DRAFT 口径</li>
 *   <li>POST   /api/v1/ontology/calibers/{code}/versions — 升版（行不可变）</li>
 *   <li>POST   /api/v1/ontology/calibers/{code}/versions/{versionNo}/approve — DRAFT/REVIEWING→APPROVED</li>
 *   <li>POST   /api/v1/ontology/calibers/{code}/versions/{versionNo}/retire — APPROVED→RETIRED</li>
 *   <li>POST   /api/v1/ontology/calibers/metrics/{metricCode}/binding — 绑定口径 + 冻结快照</li>
 * </ul>
 *
 * <p>错误码映射（规则 3 / 规则 4）：
 * <ul>
 *   <li>{@link CaliberService.SnapshotFrozenException} → 409 {@code ECOS-ONTO-041}（快照只读）;</li>
 *   <li>{@link CaliberService.CaliberMissingException} / {@link CaliberService.CaliberNotApprovedException}
 *       → 400 {@code ECOS-ONTO-040}（PUBLISHED 无口径 / 口径版本非 APPROVED）;</li>
 *   <li>身份缺失（approve 无 Username） → 400 {@code ECOS-ONTO-040};</li>
 *   <li>其它 → 500 {@code ECOS-ONTO-042}（fail-closed）。</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/v1/ontology/calibers")
public class CaliberController {

    private static final Logger log = LoggerFactory.getLogger(CaliberController.class);

    private static final String CODE_OK = "ECOS-ONTO-000";
    private static final String CODE_BAD = "ECOS-ONTO-040";
    private static final String CODE_FROZEN = "ECOS-ONTO-041";
    private static final String CODE_INTERNAL = "ECOS-ONTO-042";

    private final CaliberService caliberService;

    public CaliberController(CaliberService caliberService) {
        this.caliberService = caliberService;
    }

    /** 口径列表（status 过滤 + limit 默认 50）。 */
    @GetMapping
    public ApiResponse<List<CaliberVO>> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false, defaultValue = "50") int limit) {
        try {
            List<CaliberVO> vo = caliberService.listCalibers(status, Math.max(1, Math.min(limit, 500)))
                    .stream().map(CaliberVO::of).toList();
            return ApiResponse.success(vo);
        } catch (Exception e) {
            log.error("list calibers failed", e);
            return ApiResponse.error(500, CODE_INTERNAL, "list calibers failed");
        }
    }

    /** 口径详情（含版本历史）。 */
    @GetMapping("/{code}")
    public ApiResponse<CaliberVO> detail(@PathVariable String code) {
        try {
            Map<String, Object> row = caliberService.findByCode(code)
                    .orElseGet(() -> null);
            if (row == null) {
                return ApiResponse.error(ApiResponse.CODE_NOT_FOUND, "CAL-404",
                        "caliber '" + code + "' not found");
            }
            CaliberVO vo = CaliberVO.of(row);
            if (vo.getVersions() == null) {
                vo.setVersions(new java.util.ArrayList<>());
            }
            for (Map<String, Object> v : caliberService.listVersionsByCaliberId(
                    String.valueOf(row.get("id")), null)) {
                vo.getVersions().add(v);
            }
            return ApiResponse.success(vo);
        } catch (Exception e) {
            log.error("caliber detail failed code={}", code, e);
            return ApiResponse.error(500, CODE_INTERNAL, "caliber detail failed");
        }
    }

    /**
     * 当前生效版本行 = 跨引擎（cognitive/workspace/kb）只读消费面。
     */
    @GetMapping("/{code}/current")
    public ApiResponse<Map<String, Object>> current(@PathVariable String code) {
        try {
            Map<String, Object> row = caliberService.getByCaliberCodeAndCurrentVersion(code)
                    .orElseGet(() -> null);
            if (row == null) {
                return ApiResponse.error(ApiResponse.CODE_NOT_FOUND, "CAL-404",
                        "no current APPROVED caliber version for '" + code + "'");
            }
            return ApiResponse.success(row);
        } catch (Exception e) {
            log.error("caliber current failed code={}", code, e);
            return ApiResponse.error(500, CODE_INTERNAL, "caliber current failed");
        }
    }

    /** 新建 DRAFT 口径。 */
    @PostMapping
    public ApiResponse<CaliberVO> create(@RequestBody CaliberSaveDTO dto) {
        try {
            Map<String, Object> row = caliberService.insertCaliber(
                    dto.getCode(), dto.getName(), dto.getUnit(), dto.getCurrency(),
                    dto.getOwnerRole(), dto.getDimensionJson(), dto.getStatus());
            return ApiResponse.success(CaliberVO.of(row));
        } catch (Exception e) {
            log.error("create caliber failed", e);
            return ApiResponse.error(500, CODE_INTERNAL, "create caliber failed");
        }
    }

    /** 升版（行不可变；status 缺省 DRAFT）。 */
    @PostMapping("/{code}/versions")
    public ApiResponse<Map<String, Object>> addVersion(
            @PathVariable String code, @RequestBody CaliberVersionSaveDTO dto) {
        try {
            Map<String, Object> caliber = caliberService.findByCode(code)
                    .orElseGet(() -> null);
            if (caliber == null) {
                return ApiResponse.error(ApiResponse.CODE_NOT_FOUND, "CAL-404",
                        "caliber '" + code + "' not found");
            }
            String versionNo = (dto.getVersionNo() == null || dto.getVersionNo().isBlank())
                    ? nextVersionNo(String.valueOf(caliber.get("id")))
                    : dto.getVersionNo();
            Map<String, Object> ver = caliberService.insertVersion(
                    String.valueOf(caliber.get("id")), versionNo, dto.getFormula(),
                    dto.getAdditive(), dto.getPeriodGranularity(), dto.getStatus());
            return ApiResponse.success(ver);
        } catch (Exception e) {
            log.error("add caliber version failed code={}", code, e);
            return ApiResponse.error(500, CODE_INTERNAL, "add caliber version failed");
        }
    }

    /** 审批：DRAFT/REVIEWING→APPROVED；身份缺失 → 400 ECOS-ONTO-040。 */
    @PostMapping("/{code}/versions/{versionNo}/approve")
    public ApiResponse<Map<String, Object>> approve(
            @PathVariable String code, @PathVariable String versionNo) {
        String approver = UserContext.getCurrentUsername();
        if (approver == null || approver.isBlank()) {
            return ApiResponse.error(ApiResponse.CODE_BAD_REQUEST, CODE_BAD,
                    "approver identity missing (身份缺失)");
        }
        try {
            Map<String, Object> caliber = caliberService.findByCode(code)
                    .orElseGet(() -> null);
            if (caliber == null) {
                return ApiResponse.error(ApiResponse.CODE_NOT_FOUND, "CAL-404",
                        "caliber '" + code + "' not found");
            }
            String cid = String.valueOf(caliber.get("id"));
            // 直接 DRAFT/REVIEWING → APPROVED（Service 白名单含 DRAFT→APPROVED）
            int affected = caliberService.archiveVersion(cid, versionNo, "APPROVED", approver);
            if (affected == 0) {
                return ApiResponse.error(ApiResponse.CODE_BAD_REQUEST, CODE_BAD,
                        "caliber version '" + versionNo + "' is not approvable from its current state");
            }
            return ApiResponse.success(Map.of("code", code, "versionNo", versionNo, "status", "APPROVED",
                    "approvedBy", approver));
        } catch (Exception e) {
            log.error("approve caliber version failed code={} v={}", code, versionNo, e);
            return ApiResponse.error(500, CODE_INTERNAL, "approve caliber version failed");
        }
    }

    /** 退休：APPROVED→RETIRED。 */
    @PostMapping("/{code}/versions/{versionNo}/retire")
    public ApiResponse<Map<String, Object>> retire(
            @PathVariable String code, @PathVariable String versionNo) {
        String actor = UserContext.getCurrentUsername();
        try {
            Map<String, Object> caliber = caliberService.findByCode(code)
                    .orElseGet(() -> null);
            if (caliber == null) {
                return ApiResponse.error(ApiResponse.CODE_NOT_FOUND, "CAL-404",
                        "caliber '" + code + "' not found");
            }
            int affected = caliberService.archiveVersion(
                    String.valueOf(caliber.get("id")), versionNo, "RETIRED", actor);
            if (affected == 0) {
                return ApiResponse.error(ApiResponse.CODE_BAD_REQUEST, CODE_BAD,
                        "caliber version '" + versionNo + "' is not RETIRE-able (already terminal)");
            }
            return ApiResponse.success(Map.of("code", code, "versionNo", versionNo, "status", "RETIRED"));
        } catch (Exception e) {
            log.error("retire caliber version failed code={} v={}", code, versionNo, e);
            return ApiResponse.error(500, CODE_INTERNAL, "retire caliber version failed");
        }
    }

    /** 绑定口径 + 冻结快照（规则 3 冻结 → 409 ECOS-ONTO-041）。 */
    @PostMapping("/metrics/{metricCode}/binding")
    public ApiResponse<Map<String, Object>> bindMetric(
            @PathVariable String metricCode, @RequestBody MetricBindingDTO dto) {
        try {
            Map<String, Object> caliber = caliberService.findByCode(dto.getCaliberCode())
                    .orElseGet(() -> null);
            if (caliber == null) {
                return ApiResponse.error(ApiResponse.CODE_BAD_REQUEST, CODE_BAD,
                        "caliber '" + dto.getCaliberCode() + "' not found");
            }
            Map<String, Object> result = caliberService.bindMetricCaliber(
                    metricCode, String.valueOf(caliber.get("id")), dto.getFormulaVersion());
            return ApiResponse.success(result);
        } catch (CaliberService.SnapshotFrozenException e) {
            return ApiResponse.error(409, CODE_FROZEN, e.getMessage());
        } catch (CaliberService.CaliberMissingException e) {
            return ApiResponse.error(ApiResponse.CODE_BAD_REQUEST, CODE_BAD, e.getMessage());
        } catch (CaliberService.CaliberNotApprovedException e) {
            return ApiResponse.error(ApiResponse.CODE_BAD_REQUEST, CODE_BAD, e.getMessage());
        } catch (Exception e) {
            log.error("bind metric caliber failed metric={}", metricCode, e);
            return ApiResponse.error(500, CODE_INTERNAL, "bind metric caliber failed");
        }
    }

    /** 现版号 +1（版本号序列，字符串形态 "1"→"2"）。 */
    private String nextVersionNo(String caliberId) {
        List<Map<String, Object>> versions = caliberService.listVersionsByCaliberId(caliberId, null);
        int max = 0;
        for (Map<String, Object> v : versions) {
            try {
                max = Math.max(max, Integer.parseInt(String.valueOf(v.get("version_no")).trim()));
            } catch (NumberFormatException ignored) {
                // 非数字版本号，跳过
            }
        }
        return String.valueOf(max + 1);
    }
}
