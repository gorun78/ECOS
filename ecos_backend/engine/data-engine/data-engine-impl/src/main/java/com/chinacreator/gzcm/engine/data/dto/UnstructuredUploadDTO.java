package com.chinacreator.gzcm.engine.data.dto;

import lombok.Data;

/**
 * 非结构化原文上传入参（数据侧前端打通 step ①②③ 的"上传近源层"端点）。
 *
 * <p>与 {@link UnstructuredRegisterDTO}（纯登记，二进制由调用方自行写入）不同，
 * 本 DTO 配合 {@code MultipartFile} 在 Server 端直接写入 MinIO 近源层非结构化区，
 * 随后复用 {@code DataLakeResourceService.registerUnstructured} 登记
 * {@code td_data_resource}（layer=RAW, zone=UNSTRUCTURED, resource_type=LAKE_OBJECT），
 * 对象 key 由 {@code LakeObjectKeys.unstructuredObjectKey(source, docId, fileName)} 组装
 * （数据湖存储分层规范 §三）。
 *
 * <p>文件二进制走 multipart {@code @RequestPart("file")}，不走 base64（避免大文件内存问题）。
 */
@Data
public class UnstructuredUploadDTO {

    /** 上游数据源标识（对象 key 第二段），必填，≤128；无数据源时填 {@code default} */
    private String source;

    /** 文档 ID（对象 key 第三段），必填，≤128；前端可自动生成 uuid */
    private String docId;

    /** 原始文件名（对象 key 末段，可选；为空时取 MultipartFile.getOriginalFilename()） */
    private String originalFileName;

    /** 资源显示名（可选，默认取原始文件名，落 td_data_resource.resource_name） */
    private String resourceName;

    /** 上游数据源 ID（可选，落 td_data_resource.datasource_id；为空落兜底值） */
    private String datasourceId;
}
