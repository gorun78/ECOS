package com.chinacreator.gzcm.engine.ontology.controller;

import java.util.LinkedHashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.engine.ontology.dto.MetricBindingDTO;
import com.chinacreator.gzcm.engine.ontology.service.CaliberService;
import com.chinacreator.gzcm.sysman.security.MinimumClearance;

/**
 * #146u —— 差距台账附录A 第 35 条：{@code POST /api/v1/ontology/metrics/{code}/binding}
 * （{@code bindMetricCaliber}）契约路径。
 *
 * <h3>本批补的是什么</h3>
 * <p>原文记「偏差（挂 {@code /calibers} 下而非 {@code metrics} 下）」。契约行
 * （《ECOS详细设计说明书-从零实施版》Vol-3 接口总表）要求路径
 * {@code /api/v1/ontology/metrics/{code}/binding}、面级 {@code L2}、体
 * {@code caliberId+formulaVersion}、失败 {@code ECOS-ONTO-040}。本类按契约路径新建别名，
 * <b>旧面 {@code CaliberController#bindMetric}（{@code /api/v1/ontology/calibers/metrics/{metricCode}/binding}）
 * 一字未改</b>——前端 {@code ontologyClient.ts} 仍走旧面，additive 不破既有消费方。</p>
 *
 * <h3>为什么是「新类」而不是给旧类加 {@code @RequestMapping} 前缀</h3>
 * <p>{@link CaliberController} 的类级映射是 {@code /api/v1/ontology/calibers}，无法在同一类里
 * 再挂 {@code /api/v1/ontology/metrics}（一个类只能有一个前缀组），故别名必须独立成类。</p>
 *
 * <h3>钉住的四条如实口径（禁造假值）</h3>
 * <ol>
 *   <li><b>面级 L2 用方法级注解显式抬高</b>：{@code ClearanceInterceptor} 对 {@code /api/v1/} 的路径
 *       默认给 L1，不写 {@link MinimumClearance}(2) 就会低于契约 L2；方法级注解优先于路径规则。
 *       JWT 仍由 {@code SecurityConfig} 的 {@code anyRequest().authenticated()} 统一要求
 *       （本体域不进 permitAll，见 {@code OntologyAnonymousSurfaceTest}）；</li>
 *   <li><b>体键双收</b>：契约给的 {@code caliberId} 直接作 {@code caliber_id}；未给时回退旧键
 *       {@code caliberCode} 经 {@code findByCode} 折成 id，回执里 {@code resolvedBy} 自证走了哪条腿，
 *       两条腿都解析不出 → 400 {@code ECOS-ONTO-040}；</li>
 *   <li><b>版本存在性护栏</b>：{@code formulaVersion} 必须在 {@code ecos_caliber_version} 真有一行
 *       （{@code is_deleted=0}），否则 400 {@code ECOS-ONTO-040}。理由：
 *       {@code CaliberService#bindMetricCaliber} 对不存在的版本会以空 formula 冻结快照
 *       （旧面既有行为，本批不动旧面，仅登记），新别名不得把"冻结一个空公式"当成功；</li>
 *   <li><b>失败态一律抛携带型异常</b>：409 {@code ECOS-ONTO-041}（快照已冻结）／400
 *       {@code ECOS-ONTO-040}（口径缺失、版本非 APPROVED、入参缺失）／500 {@code ECOS-ONTO-042}
 *       （fail-closed），状态行由 {@link DataBridgeException#resolveHttpStatus(int)} 单源推出，
 *       不新增 {@code ApiResponse.error} 出口（保护 {@code OTHER_LEG_CEILING} 棘轮基线）。</li>
 * </ol>
 *
 * <p><b>未做并已登记</b>：{@code GET /api/v1/ontology/metrics}（{@code listMetricDefinitions}）不在本条
 * 差距项内，本批不补；旧面绑定的面级仍是路径默认 L1（前端在用，收紧需单独裁决），新别名更严（L2）。</p>
 */
@RestController
@RequestMapping("/api/v1/ontology/metrics")
public class OntologyMetricBindingController {

