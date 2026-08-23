package com.chinacreator.gzcm.runtime.core.database;

import java.util.List;
import java.util.Map;

/**
 * 绯荤粺鏁版嵁搴撹闂帴鍙?
 * 涓撻棬鐢ㄤ簬绯荤粺绠＄悊妯″潡鐨勬暟鎹簱鎿嶄綔锛屾彁渚涚粺涓€鐨勬暟鎹簱璁块棶鎶借薄
 * 鎵€鏈夌郴缁熺鐞嗗瓙绯荤粺鐨勬暟鎹簱鎿嶄綔閮藉簲閫氳繃姝ゆ帴鍙ｅ畬鎴?
 * 
 * @author CDRC Runtime Team
 */
public interface ISystemDatabaseAccess {
    
    /**
     * 鎻掑叆鏁版嵁
     * 
     * @param <T> 瀹炰綋绫诲瀷
     * @param tableName 琛ㄥ悕
     * @param entity 瀹炰綋瀵硅薄
     * @throws DatabaseAccessException
     */
    <T> void insert(String tableName, T entity) throws DatabaseAccessException;
    
    /**
     * 鏇存柊鏁版嵁
     * 
     * @param <T> 瀹炰綋绫诲瀷
     * @param tableName 琛ㄥ悕
     * @param entity 瀹炰綋瀵硅薄
     * @throws DatabaseAccessException
     */
    <T> void update(String tableName, T entity) throws DatabaseAccessException;
    
    /**
     * 鍒犻櫎鏁版嵁
     * 
     * @param tableName 琛ㄥ悕
     * @param primaryKey 涓婚敭鍊?
     * @throws DatabaseAccessException
     */
    void delete(String tableName, String primaryKey) throws DatabaseAccessException;
    
    /**
     * 鏍规嵁涓婚敭鏌ヨ
     * 
     * @param <T> 瀹炰綋绫诲瀷
     * @param tableName 琛ㄥ悕
     * @param clazz 瀹炰綋绫?
     * @param primaryKey 涓婚敭鍊?
     * @return 瀹炰綋瀵硅薄
     * @throws DatabaseAccessException
     */
    <T> T findById(String tableName, Class<T> clazz, String primaryKey) throws DatabaseAccessException;
    
    /**
     * 鏍规嵁鏉′欢鏌ヨ鍗曚釜瀵硅薄
     * 
     * @param <T> 瀹炰綋绫诲瀷
     * @param tableName 琛ㄥ悕
     * @param clazz 瀹炰綋绫?
     * @param condition 鏌ヨ鏉′欢
     * @return 瀹炰綋瀵硅薄
     * @throws DatabaseAccessException
     */
    <T> T findOne(String tableName, Class<T> clazz, Map<String, Object> condition) throws DatabaseAccessException;
    
    /**
     * 鏍规嵁鏉′欢鏌ヨ鍒楄〃
     * 
     * @param <T> 瀹炰綋绫诲瀷
     * @param tableName 琛ㄥ悕
     * @param clazz 瀹炰綋绫?
     * @param condition 鏌ヨ鏉′欢
     * @return 瀹炰綋鍒楄〃
     * @throws DatabaseAccessException
     */
    <T> List<T> query(String tableName, Class<T> clazz, Map<String, Object> condition) throws DatabaseAccessException;
    
    /**
     * 鏍规嵁鏉′欢鏌ヨ鍒楄〃锛堝甫鍒嗛〉锛?
     * 
     * @param <T> 瀹炰綋绫诲瀷
     * @param tableName 琛ㄥ悕
     * @param clazz 瀹炰綋绫?
     * @param condition 鏌ヨ鏉′欢
     * @param offset 鍋忕Щ閲?
     * @param limit 闄愬埗鏁伴噺
     * @return 瀹炰綋鍒楄〃
     * @throws DatabaseAccessException
     */
    <T> List<T> query(String tableName, Class<T> clazz, Map<String, Object> condition, 
            int offset, int limit) throws DatabaseAccessException;
    
    /**
     * 缁熻鏁伴噺
     * 
     * @param tableName 琛ㄥ悕
     * @param condition 鏌ヨ鏉′欢
     * @return 鏁伴噺
     * @throws DatabaseAccessException
     */
    int count(String tableName, Map<String, Object> condition) throws DatabaseAccessException;
    
    /**
     * 鎵цSQL鏌ヨ锛堣繑鍥濵ap鍒楄〃锛?
     * 
     * @param sql SQL璇彞
     * @param params 鍙傛暟
     * @return 鏌ヨ缁撴灉
     * @throws DatabaseAccessException
     */
    List<Map<String, Object>> executeQuery(String sql, Object... params) throws DatabaseAccessException;
    
    /**
     * 鎵цSQL鏇存柊锛圛NSERT/UPDATE/DELETE锛?
     * 
     * @param sql SQL璇彞
     * @param params 鍙傛暟
     * @return 褰卞搷鐨勮鏁?
     * @throws DatabaseAccessException
     */
    int executeUpdate(String sql, Object... params) throws DatabaseAccessException;
    
