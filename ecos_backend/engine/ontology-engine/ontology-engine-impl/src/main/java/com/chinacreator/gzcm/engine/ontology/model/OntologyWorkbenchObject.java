package com.chinacreator.gzcm.engine.ontology.model;

import java.time.LocalDateTime;

/**
 * 本体工作台视图对象持久化 POJO — 5 类 object 视图统一存储。
 *
 * <p>Wave B-5 T7：前端 5 类视图 (action / interface / shared_property /
 * function / dataset) 此前纯本地 state，本 POJO 对应
 * {@code ecos_ontology_workbench_object} 表（V121__ecos_ontology_workbench_view.sql）。
 *
 * <p>命名带 Workbench 前缀：与 T16-2 既有 {@code OntologyObjectSaveDTO}
 * （"对象类型/entity"语义）区分，本 POJO 为工作台"object 视图"对象。
 *
 * <p>{@code definitionJson} 承载各类差异字段（前端视图对象结构 JSON 原文），
 * 外层强类型字段对齐 5 类视图共同属性 (code/name/description)。
 */
public class OntologyWorkbenchObject {

    private String id;
    private String ontologyId;
    /** 视图类型: action / interface / shared_property / function / dataset */
    private String objectType;
    private String code;
    private String name;
    private String description;
    private String definitionJson;
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    public OntologyWorkbenchObject() {}

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getOntologyId() { return ontologyId; }
    public void setOntologyId(String ontologyId) { this.ontologyId = ontologyId; }

    public String getObjectType() { return objectType; }
    public void setObjectType(String objectType) { this.objectType = objectType; }

    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }

    public String getName() { return name; }
    public void setName(String name) { this.name = name; }

    public String getDescription() { return description; }
    public void setDescription(String description) { this.description = description; }

    public String getDefinitionJson() { return definitionJson; }
    public void setDefinitionJson(String definitionJson) { this.definitionJson = definitionJson; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getUpdateTime() { return updateTime; }
    public void setUpdateTime(LocalDateTime updateTime) { this.updateTime = updateTime; }
}