    private static final Logger log = LoggerFactory.getLogger(OntologyMetricBindingController.class);

    private static final String CODE_BAD = "ECOS-ONTO-040";
    private static final String CODE_FROZEN = "ECOS-ONTO-041";
    private static final String CODE_INTERNAL = "ECOS-ONTO-042";

    private final CaliberService caliberService;

    public OntologyMetricBindingController(CaliberService caliberService) {
        this.caliberService = caliberService;
    }

    /**
     * 显式绑版（升版不自动传播，详设-03 §2.2-2）：把指标绑定到口径 + 指定版本并冻结快照。
     *
     * @param metricCode    指标编码（路径变量 {@code {code}}）
     * @param dto           绑定入参：{@code caliberId}（契约键）或 {@code caliberCode}（旧键回退）
     *                      + {@code formulaVersion}
     * @return 绑定回执（服务返回值 + {@code caliberId}/{@code formulaVersion}/{@code resolvedBy} 回显）
     */
    @PostMapping("/{code}/binding")
    @MinimumClearance(level = 2)
    public ApiResponse<Map<String, Object>> bindMetricCaliber(
            @PathVariable("code") String metricCode,
            @RequestBody(required = false) MetricBindingDTO dto) {
        if (dto == null) {
            throw badRequest("request body is required (caliberId|caliberCode + formulaVersion)");
        }

        String caliberId = trimToNull(dto.getCaliberId());
        String resolvedBy = "caliberId";
        if (caliberId == null) {
            String caliberCode = trimToNull(dto.getCaliberCode());
            if (caliberCode == null) {
                throw badRequest("caliberId is required (旧键 caliberCode 可选回退)");
            }
            Map<String, Object> caliber = caliberService.findByCode(caliberCode).orElse(null);
            if (caliber == null) {
                throw badRequest("caliber '" + caliberCode + "' not found");
            }
            caliberId = String.valueOf(caliber.get("id"));
            resolvedBy = "caliberCode";
        }

        String formulaVersion = trimToNull(dto.getFormulaVersion());
        if (formulaVersion == null) {
            throw badRequest("formulaVersion is required");
        }
        requireVersionExists(caliberId, formulaVersion);

        try {
            Map<String, Object> result = caliberService.bindMetricCaliber(
                    metricCode, caliberId, formulaVersion);
            Map<String, Object> receipt = result == null
                    ? new LinkedHashMap<>() : new LinkedHashMap<>(result);
            receipt.put("caliberId", caliberId);
            receipt.put("formulaVersion", formulaVersion);
            receipt.put("resolvedBy", resolvedBy);
            return ApiResponse.success(receipt);
        } catch (CaliberService.SnapshotFrozenException e) {
            // rule 3：快照发布后只读
            throw new DataBridgeException(409, 409, CODE_FROZEN + ": " + e.getMessage());
        } catch (CaliberService.CaliberMissingException | CaliberService.CaliberNotApprovedException e) {
            // rule 4：PUBLISHED 无口径 / 版本非 APPROVED
            throw new DataBridgeException(400, 400, CODE_BAD + ": " + e.getMessage());
        } catch (DataBridgeException e) {
            throw e;
        } catch (Exception e) {
            log.error("bind metric caliber failed metric={} caliber={} v={}",
                    metricCode, caliberId, formulaVersion, e);
            throw new DataBridgeException(500, 500, CODE_INTERNAL + ": 指标口径绑定失败");
        }
    }

    /** 版本存在性护栏：服务层对不存在的版本会以空公式冻结快照，别名不得把该形态当成功。 */
    private void requireVersionExists(String caliberId, String formulaVersion) {
        boolean exists = caliberService.listVersionsByCaliberId(caliberId, null).stream()
                .anyMatch(v -> formulaVersion.equals(String.valueOf(v.get("version_no"))));
        if (!exists) {
            throw badRequest("caliber version '" + formulaVersion
                    + "' not found for caliber '" + caliberId + "'");
        }
    }

    private static DataBridgeException badRequest(String detail) {
        return new DataBridgeException(400, 400, CODE_BAD + ": " + detail);
    }

    private static String trimToNull(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }
}
