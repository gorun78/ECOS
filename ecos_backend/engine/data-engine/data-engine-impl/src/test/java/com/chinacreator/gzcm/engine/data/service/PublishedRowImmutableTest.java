package com.chinacreator.gzcm.engine.data.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.jdbc.core.JdbcTemplate;

import com.chinacreator.gzcm.common.exception.BusinessException;

/**
 * F02-06-4（详细设计-02，DATA-01 P0）—— PUBLISHED 事实行不可改业务字段（更正=新行）。
 *
 * <p>{@code overwriteFactRow} 先按 {@code id} 读 {@code dq_status}：若为
 * {@code PUBLISHED}，<b>必须</b>抛 {@code 409 + ECOS-DATA-021}（更正只能走新行 + 旧行
 * {@code is_active=NULL} 的导入链路），且不发起任何 UPDATE。本测试用 Mockito 打桩
 * {@link JdbcTemplate} 做运行时判定。</p>
 */
@DisplayName("F02-06-4 PUBLISHED 事实行不可再改业务字段（ECOS-DATA-021，P0）")
class PublishedRowImmutableTest {

    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final BusinessFactService svc = new BusinessFactService(jdbc);

    @Test
    @DisplayName("PublishedRowImmutableTest — PUBLISHED 行 overwrite → 409 + ECOS-DATA-021，不发起 UPDATE")
    void publishedRow_overwrite_throws409() {
        when(jdbc.queryForObject(ArgumentMatchers.contains("SELECT dq_status"),
                eq(String.class), ArgumentMatchers.any(Object[].class))).thenReturn("PUBLISHED");

        BusinessException e = assertThrows(BusinessException.class, () ->
                svc.overwriteFactRow("stage", "id-1", Map.of("amount", "2000.00")));

        assertEquals(409, e.getErrorCode(), "PUBLISHED 行更正必须 409 而非静默成功");
        assertNotNull(e.getMessage());
        assertTrue(e.getMessage().contains("ECOS-DATA-021"),
                "必须落 ECOS-DATA-021 错误码; 实际 " + e.getMessage());
        // 关键：不得发起任何 UPDATE（否则就是违规改写已发布行）
        verify(jdbc, never()).update(anyString(), ArgumentMatchers.any(Object[].class));
    }

    @Test
    @DisplayName("PublishedRowImmutableTest（对比）— PENDING/PASSED 行可更新（不误伤）")
    void pendingRow_overwrite_allowed() {
        when(jdbc.queryForObject(ArgumentMatchers.contains("SELECT dq_status"),
                eq(String.class), ArgumentMatchers.any(Object[].class))).thenReturn("PASSED");
        when(jdbc.update(anyString(), ArgumentMatchers.any(Object[].class))).thenReturn(1);

        int updated = svc.overwriteFactRow("stage", "id-2", Map.of("amount", "2000.00"));
        assertEquals(1, updated, "PASSED 行允许改写业务字段（更正路径未误伤 PUBLISHED-only 守卫）");
    }
}
