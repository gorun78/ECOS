package com.chinacreator.gzcm.datanet.service;

import com.chinacreator.gzcm.datanet.dto.DataSourceDTO;
import com.chinacreator.gzcm.runtime.core.datasource.entity.DataSourceEntity;

import java.util.List;

/**
 * 数据源管理服务 — 负责数据源连接的注册、测试和管理。
 *
 * @author DataBridge Datanet Team
 */
public interface DataSourceService {

    /**
     * 注册新的数据源连接。
     *
     * @param dto 数据源配置
     * @return 创建的数据源实体
     */
    DataSourceEntity register(DataSourceDTO dto);

    /**
     * 测试数据源连接是否可达。
     *
     * @param datasourceId 数据源 ID
     * @return 测试结果（true=连接成功）
     */
    boolean testConnection(String datasourceId);

    /**
     * 获取所有已注册的数据源。
     */
    List<DataSourceEntity> listAll();

    /**
     * 根据 ID 获取数据源。
     */
    DataSourceEntity getById(String datasourceId);

    /**
     * 删除数据源。
     */
    void remove(String datasourceId);
}
