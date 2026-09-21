package com.chinacreator.gzcm.engine.ontology.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.common.exception.BusinessException;
import com.chinacreator.gzcm.common.exception.NotFoundException;
import com.chinacreator.gzcm.engine.ontology.dto.GlossaryBindingDTO;
import com.chinacreator.gzcm.engine.ontology.dto.GlossaryGraphNodeVO;
import com.chinacreator.gzcm.engine.ontology.dto.GlossaryGraphVO;
import com.chinacreator.gzcm.engine.ontology.dto.GlossaryRelationSaveDTO;
import com.chinacreator.gzcm.engine.ontology.dto.GlossaryRelationVO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyGlossarySaveDTO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyGlossaryVO;
import com.chinacreator.gzcm.engine.ontology.glossary.GlossaryEntity;
import com.chinacreator.gzcm.engine.ontology.glossary.GlossaryRelationEntity;
import com.chinacreator.gzcm.engine.ontology.glossary.GlossaryRelationRepository;
import com.chinacreator.gzcm.engine.ontology.glossary.GlossaryRepository;
import com.chinacreator.gzcm.engine.ontology.repository.OntologyRepository;

/**
 * 词条（Wiki）业务服务 — 词条全生命周期 + 词条关系 + 图谱展开。
 *
 * <p>Controller 仅负责接收参数与组装响应，全部业务规则（状态流转合法性、
 * 关系边合法性、图谱逐层展开与上限）收敛在本层。
 */
@Service
public class GlossaryService {

    private static final Logger log = LoggerFactory.getLogger(GlossaryService.class);

    /** 允许的关系边类型（与 glossary_relation_type 字典同源） */
    private static final Set<String> RELATION_TYPES =
        Set.of("ISA", "SYNONYM", "PART_OF", "SEE_ALSO", "CAUSAL", "RELATED");

    /** 图谱展开层数下限 */
    private static final int GRAPH_MIN_DEPTH = 1;

    /** 图谱展开层数上限（防止大图拖垮前端渲染） */
    private static final int GRAPH_MAX_DEPTH = 3;

    private final GlossaryRepository glossaryRepository;
    private final GlossaryRelationRepository relationRepository;
    private final OntologyRepository ontologyRepository;

    public GlossaryService(GlossaryRepository glossaryRepository,
                           GlossaryRelationRepository relationRepository,
                           OntologyRepository ontologyRepository) {
        this.glossaryRepository = glossaryRepository;
        this.relationRepository = relationRepository;
        this.ontologyRepository = ontologyRepository;
    }

    // ═══════════════ 词条 CRUD ═══════════════

    /** 按条件查询词条列表。 */
    public List<OntologyGlossaryVO> listTerms(String domain, String status,
                                              String termType, String objectTypeId,
                                              String keyword) {
        return glossaryRepository.findAll(domain, status, termType, objectTypeId, keyword)
            .stream()
            .map(this::toVO)
            .collect(Collectors.toList());
    }

    /** 创建词条（状态固定起始为 DRAFT）。 */
    public OntologyGlossaryVO createTerm(OntologyGlossarySaveDTO dto) {
        if (dto == null || dto.getName() == null || dto.getName().isBlank()) {
            throw new BusinessException("词条名称不能为空");
        }
        GlossaryEntity entity = new GlossaryEntity();
        entity.setCode(dto.getCode());
        entity.setName(dto.getName().trim());
        entity.setDefinition(dto.getDefinition());
        entity.setDomain(dto.getDomain());
        entity.setOwner(dto.getOwner());
        entity.setStatus("DRAFT");
        entity.setCreatedBy(dto.getCreatedBy());
        entity.setTermType(normalizeTermType(dto.getTermType()));
        entity.setAliases(dto.getAliases());
        entity.setObjectTypeId(dto.getObjectTypeId());
        entity.setParentTermId(dto.getParentTermId());
        entity.setVersion(dto.getVersion() == null ? 1 : dto.getVersion());
        entity.setExamples(dto.getExamples());
        entity.setTags(dto.getTags());

        glossaryRepository.insert(entity);
        log.info("Glossary term created: {} [{}]", entity.getId(), entity.getName());
        // 回读以获得数据库生成的时间戳与规范化后的数组列
        return toVO(glossaryRepository.findById(entity.getId()).orElse(entity));
    }

