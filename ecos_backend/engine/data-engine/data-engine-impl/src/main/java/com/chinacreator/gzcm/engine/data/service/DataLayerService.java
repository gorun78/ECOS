package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.common.data.model.DataLayer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DataLayerService {

    private final JdbcTemplate jdbc;

    public DataLayerService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Object> getLayerSummary() {
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
        return summary;
    }

    public List<Map<String, Object>> getResourcesByLayer(String layer) {
        return jdbc.queryForList(
            "SELECT * FROM td_data_resource WHERE layer = ?", layer);
    }
}
