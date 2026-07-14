package com.chinacreator.gzcm.dccheng.ontology;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 版本业务服务 — Snapshot / Publish / Rollback / Diff
 */
@Service
public class OntologyVersionService {

    private static final Logger log = LoggerFactory.getLogger(OntologyVersionService.class);
    private static final AtomicInteger ID_SEQ = new AtomicInteger(5000);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final OntologyVersionRepository versionRepository;
    private final OntologyRepository ontologyRepository;

    public OntologyVersionService(OntologyVersionRepository versionRepository,
                                   OntologyRepository ontologyRepository) {
        this.versionRepository = versionRepository;
        this.ontologyRepository = ontologyRepository;
    }

    private String nextId() { return "ver" + ID_SEQ.incrementAndGet(); }

    public List<Map<String, Object>> listVersions(String ontologyId) {
        return versionRepository.findByOntology(ontologyId).stream()
            .map(this::toMap).collect(Collectors.toList());
    }

    public Map<String, Object> getVersion(String ontologyId, String versionId) {
        return versionRepository.findById(versionId).map(this::toMap).orElse(null);
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
     * 发布版本：Draft → Published
     */
    public Map<String, Object> publishVersion(String ontologyId, String versionId) {
        OntologyVersion ver = versionRepository.findById(versionId)
            .orElseThrow(() -> new IllegalArgumentException("ONT-001: Version '" + versionId + "' not found"));
        if ("Published".equals(ver.getStatus())) {
            throw new IllegalStateException("ONT-006: Version '" + versionId + "' is already Published");
        }
        versionRepository.updateStatus(versionId, "Published");
        return versionRepository.findById(versionId).map(this::toMap).orElse(null);
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
     * 废弃版本
     */
    public Map<String, Object> deprecate(String ontologyId, String versionId) {
        versionRepository.updateStatus(versionId, "Deprecated");
        return versionRepository.findById(versionId).map(this::toMap).orElse(null);
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

    // ── 简化端点支持方法 ──────────────────────────────────────

    /**
     * 列出所有版本（跨全部 ontology）
     */
    public List<Map<String, Object>> listAllVersions() {
        return versionRepository.findAll().stream()
            .map(this::toMap).collect(Collectors.toList());
    }

    /**
     * 按 ID 获取版本（不依赖 ontologyId）
     */
    public Map<String, Object> getVersionById(String id) {
        return versionRepository.findById(id).map(this::toMap).orElse(null);
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
     * 按 ID 发布版本（不依赖 ontologyId）
     */
    public Map<String, Object> publishVersionById(String versionId) {
        OntologyVersion ver = versionRepository.findById(versionId)
            .orElseThrow(() -> new IllegalArgumentException("ONT-001: Version '" + versionId + "' not found"));
        if ("Published".equals(ver.getStatus())) {
            throw new IllegalStateException("ONT-006: Version '" + versionId + "' is already Published");
        }
        versionRepository.updateStatus(versionId, "Published");
        return versionRepository.findById(versionId).map(this::toMap).orElse(null);
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

    private String computeNextVersion(String ontologyId) {
        var latest = versionRepository.findLatestPublished(ontologyId);
        if (latest.isEmpty()) return "1.0.0";
        String[] parts = latest.get().getVersionNo().split("\\.");
        int major = Integer.parseInt(parts[0]);
        int minor = Integer.parseInt(parts[1]);
        int patch = Integer.parseInt(parts[2]);
        patch++;
        return major + "." + minor + "." + patch;
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
}
