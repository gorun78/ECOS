package com.chinacreator.gzcm.engine.data.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.never;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.chinacreator.gzcm.common.exception.BusinessException;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * F02-05（详细设计-02）五层载体登记 + 唯一写通道 验收测试。
 *
 * <p>覆盖设计验收用例（判定性单测，不触库）：</p>
 * <ul>
 *   <li>{@code WriteChannelRejectsUnlistedColumnTest} — 裸列不在 carrier 白名单 → 拒</li>
 *   <li>{@code NonOlapControlWriteGuardTest} — 未登记为 write-channel 白名单表的 carrier → 拒（MC04 guard）</li>
 *   <li>{@code IdempotentWriteReplayTest} — 同 idempotencyKey 已有历史（COUNT>0）→ replay=true 不再写</li>
 * </ul>
 */
class BusinessDomainWriteServiceTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final BusinessDomainWriteService svc = new BusinessDomainWriteService(jdbc);

    private BusinessDomainWriteService.WriteChannelRequest req(String carrier, String key,
                                                              List<Map<String, Object>> rows) {
        return new BusinessDomainWriteService.WriteChannelRequest("CURATED", carrier, key, "t-1", rows);
    }

    @Test
    @DisplayName("WriteChannelRejectsUnlistedColumnTest — 白名单外裸 JSON 列 → 拒")
    void unlistedColumn_rejected() {
        // stage 表白名单无 raw_json 列
        Map<String, Object> row = Map.of(
                "project_id", "p-1",
                "department_id", "d-1",
                "period", "2025-07",
                "stage", "DELIVERY",
                "fact_type", "REVENUE",
                "raw_json", "{}");
        BusinessException e = assertThrows(BusinessException.class, () ->
                svc.writeBusinessDomainRows(req("ecos_dw.ecos_biz_stage_fact", "k-col", List.of(row))));
        assertNotNull(e.getMessage());
        assertTrue(e.getMessage().contains("raw_json"), "应指名列名; 实际 " + e.getMessage());
        verify(jdbc, never()).batchUpdate(anyString(), (List<Object[]>) any());
    }

    @Test
    @DisplayName("NonOlapControlWriteGuardTest — 未登记 DW 表 carrier → 拒（fail-loud，不写）")
    @SuppressWarnings("unchecked")
    void unknownCarrier_rejected() {
        // 库内合法 SQL 表但不在 write-channel 白名单
        Map<String, Object> row = Map.of("project_id", "p-1");
        BusinessException e = assertThrows(BusinessException.class, () ->
                svc.writeBusinessDomainRows(req("ecos_dw.ecos_biz_project_attribution_evil", "k-gu", List.of(row))));
        assertEquals(400, e.getErrorCode(), "应 400 业务异常（非静默通过）");
        assertTrue(e.getMessage().contains("白名单"), "应明示白名单语义; 实际 " + e.getMessage());
        verify(jdbc, never()).queryForObject(anyString(), any(Class.class), any());
        verify(jdbc, never()).batchUpdate(anyString(), (List<Object[]>) any());
    }

    @Test
    @DisplayName("IdempotentWriteReplayTest — 同 idempotencyKey 已有 hits>0 → replay=true，不写第二行")
    @SuppressWarnings("unchecked")
    void idempotentReplay_noRewrite() {
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any())).thenReturn(3);
        Map<String, Object> row = Map.of("project_id", "p-1");
        BusinessDomainWriteService.WriteResult res = svc.writeBusinessDomainRows(
                req("ecos_dw.ecos_biz_project_attribution", "k-idem", List.of(row)));
        assertTrue(res.idempotentReplay(), "应标记 replay=true（历史 hits=3）");
        assertEquals("k-idem", res.idempotencyKey());
        assertEquals(3, res.written());
        verify(jdbc, times(1)).queryForObject(anyString(), eq(Integer.class), any());
        verify(jdbc, never()).batchUpdate(anyString(), (List<Object[]>) any());
    }
}
