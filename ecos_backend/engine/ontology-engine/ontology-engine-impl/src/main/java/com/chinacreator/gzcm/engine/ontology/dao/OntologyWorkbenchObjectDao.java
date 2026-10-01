package com.chinacreator.gzcm.engine.ontology.dao;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import com.chinacreator.gzcm.engine.ontology.model.OntologyWorkbenchObject;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

/**
 * B3c 数据访问收口（R2/B3c）：本体工作台 5 类视图对象统一持久化
 * （{@code ecos_ontology_workbench_object}，V121）。
 *
 * <p>显式列清单（禁 {@code SELECT *}）、按需 COALESCE 更新、逻辑删除（T17 语义）
 * 全部原样搬运自 {@code OntologyWorkbenchObjectRepository}。</p>
 */
@Repository
public class OntologyWorkbenchObjectDao extends JdbcTemplateDaoSupport {

    private static final String COLUMNS =
        "id, ontology_id, object_type, code, name, description, definition_json, " +
        "status, create_time, update_time";

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

    private static LocalDateTime ts(Timestamp t) {
        return t != null ? t.toLocalDateTime() : null;
    }

    public List<OntologyWorkbenchObject> findObjectsByOntology(String ontologyId, String objectType) {
        if (objectType == null || objectType.isBlank()) {
            return jdbcTemplate.query(
                "SELECT " + COLUMNS + " FROM ecos_ontology_workbench_object " +
                "WHERE ontology_id = ? AND is_deleted = 0 ORDER BY create_time",
                OBJECT_MAPPER, ontologyId);
        }
        return jdbcTemplate.query(
            "SELECT " + COLUMNS + " FROM ecos_ontology_workbench_object " +
            "WHERE ontology_id = ? AND object_type = ? AND is_deleted = 0 ORDER BY create_time",
            OBJECT_MAPPER, ontologyId, objectType);
    }

    public Optional<OntologyWorkbenchObject> findObjectById(String ontologyId, String id) {
        List<OntologyWorkbenchObject> list = jdbcTemplate.query(
            "SELECT " + COLUMNS + " FROM ecos_ontology_workbench_object " +
            "WHERE ontology_id = ? AND id = ? AND is_deleted = 0",
            OBJECT_MAPPER, ontologyId, id);
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }

    public int insertObject(OntologyWorkbenchObject o) {
        return jdbcTemplate.update("""
            INSERT INTO ecos_ontology_workbench_object
                (id, ontology_id, object_type, code, name, description, definition_json, status,
                 create_time, update_time, create_by, update_by, is_deleted)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, NOW(), NOW(), 'system', 'system', 0)
            """, o.getId(), o.getOntologyId(), o.getObjectType(), o.getCode(),
            o.getName(), o.getDescription(), o.getDefinitionJson(), o.getStatus());
    }

    public int updateObject(String ontologyId, String id, String code, String name,
                            String description, String definitionJson, String status) {
        return jdbcTemplate.update("""
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

    public int deleteObject(String ontologyId, String id) {
        return jdbcTemplate.update("""
            UPDATE ecos_ontology_workbench_object SET is_deleted = 1, status = 'ARCHIVED',
                update_time = NOW(), update_by = 'system'
            WHERE ontology_id = ? AND id = ? AND is_deleted = 0
            """, ontologyId, id);
    }
}
