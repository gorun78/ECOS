package com.chinacreator.gzcm.workspace.security;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * ABAC 查询过滤器 — 在 ObjectQL/ObjectRuntime 查询时自动做列裁剪和行过滤。
 *
 * <h3>工作原理</h3>
 * <ol>
 *   <li>从 SecurityContextHolder 获取当前用户的角色/权限</li>
 *   <li>查询 td_abac_policy 表，找到 subjectCondition 匹配当前用户角色的策略</li>
 *   <li>解析 environment_condition (JSON)，提取列黑名单和行过滤 SQL 片段</li>
 *   <li>对查询结果做列裁剪（Java 层后处理），对 SQL 做行过滤（WHERE 条件追加）</li>
 * </ol>
 *
 * <h3>ABAC 策略 environment_condition JSON 格式</h3>
 * <pre>{@code
 * {
 *   "hiddenColumns": ["bank_account", "salary"],
 *   "rowFilterSql": "department_id IN (SELECT dept_id FROM td_user_dept WHERE user_id = '${userId}')"
 * }
 * }</pre>
 *
 * <h3>subjectCondition 格式</h3>
 * 支持简单匹配：若用户的任意 GrantedAuthority 包含 subjectCondition，即视为匹配。
 * 例如 subjectCondition="project_manager" 会匹配 authority "ROLE_PROJECT_MANAGER" 或 "project_manager"。
 */
@Component
public class AbacQueryFilter {

