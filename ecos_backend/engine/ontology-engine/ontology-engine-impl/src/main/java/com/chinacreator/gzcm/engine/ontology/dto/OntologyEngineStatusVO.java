package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 引擎运行时状态 VO — T16-5 强类型返回容器（status / start / stop 端点共用）。
 *
 * <p>对齐 {@code OntologyEngineStatusController.status / start / stop} 既有
 * {@code Map.of("name", ..., "status", ...)} 输出契约（固定两键）。
 *
 * <p>{@code name} 为引擎名（{@code "ontology-engine"}）；{@code status} 为
 * {@code EngineStatus} 枚举名（RUNNING / DEGRADED / STOPPED）。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OntologyEngineStatusVO {

    /** 引擎名（如 ontology-engine） */
    private String name;

    /** 引擎状态（RUNNING / DEGRADED / STOPPED） */
    private String status;
}
