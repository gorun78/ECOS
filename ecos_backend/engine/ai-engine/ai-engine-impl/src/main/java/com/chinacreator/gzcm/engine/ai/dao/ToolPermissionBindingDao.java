package com.chinacreator.gzcm.engine.ai.dao;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

/**
 * 工具-角色权限绑定声明（详细设计-06 F06-02 / V201 {@code ecos_ai.ecos_ai_tool_permission_binding}）。
 *
 * <p>本表只提供两件事：<b>载荷必需主体属性的声明</b>（用于校验 subject 段是否齐备）与
 * <b>期望 obligations 形态</b>（验收对账）。裁决权威始终在 security-engine，
 * 本表内容不参与 ALLOW/DENY 判定（R-18 ②：禁策略副本）。</p>
 */
@Repository
public class ToolPermissionBindingDao extends JdbcTemplateDaoSupport {

    /** 取某工具的全部绑定声明行（未逻辑删除）。 */
    public List<Map<String, Object>> findByTool(String toolKey) {
        return jdbcTemplate.queryForList(
                "SELECT tool_key, role_key, required_attributes, obligation_expectation "
                + "FROM ecos_ai.ecos_ai_tool_permission_binding "
                + "WHERE tool_key = ? AND is_deleted = 0 ORDER BY role_key",
                toolKey);
    }

    /** 声明的必需主体属性（逗号分隔文本 → 列表）；无声明返回空列表。 */
    public List<String> requiredAttributes(String toolKey) {
        List<String> attrs = new ArrayList<>();
        for (Map<String, Object> row : findByTool(toolKey)) {
            Object raw = row.get("required_attributes");
            if (raw == null) continue;
            for (String part : String.valueOf(raw).split(",")) {
                String s = part.trim();
                if (!s.isEmpty() && !attrs.contains(s)) attrs.add(s);
            }
        }
        return attrs;
    }

    /** 该工具是否在权限声明表中登记（供 F06-11 场景工具注册完整性校验）。 */
    public boolean isDeclared(String toolKey) {
        return queryInt(
                "SELECT COUNT(1) FROM ecos_ai.ecos_ai_tool_permission_binding "
                + "WHERE tool_key = ? AND is_deleted = 0",
                toolKey) > 0;
    }
}
