package com.chinacreator.gzcm.datanet.repository;

import com.chinacreator.gzcm.datanet.model.DataResource;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DataResourceRepository {

    int insert(DataResource resource);

    int update(DataResource resource);

    DataResource findById(@Param("id") String id);

    List<DataResource> findByDatasource(@Param("datasourceId") String datasourceId);

    List<DataResource> findAll();

    List<DataResource> search(@Param("keyword") String keyword);

    int deleteById(@Param("id") String id);
}
