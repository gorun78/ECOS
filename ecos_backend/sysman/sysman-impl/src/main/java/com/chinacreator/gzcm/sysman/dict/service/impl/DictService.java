package com.chinacreator.gzcm.sysman.dict.service.impl;

import com.chinacreator.gzcm.sysman.dict.entity.SysDict;
import com.chinacreator.gzcm.sysman.dict.service.IDictService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Service;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 数据字典服务实现
 * 使用 JdbcTemplate 进行数据库访问，ConcurrentHashMap 作为内存缓存
 */
@Service
public class DictService implements IDictService {

    private static final Logger log = LoggerFactory.getLogger(DictService.class);

    private final JdbcTemplate jdbc;

    /**
     * 内存缓存: dict_type -> List<SysDict>
     */
    private final ConcurrentHashMap<String, List<SysDict>> cache = new ConcurrentHashMap<>();

    /**
     * 缓存所有字典类型的集合（用于快速获取类型列表）
     */
    private volatile Set<String> cachedTypes = Collections.emptySet();

    private static final RowMapper<SysDict> ROW_MAPPER = new SysDictRowMapper();

    public DictService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        // 启动时初始化缓存
        refreshCache();
    }

    @Override
    public List<String> listDictTypes() {
        if (cachedTypes.isEmpty()) {
            refreshCache();
        }
        return new ArrayList<>(cachedTypes);
    }

    @Override
    public List<SysDict> getDictItems(String dictType) {
        List<SysDict> items = cache.get(dictType);
        if (items == null) {
            // 缓存未命中，从数据库加载
            items = loadFromDb(dictType);
            if (items != null && !items.isEmpty()) {
                cache.put(dictType, items);
                updateCachedTypes();
            }
        }
        return items != null ? items : Collections.emptyList();
    }

    @Override
    public SysDict getDictItem(String dictType, String dictCode) {
        List<SysDict> items = getDictItems(dictType);
        for (SysDict item : items) {
            if (item.getDictCode().equals(dictCode) && "active".equals(item.getStatus())) {
                return item;
            }
        }
        return null;
    }

    @Override
    public SysDict createDictItem(SysDict dict) {
        if (dict.getId() == null || dict.getId().isEmpty()) {
            dict.setId(UUID.randomUUID().toString().replace("-", ""));
        }
        if (dict.getSortOrder() == 0) {
            // 自动计算 sort_order
            Integer maxOrder = jdbc.queryForObject(
                "SELECT COALESCE(MAX(sort_order), 0) FROM sys_dict WHERE dict_type = ?",
                Integer.class, dict.getDictType());
            dict.setSortOrder(maxOrder != null ? maxOrder + 1 : 1);
        }

        jdbc.update(
            "INSERT INTO sys_dict (id, dict_type, dict_code, dict_label, dict_label_en, sort_order, status, parent_code, ext_value, subsystem, created_at, updated_at) " +
            "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW())",
            dict.getId(), dict.getDictType(), dict.getDictCode(), dict.getDictLabel(),
            dict.getDictLabelEn(), dict.getSortOrder(),
            dict.getStatus() != null ? dict.getStatus() : "active",
            dict.getParentCode(), dict.getExtValue(),
            dict.getSubsystem());

        // 刷新该类型的缓存
        refreshDictType(dict.getDictType());
        return getDictItem(dict.getDictType(), dict.getDictCode());
    }

    @Override
    public SysDict updateDictItem(String dictType, String dictCode, SysDict dict) {
        jdbc.update(
            "UPDATE sys_dict SET dict_label = ?, dict_label_en = ?, sort_order = ?, parent_code = ?, ext_value = ?, subsystem = ?, updated_at = NOW() " +
            "WHERE dict_type = ? AND dict_code = ?",
            dict.getDictLabel(), dict.getDictLabelEn(), dict.getSortOrder(),
            dict.getParentCode(), dict.getExtValue(), dict.getSubsystem(),
            dictType, dictCode);

        // 刷新该类型的缓存
        refreshDictType(dictType);
        return getDictItem(dictType, dictCode);
    }

    @Override
    public void deleteDictItem(String dictType, String dictCode) {
        jdbc.update(
            "UPDATE sys_dict SET status = 'inactive', updated_at = NOW() WHERE dict_type = ? AND dict_code = ?",
            dictType, dictCode);

        // 刷新该类型的缓存
        refreshDictType(dictType);
    }

    @Override
    public Map<String, Object> refreshCache() {
        long start = System.currentTimeMillis();
        cache.clear();

        List<SysDict> allItems = jdbc.query(
            "SELECT id, dict_type, dict_code, dict_label, dict_label_en, sort_order, status, parent_code, ext_value, subsystem, created_at, updated_at " +
            "FROM sys_dict WHERE status = 'active' ORDER BY dict_type, sort_order",
            ROW_MAPPER);

        // 按 dict_type 分组
        Map<String, List<SysDict>> grouped = new LinkedHashMap<>();
        for (SysDict item : allItems) {
            grouped.computeIfAbsent(item.getDictType(), k -> new ArrayList<>()).add(item);
        }
        cache.putAll(grouped);
        cachedTypes = new LinkedHashSet<>(grouped.keySet());

        long elapsed = System.currentTimeMillis() - start;
        log.info("Dict cache refreshed: {} types, {} items in {}ms",
            cachedTypes.size(), allItems.size(), elapsed);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("types", cachedTypes.size());
        result.put("items", allItems.size());
        result.put("elapsedMs", elapsed);
        return result;
    }

    @Override
    public Map<String, Object> getDictUsage(String dictType) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("dictType", dictType);

        // 查询该字典类型的所有条目
        List<SysDict> items = getDictItems(dictType);
        result.put("itemCount", items.size());

        // 审计：扫描前端模块对 useDict 的引用
        List<Map<String, String>> modules = new ArrayList<>();
        Map<String, String> usageMap = new LinkedHashMap<>();
        usageMap.put("agent_type", "G4-AgentStudio, G4-AgentMesh");
        usageMap.put("asset_status", "G3-MarketplaceBrowser");
        usageMap.put("audit_mode", "G5-SecurityConfigPanel");
        usageMap.put("catalog_status", "G1-DataCatalog");
        usageMap.put("clearance_level", "G5-SecurityConfigPanel");
        usageMap.put("contract_status", "G5-ProjectManager");
        usageMap.put("data_source_type", "G1-DataSourceManager");
        usageMap.put("datasource_status", "G1-DataSourceManager");
        usageMap.put("datasource_type", "G1-DataSourceManager");
        usageMap.put("dq_issue_status", "G2-DataQualityDashboard");
        usageMap.put("dq_rule_type", "G2-DataQualityDashboard");
        usageMap.put("glossary_status", "G2-GlossaryManager");
        usageMap.put("marketplace_category", "G3-MarketplaceBrowser");
        usageMap.put("object_status", "G3-ObjectExplorer");
        usageMap.put("pipeline_status", "G3-PipelineBuilder");
        usageMap.put("project_status", "G5-ProjectManager");
        usageMap.put("resource_type", "G1-DataCatalog");
        usageMap.put("task_type", "G3-PipelineBuilder");

        String moduleList = usageMap.get(dictType);
        if (moduleList != null) {
            for (String m : moduleList.split(", ")) {
                String[] parts = m.split("-", 2);
                Map<String, String> entry = new LinkedHashMap<>();
                entry.put("subsystem", parts[0]);
                entry.put("module", parts.length > 1 ? parts[1] : parts[0]);
                modules.add(entry);
            }
        } else {
            // 从 subsystem 字段推导
            String sub = items.isEmpty() ? null : items.get(0).getSubsystem();
            if (sub != null) {
                Map<String, String> entry = new LinkedHashMap<>();
                entry.put("subsystem", sub);
                entry.put("module", dictType);
                modules.add(entry);
            }
        }

        result.put("usedByModules", modules);
        result.put("usedByCount", modules.size());
        return result;
    }
    
    /**
     * 刷新指定类型的缓存
     */
    private void refreshDictType(String dictType) {
        List<SysDict> items = loadFromDb(dictType);
        if (items != null && !items.isEmpty()) {
            cache.put(dictType, items);
        } else {
            cache.remove(dictType);
        }
        updateCachedTypes();
    }

    /**
     * 从数据库加载指定类型的字典项
     */
    private List<SysDict> loadFromDb(String dictType) {
        return jdbc.query(
            "SELECT id, dict_type, dict_code, dict_label, dict_label_en, sort_order, status, parent_code, ext_value, subsystem, created_at, updated_at " +
            "FROM sys_dict WHERE dict_type = ? AND status = 'active' ORDER BY sort_order",
            ROW_MAPPER, dictType);
    }

    /**
     * 更新缓存的类型集合
     */
    private void updateCachedTypes() {
        cachedTypes = new LinkedHashSet<>(cache.keySet());
    }

    /**
     * RowMapper 实现
     */
    private static class SysDictRowMapper implements RowMapper<SysDict> {
        @Override
        public SysDict mapRow(ResultSet rs, int rowNum) throws SQLException {
            SysDict d = new SysDict();
            d.setId(rs.getString("id"));
            d.setDictType(rs.getString("dict_type"));
            d.setDictCode(rs.getString("dict_code"));
            d.setDictLabel(rs.getString("dict_label"));
            d.setDictLabelEn(rs.getString("dict_label_en"));
            d.setSortOrder(rs.getInt("sort_order"));
            d.setStatus(rs.getString("status"));
            d.setParentCode(rs.getString("parent_code"));
            d.setExtValue(rs.getString("ext_value"));
            d.setSubsystem(rs.getString("subsystem"));

            Timestamp ca = rs.getTimestamp("created_at");
            if (ca != null) d.setCreatedAt(ca.toLocalDateTime());
            Timestamp ua = rs.getTimestamp("updated_at");
            if (ua != null) d.setUpdatedAt(ua.toLocalDateTime());

            return d;
        }
    }
}
