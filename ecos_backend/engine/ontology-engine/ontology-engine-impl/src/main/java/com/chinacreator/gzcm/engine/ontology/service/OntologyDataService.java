package com.chinacreator.gzcm.engine.ontology.service;

import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * 本体数据实例持久化服务 — 承载 {@code public.ecos_ontology_data} 表的全部 JDBC 访问。
 *
 * <p>由 {@code OntologyDataController} 调用：把数据记录（ObjectType 下的实例）
 * 的列表/详情/新增/更新/计数 SQL 从 Controller 抽到 Service 层
 * （此前 Controller 直接持有 {@code JdbcTemplate}，违反"Controller 不直连数据库"铁律）。
 *
 * <p>所有查询带 {@code is_deleted = 0} 过滤（逻辑删除语义）；
 * 返回 {@code List<Map<String, Object>>} 原始行，row→前端契约 Map 的映射
 * （payload JSON 反序列化、字段名对齐）保留在调用侧。
 */
@Service
public class OntologyDataService {

    private static final Logger log = LoggerFactory.getLogger(OntologyDataService.class);

    /** 数据行统一列选择（保持列顺序与既有契约一致）。 */
    private static final String SELECT_COLUMNS =
            "SELECT id, ontology_id, object_type, record_key, payload, status, " +
            "       create_time, update_time, create_by, update_by ";

    private final JdbcTemplate jdbc;

    public OntologyDataService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * 数据记录总数（可选按归属域过滤）。
     *
     * @param filterType 可选，对象类型 ID（对应 ontology_id 列），空/空白 = 全量
     * @return 匹配 is_deleted=0 的行数
     */
    public Integer countRecords(String filterType) {
        boolean hasFilter = filterType != null && !filterType.isBlank();
        final String where = hasFilter
                ? "WHERE ontology_id = ? AND is_deleted = 0"
                : "WHERE is_deleted = 0";
        return hasFilter
                ? jdbc.queryForObject("SELECT COUNT(*) FROM public.ecos_ontology_data " + where, Integer.class, filterType)
                : jdbc.queryForObject("SELECT COUNT(*) FROM public.ecos_ontology_data " + where, Integer.class);
    }

    /**
     * 数据记录分页查询（可选按归属域过滤，按 create_time 倒序）。
     *
     * @param filterType 可选，对象类型 ID（对应 ontology_id 列），空/空白 = 全量
     * @param limit      每页大小
     * @param offset     偏移量
     * @return 原始行列表
     */
    public List<Map<String, Object>> listRecords(String filterType, int limit, int offset) {
        boolean hasFilter = filterType != null && !filterType.isBlank();
        final String where = hasFilter
                ? "WHERE ontology_id = ? AND is_deleted = 0"
                : "WHERE is_deleted = 0";
        return hasFilter
                ? jdbc.queryForList(
                        SELECT_COLUMNS +
                        "FROM public.ecos_ontology_data " + where + " " +
                        "ORDER BY create_time DESC LIMIT ? OFFSET ?",
                        filterType, limit, offset)
                : jdbc.queryForList(
                        SELECT_COLUMNS +
                        "FROM public.ecos_ontology_data " + where + " " +
                        "ORDER BY create_time DESC LIMIT ? OFFSET ?",
                        limit, offset);
    }

    /**
     * 按主键查询单条数据记录（is_deleted=0 过滤）。
     *
     * @param id 记录主键（全量 UUID）
     * @return 原始行列表（命中 0 或 1 行）
     */
    public List<Map<String, Object>> findRecordById(String id) {
        return jdbc.queryForList(
                SELECT_COLUMNS +
                "FROM public.ecos_ontology_data WHERE id = ? AND is_deleted = 0",
                id);
    }

    /**
     * 插入数据记录（全量 UUID 主键，status 固定 'ACTIVE'，is_deleted=0）。
     *
     * @return 受影响行数
     */
    public int insertRecord(String id, String objectTypeId, String objectTypeName,
                            String recordKey, String payloadJson, String createdBy) {
        return jdbc.update(
                "INSERT INTO public.ecos_ontology_data " +
                "(id, ontology_id, object_type, record_key, payload, status, " +
                " create_time, update_time, create_by, update_by, is_deleted) " +
                "VALUES (?, ?, ?, ?, ?, 'ACTIVE', now(), now(), ?, ?, 0)",
                id, objectTypeId, objectTypeName, recordKey, payloadJson, createdBy, createdBy);
    }

    /**
     * 更新数据记录（COALESCE 风格：null 参数不覆盖已有值；objectTypeId 不可变更）。
     *
     * @return 受影响行数
     */
    public int updateRecord(String id, String objectTypeName, String createdBy, String payloadJson) {
        return jdbc.update(
                "UPDATE public.ecos_ontology_data SET " +
                "  object_type = COALESCE(?, object_type), " +
                "  create_by   = COALESCE(?, create_by), " +
                "  update_by   = COALESCE(?, update_by), " +
                "  payload     = ?, " +
                "  update_time = now() " +
                "WHERE id = ? AND is_deleted = 0",
                objectTypeName, createdBy, "system", payloadJson, id);
    }

    /**
     * 按主键计数（存在性检查，is_deleted=0 过滤）。
     */
    public Integer countById(String id) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.ecos_ontology_data WHERE id = ? AND is_deleted = 0",
                Integer.class, id);
    }

    /**
     * 按归属域计数（is_deleted=0 过滤）。
     */
    public Integer countByObjectType(String objectTypeId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM public.ecos_ontology_data " +
                "WHERE ontology_id = ? AND is_deleted = 0",
                Integer.class, objectTypeId);
    }
}
