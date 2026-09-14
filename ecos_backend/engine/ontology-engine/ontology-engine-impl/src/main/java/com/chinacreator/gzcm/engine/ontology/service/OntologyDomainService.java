package com.chinacreator.gzcm.engine.ontology.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyDomainReassignDTO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyDomainSaveDTO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyDomainVO;
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

    /**
     * 列出全部领域的强类型 VO 版本（T16-2）。
     * <p>旧 {@link #listDomains()} 签名保留（Wave31 C1 mock 兼容）。
     */
    public List<OntologyDomainVO> listDomainsVO() {
        return repository.findAll().stream().map(this::toVO).collect(Collectors.toList());
    }

    public Map<String, Object> getDomain(String domainCode) {
        return repository.findByCode(domainCode)
            .map(this::toMap)
            .orElse(null);
    }

    /**
     * 强类型 VO 版（T16-2）；不存在返回 null（与旧 Map 版语义一致）。
     */
    public OntologyDomainVO getDomainVO(String domainCode) {
        return repository.findByCode(domainCode).map(this::toVO).orElse(null);
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

    /**
     * 强类型 VO 版（T16-2）；新增时 code/name 必填（由 DTO 字段语义承载）。
     */
    public OntologyDomainVO createDomain(OntologyDomainSaveDTO dto) {
        String code = dto.getCode() != null ? dto.getCode() : "";
        if (repository.existsByCode(code)) {
            throw new IllegalArgumentException("ONT-009: Domain code '" + code + "' already exists");
        }
        OntologyDomain dom = new OntologyDomain();
        dom.setId(nextId());
        dom.setCode(code);
        dom.setName(dto.getName() != null ? dto.getName() : "");
        dom.setOwner(dto.getOwner() != null ? dto.getOwner() : "");
        dom.setDescription(dto.getDescription() != null ? dto.getDescription() : "");
        dom.setStatus("Draft");
        dom.setSortOrder(1);
        repository.insert(dom);
        log.info("Domain created (VO): {} [{}]", dom.getId(), dom.getCode());
        return toVO(dom);
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

    /**
     * 强类型 VO 版（T16-2）；用于 {@code OntologyDomainApiController.updateDomain} 路径。
     * 与 {@link #updateDomain(String, OntologyDomainSaveDTO)} 同语义，
     * 不同泛型返回类型便于 Controller 显式选用。
     */
    public Optional<OntologyDomainVO> updateDomainVO(String domainCode, OntologyDomainSaveDTO dto) {
        return repository.findByCode(domainCode).map(existing -> {
            String code = dto.getCode();
            String name = dto.getName();
            String owner = dto.getOwner();
            String description = dto.getDescription();
            String status = dto.getStatus();
            repository.update(existing.getId(), code, name, owner, description, status);
            return repository.findByCode(domainCode).map(this::toVO).orElse(null);
        });
    }

    /**
     * 强类型 VO 版（T16-2）；DTO 字段 null 表示不动（与旧 Map 版语义一致）。
     */
    public Optional<OntologyDomainVO> updateDomain(String domainCode, OntologyDomainSaveDTO dto) {
        return repository.findByCode(domainCode).map(existing -> {
            String code = dto.getCode();
            String name = dto.getName();
            String owner = dto.getOwner();
            String description = dto.getDescription();
            String status = dto.getStatus();
            repository.update(existing.getId(), code, name, owner, description, status);
            return repository.findByCode(domainCode).map(this::toVO).orElse(null);
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

    /** 强类型 VO 版（T16-2）。 */
    public OntologyDomainVO publishDomainVO(String domainCode) {
        return setAndToVO(domainCode, "Published");
    }

    public Map<String, Object> deprecateDomain(String domainCode) {
        return setStatus(domainCode, "Deprecated");
    }

    /** 强类型 VO 版（T16-2）。 */
    public OntologyDomainVO deprecateDomainVO(String domainCode) {
        return setAndToVO(domainCode, "Deprecated");
    }

    /**
     * 搜索域（按 keyword 模糊匹配 name/code/description）。
     * <p>PMO E8 端点 {@code GET /api/v1/ecos/domains/search} 后端支撑。</p>
     *
     * @param keyword 关键字
     * @param limit   最大返回条数
     * @return 命中的域列表
     */
    public List<Map<String, Object>> searchDomains(String keyword, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return repository.searchDomains(keyword, safeLimit).stream()
            .map(this::toMap)
            .collect(Collectors.toList());
    }

    /**
     * 强类型 VO 版（T16-2）。
     */
    public List<OntologyDomainVO> searchDomainsVO(String keyword, int limit) {
        int safeLimit = Math.max(1, Math.min(limit, 200));
        return repository.searchDomains(keyword, safeLimit).stream()
            .map(this::toVO)
            .collect(Collectors.toList());
    }

    private Map<String, Object> setStatus(String domainCode, String status) {
        OntologyDomain dom = repository.findByCode(domainCode)
            .orElseThrow(() -> new IllegalArgumentException("ONT-008: Domain '" + domainCode + "' not found"));
        repository.update(dom.getId(), null, null, null, null, status);
        return repository.findByCode(domainCode).map(this::toMap).orElse(null);
    }

    private OntologyDomainVO setAndToVO(String domainCode, String status) {
        OntologyDomain dom = repository.findByCode(domainCode)
            .orElseThrow(() -> new IllegalArgumentException("ONT-008: Domain '" + domainCode + "' not found"));
        repository.update(dom.getId(), null, null, null, null, status);
        return repository.findByCode(domainCode).map(this::toVO).orElse(null);
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

    /**
     * 强类型 VO 版（T16-2）；DTO 兼容 domainCode / domainId 双字段。
     * <p>与旧 Map 版语义一致：domainCode 优先，否则回退 domainId。
     */
    public Map<String, Object> reassignEntityDomainVO(String entityId, OntologyDomainReassignDTO dto) {
        String domainCode = dto.getDomainCode() != null && !dto.getDomainCode().isBlank()
            ? dto.getDomainCode()
            : (dto.getDomainId() != null ? dto.getDomainId() : "");
        return reassignEntityDomain(entityId, domainCode);
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

    /**
     * 转换到 {@link OntologyDomainVO}（T16-2 强类型）。
     */
    private OntologyDomainVO toVO(OntologyDomain d) {
        OntologyDomainVO v = new OntologyDomainVO();
        v.setId(d.getId());
        v.setCode(d.getCode());
        v.setName(d.getName());
        v.setOwner(d.getOwner());
        v.setDescription(d.getDescription());
        v.setStatus(d.getStatus());
        v.setSortOrder(d.getSortOrder());
        v.setCreatedAt(d.getCreatedAt() != null ? d.getCreatedAt().toString() : null);
        v.setUpdatedAt(d.getUpdatedAt() != null ? d.getUpdatedAt().toString() : null);
        return v;
    }
}
