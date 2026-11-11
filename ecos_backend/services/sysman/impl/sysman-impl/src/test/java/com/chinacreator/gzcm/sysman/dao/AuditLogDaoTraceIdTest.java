package com.chinacreator.gzcm.sysman.dao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Arrays;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 审计哈希链双写 SQL 形态门禁（CLI 生产化批 C）。
 *
 * <p><b>为什么需要它</b>：{@code trace_id} 列由 V303 建立，但 INSERT 语句是否真的带上该列
 * 只能靠代码形态守护——漏写不会报错（列可空），只会让「CLI trace_id == 审计表 trace_id」
 * 的核对永远查不到行。本用例用记录型 JdbcTemplate 子类截获 SQL 与实参，
 * 钉住列名与参数顺序（不用 Mockito varargs 匹配，避免匹配器元数错配导致恒 null 的假绿）。</p>
 */
class AuditLogDaoTraceIdTest {

    /** 记录最后一次 queryForObject 的 SQL 与实参。 */
    private static final class RecordingJdbc extends JdbcTemplate {
        String sql;
        Object[] args;

        @Override
        public <T> T queryForObject(String sql, Class<T> requiredType, Object... args) {
            this.sql = sql;
            this.args = args;
            @SuppressWarnings("unchecked")
            T value = (T) Long.valueOf(1L);
            return value;
        }
    }

    private static AuditLogDao daoWith(JdbcTemplate jdbc) {
        AuditLogDao dao = new AuditLogDao();
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
    @DisplayName("insertHashChainAudit：SQL 含 trace_id 列，末位占位为 traceId 实参")
    void traceIdIsPersisted() {
        RecordingJdbc jdbc = new RecordingJdbc();
        AuditLogDao dao = daoWith(jdbc);

        Long id = dao.insertHashChainAudit("u1", "CREATE", "E", "id-1", "{}", "127.0.0.1",
                "audit", "trace-abc");

        assertEquals(1L, id);
        assertTrue(jdbc.sql.contains("trace_id"),
                "INSERT 必须显式写入 trace_id（漏写不报错但核对恒空）: " + jdbc.sql);
        assertEquals(8, jdbc.args.length, "占位数量应为 8（末位 trace_id）：" + Arrays.toString(jdbc.args));
        assertEquals("trace-abc", jdbc.args[jdbc.args.length - 1], "traceId 必须绑在最后一位");
    }

    @Test
    @DisplayName("兼容重载：不传 traceId 时该列为 null（不伪造链路标识）")
    void legacyOverloadLeavesTraceNull() {
        RecordingJdbc jdbc = new RecordingJdbc();
        AuditLogDao dao = daoWith(jdbc);

        dao.insertHashChainAudit("u1", "UPDATE", "E", "id-2", "{}", "10.0.0.1", "audit");

        assertEquals(8, jdbc.args.length);
        assertNull(jdbc.args[jdbc.args.length - 1], "缺省重载不得编造 traceId");
    }
}
