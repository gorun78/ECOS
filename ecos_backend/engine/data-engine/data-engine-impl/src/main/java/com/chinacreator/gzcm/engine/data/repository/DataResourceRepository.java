package com.chinacreator.gzcm.engine.data.repository;

import com.chinacreator.gzcm.common.data.model.DataResource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DataResourceRepository {

    int insert(DataResource resource);

    int update(DataResource resource);

    /**
     * 精确改写分层与近源区（可把 zone 显式置空）。
     * <p>{@link #update} 的 zone 为条件列（null 不写），无法清空旧值；当登记层从 RAW
     * 变为非 RAW 时需用本方法把 zone 强制置空，以严格符合分层合法性矩阵。
     *
     * @param resourceId 资源 ID
     * @param layer      数据分层（DataLayer 枚举名，必填）
     * @param zone       近源区（非近源层传 null）
     * @return 影响行数
     */
    int updateLayerZone(@Param("resourceId") String resourceId,
                        @Param("layer") String layer,
                        @Param("zone") String zone);

    DataResource findById(@Param("id") String id);

    /**
     * 按数据源内定位查找资源（source_path 非唯一，取最新一条）。
     *
     * @param sourcePath 数据源内定位（如 raw/structured/… 对象名 或 schema.table）
     * @return 匹配资源；不存在返回 null
     */
    DataResource findBySourcePath(@Param("sourcePath") String sourcePath);

    List<DataResource> findByDatasource(@Param("datasourceId") String datasourceId);

    List<DataResource> findAll();

    List<DataResource> search(@Param("keyword") String keyword);

    int deleteById(@Param("id") String id);
}
