package com.chinacreator.gzcm.datanet.repository;

import com.chinacreator.gzcm.runtime.core.datasource.entity.DataSourceEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DataSourceRepository {

    int insert(DataSourceEntity entity);

    int update(DataSourceEntity entity);

    DataSourceEntity findById(@Param("id") String id);

    List<DataSourceEntity> findAll();

    int deleteById(@Param("id") String id);
}