    private static final Logger log = LoggerFactory.getLogger(AbacQueryFilter.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /** 策略缓存：entityCode → 匹配的过滤规则列表 */
    private final Map<String, List<FilterRule>> ruleCache = new ConcurrentHashMap<>();

    /** 上次缓存刷新时间 */
    private volatile long lastCacheRefresh = 0;
    private static final long CACHE_TTL_MS = 60_000; // 1 分钟

    public AbacQueryFilter(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ═══════════════ 公共 API ═══════════════════

    /**
     * 对查询结果做列裁剪：移除当前用户无权查看的列。
     *
     * @param entityCode 实体编码（如 "Contract"）
     * @param rows       查询结果行列表
     * @return 裁剪后的行列表（新 List，不影响原数据）
     */
    public List<Map<String, Object>> filterColumns(String entityCode, List<Map<String, Object>> rows) {
        if (rows == null || rows.isEmpty()) return rows;

        Set<String> hiddenColumns = getHiddenColumns(entityCode);
        if (hiddenColumns.isEmpty()) return rows;

        List<Map<String, Object>> filtered = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Map<String, Object> cleaned = new LinkedHashMap<>(row);
            for (String col : hiddenColumns) {
                cleaned.remove(col);
                cleaned.remove(col.toLowerCase());
            }
            filtered.add(cleaned);
        }
        log.debug("ABAC列裁剪: entity={}, hiddenColumns={}, rows={}", entityCode, hiddenColumns, rows.size());
        return filtered;
    }

    /**
     * 构建行过滤 SQL WHERE 片段。
     * 将 ${userId} 占位符替换为当前用户 ID。
     *
     * @param entityCode 实体编码
     * @return SQL WHERE 片段（如 "department_id IN (SELECT ...)"），无匹配时返回空字符串
     */
    public String buildRowFilterCondition(String entityCode) {
        List<FilterRule> rules = getRules(entityCode);
        if (rules.isEmpty()) return "";

        String userId = getCurrentUserId();
        StringBuilder sb = new StringBuilder();
        for (FilterRule rule : rules) {
            if (rule.rowFilterSql != null && !rule.rowFilterSql.isBlank()) {
                String resolved = rule.rowFilterSql.replace("${userId}", userId != null ? userId : "");
                if (sb.length() > 0) sb.append(" AND ");
                sb.append("(").append(resolved).append(")");
            }
        }
        return sb.toString();
    }

    /**
     * 获取当前实体需要隐藏的列名集合。
     */
    public Set<String> getHiddenColumns(String entityCode) {
        List<FilterRule> rules = getRules(entityCode);
        Set<String> all = new LinkedHashSet<>();
        for (FilterRule rule : rules) {
            if (rule.hiddenColumns != null) {
                all.addAll(rule.hiddenColumns);
            }
        }
        return all;
    }

    /**
     * 强制刷新策略缓存（供策略变更后调用）。
     */
    public void invalidateCache() {
        ruleCache.clear();
        lastCacheRefresh = 0;
        log.info("ABAC 查询过滤缓存已清空");
    }

    // ═══════════════ 内部实现 ═══════════════════

    /**
     * 获取当前实体适用的过滤规则列表。
     * 从 td_abac_policy 表中查询，按 priority DESC 排序。
     */
    private List<FilterRule> getRules(String entityCode) {
        // 检查缓存是否需要刷新
        if (System.currentTimeMillis() - lastCacheRefresh > CACHE_TTL_MS) {
            refreshCache();
        }
        return ruleCache.getOrDefault(entityCode, List.of());
    }

    /**
     * 刷新策略缓存：重新从数据库加载所有 ABAC 策略并匹配当前用户。
     */
    private synchronized void refreshCache() {
        if (System.currentTimeMillis() - lastCacheRefresh <= CACHE_TTL_MS) return;
        Map<String, List<FilterRule>> newCache = new ConcurrentHashMap<>();
        try {
            List<Map<String, Object>> policies = jdbc.queryForList(
                "SELECT * FROM td_abac_policy ORDER BY priority DESC, created_time DESC");
            for (Map<String, Object> policy : policies) {
                // 检查策略是否匹配当前用户
                String subjectCondition = (String) policy.get("subject_condition");
                if (!matchesCurrentUser(subjectCondition)) continue;

                // 检查 effect 为 ALLOW（DENY 的我们不处理，直接拒绝访问由上层 PEP 处理）
                String effect = (String) policy.get("effect");
                if (!"ALLOW".equalsIgnoreCase(effect)) continue;

                // 资源条件即为实体编码
                String resourceCondition = (String) policy.get("resource_condition");
                if (resourceCondition == null || resourceCondition.isBlank()) continue;

                // 解析 environment_condition 获取列/行过滤规则
                String envCondition = (String) policy.get("environment_condition");
                FilterRule rule = parseEnvironmentCondition(envCondition);
                if (rule == null) continue;

                // 按资源条件分组
                newCache.computeIfAbsent(resourceCondition, k -> new ArrayList<>()).add(rule);
            }
        } catch (Exception e) {
            log.warn("刷新ABAC策略缓存失败: {}", e.getMessage());
        }
        ruleCache.clear();
        ruleCache.putAll(newCache);
        lastCacheRefresh = System.currentTimeMillis();
        log.debug("ABAC策略缓存已刷新: {} entities with rules", newCache.size());
    }

    /**
     * 检查 subjectCondition 是否匹配当前用户的任意权限。
     */
    private boolean matchesCurrentUser(String subjectCondition) {
        if (subjectCondition == null || subjectCondition.isBlank()) return true;
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) return false;

        String cond = subjectCondition.trim().toLowerCase();
        for (GrantedAuthority ga : auth.getAuthorities()) {
            String authority = ga.getAuthority().toLowerCase();
            // 双向包含匹配：condition 包含在 authority 中，或 authority 包含 condition
            if (authority.contains(cond) || cond.contains(authority)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 从 environment_condition JSON 解析过滤规则。
     */
    private FilterRule parseEnvironmentCondition(String envCondition) {
        if (envCondition == null || envCondition.isBlank()) return null;
        try {
            Map<String, Object> env = objectMapper.readValue(envCondition,
                new TypeReference<Map<String, Object>>() {});
            FilterRule rule = new FilterRule();

            // 列黑名单
            @SuppressWarnings("unchecked")
            List<String> hidden = (List<String>) env.get("hiddenColumns");
            if (hidden != null) {
                rule.hiddenColumns = new LinkedHashSet<>(hidden);
            }

            // 行过滤 SQL 片段
            Object rowFilterObj = env.get("rowFilterSql");
            if (rowFilterObj != null) {
                rule.rowFilterSql = rowFilterObj.toString();
            }

            return (rule.hiddenColumns != null || rule.rowFilterSql != null) ? rule : null;
        } catch (Exception e) {
            log.debug("解析ABAC environment_condition失败: {}", e.getMessage());
            return null;
        }
    }

    /**
     * 从 SecurityContext 获取当前用户 ID。
     */
    private String getCurrentUserId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.isAuthenticated()) {
            return auth.getName();
        }
        return null;
    }

    // ═══════════════ 内部类 ═══════════════════

    /**
     * 单个 ABAC 策略解析后的过滤规则。
     */
    private static class FilterRule {
        Set<String> hiddenColumns;
        String rowFilterSql;
    }
}
