package com.chinacreator.gzcm.engine.data;

import java.util.List;
import java.util.Map;

/**
 * UDF 服务 — 用户自定义函数管理。
 *
 * @author ECOS Pipeline 2.0 Team
 */
public interface UdfService {

    /** 注册 UDF */
    Map<String, Object> register(Map<String, Object> body);

    /** 更新 UDF */
    Map<String, Object> update(String id, Map<String, Object> body);

    /** 删除 UDF */
    void delete(String id);

    /** 获取 UDF 详情 */
    Map<String, Object> getById(String id);

    /** 列出 UDF (分页) */
    Map<String, Object> list(int page, int pageSize, String category, String language);

    /** 测试 UDF */
    Map<String, Object> test(String id, Map<String, Object> params);

    /** SQL → UDF 转换 */
    Map<String, Object> convertSqlToUdf(String sql, String language);
}
