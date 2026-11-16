package com.chinacreator.gzcm.engine.ontology.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.chinacreator.gzcm.engine.ontology.model.OntologyEntity;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;

/**
 * 本体实体/属性/关系读面软删过滤 + 实体写入域外键门禁（D135 / D136，2026-11-16 活体验证）。
 *
 * <p><b>病灶（活体实测）</b>：软删后 {@code is_deleted = 1} 的行仍出现在列表与详情接口
 * （{@code /api/v1/ecos/entities} 曾列出已删的 ENT_USER、已归档关系 rel001~rel005 详情可 200）；
 * 同时 {@code insertEntity} 不写 {@code domain_id}，按域查询永远查不到自己创建的实体。</p>
 *
 * <p><b>本门钉什么</b>：用记录型 JdbcTemplate 截获最终 SQL，断言 ①六条读路径都带
 * {@code is_deleted = 0}；②实体 INSERT 列面含 {@code domain_id} 且占位符与实参数量相等。
 * 只判 SQL 文本不连库，跑在纯单测层。</p>
 */
class OntologySoftDeleteReadFaceDaoTest {

    private static final class RecordingJdbc extends JdbcTemplate {
        String sql;
        Object[] args;

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper) {
            this.sql = sql;
            this.args = new Object[0];
            return List.of();
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            this.sql = sql;
            this.args = args;
            return List.of();
        }

        @Override
        public int update(String sql, Object... args) {
            this.sql = sql;
            this.args = args;
            return 1;
        }
    }

    private static OntologyDao daoWith(JdbcTemplate jdbc) {
        OntologyDao dao = new OntologyDao();
        try {
            var field = JdbcTemplateDaoSupport.class.getDeclaredField("jdbcTemplate");
            field.setAccessible(true);
            field.set(dao, jdbc);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return dao;
    }

    private static void assertLiveOnly(RecordingJdbc jdbc, String method) {
        String sql = jdbc.sql;
        assertTrue(sql != null, method + " 未发出 SQL");
        assertTrue(sql.toLowerCase().contains("is_deleted = 0"),
                method + " 读路径缺 is_deleted = 0 过滤（软删行会漏到读面）: " + sql);
        assertFalse(sql.toLowerCase().contains("is_deleted = 1"),
                method + " 不应反向过滤: " + sql);
    }

    @Test
    @DisplayName("D135：六条读路径全部只读未软删行")
    void readPathsFilterSoftDeleted() {
        RecordingJdbc jdbc = new RecordingJdbc();
        OntologyDao dao = daoWith(jdbc);

        dao.findEntitiesByOntology("ont001");
        assertLiveOnly(jdbc, "findEntitiesByOntology");

        dao.findEntityById("ent001");
        assertLiveOnly(jdbc, "findEntityById");

        dao.findPropertiesByEntity("ent001");
        assertLiveOnly(jdbc, "findPropertiesByEntity");

        dao.findPropertyById("prop001");
        assertLiveOnly(jdbc, "findPropertyById");

        dao.findRelationshipsByEntity("ent001");
        assertLiveOnly(jdbc, "findRelationshipsByEntity");

        dao.findAllRelationships();
        assertLiveOnly(jdbc, "findAllRelationships");

        dao.findRelationshipById("rel001");
        assertLiveOnly(jdbc, "findRelationshipById");

        dao.findAllEntities();
        assertLiveOnly(jdbc, "findAllEntities");

        dao.findEntitiesByDomain("Finance");
        assertLiveOnly(jdbc, "findEntitiesByDomain");
        assertTrue(jdbc.sql.contains("ecos_ontology.ecos_domain"),
                "按域查询须关联权威域表 ecos_ontology.ecos_domain（D132 同源）: " + jdbc.sql);
    }

    @Test
    @DisplayName("D134：关系归档改用 update_time，禁回潮不存在的 updated_at 列")
    void relationshipArchiveUsesUpdateTime() {
        RecordingJdbc jdbc = new RecordingJdbc();
        OntologyDao dao = daoWith(jdbc);

        dao.deleteRelationship("rel001");
        String sql = jdbc.sql.toLowerCase();
        assertTrue(sql.contains("update_time"), "关系表时间列是 update_time: " + jdbc.sql);
        assertFalse(sql.contains("updated_at"),
                "ecos_ontology_relationship 无 updated_at 列（42703 断裂）: " + jdbc.sql);
        assertTrue(sql.contains("is_deleted = 1") && sql.contains("is_deleted = 0"),
                "归档须把 is_deleted 置 1 且只动未删行: " + jdbc.sql);

        dao.deleteRelationshipsByEntity("ent002");
        assertFalse(jdbc.sql.toLowerCase().contains("updated_at"),
                "按实体批量归档同表同列，禁回潮: " + jdbc.sql);
    }

    @Test
    @DisplayName("D136：实体 INSERT 持久化 domain_id，占位符与实参数量相等")
    void insertEntityPersistsDomainId() {
        RecordingJdbc jdbc = new RecordingJdbc();
        OntologyDao dao = daoWith(jdbc);

        OntologyEntity entity = new OntologyEntity();
        entity.setId("ent9001");
        entity.setOntologyId("Finance");
        entity.setDomainId("dom_fin");
        entity.setCode("ENT_FIN_PROBE");
        entity.setName("财务探针");
        entity.setDescription("");
        entity.setEntityType("MASTER");
        entity.setSortOrder(1);

        dao.insertEntity(entity);

        String sql = jdbc.sql.toLowerCase();
        assertTrue(sql.contains("domain_id"),
                "INSERT 列面必须含 domain_id，否则按域查询永远查不到: " + jdbc.sql);
        long placeholders = jdbc.sql.chars().filter(c -> c == '?').count();
        assertEquals(jdbc.args.length, placeholders,
                "占位符与实参数量必须相等: " + jdbc.sql);
        assertEquals("dom_fin", jdbc.args[7],
                "domain_id 实参须落在第 8 个占位符位置: " + jdbc.sql);
    }
}
