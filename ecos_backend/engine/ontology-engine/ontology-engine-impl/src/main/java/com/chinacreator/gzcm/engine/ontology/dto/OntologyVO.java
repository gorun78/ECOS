package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 本体列表项 VO — {@code ListOntologies} 等接口返回单元。
 *
 * <p>字段对齐 {@code OntologyRepository.findOntologyById / findAllOntologies} 的 RowMapper 输出
 * （资料源即 Repository 层直接从 PG 取出,无二次加工）。
 *
 * <p>注：{@code toMap} 旧接口输出包含全部原 Map 字段，但本 VO 只覆盖常用稳定字段；
 * 其余字段可由前端在需要时再回退到现有旧 Map 路径（不在本批改造范围）。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OntologyVO {

    /** 主键 */
    private String id;

    /** 本体编码 */
    private String code;

    /** 本体名称 */
    private String name;

    /** 本体版本号（Repository 写入 create 默认 "1.0"） */
    private String version;

    /** 本体描述 */
    private String description;

    /** 状态（如 ACTIVE / ARCHIVED / DRAFT） */
    private String status;

    /** 创建时间 ISO 字符串 */
    private String createdAt;

    /** 更新时间 ISO 字符串 */
    private String updatedAt;
}
