package com.chinacreator.gzcm.engine.data.controller;

import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.engine.data.model.DataLayer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping("/api/v1/engine/data/layers")
public class DataLayerController {

    private final JdbcTemplate jdbc;

    public DataLayerController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping
    public ApiResponse<Map<String, Object>> getLayerSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        long total = 0;
        for (DataLayer dl : DataLayer.values()) {
            Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM td_data_resource WHERE layer = ?", Integer.class, dl.name());
            int c = count != null ? count : 0;
            summary.put(dl.name(), c);
            total += c;
        }
        summary.put("total", total);
        return ApiResponse.success(summary);
    }

    @GetMapping("/{layer}")
    public ApiResponse<Map<String, Object>> getResourcesByLayer(@PathVariable String layer) {
        try {
            DataLayer.valueOf(layer);
        } catch (IllegalArgumentException e) {
            return ApiResponse.badRequest("Invalid layer: " + layer);
        }
        List<Map<String, Object>> resources = jdbc.queryForList(
            "SELECT * FROM td_data_resource WHERE layer = ?", layer);
        return ApiResponse.success(Map.of("layer", layer, "resources", resources, "total", resources.size()));
    }
}
