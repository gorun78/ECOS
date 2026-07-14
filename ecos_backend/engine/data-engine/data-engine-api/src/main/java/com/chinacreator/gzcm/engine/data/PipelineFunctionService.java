package com.chinacreator.gzcm.engine.data;

import java.util.List;
import java.util.Map;

/**
 * Pipeline 函数注册表服务 — 管理 PB 函数目录（120+ 内置函数）。
 *
 * @author ECOS Pipeline 2.0 Team
 */
public interface PipelineFunctionService {

    /**
     * 列出所有函数（分页）。
     * @param page 页码
     * @param pageSize 每页大小
     * @param category 可选分类过滤
     * @return {total, page, pageSize, list}
     */
    Map<String, Object> listFunctions(int page, int pageSize, String category);

    /**
     * 按分类列出所有函数（不分页）。
     * @param category 分类名 string/numeric/date_time/conditional/array/window/casting
     */
    List<Map<String, Object>> listByCategory(String category);

    /**
     * 搜索函数。
     * @param query 搜索关键词（匹配 name/description）
     */
    List<Map<String, Object>> search(String query);

    /**
     * 获取函数详情。
     * @param id 函数 ID
     */
    Map<String, Object> getById(String id);

    /**
     * 获取所有分类统计。
     */
    List<Map<String, Object>> getCategories();

    /**
     * 创建自定义函数。
     */
    Map<String, Object> create(Map<String, Object> body);

    /**
     * 更新函数。
     */
    Map<String, Object> update(String id, Map<String, Object> body);

    /**
     * 删除函数（仅内置=false 的可删除）。
     */
    void delete(String id);
}
