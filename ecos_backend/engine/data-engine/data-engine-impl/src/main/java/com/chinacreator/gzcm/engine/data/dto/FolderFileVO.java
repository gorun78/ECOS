package com.chinacreator.gzcm.engine.data.dto;

import lombok.Data;

/**
 * 文件夹数据源文件列表项（GET /unstructured/folder/files 返回）。
 * <p>只暴露文件名/大小/修改时间，不暴露服务端绝对路径（防路径探测）。
 */
@Data
public class FolderFileVO {

    /** 文件名（含扩展名） */
    private String name;

    /** 文件字节数 */
    private Long size;

    /** 最后修改时间（ISO-8601 字符串） */
    private String lastModified;
}
