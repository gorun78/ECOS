package com.chinacreator.gzcm.engine.ontology.service;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import com.chinacreator.gzcm.common.event.KafkaTopics;
import com.chinacreator.gzcm.common.event.OntologyPublishedEvent;
import com.chinacreator.gzcm.runtime.eventbus.EventBusService;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyVersionSaveDTO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyVersionPreviousDiffVO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyVersionVO;
import com.chinacreator.gzcm.engine.ontology.model.OntologyEntity;
import com.chinacreator.gzcm.engine.ontology.model.OntologyProperty;
import com.chinacreator.gzcm.engine.ontology.model.OntologyRelationship;
import com.chinacreator.gzcm.engine.ontology.model.OntologyRule;
import com.chinacreator.gzcm.engine.ontology.model.OntologyAction;
import com.chinacreator.gzcm.engine.ontology.model.OntologyDomain;
import com.chinacreator.gzcm.engine.ontology.model.OntologyVersion;
import com.chinacreator.gzcm.engine.ontology.repository.OntologyRepository;
import com.chinacreator.gzcm.engine.ontology.repository.OntologyRuleRepository;
import com.chinacreator.gzcm.engine.ontology.repository.OntologyDomainRepository;
import com.chinacreator.gzcm.engine.ontology.repository.OntologyVersionRepository;
import com.chinacreator.gzcm.engine.ontology.repository.OntologyMappingStore;

/**
 * 版本业务服务 — Snapshot / Publish / Rollback / Diff
 */
@Service
public class OntologyVersionService {

