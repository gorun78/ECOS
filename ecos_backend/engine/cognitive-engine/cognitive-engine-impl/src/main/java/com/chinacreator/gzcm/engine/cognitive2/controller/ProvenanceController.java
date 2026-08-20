package com.chinacreator.gzcm.engine.cognitive2.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.cognitive2.DecisionService;
import com.chinacreator.gzcm.engine.cognitive2.model.ProvenanceEntry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

/**
 * 溯源查询 REST API — 前缀 /api/v1/cognitive/provenance
 */
@RestController
@RequestMapping("/api/v1/cognitive/provenance")
public class ProvenanceController {

    private static final Logger log = LoggerFactory.getLogger(ProvenanceController.class);

    @Autowired
    private DecisionService decisionService;

    /** GET /?entityType=&entityId= — 查询溯源记录 */
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> query(
            @RequestParam String entityType,
            @RequestParam String entityId) {
        try {
            List<ProvenanceEntry> entries = decisionService.queryProvenance(entityType, entityId);
            List<Map<String, Object>> data = new ArrayList<>();
            for (ProvenanceEntry e : entries) {
                Map<String, Object> m = new LinkedHashMap<>();
                m.put("id", e.getId());
                m.put("entityType", e.getEntityType());
                m.put("entityId", e.getEntityId());
                m.put("sourceType", e.getSourceType());
                m.put("sourceRef", e.getSourceRef());
                m.put("agent", e.getAgent());
                m.put("activity", e.getActivity());
                m.put("timestamp", e.getTimestamp());
                data.add(m);
            }
            return ApiResponse.success(data);
        } catch (Exception e) {
            log.error("Failed to query provenance", e);
            return ApiResponse.internalError("Failed to query provenance: " + e.getMessage());
        }
    }
}
