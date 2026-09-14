package com.chinacreator.gzcm.engine.ontology.repository;

import java.sql.Timestamp;
import java.util.List;
import java.util.Optional;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;
import com.chinacreator.gzcm.engine.ontology.model.OntologyWorkbenchObject;

/**
 * JdbcTemplate 仓库 — 本体工作台 5 类视图对象统一持久化（Wave B-5 T7）。
 *
 * <p>表 {@code ecos_ontology_workbench_object}（V121__ecos_ontology_workbench_view.sql），
 * 按 object_type 区分 action / interface / shared_property / function / dataset，
 * definition_json 承载各类差异字段。
 *
 * <p>风格对齐 {@link OntologyRepository}：显式列名查询（禁 SELECT *）、
 * 按需 COALESCE 更新、逻辑删除 (T17 语义: is_deleted=1 + status='ARCHIVED')。
 */
@Repository
public class OntologyWorkbenchObjectRepository {

    private static final String COLUMNS =
        "id, ontology_id, object_type, code, name, description, definition_json, " +
        "status, create_time, update_time";

    private final JdbcTemplate jdbc;

    public OntologyWorkbenchObjectRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    // ═══════════════ RowMapper ═══════════════════

    private final RowMapper<OntologyWorkbenchObject> OBJECT_MAPPER = (rs, rn) -> {
        OntologyWorkbenchObject o = new OntologyWorkbenchObject();
        o.setId(rs.getString("id"));
        o.setOntologyId(rs.getString("ontology_id"));
        o.setObjectType(rs.getString("object_type"));
        o.setCode(rs.getString("code"));
        o.setName(rs.getString("name"));
        o.setDescription(rs.getString("description"));
        o.setDefinitionJson(rs.getString("definition_json"));
        o.setStatus(rs.getString("status"));
        o.setCreateTime(ts(rs.getTimestamp("create_time")));
        o.setUpdateTime(ts(rs.getTimestamp("update_time")));
        return o;
    };

    private static java.time.LocalDateTime ts(Timestamp t) {
        return t != null ? t.toLocalDateTime() : null;
    }

    // ═══════════════ Query ═══════════════════

    /** 按本体 + 视图类型列出未删除对象（type 为空/blank 时返回该本体全部类型）。 */
    public List<OntologyWorkbenchObject> findObjectsByOntology(String ontologyId, String objectType) {
        if (objectType == null || objectType.isBlank()) {
            return jdbc.query(
                "SELECT " + COLUMNS + " FROM ecos_ontology_workbench_object " +
                "WHERE ontology_id = ? AND is_deleted = 0 ORDER BY create_time",
                OBJECT_MAPPER, ontologyId);
        }
        return jdbc.query(
            "SELECT " + COLUMNS + " FROM ecos_ontology_workbench_object " +
            "WHERE ontology_id = ? AND object_type = ? AND is_deleted = 0 ORDER BY create_time",
            OBJECT_MAPPER, ontologyId, objectType);
    }

    /** 按 id + 本体列出未删除对象（fileId 保存时校验归属用语）。 */
    public Optional<OntologyWorkbenchObject> findObjectById(String ontologyId, String id) {
        List<OntologyWorkbenchObject> list = jdbc.query(
            "SELECT " + COLUMNS + " FROM ecos_ontology_workbench_object " +
            "WHERE ontology_id = ? AND id = ? AND is_deleted = 0",
            OBJECT_MAPPER, ontologyId, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    // ═══════════════ Mutation ═══════════════════

    /** 新增视图对象（create_by/update_by 落 'system'，与 T17 一致）。 */
    public int insertObject(OntologyWorkbenchObject o) {
        return jdbc.update("""
            INSERT INTO ecos_ontology_workbench_object
                (id, ontology_id, object_type, code, name, description, definition_json, status,
                 create_time, update_time, create_by, update_by, is_deleted)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW(), 'system', 'system', 0)
            """, o.getId(), o.getOntologyId(), o.getObjectType(), o.getCode(),
            o.getName(), o.getDescription(), o.getDefinitionJson(), o.getStatus());
    }

    /**
     * 按需更新（COALESCE 语义: null 字段不覆盖有效数据, 对齐 OntologyRepository.updateEntity）。
     * object_type 参与查询语义, 不开放变更。
     */
    public int updateObject(String ontologyId, String id, String code, String name,
                            String description, String definitionJson, String status) {
        return jdbc.update("""
            UPDATE ecos_ontology_workbench_object SET
                code = COALESCE(?, code),
                name = COALESCE(?, name),
                description = COALESCE(?, description),
                definition_json = COALESCE(?, definition_json),
                status = COALESCE(?, status),
                update_time = NOW(),
                update_by = 'system'
            WHERE ontology_id = ? AND id = ? AND is_deleted = 0
            """, code, name, description, definitionJson, status, ontologyId, id);
    }

    /** 逻辑删除 (T17 语义): 按 id 单行, 限本体归属。 */
    public int deleteObject(String ontologyId, String id) {
        return jdbc.update("""
            UPDATE ecos_ontology_workbench_object SET is_deleted = 1, status = 'ARCHIVED',
                update_time = NOW(), update_by = 'system'
            WHERE ontology_id = ? AND id = ? AND is_deleted = 0
            """, ontologyId, id);
    }
}
