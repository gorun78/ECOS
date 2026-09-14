package com.chinacreator.gzcm.engine.ontology.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * 本体版本（Ontology Version）VO — T16-2 强类型返回。
 *
 * <p>字段对齐 {@code OntologyVersionService.toMap(OntologyVersion)} 输出。
 * {@code snapshot} 为动态快照对象（JSON），保持 Object 类型
 * （与既有 Map 内 {@code safeParseJson} 行为一致）。
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class OntologyVersionVO {

    /** 版本 ID（ver 前缀） */
    private String id;

    /** 所属本体 id */
    private String ontologyId;

    /** 版本号（如 1.0.0） */
    private String versionNo;

    /** 状态（Draft / Published / Deprecated） */
    private String status;

    /**
     * 快照（JSON 对象）— 动态内容保持 Object（与既有 safeParseJson 输出对齐）。
     */
    private Object snapshot;

    /** 变更日志 */
    private String changeLog;

    /** 发布人 */
    private String publisher;

    /** 发布时间 ISO 字符串 */
    private String publishedAt;

    /** 创建时间 ISO 字符串 */
    private String createdAt;

    // ── 以下两个字段仅 diffVO 端点填充 ────────────────────────

    /**
     * 版本对比 — 前一版本号（仅 diffVO 返回）。
     * <p>用于 {@code GET /{ontologyId}/versions/{v1}/diff/{v2}}，
     * 与既有 Map 行为 {@code result.put("version1", ver1.getVersionNo())} 对齐。
     */
    private String version1;

    /** 版本对比 — 第二版本号（仅 diffVO 返回）。 */
    private String version2;

    /** 版本对比 — 前一个版本快照（仅 diffVO 返回；动态 JSON 子集，保持 Object 与既有契约一致）。 */
    private Object snapshot1;

    /** 版本对比 — 第二版本快照（仅 diffVO 返回；动态 JSON 子集，保持 Object 与既有契约一致）。 */
    private Object snapshot2;
}