    /** 更新词条（字段 null 不改；定义变更时自增版本号；状态须走合法流转）。 */
    public OntologyGlossaryVO updateTerm(Long id, OntologyGlossarySaveDTO dto) {
        GlossaryEntity entity = requireTerm(id);

        if (dto.getCode() != null) {
            entity.setCode(dto.getCode());
        }
        if (dto.getName() != null && !dto.getName().isBlank()) {
            entity.setName(dto.getName().trim());
        }
        if (dto.getDefinition() != null) {
            boolean changed = !dto.getDefinition().equals(entity.getDefinition());
            entity.setDefinition(dto.getDefinition());
            if (changed) {
                int current = entity.getVersion() == null ? 1 : entity.getVersion();
                entity.setVersion(current + 1);
            }
        }
        if (dto.getDomain() != null) {
            entity.setDomain(dto.getDomain());
        }
        if (dto.getOwner() != null) {
            entity.setOwner(dto.getOwner());
        }
        if (dto.getCreatedBy() != null) {
            entity.setCreatedBy(dto.getCreatedBy());
        }
        if (dto.getTermType() != null) {
            entity.setTermType(normalizeTermType(dto.getTermType()));
        }
        if (dto.getAliases() != null) {
            entity.setAliases(dto.getAliases());
        }
        if (dto.getObjectTypeId() != null) {
            entity.setObjectTypeId(dto.getObjectTypeId().isBlank() ? null : dto.getObjectTypeId());
        }
        if (dto.getParentTermId() != null) {
            if (dto.getParentTermId().equals(id)) {
                throw new BusinessException("上位词条不能是自身");
            }
            entity.setParentTermId(dto.getParentTermId() == 0L ? null : dto.getParentTermId());
        }
        if (dto.getVersion() != null) {
            entity.setVersion(dto.getVersion());
        }
        if (dto.getExamples() != null) {
            entity.setExamples(dto.getExamples());
        }
        if (dto.getTags() != null) {
            entity.setTags(dto.getTags());
        }

        String newStatus = dto.getStatus();
        if (newStatus != null && !newStatus.equalsIgnoreCase(entity.getStatus())) {
            if (!isValidTransition(entity.getStatus(), newStatus)) {
                throw new BusinessException(
                    "状态流转不允许: " + entity.getStatus() + " → " + newStatus);
            }
            entity.setStatus(newStatus.toUpperCase());
        }

        glossaryRepository.update(entity);
        log.info("Glossary term updated: {} → status={}", id, entity.getStatus());
        return toVO(glossaryRepository.findById(id).orElse(entity));
    }

    /** 删除词条（关系边由外键级联清理）。 */
    public boolean deleteTerm(Long id) {
        int affected = glossaryRepository.deleteById(id);
        if (affected > 0) {
            log.info("Glossary term deleted: {}", id);
        }
        return affected > 0;
    }

    // ═══════════════ 本体实体绑定（T1 本体消费词条）═══════════════

    /**
     * 绑定词条到本体实体 / 解绑 / 设为主术语。
     *
     * <p>{@code objectTypeId} 为空 → 解绑（清空归属并撤下主术语标记）；
     * 非空 → 先校验实体存在（ecos_ontology_entity），再写入绑定；
     * {@code primary=true} 时先把该实体原主术语撤下，保证「一实体至多一主术语」。
     */
    public OntologyGlossaryVO bindTermToEntity(Long termId, GlossaryBindingDTO dto) {
        GlossaryEntity term = requireTerm(termId);
        String objectTypeId = dto == null ? null : dto.getObjectTypeId();

        if (objectTypeId == null || objectTypeId.isBlank()) {
            glossaryRepository.updateBinding(termId, null, false);
            log.info("Glossary term {} unbound from ontology entity", termId);
            return toVO(glossaryRepository.findById(termId).orElse(term));
        }

        String target = objectTypeId.trim();
        if (ontologyRepository.findEntityById(target).isEmpty()) {
            throw NotFoundException.entity("OntologyEntity", target);
        }
        boolean primary = Boolean.TRUE.equals(dto.getPrimary());
        if (primary) {
            glossaryRepository.clearPrimaryForEntity(target, termId);
        }
        glossaryRepository.updateBinding(termId, target, primary);
        log.info("Glossary term {} bound to ontology entity {} (primary={})", termId, target, primary);
        return toVO(glossaryRepository.findById(termId).orElse(term));
    }

    // ═══════════════ 词条关系 ═══════════════

    /** 按条件查询关系边（带两端词条名称）。 */
    public List<GlossaryRelationVO> listRelations(Long fromTermId, Long toTermId,
                                                  String relationType, Long termId) {
        List<GlossaryRelationEntity> edges =
            relationRepository.findAll(fromTermId, toTermId, relationType, termId);
        return toRelationVOs(edges);
    }

