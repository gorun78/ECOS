package com.chinacreator.gzcm.engine.data.service;

import com.chinacreator.gzcm.engine.data.model.DataLayer;
import com.chinacreator.gzcm.engine.data.repository.DataResourceRepository;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据分层服务。
 *
 * <p>F02-05（五层载体登记与写入通道）：{@link #getLayerSummary()} 响应体**只增不改**——
 * 保留原有「层名 → 载体数」计数，新增：
 * <ul>
 *   <li>{@code declared}（Map&lt;层名,Boolean&gt;）：该层是否已登记**至少一个**载体
 *       （{@code carrier_ref} 非空）。0 载体层必须回 {@code declared:false}
 *       （D-9 / W51：SEMANTIC、APPLICATION 当前 0 载体），禁止前端呈现为"有数据"。</li>
 *   <li>{@code total}（保留，既有语义不变）。</li>
 * </ul>
 */
@Service
public class DataLayerService {

    private final JdbcTemplate jdbc;

    /** F02-05：载体登记计数走 MyBatis Mapper（DataResourceRepository），禁用 Service 直连 JDBC。 */
    private final DataResourceRepository dataResourceRepository;

    public DataLayerService(JdbcTemplate jdbc, DataResourceRepository dataResourceRepository) {
        this.jdbc = jdbc;
        this.dataResourceRepository = dataResourceRepository;
    }

    public Map<String, Object> getLayerSummary() {
        Map<String, Object> summary = new LinkedHashMap<>();
        Map<String, Boolean> declared = new LinkedHashMap<>();
        long total = 0;
        for (DataLayer dl : DataLayer.values()) {
            Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM td_data_resource WHERE layer = ?", Integer.class, dl.name());
            int c = count != null ? count : 0;
            summary.put(dl.name(), c);
            total += c;

            // F02-05 / D-9：载体存在性 —— carrier_ref 非空才算"登记了载体"。
            int carriers = dataResourceRepository.countRegisteredCarriersByLayer(dl.name());
            declared.put(dl.name(), carriers > 0);
        }
        summary.put("total", total);
        // 只增不改：追加 declared 子映射（既有客户端读层名计数不受影响）。
        summary.put("declared", declared);
        return summary;
    }

    public List<Map<String, Object>> getResourcesByLayer(String layer) {
        return jdbc.queryForList(
            "SELECT * FROM td_data_resource WHERE layer = ?", layer);
    }
}
