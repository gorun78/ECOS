package com.chinacreator.gzcm.engine.data.dto;

import lombok.Data;

/**
 * 非结构化原文对象登记入参（方案 §4 F1 / §5.3「知识→数据 登记非结构化原文对象」）。
 *
 * <p>按数据湖存储分层规范 §三 组装对象 key：
 * {@code raw/unstructured/{source}/{docId}/{originalFileName}}，
 * 并按 §五 登记 {@code td_data_resource}：{@code layer=RAW}、{@code zone=UNSTRUCTURED}、
 * {@code resource_type=LAKE_OBJECT}、{@code source_path=<完整对象 key>}。
 *
 * <p>本端点只做「元数据登记」，不接收文档二进制（二进制由调用方写入近源层后登记）。
 */
@Data
public class UnstructuredRegisterDTO {

    /** 上游数据源标识（对象 key 第二段），必填，≤128 */
    private String source;

    /** 文档 ID（对象 key 第三段），必填，≤128 */
    private String docId;

    /** 原始文件名（对象 key 末段，保留原名），必填，≤512 */
    private String originalFileName;

    /** 文件字节数（可选，落 record_count 供概览展示） */
    private Long size;

    /** 内容类型（可选，如 application/pdf），当前仅作元数据补充 */
    private String contentType;

    /** 上游数据源 ID（可选，落 td_data_resource.datasource_id；为空落兜底值） */
    private String datasourceId;

    /** 资源显示名（可选，默认取原始文件名） */
    private String resourceName;
}
