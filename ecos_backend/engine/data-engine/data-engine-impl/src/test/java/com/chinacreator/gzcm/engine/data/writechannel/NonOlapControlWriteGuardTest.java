package com.chinacreator.gzcm.engine.data.writechannel;

import com.chinacreator.gzcm.common.data.model.DataResource;
import com.chinacreator.gzcm.common.event.PipelineEvent;
import com.chinacreator.gzcm.common.exception.DataBridgeException;
import com.chinacreator.gzcm.engine.data.dto.WriteChannelRequest;
import com.chinacreator.gzcm.engine.data.dto.WriteChannelResult;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import org.springframework.beans.factory.ObjectProvider;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.chinacreator.gzcm.engine.data.writechannel.WriteChannelTestFixtures.baseRequest;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F02-05 验收 · 第 4 条 / MC04 —— OLAP 双例外（Doris ∨ ClickHouse 二选一）。
 *
 * <p>断言：
 * <ul>
 *   <li>控制域 schema + OLAP 载体（DORIS）→ <b>拒</b>（控制域禁入 OLAP，{@code ECOS-DATA-054}），
 *       不登记载体/幂等、不发布 PipelineEvent；</li>
 *   <li>业务域 DW/CURATED + OLAP 载体（与 dw.olap.engine 一致）→ 放行（对照）；</li>
 *   <li>OLAP 载体与部署选中引擎不一致（禁双写）→ 拒。</li>
 * </ul>
 *
 * <p>纯 Mockito，无 Spring 容器 / 无 DB。
 */
class NonOlapControlWriteGuardTest {

    @Test
    @DisplayName("控制域 schema 写 OLAP（DORIS 载体）→ MC04 拒，不产生任何写入")
    void controlDomainToOlapRejected() {
        // OLAP 已启用（dw.olap.engine=doris）——即便启用，控制域仍禁入 OLAP
        WriteChannelTestFixtures.Ctx ctx = wireOlapEnabled();
        when(ctx.dataResourceRepository.countRegisteredCarriersByLayer(any())).thenReturn(1);
        ctx.buildService();

        WriteChannelRequest req = WriteChannelTestFixtures.baseRequest();
        req.setCarrierRef("ecos_data.ecos_sys_log"); // 控制域 schema（5 引擎之一）
        req.setStorageKind("DORIS");
        req.setLayer("CURATED");

        IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> ctx.service.write(req));
        assertTrue(e.getMessage().contains("ECOS-DATA-054"), "应命中 MC04 错误码: " + e.getMessage());
        assertTrue(e.getMessage().contains("MC04"), "应明示 MC04 违规");

        // 守卫拒后不得有任何写行为
        verify(ctx.dataResourceRepository, never()).insert(any(DataResource.class));
        verify(ctx.idempotencyMapper, never()).insert(any(DwWriteChannelIdempotency.class));
        verify(ctx.eventPublisher, never()).publishEvent(any(PipelineEvent.class));
    }

    @Test
    @DisplayName("业务域 CURATED + DORIS 载体（dw.olap.engine=doris）→ 放行")
    void businessDomainOlapAllowed() {
        WriteChannelTestFixtures.Ctx ctx = wireOlapEnabled();
        ctx.buildService();

        WriteChannelRequest req = WriteChannelTestFixtures.baseRequest();
        req.setLayer("CURATED");
        req.setCarrierRef("ecos_dw.fact_budget"); // 业务域 schema
        req.setStorageKind("DORIS");

        WriteChannelResult result = ctx.service.write(req);
        assertFalse(result.isIdempotentReplay());
        assertTrue(result.isDeclared());
        assertEquals("ecos_dw.fact_budget", result.getTargetTable());
    }

    @Test
    @DisplayName("dw.olap.engine=clickhouse 时选 DORIS 载体 → 禁双写，拒")
    void engineMismatchRejected() {
        WriteChannelTestFixtures.Ctx ctx = wireOlapEnabled("clickhouse");
        ctx.buildService();

        WriteChannelRequest req = WriteChannelTestFixtures.baseRequest();
        req.setLayer("APPLICATION");
        req.setCarrierRef("ecos_dw.app_metrics");
        req.setStorageKind("DORIS"); // 与部署选中 clickhouse 不一致

        IllegalStateException e = assertThrows(IllegalStateException.class,
            () -> ctx.service.write(req));
        assertTrue(e.getMessage().contains("ECOS-DATA-054"));
        verify(ctx.dataResourceRepository, never()).insert(any(DataResource.class));
    }

    @Test
    @DisplayName("未启用 OLAP（dw.olap.engine 空）任何 OLAP 载体 → 503 不可用（非 403 无权限）")
    void olapNotConfiguredRejected() {
        // 无参 OlapEngineResolver = 未配置
        WriteChannelTestFixtures.Ctx ctx = WriteChannelTestFixtures.wire(mock(EventBusService.class));
        ctx.buildService();

        WriteChannelRequest req = WriteChannelTestFixtures.baseRequest();
        req.setCarrierRef("ecos_dw.app_metrics");
        req.setStorageKind("CLICKHOUSE");

        // #142/B4-3e-43（D-B4-71 属主侧第四批）：本腿断言从「抛 ISE 且带 ECOS-DATA-054」改为
        // 「抛携带 503 的 DataBridgeException 且带 D.1 单源表内语义对应的 ECOS-DATA-053」。
        // 收口前它走属主端点的 catch (IllegalStateException) → forbidden(403)，
        // 与 MC04 不变式违规（真·策略拒绝）在状态行同形，违 §4.7.2「不可用 ≠ 无权限」。
        DataBridgeException e = assertThrows(DataBridgeException.class,
            () -> ctx.service.write(req));
        assertEquals(503, e.getHttpStatus(), "OLAP 未启用＝本部署档不可用，须携带 503: " + e.getHttpStatus());
        assertTrue(e.getMessage().contains("ECOS-DATA-053"),
                "未启用 OLAP 应 fail-close 并带 D.1 表内 OLAP 降级码: " + e.getMessage());
    }

    // ── 配置了 OLAP 引擎的装配（走公开 @Value 构造器注入引擎名）──

    private static WriteChannelTestFixtures.Ctx wireOlapEnabled() {
        return wireOlapEnabled("doris");
    }

    private static WriteChannelTestFixtures.Ctx wireOlapEnabled(String engine) {
        WriteChannelTestFixtures.Ctx ctx = WriteChannelTestFixtures.wire(mock(EventBusService.class));
        // 用公开构造器替换：OlapEngineResolver(@Value("${dw.olap.engine:}") String)
        ctx.olapEngineResolver = new OlapEngineResolver(engine);
        return ctx;
    }
}
