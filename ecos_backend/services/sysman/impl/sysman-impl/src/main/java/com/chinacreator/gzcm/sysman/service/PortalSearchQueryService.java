package com.chinacreator.gzcm.sysman.service;

import com.chinacreator.gzcm.common.context.TenantContextHolder;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;

/**
 * PortalSearchQueryService — Portal 全局搜索只读查询层（PMO-39 批次2 T4）。
 *
 * <p>PMO-锚点链：PMO-39 批次2 T4（来源：检查报告 §6 #4 | 消费方：T4 前端点）。
 * <p>从 PortalSearchController 下沉的 JdbcTemplate 访问（符合"Controller 禁止
 * 直接用 JdbcTemplate — 必须走 Service 层"硬规则）。实体级检索（非全文），
 * 每类检索各自 try-catch 降级为空列表（表不存在时搜索不抛错）。</p>
 */
@Service
public class PortalSearchQueryService {

    /** 候选 agent 表（历史命名不一，按序探测，首个存在者生效） */
    public static final List<String> AGENT_TABLE_CANDIDATES =
            List.of("ecos_agent", "agent_mesh", "t_agent_mesh", "agent");

    private final JdbcTemplate jdbc;

    public PortalSearchQueryService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** agent 只读检索（实体级，关键词过滤；表不存在时降级为空列表） */
    public List<Map<String, Object>> searchAgents(String keyword) {
        String like = "%" + keyword + "%";
        for (String table : AGENT_TABLE_CANDIDATES) {
            try {
                return jdbc.queryForList(
                        "SELECT id, name, status FROM " + table
                                + " WHERE name LIKE ? OR system_prompt LIKE ? LIMIT 20",
                        like, like);
            } catch (Exception e) {
                // 表不存在/无权限 → 试下一个候选表
            }
        }
        return List.of();
    }

    /** workflow 只读检索 */
    public List<Map<String, Object>> searchWorkflows(String keyword) {
        try {
            return jdbc.queryForList("""
                    SELECT id, name, description, status
                    FROM ecos_workflow_v2
                    WHERE name LIKE ? OR description LIKE ?
                    LIMIT 20
                    """, "%" + keyword + "%", "%" + keyword + "%");
        } catch (Exception e) {
            return List.of();
        }
    }

    /**
     * glossary 术语表只读检索。
     *
     * <p>PMO-30 P1-1 多租户：{@code ecos_glossary_term} 走「仓库内手工租户」约定
     * （{@code TenantAwareJdbcTemplate} 对 INSERT 追加 WHERE 属语法非法，该表已移出
     * 自动重写名单），故此处显式加租户条件：本租户行 + 共享行（{@code tenant_id IS NULL}）。
     */
    public List<Map<String, Object>> searchGlossary(String keyword) {
        String like = "%" + keyword + "%";
        String tenantId = TenantContextHolder.getTenantId();
        try {
            if (tenantId == null || tenantId.isBlank()) {
                return jdbc.queryForList("""
                        SELECT code AS id, name AS term, definition
                        FROM ecos_glossary_term
                        WHERE name LIKE ? OR definition LIKE ?
                        LIMIT 20
                        """, like, like);
            }
            return jdbc.queryForList("""
                    SELECT code AS id, name AS term, definition
                    FROM ecos_glossary_term
                    WHERE (name LIKE ? OR definition LIKE ?)
                      AND (tenant_id = ? OR tenant_id IS NULL)
                    LIMIT 20
                    """, like, like, tenantId);
        } catch (Exception e) {
            return List.of();
        }
    }

    /** scenario 场景只读检索 */
    public List<Map<String, Object>> searchScenarios(String keyword) {
        try {
            return jdbc.queryForList("""
                    SELECT id, name, description
                    FROM ecos_world_scenarios
                    WHERE name LIKE ? OR description LIKE ?
                    LIMIT 20
                    """, "%" + keyword + "%", "%" + keyword + "%");
        } catch (Exception e) {
            return List.of();
        }
    }
}
