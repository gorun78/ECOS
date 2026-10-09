package com.chinacreator.gzcm.engine.ai.dao;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.stereotype.Repository;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 数据访问收口（详细设计-06 F06-11 / F06-18；架构铁律 §2.5 #1）：
 * AI 工具声明式注册表 {@code ecos_ai.ecos_ai_tool_registry}（迁移脚本 V200 新建合规表）的只读落点。
 *
 * <p><b>只读</b>是设计约束而非疏忽：注册表的写面属管理端配置流（分册 06 §6.2 的 E 章归口），
 * 引擎侧写入即"运行期自注册工具"，等于绕过 {@code ToolExecutorService} 裁决咽喉的能力授予面。
 * 因此本 DAO 只提供读方法，禁增 insert/update（若将来确需，须由架构官签发并同步扩
 * {@code DataAccessArchTest.BANNED_PACKAGES}）。</p>
 *
 * <p>表未实跑（V200 仅落脚本，§14.4 未授权实跑）⇒ {@link #tableReady()} 为 false，
 * 由 {@code ScenarioToolRegistry} 回落到同源代码声明，禁抛错掩蔽也禁静默当空表。</p>
 */
@Repository
public class AiToolRegistryDao extends JdbcTemplateDaoSupport {

    private static final String TABLE = "ecos_ai.ecos_ai_tool_registry";

    /** V200 列清单（读取顺序固定，禁 SELECT *：列序变更会静默错位）。 */
    private static final String COLUMNS = "tool_key, name, description, operation, args_schema_json, "
            + "backend_kind, backend_endpoint, permission_hint, resource_scope_projection, timeout_ms";

    /** 注册表是否就位（读侧必须先探活，才能区分"表空"与"表不在"）。 */
    public boolean tableReady() {
        Integer exists = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM information_schema.tables "
                        + "WHERE table_schema = 'ecos_ai' AND table_name = 'ecos_ai_tool_registry'",
                Integer.class);
        return exists != null && exists > 0;
    }

    /**
     * ACTIVE 且在用（{@code is_deleted = 0}）的注册条目。
     *
     * <p>SQL 全部 schema 限定 + 参数化（DR/IR 红线）；{@code status} 作为绑定参数而非字面量拼接。</p>
     */
    public List<Map<String, Object>> listByStatus(String status) {
        return jdbcTemplate.queryForList(
                "SELECT " + COLUMNS + " FROM " + TABLE + " WHERE status = ? AND is_deleted = 0 ORDER BY tool_key",
                status);
    }

    /** 单个 tool_key 的 ACTIVE 条目（不存在返回空 Map）。 */
    public Map<String, Object> findActive(String toolKey) {
        if (toolKey == null || toolKey.isBlank()) return Map.of();
        List<Map<String, Object>> rows = jdbcTemplate.queryForList(
                "SELECT " + COLUMNS + " FROM " + TABLE
                        + " WHERE tool_key = ? AND status = ? AND is_deleted = 0",
                toolKey, "ACTIVE");
        return rows.isEmpty() ? Map.of() : new LinkedHashMap<>(rows.get(0));
    }
}
