package com.chinacreator.gzcm.engine.ontology.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.ontology.dto.CaliberSaveDTO;
import com.chinacreator.gzcm.engine.ontology.dto.CaliberVersionSaveDTO;
import com.chinacreator.gzcm.engine.ontology.dto.CaliberVO;
import com.chinacreator.gzcm.engine.ontology.dto.CaliberVersionVO;
import com.chinacreator.gzcm.engine.ontology.model.Caliber;
import com.chinacreator.gzcm.engine.ontology.model.CaliberVersion;
import com.chinacreator.gzcm.engine.ontology.service.CaliberService;
import com.chinacreator.gzcm.engine.ontology.service.CaliberServiceImpl.BusinessException;

import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * 口径（caliber）REST（F03-01 / D.1）。
 *
 * <p>口径主权归 ontology（R-6①）：本控制器是唯一写入口；cognitive/workspace/kb 只经
 * {@code GET /calibers/{code}/current} 只读消费。所有路径挂 {@code /api/v1/ontology/calibers}，
 * 与 D.5 一致——不进三滤波器匿名面（默认 authenticated）。
 */
@RestController
@RequestMapping("/api/v1/ontology/calibers")
public class OntologyCaliberController {

    private final CaliberService caliberService;

    public OntologyCaliberController(CaliberService caliberService) {
        this.caliberService = caliberService;
    }

    @GetMapping
    public ApiResponse<List<CaliberVO>> listCalibers() {
        List<CaliberVO> vos = caliberService.listCalibers().stream()
                .map(c -> CaliberVO.from(c, currentVersionNo(c.getCode())))
                .collect(Collectors.toList());
        return ApiResponse.success(vos);
    }

    @GetMapping("/{code}/current")
    public ApiResponse<CaliberVersionVO> getCurrentCaliber(@PathVariable String code) {
        Optional<Caliber> cal = caliberService.findByCode(code);
        if (cal.isEmpty()) {
            return ApiResponse.error(404, "ECOS-ONTO-080", "口径不存在: " + code);
        }
        Optional<CaliberVersion> ver = caliberService.findCurrentApprovedVersion(code);
        if (ver.isEmpty()) {
            return ApiResponse.error(404, "ECOS-ONTO-080", "口径 " + code + " 无已批准版本");
        }
        return ApiResponse.success(CaliberVersionVO.from(ver.get(), code));
    }

    @PostMapping
    public ApiResponse<CaliberVO> createCaliber(@RequestBody CaliberSaveDTO dto,
                                                @RequestHeader(value = "X-User-Id", required = false) String user) {
        Caliber c = new Caliber();
        c.setCode(dto.getCode());
        c.setName(dto.getName());
        c.setStatus(dto.getStatus());
        c.setUnit(dto.getUnit());
        c.setCurrency(dto.getCurrency());
        c.setOwnerRole(dto.getOwnerRole());
        c.setDimensionJson(dto.getDimensionJson());
        Caliber created = caliberService.createCaliber(c, operator(user));
        return ApiResponse.success(CaliberVO.from(created, null));
    }

    @PostMapping("/{code}/versions")
    public ApiResponse<CaliberVersionVO> publishCaliberVersion(@PathVariable String code,
                                                               @RequestBody CaliberVersionSaveDTO dto,
                                                               @RequestHeader(value = "X-User-Id", required = false) String user) {
        Optional<Caliber> cal = caliberService.findByCode(code);
        if (cal.isEmpty()) {
            return ApiResponse.error(404, "ECOS-ONTO-080", "口径不存在: " + code);
        }
        CaliberVersion v = new CaliberVersion();
        v.setCaliberId(cal.get().getId());
        v.setVersionNo(dto.getVersionNo());
        v.setFormula(dto.getFormula());
        v.setAdditive(dto.getAdditive());
        v.setPeriodGranularity(dto.getPeriodGranularity());
        v.setStatus(dto.getStatus());
        v.setApprovedBy(dto.getApprovedBy());
        CaliberVersion created = caliberService.publishCaliberVersion(v, operator(user));
        return ApiResponse.success(CaliberVersionVO.from(created, code));
    }

    private String currentVersionNo(String code) {
        return caliberService.findCurrentApprovedVersion(code).map(CaliberVersion::getVersionNo).orElse(null);
    }

    private static String operator(String user) {
        return (user == null || user.isBlank()) ? "system" : user;
    }

    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusiness(BusinessException e) {
        return ApiResponse.error(e.httpStatus, e.code, e.getMessage());
    }
}
