package com.chinacreator.gzcm.datanet.repository;

import com.chinacreator.gzcm.datanet.model.DataCategory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CategoryRepository {

    int insert(DataCategory category);

    int update(DataCategory category);

    DataCategory findById(@Param("id") String id);

    /** 查询所有分类，按层级+排序排列 */
    List<DataCategory> findAll();

    /** 查询子分类 */
    List<DataCategory> findByParentId(@Param("parentId") String parentId);

    /** 查询最大排序号 */
    Integer maxSortOrder(@Param("parentId") String parentId);

    /** 查询关联资源数 */
    int countResources(@Param("categoryPath") String categoryPath);

    int deleteById(@Param("id") String id);
}
