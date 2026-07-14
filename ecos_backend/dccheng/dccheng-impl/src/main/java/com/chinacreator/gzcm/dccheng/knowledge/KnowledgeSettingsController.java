package com.chinacreator.gzcm.dccheng.knowledge;

import com.chinacreator.gzcm.common.base.ApiResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/knowledge/settings")
public class KnowledgeSettingsController {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeSettingsController.class);

    private static final Map<String, String> DEFAULTS = new LinkedHashMap<>();
    static {
        DEFAULTS.put("knowledge.graph.defaultDomain", "default");
        DEFAULTS.put("knowledge.graph.maxNeighborDegree", "3");
        DEFAULTS.put("knowledge.index.autoSyncEnabled", "true");
        DEFAULTS.put("knowledge.index.batchSize", "500");
        DEFAULTS.put("knowledge.rag.topK", "5");
        DEFAULTS.put("knowledge.rag.similarityThreshold", "0.7");
        DEFAULTS.put("knowledge.rag.model", "text-embedding-3-small");
        DEFAULTS.put("knowledge.lineage.maxDepth", "10");
    }

    @GetMapping
    public ApiResponse<List<Map<String, Object>>> getAll() {
        List<Map<String, Object>> list = new ArrayList<>();
        DEFAULTS.forEach((k, v) -> {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("configKey", k);
            item.put("configValue", v);
            item.put("group", "knowledge");
            list.add(item);
        });
        return ApiResponse.success(list);
    }

    @PutMapping
    public ApiResponse<Map<String, Object>> batchUpdate(@RequestBody List<Map<String, String>> updates) {
        int count = updates != null ? updates.size() : 0;
        log.info("Knowledge settings batch update (placeholder): {} items", count);
        return ApiResponse.success(Map.of("updated", count));
    }
}
