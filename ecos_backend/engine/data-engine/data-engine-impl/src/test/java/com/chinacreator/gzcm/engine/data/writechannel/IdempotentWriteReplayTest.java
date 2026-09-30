package com.chinacreator.gzcm.engine.data.writechannel;

import com.chinacreator.gzcm.common.data.model.DataResource;
import com.chinacreator.gzcm.common.event.PipelineEvent;
import com.chinacreator.gzcm.engine.data.dto.WriteChannelRequest;
import com.chinacreator.gzcm.engine.data.dto.WriteChannelResult;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.chinacreator.gzcm.engine.data.writechannel.WriteChannelTestFixtures.baseRequest;
import static com.chinacreator.gzcm.engine.data.writechannel.WriteChannelTestFixtures.wire;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * F02-05 验收 · 第 3 条 —— 幂等（同 {@code idempotencyKey} 重放不产生第二行）。
 *
 * <p>断言：
 * <ul>
 *   <li>首次写入：登记载体 1 次 + 登记幂等台账 1 次 + 发 PipelineEvent 1 次，
 *       返回新的 writeId、{@code idempotentReplay=false}；</li>
 *   <li>同键重放：不产生第二行（载体/幂等台账 insert 均不再调用）、不重复发事件，
 *       回放首次 writeId、{@code idempotentReplay=true}；</li>
 *   <li>两个不同 key：各自独立登记（各 1 行，不互相影响）。</li>
 * </ul>
 */
class IdempotentWriteReplayTest {

    @Test
    @DisplayName("首次 → 第二行不产生：同键重放回放首次 writeId")
    void replayDoesNotProduceSecondRow() {
        WriteChannelTestFixtures.Ctx ctx = wire(mock(EventBusService.class));

        // 首次：findByKey 未命中
        when(ctx.idempotencyMapper.findByKey("idem-abc")).thenReturn(null);
        ctx.buildService();

        WriteChannelRequest req = baseRequest();
        req.setIdempotencyKey("idem-abc");

        WriteChannelResult first = ctx.service.write(req);
        assertNotNull(first.getWriteId());
        assertFalse(first.isIdempotentReplay());
        String firstWriteId = first.getWriteId();
        verify(ctx.dataResourceRepository, times(1)).insert(any(DataResource.class));
        verify(ctx.idempotencyMapper, times(1)).insert(any(DwWriteChannelIdempotency.class));
        verify(ctx.eventPublisher, times(1)).publishEvent(any(PipelineEvent.class));

        // ── 重放：同键 → findByKey 命中首次行 ──
        clearInvocations(ctx.dataResourceRepository, ctx.idempotencyMapper, ctx.eventPublisher, ctx.eventBus);
        DwWriteChannelIdempotency existing = new DwWriteChannelIdempotency();
        existing.setId("ledger-row-1");
        existing.setIdempotencyKey("idem-abc");
        existing.setWriteId(firstWriteId); // 与首次一致（同一台账行）
        existing.setLayer("CURATED");
        existing.setTargetTable("ecos_dw.fact_order");
        when(ctx.idempotencyMapper.findByKey("idem-abc")).thenReturn(existing);
        when(ctx.dataResourceRepository.countRegisteredCarriersByLayer("CURATED")).thenReturn(1);

        WriteChannelResult replay = ctx.service.write(req);
        assertTrue(replay.isIdempotentReplay(), "重放必须标记为 idempotentReplay");
        assertEquals(firstWriteId, replay.getWriteId(), "重放必须回放首次 writeId");
        assertTrue(replay.isDeclared());

        // 不产生第二行：载体 / 幂等台账 insert 都不再调用
        verify(ctx.dataResourceRepository, never()).insert(any(DataResource.class));
        verify(ctx.idempotencyMapper, never()).insert(any(DwWriteChannelIdempotency.class));
        // 不重复发事件
        verify(ctx.eventPublisher, never()).publishEvent(any(PipelineEvent.class));
    }

    @Test
    @DisplayName("两个不同 idempotencyKey → 各自独立登记（各 1 行）")
    void distinctKeysEachRegisterOnce() {
        WriteChannelTestFixtures.Ctx ctx = wire(mock(EventBusService.class));
        when(ctx.idempotencyMapper.findByKey(any())).thenReturn(null);
        ctx.buildService();

        WriteChannelRequest r1 = baseRequest();
        r1.setIdempotencyKey("key-1");
        ctx.service.write(r1);

        WriteChannelRequest r2 = baseRequest();
        r2.setIdempotencyKey("key-2");
        ctx.service.write(r2);

        verify(ctx.idempotencyMapper, times(2)).insert(any(DwWriteChannelIdempotency.class));
        verify(ctx.dataResourceRepository, times(2)).insert(any(DataResource.class));
    }

    @Test
    @DisplayName("缺 idempotencyKey → 拒 ECOS-DATA-050（不登记任何行）")
    void missingIdempotencyKeyRejected() {
        WriteChannelTestFixtures.Ctx ctx = wire(mock(EventBusService.class));
        ctx.buildService();

        WriteChannelRequest req = baseRequest();
        req.setIdempotencyKey("");

        IllegalArgumentException e = assertThrows(IllegalArgumentException.class,
            () -> ctx.service.write(req));
        assertTrue(e.getMessage().contains("ECOS-DATA-050"));
        verify(ctx.idempotencyMapper, never()).insert(any());
        verify(ctx.dataResourceRepository, never()).insert(any());
    }
}
