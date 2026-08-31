package com.chinacreator.gzcm.engine.data.repository;

import com.chinacreator.gzcm.engine.data.datasource.entity.DataSourceEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DataSourceRepository {

    int insert(DataSourceEntity entity);

    int update(DataSourceEntity entity);

    DataSourceEntity findById(@Param("id") String id);

    List<DataSourceEntity> findAll();

    int updateLastCollectTime(@Param("id") String id);

    int deleteById(@Param("id") String id);
}
