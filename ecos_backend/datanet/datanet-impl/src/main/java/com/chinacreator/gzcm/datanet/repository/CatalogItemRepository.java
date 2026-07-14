package com.chinacreator.gzcm.datanet.repository;

import com.chinacreator.gzcm.datanet.model.CatalogItem;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface CatalogItemRepository {

    int insert(CatalogItem item);

    int update(CatalogItem item);

    CatalogItem findById(@Param("id") String id);

    CatalogItem findByResourceId(@Param("resourceId") String resourceId);

    List<CatalogItem> search(@Param("keyword") String keyword,
                              @Param("resourceType") String resourceType,
                              @Param("categoryPath") String categoryPath,
                              @Param("offset") int offset,
                              @Param("limit") int limit);

    long count(@Param("keyword") String keyword,
               @Param("resourceType") String resourceType);

    List<CatalogItem> findByOrg(@Param("orgName") String orgName);

    long countAll();

    /**
     * 按字段名称搜索资源。
     *
     * @param fieldName 字段名称（模糊匹配）
     * @param offset 偏移量
     * @param limit 每页条数
     * @return 匹配的目录项列表（去重）
     */
    List<CatalogItem> searchByFieldName(@Param("fieldName") String fieldName,
                                         @Param("offset") int offset,
                                         @Param("limit") int limit);

    long countByFieldName(@Param("fieldName") String fieldName);

    int deleteById(@Param("id") String id);
}
