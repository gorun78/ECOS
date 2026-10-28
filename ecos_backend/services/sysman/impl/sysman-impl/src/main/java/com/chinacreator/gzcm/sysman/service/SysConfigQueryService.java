package com.chinacreator.gzcm.sysman.service;

import com.chinacreator.gzcm.sysman.dao.SysConfigDao;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * 系统配置查询服务 — 从 SysConfigController 下沉的 JdbcTemplate 访问。
 * <p>铁律 §2.5：SQL 与数据访问下沉到 DAO（{@link SysConfigDao}），本类保留 JSONB 反序列化。
 */
@Service
public class SysConfigQueryService {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    private final SysConfigDao dao;

    public SysConfigQueryService(SysConfigDao dao) {
        this.dao = dao;
    }

    /** 数据源是否可用（Controller 依赖此判断以返回兜底空结果） */
    public boolean isAvailable() {
        return dao != null;
    }

    /**
     * 按分组/edition 列出配置项；返回的每行已将 config_options JSONB 解析为 List。
     *
     * @param group   配置分组，可为空
     * @param edition 版本过滤，可为空
     * @param currentEdition 当前版本标识（来自 Maven profile）
     */
    public List<Map<String, Object>> listByGroup(String group, String edition, String currentEdition) {
        // 构建 SQL: 包含 config_options, impact_scope, edition 字段
        StringBuilder sqlBuilder = new StringBuilder(
            "SELECT id, config_group, config_key, config_value, config_type, " +
            "config_label, config_label_en, description, sort_order, " +
            "config_options, impact_scope, edition " +
            "FROM sys_config WHERE 1=1");
        List<Object> params = new ArrayList<>();

        if (group != null && !group.isBlank()) {
            sqlBuilder.append(" AND config_group=?");
            params.add(group);
        }

        // edition 过滤: 如果指定了 edition，返回 edition='all' 或 edition=<指定的>
        String effectiveEdition = !edition.isBlank() ? edition : currentEdition;
        if (effectiveEdition != null && !effectiveEdition.isBlank() && !"all".equals(effectiveEdition)) {
            sqlBuilder.append(" AND (edition='all' OR edition=?)");
            params.add(effectiveEdition);
        }

        if (group != null && !group.isBlank()) {
            sqlBuilder.append(" ORDER BY sort_order");
        } else {
            sqlBuilder.append(" ORDER BY config_group, sort_order");
        }

        List<Map<String, Object>> rows = dao.queryForList(
                sqlBuilder.toString(), params.toArray());

        // Post-process: parse config_options JSONB → List
        for (Map<String, Object> row : rows) {
            Object opts = row.get("config_options");
            if (opts instanceof String && !((String) opts).isBlank()) {
                try {
                    List<String> parsed = objectMapper.readValue(
                            (String) opts, new TypeReference<List<String>>() {});
                    row.put("config_options", parsed);
                } catch (Exception e) {
                    row.put("config_options", List.of());
                }
            } else if (opts == null) {
                row.put("config_options", List.of());
            }
        }

        return rows;
    }

    /** 更新单个配置值；返回受影响行数 */
    public int updateConfigValue(String configKey, String val) {
        return dao.update(
                "UPDATE sys_config SET config_value=?, updated_at=NOW() WHERE config_key=?",
                val, configKey);
    }

    /** 查询配置元数据（带定义、影响范围） */
    public List<Map<String, Object>> queryMetadata() {
        String sql = "SELECT id, config_group, config_key, config_value, config_type, " +
                     "config_label, config_label_en, description, impact_scope, edition " +
                     "FROM sys_config ORDER BY config_group, sort_order";
        return dao.queryForList(sql);
    }

    /**
     * 契约面（详设接口总表 {@code GET /api/v1/system/config/group} → getConfigByGroup）按分组取配置项。
     *
     * <p><b>列面口径</b>：只选 DDL 单源实存列（{@code V13__ecos_sys_config.sql} 建表 +
     * {@code V162} §5 ALTER 的 config_group/description/config_type/config_label/sort_order/edition/status）。
     * 本方法刻意<b>不</b>复用 {@link #listByGroup} 的列清单——那条腿选的
     * {@code config_options}/{@code impact_scope} 在 DDL 单源里零 CREATE 载体（2026-10-28 实测 live
     * {@code ecos_control.sys_config} 13 列亦无此二者），照抄会把新端点一起拖进幻影列失败。</p>
     *
     * <p><b>版本面</b>：{@code sys_config} 无 {@code version_no} 列（后端口径＝DB 只存在用版本、
     * 历史版本一律进 Git，《后端开发规范》§十一.1），故查询不合成版本号；需要时由调用方按
     * {@code updated_at} 如实呈现。</p>
     *
     * @param configGroup     配置分组（必给，调用方已裁决非空）
     * @param keys            可选配置键白名单；空/未给＝该分组全部在用项
     * @param edition         调用方指定的版本档；空＝用 {@code currentEdition}
     * @param currentEdition  当前部署档位（Maven profile）
     */
    public List<Map<String, Object>> listByConfigGroupForContract(String configGroup, List<String> keys,
                                                                 String edition, String currentEdition) {
        StringBuilder sqlBuilder = new StringBuilder(
            "SELECT config_key, config_value, config_type, config_group, edition, updated_at " +
            "FROM sys_config WHERE status='active' AND config_group=?");
        List<Object> params = new ArrayList<>();
        params.add(configGroup);

        String effectiveEdition = (edition == null || edition.isBlank()) ? currentEdition : edition;
        if (effectiveEdition != null && !effectiveEdition.isBlank() && !"all".equals(effectiveEdition)) {
            sqlBuilder.append(" AND (edition='all' OR edition=?)");
            params.add(effectiveEdition);
        }

        if (keys != null && !keys.isEmpty()) {
            sqlBuilder.append(" AND config_key IN (")
                     .append(String.join(",", java.util.Collections.nCopies(keys.size(), "?")))
                     .append(")");
            params.addAll(keys);
        }

        sqlBuilder.append(" ORDER BY sort_order, config_key");
        return dao.queryForList(sqlBuilder.toString(), params.toArray());
    }

    /** 查询用于消费审计的配置项基本信息 */
    public List<Map<String, Object>> queryForAudit() {
        String sql = "SELECT config_key, config_group, config_label, description FROM sys_config ORDER BY config_group, sort_order";
        return dao.queryForList(sql);
    }

    // ── PMO-E2 通用委托方法（供 Controller 调用）──
    public List<Map<String, Object>> queryForList(String sql, Object... args) {
        return dao.queryForList(sql, args);
    }
    public int update(String sql, Object... args) {
        return dao.update(sql, args);
    }
}
