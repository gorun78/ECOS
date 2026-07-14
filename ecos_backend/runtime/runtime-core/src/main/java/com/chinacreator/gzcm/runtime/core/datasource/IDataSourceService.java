package com.chinacreator.gzcm.runtime.core.datasource;

import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.runtime.core.datasource.entity.DataSourceEntity;

/**
 * 数据源管理服务接口
 * 提供数据源的CRUD、测试、推送等功能
 * 
 * @author CDRC Runtime Team
 */
public interface IDataSourceService {
    
    /**
     * 鍒涘缓鏁版嵁婧?
     * 
     * @param datasource 鏁版嵁婧愬疄浣?
     * @return 鏁版嵁婧怚D
     * @throws DataSourceException 鏁版嵁婧愬紓甯?
     */
    String createDataSource(DataSourceEntity datasource) throws DataSourceException;
    
    /**
     * 鏇存柊鏁版嵁婧?
     * 
     * @param datasource 鏁版嵁婧愬疄浣?
     * @throws DataSourceException 鏁版嵁婧愬紓甯?
     */
    void updateDataSource(DataSourceEntity datasource) throws DataSourceException;
    
    /**
     * 鍒犻櫎鏁版嵁婧?
     * 
     * @param datasourceId 鏁版嵁婧怚D
     * @throws DataSourceException 鏁版嵁婧愬紓甯?
     */
    void deleteDataSource(String datasourceId) throws DataSourceException;
    
    /**
     * 鏍规嵁ID鏌ヨ鏁版嵁婧?
     * 
     * @param datasourceId 鏁版嵁婧怚D
     * @return 鏁版嵁婧愬疄浣?
     * @throws DataSourceException 鏁版嵁婧愬紓甯?
     */
    DataSourceEntity getDataSourceById(String datasourceId) throws DataSourceException;
    
    /**
     * 鏌ヨ鏁版嵁婧愬垪琛?
     * 
     * @param orgId 缁勭粐鏈烘瀯ID锛堝彲閫夛級
     * @param nodeId 鑺傜偣ID锛堝彲閫夛級
     * @param datasourceType 鏁版嵁婧愮被鍨嬶紙鍙€夛級
     * @param status 鐘舵€侊紙鍙€夛級
     * @return 鏁版嵁婧愬垪琛?
     * @throws DataSourceException 鏁版嵁婧愬紓甯?
     */
    List<DataSourceEntity> queryDataSources(String orgId, String nodeId, 
                                           String datasourceType, String status) throws DataSourceException;
    
    /**
     * 娴嬭瘯鏁版嵁婧愯繛鎺?
     * 
     * @param datasourceId 鏁版嵁婧怚D
     * @return 娴嬭瘯缁撴灉锛坱rue琛ㄧず鎴愬姛锛宖alse琛ㄧず澶辫触锛?
     * @throws DataSourceException 鏁版嵁婧愬紓甯?
     */
    boolean testDataSource(String datasourceId) throws DataSourceException;
    
    /**
     * 鎺ㄩ€佹暟鎹簮鍒拌妭鐐?
     * 灏嗘満鏋勭骇鏁版嵁婧愭帹閫佸埌鎸囧畾鑺傜偣
     * 
     * @param datasourceId 鏁版嵁婧怚D
     * @param nodeId 鑺傜偣ID
     * @throws DataSourceException 鏁版嵁婧愬紓甯?
     */
    void pushDataSourceToNode(String datasourceId, String nodeId) throws DataSourceException;
    
    /**
     * 鎵归噺鎺ㄩ€佹暟鎹簮鍒拌妭鐐?
     * 
     * @param datasourceIds 鏁版嵁婧怚D鍒楄〃
     * @param nodeId 鑺傜偣ID
     * @return 鎺ㄩ€佺粨鏋?
     * @throws DataSourceException 鏁版嵁婧愬紓甯?
     */
    Map<String, Object> batchPushDataSourcesToNode(List<String> datasourceIds, String nodeId) throws DataSourceException;
    
    /**
     * 鎸夋満鏋勬帹閫佹暟鎹簮鍒版墍鏈夎妭鐐?
     * 
     * @param datasourceId 鏁版嵁婧怚D
     * @param orgId 缁勭粐鏈烘瀯ID
     * @return 鎺ㄩ€佺粨鏋?
     * @throws DataSourceException 鏁版嵁婧愬紓甯?
     */
    Map<String, Object> pushDataSourceToOrgNodes(String datasourceId, String orgId) throws DataSourceException;
    
    /**
     * 鏌ヨ鑺傜偣鐨勬暟鎹簮鍒楄〃
     * 鍖呮嫭鑺傜偣绾ф暟鎹簮鍜屾満鏋勭骇鏁版嵁婧?
     * 
     * @param nodeId 鑺傜偣ID
     * @return 鏁版嵁婧愬垪琛?
     * @throws DataSourceException 鏁版嵁婧愬紓甯?
     */
    List<DataSourceEntity> getNodeDataSources(String nodeId) throws DataSourceException;
    
    /**
     * 璁剧疆榛樿鏁版嵁婧?
     * 
     * @param datasourceId 鏁版嵁婧怚D
     * @param nodeId 鑺傜偣ID锛堝彲閫夛級
     * @throws DataSourceException 鏁版嵁婧愬紓甯?
     */
    void setDefaultDataSource(String datasourceId, String nodeId) throws DataSourceException;
    
    /**
     * 鏁版嵁婧愬紓甯?
     */
    class DataSourceException extends Exception {
        private static final long serialVersionUID = 1L;
        
        public DataSourceException(String message) {
            super(message);
        }
        
        public DataSourceException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

