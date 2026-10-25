package com.chinacreator.gzcm.engine.data.writechannel;

import com.chinacreator.gzcm.common.data.DataDomainErrorCode;
import com.chinacreator.gzcm.common.data.model.DataResource;
import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.common.event.PipelineEvent;
import com.chinacreator.gzcm.engine.data.dto.WriteChannelRequest;
import com.chinacreator.gzcm.engine.data.dto.WriteChannelResult;
import com.chinacreator.gzcm.engine.data.model.DataLayer;
import com.chinacreator.gzcm.engine.data.repository.DataResourceRepository;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * F02-05 第 3 条 / C.3.6 / ADR-14 · 业务域数据<b>唯一写通道</b>编排服务。
 *
 * <p>铁律 §0.6 / ADR-14：业务域生产数据落 production schema 的业务写入
 * 统一走网关 {@code POST /api/v1/datanet/write-channel}（data-engine 承载），
 * 不再由各业务通道各自散写。端点仅经 gateway :8080 可达
 * （route-manifest owner = datanet）。
 *
 * <p>本服务的数据域职责（编排层，Driver 归 runtime-access，铁律 §2.5）：
 * <ol>
 *   <li><b>入参红线</b>：{@code layer / carrierRef / traceId / idempotencyKey} 缺一即拒
 *       （{@code ECOS-DATA-061}），layer 必须为五层之一（DataLayer 枚举）；</li>
 *   <li><b>幂等</b>（同键重放不产生第二行）：首查台账
 *       {@link DwWriteChannelIdempotencyMapper#findByKey}，命中即回放首次
 *       {@code writeId}，不重复登记、不重复发事件；首次才登记台账行
 *       （唯一索引 {@code uniq_dw_wc_idempotency_key} 兜并发竞态）；</li>
 *   <li><b>MC06</b>：{@link DwSchemaQualifier} 解析 schema 限定名，判定归属域
 *       （业务 / 控制）；</li>
 *   <li><b>MC04</b>：{@link OlapEngineResolver} 守卫 OLAP 双例外
 *       （控制域禁入 OLAP / 仅 CURATED·APPLICATION / 单引擎二选一）；</li>
 *   <li><b>MC02</b>：{@link ColumnTypeWhitelistGuard} 列类型白名单
 *       （裸 JSON 列参与 WHERE 即拒，元数据经 {@link DwCarrierCatalogMapper}）；</li>
 *   <li><b>载体登记</b>：落 {@code td_data_resource}（layer / layer_bucket /
 *       carrier_ref / storage_kind，V260 新列，IR03 只加）；</li>
 *   <li><b>ST06</b>：审计事件发 Kafka topic {@code ecos.audit}（失败不阻塞主路径）；</li>
 *   <li><b>PipelineEvent</b>：发 {@link PipelineEvent.EventType#WRITE_CHANNEL_WRITE}
 *       供下游（血缘 / 质量 / 同步）消费。</li>
 * </ol>
 *
 * <p>所有 ID 应用侧生成（MC01：DDL 禁 {@code gen_random_uuid()}）。
 */
@Service("ecosDwWriteChannelService")
public class DwWriteChannelService {

    private static final Logger log = LoggerFactory.getLogger(DwWriteChannelService.class);

    private final DwWriteChannelIdempotencyMapper idempotencyMapper;
    private final DwCarrierCatalogMapper catalogMapper;
    private final DataResourceRepository dataResourceRepository;
    private final DwSchemaQualifier schemaQualifier;
    private final OlapEngineResolver olapEngineResolver;
    private final ObjectProvider<EventBusService> eventBusProvider;
    private final ApplicationEventPublisher eventPublisher;

    public DwWriteChannelService(DwWriteChannelIdempotencyMapper idempotencyMapper,
                                 DwCarrierCatalogMapper catalogMapper,
                                 DataResourceRepository dataResourceRepository,
                                 DwSchemaQualifier schemaQualifier,
                                 OlapEngineResolver olapEngineResolver,
                                 ObjectProvider<EventBusService> eventBusProvider,
                                 ApplicationEventPublisher eventPublisher) {
        this.idempotencyMapper = idempotencyMapper;
        this.catalogMapper = catalogMapper;
        this.dataResourceRepository = dataResourceRepository;
        this.schemaQualifier = schemaQualifier;
        this.olapEngineResolver = olapEngineResolver;
        this.eventBusProvider = eventBusProvider;
        this.eventPublisher = eventPublisher;
    }

    /**
     * 执行一次业务域写入登记（唯一入口）。
     *
     * @param request 强类型入参（layer/carrierRef/traceId/idempotencyKey 必填）
     * @return 写通道结果（writeId + declared 存在性 + 是否幂等重放）
     * @throws IllegalArgumentException 入参缺失 / 非法层 / 限定名残缺（ECOS-DATA-061，属主端点折 400）
     * @throws IllegalStateException    MC02/MC04 守卫违规（ECOS-DATA-062/063，属主端点折 403）
     */
    @Transactional
    public WriteChannelResult write(WriteChannelRequest request) {
        // ── 1) 入参红线（缺一即拒）─────────────────────────────────
        if (request == null) {
            throw new IllegalArgumentException(DataDomainErrorCode.ECOS_DATA_061 + ": 写通道入参为空");
        }
        String layer = require("layer", request.getLayer());
        String carrierRef = require("carrierRef", request.getCarrierRef());
        String traceId = require("traceId", request.getTraceId());
        String idempotencyKey = require("idempotencyKey", request.getIdempotencyKey());
        DataLayer dl = parseLayer(layer);

        // ── 2) 幂等：同键重放→回放首次 writeId（不产生第二行）────────
        DwWriteChannelIdempotency existing = idempotencyMapper.findByKey(idempotencyKey);
        if (existing != null) {
            log.info("写通道幂等命中：key={} 回放 writeId={}（不产生第二行）", idempotencyKey, existing.getWriteId());
            WriteChannelResult replay = new WriteChannelResult();
            replay.setWriteId(existing.getWriteId());
            replay.setIdempotencyKey(idempotencyKey);
            replay.setTraceId(traceId);
            replay.setLayer(dl.name());
            replay.setTargetTable(existing.getTargetTable());
            replay.setDeclared(declared(dl.name()));
            replay.setIdempotentReplay(true);
            return replay;
        }

        // ── 3) MC06 限定名解析 + 域分类 ─────────────────────────────
        DwSchemaQualifier.Qualified target = schemaQualifier.resolve(carrierRef);
        DwSchemaQualifier.Domain domain = schemaQualifier.classifyDomain(target.getSchema());

        // ── 4) MC04 OLAP 双例外守卫 ─────────────────────────────────
        olapEngineResolver.guard(dl.name(), domain, request.getStorageKind());

        // ── 5) MC02 列类型白名单（WHERE 列禁裸 JSON）────────────────
        ColumnTypeWhitelistGuard columnGuard = new ColumnTypeWhitelistGuard(
                (qualifiedTable, column) ->
                        catalogMapper.columnTypeOf(target.getSchema(), target.getTable(), column));
        columnGuard.guardWhereColumns(target.getQualifiedName(), request.getWhereColumns());

        // ── 6) 载体登记（td_data_resource，V260 新列；幂等首行才执行）──
        String operator = (request.getOperator() == null || request.getOperator().isBlank())
                ? "system" : request.getOperator();
        DataResource carrier = new DataResource();
        carrier.setResourceId(newAppId());
        carrier.setResourceName(target.getQualifiedName());
        String normalizedKind = normalizeKind(request.getStorageKind());
        carrier.setResourceType(normalizedKind != null
                && OlapEngineResolver.OLAP_KINDS.contains(normalizedKind) ? "OLAP_TABLE" : "TABLE");
        carrier.setSourcePath(target.getQualifiedName());
        carrier.setStatus("ACTIVE");
        carrier.setLayer(dl.name());
        carrier.setLayerBucket(request.getLayerBucket());
        carrier.setCarrierRef(carrierRef);
        carrier.setStorageKind(normalizedKind);
        carrier.setCreateBy(operator);
        dataResourceRepository.insert(carrier);

        // ── 7) 幂等台账登记（MC01 应用侧 UUID；唯一索引兜并发）───────
        String writeId = newAppId();
        DwWriteChannelIdempotency record = new DwWriteChannelIdempotency();
        record.setId(newAppId());
        record.setIdempotencyKey(idempotencyKey);
        record.setTraceId(traceId);
        record.setLayer(dl.name());
        record.setLayerBucket(request.getLayerBucket());
        record.setCarrierRef(carrierRef);
        record.setStorageKind(normalizeKind(request.getStorageKind()));
        record.setTargetTable(target.getQualifiedName());
        record.setWriteId(writeId);
        record.setCreateBy(operator);
        record.setCreateTime(LocalDateTime.now());
        record.setUpdateBy(operator);
        record.setUpdateTime(LocalDateTime.now());
        record.setVersionNo("1");
        record.setIsDeleted(0);
        record.setDomain((request.getDomain() == null || request.getDomain().isBlank())
                ? "default" : request.getDomain());
        idempotencyMapper.insert(record);

        // ── 8) ST06 审计事件（ecos.audit，失败不阻塞）────────────────
        publishAudit(writeId, idempotencyKey, traceId, dl.name(), carrierRef,
                target.getQualifiedName(), operator);

        // ── 9) PipelineEvent（下游血缘/质量/同步消费）────────────────
        publishPipelineEvent(writeId, idempotencyKey, traceId, dl.name(),
                target.getQualifiedName(), operator, request);

        WriteChannelResult result = new WriteChannelResult();
        result.setWriteId(writeId);
        result.setIdempotencyKey(idempotencyKey);
        result.setTraceId(traceId);
        result.setLayer(dl.name());
        result.setTargetTable(target.getQualifiedName());
        result.setDeclared(true);
        result.setIdempotentReplay(false);

        log.info("写通道写入成功：writeId={} layer={} target={} carrier={} operator={}",
                writeId, dl.name(), target.getQualifiedName(), carrierRef, operator);
        return result;
    }

    // ── 内部 ──────────────────────────────────────────────────

    /** 层载体存在性（D-9 口径：carrier_ref 非空才算登记了载体）。 */
    private boolean declared(String layer) {
        return dataResourceRepository.countRegisteredCarriersByLayer(layer) > 0;
    }

    private void publishAudit(String writeId, String idempotencyKey, String traceId,
                              String layer, String carrierRef, String targetTable,
                              String operator) {
        try {
            EventBusService bus = eventBusProvider.getIfAvailable();
            if (bus == null) {
                log.warn("EventBusService 不可用，写通道审计未发（可重放补偿）: writeId={}", writeId);
                return;
            }
            Map<String, Object> audit = new HashMap<>();
            audit.put("action", "write-channel.write");
            audit.put("write_id", writeId);
            audit.put("idempotency_key", idempotencyKey);
            audit.put("trace_id", traceId);
            audit.put("layer", layer);
            audit.put("carrier_ref", carrierRef);
            audit.put("target_table", targetTable);
            audit.put("operator", operator);
            bus.publish(KafkaTopics.AUDIT, audit);
        } catch (Exception e) {
            log.warn("写通道审计 Kafka 发布失败（不阻塞主路径）: writeId={}, err={}", writeId, e.getMessage());
        }
    }

    private void publishPipelineEvent(String writeId, String idempotencyKey, String traceId,
                                      String layer, String targetTable, String operator,
                                      WriteChannelRequest request) {
        try {
            Map<String, Object> metadata = new HashMap<>();
            metadata.put("writeId", writeId);
            metadata.put("idempotencyKey", idempotencyKey);
            metadata.put("traceId", traceId);
            metadata.put("carrierRef", request.getCarrierRef());
            metadata.put("storageKind", request.getStorageKind());
            metadata.put("operator", operator);
            PipelineEvent event = new PipelineEvent();
            event.setEventType(PipelineEvent.EventType.WRITE_CHANNEL_WRITE);
            event.withPipelineId(writeId)
                    .withMetadata(metadata)
                    .fromModule("data-engine/write-channel");
            event.setDataSourceName(targetTable);
            eventPublisher.publishEvent(event);
        } catch (Exception e) {
            log.warn("PipelineEvent(WRITE_CHANNEL_WRITE) 发布失败（不阻塞主路径）: writeId={}, err={}",
                    writeId, e.getMessage());
        }
    }

    private static DataLayer parseLayer(String raw) {
        try {
            return DataLayer.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            // #142/B4-3e-69（收口 D-B4-73）：非法 layer 是 400 入参错误，收口前借用
            // ECOS-DATA-053（表义＝OLAP 降级，503 段）——降级码被入参腿占用。现取 D.1
            // 单源表 400 段新登的机读码，码值经常量而非第二处字面量。
            throw new IllegalArgumentException(
                    DataDomainErrorCode.ECOS_DATA_061 + ": 非法 layer（须为 SOURCE/RAW/CURATED/SEMANTIC/APPLICATION）: " + raw);
        }
    }

    private static String normalizeKind(String kind) {
        return (kind == null || kind.isBlank()) ? null : kind.trim().toUpperCase();
    }

    /** MC01：应用侧 UUID（DDL 禁 gen_random_uuid()）。 */
    private static String newAppId() {
        return UUID.randomUUID().toString();
    }

    private static String require(String field, String value) {
        if (value == null || value.isBlank()) {
            // #142/B4-3e-69（收口 D-B4-73）：050 收口前不在 D.1 单源表内（表外码），
            // 与非法 layer／限定名残缺同属「写通道入参非法」语义，统一归 061。
            throw new IllegalArgumentException(
                    DataDomainErrorCode.ECOS_DATA_061 + ": 写通道入参 " + field + " 必填（F02-05 第 3 条）");
        }
        return value.trim();
    }
}
