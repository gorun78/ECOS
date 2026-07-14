package com.chinacreator.gzcm.dccheng.ontology;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * 领域业务服务 — Domain CRUD + 生命周期管理
 */
@Service
public class OntologyDomainService {

    private static final Logger log = LoggerFactory.getLogger(OntologyDomainService.class);
    private static final AtomicInteger ID_SEQ = new AtomicInteger(200);

    private final OntologyDomainRepository repository;
    private final OntologyRepository ontologyRepository;

    public OntologyDomainService(OntologyDomainRepository repository, OntologyRepository ontologyRepository) {
        this.repository = repository;
        this.ontologyRepository = ontologyRepository;
    }

    private String nextId() { return "dom" + ID_SEQ.incrementAndGet(); }

    public List<Map<String, Object>> listDomains() {
        return repository.findAll().stream().map(this::toMap).collect(Collectors.toList());
    }

    public Map<String, Object> getDomain(String domainCode) {
        return repository.findByCode(domainCode)
            .map(this::toMap)
            .orElse(null);
    }

    public Map<String, Object> createDomain(Map<String, Object> body) {
        String code = String.valueOf(body.getOrDefault("code", ""));
        if (repository.existsByCode(code)) {
            throw new IllegalArgumentException("ONT-009: Domain code '" + code + "' already exists");
        }
        OntologyDomain dom = new OntologyDomain();
        dom.setId(nextId());
        dom.setCode(code);
        dom.setName(String.valueOf(body.getOrDefault("name", "")));
        dom.setOwner(String.valueOf(body.getOrDefault("owner", "")));
        dom.setDescription(String.valueOf(body.getOrDefault("description", "")));
        dom.setStatus("Draft");
        dom.setSortOrder(1);
        repository.insert(dom);
        log.info("Domain created: {} [{}]", dom.getId(), dom.getCode());
        return toMap(dom);
    }

    public Optional<Map<String, Object>> updateDomain(String domainCode, Map<String, Object> body) {
        return repository.findByCode(domainCode).map(existing -> {
            String code = body.containsKey("code") ? String.valueOf(body.get("code")) : null;
            String name = body.containsKey("name") ? String.valueOf(body.get("name")) : null;
            String owner = body.containsKey("owner") ? String.valueOf(body.get("owner")) : null;
            String description = body.containsKey("description") ? String.valueOf(body.get("description")) : null;
            String status = body.containsKey("status") ? String.valueOf(body.get("status")) : null;
            repository.update(existing.getId(), code, name, owner, description, status);
            return repository.findByCode(domainCode).map(this::toMap).orElse(null);
        });
    }

    public boolean deleteDomain(String domainCode) {
        Optional<OntologyDomain> existing = repository.findByCode(domainCode);
        if (existing.isEmpty()) return false;
        int entityCount = repository.countEntitiesByDomain(domainCode);
        if (entityCount > 0) {
            throw new IllegalStateException("ONT-005: Cannot delete domain '" + domainCode +
                "' — it contains " + entityCount + " entities. Remove them first.");
        }
        repository.delete(existing.get().getId());
        return true;
    }

    public Map<String, Object> publishDomain(String domainCode) {
        return setStatus(domainCode, "Published");
    }

    public Map<String, Object> deprecateDomain(String domainCode) {
        return setStatus(domainCode, "Deprecated");
    }

    private Map<String, Object> setStatus(String domainCode, String status) {
        OntologyDomain dom = repository.findByCode(domainCode)
            .orElseThrow(() -> new IllegalArgumentException("ONT-008: Domain '" + domainCode + "' not found"));
        repository.update(dom.getId(), null, null, null, null, status);
        return repository.findByCode(domainCode).map(this::toMap).orElse(null);
    }

    // ═══════════════ Object → Domain 归属变更 ═══════════════════

    /**
     * 将实体重新归属到指定域。domainCode 可以是域的 code 或 id。
     */
    public Map<String, Object> reassignEntityDomain(String entityId, String domainCode) {
        OntologyDomain dom = repository.findByCode(domainCode)
            .or(() -> repository.findById(domainCode))
            .orElseThrow(() -> new IllegalArgumentException(
                "ONT-008: Domain '" + domainCode + "' not found"));
        ontologyRepository.updateEntityDomain(entityId, dom.getId());
        log.info("Entity {} reassigned to domain {} [{}]", entityId, dom.getId(), dom.getCode());
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("entityId", entityId);
        result.put("domainId", dom.getId());
        result.put("domainCode", dom.getCode());
        result.put("domainName", dom.getName());
        return result;
    }

    private Map<String, Object> toMap(OntologyDomain d) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", d.getId());
        m.put("code", d.getCode());
        m.put("name", d.getName());
        m.put("owner", d.getOwner());
        m.put("description", d.getDescription());
        m.put("status", d.getStatus());
        m.put("sortOrder", d.getSortOrder());
        m.put("createdAt", d.getCreatedAt() != null ? d.getCreatedAt().toString() : null);
        m.put("updatedAt", d.getUpdatedAt() != null ? d.getUpdatedAt().toString() : null);
        return m;
    }
}
