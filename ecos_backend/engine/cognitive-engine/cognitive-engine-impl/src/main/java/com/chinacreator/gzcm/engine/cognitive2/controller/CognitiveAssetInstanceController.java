package com.chinacreator.gzcm.engine.cognitive2.controller;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.cognitive.api.CognitiveApiPaths;
import com.chinacreator.gzcm.engine.cognitive2.service.BeliefStore;
import com.chinacreator.gzcm.engine.cognitive2.service.HypothesisStore;
import com.chinacreator.gzcm.engine.cognitive2.service.ModelRegistryService;

/**
 * 可绑定认知资产实例（V367 批次 E）：把 belief / hypothesis / model 行列成候选，
 * 供场景定义页绑定（{@code COGNITIVE_ASSET}），Context Builder 的 COGNITIVE reader 按
 * {@code bel/hyp/mod} 前缀解析同一 id。
 *
 * <p>只读投影：id + 展示名 + 类型；不返回分布/正文，避免把资产内容复制到绑定面。</p>
 */
@RestController
@RequestMapping(CognitiveApiPaths.COGNITIVE)
public class CognitiveAssetInstanceController {

    private final BeliefStore beliefStore;
    private final HypothesisStore hypothesisStore;
    private final ModelRegistryService modelRegistryService;

    public CognitiveAssetInstanceController(BeliefStore beliefStore,
                                            HypothesisStore hypothesisStore,
                                            ModelRegistryService modelRegistryService) {
        this.beliefStore = beliefStore;
        this.hypothesisStore = hypothesisStore;
        this.modelRegistryService = modelRegistryService;
    }

    @GetMapping(CognitiveApiPaths.ASSETS_INSTANCES_REL)
    public ApiResponse<List<Map<String, Object>>> instances(
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "domain", required = false) String domain) {
        String t = type == null || type.isBlank() ? "ALL" : type.trim().toUpperCase();
        List<Map<String, Object>> out = new ArrayList<>();
        if ("ALL".equals(t) || "BELIEF".equals(t)) {
            // belief 列表按 domain 过滤（属主约束）；未给 domain 时如实跳过，不做全库扫描
            if (domain != null && !domain.isBlank()) {
                for (Map<String, Object> row : beliefStore.list(domain, null)) {
                    out.add(item(row.get("id"), row.get("variable_name"), "BELIEF"));
                }
            }
        }
        if ("ALL".equals(t) || "HYPOTHESIS".equals(t)) {
            for (Map<String, Object> row : hypothesisStore.list(domain, null)) {
                out.add(item(row.get("id"), row.get("statement"), "HYPOTHESIS"));
            }
        }
        if ("ALL".equals(t) || "MODEL".equals(t)) {
            for (Map<String, Object> row : modelRegistryService.listModels(null)) {
                out.add(item(row.get("model_id"), first(row, "model_name", "modelName", "model_id"), "MODEL"));
            }
        }
        return ApiResponse.success(out);
    }

    private static Map<String, Object> item(Object id, Object name, String type) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", id == null ? null : String.valueOf(id));
        m.put("name", name == null ? null : String.valueOf(name));
        m.put("type", type);
        return m;
    }

    private static Object first(Map<String, Object> row, String... keys) {
        for (String k : keys) {
            Object v = row.get(k);
            if (v != null && !String.valueOf(v).isBlank()) return v;
        }
        return null;
    }
}
