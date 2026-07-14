package com.chinacreator.gzcm.datanet.connector;

import com.chinacreator.gzcm.datanet.model.DataResource;

import java.util.List;
import java.util.Map;

/**
 * 数据连接器 — 连接到具体数据源并获取数据资源信息。
 *
 * @author DataBridge Datanet Team
 */
public interface Connector {

    /** 支持的连接类型（如 "JDBC", "REST"） */
    String supportedType();

    /** 测试连接是否可达 */
    boolean testConnection(String connectionConfig);

    /** 列出该数据源中所有可用的数据资源（如表、视图、端点） */
    List<DataResource> listResources(String connectionConfig, String orgId, String orgName);

    /** 预览数据 — 返回表的样本行（SELECT * LIMIT n） */
    List<Map<String, Object>> queryPreview(String connectionConfig, String tableName, int limit);
}
