package com.chinacreator.gzcm.engine.ontology.audit;

import com.chinacreator.gzcm.engine.ontology.engine.FunctionAuditRecord;
import com.chinacreator.gzcm.engine.ontology.engine.FunctionCacheManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.dao.DataAccessException;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;

/**
 * W82b — Kafka 与本地兜底双失败 → 拒收。
 *
 * <p>锁定：{@code eventBus.publish} 抛 + {@code jdbc.update} 抛时，
 * {@link FunctionCacheManager#writeAudit(FunctionAuditRecord)} 抛
 * {@link FunctionCacheManager.AuditWriteException}（Controller 映射 500 ECOS-ONTO-061）。</p>
 */
@ExtendWith(MockitoExtension.class)
class FunctionAuditBothFailDenyTest {

    @Test
    @DisplayName("W82b — Kafka + 本地双失败 → 抛 AuditWriteException（双失败拒收）")
    void bothFail_throwsAuditWriteException() {
        EventBusService eventBus = mock(EventBusService.class);
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        doThrow(new IllegalStateException("broker unreachable")).when(eventBus).publish(anyString(), any());
        doThrow(new DataAccessException("pg down") { }).when(jdbc).update(anyString(), any(Object[].class));

        FunctionCacheManager mgr = new FunctionCacheManager(jdbc, eventBus, "ecos_ontology");

        FunctionAuditRecord rec = FunctionAuditRecord.builder()
                .functionId("fid-1")
                .functionName("fn_x")
                .expression("SUM(amount) FROM t")
                .objectType("Invoice")
                .callerId("alice")
                .traceId("tr-1")
                .status("SUCCESS")
                .build();

        FunctionCacheManager.AuditWriteException ex =
                assertThrows(FunctionCacheManager.AuditWriteException.class, () -> mgr.writeAudit(rec));
        assertTrue(ex.getMessage().contains("dual-fail"),
                "异常消息应标注 dual-fail，实际 = " + ex.getMessage());
        // 有 cause（承载底层异常链）
        assertTrue(ex.getCause() != null, "AuditWriteException 应携带底层原因链");
    }
}
