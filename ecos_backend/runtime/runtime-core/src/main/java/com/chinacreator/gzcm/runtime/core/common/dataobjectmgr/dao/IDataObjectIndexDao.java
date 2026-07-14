package com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.dao;

import java.util.List;
import com.chinacreator.gzcm.runtime.core.common.dataobjectmgr.bean.DataObjectIndex;

/**
 * 数据对象索引 DAO 接口
 */
public interface IDataObjectIndexDao {
    
    /**
     * 根据对象ID查询索引列表
     * @param objectId 对象ID
     * @return 索引列表
     * @throws Exception
     */
    List<DataObjectIndex> selectByObjectId(String objectId) throws Exception;
    
    /**
     * 根据索引ID查询
     * @param indexId 索引ID
     * @return 索引对象
     * @throws Exception
     */
    DataObjectIndex selectById(String indexId) throws Exception;
    
    /**
     * 根据对象ID和列编码（LIKE）查询数量
     * @param objectId 对象ID
     * @param columnCode 列编码（支持LIKE）
     * @return 数量
     * @throws Exception
     */
    int selectCountByObjectIdAndLikeColumnCode(String objectId, String columnCode) throws Exception;
    
    /**
     * 根据对象ID删除索引
     * @param objectId 对象ID
     * @throws Exception
     */
    void deleteByObjectId(String objectId) throws Exception;
    
    /**
     * 添加索引
     * @param index 索引对象
     * @throws Exception
     */
    void add(DataObjectIndex index) throws Exception;
    
    /**
     * 更新索引
     * @param index 索引对象
     * @throws Exception
     */
    void update(DataObjectIndex index) throws Exception;
    
    /**
     * 删除索引
     * @param indexId 索引ID
     * @throws Exception
     */
    void delete(String indexId) throws Exception;
}
