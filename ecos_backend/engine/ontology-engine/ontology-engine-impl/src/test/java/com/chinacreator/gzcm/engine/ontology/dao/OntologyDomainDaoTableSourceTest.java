package com.chinacreator.gzcm.engine.ontology.dao;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;

import com.chinacreator.gzcm.engine.ontology.model.OntologyDomain;
import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;

/**
 * 域表读写指向权威副本门禁（D132，2026-11-16 活体验证）。
 *
 * <p><b>病灶</b>：{@code OntologyDomainDao} 用裸表名 {@code ecos_ontology_domain}，
 * 在 {@code search_path} 里先命中 {@code public.ecos_ontology_domain}（V120 空壳，0 业务行），
 * 而真正的域表是<b>另一张名字</b> {@code ecos_ontology.ecos_domain}。后果：域列表读空壳、
 * 空壳域 id 写进实体 {@code domain_id} 撞外键（PUT /ontology/objects/{id}/domain 恒 409）。</p>
 *
 * <p><b>本门钉两件事</b>：①每条语句都 schema 限定到 {@code ecos_ontology.ecos_domain}，
 * 且不再出现裸名 {@code ecos_ontology_domain}（否则又会被空壳抢先命中）；
 * ②时间列用真源的 {@code created_at} / {@code updated_at}，不是空壳的 {@code create_time} / {@code update_time}。</p>
 */
class OntologyDomainDaoTableSourceTest {

    private static final class RecordingJdbc extends JdbcTemplate {
        final List<String> statements = new ArrayList<>();

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper) {
            statements.add(sql);
            return List.of();
        }

        @Override
        public <T> List<T> query(String sql, RowMapper<T> rowMapper, Object... args) {
            statements.add(sql);
            return List.of();
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            statements.add(sql);
            return null;
        }

        @Override
        public int update(String sql, Object... args) {
            statements.add(sql);
            return 1;
        }
    }

    private static OntologyDomainDao daoWith(RecordingJdbc jdbc) {
        OntologyDomainDao dao = new OntologyDomainDao();
        try {
            var field = JdbcTemplateDaoSupport.class.getDeclaredField("jdbcTemplate");
            field.setAccessible(true);
            field.set(dao, jdbc);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return dao;
    }

    private static void assertAuthoritative(List<String> statements, String method) {
        String sql = statements.get(statements.size() - 1);
        assertTrue(sql.contains("ecos_ontology.ecos_domain"),
                method + " 必须 schema 限定到权威域表: " + sql);
        assertFalse(sql.toLowerCase().contains(" create_time"),
                method + " 用了空壳的 create_time（真源是 created_at）: " + sql);
        assertFalse(sql.toLowerCase().contains(" update_time"),
                method + " 用了空壳的 update_time（真源是 updated_at）: " + sql);
    }

    @Test
    @DisplayName("D132：域 CRUD 全部读权威表 ecos_ontology.ecos_domain，且不碰空壳时间列")
    void allStatementsTargetAuthoritativeTable() {
        RecordingJdbc jdbc = new RecordingJdbc();
        OntologyDomainDao dao = daoWith(jdbc);

        dao.findAll();
        assertAuthoritative(jdbc.statements, "findAll");

        dao.findByCode("Finance");
        assertAuthoritative(jdbc.statements, "findByCode");

        dao.findById("dom_fin");
        assertAuthoritative(jdbc.statements, "findById");

        dao.existsByCode("Finance");
        assertAuthoritative(jdbc.statements, "existsByCode");

        dao.searchDomains("Fin", 20);
        assertAuthoritative(jdbc.statements, "searchDomains");

        dao.countEntitiesByDomain("Finance");
        assertAuthoritative(jdbc.statements, "countEntitiesByDomain");

        OntologyDomain domain = new OntologyDomain();
        domain.setId("dom_probe");
        domain.setCode("Probe");
        domain.setName("探针域");
        domain.setStatus("Active");
        domain.setSortOrder(1);
        dao.insert(domain);
        String insertSql = jdbc.statements.get(jdbc.statements.size() - 1);
        assertTrue(insertSql.contains("ecos_ontology.ecos_domain"), "insert 必须写权威表: " + insertSql);
        assertTrue(insertSql.contains("created_at") && insertSql.contains("updated_at"),
                "insert 时间列须用真源 created_at/updated_at: " + insertSql);
        assertFalse(insertSql.toLowerCase().contains("create_time"), "insert 禁回潮空壳列: " + insertSql);

        dao.update("dom_probe", "Probe", "探针域", "admin", "", "Active");
        assertAuthoritative(jdbc.statements, "update");

        dao.delete("dom_probe");
        assertAuthoritative(jdbc.statements, "delete");
    }
}
