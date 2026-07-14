package com.chinacreator.gzcm.engine.data;

import com.chinacreator.gzcm.datanet.dto.CatalogQueryDTO;
import com.chinacreator.gzcm.datanet.model.CatalogItem;
import com.chinacreator.gzcm.datanet.model.DataResource;

import java.util.List;

/**
 * 目录服务 — 数据资源编目、搜索、发现。
 * <p>
 * 这是数据网络的核心服务：各部门通过此服务注册数据资源，
 * 其他子系统通过此服务发现和获取数据。
 *
 * @author DataBridge Datanet Team
 */
public interface CatalogService {

    /**
     * 将数据资源注册到目录中。
     *
     * @param resource 数据资源
     * @return 生成的目录项
     */
    CatalogItem register(DataResource resource);

    /**
     * 搜索目录。
     *
     * @param query 查询参数
     * @return 匹配的目录项列表
     */
    List<CatalogItem> search(CatalogQueryDTO query);

    /**
     * 根据 ID 获取目录项详情。
     */
    CatalogItem getById(String catalogId);

    /**
     * 根据资源 ID 获取目录项。
     */
    CatalogItem getByResourceId(String resourceId);

    /**
     * 获取某个部门的所有资源。
     */
    List<CatalogItem> listByOrg(String orgId);

    /**
     * 统计目录资源总数。
     */
    long count();

    /**
     * 按字段名称搜索资源（跨 td_data_field 关联查询）。
     *
     * @param fieldName 字段名称模糊匹配
     * @param page 页码
     * @param pageSize 每页条数
     * @return 匹配的资源目录项（去重）
     */
    List<CatalogItem> searchByFieldName(String fieldName, int page, int pageSize);

    /**
     * 按字段名称搜索的结果数。
     */
    long countByFieldName(String fieldName);

    /**
     * 更新目录项。
     */
    CatalogItem update(CatalogItem item);

    /**
     * 从目录中移除资源。
     */
    void remove(String catalogId);
}
