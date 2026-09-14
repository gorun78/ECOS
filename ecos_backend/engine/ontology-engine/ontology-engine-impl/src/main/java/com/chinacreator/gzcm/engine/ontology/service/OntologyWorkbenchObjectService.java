package com.chinacreator.gzcm.engine.ontology.service;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.chinacreator.gzcm.engine.ontology.dto.OntologyWorkbenchObjectSaveDTO;
import com.chinacreator.gzcm.engine.ontology.dto.OntologyWorkbenchObjectVO;
import com.chinacreator.gzcm.engine.ontology.model.OntologyWorkbenchObject;
import com.chinacreator.gzcm.engine.ontology.repository.OntologyWorkbenchObjectRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 本体工作台 5 类视图对象业务服务（Wave B-5 T7）。
 *
 * <p>管理 action / interface / shared_property / function / dataset 5 类
 * "object 视图"对象的本体域 CRUD。前端此前纯本地 state（OntologyWorkbenchLayout
 * L85 "no backend yet"），本服务补齐 list (按 type 过滤) / detail / create /
 * update / delete 后端链路，存入 {@code ecos_ontology_workbench_object}（V121）。
 *
 * <p>分层遵循：Controller 仅参数接收 + 统一返回；本 Service 负责
 * type 白名单校验、JSON 序列化、数据组装；DAO 仅 SQL。
 */
@Service
public class OntologyWorkbenchObjectService {

    private static final Logger log = LoggerFactory.getLogger(OntologyWorkbenchObjectService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    /** 5 类视图类型白名单（与前端 FileType 定义同源）— 枚举边界一致铁律 4.8.2 */
    public static final Set<String> OBJECT_TYPES =
        Set.of("action", "interface", "shared_property", "function", "dataset");

    private final OntologyWorkbenchObjectRepository repository;

    public OntologyWorkbenchObjectService(OntologyWorkbenchObjectRepository repository) {
        this.repository = repository;
    }

    private String nextId() {
        return "wbobj_" + UUID.randomUUID().toString().replace("-", "");
    }

    // ═══════════════ Query ═══════════════════

    /**
     * 列出本体下指定类型的视图对象（type blank=全部 5 类）。
     *
     * @throws IllegalArgumentException ONT-023: type 非法
     */
    public List<OntologyWorkbenchObjectVO> listObjects(String ontologyId, String objectType) {
        validateType(objectType);
        return repository.findObjectsByOntology(ontologyId, objectType).stream()
            .map(this::toVO).collect(Collectors.toList());
    }

    /** 单个对象详情（fileId 直接查库, 限本体归属）。空 = 404 语义。 */
    public Optional<OntologyWorkbenchObjectVO> getObjectVO(String ontologyId, String fileId) {
        return repository.findObjectById(ontologyId, fileId).map(this::toVO);
    }

    // ═══════════════ Mutation ═══════════════════

    /**
     * 新增视图对象。
     *
     * @throws IllegalArgumentException ONT-020/021/022/023 校验失败
     */
    public OntologyWorkbenchObjectVO createObject(String ontologyId, OntologyWorkbenchObjectSaveDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("ONT-020: request body is required");
        }
        validateType(dto.getObjectType());
        if (dto.getCode() == null || dto.getCode().isBlank()) {
            throw new IllegalArgumentException("ONT-021: code is required");
        }
        if (dto.getName() == null || dto.getName().isBlank()) {
            throw new IllegalArgumentException("ONT-022: name is required");
        }
        OntologyWorkbenchObject obj = new OntologyWorkbenchObject();
        obj.setId(nextId());
        obj.setOntologyId(ontologyId);
        obj.setObjectType(dto.getObjectType().toLowerCase(Locale.ROOT));
        obj.setCode(dto.getCode());
        obj.setName(dto.getName());
        obj.setDescription(dto.getDescription() != null ? dto.getDescription() : "");
        obj.setDefinitionJson(toJson(dto.getDefinition()));
        obj.setStatus(dto.getStatus() != null ? dto.getStatus() : "ACTIVE");
        repository.insertObject(obj);
        log.info("Workbench object created: {} [{}] type={} ontology={}",
            obj.getId(), obj.getCode(), obj.getObjectType(), ontologyId);
        return toVO(obj);
    }

    /**
     * 按 fileId 更新（限本体归属；不存在 → empty = 404 语义）。
     * null 字段不更新（COALESCE 语义, 对齐 OntologyRepository.updateEntity）。
     */
    public Optional<OntologyWorkbenchObjectVO> updateObject(String ontologyId, String fileId,
                                                            OntologyWorkbenchObjectSaveDTO dto) {
        if (dto == null) {
            throw new IllegalArgumentException("ONT-020: request body is required");
        }
        if (repository.findObjectById(ontologyId, fileId).isEmpty()) {
            return Optional.empty();
        }
        repository.updateObject(ontologyId, fileId,
            dto.getCode(), dto.getName(), dto.getDescription(),
            toJson(dto.getDefinition()), dto.getStatus());
        log.info("Workbench object updated: {} (ontology={})", fileId, ontologyId);
        return repository.findObjectById(ontologyId, fileId).map(this::toVO);
    }

    /** 逻辑删除 (T17 语义): is_deleted=1 + status='ARCHIVED'。
     *
     * @return true=删除成功, false=不存在
     */
    public boolean deleteObject(String ontologyId, String fileId) {
        return repository.deleteObject(ontologyId, fileId) > 0;
    }

    // ═══════════════ 校验 & 转换 ═══════════════════

    /** 5 类类型白名单校验（blank 合法=不过滤；非法 → 400 语义业务异常）。 */
    private void validateType(String objectType) {
        if (objectType == null || objectType.isBlank()) {
            return;
        }
        String normalized = objectType.toLowerCase(Locale.ROOT);
        if (!OBJECT_TYPES.contains(normalized)) {
            throw new IllegalArgumentException(
                "ONT-023: invalid objectType '" + objectType + "', allowed: " + OBJECT_TYPES);
        }
    }

    private OntologyWorkbenchObjectVO toVO(OntologyWorkbenchObject o) {
        OntologyWorkbenchObjectVO vo = new OntologyWorkbenchObjectVO();
        vo.setId(o.getId());
        vo.setOntologyId(o.getOntologyId());
        vo.setObjectType(o.getObjectType());
        vo.setCode(o.getCode());
        vo.setName(o.getName());
        vo.setDescription(o.getDescription());
        vo.setDefinition(safeParseJson(o.getDefinitionJson()));
        vo.setStatus(o.getStatus());
        vo.setCreateTime(o.getCreateTime() != null ? o.getCreateTime().toString() : null);
        vo.setUpdateTime(o.getUpdateTime() != null ? o.getUpdateTime().toString() : null);
        return vo;
    }

    @SuppressWarnings("unchecked")
    private Object safeParseJson(String json) {
        if (json == null || json.isBlank()) {
            return null;
        }
        try {
            return MAPPER.readValue(json, Map.class);
        } catch (Exception e) {
            // 非 JSON 对象（数组/字符串）时回退原文, 不阻塞渲染
            return json;
        }
    }

    private String toJson(Object obj) {
        if (obj == null) {
            return "";
        }
        try {
            return MAPPER.writeValueAsString(obj);
        } catch (Exception e) {
            log.warn("Workbench object definition serialize failed, fallback to string", e);
            return String.valueOf(obj);
        }
    }
}
