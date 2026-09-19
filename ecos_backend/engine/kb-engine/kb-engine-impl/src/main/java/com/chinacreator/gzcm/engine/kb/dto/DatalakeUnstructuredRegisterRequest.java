package com.chinacreator.gzcm.engine.kb.dto;

import lombok.Data;

/**
 * 数据工作台「非结构化原文登记」出站请求体
 * （{@code POST /api/v1/datanet/datalake/unstructured}，分层规范 §三/§五）。
 *
 * <p>跨引擎只调 REST（架构铁律 §2.1），故 kb 侧自备强类型出站 DTO。
 */
@Data
public class DatalakeUnstructuredRegisterRequest {

    /** 上游数据源标识（对象 key {source} 段） */
    private String source;

    /** 文档 ID（对象 key {docId} 段） */
    private String docId;

    /** 原始文件名（对象 key 末段） */
    private String originalFileName;

    /** 文件字节数 */
    private Long size;

    /** 内容类型 */
    private String contentType;

    /** 上游数据源 ID（可为空，数据工作台落兜底值） */
    private String datasourceId;

    /** 资源显示名（可为空，数据工作台默认取文件名） */
    private String resourceName;
}