    private static final Logger log = LoggerFactory.getLogger(OntologyVersionService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final OntologyVersionRepository versionRepository;
    private final OntologyRepository ontologyRepository;
    private final OntologyProposalService proposalService;
    /** 发布事件内存路径（同 JVM {@code @EventListener} 消费）。 */
    private final ApplicationEventPublisher appEventPublisher;
    /** 发布事件 Kafka/总线路径（跨 JVM 路由）；runtime-event 未装配时为空。 */
    private final Optional<EventBusService> eventBus;

    public OntologyVersionService(OntologyVersionRepository versionRepository,
                                   OntologyRepository ontologyRepository,
                                   OntologyProposalService proposalService,
                                   ApplicationEventPublisher appEventPublisher,
                                   Optional<EventBusService> eventBus) {
        this.versionRepository = versionRepository;
        this.ontologyRepository = ontologyRepository;
        this.proposalService = proposalService;
        this.appEventPublisher = appEventPublisher;
        this.eventBus = eventBus;
    }

    /** 版本主键：库内最大后缀自增（重启安全）；方法级加锁避免并发取到同一序号。 */
    private synchronized String nextId() { return versionRepository.nextId(); }

    public List<Map<String, Object>> listVersions(String ontologyId) {
        return versionRepository.findByOntology(ontologyId).stream()
            .map(this::toMap).collect(Collectors.toList());
    }

    /**
     * 强类型 VO 版（T16-2）。
     * <p>旧 {@link #listVersions(String)} 签名保留（Wave31 C1 mock 兼容）。
     */
    public List<OntologyVersionVO> listVersionsVO(String ontologyId) {
        return versionRepository.findByOntology(ontologyId).stream()
            .map(this::toVO).collect(Collectors.toList());
    }

    public Map<String, Object> getVersion(String ontologyId, String versionId) {
        return versionRepository.findById(versionId).map(this::toMap).orElse(null);
    }

    /**
     * 强类型 VO 版（T16-2）；不存在返回 null。
     */
    public OntologyVersionVO getVersionVO(String ontologyId, String versionId) {
        return versionRepository.findById(versionId).map(this::toVO).orElse(null);
    }

    /**
     * 创建新版本 Draft：从当前 ontology 生成完整快照
     */
    public Map<String, Object> createVersion(String ontologyId, Map<String, Object> body) {
        // 1. 计算新版本号
        String nextVersion = computeNextVersion(ontologyId);
        // 2. 生成 snapshot
        String snapshot = generateSnapshot(ontologyId);
        // 3. 持久化
        OntologyVersion ver = new OntologyVersion();
        ver.setId(nextId());
        ver.setOntologyId(ontologyId);
        ver.setVersionNo(nextVersion);
        ver.setStatus("Draft");
        ver.setSnapshot(snapshot);
        ver.setChangeLog(String.valueOf(body.getOrDefault("changeLog", "")));
        ver.setPublisher(String.valueOf(body.getOrDefault("publisher", "")));
        versionRepository.insert(ver);
        log.info("Version created: {} v{} for ontology {}", ver.getId(), nextVersion, ontologyId);
        return toMap(ver);
    }

    /**
     * 强类型 VO 版（T16-2）；DTO 承载 changeLog / publisher（均可选）。
     */
    public OntologyVersionVO createVersionVO(String ontologyId, OntologyVersionSaveDTO dto) {
        String nextVersion = computeNextVersion(ontologyId);
        String snapshot = generateSnapshot(ontologyId);
        OntologyVersion ver = new OntologyVersion();
        ver.setId(nextId());
        ver.setOntologyId(ontologyId);
        ver.setVersionNo(nextVersion);
        ver.setStatus("Draft");
        ver.setSnapshot(snapshot);
        ver.setChangeLog(dto.getChangeLog() != null ? dto.getChangeLog() : "");
        ver.setPublisher(dto.getPublisher() != null ? dto.getPublisher() : "");
        versionRepository.insert(ver);
        log.info("Version created (VO): {} v{} for ontology {}", ver.getId(), nextVersion, ontologyId);
        return toVO(ver);
    }

    /**
     * 发布版本：Draft → Published
     *
     * <p>PMO-50 T4：发布成功后经 {@link #fanOutOntologyPublished(String, String, String)}
     * 事件双发，驱动 kb 侧 KG 同步（buszhi 金·I → dccheng 水·K）。
     */
    public Map<String, Object> publishVersion(String ontologyId, String versionId) {
        OntologyVersion ver = versionRepository.findById(versionId)
            .orElseThrow(() -> new IllegalArgumentException("ONT-001: Version '" + versionId + "' not found"));
        if ("Published".equals(ver.getStatus())) {
            throw new IllegalStateException("ONT-006: Version '" + versionId + "' is already Published");
        }
        versionRepository.updateStatus(versionId, "Published");
        fanOutOntologyPublished(ontologyId, ver.getVersionNo(), ver.getPublisher());
        return versionRepository.findById(versionId).map(this::toMap).orElse(null);
    }

    /**
     * 强类型 VO 版（T16-2）；语义与旧版一致（Illustrated，含 PMO-50 T4 发布事件双发）。
     */
    public OntologyVersionVO publishVersionVO(String ontologyId, String versionId) {
        OntologyVersion ver = versionRepository.findById(versionId)
            .orElseThrow(() -> new IllegalArgumentException("ONT-001: Version '" + versionId + "' not found"));
        if ("Published".equals(ver.getStatus())) {
            throw new IllegalStateException("ONT-006: Version '" + versionId + "' is already Published");
        }
        versionRepository.updateStatus(versionId, "Published");
        fanOutOntologyPublished(ontologyId, ver.getVersionNo(), ver.getPublisher());
        return versionRepository.findById(versionId).map(this::toVO).orElse(null);
    }

    /**
     * 发布事件双发（PMO-50 T4）— 必须覆盖全部发布路径，否则 kb 侧 KG 同步永不触发。
     *
     * <ol>
     *   <li>Spring 内存事件 {@link ApplicationEventPublisher#publishEvent} — 同 JVM
     *       {@code EcosOntologyEventConsumer#onOntologyPublished} 的 {@code @EventListener} 路径；</li>
     *   <li>Kafka 事件总线 {@link EventBusService#publish} — 跨 JVM 路由（{@code ecos.event.kafka.enabled=true}
     *       时入 Kafka）。
     *       注：内存 fallback 下本路径被包成 {@code MemoryEnvelopeEvent}、无对应监听者，属预期无害重复。</li>
     * </ol>
     *
     * <p>发布状态已提交，事件下发失败仅记日志，不阻塞主流程；载荷只含版本标识，不含本体业务文本。
     *
     * <p>PMO-B2（缺陷 D7 修复）：{@code entityCodes / relationshipCodes} 不再使用硬编码空列表占位，
     * 改为从本体引擎自有表 {@code ecos_ontology_entity} / {@code ecos_ontology_relationship}
     * 按 ontologyId 实查填充，供 kb 侧比对增量清单计算 CREATE / UPDATE / DEPRECATE（方案 §2.2）。
     * 本体确实无对象时返回空列表（数据事实），查询异常仅记 error 日志并以空列表继续下发。
     */
    private void fanOutOntologyPublished(String ontologyId, String versionNo, String actor) {
        try {
            OntologyPublishedEvent evt = OntologyPublishedEvent.of(
                    ontologyId,
                    versionNo,
                    loadEntityCodes(ontologyId),
                    loadRelationshipCodes(ontologyId),
                    actor == null || actor.isBlank() ? "system" : actor);
            if (appEventPublisher != null) {
                appEventPublisher.publishEvent(evt);
            } else {
                log.warn("publishVersion appEventPublisher null, spring-event path skipped ontologyId={}", ontologyId);
            }
            eventBus.ifPresentOrElse(
                    bus -> bus.publish(KafkaTopics.ONTOLOGY_PUBLISHED, evt),
                    () -> log.warn("publishVersion EventBusService unavailable, kafka path skipped topic={} version={}",
                            KafkaTopics.ONTOLOGY_PUBLISHED, versionNo));
        } catch (Exception e) {
            log.error("publishVersion event fan-out failed ontologyId={} version={}", ontologyId, versionNo, e);
        }
    }

    /**
     * 查本体下全部实体 code（发布事件增量清单用）。
     *
     * @param ontologyId 本体 ID
     * @return 去重去空后的实体 code 列表；本体无实体时为空列表
     */
    private List<String> loadEntityCodes(String ontologyId) {
        try {
            return ontologyRepository.findEntitiesByOntology(ontologyId).stream()
                .map(OntologyEntity::getCode)
                .filter(code -> code != null && !code.isBlank())
                .distinct()
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("loadEntityCodes failed ontologyId={}", ontologyId, e);
            return new ArrayList<>();
        }
    }

    /**
     * 查本体下全部关系 code（发布事件增量清单用）。
     *
     * <p>关系经 {@code source_entity_id} 归属本体（{@link OntologyRepository#findRelationshipsByOntology}）。
     *
     * @param ontologyId 本体 ID
     * @return 去重去空后的关系 code 列表；本体无关系时为空列表
     */
    private List<String> loadRelationshipCodes(String ontologyId) {
        try {
            return ontologyRepository.findRelationshipsByOntology(ontologyId).stream()
                .map(row -> row.get("code"))
                .filter(Objects::nonNull)
                .map(String::valueOf)
                .filter(code -> !code.isBlank())
                .distinct()
                .collect(Collectors.toList());
        } catch (Exception e) {
            log.error("loadRelationshipCodes failed ontologyId={}", ontologyId, e);
            return new ArrayList<>();
        }
    }

    /**
     * 回滚：将 published version 状态设为 Deprecated，并创建新 Draft 作为回滚目标
     */
    public Map<String, Object> rollback(String ontologyId, String versionId) {
        OntologyVersion ver = versionRepository.findById(versionId)
            .orElseThrow(() -> new IllegalArgumentException("ONT-001: Version '" + versionId + "' not found"));
        versionRepository.updateStatus(versionId, "Deprecated");
        // 基于此版本的 snapshot 创建新 Draft
        OntologyVersion rollback = new OntologyVersion();
        String nextVersion = computeNextVersion(ontologyId);
        rollback.setId(nextId());
        rollback.setOntologyId(ontologyId);
        rollback.setVersionNo(nextVersion + "-rollback");
        rollback.setStatus("Draft");
        rollback.setSnapshot(ver.getSnapshot());
        rollback.setChangeLog("Rollback from v" + ver.getVersionNo());
        versionRepository.insert(rollback);
        log.info("Rollback: {} → {} (new draft)", versionId, rollback.getId());
        return toMap(rollback);
    }

    /**
     * 强类型 VO 版（T16-2）。
     */
    public OntologyVersionVO rollbackVO(String ontologyId, String versionId) {
        OntologyVersion ver = versionRepository.findById(versionId)
            .orElseThrow(() -> new IllegalArgumentException("ONT-001: Version '" + versionId + "' not found"));
        versionRepository.updateStatus(versionId, "Deprecated");
        OntologyVersion rollback = new OntologyVersion();
        String nextVersion = computeNextVersion(ontologyId);
        rollback.setId(nextId());
        rollback.setOntologyId(ontologyId);
        rollback.setVersionNo(nextVersion + "-rollback");
        rollback.setStatus("Draft");
        rollback.setSnapshot(ver.getSnapshot());
        rollback.setChangeLog("Rollback from v" + ver.getVersionNo());
        versionRepository.insert(rollback);
        log.info("Rollback (VO): {} → {} (new draft)", versionId, rollback.getId());
        return toVO(rollback);
    }

    /**
     * 废弃版本
     */
    public Map<String, Object> deprecate(String ontologyId, String versionId) {
        versionRepository.updateStatus(versionId, "Deprecated");
        return versionRepository.findById(versionId).map(this::toMap).orElse(null);
    }

    /**
     * 强类型 VO 版（T16-2）。
     */
    public OntologyVersionVO deprecateVO(String ontologyId, String versionId) {
        versionRepository.updateStatus(versionId, "Deprecated");
        return versionRepository.findById(versionId).map(this::toVO).orElse(null);
    }

    /**
     * Diff 两个版本
     */
    public Map<String, Object> diff(String ontologyId, String v1, String v2) {
        OntologyVersion ver1 = versionRepository.findById(v1)
            .orElseThrow(() -> new IllegalArgumentException("ONT-001: Version '" + v1 + "' not found"));
        OntologyVersion ver2 = versionRepository.findById(v2)
            .orElseThrow(() -> new IllegalArgumentException("ONT-001: Version '" + v2 + "' not found"));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("version1", ver1.getVersionNo());
        result.put("version2", ver2.getVersionNo());
        result.put("snapshot1", safeParseJson(ver1.getSnapshot()));
        result.put("snapshot2", safeParseJson(ver2.getSnapshot()));
        return result;
    }

    /**
     * 强类型 VO 版（T16-2）。
     *
     * <p>返回 {@link OntologyVersionVO} 的 version1/version2/snapshot1/snapshot2 字段，
     * 与既有 Map 行为（{@code diff} 端点输出）等价。
     */
    public OntologyVersionVO diffVO(String ontologyId, String v1, String v2) {
        OntologyVersion ver1 = versionRepository.findById(v1)
            .orElseThrow(() -> new IllegalArgumentException("ONT-001: Version '" + v1 + "' not found"));
        OntologyVersion ver2 = versionRepository.findById(v2)
            .orElseThrow(() -> new IllegalArgumentException("ONT-001: Version '" + v2 + "' not found"));
        OntologyVersionVO vo = new OntologyVersionVO();
        vo.setVersion1(ver1.getVersionNo());
        vo.setVersion2(ver2.getVersionNo());
        vo.setSnapshot1(safeParseJson(ver1.getSnapshot()));
        vo.setSnapshot2(safeParseJson(ver2.getSnapshot()));
        return vo;
    }

    // ── 简化端点支持方法 ──────────────────────────────────────

    /**
     * 列出所有版本（跨全部 ontology）
     */
    public List<Map<String, Object>> listAllVersions() {
        return versionRepository.findAll().stream()
            .map(this::toMap).collect(Collectors.toList());
    }

    /**
     * 强类型 VO 版（T16-5，简化端点 listAll）；旧 {@link #listAllVersions()} 签名保留（Wave31 C1 mock 兼容）。
     */
    public List<OntologyVersionVO> listAllVersionsVO() {
        return versionRepository.findAll().stream()
            .map(this::toVO).collect(Collectors.toList());
    }

    /**
     * 按 ID 获取版本（不依赖 ontologyId）
     */
    public Map<String, Object> getVersionById(String id) {
        return versionRepository.findById(id).map(this::toMap).orElse(null);
    }

    /**
     * 强类型 VO 版（T16-5，简化端点 get）；不存在返回 null。
     */
    public OntologyVersionVO getVersionVOById(String id) {
        return versionRepository.findById(id).map(this::toVO).orElse(null);
    }

    /**
     * 与前一版本 diff（按创建时间排序，取当前版本的前一个）
     */
    public Map<String, Object> diffWithPrevious(String versionId) {
        OntologyVersion current = versionRepository.findById(versionId)
            .orElseThrow(() -> new IllegalArgumentException("ONT-001: Version '" + versionId + "' not found"));
        Optional<OntologyVersion> previous = versionRepository.findPreviousVersion(
            current.getOntologyId(), current.getCreatedAt());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("currentVersion", current.getVersionNo());
        result.put("currentSnapshot", safeParseJson(current.getSnapshot()));
        if (previous.isPresent()) {
            OntologyVersion prev = previous.get();
            result.put("previousVersion", prev.getVersionNo());
            result.put("previousSnapshot", safeParseJson(prev.getSnapshot()));
        } else {
            result.put("previousVersion", null);
            result.put("previousSnapshot", null);
        }
        return result;
    }

    /**
     * 强类型 VO 版（T16-5，简化端点 diffWithPrevious）。
     * <p>语义与旧 {@link #diffWithPrevious(String)} 一致；
     * {@code previousVersion / previousSnapshot} 不存在时不填充
     * （VO {@code NON_NULL} 省略，与既有 Map put null 前端 undefined 行为一致）。
     */
    public OntologyVersionPreviousDiffVO diffWithPreviousVO(String versionId) {
        OntologyVersion current = versionRepository.findById(versionId)
            .orElseThrow(() -> new IllegalArgumentException("ONT-001: Version '" + versionId + "' not found"));
        Optional<OntologyVersion> previous = versionRepository.findPreviousVersion(
            current.getOntologyId(), current.getCreatedAt());

        OntologyVersionPreviousDiffVO vo = new OntologyVersionPreviousDiffVO();
        vo.setCurrentVersion(current.getVersionNo());
        vo.setCurrentSnapshot(safeParseJson(current.getSnapshot()));
        if (previous.isPresent()) {
            OntologyVersion prev = previous.get();
            vo.setPreviousVersion(prev.getVersionNo());
            vo.setPreviousSnapshot(safeParseJson(prev.getSnapshot()));
        }
        return vo;
    }

    /**
     * 按 ID 发布版本（不依赖 ontologyId）
     */
    public Map<String, Object> publishVersionById(String versionId) {
        OntologyVersion ver = versionRepository.findById(versionId)
            .orElseThrow(() -> new IllegalArgumentException("ONT-001: Version '" + versionId + "' not found"));
        if ("Published".equals(ver.getStatus())) {
            throw new IllegalStateException("ONT-006: Version '" + versionId + "' is already Published");
        }
        versionRepository.updateStatus(versionId, "Published");
        fanOutOntologyPublished(ver.getOntologyId(), ver.getVersionNo(), ver.getPublisher());
        return versionRepository.findById(versionId).map(this::toMap).orElse(null);
    }

    /**
     * 强类型 VO 版（T16-5，简化端点 publish）；语义与旧 {@link #publishVersionById(String)} 一致。
     */
    public OntologyVersionVO publishVersionVOById(String versionId) {
        OntologyVersion ver = versionRepository.findById(versionId)
            .orElseThrow(() -> new IllegalArgumentException("ONT-001: Version '" + versionId + "' not found"));
        if ("Published".equals(ver.getStatus())) {
            throw new IllegalStateException("ONT-006: Version '" + versionId + "' is already Published");
        }
        versionRepository.updateStatus(versionId, "Published");
        fanOutOntologyPublished(ver.getOntologyId(), ver.getVersionNo(), ver.getPublisher());
        return versionRepository.findById(versionId).map(this::toVO).orElse(null);
    }

    // ── PMO-28 提案联动: 审批通过→自动创建版本并发布 ─────────

    /**
     * 提案联动发布（PMO-28 T3）。
     *
     * <p>1. 把提案状态改为 APPROVED（乐观锁保护）。</p>
     * <p>2. 创建新 Draft 版本 — 从当前 ontology 产生完整快照。</p>
     * <p>3. 自动 publish Draft 版本，回填 proposal.version_id。</p>
     *
     * <p>调用方需要先把提案状态从 DRAFT/PENDING → PENDING（如果还不是），
     * 然后调用本方法审批 + 发布。</p>
     *
     * @param ontologyId     本体 ID
     * @param proposalId     提案 ID
     * @param expectedVersion 客户端持有的提案乐观锁版本号
     * @param publisher      发布人（同时作为 reviewer）
     * @return 含 versionId + versionNo 的发布结果
     */
    public Map<String, Object> publishFromProposal(String ontologyId, String proposalId,
                                                    Integer expectedVersion, String publisher) {
        // 1. 改提案 APPROVED（乐观锁）
        int updated = proposalService.optimisticTransition(
            proposalId, "APPROVED", publisher, null, expectedVersion);
        if (updated == 0) {
            throw new IllegalStateException("ONT-409: OPTIMISTIC_LOCK_CONFLICT — proposal version mismatch or not found");
        }
        // 2. 创建 Draft 版本
        java.util.Map<String, Object> newVer = createVersion(ontologyId,
            java.util.Map.of("changeLog", "auto-published from proposal " + proposalId,
                    "publisher", publisher));
        // 3. 发布
        Map<String, Object> published = publishVersion(ontologyId, newVer.get("id").toString());
        log.info("Proposal {} linked to version {}", proposalId, newVer.get("id"));
        return published;
    }

    // ── 内部方法 ──────────────────────────────────────

    @SuppressWarnings("unchecked")
    private String generateSnapshot(String ontologyId) {
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("entities", ontologyRepository.findEntitiesByOntology(ontologyId).stream()
            .map(this::entityToSnapshot).collect(Collectors.toList()));
        // 收集所有 entity IDs
        List<String> entityIds = ontologyRepository.findEntitiesByOntology(ontologyId).stream()
            .map(OntologyEntity::getId).collect(Collectors.toList());
        List<Map<String, Object>> allProps = new ArrayList<>();
        List<Map<String, Object>> allRels = new ArrayList<>();
        List<Map<String, Object>> allActs = new ArrayList<>();
        for (String eid : entityIds) {
            allProps.addAll(ontologyRepository.findPropertiesByEntity(eid).stream()
                .map(this::propToSnapshot).collect(Collectors.toList()));
            allRels.addAll(ontologyRepository.findRelationshipsByEntity(eid).stream()
                .map(this::relToSnapshot).collect(Collectors.toList()));
            allActs.addAll(ontologyRepository.findActionsByEntity(eid).stream()
                .map(this::actionToSnapshot).collect(Collectors.toList()));
        }
        snapshot.put("properties", allProps);
        snapshot.put("relationships", allRels);
        snapshot.put("actions", allActs);
        // rules are loaded by a separate ruleRepository if available
        try {
            return MAPPER.writeValueAsString(snapshot);
        } catch (Exception e) {
            log.error("Failed to serialize snapshot", e);
            return "{}";
        }
    }

    /**
     * 计算该本体的下一个版本号：取全部版本（含 Draft / Published）中的最大版本号，patch 位递增。
     *
     * <p>不可只依据 Published 版本 —— 存在未发布的 Draft 版本时会重复生成同一版本号，
     * 撞唯一约束 {@code (ontology_id, version_no)}，导致提案执行失败。
     *
     * @param ontologyId 本体 ID
     * @return 下一个版本号，如 {@code 1.0.1}；无历史版本时返回 {@code 1.0.0}
     */
    private String computeNextVersion(String ontologyId) {
        return versionRepository.findByOntology(ontologyId).stream()
            .map(OntologyVersion::getVersionNo)
            .filter(no -> no != null && no.matches("\\d+\\.\\d+\\.\\d+"))
            .max(Comparator.comparingLong(this::versionWeight))
            .map(no -> {
                String[] parts = no.split("\\.");
                return parts[0] + "." + parts[1] + "." + (Integer.parseInt(parts[2]) + 1);
            })
            .orElse("1.0.0");
    }

    /** 版本号排序权重（major/minor/patch 加权，用于取最大值） */
    private long versionWeight(String versionNo) {
        String[] parts = versionNo.split("\\.");
        return Long.parseLong(parts[0]) * 1_000_000L
            + Long.parseLong(parts[1]) * 1_000L
            + Long.parseLong(parts[2]);
    }

    private Map<String, Object> entityToSnapshot(OntologyEntity e) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", e.getId()); m.put("code", e.getCode()); m.put("name", e.getName());
        m.put("entityType", e.getEntityType()); m.put("description", e.getDescription());
        return m;
    }