    /** 创建关系边（校验两端存在、非自环、类型合法、不重复）。 */
    public GlossaryRelationVO createRelation(GlossaryRelationSaveDTO dto) {
        if (dto == null || dto.getFromTermId() == null || dto.getToTermId() == null) {
            throw new BusinessException("关系两端词条不能为空");
        }
        String type = dto.getRelationType() == null ? "" : dto.getRelationType().toUpperCase();
        if (!RELATION_TYPES.contains(type)) {
            throw new BusinessException("关系类型不合法: " + dto.getRelationType());
        }
        if (dto.getFromTermId().equals(dto.getToTermId())) {
            throw new BusinessException("关系两端不能是同一词条");
        }
        requireTerm(dto.getFromTermId());
        requireTerm(dto.getToTermId());
        if (relationRepository.exists(dto.getFromTermId(), dto.getToTermId(), type)) {
            throw new BusinessException("该关系已存在");
        }

        GlossaryRelationEntity entity = new GlossaryRelationEntity();
        entity.setFromTermId(dto.getFromTermId());
        entity.setToTermId(dto.getToTermId());
        entity.setRelationType(type);
        entity.setWeight(dto.getWeight());
        entity.setDescription(dto.getDescription());
        entity.setCreatedBy(dto.getCreatedBy());

        relationRepository.insert(entity);
        log.info("Glossary relation created: {} -[{}]-> {}",
            entity.getFromTermId(), type, entity.getToTermId());

        GlossaryRelationEntity saved = relationRepository.findById(entity.getId()).orElse(entity);
        return toRelationVOs(List.of(saved)).get(0);
    }

    /** 删除关系边。 */
    public boolean deleteRelation(Long id) {
        int affected = relationRepository.deleteById(id);
        if (affected > 0) {
            log.info("Glossary relation deleted: {}", id);
        }
        return affected > 0;
    }

    // ═══════════════ 图谱展开 ═══════════════

    /**
     * 以指定词条为中心，逐层展开 N 层邻居关系图。
     *
     * <p>深度收敛在 [{@value #GRAPH_MIN_DEPTH}, {@value #GRAPH_MAX_DEPTH}]；
     * 仅返回两端都已纳入节点集合的边，保证前后端节点/边自洽。
     */
    public GlossaryGraphVO buildGraph(Long centerId, Integer depth) {
        GlossaryEntity center = requireTerm(centerId);
        int maxDepth = depth == null ? GRAPH_MIN_DEPTH
            : Math.max(GRAPH_MIN_DEPTH, Math.min(depth, GRAPH_MAX_DEPTH));

        // BFS：记录每个节点距中心的跳数
        Map<Long, Integer> hops = new LinkedHashMap<>();
        hops.put(centerId, 0);
        Set<Long> edgeIds = new LinkedHashSet<>();
        List<GlossaryRelationEntity> edges = new ArrayList<>();
        List<Long> frontier = List.of(centerId);

        for (int d = 1; d <= maxDepth && !frontier.isEmpty(); d++) {
            List<Long> next = new ArrayList<>();
            for (GlossaryRelationEntity edge : relationRepository.findEdgesByTermIds(frontier)) {
                if (edgeIds.add(edge.getId())) {
                    edges.add(edge);
                }
                for (Long end : Arrays.asList(edge.getFromTermId(), edge.getToTermId())) {
                    if (!hops.containsKey(end)) {
                        hops.put(end, d);
                        next.add(end);
                    }
                }
            }
            frontier = next;
        }

        // 仅保留两端均在图内的边
        List<GlossaryRelationEntity> visibleEdges = edges.stream()
            .filter(e -> hops.containsKey(e.getFromTermId()) && hops.containsKey(e.getToTermId()))
            .collect(Collectors.toList());

        // 节点实体一次批量取回，同时用于节点 VO 与边端名称回填
        List<GlossaryEntity> nodeEntities =
            glossaryRepository.findAllByIds(new ArrayList<>(hops.keySet()));
        Map<Long, String> nameMap = nodeEntities.stream()
            .collect(Collectors.toMap(GlossaryEntity::getId, GlossaryEntity::getName));

        List<GlossaryGraphNodeVO> nodes = nodeEntities.stream()
            .map(e -> toGraphNode(e, centerId, hops.getOrDefault(e.getId(), 0)))
            .sorted(Comparator.comparingInt(GlossaryGraphNodeVO::getHop)
                .thenComparing(GlossaryGraphNodeVO::getName))
            .collect(Collectors.toList());

        GlossaryGraphVO vo = new GlossaryGraphVO();
        vo.setCenterId(center.getId());
        vo.setDepth(maxDepth);
        vo.setNodes(nodes);
        vo.setEdges(toRelationVOs(visibleEdges, nameMap));
        vo.setNodeCount(nodes.size());
        vo.setEdgeCount(vo.getEdges().size());
        return vo;
    }

    // ═══════════════ 内部工具 ═══════════════

