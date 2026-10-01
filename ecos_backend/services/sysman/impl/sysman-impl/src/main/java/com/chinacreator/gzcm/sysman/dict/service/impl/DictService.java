package com.chinacreator.gzcm.sysman.dict.service.impl;

import com.chinacreator.gzcm.sysman.dao.DictDao;
import com.chinacreator.gzcm.sysman.dict.entity.SysDict;
import com.chinacreator.gzcm.sysman.dict.service.IDictService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;

import java.util.*;

/**
 * 数据字典服务实现
 * 使用 JdbcTemplate 进行数据库访问，Caffeine 作为内存缓存
 */
@Service
public class DictService implements IDictService {

    private static final Logger log = LoggerFactory.getLogger(DictService.class);

    private final DictDao dao;

    /**
     * 内存缓存: dict_type -> List<SysDict>（Caffeine，字典常驻不设过期，靠 refreshCache 主动失效）
     */
    private final Cache<String, List<SysDict>> cache = Caffeine.newBuilder()
        .maximumSize(100)
        .build();

    /**
     * 缓存所有字典类型的集合（用于快速获取类型列表）
     */
    private volatile Set<String> cachedTypes = Collections.emptySet();

    public DictService(DictDao dao) {
        this.dao = dao;
        // H8-T1: sys_dict.subsystem 列补齐 ALTER 收编至 db/migration（V162），构造函数不再执行 DDL。
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
        List<SysDict> items = cache.getIfPresent(dictType);
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
            Integer maxOrder = dao.maxSortOrder(dict.getDictType());
            dict.setSortOrder(maxOrder != null ? maxOrder + 1 : 1);
        }

        dao.insert(dict);

        // 刷新该类型的缓存
        refreshDictType(dict.getDictType());
        return getDictItem(dict.getDictType(), dict.getDictCode());
    }

    @Override
    public SysDict updateDictItem(String dictType, String dictCode, SysDict dict) {
        dao.update(dict, dictType, dictCode);

        // 刷新该类型的缓存
        refreshDictType(dictType);
        return getDictItem(dictType, dictCode);
    }

    @Override
    public void deleteDictItem(String dictType, String dictCode) {
        dao.deactivate(dictType, dictCode);

        // 刷新该类型的缓存
        refreshDictType(dictType);
    }

    @Override
    public Map<String, Object> refreshCache() {
        long start = System.currentTimeMillis();
        cache.invalidateAll();

        List<SysDict> allItems = dao.listAllActive();

        // 按 dict_type 分组
        Map<String, List<SysDict>> grouped = new LinkedHashMap<>();
        for (SysDict item : allItems) {
            grouped.computeIfAbsent(item.getDictType(), k -> new ArrayList<>()).add(item);
        }
        for (Map.Entry<String, List<SysDict>> entry : grouped.entrySet()) {
            cache.put(entry.getKey(), entry.getValue());
        }
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
            cache.invalidate(dictType);
        }
        updateCachedTypes();
    }

    /**
     * 从数据库加载指定类型的字典项
     */
    private List<SysDict> loadFromDb(String dictType) {
        return dao.listByType(dictType);
    }

    /**
     * 更新缓存的类型集合
     */
    private void updateCachedTypes() {
        cachedTypes = new LinkedHashSet<>(cache.asMap().keySet());
    }
}
