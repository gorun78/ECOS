package com.chinacreator.gzcm.engine.data.writechannel;

import com.chinacreator.gzcm.engine.data.dto.WriteChannelRequest;
import com.chinacreator.gzcm.engine.data.repository.DataResourceRepository;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 写通道单测公共装配（纯 Mockito，无 Spring 容器 / 无 DB）。
 *
 * <p>约定：先 {@link #wire(EventBusService)} 拿 mock 集，测试按需 stub 行为，
 * 最后 {@link Ctx#buildService()} 装配被测 {@link DwWriteChannelService}。
 * OlapEngineResolver 走包级无参构造（dw.olap.engine 未启用），业务 schema 传 ecos_dw。
 */
final class WriteChannelTestFixtures {

    private WriteChannelTestFixtures() {}

    static final class Ctx {
        DwWriteChannelIdempotencyMapper idempotencyMapper;
        DwCarrierCatalogMapper catalogMapper;
        DataResourceRepository dataResourceRepository;
        DwSchemaQualifier schemaQualifier;
        OlapEngineResolver olapEngineResolver;
        EventBusService eventBus;
        ApplicationEventPublisher eventPublisher;
        DwWriteChannelService service;

        /** 装配被测服务（在 stub 完 mock 行为之后调用）。 */
        @SuppressWarnings("unchecked")
        DwWriteChannelService buildService() {
            ObjectProvider<EventBusService> busProvider = (ObjectProvider<EventBusService>)
                    mock(ObjectProvider.class);
            when(busProvider.getIfAvailable()).thenReturn(eventBus);
            service = new DwWriteChannelService(
                    idempotencyMapper, catalogMapper, dataResourceRepository,
                    schemaQualifier, olapEngineResolver, busProvider, eventPublisher);
            return service;
        }
    }

    /** 装配默认 mock。测试可继续 against ctx.xxx 做 when/verify。 */
    static Ctx wire(EventBusService eventBus) {
        Ctx ctx = new Ctx();
        ctx.idempotencyMapper = mock(DwWriteChannelIdempotencyMapper.class);
        ctx.catalogMapper = mock(DwCarrierCatalogMapper.class);
        ctx.dataResourceRepository = mock(DataResourceRepository.class);
        ctx.schemaQualifier = new DwSchemaQualifier("ecos_dw");
        ctx.olapEngineResolver = new OlapEngineResolver(); // 未配置 OLAP 引擎
        ctx.eventBus = eventBus;
        ctx.eventPublisher = mock(ApplicationEventPublisher.class);
        return ctx;
    }

    /** 基础入参：五层之一 + 业务域 schema.table + traceId + idempotencyKey 齐全。 */
    static WriteChannelRequest baseRequest() {
        WriteChannelRequest req = new WriteChannelRequest();
        req.setLayer("CURATED");
        req.setCarrierRef("ecos_dw.fact_order");
        req.setTraceId("trace-1");
        req.setIdempotencyKey("idem-1");
        req.setOperator("tester");
        return req;
    }
}