    private Map<String, Object> propToSnapshot(OntologyProperty p) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", p.getId()); m.put("entityId", p.getEntityId());
        m.put("code", p.getCode()); m.put("name", p.getName());
        m.put("propertyType", p.getPropertyType()); m.put("requiredFlag", p.getRequiredFlag());
        m.put("enumValues", p.getEnumValues()); m.put("defaultValue", p.getDefaultValue());
        return m;
    }

    private Map<String, Object> relToSnapshot(OntologyRelationship r) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", r.getId()); m.put("sourceEntityId", r.getSourceEntityId());
        m.put("targetEntityId", r.getTargetEntityId()); m.put("code", r.getCode());
        m.put("relationshipType", r.getRelationshipType());
        return m;
    }

    private Map<String, Object> actionToSnapshot(OntologyAction a) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", a.getId()); m.put("entityId", a.getEntityId());
        m.put("name", a.getName()); m.put("actionType", a.getActionType());
        m.put("strategy", a.getStrategy()); m.put("status", a.getStatus());
        return m;
    }

    @SuppressWarnings("unchecked")
    private Object safeParseJson(String json) {
        try {
            return MAPPER.readValue(json, Map.class);
        } catch (Exception e) {
            return json;
        }
    }

    private Map<String, Object> toMap(OntologyVersion v) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", v.getId());
        m.put("ontologyId", v.getOntologyId());
        m.put("versionNo", v.getVersionNo());
        m.put("status", v.getStatus());
        m.put("snapshot", safeParseJson(v.getSnapshot()));
        m.put("changeLog", v.getChangeLog());
        m.put("publisher", v.getPublisher());
        m.put("publishedAt", v.getPublishedAt() != null ? v.getPublishedAt().toString() : null);
        m.put("createdAt", v.getCreatedAt() != null ? v.getCreatedAt().toString() : null);
        return m;
    }

    /**
     * 转换到 {@link OntologyVersionVO}（T16-2 强类型）。
     * <p>{@code snapshot} 复用 safeParseJson 行为以保持契约一致。
     */
    private OntologyVersionVO toVO(OntologyVersion v) {
        OntologyVersionVO vo = new OntologyVersionVO();
        vo.setId(v.getId());
        vo.setOntologyId(v.getOntologyId());
        vo.setVersionNo(v.getVersionNo());
        vo.setStatus(v.getStatus());
        vo.setSnapshot(safeParseJson(v.getSnapshot()));
        vo.setChangeLog(v.getChangeLog());
        vo.setPublisher(v.getPublisher());
        vo.setPublishedAt(v.getPublishedAt() != null ? v.getPublishedAt().toString() : null);
        vo.setCreatedAt(v.getCreatedAt() != null ? v.getCreatedAt().toString() : null);
        return vo;
    }
}
