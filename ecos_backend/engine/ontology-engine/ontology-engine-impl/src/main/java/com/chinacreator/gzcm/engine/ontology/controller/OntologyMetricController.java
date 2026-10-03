package com.chinacreator.gzcm.engine.ontology.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ontology.dto.MetricCaliberBindingDTO;
import com.chinacreator.gzcm.engine.ontology.dto.MetricDefinitionVO;
import com.chinacreator.gzcm.engine.ontology.dto.MetricSaveDTO;
import com.chinacreator.gzcm.engine.ontology.model.MetricDefinition;
import com.chinacreator.gzcm.engine.ontology.service.MetricDefinitionService;
import com.chinacreator.gzcm.engine.ontology.service.MetricDefinitionService.MetricException;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * 指标定义 REST（F03-01/F03-09 / D.1）。
 *
 * <p>指标定义 = 语义资产（控制域），唯一可写入口（F03-09 语义二分"定义"侧）。
 * {@code PUBLISHED} 指标无口径 → 400 {@code ECOS-ONTO-040}；快照一次性冻结后只读。
 */
@RestController
@RequestMapping("/api/v1/ontology/metrics")
public class OntologyMetricController {

    private final MetricDefinitionService metricService;

    public OntologyMetricController(MetricDefinitionService metricService) {
        this.metricService = metricService;
    }

    @GetMapping
    public ApiResponse<List<MetricDefinitionVO>> listMetricDefinitions() {
        List<MetricDefinitionVO> vos = metricService.listAll().stream()
                .map(MetricDefinitionVO::from)
                .collect(Collectors.toList());
        return ApiResponse.success(vos);
    }

    @PostMapping
    public ApiResponse<MetricDefinitionVO> createMetric(@RequestBody MetricSaveDTO dto,
                                                        @RequestHeader(value = "X-User-Id", required = false) String user) {
        MetricDefinition m = new MetricDefinition();
        m.setCode(dto.getCode());
        m.setName(dto.getName());
        m.setExpression(dto.getExpression());
        m.setAggregation(dto.getAggregation());
        m.setEntityCode(dto.getEntityCode());
        m.setCaliberId(dto.getCaliberId());
        m.setFormulaVersion(dto.getFormulaVersion());
        m.setStatus(dto.getStatus());
        MetricDefinition created = metricService.create(m, operator(user));
        return ApiResponse.success(MetricDefinitionVO.from(created));
    }

    @PostMapping("/{code}/binding")
    public ApiResponse<MetricDefinitionVO> bindMetricCaliber(@PathVariable String code,
                                                             @RequestBody MetricCaliberBindingDTO dto,
                                                             @RequestHeader(value = "X-User-Id", required = false) String user) {
        MetricDefinition m = metricService.bindMetricCaliber(
                code, dto.getCaliberId(), dto.getFormulaVersion(), dto.getStatus(), operator(user));
        return ApiResponse.success(MetricDefinitionVO.from(m));
    }

    private static String operator(String user) {
        return (user == null || user.isBlank()) ? "system" : user;
    }

    @ExceptionHandler(MetricException.class)
    public ApiResponse<Void> handleBusiness(MetricException e) {
        return ApiResponse.error(e.httpStatus, e.code, e.getMessage());
    }
}
