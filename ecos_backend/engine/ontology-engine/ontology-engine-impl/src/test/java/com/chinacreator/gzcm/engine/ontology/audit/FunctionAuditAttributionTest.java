package com.chinacreator.gzcm.engine.ontology.audit;

import com.chinacreator.gzcm.engine.ontology.dao.FunctionAuditDao;
import com.chinacreator.gzcm.engine.ontology.engine.FunctionAuditRecord;
import com.chinacreator.gzcm.engine.ontology.engine.FunctionCacheManager;
import com.chinacreator.gzcm.sysman.iam.context.UserContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

/**
 * W80 — Function 审计实参归因测。
 *
 * <p>本测锁定 {@link FunctionCacheManager#writeAudit(FunctionAuditRecord)} 走本地兜底时：
 * <ul>
 *   <li>INSERT 命中的是<b>schema 限定</b> {@code ecos_ontology.ecos_function_audit}（V172 新表）；</li>
 *   <li>{@code function_name} 列不为 null（旧代码把 function_name 位置错写成 entityName）；</li>
 *   <li>{@code caller_id} 与 {@code trace_id} 参数绑定正确（alice/tr-1）。</li>
 * </ul>
 * </p>
 *
 * <p>Kafka 通道置 null 强制本地兜底（测试目标就是兜底路径的列绑定）。</p>
 */
@ExtendWith(MockitoExtension.class)
class FunctionAuditAttributionTest {

    @Mock private JdbcTemplate jdbc;

    private FunctionCacheManager cacheManager;

    @BeforeEach
    void setUp() {
        // eventBus=null → 直接走本地兜底
        cacheManager = new FunctionCacheManager(new FunctionAuditDao(jdbc, "ecos_ontology"), null, "ecos_ontology");
    }

    @AfterEach
    void teardown() {
        MDC.clear();
        UserContext.clear();
    }

    @Test
    @DisplayName("W80 — 审计行归因：INSERT 命中 {schema}.ecos_function_audit，function_name/caller_id/trace_id 参数绑定正确")
    void writeAudit_localFallback_bindsCorrectColumns() {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        FunctionAuditRecord rec = FunctionAuditRecord.builder()
                .functionId("fid-1")
                .functionName("fn_x")
                .expression("SUM(amount) FROM t WHERE period='2026-07'")
                .objectType("Invoice")
                .resultSummary("42.0")
                .durationMs(12L)
                .cacheHit(false)
                .callerId("alice")
                .traceId("tr-1")
                .status("SUCCESS")
                .build();

        // 不抛 = 本地兜底成功（Kafka 未启用）
        cacheManager.writeAudit(rec);

        ArgumentCaptor<String> sqlCap = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<Object[]> argsCap = ArgumentCaptor.forClass(Object[].class);
        org.mockito.Mockito.verify(jdbc).update(sqlCap.capture(), argsCap.capture());

        // 1) SQL 命中 V172 新表且 schema 限定
        String sql = sqlCap.getValue();
        assertTrue(sql.contains("ecos_ontology.ecos_function_audit"),
                "INSERT 必须命中 V172 新表且带 schema 限定，实际 SQL = " + sql);

        // 2) 列清单显式（禁 SELECT *）：function_name / caller_id / trace_id 均在列清单内
        assertTrue(sql.contains("function_name"), "列清单必须含 function_name");
        assertTrue(sql.contains("caller_id"), "列清单必须含 caller_id");
        assertTrue(sql.contains("trace_id"), "列清单必须含 trace_id");
        assertTrue(sql.contains("function_id"), "列清单必须含 function_id");
        assertTrue(sql.contains("object_type"), "列清单必须含 object_type（W80 修正命名，替代旧 entity_name）");

        // 3) 参数绑定：按 SQL 占位符顺序取值
        Object[] args = argsCap.getValue();
        // SQL 占位顺序：id, function_id, function_name, expression, object_type, params_json,
        //              result_summary, duration_ms, is_cache_hit, caller_id, trace_id,
        //              status, error_message, create_time(NOW), update_time(NOW),
        //              create_by, update_by, is_deleted, version_no, domain
        // 对应 args 长度 = 18（去掉两个 NOW()）
        assertEquals(18, args.length, "参数个数应匹配 SQL 18 个占位");
        // id = UUID（36 字符）
        assertTrue(args[0] instanceof String && ((String) args[0]).length() == 36,
                "id 应为应用侧 UUID（36 字符），实际 = " + args[0]);
        assertEquals("fid-1", args[1], "function_id 参数应绑定 fid-1");
        assertEquals("fn_x", args[2], "function_name 参数应绑定 fn_x（非 null，W80 归因修正）");
        assertEquals("SUM(amount) FROM t WHERE period='2026-07'", args[3]);
        assertEquals("Invoice", args[4], "object_type 参数应绑定 Invoice");
        assertEquals("42.0", args[6], "result_summary 绑定");
        assertEquals(12, args[7], "duration_ms 绑定");
        assertEquals(0, args[8], "is_cache_hit 应为 0（boolean=false→0）");
        assertEquals("alice", args[9], "caller_id 参数应绑定 alice");
        assertEquals("tr-1", args[10], "trace_id 参数应绑定 tr-1");
        assertEquals("SUCCESS", args[11], "status 参数");

        // 关键：function_name 列参数非 null（W80 旧 bug 就是 function_name 错位导致的审计行丧失归因）
        assertNotNull(args[2], "function_name 参数不能为 null");
    }

    @Test
    @DisplayName("W80 — 事件侧 expression 截断 ≤512：buildFunctionExecEvent 已内化在 writeAudit；此处仅校验 Kafka 通道 null 时仍走本地")
    void writeAudit_nullEventBus_stillFallsBackToJdbc() {
        when(jdbc.update(anyString(), any(Object[].class))).thenReturn(1);
        FunctionAuditRecord rec = FunctionAuditRecord.builder()
                .functionName("fn_x")
                .expression("x")
                .callerId("alice")
                .traceId("tr-1")
                .status("SUCCESS")
                .build();
        assertNotNull(cacheManager); // 哨兵：构造不受 eventBus=null 影响
        cacheManager.writeAudit(rec);
        org.mockito.Mockito.verify(jdbc).update(anyString(), any(Object[].class));
    }
}
