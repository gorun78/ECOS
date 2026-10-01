package com.chinacreator.gzcm.engine.ontology.audit;

import com.chinacreator.gzcm.engine.ontology.engine.FunctionAuditRecord;
import com.chinacreator.gzcm.engine.ontology.engine.FunctionCacheManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * W82a — Kafka 不可用 → 本地兜底不拒绝。
 *
 * <p>锁定：{@code eventBus.publish} 抛 {@link IllegalStateException} 时，
 * {@link FunctionCacheManager#writeAudit(FunctionAuditRecord)} 应<b>落到本地兜底 INSERT</b>
 * 且不向调用方传播异常（"Kafka down = fall back, still succeeds"）。</p>
 */
@ExtendWith(MockitoExtension.class)
class FunctionAuditKafkaFallbackTest {

    @Test
    @DisplayName("W82a — Kafka publish 失败 → 本地兜底 INSERT 命中, 且不抛异常")
    void kafkaPublishFailure_fallsBackToLocalJdbc_noThrow() {
        EventbusFakes fakes = new EventbusFakes();
        // Kafka 抛异常
        org.mockito.Mockito.doThrow(new IllegalStateException("broker unreachable"))
                .when(fakes.eventBus).publish(anyString(), any());
        // 本地兜底成功
        when(fakes.jdbc.update(anyString(), any(Object[].class))).thenReturn(1);

        FunctionCacheManager mgr = new FunctionCacheManager(fakes.jdbc, fakes.eventBus, "ecos_ontology");

        FunctionAuditRecord rec = FunctionAuditRecord.builder()
                .functionId("fid-1")
                .functionName("fn_x")
                .expression("SUM(amount) FROM t")
                .objectType("Invoice")
                .resultSummary("42.0")
                .durationMs(8L)
                .cacheHit(true)
                .callerId("alice")
                .traceId("tr-1")
                .status("SUCCESS")
                .build();

        // 不抛 = 兜底成功（断言本身即"no exception propagated"）
        mgr.writeAudit(rec);

        // 本地兜底确实执行了 INSERT
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(fakes.jdbc).update(sql.capture(), any(Object[].class));
        assertTrue(sql.getValue().contains("ecos_ontology.ecos_function_audit"),
                "本地兜底应落 V172 新表，实际 = " + sql.getValue());
    }

    /** 捆绑 mock，便于在断言中复用同一实例。 */
    private static class EventbusFakes {
        final EventBusService eventBus = mock(EventBusService.class);
        final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    }
}
