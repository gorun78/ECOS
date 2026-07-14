package com.chinacreator.gzcm.runtime.core.dataaccess;

import java.util.List;

import com.chinacreator.gzcm.runtime.core.dataaccess.model.BatchRequest;
import com.chinacreator.gzcm.runtime.core.dataaccess.model.BatchResult;
import com.chinacreator.gzcm.runtime.core.dataaccess.model.DeleteRequest;
import com.chinacreator.gzcm.runtime.core.dataaccess.model.InsertOptions;
import com.chinacreator.gzcm.runtime.core.dataaccess.model.QueryRequest;
import com.chinacreator.gzcm.runtime.core.dataaccess.model.QueryResult;
import com.chinacreator.gzcm.runtime.core.dataaccess.model.UpdateRequest;

/**
 * 缁熶竴鏁版嵁璁块棶鎺ュ彛
 * 鎻愪緵缁熶竴鐨勬暟鎹闂娊璞★紝鏀寔澶氱鏁版嵁婧愮殑鏌ヨ銆佹彃鍏ャ€佹洿鏂般€佸垹闄ゆ搷浣?
 * 
 * @author CDRC Runtime Team
 */
public interface DataAccess {
    
    /**
     * 鏌ヨ鏁版嵁
     * 
     * @param <T> 杩斿洖鏁版嵁绫诲瀷
     * @param request 鏌ヨ璇锋眰
     * @param clazz 鐩爣绫诲瀷
     * @return 鏌ヨ缁撴灉
     * @throws DataAccessException
     */
    <T> QueryResult<T> query(QueryRequest request, Class<T> clazz) throws DataAccessException;
    
    /**
     * 鎻掑叆鏁版嵁
     * 
     * @param <T> 鏁版嵁绫诲瀷
     * @param dataProductId 鏁版嵁浜у搧ID
     * @param data 寰呮彃鍏ョ殑鏁版嵁鍒楄〃
     * @param options 鎻掑叆閫夐」
     * @throws DataAccessException
     */
    <T> void insert(String dataProductId, List<T> data, InsertOptions options) throws DataAccessException;
    
    /**
     * 鏇存柊鏁版嵁
     * 
     * @param <T> 鏁版嵁绫诲瀷
     * @param request 鏇存柊璇锋眰
     * @param data 鏇存柊鍚庣殑鏁版嵁锛堝彲閫夛紝濡傛灉鎻愪緵鍒欎娇鐢╠ata涓殑鍊硷紝鍚﹀垯浣跨敤request涓殑updateFields锛?
     * @return 鏇存柊鐨勮褰曟暟
     * @throws DataAccessException
     */
    <T> int update(UpdateRequest request, T data) throws DataAccessException;
    
    /**
     * 鍒犻櫎鏁版嵁
     * 
     * @param request 鍒犻櫎璇锋眰
     * @return 鍒犻櫎鐨勮褰曟暟
     * @throws DataAccessException
     */
    int delete(DeleteRequest request) throws DataAccessException;
    
    /**
     * 鎵归噺鎿嶄綔
     * 
     * @param <T> 鏁版嵁绫诲瀷
     * @param request 鎵归噺璇锋眰
     * @return 鎵归噺鎿嶄綔缁撴灉
     * @throws DataAccessException
     */
    <T> BatchResult batch(BatchRequest<T> request) throws DataAccessException;
    
    /**
     * 鏁版嵁璁块棶寮傚父
     */
    class DataAccessException extends Exception {
        private static final long serialVersionUID = 1L;
        private String errorCode;
        
        public DataAccessException(String message) {
            super(message);
        }
        
        public DataAccessException(String message, Throwable cause) {
            super(message, cause);
        }
        
        public DataAccessException(String errorCode, String message) {
            super(message);
            this.errorCode = errorCode;
        }
        
        public DataAccessException(String errorCode, String message, Throwable cause) {
            super(message, cause);
            this.errorCode = errorCode;
        }
        
        public String getErrorCode() {
            return errorCode;
        }
    }
}

