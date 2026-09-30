package com.chinacreator.gzcm.engine.data.writechannel;

import com.chinacreator.gzcm.common.event.PipelineEvent;
import com.chinacreator.gzcm.engine.data.dto.WriteChannelRequest;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static com.chinacreator.gzcm.engine.data.writechannel.WriteChannelTestFixtures.baseRequest;
import static com.chinacreator.gzcm.engine.data.writechannel.WriteChannelTestFixtures.wire;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F02-05 验收 · 第 3 条 / MC02 —— 列类型白名单（裸 JSON 列参与 WHERE 即拒）。
 *
 * <p>断言：写通道 {@code whereColumns} 里若含 JSON 类型列（类型判）或
 * {@code *_json} 语义列（命名判），一律拒（{@code ECOS-DATA-052}），
 * 不登记载体、不登记幂等台账、不发 PipelineEvent。
 *
 * <p>纯 Mockito：{@link DwCarrierCatalogMapper#columnTypeOf} 提供列类型元数据。
 */
class WriteChannelRejectsUnlistedColumnTest {

    @Test
    @DisplayName("裸 JSON 列参与 WHERE（类型判）→ 拒 ECOS-DATA-052")
    void bareJsonColumnTypeRejected() {
        WriteChannelTestFixtures.Ctx ctx = wire(mock(EventBusService.class));
        when(ctx.catalogMapper.columnTypeOf(eq("ecos_dw"), eq("fact_order"), eq("order_ext")))
            .thenReturn("json");
        ctx.buildService();

        WriteChannelRequest req = baseRequest();
        req.setWhereColumns(List.of("order_ext"));

        IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> ctx.service.write(req));
        assertTrue(e.getMessage().contains("ECOS-DATA-052"), "应命中 MC02 错误码: " + e.getMessage());

        verify(ctx.dataResourceRepository, never()).insert(any());
        verify(ctx.idempotencyMapper, never()).insert(any());
        verify(ctx.eventPublisher, never()).publishEvent(any(PipelineEvent.class));
    }

    @Test
    @DisplayName("*_json 语义列参与 WHERE（命名判）→ 拒 ECOS-DATA-052，不触达元数据")
    void jsonSemanticColumnNamingRejected() {
        WriteChannelTestFixtures.Ctx ctx = wire(mock(EventBusService.class));
        ctx.buildService();

        WriteChannelRequest req = baseRequest();
        req.setWhereColumns(List.of("payload_json"));

        IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> ctx.service.write(req));
        assertTrue(e.getMessage().contains("ECOS-DATA-052"));

        // 命名判先命中，不应触达元数据查询
        verify(ctx.catalogMapper, never()).columnTypeOf(anyString(), anyString(), anyString());
        verify(ctx.dataResourceRepository, never()).insert(any());
    }

    @Test
    @DisplayName("白名单内类型列参与 WHERE → 放行 + 登记载体/幂等台账（对照）")
    void whitelistedColumnAllowed() {
        WriteChannelTestFixtures.Ctx ctx = wire(mock(EventBusService.class));
        when(ctx.catalogMapper.columnTypeOf(eq("ecos_dw"), eq("fact_order"), eq("order_id")))
            .thenReturn("character varying");
        when(ctx.catalogMapper.columnTypeOf(eq("ecos_dw"), eq("fact_order"), eq("gmt_create")))
            .thenReturn("timestamp without time zone");
        ctx.buildService();

        WriteChannelRequest req = baseRequest();
        req.setWhereColumns(List.of("order_id", "gmt_create"));

        var result = ctx.service.write(req);
        assertFalse(result.isIdempotentReplay());
        assertTrue(result.isDeclared());
        verify(ctx.dataResourceRepository).insert(any());
        verify(ctx.idempotencyMapper).insert(any());
        verify(ctx.eventPublisher).publishEvent(any(PipelineEvent.class));
    }
}
