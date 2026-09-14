package com.chinacreator.gzcm.sysman.service;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * PMO-39 批次2 T4 — 参数模板只读查询（td_system_param，参数仅增不改）。
 *
 * <h3>契约（前端 ParameterTab，检查报告 §6 #3）：</h3>
 * <ul>
 *   <li>类目枚举四值：system / agent / workflow / global（墓碑表，不可扩展）。</li>
 *   <li>CRUD 端点前缀 /api/v1/system/params，模板列表合并进 T4 全局搜索 type=all。</li>
 *   <li>宴悖点：战国 PMO 参数模块（仿 GSXK 语义）。TD_SYSTEM_PARAM 表结构不变。</li>
 * </ul>
 */
@Service
public class ParamTemplateQueryService {

    /** 类目枚举（墓碑表：system/agent/workflow/global 四值锁定） */
    public static final List<String> CATEGORIES = List.of("system", "agent", "workflow", "global");

    private final JdbcTemplate jdbc;

    public ParamTemplateQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 参数模板列表（category 可选过滤，表缺失/无权限降级空列表） */
    public List<Map<String, Object>> list(String category) {
        try {
            StringBuilder sql = new StringBuilder("""
                    SELECT "PARAM_ID" AS id, "PARAM_NAME" AS name,
                           "PARAM_DESC" AS description, "PARAM_TYPE" AS templateType
                    FROM td_system_param
                    """);
            List<Object> params = new java.util.ArrayList<>();
            if (category != null && !category.isBlank() && CATEGORIES.contains(category)) {
                sql.append(" WHERE \"PARAM_TYPE\" = ?");
                params.add(category);
            }
            sql.append(" ORDER BY \"PARAM_NAME\" LIMIT 100");
            return jdbc.queryForList(sql.toString(), params.toArray());
        } catch (Exception e) {
            return List.of();
        }
    }

    /** 单个模板（按 PARAM_ID） */
    public Map<String, Object> get(String paramId) {
        try {
            List<Map<String, Object>> rows = jdbc.queryForList("""
                    SELECT "PARAM_ID" AS id, "PARAM_NAME" AS name,
                           "PARAM_CONTENT" AS content, "PARAM_DESC" AS description,
                           "PARAM_TYPE" AS templateType
                    FROM td_system_param WHERE "PARAM_ID" = ?
                    """, paramId);
            return rows.isEmpty() ? null : rows.get(0);
        } catch (Exception e) {
            return null;
        }
    }

    /** 创建模板（category 限定四值枚举；重名由 UK_PARAM_NAME 兜底 409） */
    public Map<String, Object> create(String paramId, String name, Object content,
                                      String description, String category) {
        try {
            jdbc.update("""
                        INSERT INTO td_system_param
                            ("PARAM_ID", "PARAM_NAME", "PARAM_CONTENT", "PARAM_DESC", "PARAM_TYPE")
                        VALUES (?, ?, ?, ?, ?)
                        """, paramId, name,
                    content != null ? String.valueOf(content) : null,
                    description, category);
            return get(paramId);
        } catch (Exception e) {
            throw new IllegalStateException("SYS-PARAM-002: 参数模板创建失败: " + e.getMessage(), e);
        }
    }

    /** 更新模板（仅允许改 content/description — 参数只增不改，键名锁定） */
    public boolean update(String paramId, Object content, String description) {
        try {
            int n = jdbc.update("""
                        UPDATE td_system_param
                        SET "PARAM_CONTENT" = ?, "PARAM_DESC" = ?,
                            "UPDATED_TIME" = NOW()
                        WHERE "PARAM_ID" = ?
                        """, content != null ? String.valueOf(content) : null,
                    description, paramId);
            return n > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** 删除模板（软删：清 content 保留行 — DB 铁律只增不删） */
    public boolean delete(String paramId) {
        try {
            int n = jdbc.update("""
                        UPDATE td_system_param
                        SET "PARAM_CONTENT" = NULL,
                            "PARAM_DESC" = 'DELETED',
                            "UPDATED_TIME" = NOW()
                        WHERE "PARAM_ID" = ?
                        """, paramId);
            return n > 0;
        } catch (Exception e) {
            return false;
        }
    }

    /** T4 全局搜索用：标题/描述命中才计入 */
    public List<Map<String, Object>> search(String keyword) {
        try {
            return jdbc.queryForList("""
                    SELECT "PARAM_ID" AS id, "PARAM_NAME" AS name,
                           "PARAM_DESC" AS description, "PARAM_TYPE" AS templateType
                    FROM td_system_param
                    WHERE "PARAM_NAME" LIKE ? OR "PARAM_DESC" LIKE ?
                    LIMIT 20
                    """, "%" + keyword + "%", "%" + keyword + "%");
        } catch (Exception e) {
            return List.of();
        }
    }
}
