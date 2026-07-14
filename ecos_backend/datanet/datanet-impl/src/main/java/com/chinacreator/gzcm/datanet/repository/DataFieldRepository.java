package com.chinacreator.gzcm.datanet.repository;

import com.chinacreator.gzcm.datanet.model.DataField;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface DataFieldRepository {

    int insert(DataField field);

    int batchInsert(@Param("fields") List<DataField> fields);

    List<DataField> findByResourceId(@Param("resourceId") String resourceId);

    int deleteByResourceId(@Param("resourceId") String resourceId);
}