    /**
     * 鎵цSQL閰嶇疆鏂囦欢涓殑SQL锛堜娇鐢–onfigSQLExecutor锛?
     * 鐢ㄤ簬鍏煎鐜版湁鐨凷QL閰嶇疆鏂囦欢
     * 
     * @param sqlConfigPath SQL閰嶇疆鏂囦欢璺緞锛堢浉瀵逛簬classpath锛?
     * @param sqlName SQL鍚嶇О
     * @param params 鍙傛暟锛堝彲浠ユ槸瀹炰綋瀵硅薄銆丮ap鎴栧熀鏈被鍨嬶級
     * @return 鏌ヨ缁撴灉锛圡ap鍒楄〃锛?
     * @throws DatabaseAccessException
     */
    List<Map<String, Object>> executeQueryFromConfig(String sqlConfigPath, String sqlName, Object params) throws DatabaseAccessException;
    
    /**
     * 鎵цSQL閰嶇疆鏂囦欢涓殑SQL锛堟洿鏂版搷浣滐紝浣跨敤鍩烘湰鍙傛暟锛?
     * 
     * @param sqlConfigPath SQL閰嶇疆鏂囦欢璺緞
     * @param sqlName SQL鍚嶇О
     * @param params 鍙傛暟锛圡ap鎴栧熀鏈被鍨嬶級
     * @return 褰卞搷鐨勮鏁?
     * @throws DatabaseAccessException
     */
    int executeUpdateFromConfig(String sqlConfigPath, String sqlName, java.util.Map<String, Object> params) throws DatabaseAccessException;
    
    /**
     * 鎵цSQL閰嶇疆鏂囦欢涓殑SQL锛堟彃鍏ユ搷浣滐紝杩斿洖瀹炰綋瀵硅薄锛?
     * 
     * @param <T> 瀹炰綋绫诲瀷
     * @param sqlConfigPath SQL閰嶇疆鏂囦欢璺緞
     * @param sqlName SQL鍚嶇О
     * @param entity 瀹炰綋瀵硅薄
     * @throws DatabaseAccessException
     */
    <T> void executeInsertFromConfig(String sqlConfigPath, String sqlName, T entity) throws DatabaseAccessException;
    
    /**
     * 鎵цSQL閰嶇疆鏂囦欢涓殑SQL锛堟洿鏂版搷浣滐紝浣跨敤瀹炰綋瀵硅薄锛?
     * 
     * @param <T> 瀹炰綋绫诲瀷
     * @param sqlConfigPath SQL閰嶇疆鏂囦欢璺緞
     * @param sqlName SQL鍚嶇О
     * @param entity 瀹炰綋瀵硅薄
     * @throws DatabaseAccessException
     */
    <T> void executeUpdateFromConfigWithEntity(String sqlConfigPath, String sqlName, T entity) throws DatabaseAccessException;
    
    /**
     * 鎵цSQL閰嶇疆鏂囦欢涓殑SQL锛堟煡璇㈠崟涓璞★級
     * 
     * @param <T> 瀹炰綋绫诲瀷
     * @param sqlConfigPath SQL閰嶇疆鏂囦欢璺緞
     * @param sqlName SQL鍚嶇О
     * @param clazz 瀹炰綋绫?
     * @param params 鍙傛暟
     * @return 瀹炰綋瀵硅薄
     * @throws DatabaseAccessException
     */
    <T> T queryObjectFromConfig(String sqlConfigPath, String sqlName, Class<T> clazz, Object params) throws DatabaseAccessException;
    
    /**
     * 鎵цSQL閰嶇疆鏂囦欢涓殑SQL锛堟煡璇㈠垪琛級
     * 
     * @param <T> 瀹炰綋绫诲瀷
     * @param sqlConfigPath SQL閰嶇疆鏂囦欢璺緞
     * @param sqlName SQL鍚嶇О
     * @param clazz 瀹炰綋绫?
     * @param params 鍙傛暟
     * @return 瀹炰綋鍒楄〃
     * @throws DatabaseAccessException
     */
    <T> List<T> queryListFromConfig(String sqlConfigPath, String sqlName, Class<T> clazz, Object params) throws DatabaseAccessException;
    
    /**
     * 寮€濮嬩簨鍔?
     * 
     * @throws DatabaseAccessException
     */
    void beginTransaction() throws DatabaseAccessException;
    
    /**
     * 鎻愪氦浜嬪姟
     * 
     * @throws DatabaseAccessException
     */
    void commit() throws DatabaseAccessException;
    
    /**
     * 鍥炴粴浜嬪姟
     * 
     * @throws DatabaseAccessException
     */
    void rollback() throws DatabaseAccessException;
    
    /**
     * 鏁版嵁搴撹闂紓甯?
     */
    class DatabaseAccessException extends Exception {
        private static final long serialVersionUID = 1L;
        
        public DatabaseAccessException(String message) {
            super(message);
        }
        
        public DatabaseAccessException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}

