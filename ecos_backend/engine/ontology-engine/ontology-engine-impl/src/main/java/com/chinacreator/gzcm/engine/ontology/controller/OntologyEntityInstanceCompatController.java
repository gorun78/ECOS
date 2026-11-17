package com.chinacreator.gzcm.engine.ontology.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 实体实例 v1 兼容端点（D34）。
 *
 * <p><b>背景</b>：前端 {@code OperationalApps.tsx} 调
 * {@code GET /api/v1/ontology/entities/{Customer,Facility}/instances}，但该面此前只有
 * {@code OntologyCompatController} 的无 v1 路径 {@code /api/ontology/entities/{type}/instances}
 * （live：无 v1 = 200 空列表，v1 = 404）⇒ {@code #/ops_apps} 首屏报错。</p>
 *
 * <p><b>为何独立 Controller 而非类级别名</b>：{@code OntologyCompatController} 若类级并列挂
 * {@code /api/v1/ontology}，其 {@code /mappings} 等子路径会与 {@code OntologyMappingController}
 * 的 {@code /api/v1/ontology/mappings} 撞车（实测 {@code /api/v1/ontology/mappings} 变 500）。
 * 故只为本子族建独立壳，不动其它路径（API 只增不改）。</p>
 *
 * <p><b>数据口径（无假真理铁律 7）</b>：本表无实体实例存储——{@code ecos_ontology.ecos_object_data}
 * 为 0 行；{@code ecos_control.demo_customer} 虽有 15 行，但缺前端 {@code CustomerData} 所需的
 * revenue/isActive/churn_risk 三字段，接上即编造。故如实返回空列表，页面呈现「无数据」空态。</p>
 *
 * @author ecos-factory
 * @since D34 (2026-11-16)
 */
@RestController
@RequestMapping("/api/v1/ontology")
public class OntologyEntityInstanceCompatController {

    private static final Logger log = LoggerFactory.getLogger(OntologyEntityInstanceCompatController.class);

    /** GET /api/v1/ontology/entities/{entityType}/instances — v1 兼容（返回空列表，不造 mock）。 */
    @GetMapping("/entities/{entityType}/instances")
    public ApiResponse<List<Map<String, Object>>> getEntityInstances(@PathVariable String entityType) {
        log.info("v1 entity instances requested: type={}; returning empty list (no mock data, D34).", entityType);
        return ApiResponse.success(new ArrayList<>());
    }
}
