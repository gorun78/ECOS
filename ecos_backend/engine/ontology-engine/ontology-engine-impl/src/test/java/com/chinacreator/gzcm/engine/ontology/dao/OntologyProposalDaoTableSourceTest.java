package com.chinacreator.gzcm.engine.ontology.dao;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport;

/**
 * 提案表读写指向 V174 合规表门禁（D137，2026-11-16 活体验证）。
 *
 * <p><b>病灶</b>：V174 已建合规表 {@code ecos_ontology.ecos_ontology_proposal}
 * （VARCHAR(36) PK / {@code payload_json TEXT} / {@code snapshot_json TEXT} /
 * DR06 审计列 {@code create_time/update_time/is_deleted}），但 DAO 一直读写旧复数表
 * {@code ecos_ontology_proposals}（BIGSERIAL PK + {@code jsonb} 列 + 无 {@code is_deleted}）。
 * 三条活体症状：POST 创建 409（应用侧 UUID 撞 {@code bigint} 主键）、
 * GET 详情 500（{@code is_deleted} 列不存在）、列表恒空（读 0 行旧表）。</p>
 *
 * <p><b>本门钉三件事</b>：①每条语句都 schema 限定到 {@code ecos_ontology.ecos_ontology_proposal}，
 * 且不再出现旧复数表名；②时间列一律 {@code create_time}/{@code update_time}，
 * 不出现旧表列 {@code created_at}/{@code updated_at}；③JSON 列写 {@code payload_json}/{@code snapshot_json}
 * （读语句经列别名承旧键面 {@code payload}/{@code snapshot}，故读面出现别名属正常）。</p>
 *
 * <p>断言遍历<b>全部</b>累计语句而非只看最后一条——否则 update 后紧跟的 SELECT 会把写语句的病灶洗白。</p>
 */
class OntologyProposalDaoTableSourceTest {

    private static final String COMPLIANT_TABLE = "ecos_ontology.ecos_ontology_proposal";

    private static final class RecordingJdbc extends JdbcTemplate {
        final List<String> statements = new ArrayList<>();
        final List<List<Object>> args = new ArrayList<>();

        List<Object> argsOfLast() {
            return args.isEmpty() ? List.of() : args.get(args.size() - 1);
        }

        private void record(String sql, Object[] sqlArgs) {
            statements.add(sql);
            args.add(sqlArgs == null ? new ArrayList<>() : new ArrayList<>(Arrays.asList(sqlArgs)));
        }

        @Override
        public List<Map<String, Object>> queryForList(String sql, Object... sqlArgs) {
            record(sql, sqlArgs);
            return List.of();
        }

        @Override
        public Map<String, Object> queryForMap(String sql, Object... sqlArgs) {
            record(sql, sqlArgs);
            Map<String, Object> row = new HashMap<>();
            row.put("id", "probe-1");
            row.put("status", "DRAFT");
            return row;
        }

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... sqlArgs) {
            record(sql, sqlArgs);
            return null;
        }

