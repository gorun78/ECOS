package com.chinacreator.gzcm.engine.ontology.governance.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 本体候选 INSERT 占位符/实参/列数一致性门禁。
 *
 * <p><b>为什么需要</b>：2026-11-10 活体实测发现该 INSERT 少一个 {@code ?}（{@code version_no} 无对应值），
 * 报错 `栏位索引超过许可范围：28，栏位数：27` —— 任何租户建候选都失败，且单测/静态检查都不覆盖这类
 * 「占位符元数与列数不等」的形态缺陷。本类用记录型 JdbcTemplate 截获最终 SQL 与实参，
 * 钉住 ①占位符数 == 实参数；②列数 == 占位符 + NOW()×2 + 字面量 1。</p>
 */
class OntologyCandidateDaoInsertArityTest {

    /** 记录最后一次 update 的 SQL 与实参。 */
    private static final class RecordingJdbc extends JdbcTemplate {
        String sql;
        Object[] args;

        @Override
        public int update(String sql, Object... args) {
            this.sql = sql;
            this.args = args;
            return 1;
        }
    }

    private static OntologyCandidateDao daoWith(JdbcTemplate jdbc) {
        OntologyCandidateDao dao = new OntologyCandidateDao("ecos_ontology");
        try {
            var field = com.chinacreator.gzcm.runtime.core.database.support.JdbcTemplateDaoSupport.class
                    .getDeclaredField("jdbcTemplate");
            field.setAccessible(true);
            field.set(dao, jdbc);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
        return dao;
    }

    @Test
    @DisplayName("insert：占位符数必须等于实参数，且列数 = 占位符 + NOW()×2 + 字面量 1")
    void placeholdersMatchArguments() {
        RecordingJdbc jdbc = new RecordingJdbc();
        OntologyCandidateDao dao = daoWith(jdbc);

        int affected = dao.insert("id-1", "tenant-a", "org-1", "ENTITY", "Customer", null, null,
                "Customer", "{}", "ev-1", 0.5d, "UNKNOWN", 0, "[]", "{}", "{}", null,
                "DRAFT", null, null, null, null, "trace-1", "idem-1", "default", "1", "u1");

        assertEquals(1, affected);
        long placeholders = jdbc.sql.chars().filter(c -> c == '?').count();
        assertEquals(jdbc.args.length, placeholders,
                "占位符与实参数量必须相等（少一个 ? 即运行时 '栏位索引超过许可范围'）: " + jdbc.sql);

        String columnsPart = jdbc.sql.substring(jdbc.sql.indexOf('(') + 1, jdbc.sql.indexOf(')'));
        long columns = columnsPart.split(",").length;
        long nowFuncs = jdbc.sql.split("NOW\\(\\)", -1).length - 1;
        assertEquals(placeholders + nowFuncs + 1, columns,
                "列数应为 占位符 + NOW()×2 + 字面量(is_deleted 0)；实得 columns=" + columns
                        + " placeholders=" + placeholders + " now=" + nowFuncs);
        assertTrue(jdbc.sql.contains("version_no"), "列清单必须含 version_no");
    }
}
