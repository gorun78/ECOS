package com.chinacreator.gzcm.engine.ontology.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.common.engine.HealthCheck;
import com.chinacreator.gzcm.engine.ontology.OntologyEngineImpl;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyEngineStatusVO;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 本体引擎运行时状态 REST API — T16-5 强类型返回。
 *
 * <p>T16-5：/status /start /stop 返回固定两键状态结构 → {@link OntologyEngineStatusVO}
 * （替代既有 {@code Map.of("name", ..., "status", ...)}）；/health 本就返回强类型
 * {@link HealthCheck}（common-api 契约，不动）。
 *
 * <p>T16-5: 引擎状态动态结构豁免 — /config 为 {@code IEngine.getConfig()}
 * （common-api 接口签名，Map）动态配置探测结构，保留 Map 返回。
 */
@RestController
@RequestMapping("/api/v1/engine/ontology")
public class OntologyEngineStatusController {

    private final OntologyEngineImpl engine;

    public OntologyEngineStatusController(OntologyEngineImpl engine) {
        this.engine = engine;
    }

    @GetMapping("/health")
    public ApiResponse<HealthCheck> health() {
        return ApiResponse.success(engine.healthCheck());
    }

    @GetMapping("/config")
    public ApiResponse<Map<String, Object>> config() {
        // T16-5: 引擎状态动态结构豁免（IEngine.getConfig 为 common-api 契约签名，保留 Map）
        return ApiResponse.success(engine.getConfig());
    }

    @GetMapping("/status")
    public ApiResponse<OntologyEngineStatusVO> status() {
        return ApiResponse.success(toStatusVO());
    }

    @PostMapping("/start")
    public ApiResponse<OntologyEngineStatusVO> start() {
        engine.start();
        return ApiResponse.success(toStatusVO());
    }

    @PostMapping("/stop")
    public ApiResponse<OntologyEngineStatusVO> stop() {
        engine.stop();
        return ApiResponse.success(toStatusVO());
    }

    /** 构造状态 VO（name/status 两键，与既有 Map.of 输出结构一致）。 */
    private OntologyEngineStatusVO toStatusVO() {
        OntologyEngineStatusVO vo = new OntologyEngineStatusVO();
        vo.setName(engine.getName());
        vo.setStatus(engine.getStatus().name());
        return vo;
    }
}
