package com.chinacreator.gzcm.runtime.core.dataaccess.storage;

import java.util.List;
import java.util.Map;

import com.chinacreator.gzcm.runtime.core.dataaccess.model.QueryRequest;
import com.chinacreator.gzcm.runtime.core.dataaccess.model.QueryResult;
import com.chinacreator.gzcm.runtime.core.dataaccess.storage.model.StorageConfig;
import com.chinacreator.gzcm.runtime.core.dataaccess.storage.model.WriteRequest;
import com.chinacreator.gzcm.runtime.core.datadescription.model.DataSchema;

/**
 * 瀛樺偍閫傞厤鍣ㄦ帴鍙?
 * 鎻愪緵缁熶竴鐨勫瓨鍌ㄨ闂帴鍙ｏ紝鏀寔澶氱瀛樺偍寮曟搸
 * 
 * @author CDRC Runtime Team
 */
public interface IStorageAdapter {
    
    /**
     * 杩炴帴瀛樺偍
     * 
     * @param config 瀛樺偍閰嶇疆
     * @throws Exception
     */
    void connect(StorageConfig config) throws Exception;
    
    /**
     * 鏂紑杩炴帴
     * 
     * @throws Exception
     */
    void disconnect() throws Exception;
    
    /**
     * 娴嬭瘯杩炴帴
     * 
     * @return 杩炴帴鏄惁鎴愬姛
     * @throws Exception
     */
    boolean testConnection() throws Exception;
    
    /**
     * 鏌ヨ鏁版嵁
     * 
     * @param request 鏌ヨ璇锋眰
     * @return 鏌ヨ缁撴灉
     * @throws Exception
     */
    QueryResult<Map<String, Object>> query(QueryRequest request) throws Exception;
    
    /**
     * 鏌ヨ鏁版嵁锛堢畝鍖栫増鏈紝淇濇寔鍚戝悗鍏煎锛?
     * 
     * @param resource 璧勬簮鏍囪瘑锛堝琛ㄥ悕銆侀泦鍚堝悕绛夛級
     * @param columns 鏌ヨ鍒楀垪琛?
     * @param filter 杩囨护鏉′欢锛堢畝鍖栫増鏈紝浣跨敤Map琛ㄧず锛?
     * @param sort 鎺掑簭鏉′欢锛堢畝鍖栫増鏈紝浣跨敤Map琛ㄧず锛?
     * @param offset 鍋忕Щ閲?
     * @param limit 闄愬埗鏁伴噺
     * @return 鏌ヨ缁撴灉锛圠ist<Map<String, Object>>锛?
     * @throws Exception
     */
    List<Map<String, Object>> query(String resource, List<String> columns, 
            Map<String, Object> filter, Map<String, String> sort, 
            Integer offset, Integer limit) throws Exception;
    
    /**
     * 鍐欏叆鏁版嵁
     * 
     * @param request 鍐欏叆璇锋眰
     * @return 鍐欏叆鐨勮褰曟暟
     * @throws Exception
     */
    int write(WriteRequest request) throws Exception;
    
    /**
     * 鏌ヨ鎬绘暟
     * 
     * @param resource 璧勬簮鏍囪瘑
     * @param filter 杩囨护鏉′欢
     * @return 鎬绘暟
     * @throws Exception
     */
    long count(String resource, Map<String, Object> filter) throws Exception;
    
    /**
     * 鎻掑叆鏁版嵁锛堢畝鍖栫増鏈紝淇濇寔鍚戝悗鍏煎锛?
     * 
     * @param resource 璧勬簮鏍囪瘑
     * @param data 鏁版嵁鍒楄〃
     * @throws Exception
     */
    void insert(String resource, List<Map<String, Object>> data) throws Exception;
    
    /**
     * 鏇存柊鏁版嵁锛堢畝鍖栫増鏈紝淇濇寔鍚戝悗鍏煎锛?
     * 
     * @param resource 璧勬簮鏍囪瘑
     * @param filter 鏇存柊鏉′欢
     * @param updateFields 鏇存柊瀛楁
     * @return 鏇存柊鐨勮褰曟暟
     * @throws Exception
     */
    int update(String resource, Map<String, Object> filter, 
            Map<String, Object> updateFields) throws Exception;
    
    /**
     * 鍒犻櫎鏁版嵁锛堢畝鍖栫増鏈紝淇濇寔鍚戝悗鍏煎锛?
     * 
     * @param resource 璧勬簮鏍囪瘑
     * @param filter 鍒犻櫎鏉′欢
     * @param softDelete 鏄惁杞垹闄?
     * @return 鍒犻櫎鐨勮褰曟暟
     * @throws Exception
     */
    int delete(String resource, Map<String, Object> filter, boolean softDelete) throws Exception;
    
    /**
     * 鑾峰彇Schema
     * 
     * @param resource 璧勬簮鏍囪瘑
     * @return Schema淇℃伅
     * @throws Exception
     */
    DataSchema getSchema(String resource) throws Exception;
    
    /**
     * 鑾峰彇瀛樺偍鍏冩暟鎹?
     * 
     * @return 鍏冩暟鎹俊鎭紙鍖呭惈瀛樺偍绫诲瀷銆佺増鏈€佺壒鎬х瓑锛?
     * @throws Exception
     */
    Map<String, Object> getMetadata() throws Exception;
    
    /**
     * 寮€濮嬩簨鍔?
     * 
     * @throws Exception
     */
    void beginTransaction() throws Exception;
    
    /**
     * 鎻愪氦浜嬪姟
     * 
     * @throws Exception
     */
    void commit() throws Exception;
    
    /**
     * 鍥炴粴浜嬪姟
     * 
     * @throws Exception
     */
    void rollback() throws Exception;
    
    /**
     * 鏄惁鏀寔浜嬪姟
     * 
     * @return 鏄惁鏀寔浜嬪姟
     */
    boolean supportsTransaction();
    
    /**
     * 鏄惁宸茶繛鎺?
     * 
     * @return 鏄惁宸茶繛鎺?
     */
    boolean isConnected();
}

