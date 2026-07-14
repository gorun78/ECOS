package com.chinacreator.gzcm.engine.data;

import com.chinacreator.gzcm.datanet.model.DataCategory;

import java.util.List;

/**
 * 分类管理服务 — 管理数据目录的分类树。
 *
 * @author DataBridge Datanet Team
 */
public interface CategoryService {

    /** 创建分类 */
    DataCategory create(DataCategory category);

    /** 更新分类 */
    DataCategory update(DataCategory category);

    /** 查询分类详情 */
    DataCategory getById(String categoryId);

    /** 获取完整分类树 */
    List<DataCategory> getTree();

    /** 获取子分类列表 */
    List<DataCategory> getChildren(String parentId);

    /** 删除分类 */
    void remove(String categoryId);

    /** 获取分类的资源数量统计 */
    List<DataCategory> getCategoryStats();
}
