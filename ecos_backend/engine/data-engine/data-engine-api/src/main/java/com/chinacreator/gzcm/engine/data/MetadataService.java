package com.chinacreator.gzcm.engine.data;

import com.chinacreator.gzcm.datanet.model.DataField;

import java.util.List;

/**
 * 元数据采集服务 — 从已连接的数据源中自动采集表结构、字段信息。
 *
 * @author DataBridge Datanet Team
 */
public interface MetadataService {

    /**
     * 采集指定数据源的元数据（所有表/视图的结构信息）。
     *
     * @param datasourceId 数据源 ID
     * @return 采集到的数据资源列表
     */
    int collectAll(String datasourceId);

    /**
     * 获取指定资源的字段列表。
     *
     * @param resourceId 资源 ID
     * @return 字段列表
     */
    List<DataField> getFields(String resourceId);
}
