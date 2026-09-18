package com.chinacreator.gzcm.engine.data;

import com.chinacreator.gzcm.common.data.dto.DataSourceDTO;
import com.chinacreator.gzcm.engine.data.datasource.entity.DataSourceEntity;

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
     * 更新已有数据源连接。
     *
     * @param datasourceId 数据源 ID
     * @param dto 数据源配置
     * @return 更新后的数据源实体
     */
    DataSourceEntity updateDataSource(String datasourceId, DataSourceDTO dto);

    /**
     * 删除数据源。
     */
    void remove(String datasourceId);

    /**
     * 更新数据源的元数据策略配置 (metadata_config JSONB)。
     * P0-3 新增。
     *
     * @param datasourceId 数据源 ID
     * @param json         完整的 MetadataStrategyConfig JSON 串
     */
    void updateMetadataConfig(String datasourceId, String json);

    /**
     * 返回「解密回填密码后」的连接配置，供真实建立连接的场景使用
     * （元数据采集、表结构预览等）。
     *
     * <p>PMO-49 起密码只以密文存于 {@code password_enc}，{@code connection_config} 中的
     * 密码字段已剥离；读取路径需经 security-engine 解密回填后才能建连，否则报
     * 「SCRAM-based authentication, but no password was provided」。
     * 本方法仅内存回填，不写库、不回显。
     *
     * @param datasourceId 数据源 ID
     * @return 已回填密码的 connectionConfig JSON；数据源不存在时返回 {@code null}
     */
    String getResolvedConnectionConfig(String datasourceId);
}