    /** 取词条，不存在抛 404。 */
    private GlossaryEntity requireTerm(Long id) {
        Optional<GlossaryEntity> found = glossaryRepository.findById(id);
        if (found.isEmpty()) {
            throw NotFoundException.entity("GlossaryTerm", String.valueOf(id));
        }
        return found.get();
    }

    /** 词条分类空值回落 CONCEPT，保证前端过滤不为 NULL。 */
    private String normalizeTermType(String termType) {
        return (termType == null || termType.isBlank()) ? "CONCEPT" : termType.toUpperCase();
    }

    /** 状态流转合法性 — DRAFT→REVIEW→PUBLISHED→DEPRECATED，DEPRECATED 可回 DRAFT。 */
    private boolean isValidTransition(String from, String to) {
        String upperFrom = from.toUpperCase();
        String upperTo = to.toUpperCase();
        switch (upperFrom) {
            case "DRAFT":
                return "REVIEW".equals(upperTo) || "PUBLISHED".equals(upperTo) || "DEPRECATED".equals(upperTo);
            case "REVIEW":
                return "DRAFT".equals(upperTo) || "PUBLISHED".equals(upperTo) || "DEPRECATED".equals(upperTo);
            case "PUBLISHED":
                return "DEPRECATED".equals(upperTo);
            case "DEPRECATED":
                return "DRAFT".equals(upperTo);
            default:
                return true;
        }
    }

    /** GlossaryEntity → 词条 VO。 */
    private OntologyGlossaryVO toVO(GlossaryEntity e) {
        OntologyGlossaryVO vo = new OntologyGlossaryVO();
        vo.setId(e.getId());
        vo.setCode(e.getCode());
        vo.setName(e.getName());
        vo.setDefinition(e.getDefinition());
        vo.setDomain(e.getDomain());
        vo.setOwner(e.getOwner());
        vo.setStatus(e.getStatus());
        vo.setCreatedBy(e.getCreatedBy());
        vo.setCreatedAt(e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
        vo.setUpdatedAt(e.getUpdatedAt() != null ? e.getUpdatedAt().toString() : null);
        vo.setTermType(e.getTermType());
        vo.setAliases(e.getAliases());
        vo.setObjectTypeId(e.getObjectTypeId());
        vo.setParentTermId(e.getParentTermId());
        vo.setVersion(e.getVersion());
        vo.setExamples(e.getExamples());
        vo.setTags(e.getTags());
        vo.setIsPrimary(e.getIsPrimary());
        return vo;
    }

    /** GlossaryEntity → 图谱节点 VO。 */
    private GlossaryGraphNodeVO toGraphNode(GlossaryEntity e, Long centerId, int hop) {
        GlossaryGraphNodeVO node = new GlossaryGraphNodeVO();
        node.setId(e.getId());
        node.setCode(e.getCode());
        node.setName(e.getName());
        node.setTermType(e.getTermType());
        node.setDomain(e.getDomain());
        node.setStatus(e.getStatus());
        node.setCenter(e.getId().equals(centerId));
        node.setHop(hop);
        return node;
    }

    /** 关系边批量转 VO（名称经一次批量查询回填，避免 N+1）。 */
    private List<GlossaryRelationVO> toRelationVOs(List<GlossaryRelationEntity> edges) {
        if (edges.isEmpty()) {
            return List.of();
        }
        Set<Long> termIds = new HashSet<>();
        edges.forEach(e -> {
            termIds.add(e.getFromTermId());
            termIds.add(e.getToTermId());
        });
        Map<Long, String> nameMap = glossaryRepository.findAllByIds(new ArrayList<>(termIds))
            .stream()
            .collect(Collectors.toMap(GlossaryEntity::getId, GlossaryEntity::getName));
        return toRelationVOs(edges, nameMap);
    }

    /** 关系边批量转 VO（名称取自给定映射）。 */
    private List<GlossaryRelationVO> toRelationVOs(List<GlossaryRelationEntity> edges,
                                                   Map<Long, String> nameMap) {
        return edges.stream().map(e -> {
            GlossaryRelationVO vo = new GlossaryRelationVO();
            vo.setId(e.getId());
            vo.setFromTermId(e.getFromTermId());
            vo.setFromTermName(nameMap.get(e.getFromTermId()));
            vo.setToTermId(e.getToTermId());
            vo.setToTermName(nameMap.get(e.getToTermId()));
            vo.setRelationType(e.getRelationType());
            vo.setWeight(e.getWeight());
            vo.setDescription(e.getDescription());
            vo.setCreatedBy(e.getCreatedBy());
            vo.setCreatedAt(e.getCreatedAt() != null ? e.getCreatedAt().toString() : null);
            return vo;
        }).collect(Collectors.toList());
    }
}