package com.chinacreator.gzcm.buszhi.workflow.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * 操作删除结果 VO — 强类型返回。
 * （溯源：ontology-engine-impl T16-4 等价移植，PMO-57 T2 迁入 buszhi 服务层，
 * 供 workflow 删除端点使用；与 ontology 侧 DeleteResultVO 结构同形。）
 *
 * <p>替代原先返回的 {@code Map.of("id", id, "deleted", true)} Map 形态。
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class WorkflowDeleteResultVO {

    /** 是否删除成功 */
    private Boolean deleted;

    /** 被删除项的主键 ID */
    private String id;

    public WorkflowDeleteResultVO() {
    }

    public Boolean getDeleted() {
        return deleted;
    }

    public void setDeleted(Boolean deleted) {
        this.deleted = deleted;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }
}
