package com.chinacreator.gzcm.sysman.controller;

import com.chinacreator.gzcm.common.annotation.RequirePermission;
import com.chinacreator.gzcm.common.base.ApiResponse;
import com.chinacreator.gzcm.sysman.dict.entity.SysDict;
import com.chinacreator.gzcm.sysman.dict.service.IDictService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.*;

@RestController
@RequestMapping({"/api/v1/system/dict", "/api/system/dict"})
public class DictController {

    private static final Logger log = LoggerFactory.getLogger(DictController.class);

    @Autowired
    private IDictService dictService;

    /**
     * GET /api/v1/system/dict → 列出所有字典类型（含条目）
     */
    @GetMapping
    public ApiResponse<List<Map<String, Object>>> listDictTypes() {
        try {
            List<String> types = dictService.listDictTypes();
            List<Map<String, Object>> result = new ArrayList<>();
            for (String type : types) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("dictType", type);
                List<SysDict> items = dictService.getDictItems(type);
                entry.put("itemCount", items.size());
                entry.put("items", items);
                result.add(entry);
            }
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("查询字典类型失败", e);
            return ApiResponse.internalError("查询失败: " + e.getMessage());
        }
    }

    /**
     * GET /api/v1/system/dict/types → 字典类型汇总（不含条目详情）
     */
    @GetMapping("/types")
    public ApiResponse<List<Map<String, Object>>> listTypes() {
        try {
            List<String> types = dictService.listDictTypes();
            List<Map<String, Object>> result = new ArrayList<>();
            for (String type : types) {
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("dictType", type);
                List<SysDict> items = dictService.getDictItems(type);
                entry.put("itemCount", items.size());
                entry.put("subsystem", items.isEmpty() ? null : items.get(0).getSubsystem());
                result.add(entry);
            }
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("查询字典类型失败", e);
            return ApiResponse.internalError("查询失败: " + e.getMessage());
        }
    }

    /**
     * GET /api/v1/system/dict/subsystems → 按子系统(G1-G5)分组
     */
    @GetMapping("/subsystems")
    public ApiResponse<Map<String, List<Map<String, Object>>>> listBySubsystem() {
        try {
            List<String> types = dictService.listDictTypes();
            Map<String, List<Map<String, Object>>> grouped = new LinkedHashMap<>();
            for (String type : types) {
                List<SysDict> items = dictService.getDictItems(type);
                String sub = items.isEmpty() ? "G0" : 
                    (items.get(0).getSubsystem() != null ? items.get(0).getSubsystem() : "G0");
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("dictType", type);
                entry.put("itemCount", items.size());
                entry.put("items", items);
                grouped.computeIfAbsent(sub, k -> new ArrayList<>()).add(entry);
            }
            return ApiResponse.success(grouped);
        } catch (Exception e) {
            log.error("按子系统查询字典失败", e);
            return ApiResponse.internalError("查询失败: " + e.getMessage());
        }
    }

    /**
     * GET /api/v1/system/dict/{type} → 获取指定类型的字典项
     */
    @GetMapping("/{type}")
    public ApiResponse<List<SysDict>> getDictItems(@PathVariable String type) {
        try {
            List<SysDict> items = dictService.getDictItems(type);
            return ApiResponse.success(items);
        } catch (Exception e) {
            log.error("查询字典项失败: type={}", type, e);
            return ApiResponse.internalError("查询失败: " + e.getMessage());
        }
    }

    /**
     * GET /api/v1/system/dict/{type}/{code} → 获取单个字典项
     */
    @GetMapping("/{type}/{code}")
    public ApiResponse<SysDict> getDictItem(@PathVariable String type, @PathVariable String code) {
        try {
            SysDict item = dictService.getDictItem(type, code);
            if (item == null) {
                return ApiResponse.notFound("字典项不存在: " + type + "/" + code);
            }
            return ApiResponse.success(item);
        } catch (Exception e) {
            log.error("查询字典项失败: type={}, code={}", type, code, e);
            return ApiResponse.internalError("查询失败: " + e.getMessage());
        }
    }

    /**
     * POST /api/v1/system/dict → 创建字典项
     */
    @PostMapping
    @RequirePermission(permission = "system:dict:manage")
    public ApiResponse<SysDict> createDictItem(@RequestBody SysDict dict) {
        try {
            if (dict.getDictType() == null || dict.getDictType().isEmpty()) {
                return ApiResponse.badRequest("dictType 不能为空");
            }
            if (dict.getDictCode() == null || dict.getDictCode().isEmpty()) {
                return ApiResponse.badRequest("dictCode 不能为空");
            }
            if (dict.getDictLabel() == null || dict.getDictLabel().isEmpty()) {
                return ApiResponse.badRequest("dictLabel 不能为空");
            }

            SysDict existing = dictService.getDictItem(dict.getDictType(), dict.getDictCode());
            if (existing != null) {
                return ApiResponse.badRequest("字典项已存在: " + dict.getDictType() + "/" + dict.getDictCode());
            }

            SysDict created = dictService.createDictItem(dict);
            return ApiResponse.success(created);
        } catch (Exception e) {
            log.error("创建字典项失败", e);
            return ApiResponse.internalError("创建失败: " + e.getMessage());
        }
    }

    /**
     * PUT /api/v1/system/dict/{type}/{code} → 更新字典项
     */
    @PutMapping("/{type}/{code}")
    @RequirePermission(permission = "system:dict:manage")
    public ApiResponse<SysDict> updateDictItem(@PathVariable String type, @PathVariable String code,
                                                @RequestBody SysDict dict) {
        try {
            SysDict existing = dictService.getDictItem(type, code);
            if (existing == null) {
                return ApiResponse.notFound("字典项不存在: " + type + "/" + code);
            }

            SysDict updated = dictService.updateDictItem(type, code, dict);
            return ApiResponse.success(updated);
        } catch (Exception e) {
            log.error("更新字典项失败: type={}, code={}", type, code, e);
            return ApiResponse.internalError("更新失败: " + e.getMessage());
        }
    }

    /**
     * DELETE /api/v1/system/dict/{type}/{code} → 软删除
     */
    @DeleteMapping("/{type}/{code}")
    @RequirePermission(permission = "system:dict:manage")
    public ApiResponse<Map<String, Object>> deleteDictItem(@PathVariable String type, @PathVariable String code) {
        try {
            SysDict existing = dictService.getDictItem(type, code);
            if (existing == null) {
                return ApiResponse.notFound("字典项不存在: " + type + "/" + code);
            }

            dictService.deleteDictItem(type, code);
            return ApiResponse.success(Map.of("success", true, "dictType", type, "dictCode", code));
        } catch (Exception e) {
            log.error("删除字典项失败: type={}, code={}", type, code, e);
            return ApiResponse.internalError("删除失败: " + e.getMessage());
        }
    }

    /**
     * POST /api/v1/system/dict/refresh → 刷新缓存
     */
    @PostMapping("/refresh")
    @RequirePermission(permission = "system:dict:manage")
    public ApiResponse<Map<String, Object>> refreshCache() {
        try {
            Map<String, Object> result = dictService.refreshCache();
            return ApiResponse.success(result);
        } catch (Exception e) {
            log.error("刷新字典缓存失败", e);
            return ApiResponse.internalError("刷新失败: " + e.getMessage());
        }
    }

    /**
     * GET /api/v1/system/dict/{type}/usage → 字典审计：哪些模块用到了该字典
     */
    @GetMapping("/{type}/usage")
    public ApiResponse<Map<String, Object>> getDictUsage(@PathVariable String type) {
        try {
            Map<String, Object> usage = dictService.getDictUsage(type);
            return ApiResponse.success(usage);
        } catch (Exception e) {
            log.error("查询字典审计失败: type={}", type, e);
            return ApiResponse.internalError("查询失败: " + e.getMessage());
        }
    }
}