        @Override
        public int update(String sql, Object... sqlArgs) {
            record(sql, sqlArgs);
            return 1;
        }
    }

    private static OntologyProposalDao daoWith(RecordingJdbc jdbc) {
        OntologyProposalDao dao = new OntologyProposalDao("ecos_ontology");
        try {
            var field = JdbcTemplateDaoSupport.class.getDeclaredField("jdbcTemplate");
            field.setAccessible(true);
            field.set(dao, jdbc);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return dao;
    }

    /** 全量累计语句逐条断言（表源 + 旧表列名禁回潮）。 */
    private static void assertAllCompliant(List<String> statements, String method) {
        assertFalse(statements.isEmpty(), method + " 未发出任何语句，录制失效");
        for (String sql : statements) {
            assertTrue(sql.contains(COMPLIANT_TABLE),
                    method + " 必须 schema 限定到 V174 合规表: " + sql);
            assertFalse(sql.contains("ecos_ontology_proposals"),
                    method + " 仍指向旧复数表（V174 已停写）: " + sql);
            // 读面用 `create_time AS created_at` 承旧键面，别名合法；先剥别名再判真列名
            String normalized = sql.replaceAll("(?i)AS\\s+created_at", "AS __alias1")
                    .replaceAll("(?i)AS\\s+updated_at", "AS __alias2");
            assertFalse(normalized.contains("created_at"),
                    method + " 用了旧表列 created_at（合规表是 create_time）: " + sql);
            assertFalse(normalized.contains("updated_at"),
                    method + " 用了旧表列 updated_at（合规表是 update_time）: " + sql);
        }
    }

    private static String last(List<String> statements) {
        return statements.get(statements.size() - 1);
    }

    @Test
    @DisplayName("D137：提案 CRUD/状态流转全部读写 ecos_ontology.ecos_ontology_proposal")
    void allStatementsTargetCompliantTable() {
        RecordingJdbc jdbc = new RecordingJdbc();
        OntologyProposalDao dao = daoWith(jdbc);

        dao.listProposals(null, null);
        assertAllCompliant(jdbc.statements, "listProposals");
        assertTrue(last(jdbc.statements).contains("is_deleted=0"),
                "listProposals 须过滤软删行（DR05）: " + last(jdbc.statements));
        assertTrue(last(jdbc.statements).contains("ORDER BY create_time DESC"),
                "listProposals 须按 create_time 倒序: " + last(jdbc.statements));

        dao.findProposalById("probe-1");
        assertAllCompliant(jdbc.statements, "findProposalById");

        dao.insertProposal("probe-1", "Finance", "CREATE_ENTITY", "Customer",
                "{\"title\":\"t\"}", null, "DRAFT", "admin");
        assertAllCompliant(jdbc.statements, "insertProposal");
        String insertSql = last(jdbc.statements);
        assertTrue(insertSql.contains("payload_json") && insertSql.contains("snapshot_json"),
                "insert 须写合规 JSON 列: " + insertSql);
        assertFalse(insertSql.contains(" payload,") || insertSql.contains("payload=?"),
                "insert 列清单禁回潮旧表 payload: " + insertSql);

        dao.updateAndFind("UPDATE " + COMPLIANT_TABLE + " SET update_time=CURRENT_TIMESTAMP", List.of(), "probe-1");
        assertAllCompliant(jdbc.statements, "updateAndFind");

        dao.deleteProposal("probe-1");
        assertAllCompliant(jdbc.statements, "deleteProposal");
        String deleteSql = last(jdbc.statements);
        assertFalse(deleteSql.trim().toUpperCase().startsWith("DELETE"),
                "deleteProposal 须走 DR05 软删，不得物理删行: " + deleteSql);
        assertTrue(deleteSql.contains("is_deleted=1"),
                "deleteProposal 须置 is_deleted=1: " + deleteSql);

        dao.updateStatus("probe-1", "PENDING");
        assertAllCompliant(jdbc.statements, "updateStatus");

        dao.execute("probe-1");
        assertAllCompliant(jdbc.statements, "execute");

        dao.approve("probe-1", "APPROVED", "reviewer", "ok");
        assertAllCompliant(jdbc.statements, "approve");

        dao.markExecutedWithVersion("probe-1", "EXECUTED", "vid-probe-42");
        assertAllCompliant(jdbc.statements, "markExecutedWithVersion");
        assertTrue(jdbc.argsOfLast().contains("vid-probe-42"),
                "version_id 须按版本 ID 字符串写入（D139），实际参数: " + jdbc.argsOfLast());

        dao.markExecuted("probe-1", "EXECUTED");
        assertAllCompliant(jdbc.statements, "markExecuted");

        dao.findOptimisticVersion("probe-1");
        assertAllCompliant(jdbc.statements, "findOptimisticVersion");

        dao.optimisticTransition("probe-1", "APPROVED", "reviewer", "ok", 1);
        assertAllCompliant(jdbc.statements, "optimisticTransition");

        dao.optimisticVersionIncrement("probe-1", 1, "APPROVED");
        assertAllCompliant(jdbc.statements, "optimisticVersionIncrement(status)");

        dao.optimisticVersionIncrement("probe-1", 1, null);
        assertAllCompliant(jdbc.statements, "optimisticVersionIncrement(null)");
    }
}
