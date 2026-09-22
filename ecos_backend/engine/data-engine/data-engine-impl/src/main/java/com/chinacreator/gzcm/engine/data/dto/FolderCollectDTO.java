package com.chinacreator.gzcm.engine.data.dto;

import lombok.Data;

import java.util.List;

/**
 * 文件夹数据源采集入参（非结构化近源层「文件夹模式」）。
 *
 * <p>服务端从 FILESYSTEM 类型数据源的 {@code connectionConfig.rootPath} 读取文件
 * （不信任前端 path 参数），将所选文件写入 MinIO 近源层
 * {@code raw/unstructured/{datasourceId}/{docId}/{fileName}} 并登记
 * {@code td_data_resource}（layer=RAW, zone=UNSTRUCTURED, resource_type=LAKE_OBJECT）。
 */
@Data
public class FolderCollectDTO {

    /** FILESYSTEM 类型数据源 ID（必填），文件根目录取自该数据源 connectionConfig.rootPath */
    private String datasourceId;

    /** 文档 ID（必填），同一次采集的所有文件共享该 docId（对象 key 第三段） */
    private String docId;

    /** 待采集的文件名列表（必填，仅文件名，不含路径，防越权） */
    private List<String> fileNames;
}
