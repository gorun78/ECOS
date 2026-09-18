package com.chinacreator.gzcm.engine.data.repository;

import com.chinacreator.gzcm.common.data.model.DataResource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DataResourceRepository {

    int insert(DataResource resource);

    int update(DataResource resource);

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
