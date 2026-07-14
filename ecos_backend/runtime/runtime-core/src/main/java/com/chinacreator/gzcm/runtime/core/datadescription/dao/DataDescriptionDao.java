package com.chinacreator.gzcm.runtime.core.datadescription.dao;

import java.util.List;

import com.chinacreator.gzcm.runtime.core.datadescription.entity.DataDescriptionEntity;
import com.chinacreator.gzcm.runtime.core.datadescription.service.IDataDescriptionService.QueryCondition;

/**
 * 鏁版嵁鎻忚堪DAO鎺ュ彛
 * 
 * @author CDRC Runtime Team
 */
public interface DataDescriptionDao {
    
    /**
     * 鍒涘缓鏁版嵁鎻忚堪
     * 
     * @param entity 鏁版嵁鎻忚堪瀹炰綋
     * @throws Exception
     */
    void createDataDescription(DataDescriptionEntity entity) throws Exception;
    
    /**
     * 鏍规嵁ID鏌ヨ鏁版嵁鎻忚堪
     * 
     * @param id 鏁版嵁鎻忚堪ID
     * @return 鏁版嵁鎻忚堪瀹炰綋
     * @throws Exception
     */
    DataDescriptionEntity getDataDescription(String id) throws Exception;
    
    /**
     * 鏇存柊鏁版嵁鎻忚堪
     * 
     * @param entity 鏁版嵁鎻忚堪瀹炰綋
     * @throws Exception
     */
    void updateDataDescription(DataDescriptionEntity entity) throws Exception;
    
    /**
     * 鍒犻櫎鏁版嵁鎻忚堪
     * 
     * @param id 鏁版嵁鎻忚堪ID
     * @throws Exception
     */
    void deleteDataDescription(String id) throws Exception;
    
    /**
     * 鏍规嵁鏉′欢鏌ヨ鏁版嵁鎻忚堪鍒楄〃
     * 
     * @param condition 鏌ヨ鏉′欢
     * @return 鏁版嵁鎻忚堪鍒楄〃
     * @throws Exception
     */
    List<DataDescriptionEntity> listDataDescriptions(QueryCondition condition) throws Exception;
    
    /**
     * 鏍规嵁鏁版嵁绫诲瀷鏌ヨ鏁版嵁鎻忚堪鍒楄〃
     * 
     * @param dataType 鏁版嵁绫诲瀷
     * @return 鏁版嵁鎻忚堪鍒楄〃
     * @throws Exception
     */
    List<DataDescriptionEntity> listDataDescriptionsByType(String dataType) throws Exception;
}

